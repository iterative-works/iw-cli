# Phase 5: CI failure recovery via agent re-invocation

**Issue:** IW-289
**Phase:** 5 of 7
**Story:** CI failure recovery via agent (Story 3 from analysis)

## Goals

When CI checks fail, instead of immediately exiting, `iw phase-merge` should invoke the claude CLI agent with a recovery prompt describing the failures. The agent fixes the issue and pushes. The command then re-polls CI. This retry loop continues up to `maxRetries` times. If retries are exhausted, set review-state `activity: "waiting"` and exit non-zero.

## Scope

### In Scope

- **`--max-retries` CLI flag** in `phase-merge.scala` — parsed with `PhaseArgs.namedArg`, default `"2"`, passed to `PhaseMergeConfig.maxRetries`
- **Retry loop** around the `SomeFailed` case in the polling loop — calls `PhaseMerge.shouldRetry`, invokes claude agent, then re-polls
- **Agent invocation** via `ProcessAdapter.runInteractive` (no timeout) with a prompt built by `PhaseMerge.buildRecoveryPrompt`
- **Review-state transitions** — `ci_fixing` while agent runs, `ci_pending` after agent completes (back to polling), `activity: "waiting"` on retries exhausted
- **Unit tests** for any new pure functions added (if needed)
- **E2E tests** for recovery scenarios (agent succeeds, retries exhausted)

### Out of Scope

- GitLab support (Phase 6)
- Changes to `PhaseMerge.shouldRetry` or `buildRecoveryPrompt` signatures (already exist from Phase 1)
- Changes to the happy path (AllPassed → merge) or timeout behavior (Phase 4)
- Improving the recovery prompt beyond what `buildRecoveryPrompt` already produces
- Any new pure model types — existing `PhaseMergeConfig.maxRetries`, `shouldRetry`, and `buildRecoveryPrompt` cover the pure logic

## Dependencies

- **Phase 1** — `PhaseMerge.shouldRetry`, `PhaseMerge.buildRecoveryPrompt`, `PhaseMergeConfig.maxRetries`
- **Phase 3** — `phase-merge.scala` command with working polling loop, `SomeFailed` case
- **Phase 4** — `--timeout` and `--poll-interval` CLI flags, timeout with `activity: "waiting"` pattern
- **Existing code:**
  - `ProcessAdapter.runInteractive` for long-running interactive processes (no timeout)
  - `batch-implement.scala` `attemptRecovery` / `claudeCmd` pattern for agent invocation
  - `PhaseArgs.namedArg(argList, "--flag-name")` for named CLI arguments
  - `ReviewStateAdapter.update` and `ReviewStateUpdater.UpdateInput` for review-state transitions

## Approach

### Layer 1: Add `--max-retries` CLI flag

Parse `--max-retries` from `argList` using `PhaseArgs.namedArg`. Default to `"2"`. Validate that it is a non-negative integer. Construct `PhaseMergeConfig` with all three values (timeout, pollInterval, maxRetries).

### Layer 2: Restructure polling loop to support retries

The current polling loop is a `@annotation.tailrec def poll(): Unit` that calls `sys.exit(1)` on `SomeFailed`. Restructure it to:

1. Make `poll()` return a `CIVerdict` instead of calling `sys.exit` for the `SomeFailed` case
2. Wrap the poll in an outer retry loop that tracks `attempt: Int`
3. On `SomeFailed`:
   a. Call `PhaseMerge.shouldRetry(attempt, mergeConfig)`
   b. If retry allowed: update review-state to `ci_fixing`, invoke agent, update review-state to `ci_pending`, increment attempt, re-enter poll
   c. If retries exhausted: update review-state with `activity: "waiting"`, exit non-zero

The outer retry loop should be tail-recursive to match the existing codebase style.

### Layer 3: Agent invocation

Follow the `batch-implement.scala` pattern:

1. Build the recovery prompt using `PhaseMerge.buildRecoveryPrompt(failedChecks)`
2. Prepend context about the PR (URL, branch) so the agent knows what it is fixing
3. Invoke `claude --dangerously-skip-permissions -p "<prompt>"` via `ProcessAdapter.runInteractive`
4. After the agent exits (regardless of exit code), re-enter the polling loop — the agent may or may not have pushed fixes; the CI re-check will determine the outcome

### Layer 4: Review-state transitions

- On `SomeFailed` with retries remaining: update status to `ci_fixing`, displayText to `"Phase N: CI Fixing (attempt M/K)"`
- After agent completes: update status to `ci_pending`, displayText to `"Phase N: Waiting for CI"`
- On retries exhausted: update `activity: "waiting"` (same pattern as timeout in Phase 4)

## Files to Create/Modify

### Modify

- `.iw/commands/phase-merge.scala` — Add `--max-retries` flag parsing, restructure `SomeFailed` case into retry loop with agent invocation, add review-state transitions for `ci_fixing`/`ci_pending`
- `.iw/core/test/PhaseMergeTest.scala` — Add tests for any new pure functions (if needed; existing `shouldRetry` and `buildRecoveryPrompt` are already tested)
- `.iw/test/phase-merge.bats` — Add E2E tests for recovery scenarios

### No new files expected

All changes fit into existing files.

## Testing Strategy

### Unit tests (pure, in PhaseMergeTest.scala)

The pure functions `shouldRetry` and `buildRecoveryPrompt` were already tested in Phase 1. If new pure logic is added during implementation, add corresponding unit tests. No new unit tests are expected a priori.

### E2E tests (BATS, in phase-merge.bats)

**Agent recovery succeeds scenario:**
- Setup: mock `gh` that returns failing checks on first call, then all-passing on second call
- Setup: mock `claude` that "succeeds" (exits 0)
- Run `iw phase-merge --timeout 30s --poll-interval 1s --max-retries 1`
- Verify: exits 0 (merge succeeds)
- Verify: output contains recovery-related messages (e.g., "CI Fixing")

**Retries exhausted scenario:**
- Setup: mock `gh` that always returns failing checks
- Setup: mock `claude` that exits 0 but doesn't fix anything
- Run `iw phase-merge --timeout 30s --poll-interval 1s --max-retries 1`
- Verify: exits non-zero
- Verify: review-state.json has `activity: "waiting"`
- Verify: output contains exhaustion message

**Zero retries (opt-out of recovery) scenario:**
- Setup: mock `gh` that returns failing checks
- Run `iw phase-merge --max-retries 0`
- Verify: exits non-zero immediately (no agent invocation)
- Verify: same behavior as current Phase 3/4 (no recovery attempt)

**Invalid --max-retries flag:**
- Run `iw phase-merge --max-retries abc`
- Verify: exits non-zero with parse error message

**All E2E tests must export `IW_SERVER_DISABLED=1` in setup().**

### Verification commands

- `scala-cli compile --scalac-option -Werror .iw/core/` — no warnings
- `./iw test unit` — all tests pass
- `./iw test e2e` — all E2E tests pass

## Acceptance Criteria

- [ ] `phase-merge` accepts `--max-retries <N>` flag (default `2`)
- [ ] Invalid `--max-retries` value produces clear error message and non-zero exit
- [ ] `--max-retries 0` disables recovery (exits immediately on CI failure, same as Phase 3/4 behavior)
- [ ] On `SomeFailed` with retries remaining: invokes claude agent with recovery prompt containing failed check names and URLs
- [ ] Review-state transitions: `ci_fixing` during agent run, `ci_pending` after agent completes
- [ ] After agent completes, re-polls CI checks (does not blindly assume success)
- [ ] On CI pass after recovery: merges PR as usual (happy path unchanged)
- [ ] On CI fail after recovery: retries up to `maxRetries` times
- [ ] On retries exhausted: sets `activity: "waiting"` in review-state and exits non-zero
- [ ] Agent invocation uses `ProcessAdapter.runInteractive` (no timeout)
- [ ] No compilation warnings with `-Werror`
- [ ] All existing tests still pass
