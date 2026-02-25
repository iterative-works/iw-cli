# Phase 03 Tasks: Presentation Layer — new commands (projects, worktrees, status) with --json

## Setup

- [ ] Verify all existing tests pass before starting (`./iw test unit`) to establish a clean baseline
- [ ] Verify E2E tests pass before starting (`./iw test e2e`) to confirm Phase 2 adapters are working
- [ ] Verify Phase 2 foundation exists: `StateReader.read()` in `.iw/core/adapters/StateReader.scala`, value objects (`ProjectSummary`, `WorktreeSummary`, `WorktreeStatus`) in `.iw/core/model/`, `ProjectPath.deriveMainProjectPath()` in `.iw/core/model/ProjectPath.scala`

## Formatters (TDD — tests first)

### Task 3.1: ProjectsFormatter — test and implement

**What to do:**
1. Create `.iw/core/test/ProjectsFormatterTest.scala` (package `iw.tests`, extends `munit.FunSuite`)
2. Run tests — confirm they all fail
3. Create `.iw/core/output/ProjectsFormatter.scala` (package `iw.core.output`, object `ProjectsFormatter`)
4. Run tests — confirm they all pass

**Test cases for `ProjectsFormatterTest`:**
- `"format single project shows all fields"` — construct `ProjectSummary(name = "testproject", path = "/home/user/testproject", trackerType = "linear", team = "IWLE", worktreeCount = 1)`, call `ProjectsFormatter.format(List(summary))`, assert output contains "testproject", "/home/user/testproject", "linear", "IWLE", "1 worktree" (singular)
- `"format multiple projects shows all projects"` — two `ProjectSummary` instances, assert output contains both project names
- `"format project with multiple worktrees pluralizes"` — `worktreeCount = 3`, assert output contains "3 worktrees" (plural)
- `"format project with zero worktrees"` — `worktreeCount = 0`, assert output contains "0 worktrees"
- `"format empty list shows no projects message"` — `ProjectsFormatter.format(List.empty)`, assert output contains "No projects registered."
- `"format includes section header"` — assert output contains "Registered Projects"

**Formatter signature:** `object ProjectsFormatter` with `def format(projects: List[ProjectSummary]): String`

**Implementation notes:**
- Return "No projects registered." for empty list
- Header: `=== Registered Projects ===`
- Each line: `{name}  {path}  {trackerType}  {team}  {count} worktree(s)` — use column alignment via `f"..."` string interpolation or `%-Ns` formatting
- Pluralize "worktree"/"worktrees" based on count (anything != 1 gets "worktrees")

**Dependencies:** `ProjectSummary` from Phase 1
**Acceptance criteria:** All 6 test cases pass, file has PURPOSE header, output matches spec format

---

### Task 3.2: WorktreesFormatter — test and implement

**What to do:**
1. Create `.iw/core/test/WorktreesFormatterTest.scala` (package `iw.tests`, extends `munit.FunSuite`)
2. Run tests — confirm they all fail
3. Create `.iw/core/output/WorktreesFormatter.scala` (package `iw.core.output`, object `WorktreesFormatter`)
4. Run tests — confirm they all pass

**Test cases for `WorktreesFormatterTest`:**
- `"format worktree with all fields"` — construct `WorktreeSummary(issueId = "IWLE-123", path = "/home/user/testproject-IWLE-123", issueTitle = Some("Add user login"), issueStatus = Some("In Progress"), prState = Some("Open"), reviewDisplay = Some("Waiting for review"), needsAttention = true)`, assert output contains "IWLE-123", "Add user login", "In Progress", "Open", attention indicator
- `"format worktree with no cached data"` — all Option fields are `None`, `needsAttention = false`, assert output contains issueId, does not contain attention indicator
- `"format worktree with needsAttention shows indicator"` — `needsAttention = true`, assert output contains attention marker (e.g., a warning symbol or text like "Needs attention")
- `"format worktree without needsAttention omits indicator"` — `needsAttention = false`, assert output does NOT contain attention marker
- `"format multiple worktrees"` — two summaries, assert output contains both issue IDs
- `"format empty list shows no worktrees message"` — `WorktreesFormatter.format(List.empty)`, assert output contains "No worktrees found."
- `"format truncates long issue titles"` — title longer than 40 chars, assert output contains truncated version with ellipsis
- `"format includes section header"` — assert output contains "Worktrees"

