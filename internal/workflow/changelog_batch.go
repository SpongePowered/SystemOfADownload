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

// ChangelogBatchInput is the input for the ChangelogBatchWorkflow.
type ChangelogBatchInput struct {
	Versions        []activity.VersionForEnrichment
	GitRepositories []string
	WindowSize      int // should be 1 for sequential processing
	Offset          int
	Progress        int
	CurrentRecords  map[string]bool
}

const (
	changelogBatchPageSize             = 50
	changelogBatchDefaultWindowSize    = 5 // parallel: enrichment phase guarantees N-1 is already resolved
	changelogBatchCompletionSignalName = "ChangelogVersionCompletion"
	changelogPollInterval              = 30 * time.Second
	changelogPollMaxAttempts           = 20 // 30s × 20 = 10 minutes
)

type changelogBatchState struct {
	input           ChangelogBatchInput
	currentRecords  map[string]bool
	childrenStarted []workflow.ChildWorkflowFuture
	offset          int
	progress        int
	pumpCancel      workflow.CancelFunc
	pumpDone        workflow.Future
}

// ChangelogBatchWorkflow processes versions sequentially (window size 1) to
// compute changelogs between consecutive versions in sort order.
func ChangelogBatchWorkflow(ctx workflow.Context, input ChangelogBatchInput) (int, error) { //nolint:gocritic // Temporal workflow signature requires value type
	if input.WindowSize <= 0 {
		input.WindowSize = changelogBatchDefaultWindowSize
	}

	s := &changelogBatchState{
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

func (s *changelogBatchState) execute(ctx workflow.Context) (int, error) {
	s.startCompletionPump(ctx)

	end := s.offset + changelogBatchPageSize
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
			WorkflowID:        fmt.Sprintf("%s/changelog-%s", parentID, v.Version),
			ParentClosePolicy: enumspb.PARENT_CLOSE_POLICY_ABANDON,
			RetryPolicy: &temporal.RetryPolicy{
				MaximumAttempts: 3,
			},
		}
		childCtx := workflow.WithChildOptions(ctx, childOpts)

		child := workflow.ExecuteChildWorkflow(childCtx, ChangelogVersionWorkflow, ChangelogVersionInput{
			VersionID:       v.ID,
			ArtifactID:      v.ArtifactID,
			Version:         v.Version,
			SortOrder:       v.SortOrder,
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

func (s *changelogBatchState) continueOrComplete(ctx workflow.Context) (int, error) {
	if s.offset < len(s.input.Versions) {
		for _, child := range s.childrenStarted {
			if err := child.GetChildWorkflowExecution().Get(ctx, nil); err != nil {
				return 0, err
			}
		}
		s.drainCompletionSignals(ctx)

		return 0, workflow.NewContinueAsNewError(ctx, ChangelogBatchWorkflow, ChangelogBatchInput{
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
	s.drainCompletionSignals(ctx)
	return s.progress, nil
}

func (s *changelogBatchState) startCompletionPump(ctx workflow.Context) {
	pumpCtx, cancel := workflow.WithCancel(ctx)
	s.pumpCancel = cancel

	done, doneSettable := workflow.NewFuture(ctx)
	s.pumpDone = done

	ch := workflow.GetSignalChannel(ctx, changelogBatchCompletionSignalName)

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

func (s *changelogBatchState) drainCompletionSignals(ctx workflow.Context) {
	s.pumpCancel()
	_ = s.pumpDone.Get(ctx, nil)

	ch := workflow.GetSignalChannel(ctx, changelogBatchCompletionSignalName)
	for {
		var version string
		if !ch.ReceiveAsync(&version) {
			break
		}
		s.recordCompletion(version)
	}
}

func (s *changelogBatchState) recordCompletion(version string) {
	if _, ok := s.currentRecords[version]; ok {
		delete(s.currentRecords, version)
		s.progress++
	}
}

// ChangelogVersionInput is the input for ChangelogVersionWorkflow.
type ChangelogVersionInput struct {
	VersionID       int64
	ArtifactID      int64
	Version         string
	SortOrder       int32
	CommitSha       string
	Repository      string
	GitRepositories []string
}

// ChangelogVersionWorkflow computes the changelog between this version and
// its predecessor. It waits for the predecessor to be enriched if needed.
func ChangelogVersionWorkflow(ctx workflow.Context, input ChangelogVersionInput) error { //nolint:gocritic // Temporal workflow signature requires value type
	err := changelogVersionWork(ctx, input)

	// Always signal the parent, even on failure.
	parent := workflow.GetInfo(ctx).ParentWorkflowExecution
	if parent != nil {
		signaled := workflow.SignalExternalWorkflow(ctx, parent.ID, "", changelogBatchCompletionSignalName, input.Version)
		_ = signaled.Get(ctx, nil)
	}

	return err
}

func changelogVersionWork(ctx workflow.Context, input ChangelogVersionInput) error { //nolint:gocritic // matches workflow signature
	activityOpts := workflow.ActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 3,
		},
	}
	actCtx := workflow.WithActivityOptions(ctx, activityOpts)

	localOpts := workflow.LocalActivityOptions{
		StartToCloseTimeout: 2 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 3,
		},
	}
	localCtx := workflow.WithLocalActivityOptions(ctx, localOpts)

	var changelogActs *activity.ChangelogActivities
	var gitActs *activity.GitActivities

	// Step 1: Get the previous version's commit data
	var prevResult activity.GetPreviousVersionCommitOutput
	err := workflow.ExecuteActivity(actCtx, changelogActs.GetPreviousVersionCommit, activity.GetPreviousVersionCommitInput{
		ArtifactID: input.ArtifactID,
		SortOrder:  input.SortOrder,
	}).Get(ctx, &prevResult)
	if err != nil {
		return fmt.Errorf("getting previous version: %w", err)
	}

	// No predecessor — skip changelog, just signal completion
	if !prevResult.Found || prevResult.CommitInfo == nil || prevResult.CommitInfo.Sha == "" {
		return nil
	}

	// Step 2: Wait for predecessor to be enriched (poll with timeout)
	if prevResult.CommitInfo.EnrichedAt == "" {
		enriched, err := waitForPredecessorEnrichment(ctx, actCtx, changelogActs, prevResult.ID)
		if err != nil {
			return err
		}
		if !enriched {
			// Timeout: mark as pending_predecessor and move on
			return markPendingPredecessor(ctx, actCtx, changelogActs, input)
		}
		// Re-read the previous version's commit data now that it's enriched
		err = workflow.ExecuteActivity(actCtx, changelogActs.GetPreviousVersionCommit, activity.GetPreviousVersionCommitInput{
			ArtifactID: input.ArtifactID,
			SortOrder:  input.SortOrder,
		}).Get(ctx, &prevResult)
		if err != nil {
			return fmt.Errorf("re-reading previous version: %w", err)
		}
	}

	// Determine the repo URL for this version.
	// Prefer the enriched Repository (set during Phase 1), fall back to input.Repository.
	currentRepo := input.Repository
	if currentRepo == "" && len(input.GitRepositories) > 0 {
		// Use the same repo as the previous version if available,
		// otherwise use the first registered repo.
		if prevResult.CommitInfo.Repository != "" {
			currentRepo = prevResult.CommitInfo.Repository
		} else {
			currentRepo = input.GitRepositories[0]
		}
	}

	// Step 3: Check for repo transition (different repos → skip main changelog)
	prevRepo := prevResult.CommitInfo.Repository
	sameRepo := prevRepo != "" && currentRepo != "" && prevRepo == currentRepo

	// Step 4: Compute main repo changelog
	var mainCommits []domain.CommitSummary
	if sameRepo {
		var cloneOut activity.EnsureRepoClonedOutput
		err = workflow.ExecuteLocalActivity(localCtx, gitActs.ResolveRepo, activity.ResolveRepoInput{
			RepoURL: currentRepo,
		}).Get(ctx, &cloneOut)
		if err != nil {
			return fmt.Errorf("resolving repo for changelog: %w", err)
		}

		var changelogOut activity.ComputeChangelogOutput
		err = workflow.ExecuteLocalActivity(localCtx, gitActs.ComputeChangelog, activity.ComputeChangelogInput{
			RepoPath: cloneOut.LocalPath,
			FromSHA:  prevResult.CommitInfo.Sha,
			ToSHA:    input.CommitSha,
		}).Get(ctx, &changelogOut)
		if err != nil {
			// Git log failed (e.g., unrelated histories). Store changelog without main commits.
			mainCommits = nil
		} else {
			mainCommits = toCommitSummaries(changelogOut.Commits, currentRepo)
		}
	}

	// Step 5: Compute submodule changelogs
	subChangelogs := computeSubmoduleChangelogs(ctx, localCtx, gitActs, currentRepo, input.CommitSha, prevResult)

	// Step 6: Build and store changelog on the enriched commit
	changelog := &domain.Changelog{
		PreviousVersion:     prevResult.CommitInfo.Sha,
		Commits:             mainCommits,
		SubmoduleChangelogs: subChangelogs,
	}

	// Read current enriched commit to preserve existing data
	err = storeChangelogOnVersion(ctx, actCtx, changelogActs, input.VersionID, changelog)
	if err != nil {
		return fmt.Errorf("storing changelog: %w", err)
	}

	return nil
}

func waitForPredecessorEnrichment(
	ctx workflow.Context,
	actCtx workflow.Context,
	acts *activity.ChangelogActivities,
	prevVersionID int64,
) (bool, error) {
	for attempt := 0; attempt < changelogPollMaxAttempts; attempt++ {
		var enriched bool
		err := workflow.ExecuteActivity(actCtx, acts.CheckPreviousVersionEnriched, activity.CheckPreviousVersionEnrichedInput{
			VersionID: prevVersionID,
		}).Get(ctx, &enriched)
		if err != nil {
			return false, fmt.Errorf("checking predecessor enrichment: %w", err)
		}
		if enriched {
			return true, nil
		}
		if err := workflow.Sleep(ctx, changelogPollInterval); err != nil {
			return false, err
		}
	}
	return false, nil
}

func markPendingPredecessor( //nolint:gocritic // matches workflow signature
	ctx workflow.Context,
	actCtx workflow.Context,
	acts *activity.ChangelogActivities,
	input ChangelogVersionInput, //nolint:gocritic // matches workflow signature
) error {
	pending := domain.CommitInfo{
		Sha:             input.CommitSha,
		Repository:      input.Repository,
		EnrichedAt:      workflow.Now(ctx).UTC().Format("2006-01-02T15:04:05Z"),
		ChangelogStatus: "pending_predecessor",
	}
	_ = workflow.ExecuteActivity(actCtx, acts.StoreEnrichedCommit, activity.StoreEnrichedCommitInput{
		VersionID:  input.VersionID,
		CommitInfo: pending,
	}).Get(ctx, nil)
	return nil
}

func storeChangelogOnVersion(
	ctx workflow.Context,
	actCtx workflow.Context,
	acts *activity.ChangelogActivities,
	versionID int64,
	changelog *domain.Changelog,
) error {
	// We need to read-modify-write the commit_body to add the changelog
	// to the already-enriched data. The StoreEnrichedCommit activity handles this
	// by fully replacing the commit_body, so we need the current enriched state.
	//
	// For simplicity, we re-read via GetPreviousVersionCommit-style query
	// but for our own version. Instead, we store the changelog by updating
	// just the commit_body with the changelog field set.
	//
	// Note: The enrichment phase already stored the full CommitInfo. We need to
	// preserve that data and just add the changelog. Since we're in a workflow
	// and can't easily do a read-modify-write atomically via activities, we
	// accept that the StoreEnrichedCommit will re-write the full blob.
	// The enrichment data is immutable at this point (EnrichVersionWorkflow
	// already completed), so this is safe.

	// We'll need a dedicated activity to update just the changelog field.
	// For now, use a lightweight approach: store the changelog as part of a
	// "changelog update" that reads the current commit_body and merges.
	return workflow.ExecuteActivity(actCtx, acts.StoreChangelog, activity.StoreChangelogInput{
		VersionID: versionID,
		Changelog: *changelog,
	}).Get(ctx, nil)
}

// maxSubmoduleDepth limits recursive submodule changelog resolution.
const maxSubmoduleDepth = 3

func computeSubmoduleChangelogs(
	ctx workflow.Context,
	localCtx workflow.Context,
	gitActs *activity.GitActivities,
	repoURL string,
	commitSha string,
	prevResult activity.GetPreviousVersionCommitOutput,
) map[string]*domain.Changelog {
	return computeSubmoduleChangelogsWithDepth(ctx, localCtx, gitActs, repoURL, commitSha, prevResult, 0)
}

func computeSubmoduleChangelogsWithDepth(
	ctx workflow.Context,
	localCtx workflow.Context,
	gitActs *activity.GitActivities,
	repoURL string,
	commitSha string,
	prevResult activity.GetPreviousVersionCommitOutput,
	depth int,
) map[string]*domain.Changelog {
	if depth >= maxSubmoduleDepth {
		return nil
	}
	if prevResult.CommitInfo == nil || len(prevResult.CommitInfo.Submodules) == 0 {
		return nil
	}

	// Build lookup: repo URL → previous SHA
	prevSubSHAs := make(map[string]string)
	for _, sub := range prevResult.CommitInfo.Submodules {
		prevSubSHAs[sub.Repository] = sub.Sha
	}

	// We need the current version's submodule SHAs too. These should have been
	// stored during enrichment. For now, we resolve them again from git.
	var cloneOut activity.EnsureRepoClonedOutput
	err := workflow.ExecuteLocalActivity(localCtx, gitActs.ResolveRepo, activity.ResolveRepoInput{
		RepoURL: repoURL,
	}).Get(ctx, &cloneOut)
	if err != nil {
		return nil
	}

	var submoduleResult activity.ResolveSubmodulesOutput
	readOpts := workflow.LocalActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
		RetryPolicy:         &temporal.RetryPolicy{MaximumAttempts: 3},
	}
	readCtx := workflow.WithLocalActivityOptions(ctx, readOpts)
	err = workflow.ExecuteLocalActivity(readCtx, gitActs.ResolveSubmodules, activity.ResolveSubmodulesInput{
		RepoPath: cloneOut.LocalPath,
		Sha:      commitSha,
	}).Get(ctx, &submoduleResult)
	if err != nil {
		return nil
	}

	result := make(map[string]*domain.Changelog)

	for _, sub := range submoduleResult.Submodules {
		prevSHA, ok := prevSubSHAs[sub.URL]
		if !ok || prevSHA == sub.Sha {
			continue // no change or new submodule
		}

		// Resolve submodule repo path (already cloned during enrichment)
		var subClone activity.EnsureRepoClonedOutput
		err := workflow.ExecuteLocalActivity(localCtx, gitActs.ResolveRepo, activity.ResolveRepoInput{
			RepoURL: sub.URL,
		}).Get(ctx, &subClone)
		if err != nil {
			continue
		}

		var changelogOut activity.ComputeChangelogOutput
		err = workflow.ExecuteLocalActivity(localCtx, gitActs.ComputeChangelog, activity.ComputeChangelogInput{
			RepoPath: subClone.LocalPath,
			FromSHA:  prevSHA,
			ToSHA:    sub.Sha,
		}).Get(ctx, &changelogOut)
		if err != nil {
			continue
		}

		if len(changelogOut.Commits) > 0 {
			subChangelog := &domain.Changelog{
				PreviousVersion: prevSHA,
				Commits:         toCommitSummaries(changelogOut.Commits, sub.URL),
			}

			// Recursively resolve nested submodules for this submodule repo
			var nestedSubmodules activity.ResolveSubmodulesOutput
			_ = workflow.ExecuteLocalActivity(readCtx, gitActs.ResolveSubmodules, activity.ResolveSubmodulesInput{
				RepoPath: subClone.LocalPath,
				Sha:      sub.Sha,
			}).Get(ctx, &nestedSubmodules)

			if len(nestedSubmodules.Submodules) > 0 {
				// Build a fake prevResult with the previous submodule's submodule SHAs
				var prevNestedSubmodules activity.ResolveSubmodulesOutput
				_ = workflow.ExecuteLocalActivity(readCtx, gitActs.ResolveSubmodules, activity.ResolveSubmodulesInput{
					RepoPath: subClone.LocalPath,
					Sha:      prevSHA,
				}).Get(ctx, &prevNestedSubmodules)

				if len(prevNestedSubmodules.Submodules) > 0 {
					// Convert to SubmoduleCommit for the nested prev
					var nestedPrevSubs []domain.SubmoduleCommit
					for _, ns := range prevNestedSubmodules.Submodules {
						nestedPrevSubs = append(nestedPrevSubs, domain.SubmoduleCommit{
							Repository: ns.URL,
							Sha:        ns.Sha,
						})
					}
					nestedPrev := activity.GetPreviousVersionCommitOutput{
						Found: true,
						CommitInfo: &domain.CommitInfo{
							Sha:        prevSHA,
							Repository: sub.URL,
							Submodules: nestedPrevSubs,
						},
					}
					nested := computeSubmoduleChangelogsWithDepth(ctx, localCtx, gitActs, sub.URL, sub.Sha, nestedPrev, depth+1)
					if len(nested) > 0 {
						subChangelog.SubmoduleChangelogs = nested
					}
				}
			}

			result[sub.URL] = subChangelog
		}
	}

	if len(result) == 0 {
		return nil
	}
	return result
}

func toCommitSummaries(commits []activity.ChangelogCommit, repoURL string) []domain.CommitSummary {
	summaries := make([]domain.CommitSummary, len(commits))
	for i, c := range commits {
		summaries[i] = domain.CommitSummary{
			Sha:     c.Sha,
			URL:     domain.CommitURL(repoURL, c.Sha),
			Message: c.Message,
			Author: &domain.CommitAuthor{
				Name:  c.AuthorName,
				Email: c.AuthorEmail,
			},
			CommitDate: c.CommitDate,
		}
	}
	return summaries
}
