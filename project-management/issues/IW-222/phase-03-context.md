# Phase 3: Presentation Layer — new commands (projects, worktrees, status) with --json

## Goals

Add three new CLI commands (`iw projects`, `iw worktrees`, `iw status`) that give agents and humans read-only visibility into the state of registered projects and worktrees. Each command supports a `--json` flag for machine-readable output (agent consumption) and defaults to human-readable formatted output. Three corresponding formatter objects are created in `output/` for the human-readable presentation.

## Scope

### In Scope

1. **`commands/projects.scala`** — `iw projects [--json]` command showing all registered projects
2. **`commands/worktrees.scala`** — `iw worktrees [--all] [--json]` command showing worktrees for current project (or all projects)
3. **`commands/status.scala`** — `iw status [issue-id] [--json]` command showing detailed status for a specific worktree
4. **`output/ProjectsFormatter.scala`** — Human-readable formatter for projects list
5. **`output/WorktreesFormatter.scala`** — Human-readable formatter for worktrees list
6. **`output/StatusFormatter.scala`** — Human-readable formatter for detailed worktree status
7. **Unit tests** for all three formatters
8. **E2E tests** (BATS) for all three commands

### Out of Scope

- `--prompt` flag support for `start`/`open` — Phase 4
- Changes to existing commands (`start`, `open`, `rm`, `issue`, etc.) — no modifications needed; imports were already updated in Phase 2
- Dashboard server changes — these commands are read-only, using `StateReader` to read `state.json` directly
- Changes to any model types — value objects (`ProjectSummary`, `WorktreeSummary`, `WorktreeStatus`) are already defined from Phase 1

## Dependencies on Prior Phases

### From Phase 1 (Domain Layer)

- **`model/ProjectSummary`** (`iw.core.model.ProjectSummary`) — Value object for `iw projects` output, has `derives ReadWriter` for JSON serialization. Fields: `name`, `path`, `trackerType`, `team`, `worktreeCount`.
- **`model/WorktreeSummary`** (`iw.core.model.WorktreeSummary`) — Value object for `iw worktrees` output with `derives ReadWriter`. Fields: `issueId`, `path`, `issueTitle`, `issueStatus`, `prState`, `reviewDisplay`, `needsAttention`.
- **`model/WorktreeStatus`** (`iw.core.model.WorktreeStatus`) — Value object for `iw status` output with `derives ReadWriter`. Fields: `issueId`, `path`, `branchName`, `gitClean`, `issueTitle`, `issueStatus`, `issueUrl`, `prUrl`, `prState`, `prNumber`, `reviewDisplay`, `reviewBadges`, `needsAttention`, `currentPhase`, `totalPhases`, `overallProgress`.
- **`model/ProjectPath`** (`iw.core.model.ProjectPath`) — Pure function `deriveMainProjectPath(worktreePath: String): Option[String]` used to group worktrees by main project.
- **`model/ServerState`** — Domain model with `worktrees: Map[String, WorktreeRegistration]`, `issueCache`, `progressCache`, `prCache`, `reviewStateCache`.
- **`model/ServerStateCodec`** — JSON codecs for all server state types.

### From Phase 2 (Infrastructure Layer)

- **`adapters/StateReader`** (`iw.core.adapters.StateReader`) — Read-only adapter for `state.json`. `StateReader.read(statePath): Either[String, ServerState]`. Returns empty state if file missing, `Left(error)` on parse failure.
- **Clean imports** — All commands now import from `iw.core.model.*`, `iw.core.adapters.*`, `iw.core.output.*` (no `dashboard/` imports).

### Existing Infrastructure (Pre-IW-222)

- **`adapters/ConfigFileRepository`** — `read(path: os.Path): Option[ProjectConfiguration]` for reading project config.
- **`adapters/GitAdapter`** — `getCurrentBranch(dir: os.Path): Either[String, String]` and `hasUncommittedChanges(path: os.Path): Either[String, Boolean]` for live git state.
- **`output/Output`** — `Output.error()`, `Output.info()`, `Output.success()`, `Output.warning()`, `Output.section()`, `Output.keyValue()` for consistent CLI output.
- **`model/Constants`** — `Constants.Paths.IwDir`, `Constants.Paths.ConfigFileName`, `Constants.SystemProps.UserDir`.
- **`model/IssueId`** — `IssueId.parse()`, `IssueId.fromBranch()` for issue ID resolution.

