# Phase 01: Domain Layer — model/ extractions and value objects

## Goals

Extract pure logic and codec definitions from `dashboard/` into `model/` so that both `dashboard/StateRepository` and the new `adapters/StateReader` (Phase 02) can share the same JSON serialization. Also create value objects that the new CLI commands (Phase 03) will use for structured output.

## Scope

### In Scope

1. **ProjectPath** — Extract `deriveMainProjectPath` pure function from `MainProject` companion object to a new `model/ProjectPath` object
2. **ServerStateCodec** — Extract all `given ReadWriter[X]` instances from `StateRepository` to a standalone `model/ServerStateCodec` object
3. **ServerLifecycleService** — Move from `dashboard/ServerLifecycleService.scala` to `model/ServerLifecycleService.scala`, changing package from `iw.core.dashboard` to `iw.core.model`
4. **FeedbackParser** — Move from `dashboard/FeedbackParser.scala` to `model/FeedbackParser.scala`, changing package from `iw.core.dashboard` to `iw.core.model`
5. **New value objects** — Create `ProjectSummary`, `WorktreeSummary`, `WorktreeStatus` case classes in `model/`

### Out of Scope

- Moving `ServerClient`, `ServerConfigRepository`, `ProcessManager` from `dashboard/` to `adapters/` (Phase 02)
- Creating the new `StateReader` adapter (Phase 02)
- Updating import statements in `commands/` scripts — those will be updated in Phase 02 when the adapter moves happen
- New CLI commands `projects`, `worktrees`, `status` (Phase 03)
- `--prompt` flag support (Phase 04)
- Deleting the original `dashboard/ServerLifecycleService.scala` and `dashboard/FeedbackParser.scala` files — the originals remain as re-exports or are deleted in Phase 02 when command imports are updated

## Dependencies

### Required Before This Phase

Nothing. Phase 01 is the first phase and has no dependencies.

### Provides for Later Phases

- **Phase 02** depends on `ServerStateCodec` — both `StateRepository` (refactored) and `StateReader` (new) will import codec givens from `model/ServerStateCodec`
- **Phase 02** depends on `ServerLifecycleService` and `FeedbackParser` being in `model/` — after the adapter moves, commands can import everything from `model/` and `adapters/` without touching `dashboard/`
- **Phase 03** depends on `ProjectSummary`, `WorktreeSummary`, `WorktreeStatus` for command output formatting and JSON serialization
- **Phase 03** depends on `ProjectPath.deriveMainProjectPath` for grouping worktrees by main project

## Approach

### Step-by-step plan

**Step 1: Create `model/ProjectPath.scala`**

Extract the `deriveMainProjectPath` function from `MainProject` (located at `.iw/core/dashboard/domain/MainProject.scala`, lines 29-54) into a new `model/ProjectPath` object. The function is pure (String => Option[String]) with no dependencies on `MainProject` fields or `os.Path`. Leave the original in `MainProject` as a delegation to `ProjectPath.deriveMainProjectPath` to avoid breaking existing callers during this phase.

**Step 2: Create `model/ServerStateCodec.scala`**

Extract the 16 `given ReadWriter[X]` definitions from `StateRepository` (lines 15-54 of `.iw/core/dashboard/StateRepository.scala`) into a standalone object. The `StateJson` case class also needs to move since it's the wire format. After extraction, `StateRepository` will import from `ServerStateCodec` instead of defining its own givens. The `StateRepository` tests must still pass after this change.

**Step 3: Move `ServerLifecycleService` to `model/`**

Copy `.iw/core/dashboard/ServerLifecycleService.scala` to `.iw/core/model/ServerLifecycleService.scala` and change the package declaration from `iw.core.dashboard` to `iw.core.model`. The file only imports from `iw.core.model` and `java.time`, so it already belongs in `model/`. Leave the original file as a package re-export (`export iw.core.model.ServerLifecycleService`) so existing `dashboard` internal callers don't break.

**Step 4: Move `FeedbackParser` to `model/`**

