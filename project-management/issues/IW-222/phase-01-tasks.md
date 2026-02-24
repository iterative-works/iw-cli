# Phase 01 Tasks: Domain Layer — model/ extractions and value objects

## Setup
- [ ] Verify all existing tests pass before starting (`./iw test unit`) to establish a clean baseline

## Tests

### ProjectPath tests
- [ ] Create `.iw/core/test/ProjectPathTest.scala` with tests for `model.ProjectPath.deriveMainProjectPath`: standard issue ID suffix (IW-79), Linear format (IWLE-123), GitHub numeric format (123), multi-digit numbers (ABC-9999), project names with hyphens, single letter prefix (A-123), returns None for path without issue ID, returns None for path with only project name. Mirror the 8 derivation tests from `MainProjectTest` (lines 51-89) but call `ProjectPath.deriveMainProjectPath` instead of `MainProject.deriveMainProjectPath`

### ServerStateCodec tests
- [ ] Create `.iw/core/test/ServerStateCodecTest.scala` with roundtrip serialization tests using `ServerStateCodec` givens directly (not via `StateRepository`): (1) full `StateJson` with all 5 cache types populated (worktrees, issueCache, progressCache, prCache, reviewStateCache), (2) empty `StateJson` with only required worktrees field, (3) `Instant` timestamp preservation through serialize/deserialize, (4) `PRState` enum roundtrip for Open/Merged/Closed, (5) backward compatibility — JSON missing optional cache fields (progressCache, prCache, reviewStateCache) parses successfully

### Value object tests
- [ ] Add tests to `ServerStateCodecTest.scala` (or a separate test file if preferred) for the 3 new value objects: construct `ProjectSummary`, `WorktreeSummary`, `WorktreeStatus` and verify JSON roundtrip via `upickle.default.write`/`read` works (confirming `derives ReadWriter` is functional). Include a test with all `Option` fields as `None` and a test with all `Option` fields populated

## Implementation

### Step 1: ProjectPath extraction
- [ ] Create `.iw/core/model/ProjectPath.scala` — package `iw.core.model`, object `ProjectPath` with `def deriveMainProjectPath(worktreePath: String): Option[String]`. Copy the logic from `MainProject.deriveMainProjectPath` (`.iw/core/dashboard/domain/MainProject.scala` lines 29-54). Add PURPOSE header
- [ ] Modify `.iw/core/dashboard/domain/MainProject.scala` — change `deriveMainProjectPath` body to delegate: `ProjectPath.deriveMainProjectPath(worktreePath)`. Add import for `iw.core.model.ProjectPath`
- [ ] Run `ProjectPathTest` and `MainProjectTest` — both must pass

### Step 2: ServerStateCodec extraction
- [ ] Create `.iw/core/model/ServerStateCodec.scala` — package `iw.core.model`, object `ServerStateCodec` containing all 16 `given ReadWriter[X]` instances (Instant, WorktreeRegistration, IssueData, CachedIssue, PhaseInfo, WorkflowProgress, CachedProgress, PRState, PullRequestData, CachedPR, ReviewArtifact, Display, Badge, TaskList, ReviewState, CachedReviewState) plus the `StateJson` case class and its `given ReadWriter[StateJson]`. Copy codec definitions from `StateRepository.scala` lines 14-54. Add PURPOSE header
- [ ] Modify `.iw/core/dashboard/StateRepository.scala` — remove all local `given ReadWriter[X]` definitions (lines 14-54) and the `StateJson` case class. Add `import iw.core.model.ServerStateCodec.{given, *}` at the top. The `read()` and `write()` methods remain unchanged; they now use the imported codecs and `ServerStateCodec.StateJson`
- [ ] Run `ServerStateCodecTest` and `StateRepositoryTest` — both must pass

### Step 3: Move ServerLifecycleService to model/
- [ ] Create `.iw/core/model/ServerLifecycleService.scala` — copy from `.iw/core/dashboard/ServerLifecycleService.scala`, change package from `iw.core.dashboard` to `iw.core.model`. Imports stay the same (they already reference `iw.core.model.*`). Add PURPOSE header
- [ ] Modify `.iw/core/dashboard/ServerLifecycleService.scala` — replace entire implementation with a re-export: change the file to just `package iw.core.dashboard` + `export iw.core.model.ServerLifecycleService` (so existing `dashboard` internal callers still compile)
- [ ] Run `ServerLifecycleServiceTest` — all 16 tests must pass (tests import from `iw.core.dashboard.ServerLifecycleService` which re-exports from `model/`)

### Step 4: Move FeedbackParser to model/
- [ ] Create `.iw/core/model/FeedbackParser.scala` — copy from `.iw/core/dashboard/FeedbackParser.scala`, change package from `iw.core.dashboard` to `iw.core.model`. Import of `iw.core.model.Constants` stays the same. Add PURPOSE header
- [ ] Modify `.iw/core/dashboard/FeedbackParser.scala` — replace entire implementation with a re-export: `package iw.core.dashboard` + `export iw.core.model.FeedbackParser` (so existing callers like `feedback.scala`, `GitHubClient.scala`, `GitLabClient.scala` still compile)
- [ ] Run `FeedbackParserTest` — all 16 tests must pass

### Step 5: Create value objects
- [ ] Create `.iw/core/model/ProjectSummary.scala` — package `iw.core.model`, `case class ProjectSummary(name: String, path: String, trackerType: String, team: String, worktreeCount: Int) derives ReadWriter`. Add `import upickle.default.*` and PURPOSE header. Note: this is a different type from the existing `dashboard/presentation/views/ProjectSummary` — it's the CLI output value object
- [ ] Create `.iw/core/model/WorktreeSummary.scala` — package `iw.core.model`, `case class WorktreeSummary(issueId: String, path: String, issueTitle: Option[String], issueStatus: Option[String], prState: Option[String], reviewDisplay: Option[String], needsAttention: Boolean) derives ReadWriter`. Add `import upickle.default.*` and PURPOSE header
- [ ] Create `.iw/core/model/WorktreeStatus.scala` — package `iw.core.model`, `case class WorktreeStatus(issueId: String, path: String, branchName: Option[String], gitClean: Option[Boolean], issueTitle: Option[String], issueStatus: Option[String], issueUrl: Option[String], prUrl: Option[String], prState: Option[String], prNumber: Option[Int], reviewDisplay: Option[String], reviewBadges: Option[List[String]], needsAttention: Boolean, currentPhase: Option[Int], totalPhases: Option[Int], overallProgress: Option[Int]) derives ReadWriter`. Add `import upickle.default.*` and PURPOSE header
- [ ] Run value object tests — all roundtrip serialization tests must pass

## Integration
- [ ] Run full unit test suite (`./iw test unit`) — all existing tests must pass including `MainProjectTest` (10 tests), `StateRepositoryTest` (18 tests), `ServerLifecycleServiceTest` (16 tests), `FeedbackParserTest` (16 tests), plus all new tests
- [ ] Run full E2E test suite (`./iw test e2e`) — no regressions from package changes
- [ ] Verify no code duplication: `MainProject.deriveMainProjectPath` delegates to `ProjectPath` (not copy-pasted), `StateRepository` imports codecs from `ServerStateCodec` (not re-defined), `dashboard/ServerLifecycleService.scala` and `dashboard/FeedbackParser.scala` are re-exports only (no logic)
- [ ] Verify all new files have PURPOSE headers
