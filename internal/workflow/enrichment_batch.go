package workflow

import (
	"fmt"
	"time"

	enumspb "go.temporal.io/api/enums/v1"
	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/spongepowered/systemofadownload/internal/activity"
	"github.com/spongepowered/systemofadownload/internal/domain"
)

// EnrichmentBatchInput is the input for the EnrichmentBatchWorkflow.
type EnrichmentBatchInput struct {
	Versions        []activity.VersionForEnrichment
	GitRepositories []string // from artifact registration
	WindowSize      int
	Offset          int
	Progress        int
	CurrentRecords  map[string]bool // version string → in-progress
}

const (
	enrichmentBatchPageSize             = 10
	enrichmentBatchDefaultWindowSize    = 3
	enrichmentBatchCompletionSignalName = "EnrichVersionCompletion"
)

type enrichmentBatchState struct {
	input           EnrichmentBatchInput
	currentRecords  map[string]bool
	childrenStarted []workflow.ChildWorkflowFuture
	offset          int
	progress        int
	pumpCancel      workflow.CancelFunc
	pumpDone        workflow.Future
}

// EnrichmentBatchWorkflow processes versions using a sliding window of child
// workflows to enrich commit details. Versions can be processed in parallel.
func EnrichmentBatchWorkflow(ctx workflow.Context, input EnrichmentBatchInput) (int, error) {
	if input.WindowSize <= 0 {
		input.WindowSize = enrichmentBatchDefaultWindowSize
	}

	s := &enrichmentBatchState{
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

func (s *enrichmentBatchState) execute(ctx workflow.Context) (int, error) {
	s.startCompletionPump(ctx)

	end := s.offset + enrichmentBatchPageSize
	if end > len(s.input.Versions) {
		end = len(s.input.Versions)
	}
	page := s.input.Versions[s.offset:end]
	parentID := workflow.GetInfo(ctx).WorkflowExecution.ID

	for _, v := range page {
		err := workflow.Await(ctx, func() bool {
			return len(s.currentRecords) < s.input.WindowSize
		})
		if err != nil {
			return 0, err
		}

		childOpts := workflow.ChildWorkflowOptions{
			WorkflowID:        fmt.Sprintf("%s/enrich-%s", parentID, v.Version),
			ParentClosePolicy: enumspb.PARENT_CLOSE_POLICY_ABANDON,
			RetryPolicy: &temporal.RetryPolicy{
				MaximumAttempts: 3,
			},
		}
		childCtx := workflow.WithChildOptions(ctx, childOpts)

		child := workflow.ExecuteChildWorkflow(childCtx, EnrichVersionWorkflow, EnrichVersionInput{
			VersionID:       v.ID,
			ArtifactID:      v.ArtifactID,
			Version:         v.Version,
			CommitSha:       v.CommitSha,
			Repository:      v.Repository,
			GitRepositories: s.input.GitRepositories,
		})

		s.childrenStarted = append(s.childrenStarted, child)
		s.currentRecords[v.Version] = true
	}

	s.offset = end
	return s.continueOrComplete(ctx)
}

func (s *enrichmentBatchState) continueOrComplete(ctx workflow.Context) (int, error) {
	if s.offset < len(s.input.Versions) {
		for _, child := range s.childrenStarted {
			if err := child.GetChildWorkflowExecution().Get(ctx, nil); err != nil {
				return 0, err
			}
		}
		s.drainCompletionSignals(ctx)

		return 0, workflow.NewContinueAsNewError(ctx, EnrichmentBatchWorkflow, EnrichmentBatchInput{
			Versions:        s.input.Versions,
			GitRepositories: s.input.GitRepositories,
			WindowSize:      s.input.WindowSize,
			Offset:          s.offset,
			Progress:        s.progress,
			CurrentRecords:  s.currentRecords,
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

func (s *enrichmentBatchState) startCompletionPump(ctx workflow.Context) {
	pumpCtx, cancel := workflow.WithCancel(ctx)
	s.pumpCancel = cancel

	done, doneSettable := workflow.NewFuture(ctx)
	s.pumpDone = done

	ch := workflow.GetSignalChannel(ctx, enrichmentBatchCompletionSignalName)

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

func (s *enrichmentBatchState) drainCompletionSignals(ctx workflow.Context) {
	s.pumpCancel()
	_ = s.pumpDone.Get(ctx, nil)

	ch := workflow.GetSignalChannel(ctx, enrichmentBatchCompletionSignalName)
	for {
		var version string
		if !ch.ReceiveAsync(&version) {
			break
		}
		s.recordCompletion(version)
	}
}

func (s *enrichmentBatchState) recordCompletion(version string) {
	if _, ok := s.currentRecords[version]; ok {
		delete(s.currentRecords, version)
		s.progress++
	}
}

// EnrichVersionInput is the input for EnrichVersionWorkflow.
type EnrichVersionInput struct {
	VersionID       int64
	ArtifactID      int64
	Version         string
	CommitSha       string
	Repository      string   // from commit_body (may be empty)
	GitRepositories []string // from artifact registration
}

// EnrichVersionWorkflow enriches a single version with full commit details and
// submodule state by running local activities against the git cache.
//
// It tries each registered git repository to find which one contains the commit SHA.
func EnrichVersionWorkflow(ctx workflow.Context, input EnrichVersionInput) error {
	localOpts := workflow.LocalActivityOptions{
		StartToCloseTimeout: 2 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			InitialInterval:    2 * time.Second,
			BackoffCoefficient: 2.0,
			MaximumInterval:    30 * time.Second,
			MaximumAttempts:    3,
		},
	}
	localCtx := workflow.WithLocalActivityOptions(ctx, localOpts)

	readLocalOpts := workflow.LocalActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 1, // don't retry reads — we'll try the next repo
		},
	}
	readLocalCtx := workflow.WithLocalActivityOptions(ctx, readLocalOpts)

	activityOpts := workflow.ActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 3,
		},
	}
	actCtx := workflow.WithActivityOptions(ctx, activityOpts)

	var gitActs *activity.GitActivities
	var changelogActs *activity.ChangelogActivities

	// Build list of repos to try: if commit_body had a repo URL, try it first,
	// then fall back to the artifact's registered git_repositories.
	reposToTry := input.GitRepositories
	if input.Repository != "" {
		// Prepend the commit_body repo URL if it's not already in the list
		found := false
		for _, r := range reposToTry {
			if r == input.Repository {
				found = true
				break
			}
		}
		if !found {
			reposToTry = append([]string{input.Repository}, reposToTry...)
		}
	}

	// Step 1+2: Clone each repo and try to resolve the commit
	var commitResult activity.GetCommitDetailsOutput
	var cloneResult activity.EnsureRepoClonedOutput
	var foundRepo string

	for _, repoURL := range reposToTry {
		err := workflow.ExecuteLocalActivity(localCtx, gitActs.EnsureRepoCloned, activity.EnsureRepoClonedInput{
			RepoURL: repoURL,
		}).Get(ctx, &cloneResult)
		if err != nil {
			continue // try next repo
		}

		err = workflow.ExecuteLocalActivity(readLocalCtx, gitActs.GetCommitDetails, activity.GetCommitDetailsInput{
			RepoPath: cloneResult.LocalPath,
			Sha:      input.CommitSha,
		}).Get(ctx, &commitResult)
		if err == nil {
			foundRepo = repoURL
			break
		}
	}

	if foundRepo == "" {
		// Commit not found in any repo. Store partial enrichment with error.
		enriched := domain.CommitInfo{
			Sha:             input.CommitSha,
			Repository:      input.Repository,
			EnrichedAt:      workflow.Now(ctx).UTC().Format("2006-01-02T15:04:05Z"),
			ChangelogStatus: "error_commit_not_found",
		}
		_ = workflow.ExecuteActivity(actCtx, changelogActs.StoreEnrichedCommit, activity.StoreEnrichedCommitInput{
			VersionID:  input.VersionID,
			CommitInfo: enriched,
		}).Get(ctx, nil)
		return signalEnrichmentParent(ctx, input.Version)
	}

	// Step 3: Resolve submodules
	var submoduleResult activity.ResolveSubmodulesOutput
	err := workflow.ExecuteLocalActivity(readLocalCtx, gitActs.ResolveSubmodules, activity.ResolveSubmodulesInput{
		RepoPath: cloneResult.LocalPath,
		Sha:      input.CommitSha,
	}).Get(ctx, &submoduleResult)
	if err != nil {
		// No submodules or error reading them — not fatal
		submoduleResult = activity.ResolveSubmodulesOutput{}
	}

	// Step 4: Clone submodule repos and get their commit details (parallel)
	var submoduleCommits []domain.SubmoduleCommit
	if len(submoduleResult.Submodules) > 0 {
		submoduleCommits, err = enrichSubmodules(ctx, localCtx, readLocalCtx, gitActs, submoduleResult.Submodules)
		if err != nil {
			// Non-fatal: continue without submodule data
			submoduleCommits = nil
		}
	}

	// Step 5: Build enriched CommitInfo and store
	enriched := domain.CommitInfo{
		Sha:        commitResult.Sha,
		Repository: foundRepo,
		Message:    commitResult.Message,
		Body:       commitResult.Body,
		Author: &domain.CommitAuthor{
			Name:  commitResult.AuthorName,
			Email: commitResult.AuthorEmail,
		},
		CommitDate: commitResult.CommitDate,
		Submodules: submoduleCommits,
		EnrichedAt: workflow.Now(ctx).UTC().Format("2006-01-02T15:04:05Z"),
	}

	err = workflow.ExecuteActivity(actCtx, changelogActs.StoreEnrichedCommit, activity.StoreEnrichedCommitInput{
		VersionID:  input.VersionID,
		CommitInfo: enriched,
	}).Get(ctx, nil)
	if err != nil {
		return fmt.Errorf("storing enriched commit: %w", err)
	}

	return signalEnrichmentParent(ctx, input.Version)
}

func signalEnrichmentParent(ctx workflow.Context, version string) error {
	parent := workflow.GetInfo(ctx).ParentWorkflowExecution
	if parent != nil {
		signaled := workflow.SignalExternalWorkflow(ctx, parent.ID, "", enrichmentBatchCompletionSignalName, version)
		if err := signaled.Get(ctx, nil); err != nil {
			return fmt.Errorf("signaling parent: %w", err)
		}
	}
	return nil
}

// enrichSubmodules clones and resolves commit details for each submodule in parallel.
func enrichSubmodules(
	ctx workflow.Context,
	localCtx workflow.Context,
	readLocalCtx workflow.Context,
	gitActs *activity.GitActivities,
	submodules []activity.SubmoduleRefOutput,
) ([]domain.SubmoduleCommit, error) {
	// Launch clone + details in parallel for each submodule
	type subResult struct {
		commit domain.SubmoduleCommit
		err    error
	}

	futures := make([]workflow.Future, len(submodules))
	for i, sub := range submodules {
		sub := sub
		done, settable := workflow.NewFuture(ctx)
		futures[i] = done

		workflow.Go(ctx, func(gCtx workflow.Context) {
			// Clone submodule repo
			var cloneOut activity.EnsureRepoClonedOutput
			err := workflow.ExecuteLocalActivity(localCtx, gitActs.EnsureRepoCloned, activity.EnsureRepoClonedInput{
				RepoURL: sub.URL,
			}).Get(gCtx, &cloneOut)
			if err != nil {
				settable.Set(nil, fmt.Errorf("cloning submodule %s: %w", sub.URL, err))
				return
			}

			// Get commit details
			var detailOut activity.GetCommitDetailsOutput
			err = workflow.ExecuteLocalActivity(readLocalCtx, gitActs.GetCommitDetails, activity.GetCommitDetailsInput{
				RepoPath: cloneOut.LocalPath,
				Sha:      sub.Sha,
			}).Get(gCtx, &detailOut)
			if err != nil {
				// Submodule commit not found — still report what we know
				settable.Set(&domain.SubmoduleCommit{
					Repository: sub.URL,
					Sha:        sub.Sha,
				}, nil)
				return
			}

			settable.Set(&domain.SubmoduleCommit{
				Repository: sub.URL,
				Sha:        detailOut.Sha,
				Message:    detailOut.Message,
				Author: &domain.CommitAuthor{
					Name:  detailOut.AuthorName,
					Email: detailOut.AuthorEmail,
				},
				CommitDate: detailOut.CommitDate,
			}, nil)
		})
	}

	// Collect results
	commits := make([]domain.SubmoduleCommit, 0, len(futures))
	for _, f := range futures {
		var commit *domain.SubmoduleCommit
		if err := f.Get(ctx, &commit); err != nil {
			return nil, err
		}
		if commit != nil {
			commits = append(commits, *commit)
		}
	}

	return commits, nil
}