**Formatter signature:** `object WorktreesFormatter` with `def format(worktrees: List[WorktreeSummary]): String`

**Implementation notes:**
- Return "No worktrees found." for empty list
- Header: `=== Worktrees ===`
- Each line: `{issueId}  {title (truncated)}  {issueStatus}  PR: {prState}  {attention}`
- Truncate titles longer than ~40 chars with "..."
- Only show PR section if `prState` is `Some`
- Only show attention indicator if `needsAttention == true`

**Dependencies:** `WorktreeSummary` from Phase 1
**Acceptance criteria:** All 8 test cases pass, file has PURPOSE header

---

### Task 3.3: StatusFormatter — test and implement

**What to do:**
1. Create `.iw/core/test/StatusFormatterTest.scala` (package `iw.tests`, extends `munit.FunSuite`)
2. Run tests — confirm they all fail
3. Create `.iw/core/output/StatusFormatter.scala` (package `iw.core.output`, object `StatusFormatter`)
4. Run tests — confirm they all pass

**Test cases for `StatusFormatterTest`:**
- `"format status with all fields populated"` — full `WorktreeStatus` with every Option as `Some(...)`, assert output contains all sections: Git (branch, clean), Issue (status, URL), Pull Request (state, number, URL), Review (display, badges, attention), Progress (phase, percentage)
- `"format status with only required fields"` — `WorktreeStatus` with all Options as `None` and `needsAttention = false`, assert output contains issueId and path but NOT "Git", "Issue", "Pull Request", "Review", "Progress" section headers
- `"format status with git section"` — `branchName = Some("IWLE-123")`, `gitClean = Some(true)`, assert output contains "Git", "Branch", "IWLE-123", "Clean"
- `"format status with dirty git"` — `gitClean = Some(false)`, assert output contains "Uncommitted changes" or "Dirty"
- `"format status with issue section"` — `issueTitle`, `issueStatus`, `issueUrl` all `Some(...)`, assert output contains "Issue" section with all values
- `"format status with PR section"` — `prUrl`, `prState`, `prNumber` all `Some(...)`, assert output contains "Pull Request" section with state, number, URL
- `"format status with review badges"` — `reviewBadges = Some(List("Phase 2", "Tests passing"))`, assert output contains both badge labels
- `"format status with needsAttention"` — `needsAttention = true`, assert output contains attention indicator
- `"format status with progress"` — `currentPhase = Some(2)`, `totalPhases = Some(4)`, `overallProgress = Some(65)`, assert output contains "2/4" and "65%"
- `"format status omits sections with no data"` — only `issueTitle` and `issueStatus` set (no git, no PR, no review, no progress), assert output contains "Issue" section but NOT "Git", "Pull Request", "Review", "Progress"

**Formatter signature:** `object StatusFormatter` with `def format(status: WorktreeStatus): String`

**Implementation notes:**
- Header: `=== {issueId}: {issueTitle} ===` (omit title if None: `=== {issueId} ===`)
- Sections are conditional — only render if at least one field in the section is `Some`:
  - **Git section:** shown if `branchName` or `gitClean` is `Some`. Display: "Branch: {name}", "Status: Clean" / "Status: Uncommitted changes"
  - **Issue section:** shown if `issueTitle` or `issueStatus` or `issueUrl` is `Some`. Display: "Status: {status}", "URL: {url}"
  - **Pull Request section:** shown if `prState` or `prUrl` or `prNumber` is `Some`. Display: "State: {state}", "PR: #{number}", "URL: {url}"
  - **Review section:** shown if `reviewDisplay` or `reviewBadges` is `Some` or `needsAttention` is `true`. Display: "Status: {display}", "Badges: [{badge1}] [{badge2}]", attention indicator
  - **Progress section:** shown if `currentPhase` or `overallProgress` is `Some`. Display: "Phase: {current}/{total}", "Overall: {pct}%"
- Use `Output.section`-style formatting (indented key-value pairs)

**Dependencies:** `WorktreeStatus` from Phase 1
**Acceptance criteria:** All 10 test cases pass, file has PURPOSE header

## Commands

### Task 3.4: `iw projects` command

**What to do:**
1. Create `.iw/commands/projects.scala` with PURPOSE header and USAGE comment

**Command signature:**
```scala
// PURPOSE: Lists all registered projects from server state
// PURPOSE: Supports --json flag for machine-readable output
// USAGE: iw projects [--json]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def projects(args: String*): Unit =
```

