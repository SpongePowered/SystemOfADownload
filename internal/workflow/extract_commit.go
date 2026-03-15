package workflow

import (
	"fmt"
	"time"

	enumspb "go.temporal.io/api/enums/v1"
	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
)

// ExtractCommitBatchInput is the input for the ExtractCommitBatchWorkflow.
type ExtractCommitBatchInput struct {
	GroupID    string
	ArtifactID string
	Version    string
	Candidates []activity.JarCommitCandidate
	WindowSize int
	Offset     int
	Progress   int
	CurrentRecords map[string]bool // download URL -> in-progress
}

const (
	extractCommitPageSize             = 5
	extractCommitDefaultWindowSize    = 3
	extractCommitCompletionSignalName = "ExtractCommitCompletion"
)

type extractCommitBatchState struct {
	input          ExtractCommitBatchInput
	currentRecords map[string]bool
	childrenStarted []workflow.ChildWorkflowFuture
	offset         int
	progress       int

	pumpCancel workflow.CancelFunc
	pumpDone   workflow.Future
}

// ExtractCommitBatchWorkflow processes jar files using a sliding window to extract commits.
func ExtractCommitBatchWorkflow(ctx workflow.Context, input ExtractCommitBatchInput) (int, error) {
	if input.WindowSize <= 0 {
		input.WindowSize = extractCommitDefaultWindowSize
	}

	s := &extractCommitBatchState{
		input:          input,
		currentRecords: input.CurrentRecords,
		offset:         input.Offset,
		progress:       input.Progress,
	}
	if s.currentRecords == nil {
		s.currentRecords = make(map[string]bool)
	}

	return s.execute(ctx)
}

func (s *extractCommitBatchState) execute(ctx workflow.Context) (int, error) {
	s.startCompletionPump(ctx)

	end := s.offset + extractCommitPageSize
	if end > len(s.input.Candidates) {
		end = len(s.input.Candidates)
	}
	page := s.input.Candidates[s.offset:end]

	parentID := workflow.GetInfo(ctx).WorkflowExecution.ID

	for i, candidate := range page {
		err := workflow.Await(ctx, func() bool {
			return len(s.currentRecords) < s.input.WindowSize
		})
		if err != nil {
			return 0, err
		}

		childOpts := workflow.ChildWorkflowOptions{
			WorkflowID:        fmt.Sprintf("%s/extract-%d", parentID, s.offset+i),
			ParentClosePolicy: enumspb.PARENT_CLOSE_POLICY_ABANDON,
		}
		childCtx := workflow.WithChildOptions(ctx, childOpts)

		child := workflow.ExecuteChildWorkflow(childCtx, ExtractCommitWorkflow, ExtractCommitInput{
			GroupID:     s.input.GroupID,
			ArtifactID:  s.input.ArtifactID,
			Version:     s.input.Version,
			DownloadURL: candidate.DownloadURL,
		})

		s.childrenStarted = append(s.childrenStarted, child)
		s.currentRecords[candidate.DownloadURL] = true
	}

	s.offset = end
	return s.continueOrComplete(ctx)
}

func (s *extractCommitBatchState) continueOrComplete(ctx workflow.Context) (int, error) {
	if s.offset < len(s.input.Candidates) {
		for _, child := range s.childrenStarted {
			if err := child.GetChildWorkflowExecution().Get(ctx, nil); err != nil {
				return 0, err
			}
		}

		s.drainCompletionSignals(ctx)

		return 0, workflow.NewContinueAsNewError(ctx, ExtractCommitBatchWorkflow, ExtractCommitBatchInput{
			GroupID:        s.input.GroupID,
			ArtifactID:     s.input.ArtifactID,
			Version:        s.input.Version,
			Candidates:     s.input.Candidates,
			WindowSize:     s.input.WindowSize,
			Offset:         s.offset,
			Progress:       s.progress,
			CurrentRecords: s.currentRecords,
		})
	}

	err := workflow.Await(ctx, func() bool {
		return len(s.currentRecords) == 0
	})
	if err != nil {
		return 0, err
	}
	return s.progress, nil
}

func (s *extractCommitBatchState) startCompletionPump(ctx workflow.Context) {
	pumpCtx, cancel := workflow.WithCancel(ctx)
	s.pumpCancel = cancel

	done, doneSettable := workflow.NewFuture(ctx)
	s.pumpDone = done

	ch := workflow.GetSignalChannel(ctx, extractCommitCompletionSignalName)

	workflow.Go(pumpCtx, func(gCtx workflow.Context) {
		sel := workflow.NewSelector(gCtx)
		sel.AddReceive(ch, func(c workflow.ReceiveChannel, more bool) {
			var url string
			c.Receive(gCtx, &url)
			s.recordCompletion(url)
		})
		sel.AddReceive(gCtx.Done(), func(c workflow.ReceiveChannel, more bool) {
			doneSettable.Set(nil, nil)
		})
		for !done.IsReady() {
			sel.Select(gCtx)
		}
	})
}

func (s *extractCommitBatchState) drainCompletionSignals(ctx workflow.Context) {
	s.pumpCancel()
	_ = s.pumpDone.Get(ctx, nil)

	ch := workflow.GetSignalChannel(ctx, extractCommitCompletionSignalName)
	for {
		var url string
		if !ch.ReceiveAsync(&url) {
			break
		}
		s.recordCompletion(url)
	}
}

func (s *extractCommitBatchState) recordCompletion(url string) {
	if _, ok := s.currentRecords[url]; ok {
		delete(s.currentRecords, url)
		s.progress++
	}
}

// ExtractCommitInput is the input for the ExtractCommitWorkflow.
type ExtractCommitInput struct {
	GroupID     string
	ArtifactID string
	Version    string
	DownloadURL string
}

// ExtractCommitWorkflow downloads a single jar, extracts commit info, and stores it.
func ExtractCommitWorkflow(ctx workflow.Context, input ExtractCommitInput) error {
	activityOpts := workflow.ActivityOptions{
		StartToCloseTimeout: 5 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			InitialInterval:    2 * time.Second,
			BackoffCoefficient: 2.0,
			MaximumInterval:    time.Minute,
			MaximumAttempts:    3,
		},
	}
	ctx = workflow.WithActivityOptions(ctx, activityOpts)

	var indexActivities *activity.VersionIndexActivities

	// Extract commit from jar
	var extractResult activity.ExtractCommitFromJarOutput
	err := workflow.ExecuteActivity(ctx, indexActivities.ExtractCommitFromJar, activity.ExtractCommitFromJarInput{
		DownloadURL: input.DownloadURL,
	}).Get(ctx, &extractResult)
	if err != nil {
		return fmt.Errorf("extracting commit from jar: %w", err)
	}

	// Store if commit was found
	if extractResult.CommitInfo != nil {
		err = workflow.ExecuteActivity(ctx, indexActivities.StoreCommitInfo, activity.StoreCommitInfoInput{
			GroupID:    input.GroupID,
			ArtifactID: input.ArtifactID,
			Version:    input.Version,
			CommitInfo: *extractResult.CommitInfo,
		}).Get(ctx, nil)
		if err != nil {
			return fmt.Errorf("storing commit info: %w", err)
		}
	}

	// Signal parent about completion
	parent := workflow.GetInfo(ctx).ParentWorkflowExecution
	if parent != nil {
		signaled := workflow.SignalExternalWorkflow(ctx, parent.ID, "", extractCommitCompletionSignalName, input.DownloadURL)
		if err := signaled.Get(ctx, nil); err != nil {
			return fmt.Errorf("signaling parent: %w", err)
		}
	}

	return nil
}