Copy `.iw/core/dashboard/FeedbackParser.scala` to `.iw/core/model/FeedbackParser.scala` and change the package declaration from `iw.core.dashboard` to `iw.core.model`. It only imports `iw.core.model.Constants`, so it belongs in `model/`. Leave the original file as a package re-export so existing callers (including `adapters/GitHubClient.scala`, `adapters/GitLabClient.scala`, `commands/feedback.scala`) don't break during this phase.

**Step 5: Create value objects**

Create `model/ProjectSummary.scala`, `model/WorktreeSummary.scala`, and `model/WorktreeStatus.scala` with the case class definitions and `derives ReadWriter` for JSON serialization.

**Step 6: Write tests**

- `ProjectPathTest` — migrate the `deriveMainProjectPath` tests from `MainProjectTest` to test the new `ProjectPath` object directly
- `ServerStateCodecTest` — roundtrip serialization test for a full `ServerState` with all cache types populated
- Value object construction tests for `ProjectSummary`, `WorktreeSummary`, `WorktreeStatus`
- Verify all existing tests still pass (`MainProjectTest`, `StateRepositoryTest`, `ServerLifecycleServiceTest`, `FeedbackParserTest`)

## Files to Modify

### New Files

- `.iw/core/model/ProjectPath.scala` — Pure function `deriveMainProjectPath(worktreePath: String): Option[String]`
- `.iw/core/model/ServerStateCodec.scala` — All `given ReadWriter[X]` instances and `StateJson` case class
- `.iw/core/model/ProjectSummary.scala` — Value object for `iw projects` output
- `.iw/core/model/WorktreeSummary.scala` — Value object for `iw worktrees` output
- `.iw/core/model/WorktreeStatus.scala` — Value object for `iw status` output
- `.iw/core/test/ProjectPathTest.scala` — Unit tests for `ProjectPath.deriveMainProjectPath`
- `.iw/core/test/ServerStateCodecTest.scala` — Roundtrip serialization tests

### Modified Files

- `.iw/core/dashboard/StateRepository.scala` — Remove local `given ReadWriter[X]` definitions and `StateJson` case class; import from `model/ServerStateCodec` instead
- `.iw/core/dashboard/domain/MainProject.scala` — Delegate `deriveMainProjectPath` to `model/ProjectPath`
- `.iw/core/dashboard/ServerLifecycleService.scala` — Replace implementation with re-export from `model/`
- `.iw/core/dashboard/FeedbackParser.scala` — Replace implementation with re-export from `model/`

## Component Specifications

### `model/ProjectPath`

```scala
package iw.core.model

object ProjectPath:
  /** Derive main project path from a worktree path by stripping the issue ID suffix.
    *
    * Worktree paths follow the pattern: {mainProjectPath}-{issueId}
    * where issueId matches: [A-Z]+-[0-9]+ or just [0-9]+
    *
    * @param worktreePath The full path to the worktree directory
    * @return Some(mainProjectPath) if pattern matches, None otherwise
    */
  def deriveMainProjectPath(worktreePath: String): Option[String]
```

**Package:** `iw.core.model`
**Extracted from:** `iw.core.dashboard.domain.MainProject.deriveMainProjectPath` (lines 29-54)
**Logic:** Identical to current implementation — regex match on directory name, strip suffix

---

### `model/ServerStateCodec`

```scala
package iw.core.model

import upickle.default.*

object ServerStateCodec:
  // Instant codec
  given ReadWriter[java.time.Instant]

  // Domain model codecs
  given ReadWriter[WorktreeRegistration]
  given ReadWriter[IssueData]
  given ReadWriter[CachedIssue]
  given ReadWriter[PhaseInfo]
  given ReadWriter[WorkflowProgress]
  given ReadWriter[CachedProgress]
  given ReadWriter[PRState]
  given ReadWriter[PullRequestData]
  given ReadWriter[CachedPR]
  given ReadWriter[ReviewArtifact]
  given ReadWriter[Display]
  given ReadWriter[Badge]
  given ReadWriter[TaskList]
  given ReadWriter[ReviewState]
  given ReadWriter[CachedReviewState]

  /** Wire format for state.json file. */
  case class StateJson(
    worktrees: Map[String, WorktreeRegistration],
    issueCache: Map[String, CachedIssue] = Map.empty,
    progressCache: Map[String, CachedProgress] = Map.empty,
    prCache: Map[String, CachedPR] = Map.empty,
    reviewStateCache: Map[String, CachedReviewState] = Map.empty
  )
  given ReadWriter[StateJson]
```