**Argument parsing:**
- Extract `--json` flag from `args`
- No positional arguments

**Logic flow:**
1. Parse args: extract `--json` flag
2. Call `StateReader.read()` — on `Left(error)`, print error via `Output.error` and `sys.exit(1)`
3. Group `state.worktrees.values` by `ProjectPath.deriveMainProjectPath(wt.path)` — worktrees whose path doesn't match any pattern get grouped under their own path as a standalone entry
4. For each group (main project path):
   - Derive project name: last directory component of the main path (e.g., `/home/user/testproject` -> `testproject`)
   - Read project config: `ConfigFileRepository.read(os.Path(mainPath) / Constants.Paths.IwDir / "config.conf")` — if config exists, use `trackerType` and `team`; if missing, use `"unknown"` for both
   - Count worktrees in the group
   - Build `ProjectSummary(name, mainPath, trackerType, team, worktreeCount)`
5. If `--json`: `println(upickle.default.write(summaries))`
6. Else: `println(ProjectsFormatter.format(summaries))`

**Error handling:**
- `StateReader.read()` returns `Left` → `Output.error(msg)` + `sys.exit(1)`
- Empty state (no worktrees) → format empty list (shows "No projects registered." or `[]`)
- Config file missing for a project → use "unknown" for trackerType and team

**Dependencies:** `StateReader`, `ProjectPath`, `ConfigFileRepository`, `ProjectSummary`, `ProjectsFormatter`, `Output`, `Constants`
**Acceptance criteria:** Command runs successfully with `./iw projects` and `./iw projects --json`, handles empty state gracefully, has PURPOSE header

---

### Task 3.5: `iw worktrees` command

**What to do:**
1. Create `.iw/commands/worktrees.scala` with PURPOSE header and USAGE comment

**Command signature:**
```scala
// PURPOSE: Lists worktrees for current project or all projects
// PURPOSE: Supports --json and --all flags for machine-readable and cross-project output
// USAGE: iw worktrees [--all] [--json]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def worktrees(args: String*): Unit =
```

**Argument parsing:**
- Extract `--json` flag from `args`
- Extract `--all` flag from `args`

**Logic flow:**
1. Parse args: extract `--json` and `--all` flags
2. Call `StateReader.read()` — on `Left(error)`, print error and exit
3. Get all worktree registrations: `state.worktrees.values.toList`
4. If not `--all`:
   - Determine current project's main path: try `ProjectPath.deriveMainProjectPath(os.pwd.toString)`, falling back to `os.pwd.toString` itself (we may be in the main project dir)
   - Filter worktrees to those whose `ProjectPath.deriveMainProjectPath(wt.path)` matches the current project's main path (also include worktrees at the main path itself)
5. For each worktree registration, build `WorktreeSummary`:
   - `issueId` = `wt.issueId`
   - `path` = `wt.path`
   - `issueTitle` = `state.issueCache.get(wt.issueId).map(_.data.title)`
   - `issueStatus` = `state.issueCache.get(wt.issueId).map(_.data.status)`
   - `prState` = `state.prCache.get(wt.issueId).map(_.pr.stateBadgeText)`
   - `reviewDisplay` = `state.reviewStateCache.get(wt.issueId).flatMap(_.state.display.map(_.text))`
   - `needsAttention` = `state.reviewStateCache.get(wt.issueId).flatMap(_.state.needsAttention).getOrElse(false)`
6. Sort summaries by `issueId`
7. If `--json`: `println(upickle.default.write(summaries))`
8. Else: `println(WorktreesFormatter.format(summaries))`

**Error handling:**
- `StateReader.read()` returns `Left` → `Output.error(msg)` + `sys.exit(1)`
- No worktrees match current project → format empty list (shows "No worktrees found." or `[]`)
- Current directory is not a recognized project → show all worktrees with a warning, or show empty

**Dependencies:** `StateReader`, `ProjectPath`, `WorktreeSummary`, `WorktreesFormatter`, `Output`, `ServerState` caches
**Acceptance criteria:** Command runs successfully with `./iw worktrees`, `./iw worktrees --all`, and `./iw worktrees --json`, handles empty state gracefully, has PURPOSE header

---

### Task 3.6: `iw status` command

**What to do:**
1. Create `.iw/commands/status.scala` with PURPOSE header and USAGE comment

