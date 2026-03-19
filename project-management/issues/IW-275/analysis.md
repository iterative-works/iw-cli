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
  - `ProcessAdapter.run()` — for running `claude -p` with JSON output
  - `ProcessAdapter.runStreaming()` — alternative for claude invocation (stdout visible)
  - `ProcessAdapter.commandExists()` — pre-flight checks for `claude`, `gh`/`glab`
  - `GitAdapter.getCurrentBranch()` — auto-detect issue ID
  - `GitAdapter.hasUncommittedChanges()` — clean working tree check
  - `GitAdapter.fetchAndReset()` — advance feature branch after squash merge
  - `GitAdapter.checkoutBranch()` — switch to feature branch after merge
  - `GitAdapter.stageAll()` + `GitAdapter.commit()` — commit uncommitted changes
  - `GitHubClient.mergePullRequest()` — merge GitHub PRs
  - `GitLabClient.mergeMergeRequest()` — merge GitLab MRs
  - `ReviewStateAdapter.read()` — read review-state.json
  - `ReviewStateAdapter.update()` — update review-state.json
  - `ConfigFileRepository.read()` — read project config

**Responsibilities:**
- All I/O is handled by existing adapters
- One addition may be needed: the shell script uses `gh pr merge --squash --delete-branch` but `GitHubClient.buildMergePrCommand` uses `--merge` (not `--squash`). See CLARIFY below.

**Estimated Effort:** 1-2 hours (mostly for any merge command adjustments)
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
- **Direct `ProcessAdapter.run` for claude invocation:** Rather than introducing a Claude-specific adapter, use `ProcessAdapter.run` with the appropriate command args (consistent with how other tools are invoked)

### Technology Choices

- **Frameworks/Libraries**: os-lib (file I/O), ujson (JSON parsing of claude output and review-state), existing adapters
- **Data Storage**: Reads/writes `review-state.json` and `tasks.md` via existing mechanisms
- **External Systems**: Claude CLI (`claude -p`), GitHub CLI (`gh`), GitLab CLI (`glab`), git

### Integration Points

- `model/BatchImplement` is called by `batch-implement.scala` for all decisions
- `batch-implement.scala` calls `MarkdownTaskParser.parsePhaseIndex` to find phases (requires CLARIFY resolution)
- `batch-implement.scala` calls `GitHubClient`/`GitLabClient` for PR merging based on `ForgeType`
- `batch-implement.scala` calls `ReviewStateAdapter` for reading/updating state
- `batch-implement.scala` calls `ProcessAdapter.run` for claude CLI invocation

## Technical Risks & Uncertainties

### CLARIFY: MarkdownTaskParser Location

`MarkdownTaskParser` is in `dashboard/` package. The `parsePhaseIndex` function is pure (takes `Seq[String]`, returns `List[PhaseIndexEntry]`), but commands should NOT import from `dashboard/`.

**Questions to answer:**
1. Should we move `parsePhaseIndex` (and `PhaseIndexEntry`) to `model/`?
2. If we move it, should we also move `parseTasks` and `extractPhaseName` (also pure)?
3. Or should we extract just `parsePhaseIndex` and `PhaseIndexEntry` and leave the rest?

**Options:**
- **Option A: Move `MarkdownTaskParser` entirely to `model/`**: Clean separation, all pure functions in `model/`. Dashboard code would import from `model/` instead. Requires updating all dashboard imports. Pros: architecturally correct, one-time cleanup. Cons: touches many files in dashboard.
- **Option B: Extract only `parsePhaseIndex` + `PhaseIndexEntry` to a new `model/` file**: Minimal change, duplicates the type/function boundary. Pros: small diff. Cons: splits related parsing logic across packages.
- **Option C: Accept the `dashboard/` import in this command**: Pragmatic, no refactoring. Pros: zero risk, fastest. Cons: violates the stated architectural rule.

**Impact:** Affects import structure of the new command and potentially dashboard code.

**Recommendation:** Option A. `MarkdownTaskParser` is entirely pure functions — it has no business in `dashboard/`. Moving it is a small, safe refactoring that fixes an existing architectural violation.

---

### CLARIFY: Squash Merge vs Regular Merge

The shell script uses `gh pr merge --squash --delete-branch` and `glab mr merge --squash --remove-source-branch --yes`. The existing `GitHubClient.buildMergePrCommand` uses `--merge` (regular merge), not `--squash`.

**Questions to answer:**
1. Should batch-implement always squash-merge (matching the shell script)?
2. Should the existing `GitHubClient.mergePullRequest` be updated to support squash, or should batch-implement build its own merge command?
3. Is the `--delete-branch` / `--remove-source-branch` flag important (phase branches should be cleaned up)?

**Options:**
- **Option A: Add squash+delete-branch flags to existing merge methods**: Changes the behavior of existing merge methods, which may be used elsewhere. Risky unless parameterized.
- **Option B: Add new squash-merge methods to GitHubClient/GitLabClient**: Keeps existing merge behavior unchanged, adds new methods. More code but safer.
- **Option C: Build merge command directly in batch-implement**: Bypass the client methods, build `ProcessAdapter.run(Seq("gh", "pr", "merge", ...))` directly. Pragmatic but duplicates pattern.

**Impact:** Affects PR merge behavior and phase branch cleanup.

**Recommendation:** Option B. Add `squashMergePullRequest` to `GitHubClient` and `squashMergeMergeRequest` to `GitLabClient`. The existing regular merge methods are used by other workflows and should not be changed.