**Package:** `iw.core.model`
**Extracted from:** `iw.core.dashboard.StateRepository` (lines 12-54)
**Note:** The `Instant` ReadWriter uses `bimap` (same as current), the `PRState` ReadWriter uses `bimap` with `toString`/`valueOf` (same as current), all others use `macroRW`.

---

### `model/ServerLifecycleService` (moved)

```scala
package iw.core.model

object ServerLifecycleService:
  def formatUptime(startedAt: Instant, now: Instant): String
  def formatHostsDisplay(hosts: Seq[String], port: Int): String
  def createStatus(state: ServerState, startedAt: Instant, pid: Long, port: Int): ServerStatus
  def formatSecurityWarning(analysis: SecurityAnalysis): Option[String]
```

**Package:** `iw.core.model` (was `iw.core.dashboard`)
**Imports:** `iw.core.model.{ServerState, ServerStatus, SecurityAnalysis}`, `java.time.{Instant, Duration}`
**Logic:** Identical to current implementation (67 lines, all pure)

---

### `model/FeedbackParser` (moved)

```scala
package iw.core.model

object FeedbackParser:
  val MaxTitleLength: Int = 500
  val MaxDescriptionLength: Int = 10000

  enum IssueType:
    case Bug, Feature

  object IssueType:
    def fromString(s: String): Either[String, IssueType]

  case class FeedbackRequest(title: String, description: String, issueType: IssueType)

  def getLabelIdForIssueType(issueType: IssueType): String
  def parseFeedbackArgs(args: Seq[String]): Either[String, FeedbackRequest]
```

**Package:** `iw.core.model` (was `iw.core.dashboard`)
**Imports:** `iw.core.model.Constants`
**Logic:** Identical to current implementation (100 lines, all pure)

---

### `model/ProjectSummary`

```scala
package iw.core.model

import upickle.default.*

/** Summary of a main project for `iw projects` output.
  *
  * @param name Project name (e.g., "iw-cli", "kanon")
  * @param path Absolute path to main project directory
  * @param trackerType Tracker type (e.g., "linear", "github")
  * @param team Team identifier (e.g., "IWLE", "iterative-works/iw-cli")
  * @param worktreeCount Number of active worktrees for this project
  */
case class ProjectSummary(
  name: String,
  path: String,
  trackerType: String,
  team: String,
  worktreeCount: Int
) derives ReadWriter
```

**Package:** `iw.core.model`

---

### `model/WorktreeSummary`

```scala
package iw.core.model

import upickle.default.*

/** Summary of a worktree for `iw worktrees` output.
  *
  * @param issueId Issue identifier (e.g., "IWLE-123")
  * @param path Absolute path to worktree directory
  * @param issueTitle Issue title from cache, if available
  * @param issueStatus Issue status from cache (e.g., "In Progress"), if available
  * @param prState PR state from cache (e.g., "Open", "Merged"), if available
  * @param reviewDisplay Review state display text from cache, if available
  * @param needsAttention True if review state indicates human attention needed
  */
case class WorktreeSummary(
  issueId: String,
  path: String,
  issueTitle: Option[String],
  issueStatus: Option[String],
  prState: Option[String],
  reviewDisplay: Option[String],
  needsAttention: Boolean
) derives ReadWriter
```

**Package:** `iw.core.model`

---

### `model/WorktreeStatus`

