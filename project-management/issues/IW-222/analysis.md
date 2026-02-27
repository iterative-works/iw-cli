# Technical Analysis: Agent-usable CLI: projects, worktrees, status commands + Claude-in-tmux

**Issue:** IW-222
**Created:** 2026-02-24
**Status:** Draft

## Problem Statement

A personal assistant agent needs to use `iw` exclusively to manage development workflows. Currently `iw` provides commands for human-interactive workflows (start/open/rm) but lacks the read-only discovery and status primitives an agent needs to reason about the state of projects and worktrees programmatically.

Additionally, several commands (`start`, `open`, `rm`, `issue`, `register`, `server`, `feedback`) import from `dashboard/`, violating the architecture rule that commands should only import from `model/`, `adapters/`, and `output/`. The affected types (`ServerClient`, `ServerConfigRepository`, `ProcessManager`, `FeedbackParser`) are pure I/O adapters that happen to live in `dashboard/` for historical reasons.

## Proposed Solution

### High-Level Approach

The work splits into two distinct tracks: architecture cleanup (moving misplaced code to correct layers) and new command implementation (adding agent-facing CLI primitives). The architecture cleanup must happen first because the new commands need to import `ServerClient` and `StateReader` from `adapters/`, not `dashboard/`.

For the architecture cleanup, we move `ServerClient`, `ServerConfigRepository`, `ProcessManager`, and `FeedbackParser` from `dashboard/` to `adapters/`, extract `MainProject.deriveMainProjectPath` pure logic to `model/`, extract shared `ReadWriter` codec instances from `StateRepository` to a standalone `model/` object, and create a new `StateReader` adapter that reads `state.json` without the write concern. All existing imports are updated.

For the new commands, we add `iw projects`, `iw worktrees`, and `iw status` as new command scripts, each supporting `--json` for machine-readable output. We also add `--prompt` support to `start` and `open` for launching an agent in a tmux session. The `TmuxAdapter` gets a new `sendKeys` method to support this.

### Why This Approach

The architecture cleanup is necessary -- not just cosmetic -- because the new commands (`projects`, `worktrees`, `status`) need `StateReader` (new) and `ServerClient` (existing), and commands must not import from `dashboard/`. Doing the cleanup first establishes the correct layering that all subsequent work builds on. The alternative of duplicating code or adding exceptions to the import rule would compound the existing violation.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### Domain Layer (`model/`)