## Approach

### Step-by-step Implementation Plan

**Step 1: Create `output/ProjectsFormatter.scala`**

Pure formatting function: takes `List[ProjectSummary]` and returns a formatted string. Test-first with `ProjectsFormatterTest`.

**Step 2: Create `output/WorktreesFormatter.scala`**

Pure formatting function: takes `List[WorktreeSummary]` and returns a formatted string. Test-first with `WorktreesFormatterTest`.

**Step 3: Create `output/StatusFormatter.scala`**

Pure formatting function: takes `WorktreeStatus` and returns a formatted string. Test-first with `StatusFormatterTest`.

**Step 4: Create `commands/projects.scala`**

Thin script that:
1. Reads `state.json` via `StateReader.read()`
2. Groups worktrees by main project path using `ProjectPath.deriveMainProjectPath`
3. For each group, reads the project config from that path to get `trackerType` and `team`
4. Builds `List[ProjectSummary]`
5. If `--json`: writes JSON via `upickle.default.write`
6. If no flag: uses `ProjectsFormatter.format`

**Step 5: Create `commands/worktrees.scala`**

Thin script that:
1. Reads `state.json` via `StateReader.read()`
2. Determines current project from `os.pwd` and config
3. Filters worktrees to current project (unless `--all`)
4. Builds `List[WorktreeSummary]` from worktree registrations + caches
5. If `--json`: writes JSON
6. If no flag: uses `WorktreesFormatter.format`

**Step 6: Create `commands/status.scala`**

Thin script that:
1. Resolves issue ID from args or current branch (same pattern as `open.scala`)
2. Reads `state.json` via `StateReader.read()`
3. Finds the worktree registration for that issue ID
4. Gets live git state via `GitAdapter` (branch name, clean status)
5. Pulls cached data (issue, PR, progress, review state)
6. Builds `WorktreeStatus`
7. If `--json`: writes JSON
8. If no flag: uses `StatusFormatter.format`

**Step 7: Write E2E tests**

BATS tests for each command: `projects.bats`, `worktrees.bats`, `status.bats`.

### Command Pattern

All new commands follow the established pattern from existing commands:

```scala
// PURPOSE: <description>
// PURPOSE: <additional description>

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def commandName(args: String*): Unit =
  // Parse arguments (flags like --json, --all, positional args)
  // Read config if needed
  // Read state via StateReader
  // Build value object(s)
  // Output JSON or formatted text
```

### JSON Output Pattern

When `--json` is present, output is a single JSON object/array to stdout using `upickle.default.write`. The value objects have `derives ReadWriter` so serialization is automatic:

```scala
import upickle.default.*

if jsonFlag then
  println(write(summaries))  // For lists
  // or
  println(write(status))     // For single object
```

## Component Specifications

### New Commands

#### `commands/projects.scala`

- **File location:** `.iw/commands/projects.scala`
- **Arguments/flags:**
  - `--json` — Output JSON instead of human-readable format
  - No positional arguments
- **Adapters/models used:**
  - `StateReader.read()` — Get all worktrees from state
  - `ProjectPath.deriveMainProjectPath()` — Group worktrees by project
  - `ConfigFileRepository.read()` — Get tracker type and team for each project
  - `ProjectSummary` — Value object for output
- **Key logic flow:**
  1. Parse args: extract `--json` flag
  2. Call `StateReader.read()` to get `ServerState`
  3. Group `state.worktrees` by `ProjectPath.deriveMainProjectPath(wt.path)` — worktrees that share a main project path belong to the same project
  4. For each group: derive project name from the main path's last directory component. Read the project's `.iw/config.conf` (at the main project path) to get `trackerType` and `team`. Count worktrees.
  5. Build `List[ProjectSummary]`
  6. If `--json`: `println(write(summaries))`
  7. Else: `println(ProjectsFormatter.format(summaries))`
  8. Handle empty state gracefully (print message or empty list)
- **Output format:**
  - Human: Tabular list of projects with name, path, tracker, team, worktree count
  - JSON: Array of `ProjectSummary` objects

#### `commands/worktrees.scala`

- **File location:** `.iw/commands/worktrees.scala`
- **Arguments/flags:**
  - `--json` — Output JSON instead of human-readable format
  - `--all` — Show worktrees for all projects (default: current project only)
  - No positional arguments
