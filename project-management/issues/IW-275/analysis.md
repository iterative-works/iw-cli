# Technical Analysis: Add `iw batch-implement` command to replace shell script

**Issue:** IW-275
**Created:** 2026-03-19
**Status:** Draft

## Problem Statement

Batch implementation (running all phases of an issue sequentially) is currently a standalone shell script (`batch-implement.sh`, 473 lines) that depends on `jq` for JSON parsing, uses `sed` for task file updates, and duplicates logic already available in iw-cli's core modules. This creates maintenance burden (two codebases for the same logic), fragile string manipulation, and no unit-testable components.

Porting this to a proper `iw batch-implement` Scala command gives us direct access to existing model types (`ReviewState`, `PhaseIndexEntry`, `ForgeType`), existing adapters (`GitAdapter`, `GitHubClient`, `GitLabClient`, `ReviewStateAdapter`), and proper error handling through `Either`. The decision logic becomes unit-testable pure functions.

## Proposed Solution

### High-Level Approach

Create a new command at `.iw/commands/batch-implement.scala` that orchestrates the full batch implementation loop. The command reuses existing model types and adapters rather than shelling out to `jq`/`sed`. The core loop logic (phase selection, outcome handling, recovery decisions) lives as pure functions in a new model file, keeping the command script thin.

The command parses arguments, resolves issue ID and workflow type (with auto-detection from branch and `review-state.json`), then enters the phase loop: find next unchecked phase from `tasks.md`, invoke `claude -p` via `ProcessAdapter`, read status from `review-state.json`, handle the outcome (merge PR, mark done, recover, or fail). After all phases complete, run the final completion invocation.

### Why This Approach

The shell script already works, so this is a direct port rather than a redesign. The value comes from: (1) eliminating the `jq` dependency, (2) making the decision logic testable, (3) consistency with other `iw` commands, (4) access to existing forge adapters that already handle GitHub/GitLab differences. The architecture follows the existing FCIS pattern where pure decision functions live in `model/` and I/O operations use existing adapters.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### Domain Layer

**Components:**
- `BatchImplement` object in `model/` — pure functions for batch implementation decisions
  - Phase outcome decision: given a status string, return the action to take (merge, mark done, recover, fail)
  - Recovery prompt selection: given a status string, return the recovery prompt text
  - Terminal status check: determine if a status requires no further action
  - Next phase selection: given `List[PhaseIndexEntry]`, return the next unchecked phase number or None
  - Workflow type resolution: given a `ReviewState`, map `workflowType` field to `ag`/`wf` short code
  - Tasks.md phase completion: given file content and a phase number, return updated content with the phase checked off
- `PhaseIndexEntry` — already exists in `dashboard/MarkdownTaskParser` (see CLARIFY below)

**Responsibilities:**
- All decision logic for phase outcomes is pure and testable
- No I/O, no side effects
- Validates inputs (e.g., workflow type must be "ag" or "wf")

**Estimated Effort:** 3-5 hours
**Complexity:** Moderate — several decision branches, but each is straightforward

---

### Application Layer

**Note:** iw-cli does not have a formal application layer. Commands in `.iw/commands/` serve as both application and presentation layers. The orchestration logic that would normally be in an application service is split between:
- Pure decision functions in `model/` (domain layer)
- The command script itself (imperative shell)

This is consistent with all other iw commands. No separate application layer is needed.

**Estimated Effort:** 0 hours (not applicable)

---

### Infrastructure Layer

**Components:**
- No new adapter files needed — existing adapters cover all I/O operations
- Existing adapters to reuse:
  - `ProcessAdapter.runStreaming()` — for running `claude -p` with real-time output
  - `ProcessAdapter.commandExists()` — pre-flight checks for `claude`, `gh`/`glab`
  - `GitAdapter.getCurrentBranch()` — auto-detect issue ID
  - `GitAdapter.hasUncommittedChanges()` — clean working tree check
  - `GitAdapter.fetchAndReset()` — advance feature branch after merge
  - `GitAdapter.checkoutBranch()` — switch to feature branch after merge
  - `GitAdapter.stageAll()` + `GitAdapter.commit()` — commit uncommitted changes
  - `GitHubClient.mergePullRequest()` — merge GitHub PRs (regular merge)
  - `GitLabClient.mergeMergeRequest()` — merge GitLab MRs (regular merge)
  - `ReviewStateAdapter.read()` — read review-state.json
  - `ReviewStateAdapter.update()` — update review-state.json
  - `ConfigFileRepository.read()` — read project config

