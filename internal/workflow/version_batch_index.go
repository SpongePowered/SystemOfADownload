package workflow

import (
	"fmt"

	enumspb "go.temporal.io/api/enums/v1"
	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/domain"
)

// VersionBatchIndexInput is the input for the VersionBatchIndexWorkflow.
type VersionBatchIndexInput struct {
	Versions       []domain.VersionInfo
	WindowSize     int
	Offset         int
	Progress       int
	CurrentRecords map[string]bool // version string -> in-progress
}

// VersionBatchIndexOutput is the result of the VersionBatchIndexWorkflow.
type VersionBatchIndexOutput struct {
	TotalProcessed int
}

const (
	versionBatchPageSize              = 50
	versionBatchDefaultWindowSize     = 10
	versionBatchCompletionSignalName  = "VersionIndexCompletion"
)

// versionBatchState holds the mutable state for the sliding window.
type versionBatchState struct {
	input          VersionBatchIndexInput
	currentRecords map[string]bool
	childrenStarted []workflow.ChildWorkflowFuture
	offset         int
	progress       int

	pumpCancel     workflow.CancelFunc
	pumpDone       workflow.Future
}

// VersionBatchIndexWorkflow processes new versions using a sliding window of child workflows.
func VersionBatchIndexWorkflow(ctx workflow.Context, input VersionBatchIndexInput) (int, error) {
	if input.WindowSize <= 0 {
		input.WindowSize = versionBatchDefaultWindowSize
	}

	s := &versionBatchState{
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

func (s *versionBatchState) execute(ctx workflow.Context) (int, error) {
	s.startCompletionPump(ctx)

	// Determine the page of versions to process in this run
	end := s.offset + versionBatchPageSize
	if end > len(s.input.Versions) {
		end = len(s.input.Versions)
	}
	page := s.input.Versions[s.offset:end]

	parentID := workflow.GetInfo(ctx).WorkflowExecution.ID

	for _, v := range page {
		// Wait for capacity in the sliding window
		err := workflow.Await(ctx, func() bool {
			return len(s.currentRecords) < s.input.WindowSize
		})
		if err != nil {
			return 0, err
		}

		childOpts := workflow.ChildWorkflowOptions{
			WorkflowID:        fmt.Sprintf("%s/version-index-%s", parentID, v.Version),
			ParentClosePolicy: enumspb.PARENT_CLOSE_POLICY_ABANDON,
			RetryPolicy: &temporal.RetryPolicy{
				MaximumAttempts: 3,
			},
		}
		childCtx := workflow.WithChildOptions(ctx, childOpts)

		child := workflow.ExecuteChildWorkflow(childCtx, VersionIndexWorkflow, VersionIndexInput{
			GroupID:    v.GroupID,
			ArtifactID: v.ArtifactID,
			Version:    v.Version,
		})

		s.childrenStarted = append(s.childrenStarted, child)
		s.currentRecords[v.Version] = true
	}

	s.offset = end
	return s.continueOrComplete(ctx)
}

func (s *versionBatchState) continueOrComplete(ctx workflow.Context) (int, error) {
	if s.offset < len(s.input.Versions) {
		// Wait for all children started in this run to actually begin executing
		for _, child := range s.childrenStarted {
			if err := child.GetChildWorkflowExecution().Get(ctx, nil); err != nil {
				return 0, err
			}
		}

		// Drain completion signals before continue-as-new
		s.drainCompletionSignals(ctx)

		return 0, workflow.NewContinueAsNewError(ctx, VersionBatchIndexWorkflow, VersionBatchIndexInput{
			Versions:       s.input.Versions,
			WindowSize:     s.input.WindowSize,
			Offset:         s.offset,
			Progress:       s.progress,
			CurrentRecords: s.currentRecords,
		})
	}

	// Last run: wait for all children to complete
	err := workflow.Await(ctx, func() bool {
		return len(s.currentRecords) == 0
	})
	if err != nil {
		return 0, err
	}

	// Drain any remaining signals before completing
	s.drainCompletionSignals(ctx)

	return s.progress, nil
}

func (s *versionBatchState) startCompletionPump(ctx workflow.Context) {
	pumpCtx, cancel := workflow.WithCancel(ctx)
	s.pumpCancel = cancel

	done, doneSettable := workflow.NewFuture(ctx)
	s.pumpDone = done

	ch := workflow.GetSignalChannel(ctx, versionBatchCompletionSignalName)

	workflow.Go(pumpCtx, func(gCtx workflow.Context) {
		sel := workflow.NewSelector(gCtx)
		sel.AddReceive(ch, func(c workflow.ReceiveChannel, more bool) {
			var version string
			c.Receive(gCtx, &version)
			s.recordCompletion(version)
		})
		sel.AddReceive(gCtx.Done(), func(c workflow.ReceiveChannel, more bool) {
			doneSettable.Set(nil, nil)
		})
		for !done.IsReady() {
			sel.Select(gCtx)
		}
	})
}

func (s *versionBatchState) drainCompletionSignals(ctx workflow.Context) {
	s.pumpCancel()
	_ = s.pumpDone.Get(ctx, nil)

	ch := workflow.GetSignalChannel(ctx, versionBatchCompletionSignalName)
	for {
		var version string
		if !ch.ReceiveAsync(&version) {
			break
		}
		s.recordCompletion(version)
	}
}

func (s *versionBatchState) recordCompletion(version string) {
	if _, ok := s.currentRecords[version]; ok {
		delete(s.currentRecords, version)
		s.progress++
	}
}