- **Adapters/models used:**
  - `StateReader.read()` — Get all worktrees from state
  - `ProjectPath.deriveMainProjectPath()` — Filter to current project
  - `ConfigFileRepository.read()` — Get current project config (for team prefix if needed)
  - `ServerState` caches — `issueCache`, `prCache`, `reviewStateCache`
  - `WorktreeSummary` — Value object for output
- **Key logic flow:**
  1. Parse args: extract `--json` and `--all` flags
  2. Call `StateReader.read()` to get `ServerState`
  3. If not `--all`: determine current project's main path from `os.pwd` (either directly if in main project, or via `ProjectPath.deriveMainProjectPath` if in a worktree). Filter worktrees to those whose `deriveMainProjectPath` matches.
  4. For each worktree registration: look up `issueCache[issueId]` for title/status, `prCache[issueId]` for PR state, `reviewStateCache[issueId]` for review display and needsAttention
  5. Build `List[WorktreeSummary]`, sorted by issue ID
  6. If `--json`: `println(write(summaries))`
  7. Else: `println(WorktreesFormatter.format(summaries))`
- **Output format:**
  - Human: Table-like list with issue ID, title, PR state, attention indicator
  - JSON: Array of `WorktreeSummary` objects

#### `commands/status.scala`

- **File location:** `.iw/commands/status.scala`
- **Arguments/flags:**
  - `[issue-id]` — Optional issue ID (inferred from current branch if not provided)
  - `--json` — Output JSON instead of human-readable format
- **Adapters/models used:**
  - `StateReader.read()` — Get worktree state and caches
  - `ConfigFileRepository.read()` — Get config for team prefix
  - `GitAdapter.getCurrentBranch()` — For branch inference and live git state
  - `GitAdapter.hasUncommittedChanges()` — For live git clean status
  - `IssueId.parse()`, `IssueId.fromBranch()` — Issue ID resolution (same as `open.scala`)
  - `WorktreeStatus` — Value object for output
- **Key logic flow:**
  1. Parse args: extract `--json` flag and optional issue ID
  2. Load config for team prefix
  3. Resolve issue ID from args or current branch (same pattern as `open.scala`)
  4. Call `StateReader.read()` to get `ServerState`
  5. Find worktree registration by issue ID in `state.worktrees`
  6. Get live git state: `GitAdapter.getCurrentBranch(os.Path(wt.path))`, `GitAdapter.hasUncommittedChanges(os.Path(wt.path))`
  7. Populate from caches:
     - `issueCache[issueId]` → title, status, URL
     - `prCache[issueId]` → PR URL, state, number
     - `reviewStateCache[issueId]` → display text, badges (as label strings), needsAttention
     - `progressCache[issueId]` → currentPhase, totalPhases, overallPercentage
  8. Build `WorktreeStatus`
  9. If `--json`: `println(write(status))`
  10. Else: `println(StatusFormatter.format(status))`
  11. If worktree not found in state: error message and `sys.exit(1)`
- **Output format:**
  - Human: Detailed multi-section display with git, issue, PR, review, and progress sections
  - JSON: Single `WorktreeStatus` object

### New Formatters

#### `output/ProjectsFormatter.scala`

- **File location:** `.iw/core/output/ProjectsFormatter.scala`
- **Input type:** `List[ProjectSummary]`
- **Output format:** Tabular display:
  ```
  === Registered Projects ===

  testproject        /home/user/testproject     linear   IWLE   3 worktrees
  kanon              /home/user/kanon           github   KANO   1 worktree
  ```
  Columns: name, path, trackerType, team, worktreeCount (with "worktree"/"worktrees" pluralization).
  Empty list shows: "No projects registered."

#### `output/WorktreesFormatter.scala`

- **File location:** `.iw/core/output/WorktreesFormatter.scala`
- **Input type:** `List[WorktreeSummary]`
- **Output format:** List display:
  ```
  === Worktrees ===

  IWLE-123  Add user login                In Progress  PR: Open    ⚠ Needs attention
  IWLE-456  Fix authentication bug        Done         PR: Merged
  IWLE-789  Update documentation          Todo
  ```
  Each line: issue ID, title (truncated if long), status, PR state (if available), attention indicator (if `needsAttention`).
  Empty list shows: "No worktrees found."

#### `output/StatusFormatter.scala`