---

### CLARIFY: Claude CLI Invocation Strategy

The shell script captures claude's JSON output and parses it for diagnostics (turns, cost, error). It also uses `--session-id` and `--resume` for recovery.

**Questions to answer:**
1. Should we capture JSON output (via `ProcessAdapter.run`) or stream output (via `ProcessAdapter.runStreaming`)?
2. The JSON output parsing is purely for logging — is it worth the complexity?
3. Should we use `--dangerously-skip-permissions` like the shell script, or make it configurable?

**Options:**
- **Option A: Capture JSON output, parse diagnostics**: Full parity with shell script. Requires `ProcessAdapter.run` with large output buffer and long timeout. Provides cost/turn tracking.
- **Option B: Stream output, skip diagnostics**: Simpler, user sees claude's output in real time. Loses cost/turn tracking but gains visibility. Uses `ProcessAdapter.runStreaming`.
- **Option C: Stream output + capture to log file**: Best of both — real-time visibility and a log record. More complex but closest to shell script behavior (which uses `2>/dev/null` to suppress stderr anyway).

**Impact:** Affects user experience during long-running phases and ability to track costs.

**Recommendation:** Option A for now. The JSON output with cost tracking is valuable for batch runs. The command already logs to a file, so real-time visibility is secondary. The timeout needs to be generous (30+ minutes per phase).

---

### CLARIFY: Remote Branch Deletion After Squash Merge

The shell script handles the case where the remote feature branch is deleted after squash merge (GitLab's `--remove-source-branch` behavior). It falls back to resetting to the base branch. The existing `GitAdapter.fetchAndReset` assumes the remote branch still exists.

**Questions to answer:**
1. Does `GitAdapter.fetchAndReset` fail gracefully when the remote branch doesn't exist?
2. Should we add a `remoteBranchExists` check to `GitAdapter`?
3. What base branch should we fall back to? The shell script reads it from review-state or defaults to `main`.

**Options:**
- **Option A: Check if remote branch exists before fetchAndReset, fall back to base branch**: Matches shell script behavior exactly. Requires adding a `remoteBranchExists` method to `GitAdapter`.
- **Option B: Always use fetchAndReset, handle its error by falling back**: Simpler, but error message parsing is fragile.

**Impact:** Determines whether batch-implement can handle GitLab's source branch deletion behavior.

**Recommendation:** Option A. Add a simple `remoteBranchExists` check to `GitAdapter` (one `git ls-remote` call).

---

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer: 3-5 hours
- Application Layer: 0 hours (N/A)
- Infrastructure Layer: 1-2 hours
- Presentation Layer: 6-10 hours

**Total Range:** 10 - 17 hours

**Confidence:** Medium

**Reasoning:**
- The shell script already defines the exact behavior, reducing design uncertainty
- Most I/O operations reuse existing adapters, reducing infrastructure risk
- The phase loop and recovery logic has many branches that need careful testing
- CLARIFY items (especially squash merge and MarkdownTaskParser location) could add or remove scope
- Claude CLI invocation with long timeouts may surface unexpected issues in testing

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
- Unit: Squash merge command building — correct flags for GitHub and GitLab
- Unit: `remoteBranchExists` — if added per CLARIFY resolution

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
- Decision on CLARIFY items (especially MarkdownTaskParser location)

### Layer Dependencies
- Domain layer must be implemented first (pure decision functions)
- Infrastructure changes (squash merge methods, remoteBranchExists) can be done in parallel with domain
- Command script depends on both domain and infrastructure layers
- Tests can be written alongside each layer

### External Blockers
- None. All dependencies are internal.

## Risks & Mitigations

### Risk 1: Claude CLI timeout behavior
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:** Use generous timeout (30+ minutes). If `ProcessAdapter.run` timeout is hit, treat it as a phase failure and enter recovery loop. Test with shorter timeouts first.

### Risk 2: Phase branch state after squash merge is complex
**Likelihood:** Medium
**Impact:** High
**Mitigation:** The shell script's fetch+reset logic is battle-tested. Port it faithfully, including the remote-branch-deleted fallback. Add explicit E2E test for this scenario.

### Risk 3: MarkdownTaskParser move breaks dashboard
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** If moving MarkdownTaskParser to `model/`, update all dashboard imports in the same commit. Run full test suite before and after.

---

## Implementation Sequence

**Recommended Layer Order:**

1. **Infrastructure Layer** — Add squash merge methods to `GitHubClient`/`GitLabClient`, add `remoteBranchExists` to `GitAdapter`. Small, self-contained changes with their own tests.
2. **Domain Layer** — Create `model/BatchImplement.scala` with pure decision functions + move `MarkdownTaskParser` to `model/` if decided. Full unit test coverage.
3. **Presentation Layer** — Create `.iw/commands/batch-implement.scala` orchestration script. E2E tests.

**Ordering Rationale:**
- Infrastructure changes are small and independent — good to get them in first
- Domain layer depends on the MarkdownTaskParser CLARIFY decision but not on infrastructure
- Command script depends on both other layers
- Infrastructure and domain layers can be parallelized

## Documentation Requirements

- [ ] Code documentation (inline comments for decision logic in BatchImplement)
- [ ] Command help text (usage line in the script header)
- [ ] Update CLAUDE.md if the MarkdownTaskParser move changes import patterns

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers — especially MarkdownTaskParser location and squash merge approach
2. Run **wf-create-tasks** with issue ID IW-275
3. Run **wf-implement** for layer-by-layer implementation