```scala
package iw.core.model

import upickle.default.*

/** Detailed status of a single worktree for `iw status` output.
  *
  * @param issueId Issue identifier (e.g., "IWLE-123")
  * @param path Absolute path to worktree directory
  * @param branchName Current git branch name
  * @param gitClean True if no uncommitted changes
  * @param issueTitle Issue title from cache, if available
  * @param issueStatus Issue status from cache, if available
  * @param issueUrl Direct link to issue in tracker, if available
  * @param prUrl PR URL, if available
  * @param prState PR state as string (e.g., "Open"), if available
  * @param prNumber PR number, if available
  * @param reviewDisplay Review state display text, if available
  * @param reviewBadges Review state badges as list of label strings, if available
  * @param needsAttention True if review state indicates human attention needed
  * @param currentPhase Current workflow phase number, if available
  * @param totalPhases Total workflow phases, if available
  * @param overallProgress Overall task completion percentage (0-100), if available
  */
case class WorktreeStatus(
  issueId: String,
  path: String,
  branchName: Option[String],
  gitClean: Option[Boolean],
  issueTitle: Option[String],
  issueStatus: Option[String],
  issueUrl: Option[String],
  prUrl: Option[String],
  prState: Option[String],
  prNumber: Option[Int],
  reviewDisplay: Option[String],
  reviewBadges: Option[List[String]],
  needsAttention: Boolean,
  currentPhase: Option[Int],
  totalPhases: Option[Int],
  overallProgress: Option[Int]
) derives ReadWriter
```

**Package:** `iw.core.model`

## Testing Strategy

### New Tests

1. **`ProjectPathTest`** — Test `deriveMainProjectPath` directly on `model.ProjectPath`:
   - Standard issue ID suffix (IW-79)
   - Linear format (IWLE-123)
   - GitHub format (numeric only: 123)
   - Multi-digit numbers
   - Project names with hyphens
   - No issue ID pattern returns None
   - Single letter prefix (A-123)
   - (Mirrors existing `MainProjectTest` derivation tests, lines 51-89)

2. **`ServerStateCodecTest`** — Roundtrip serialization via `upickle.default.write` / `upickle.default.read` using `ServerStateCodec` givens:
   - Full `StateJson` with all cache types populated (worktrees, issueCache, progressCache, prCache, reviewStateCache)
   - Empty `StateJson`
   - `Instant` timestamp preservation
   - `PRState` enum roundtrip (Open, Merged, Closed)
   - Backward compatibility: JSON missing optional caches parses successfully

3. **Value object tests** — Simple construction and equality for `ProjectSummary`, `WorktreeSummary`, `WorktreeStatus`. Verify `derives ReadWriter` works via roundtrip.

### Existing Tests That Must Pass

- `MainProjectTest` — All 10 tests (construction + derivation) must pass; derivation tests still call `MainProject.deriveMainProjectPath` which delegates to `ProjectPath`
- `StateRepositoryTest` — All 18 tests must pass; `StateRepository` now imports codecs from `ServerStateCodec`
- `ServerLifecycleServiceTest` — All 16 tests must pass; test imports updated from `iw.core.dashboard.ServerLifecycleService` to `iw.core.model.ServerLifecycleService` (or keep working via the re-export)
- `FeedbackParserTest` — All 16 tests must pass; same import update approach

### Test Execution

```bash
./iw test unit   # All unit tests including new and existing
./iw test e2e    # All E2E tests to verify no regressions
```

## Acceptance Criteria

- [ ] `model/ProjectPath.deriveMainProjectPath` exists and passes all derivation test cases
- [ ] `MainProject.deriveMainProjectPath` delegates to `ProjectPath` (not duplicated)
- [ ] `model/ServerStateCodec` contains all 16 `given ReadWriter[X]` instances plus `StateJson`
- [ ] `StateRepository` imports codecs from `ServerStateCodec` instead of defining its own
- [ ] `ServerStateCodecTest` roundtrip test passes with fully-populated `StateJson`
- [ ] `model/ServerLifecycleService` exists with package `iw.core.model`
- [ ] `dashboard/ServerLifecycleService` re-exports from `model/` (no logic duplication)
- [ ] `model/FeedbackParser` exists with package `iw.core.model`
- [ ] `dashboard/FeedbackParser` re-exports from `model/` (no logic duplication)
- [ ] `ProjectSummary`, `WorktreeSummary`, `WorktreeStatus` exist in `model/` with `derives ReadWriter`
- [ ] All existing unit tests pass: `MainProjectTest`, `StateRepositoryTest`, `ServerLifecycleServiceTest`, `FeedbackParserTest`
- [ ] All existing E2E tests pass (no regressions from package changes)
- [ ] All new files have PURPOSE headers
- [ ] No code duplication between original and extracted/moved files