**Command signature:**
```scala
// PURPOSE: Shows detailed status for a specific worktree
// PURPOSE: Combines live git state with cached issue, PR, review, and progress data
// USAGE: iw status [issue-id] [--json]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def status(args: String*): Unit =
```

**Argument parsing:**
- Extract `--json` flag from `args`
- First non-flag argument is the optional issue ID
- If no issue ID argument: infer from current branch (same pattern as `open.scala`)

**Logic flow:**
1. Parse args: extract `--json` flag, optional issue ID
2. Load config: `ConfigFileRepository.read(os.pwd / Constants.Paths.IwDir / "config.conf")` — needed for team prefix when parsing issue ID. If config missing, try to infer issue ID without team prefix
3. Resolve issue ID:
   - If provided as arg: `IssueId.parse(rawId, teamPrefix)` (same pattern as `open.scala` lines 20-27)
   - If not provided: `GitAdapter.getCurrentBranch(os.pwd).flatMap(IssueId.fromBranch(_))` (same pattern as `open.scala` lines 37-39)
   - On failure: `Output.error(msg)` + `sys.exit(1)`
4. Call `StateReader.read()` — on `Left(error)`, print error and exit
5. Find worktree: `state.worktrees.get(issueId.value)` — if `None`, `Output.error(s"Worktree not found in state for ${issueId.value}")` + `sys.exit(1)`
6. Get live git state:
   - `branchName` = `GitAdapter.getCurrentBranch(os.Path(wt.path)).toOption`
   - `gitClean` = `GitAdapter.hasUncommittedChanges(os.Path(wt.path)).map(!_).toOption` (invert: `hasUncommittedChanges` returns true for dirty)
7. Populate from caches:
   - `issueTitle` = `state.issueCache.get(issueId.value).map(_.data.title)`
   - `issueStatus` = `state.issueCache.get(issueId.value).map(_.data.status)`
   - `issueUrl` = `state.issueCache.get(issueId.value).map(_.data.url)`
   - `prUrl` = `state.prCache.get(issueId.value).map(_.pr.url)`
   - `prState` = `state.prCache.get(issueId.value).map(_.pr.stateBadgeText)`
   - `prNumber` = `state.prCache.get(issueId.value).map(_.pr.number)`
   - `reviewDisplay` = `state.reviewStateCache.get(issueId.value).flatMap(_.state.display.map(_.text))`
   - `reviewBadges` = `state.reviewStateCache.get(issueId.value).flatMap(_.state.badges.map(_.map(_.label)))`
   - `needsAttention` = `state.reviewStateCache.get(issueId.value).flatMap(_.state.needsAttention).getOrElse(false)`
   - `currentPhase` = `state.progressCache.get(issueId.value).flatMap(_.progress.currentPhase)`
   - `totalPhases` = `state.progressCache.get(issueId.value).map(_.progress.totalPhases)`
   - `overallProgress` = `state.progressCache.get(issueId.value).map(_.progress.overallPercentage)`
8. Build `WorktreeStatus(issueId.value, wt.path, branchName, gitClean, ...)`
9. If `--json`: `println(upickle.default.write(status))`
10. Else: `println(StatusFormatter.format(status))`