**Responsibilities:**
- All I/O is handled by existing adapters — no new adapter methods needed

**Estimated Effort:** 0.5-1 hours (just wiring existing adapters)
**Complexity:** Straightforward — reusing existing code

---

### Presentation Layer

**Components:**
- `batch-implement.scala` command script in `.iw/commands/`
  - Argument parsing: `ISSUE_ID` (positional), `ag|wf` (positional), `--max-budget-usd` (named), `--model` (named), `--max-turns` (named), `--max-retries` (named)
  - Pre-flight validation and output
  - Phase loop with logging
  - Status reporting after each phase
- Log file output: `{issue-dir}/batch-implement.log`

**Responsibilities:**
- Argument parsing and validation
- User-facing output (progress, errors, status)
- Orchestrating the phase loop by calling pure decision functions + adapters
- Logging to file

**Estimated Effort:** 6-10 hours
**Complexity:** Complex — this is the main orchestration script with many branches and the phase/recovery loops

---

## Technical Decisions

### Patterns

- **FCIS (Functional Core, Imperative Shell):** Pure decision logic in `model/BatchImplement`, all I/O in the command script using existing adapters
- **Either-based error handling:** Consistent with existing commands, using `CommandHelpers.exitOnError` for fatal errors
- **`ProcessAdapter.runStreaming` for claude invocation:** Stream output for real-time visibility rather than capturing JSON. Use exit code for success/failure detection.

### Technology Choices

- **Frameworks/Libraries**: os-lib (file I/O), ujson (JSON parsing of claude output and review-state), existing adapters
- **Data Storage**: Reads/writes `review-state.json` and `tasks.md` via existing mechanisms
- **External Systems**: Claude CLI (`claude -p`), GitHub CLI (`gh`), GitLab CLI (`glab`), git

### Integration Points

- `model/BatchImplement` is called by `batch-implement.scala` for all decisions
- `batch-implement.scala` calls `MarkdownTaskParser.parsePhaseIndex` to find phases (requires CLARIFY resolution)
- `batch-implement.scala` calls `GitHubClient`/`GitLabClient` for PR merging (regular merge) based on `ForgeType`
- `batch-implement.scala` calls `ReviewStateAdapter` for reading/updating state
- `batch-implement.scala` calls `ProcessAdapter.runStreaming` for claude CLI invocation

## Technical Risks & Uncertainties

### RESOLVED: MarkdownTaskParser Location

**Decision:** Move entire `MarkdownTaskParser` to `model/`. It is purely pure functions with no I/O — it belongs in `model/`, not `dashboard/`. Dashboard code will import from `model/` instead. Update all dashboard imports in the same commit.

---

### RESOLVED: Merge Strategy for Phase Branches

**Decision:** Use regular merge (not squash) for phase→feature branch merges. Reuse existing `GitHubClient.mergePullRequest` and `GitLabClient.mergeMergeRequest` as-is.

**Rationale:** Squash merge on phase branches was the root cause of two complications in the shell script: (1) needing `git reset --hard` after merge because histories diverge, and (2) handling remote branch deletion fallback. Regular merge eliminates both problems — histories converge naturally, no force-reset needed. Squash merge is appropriate later when merging the feature branch into `main`.

This also resolves the "Remote Branch Deletion" concern — with regular merge, the remote branch is not deleted and `GitAdapter.fetchAndReset` works normally. No new adapter methods needed.

---

### RESOLVED: Claude CLI Invocation Strategy

**Decision:** Stream output via `ProcessAdapter.runStreaming`. No JSON output parsing, no cost/turn tracking for now.

**Rationale:** When phases take 10-30 minutes, real-time visibility is more valuable than cost tracking. `runStreaming` is simpler (no large output buffers, no JSON parsing). Cost tracking can be added later if needed. Success/failure is determined by exit code.

