# Phase 1 Tasks: Establish Public API Boundary

**Issue:** IW-126
**Phase:** 1 of 3
**Goal:** Reorganize .iw/core/ by architectural responsibility (FCIS pattern)

## Setup

- [ ] [setup] Create directory structure: model/, adapters/, output/, dashboard/
- [ ] [setup] Create ARCHITECTURE.md with structure documentation

## Model Layer (Pure Domain Types)

- [ ] [impl] Move pure value types to model/: IssueId, Issue, IssueData, Config
- [ ] [impl] Move pure value types to model/: ConfigRepository, Constants, WorktreePath, WorktreePriority
- [ ] [impl] Move pure value types to model/: WorktreeRegistration, ApiToken, GitStatus, ReviewState
- [ ] [impl] Move pure value types to model/: PhaseInfo, WorkflowProgress, PullRequestData
- [ ] [impl] Move pure value types to model/: ServerConfig, ServerStatus, ServerState, CacheConfig, DeletionSafety

## Adapters Layer (I/O Operations)

- [ ] [impl] Move I/O adapters to adapters/: Git, GitWorktree, Process, Prompt
- [ ] [impl] Move I/O adapters to adapters/: Tmux, CommandRunner, Log
- [ ] [impl] Move API clients to adapters/: GitHubClient, LinearClient, GitLabClient, YouTrackClient

## Output Layer (CLI Presentation)

- [ ] [impl] Move CLI output modules to output/: Output, IssueFormatter
- [ ] [impl] Move CLI output modules to output/: MarkdownRenderer, TimestampFormatter

## Dashboard Layer (Server Internals)

- [ ] [impl] Move dashboard services to dashboard/: CaskServer, DashboardService
- [ ] [impl] Move cache services to dashboard/: IssueCacheService, PullRequestCacheService
- [ ] [impl] Move cache types to dashboard/: CachedIssue, CachedPR, CachedProgress, CachedReviewState
- [ ] [impl] Move server services to dashboard/: ServerClient, ServerConfigRepository, ServerLifecycleService
- [ ] [impl] Move server services to dashboard/: ServerStateService, StateRepository, ReviewStateService
- [ ] [impl] Move workflow services to dashboard/: WorkflowProgressService, WorktreeCardService
- [ ] [impl] Move worktree services to dashboard/: WorktreeListSync, WorktreeListView, WorktreeRegistrationService, WorktreeUnregistrationService
- [ ] [impl] Move utility services to dashboard/: GitStatusService, GitHubHookDoctor, DoctorChecks
- [ ] [impl] Move utility services to dashboard/: ArtifactService, FeedbackParser, MarkdownTaskParser
- [ ] [impl] Move utility services to dashboard/: IssueSearchService, IssueSearchResult, PathValidator, RefreshThrottle, ProcessManager
- [ ] [impl] Move existing subdirectories to dashboard/: application/, domain/, infrastructure/, presentation/

## Import Updates

- [ ] [impl] Update imports in .iw/commands/*.scala to use new package paths
- [ ] [impl] Update internal cross-references within .iw/core/ modules
- [ ] [impl] Update test imports in .iw/core/test/

## Verification

- [ ] [test] Verify compilation: scala-cli compile .iw/core/
- [ ] [test] Run full test suite: ./iw test
- [ ] [verify] Manual test: ./iw issue IW-126 (uses model + adapters + output)
- [ ] [verify] Manual test: ./iw doctor (uses adapters)
- [ ] [verify] Manual test: ./iw worktree list (uses model + adapters)
- [ ] [verify] Manual test: ./iw dashboard (uses dashboard internals)

## Notes

- Move files incrementally with compile checks after each batch
- Keep module filenames identical - only package paths change
- model/ must remain pure with no dependencies on other core directories
- adapters/ may depend on model/
- output/ may depend on model/
- dashboard/ may depend on all others