**Error handling:**
- Config missing → proceed without team prefix (try to parse issue ID as-is)
- Issue ID resolution fails → `Output.error` + exit 1 with usage hint
- `StateReader.read()` returns `Left` → `Output.error(msg)` + `sys.exit(1)`
- Worktree not in state → `Output.error("Worktree not found in state for {issueId}")` + `sys.exit(1)`
- Git commands fail for live state → use `None` for `branchName`/`gitClean` (don't fail the whole command)

**Dependencies:** `StateReader`, `GitAdapter`, `ConfigFileRepository`, `IssueId`, `WorktreeStatus`, `StatusFormatter`, `Output`, `Constants`, all cache types
**Acceptance criteria:** Command runs with `./iw status IWLE-123`, `./iw status`, and `./iw status --json IWLE-123`, handles missing worktree with error exit, has PURPOSE header

## E2E Tests

### Task 3.7: E2E tests for `iw projects`

**What to do:**
1. Create `.iw/test/projects.bats`

**Setup pattern** (follows `start.bats` pattern):
```bash
setup() {
    export IW_SERVER_DISABLED=1
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
    # Create minimal git repo + iw config
    # Create a known state.json in the default location or override via env
}
```

**Key consideration:** `StateReader.DefaultStatePath` uses `$HOME/.local/share/iw/server/state.json`. For E2E tests, either:
- Override `HOME` to a temp dir so `StateReader` reads from a controlled location
- Or create the state.json in the real default path (risky, avoid)
Best approach: set `HOME` to `$TEST_DIR` in setup, create `$TEST_DIR/.local/share/iw/server/state.json` with test data.

**Test cases:**
- `"projects with no state file shows empty message"` — don't create state.json, run `./iw projects`, assert output contains "No projects registered."
- `"projects with populated state shows project info"` — create state.json with 1+ worktree registrations pointing to the test project path, create `.iw/config.conf` at that path, run `./iw projects`, assert output contains project name and worktree count
- `"projects --json with populated state outputs valid JSON"` — run `./iw projects --json`, pipe through `jq`, assert exit code 0 and JSON is an array
- `"projects --json with empty state outputs empty array"` — no state.json, run `./iw projects --json`, assert output is `[]`
- `"projects --json contains expected fields"` — run `./iw projects --json`, assert JSON contains `name`, `path`, `trackerType`, `team`, `worktreeCount` fields

**Dependencies:** Tasks 3.1 and 3.4 complete
**Acceptance criteria:** All BATS tests pass when run with `bats .iw/test/projects.bats`

---

### Task 3.8: E2E tests for `iw worktrees`

**What to do:**
1. Create `.iw/test/worktrees.bats`

**Setup pattern:** Same HOME override approach as projects.bats. Additionally create a git repo so `os.pwd` resolves to a known project.

**Test cases:**
- `"worktrees with no state file shows empty message"` — run `./iw worktrees`, assert output contains "No worktrees found."
- `"worktrees --all shows all worktrees"` — create state.json with worktrees from 2 projects, run `./iw worktrees --all`, assert output contains worktrees from both projects
- `"worktrees --json outputs valid JSON array"` — run `./iw worktrees --json --all`, pipe through `jq`, assert exit code 0
- `"worktrees --json with empty state outputs empty array"` — run `./iw worktrees --json --all`, assert output is `[]`
- `"worktrees --json contains expected fields"` — assert JSON contains `issueId`, `path`, `needsAttention`

**Dependencies:** Tasks 3.2 and 3.5 complete
**Acceptance criteria:** All BATS tests pass when run with `bats .iw/test/worktrees.bats`

---

### Task 3.9: E2E tests for `iw status`

**What to do:**
1. Create `.iw/test/status.bats`

**Setup pattern:** Same HOME override approach. Additionally need a real git worktree for live git state queries.

**Test cases:**
- `"status with explicit issue ID shows status"` — create state.json with a worktree registration, create the actual worktree directory with git, run `./iw status IWLE-123`, assert output contains "IWLE-123"
- `"status --json outputs valid JSON object"` — run `./iw status --json IWLE-123`, pipe through `jq`, assert exit code 0 and output is a JSON object (not array)
- `"status for nonexistent issue ID exits with error"` — run `./iw status NONEXISTENT-99`, assert exit code 1 and stderr contains error message
- `"status --json contains expected fields"` — assert JSON contains `issueId`, `path`, `needsAttention`
- `"status without args infers issue from branch"` — create worktree, cd into it, run `./iw status`, assert output contains the worktree's issue ID

**Dependencies:** Tasks 3.3 and 3.6 complete
**Acceptance criteria:** All BATS tests pass when run with `bats .iw/test/status.bats`

## Integration

### Task 3.10: Run full test suite and verify no regressions

**What to do:**
1. Run `./iw test unit` — all existing unit tests must pass, plus all new formatter tests
2. Run `./iw test e2e` — all existing E2E tests must pass, plus all new command E2E tests
3. Verify no imports from `iw.core.dashboard` in any of the 3 new command files
4. Verify all 9 new files (3 formatters, 3 formatter tests, 3 commands) have PURPOSE headers
5. Verify all 3 new BATS test files have PURPOSE comments
6. Spot-check `--json` output stability: run each command with `--json` and verify output matches the API contract in `phase-03-context.md`

**Dependencies:** All tasks 3.0-3.9 complete
**Acceptance criteria:**
- `./iw test unit` passes (all existing + new tests)
- `./iw test e2e` passes (all existing + new tests)
- No `iw.core.dashboard` imports in `projects.scala`, `worktrees.scala`, `status.scala`
- All new files have PURPOSE headers
- `iw projects --json`, `iw worktrees --json --all`, `iw status --json <id>` produce valid JSON matching documented schemas
