# Phase 5 Tasks: CI failure recovery via agent re-invocation

**Issue:** IW-289
**Phase:** 5 of 7

## Setup

- [ ] [setup] Review `PhaseMerge.shouldRetry`, `PhaseMerge.buildRecoveryPrompt`, and `PhaseMergeConfig.maxRetries` from Phase 1 to confirm signatures
- [ ] [setup] Review `batch-implement.scala` `attemptRecovery` / `claudeCmd` pattern for agent invocation via `ProcessAdapter.runInteractive`
- [ ] [setup] Review `phase-merge.scala` polling loop (`SomeFailed` case) to plan restructuring
- [ ] [setup] Verify Phase 4 code compiles and tests pass: `scala-cli compile --scalac-option -Werror .iw/core/` and `./iw test unit`

## Tests (Write First — TDD)

### Layer 1: No new pure model tests expected

`shouldRetry` and `buildRecoveryPrompt` are already tested from Phase 1. If new pure logic is added during implementation, add tests here.

## Implementation

### Layer 1: Add `--max-retries` CLI flag parsing

- [ ] [impl] Extract `--max-retries` from `argList` using `PhaseArgs.namedArg`, default `"2"`
- [ ] [impl] Validate parsed value is a non-negative integer; exit with clear error message on invalid input (e.g., `"abc"`, negative)
- [ ] [impl] Pass `maxRetries` to `PhaseMergeConfig` constructor alongside `timeoutMs` and `pollIntervalMs`
- [ ] [impl] Compile command: `scala-cli compile .iw/commands/phase-merge.scala`

### Layer 2: Restructure polling loop for retry support

- [ ] [impl] Change `poll()` to return `CIVerdict` instead of calling `sys.exit(1)` on `SomeFailed` — return the verdict and let the caller decide
- [ ] [impl] Keep `AllPassed` and `NoChecksFound` cases returning their verdicts (caller proceeds to merge)
- [ ] [impl] Keep `StillRunning` tail-recursive within `poll()` (re-polls after sleep)
- [ ] [impl] Keep timeout check returning `CIVerdict.TimedOut` or exiting (same pattern as Phase 4)
- [ ] [impl] Add outer tail-recursive retry loop `def retryLoop(attempt: Int): Unit` that wraps `poll()` calls
- [ ] [impl] On `poll()` returning `SomeFailed`: call `PhaseMerge.shouldRetry(attempt, mergeConfig)` to decide next step
- [ ] [impl] If retry allowed: invoke agent, increment attempt, re-enter `poll()` via `retryLoop`
- [ ] [impl] If retries exhausted: update review-state with `activity: "waiting"`, exit non-zero
- [ ] [impl] Compile with `-Werror`

### Layer 3: Agent invocation

- [ ] [impl] Build recovery prompt using `PhaseMerge.buildRecoveryPrompt(failedChecks)` with PR context (URL, branch)
- [ ] [impl] Invoke `claude --dangerously-skip-permissions -p "<prompt>"` via `ProcessAdapter.runInteractive` (no timeout), following `batch-implement.scala` `claudeCmd` pattern
- [ ] [impl] After agent exits (regardless of exit code), re-enter polling loop — CI re-check determines outcome
- [ ] [impl] Compile with `-Werror`

### Layer 4: Review-state transitions

- [ ] [impl] On `SomeFailed` with retries remaining: update review-state to `ci_fixing`, displayText `"Phase N: CI Fixing (attempt M/K)"`
- [ ] [impl] After agent completes: update review-state to `ci_pending`, displayText `"Phase N: Waiting for CI"`
- [ ] [impl] On retries exhausted: update review-state with `activity: "waiting"` (same pattern as timeout in Phase 4)
- [ ] [impl] Compile with `-Werror`

## E2E Tests (BATS)

- [ ] [e2e] Agent recovery succeeds: mock `gh` returns failing checks first call then all-passing second call; mock `claude` exits 0; run `iw phase-merge --timeout 30s --poll-interval 1s --max-retries 1`; verify exit 0 (merge succeeds), output contains recovery messages (e.g., "CI Fixing")
- [ ] [e2e] Retries exhausted: mock `gh` always returns failing checks; mock `claude` exits 0; run `iw phase-merge --timeout 30s --poll-interval 1s --max-retries 1`; verify non-zero exit, review-state.json has `activity: "waiting"`, output contains exhaustion message
- [ ] [e2e] Zero retries (opt-out): mock `gh` returns failing checks; run `iw phase-merge --max-retries 0`; verify non-zero exit immediately (no agent invocation), same behavior as Phase 3/4
- [ ] [e2e] Invalid `--max-retries` flag: run `iw phase-merge --max-retries abc`; verify non-zero exit with parse error message
- [ ] [e2e] All E2E tests export `IW_SERVER_DISABLED=1` in setup()
- [ ] [e2e] Run E2E tests: `./iw test e2e`

## Integration & Verification

- [ ] [verify] Compile core with `-Werror`: `scala-cli compile --scalac-option -Werror .iw/core/`
- [ ] [verify] All unit tests pass: `./iw test unit`
- [ ] [verify] All E2E tests pass: `./iw test e2e`
- [ ] [verify] Existing phase-merge, phase-pr, and batch-implement tests still pass
- [ ] [verify] No new I/O imports in `PhaseMerge.scala` model (all new logic is in the command script)