---

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer: 3-5 hours (pure decision functions + MarkdownTaskParser move)
- Application Layer: 0 hours (N/A)
- Infrastructure Layer: 0.5-1 hours (wiring existing adapters only)
- Presentation Layer: 5-8 hours (command script, simplified by CLARIFY resolutions)

**Total Range:** 8.5 - 14 hours

**Confidence:** Medium-High

**Reasoning:**
- The shell script already defines the exact behavior, reducing design uncertainty
- All CLARIFY items are resolved — scope is well-defined
- Regular merge (not squash) eliminates branch-state complexity
- Streaming output (not JSON capture) simplifies claude invocation
- No new adapter methods needed — all I/O reuses existing code
- The phase loop and recovery logic still has many branches that need careful testing

## Testing Strategy

### Per-Layer Testing

**Domain Layer:**
- Unit: Phase outcome decision function — all status values mapped to correct actions
- Unit: Recovery prompt selection — correct prompt for each status
- Unit: Terminal status check — exhaustive status coverage
- Unit: Next phase selection — handles empty list, all complete, mixed states
- Unit: Workflow type resolution — maps "agile"→"ag", "waterfall"→"wf", None→error
- Unit: Tasks.md phase completion — marks correct phase, handles edge cases (already checked, missing phase)

**Infrastructure Layer:**
- No new adapter methods needed — existing adapters have full test coverage

**Presentation Layer (Command Script):**
- E2E: Happy path — mock claude output with a script that writes review-state.json correctly, verify phases are processed in order
- E2E: Argument parsing — issue ID from branch, workflow from review-state, explicit args
- E2E: Pre-flight failures — missing tasks.md, missing claude, dirty working tree

**Test Data Strategy:**
- Model tests use inline test data (status strings, phase lists)
- E2E tests use temporary git repos with real file structures
- Claude CLI is NOT called in tests — E2E tests should stub it with a shell script that writes expected state

**Regression Coverage:**
- Existing `phase-advance` tests may overlap with merge+reset behavior
- Existing `MarkdownTaskParser` tests cover phase index parsing
- Existing `ReviewStateUpdater` tests cover state merging

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
None. The command reads existing `config.conf` for tracker type and repository.

### Rollout Strategy
Ship as a new command. The shell script continues to work independently. Users can migrate at their own pace.

### Rollback Plan
Remove the command file. The shell script is not affected.

## Dependencies

### Prerequisites
- IW-274 (activity + workflow_type fields) — already merged
- All CLARIFY items resolved

### Layer Dependencies
- Domain layer must be implemented first (MarkdownTaskParser move + pure decision functions)
- Infrastructure layer is minimal — no new methods, just verification
- Command script depends on domain layer being complete
- Tests can be written alongside each layer

### External Blockers
- None. All dependencies are internal.

## Risks & Mitigations

### Risk 1: Claude CLI timeout behavior
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:** Use generous timeout (30+ minutes) for `ProcessAdapter.runStreaming`. If timeout is hit, treat it as a phase failure and enter recovery loop. Test with shorter timeouts first.

### Risk 2: MarkdownTaskParser move breaks dashboard
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** Update all dashboard imports in the same commit. Run full test suite before and after.

---

## Implementation Sequence

**Recommended Layer Order:**

1. **Domain Layer** — Move `MarkdownTaskParser` to `model/` (refactoring). Create `model/BatchImplement.scala` with pure decision functions. Full unit test coverage.
2. **Infrastructure Layer** — Minimal wiring only, no new adapter methods. Verify existing adapters cover all needs.
3. **Presentation Layer** — Create `.iw/commands/batch-implement.scala` orchestration script. E2E tests.

**Ordering Rationale:**
- Domain layer first: MarkdownTaskParser move is a prerequisite, and pure decision functions are independently testable
- Infrastructure is minimal (no new methods), mostly validation that existing adapters suffice
- Command script depends on domain layer being complete

## Documentation Requirements

- [ ] Code documentation (inline comments for decision logic in BatchImplement)
- [ ] Command help text (usage line in the script header)
- [ ] Update CLAUDE.md if the MarkdownTaskParser move changes import patterns

---

**Analysis Status:** All CLARIFYs Resolved

**Next Steps:**
1. Run **wf-create-tasks** with issue ID IW-275
2. Run **wf-implement** for layer-by-layer implementation