- **File location:** `.iw/core/output/StatusFormatter.scala`
- **Input type:** `WorktreeStatus`
- **Output format:** Multi-section display:
  ```
  === IWLE-123: Add user login ===

  Git
    Branch:     IWLE-123
    Status:     Clean

  Issue
    Status:     In Progress
    URL:        https://linear.app/team/IWLE-123

  Pull Request
    State:      Open
    PR:         #42
    URL:        https://github.com/org/repo/pull/42

  Review
    Status:     Waiting for review
    Badges:     [Phase 2] [Tests passing]
    ⚠ Needs attention

  Progress
    Phase:      2/4
    Overall:    65%
  ```
  Sections are only shown if data is available. Missing data is omitted (not shown as "N/A").

## API Contracts

### `iw projects --json`

```json
[
  {
    "name": "testproject",
    "path": "/home/user/testproject",
    "trackerType": "linear",
    "team": "IWLE",
    "worktreeCount": 3
  },
  {
    "name": "kanon",
    "path": "/home/user/kanon",
    "trackerType": "github",
    "team": "iterative-works/kanon",
    "worktreeCount": 1
  }
]
```

- **Type:** JSON array of `ProjectSummary` objects
- **Fields:** All fields are required (no optionals in `ProjectSummary`)
- **Empty state:** Returns `[]`

### `iw worktrees --json`

```json
[
  {
    "issueId": "IWLE-123",
    "path": "/home/user/testproject-IWLE-123",
    "issueTitle": "Add user login",
    "issueStatus": "In Progress",
    "prState": "Open",
    "reviewDisplay": "Waiting for review",
    "needsAttention": true
  },
  {
    "issueId": "IWLE-456",
    "path": "/home/user/testproject-IWLE-456",
    "issueTitle": null,
    "issueStatus": null,
    "prState": null,
    "reviewDisplay": null,
    "needsAttention": false
  }
]
```

- **Type:** JSON array of `WorktreeSummary` objects
- **Optional fields:** `issueTitle`, `issueStatus`, `prState`, `reviewDisplay` may be `null` (serialized from `Option[String]`)
- **`needsAttention`:** Always present as boolean
- **Empty state:** Returns `[]`

### `iw status --json`

```json
{
  "issueId": "IWLE-123",
  "path": "/home/user/testproject-IWLE-123",
  "branchName": "IWLE-123",
  "gitClean": true,
  "issueTitle": "Add user login",
  "issueStatus": "In Progress",
  "issueUrl": "https://linear.app/team/IWLE-123",
  "prUrl": "https://github.com/org/repo/pull/42",
  "prState": "Open",
  "prNumber": 42,
  "reviewDisplay": "Waiting for review",
  "reviewBadges": ["Phase 2", "Tests passing"],
  "needsAttention": true,
  "currentPhase": 2,
  "totalPhases": 4,
  "overallProgress": 65
}
```

- **Type:** Single `WorktreeStatus` JSON object
- **Optional fields:** All fields except `issueId`, `path`, and `needsAttention` may be `null`
- **`reviewBadges`:** Array of strings (badge labels) or `null`
- **`overallProgress`:** Integer 0-100 or `null`
- **Error case:** If issue ID not found in state, command exits with error on stderr (no JSON output)

## Files to Create

| File | Description |
|------|-------------|
| `.iw/commands/projects.scala` | `iw projects [--json]` command script |
| `.iw/commands/worktrees.scala` | `iw worktrees [--all] [--json]` command script |
| `.iw/commands/status.scala` | `iw status [issue-id] [--json]` command script |
| `.iw/core/output/ProjectsFormatter.scala` | Human-readable projects list formatter |
| `.iw/core/output/WorktreesFormatter.scala` | Human-readable worktrees list formatter |
| `.iw/core/output/StatusFormatter.scala` | Human-readable worktree status formatter |
| `.iw/core/test/ProjectsFormatterTest.scala` | Unit tests for ProjectsFormatter |
| `.iw/core/test/WorktreesFormatterTest.scala` | Unit tests for WorktreesFormatter |
| `.iw/core/test/StatusFormatterTest.scala` | Unit tests for StatusFormatter |
| `.iw/test/projects.bats` | E2E tests for `iw projects` command |
| `.iw/test/worktrees.bats` | E2E tests for `iw worktrees` command |
| `.iw/test/status.bats` | E2E tests for `iw status` command |

## Files to Modify

No existing files need to be modified in Phase 3. All imports were already updated in Phase 2, and the value objects were created in Phase 1.

