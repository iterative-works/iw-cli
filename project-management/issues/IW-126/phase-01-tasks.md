# Phase 1 Tasks: Establish Public API Boundary

**Issue:** IW-126
**Phase:** 1 of 3
**Goal:** Reorganize .iw/core/ by architectural responsibility (FCIS pattern)

## Setup

- [x] [setup] Create directory structure: model/, adapters/, output/, dashboard/
- [x] [setup] Create ARCHITECTURE.md with structure documentation

## Model Layer (Pure Domain Types)

- [x] [impl] Move pure value types to model/: IssueId, Issue, IssueData, Config
- [x] [impl] Move pure value types to model/: ConfigRepository, Constants, WorktreePath, WorktreePriority
- [x] [impl] Move pure value types to model/: WorktreeRegistration, ApiToken, GitStatus, ReviewState
- [x] [impl] Move pure value types to model/: PhaseInfo, WorkflowProgress, PullRequestData
- [x] [impl] Move pure value types to model/: ServerConfig, ServerStatus, ServerState, CacheConfig, DeletionSafety

## Adapters Layer (I/O Operations)

- [x] [impl] Move I/O adapters to adapters/: Git, GitWorktree, Process, Prompt
- [x] [impl] Move I/O adapters to adapters/: Tmux, CommandRunner, Log
- [x] [impl] Move API clients to adapters/: GitHubClient, LinearClient, GitLabClient, YouTrackClient

## Output Layer (CLI Presentation)

- [x] [impl] Move CLI output modules to output/: Output, IssueFormatter
- [x] [impl] Move CLI output modules to output/: MarkdownRenderer, TimestampFormatter

## Dashboard Layer (Server Internals)

- [x] [impl] Move dashboard services to dashboard/: CaskServer, DashboardService
- [x] [impl] Move cache services to dashboard/: IssueCacheService, PullRequestCacheService
- [x] [impl] Move cache types to dashboard/: CachedIssue, CachedPR, CachedProgress, CachedReviewState
- [x] [impl] Move server services to dashboard/: ServerClient, ServerConfigRepository, ServerLifecycleService
- [x] [impl] Move server services to dashboard/: ServerStateService, StateRepository, ReviewStateService
- [x] [impl] Move workflow services to dashboard/: WorkflowProgressService, WorktreeCardService
- [x] [impl] Move worktree services to dashboard/: WorktreeListSync, WorktreeListView, WorktreeRegistrationService, WorktreeUnregistrationService
- [x] [impl] Move utility services to dashboard/: GitStatusService, GitHubHookDoctor, DoctorChecks
- [x] [impl] Move utility services to dashboard/: ArtifactService, FeedbackParser, MarkdownTaskParser
- [x] [impl] Move utility services to dashboard/: IssueSearchService, IssueSearchResult, PathValidator, RefreshThrottle, ProcessManager
- [x] [impl] Move existing subdirectories to dashboard/: application/, domain/, infrastructure/, presentation/

## Import Updates

- [x] [impl] Update imports in .iw/commands/*.scala to use new package paths
- [x] [impl] Update internal cross-references within .iw/core/ modules
- [x] [impl] Update test imports in .iw/core/test/

## Verification

- [x] [test] Verify compilation: scala-cli compile .iw/core/
- [x] [test] Run full test suite: ./iw test
- [x] [verify] Manual test: ./iw issue IW-126 (uses model + adapters + output)
- [x] [verify] Manual test: ./iw doctor (uses adapters)
- [x] [verify] Manual test: ./iw register (uses model + adapters)
- [x] [verify] Manual test: ./iw dashboard (uses dashboard internals)

## Notes

- Move files incrementally with compile checks after each batch
- Keep module filenames identical - only package paths change
- model/ must remain pure with no dependencies on other core directories
- adapters/ may depend on model/
- output/ may depend on model/
- dashboard/ may depend on all others