**Components:**
- `ProjectPath` -- pure function to derive main project path from worktree path (extracted from `MainProject.deriveMainProjectPath`)
- `ServerStateCodec` -- shared `ReadWriter` instances for `ServerState` and all its nested types (extracted from `StateRepository`'s instance-scoped givens)
- `ServerLifecycleService` -- moved from `dashboard/` to `model/` (pure formatting/status creation logic, only imports from `model/`)
- `FeedbackParser` -- moved from `dashboard/` to `model/` (pure argument parsing logic, no I/O)
- `ProjectSummary` -- value object for `iw projects` output (project name, main tree path, tracker type, team, worktree count)
- `WorktreeSummary` -- value object for `iw worktrees` output (issue ID, path, PR state, review display, needs_attention)
- `WorktreeStatus` -- value object for `iw status` output (git state, issue data, PR data, review state, progress)

**Responsibilities:**
- Pure derivation of main project path from worktree path string
- JSON codec definitions shared between `StateRepository` (dashboard) and `StateReader` (adapters)
- Data shapes for CLI command output (both human and JSON formats)

**Estimated Effort:** 3-5 hours
**Complexity:** Straightforward

---

### Application Layer

This issue does not introduce a separate application layer. The commands are thin scripts that compose `model/` types with `adapters/` I/O and `output/` formatting directly. This is consistent with the existing pattern -- all current commands are direct scripts without an application service layer (those live only in `dashboard/` for the server).

**Estimated Effort:** 0 hours
**Complexity:** N/A

---

### Infrastructure Layer (`adapters/`)

**Components:**
- `ServerClient` -- moved from `dashboard/`, package changed to `iw.core.adapters`, imports updated
- `ServerConfigRepository` -- moved from `dashboard/`, package changed to `iw.core.adapters`
- `ProcessManager` -- moved from `dashboard/`, package changed to `iw.core.adapters`
- `FeedbackParser` -- moved from `dashboard/` to `iw.core.model` (pure parsing logic, no I/O)
- `StateReader` -- new adapter: reads `state.json` from standard location (`~/.local/share/iw/server/state.json`), returns `ServerState`, uses `ServerStateCodec` from `model/`
- `TmuxAdapter.sendKeys` -- new method: sends keystrokes to a tmux session pane via `tmux send-keys`

**Responsibilities:**
- HTTP communication with dashboard server (ServerClient)
- JSON config file I/O (ServerConfigRepository)
- Process lifecycle management (ProcessManager)
- *(FeedbackParser moved to `model/` instead — pure parsing logic)*
- Read-only access to server state file (StateReader)
- Tmux session keystroke injection (TmuxAdapter.sendKeys)

**Estimated Effort:** 6-9 hours
**Complexity:** Moderate

The complexity is moderate primarily because of the codec extraction and the need to update all downstream imports without breaking anything. The moves themselves are mechanical but the `StateRepository` refactoring to extract codecs requires care -- the `ReadWriter` givens are currently instance-scoped in the `StateRepository` case class, and they need to become top-level (in a `model/` object) so both `StateRepository` and `StateReader` can use them.

---

### Presentation Layer (`commands/` + `output/`)

**Components:**
- `commands/projects.scala` -- `iw projects [--json]` command script
- `commands/worktrees.scala` -- `iw worktrees [--json]` command script
- `commands/status.scala` -- `iw status [issue-id] [--json]` command script
- Updated `commands/start.scala` -- `--prompt` flag support
- Updated `commands/open.scala` -- `--prompt` flag support
- Updated imports in `commands/start.scala`, `commands/open.scala`, `commands/rm.scala`, `commands/issue.scala`, `commands/register.scala`, `commands/server.scala`, `commands/feedback.scala` -- change `iw.core.dashboard.X` to `iw.core.adapters.X`
- `output/ProjectsFormatter` -- human-readable formatting for projects list
- `output/WorktreesFormatter` -- human-readable formatting for worktrees list
- `output/StatusFormatter` -- human-readable formatting for detailed status

**Responsibilities:**
- Argument parsing and flag handling (`--json`, `--prompt`, issue-id resolution)
- JSON output generation (via upickle serialization of model types)
- Human-readable table/summary output
- Orchestrating adapter calls (StateReader, ConfigFileRepository, GitAdapter, TmuxAdapter)
- `--prompt` implies detach: skip `tmux attach`/`switch`, instead use `sendKeys` to launch agent

**Estimated Effort:** 8-12 hours
**Complexity:** Moderate

---

## Technical Decisions

### Patterns

- **Existing script-per-command pattern**: Each new command is a standalone `.scala` file in `commands/`, consistent with `start.scala`, `open.scala`, etc.
- **`--json` flag pattern**: When `--json` is present, output a single JSON object to stdout (parseable by agents). When absent, use human-readable formatting from `output/`.
- **Codec extraction**: Move `ReadWriter` givens from `StateRepository` instance scope to a `model/ServerStateCodec` object so they can be reused.
- **Value objects for output**: Use dedicated case classes (`ProjectSummary`, `WorktreeSummary`, `WorktreeStatus`) rather than raw tuples or maps, enabling both JSON serialization and human formatting.

### Technology Choices

- **Frameworks/Libraries**: upickle (already in use) for JSON serialization of new value objects and `--json` output. No new dependencies.
- **Data Storage**: Reads from existing `state.json` (via new `StateReader`), existing `config.conf` (via existing `ConfigFileRepository`), and `review-state.json` files on disk.
- **External Systems**: No new external system integrations. `ServerClient` calls are best-effort (existing pattern).

### Integration Points

- `StateReader` reads the same `state.json` that `StateRepository` writes -- must use the same codec
- `commands/projects.scala` composes `StateReader` + `ProjectPath` (model) + `ConfigFileRepository` (adapters)
- `commands/worktrees.scala` composes `StateReader` + `ProjectPath` (model) + current project detection
- `commands/status.scala` composes `StateReader` + disk-read of `review-state.json` + `GitAdapter` for fresh git state
- `--prompt` in `start`/`open` composes existing session creation with new `TmuxAdapter.sendKeys`
- `dashboard/StateRepository` must import codecs from `model/ServerStateCodec` after extraction
- `dashboard/ServerLifecycleService` still used by `server.scala` command -- this is a legitimate dashboard import since it's server-specific logic; however `server.scala` also imports `ProcessManager` and `ServerConfigRepository` which are being moved

## Technical Risks & Uncertainties

### RESOLVED: ServerStateCodec extraction from StateRepository

**Decision:** Option A — Extract codecs to `model/ServerStateCodec` object with all `given ReadWriter[X]` as members. Both `StateRepository` and `StateReader` import from it. Minimal change to `StateRepository` — just add an import and remove the local given definitions.

**Impact:** Affects `StateRepository` (must import codecs), `StateReader` (new, uses codecs), and any test that creates `StateRepository` instances. Mitigate with roundtrip serialization test.

---

### RESOLVED: FeedbackParser placement

**Decision:** Option A — Move to `model/` since it's pure parsing logic with no I/O. Included in the architecture cleanup phase.

**Impact:** `feedback.scala` import line changes. Minimal risk.

---

### RESOLVED: `server.scala` import of ServerLifecycleService

**Decision:** Option B — Move `ServerLifecycleService` to `model/`. It's ~67 lines of pure formatting logic with only `model/` imports. Eliminates the last `dashboard/` import from commands.

**Impact:** `server.scala` and `dashboard.scala` import lines change. Minimal risk.

---

### RESOLVED: `iw status` data freshness strategy

**Decision:** Option A — Read `review-state.json` from the canonical path `{worktree_path}/project-management/issues/{issue-id}/review-state.json` (confirmed from `ReviewStateService.scala:135`). Fall back gracefully if missing.

**Impact:** `status` command reads from this path using the worktree path from `state.json` and the issue ID.

---

### RESOLVED: `--prompt` agent launch mechanism

**Decision:** Option A — Hard-code `claude --dangerously-skip-permissions --prompt "..."` for now. Matches the issue spec. YAGNI — trivial to change later if needed.

**Impact:** `start.scala` and `open.scala` `--prompt` handling. If `claude` is missing, `sendKeys` still succeeds (types into terminal); error appears in tmux pane.

---

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer (`model/`): 3-5 hours
- Application Layer: 0 hours (no separate application layer for commands)
- Infrastructure Layer (`adapters/`): 6-9 hours
- Presentation Layer (`commands/` + `output/`): 8-12 hours

**Total Range:** 17-26 hours

**Confidence:** Medium

**Reasoning:**
- The architecture cleanup (moves + codec extraction) is the riskiest part -- import changes across many files with potential for subtle breakage
- The new commands follow well-established patterns (existing commands provide clear templates)
- The `--json` output is straightforward serialization but needs careful schema design for agent consumption
- Testing effort is significant: each new command needs both unit tests and E2E BATS tests
- The `--prompt` feature is relatively simple (thin wrapper over `sendKeys`) but the interaction with existing start/open flow needs care to avoid regressions

## Testing Strategy

### Per-Layer Testing

**Domain Layer:**
- Unit: `ProjectPathTest` -- derivation of main project path (migrated from `MainProjectTest`)
- Unit: `ServerStateCodecTest` -- roundtrip serialization of all state types
- Unit: `ProjectSummaryTest`, `WorktreeSummaryTest`, `WorktreeStatusTest` -- value object construction and equality

**Infrastructure Layer:**
- Unit: `StateReaderTest` -- reading state.json, handling missing file, handling malformed JSON
- Unit: `TmuxAdapter.sendKeys` test -- verify correct tmux command construction
- Integration: Existing `ServerClientTest`, `ProcessManagerTest`, `ServerConfigRepositoryTest` must pass after package move (import-only change)

**Presentation Layer:**
- Unit: Formatter tests for human-readable output (`ProjectsFormatterTest`, etc.)
- E2E: `projects.bats` -- `iw projects` with and without `--json`, empty state, populated state
- E2E: `worktrees.bats` -- `iw worktrees` with and without `--json`, filtering by current project
- E2E: `status.bats` -- `iw status` with issue ID, inferred from branch, `--json` output
- E2E: Updated `start.bats` -- `--prompt` flag creates session and sends keys without attaching
- E2E: Updated `open.bats` -- `--prompt` flag sends keys to existing session

**Test Data Strategy:**
- E2E tests create temporary `state.json` files with known data (similar to how `start.bats` creates temporary git repos)
- Unit tests use inline test data (case class construction) consistent with existing test patterns
- `IW_SERVER_DISABLED=1` environment variable used in E2E tests to prevent real server interaction (existing pattern)

**Regression Coverage:**
- All existing E2E tests (`start.bats`, `open.bats`, `rm.bats`, `register.bats`, `issue.bats`, `server.bats`) must pass after the import moves
- `ServerClientTest`, `ProcessManagerTest`, `ServerConfigRepositoryTest`, `MainProjectTest`, `MainProjectServiceTest`, `StateRepositoryTest` must pass after refactoring
- Run full test suite (`./iw test`) after each phase

## Deployment Considerations

### Database Changes
No database migrations. The only data file is `state.json`, whose format is unchanged.

### Configuration Changes
No new configuration fields. The `--json` and `--prompt` flags are CLI arguments, not config.

### Rollout Strategy
This is a CLI tool distributed as source in the repository. Changes take effect on next `iw` invocation after pulling the branch. No staged rollout needed.

### Rollback Plan
Revert the branch. Since state.json format is unchanged, there is no data migration concern.

## Dependencies

### Prerequisites
- All CLARIFY markers resolved (see decisions above)

### Layer Dependencies
- Domain layer (`model/`) changes must be completed before infrastructure layer, because `StateReader` and `StateRepository` both need `ServerStateCodec`
- Infrastructure layer (`adapters/`) moves must be completed before presentation layer import updates
- `TmuxAdapter.sendKeys` must exist before `--prompt` support in `start`/`open`

### External Blockers
- None. All dependencies are internal.

## Risks & Mitigations

### Risk 1: Import breakage during architecture cleanup
**Likelihood:** Medium
**Impact:** High (compilation failures across multiple commands and tests)
**Mitigation:** Make the moves one file at a time with compilation check after each. Run full test suite after completing all moves before starting new command work.

### Risk 2: ServerStateCodec extraction breaks JSON roundtrip
**Likelihood:** Low
**Impact:** High (state.json becomes unreadable or writable data is corrupted)
**Mitigation:** Write a roundtrip test that serializes `ServerState` via `ServerStateCodec`, deserializes it back, and asserts equality. Run this test before and after the extraction.

### Risk 3: `--prompt` interaction with tmux session lifecycle
**Likelihood:** Low
**Impact:** Medium (agent not launched, or session left in bad state)
**Mitigation:** The `sendKeys` approach is inherently safe -- it just types text into an existing session. If the claude command fails, the error appears in the tmux pane but the session remains usable.

### Risk 4: E2E tests for new commands require state.json setup
**Likelihood:** Medium
**Impact:** Low (test complexity, not production risk)
**Mitigation:** Create a shared BATS helper function for setting up test `state.json` files, similar to the existing `setup()` pattern in `start.bats`.

---

## Implementation Sequence

**Recommended Layer Order:**

1. **Domain Layer** -- Extract `ProjectPath` from `MainProject`, create `ServerStateCodec`, define output value objects. Pure logic with no dependencies, foundation for everything else.

2. **Infrastructure Layer (moves)** -- Move `ServerClient`, `ServerConfigRepository`, `ProcessManager`, `FeedbackParser` to `adapters/`. Update `StateRepository` to import from `ServerStateCodec`. Create `StateReader`. Add `TmuxAdapter.sendKeys`. Update all imports in commands. Run full test suite.

3. **Presentation Layer (new commands)** -- Implement `iw projects`, `iw worktrees`, `iw status` with `--json` support. Add formatters in `output/`. Write unit and E2E tests for each.

4. **Presentation Layer (--prompt)** -- Add `--prompt` flag to `start.scala` and `open.scala`. Wire `TmuxAdapter.sendKeys`. Write E2E tests.

**Ordering Rationale:**
- Domain must come first because infrastructure (StateReader, StateRepository) depends on `ServerStateCodec`
- Infrastructure moves must precede new commands because commands import from `adapters/`
- New commands and `--prompt` are independent of each other and could be parallelized, but sequencing them reduces risk
- The `--prompt` feature builds on the same commands being modified in the moves phase, so doing it last avoids merge conflicts within the same files

## Documentation Requirements

- [ ] Code documentation (inline comments for complex logic, PURPOSE headers on all new files)
- [ ] API documentation (document `--json` output schema for each new command, either inline or in docs/)
- [ ] Architecture decision record (document the architecture cleanup rationale -- why these files were moved)
- [ ] User-facing documentation (update help text / README for new commands and `--prompt` flag)
- [ ] Migration guide (not needed -- no breaking changes to existing command interfaces)

---

**Analysis Status:** Approved (all CLARIFY markers resolved)

**Next Steps:**
1. Run **wf-create-tasks** with the issue ID
2. Run **wf-implement** for layer-by-layer implementation