The `iw` bootstrap script may need to be checked to ensure `projects`, `worktrees`, and `status` are recognized as valid command names (check if command dispatch is automatic based on file presence in `commands/` or if there is a static list).

## Testing Strategy

### Unit Tests

**`ProjectsFormatterTest`** — Test `ProjectsFormatter.format(List[ProjectSummary])`:
- Format single project → shows name, path, tracker, team, "1 worktree"
- Format multiple projects → shows all projects, sorted or in input order
- Format project with multiple worktrees → pluralization "3 worktrees"
- Format empty list → "No projects registered."

**`WorktreesFormatterTest`** — Test `WorktreesFormatter.format(List[WorktreeSummary])`:
- Format worktree with all fields populated
- Format worktree with no cached data (all Options are None)
- Format worktree with `needsAttention = true` → shows attention indicator
- Format multiple worktrees
- Format empty list → "No worktrees found."
- Long issue titles are truncated

**`StatusFormatterTest`** — Test `StatusFormatter.format(WorktreeStatus)`:
- Format status with all fields populated → shows all sections
- Format status with only git data (everything else None) → shows git section only
- Format status with no git data → omits git section
- Format status with PR data → shows PR section
- Format status with review badges → shows badge labels
- Format status with `needsAttention = true` → shows attention indicator
- Format status with progress data → shows phase and percentage

### E2E Tests

**`projects.bats`** — End-to-end tests for `iw projects`:

Setup: Create a temporary `state.json` with known worktree data. Set `IW_SERVER_DISABLED=1`.

- `iw projects` with no state file → shows "No projects registered." or empty output
- `iw projects` with populated state → shows project name, path, worktree count
- `iw projects --json` with populated state → valid JSON array, parseable with `jq`
- `iw projects --json` with empty state → returns `[]`

**`worktrees.bats`** — End-to-end tests for `iw worktrees`:

Setup: Create a temporary `state.json` and a project config.

- `iw worktrees` in a project directory → shows worktrees for that project
- `iw worktrees --all` → shows worktrees for all projects
- `iw worktrees --json` → valid JSON array
- `iw worktrees` with no worktrees for current project → "No worktrees found."

**`status.bats`** — End-to-end tests for `iw status`:

Setup: Create a temporary `state.json`, a git worktree, and project config.

- `iw status IWLE-123` → shows detailed status for that worktree
- `iw status` (no args, in a worktree branch) → infers issue ID from branch
- `iw status --json IWLE-123` → valid JSON object
- `iw status NONEXISTENT-99` → error message, exit code 1

**Test Data Strategy:**
- E2E tests create temporary `state.json` files in known locations, overriding `StateReader.DefaultStatePath` via environment variable or by placing the file in the expected location
- Use `IW_SERVER_DISABLED=1` to prevent real server interaction
- Use isolated tmux sockets via `IW_TMUX_SOCKET` (existing pattern)
- Git worktrees created with `git worktree add` for status tests that need live git state

## Acceptance Criteria

- [ ] `iw projects` displays all registered projects from `state.json` in human-readable format
- [ ] `iw projects --json` outputs valid JSON array of `ProjectSummary` objects
- [ ] `iw worktrees` displays worktrees for the current project with issue/PR/review data from caches
- [ ] `iw worktrees --all` displays worktrees for all projects
- [ ] `iw worktrees --json` outputs valid JSON array of `WorktreeSummary` objects
- [ ] `iw status <issue-id>` displays detailed worktree status with live git data and cached data
- [ ] `iw status` (no args) infers issue ID from current branch
- [ ] `iw status --json` outputs valid JSON `WorktreeStatus` object
- [ ] All three commands handle empty state gracefully (no crash, informative message)
- [ ] All three commands handle missing `state.json` gracefully (empty state, not error)
- [ ] `ProjectsFormatter` unit tests pass
- [ ] `WorktreesFormatter` unit tests pass
- [ ] `StatusFormatter` unit tests pass
- [ ] E2E tests for `projects`, `worktrees`, `status` pass
- [ ] All existing unit tests still pass (`./iw test unit`)
- [ ] All existing E2E tests still pass (`./iw test e2e`)
- [ ] All new files have PURPOSE headers
- [ ] No imports from `iw.core.dashboard` in any new command files
- [ ] `--json` output is stable and documented (API contract above)
