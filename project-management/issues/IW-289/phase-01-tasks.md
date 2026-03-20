# Phase 1 Tasks: Pure decision logic for CI check results

## Setup

- [ ] [setup] Create `.iw/core/model/PhaseMerge.scala` with file header, package `iw.core.model`, and empty `PhaseMerge` object
- [ ] [setup] Create `.iw/core/test/PhaseMergeTest.scala` with file header, package `iw.core.application`, munit imports, and empty `PhaseMergeTest extends FunSuite`

## Tests (write first — TDD)

### Domain types

- [ ] [test] Write tests for `CICheckStatus` enum: verify all five values exist (`Passed`, `Failed`, `Pending`, `Cancelled`, `Unknown`)
- [ ] [test] Write tests for `CICheckResult` case class: construct with name + status, construct with name + status + URL
- [ ] [test] Write tests for `PhaseMergeConfig` defaults: timeout is 30 minutes, poll interval is 30 seconds, max retries is 2

### evaluateChecks

- [ ] [test] Write tests for `evaluateChecks` — empty and all-passed scenarios: empty list returns `NoChecksFound`; all `Passed` returns `AllPassed`; single `Passed` returns `AllPassed`
- [ ] [test] Write tests for `evaluateChecks` — failure scenarios: some `Failed` with others `Passed` returns `SomeFailed` carrying the failed checks; all `Cancelled` returns `SomeFailed`; mix of `Failed` and `Cancelled` returns `SomeFailed` with both
- [ ] [test] Write tests for `evaluateChecks` — pending scenarios: some `Pending` with none `Failed` returns `StillRunning`; single `Pending` returns `StillRunning`
- [ ] [test] Write tests for `evaluateChecks` — precedence: mix of `Pending` and `Failed` returns `SomeFailed` (failure takes priority over pending)
- [ ] [test] Write tests for `evaluateChecks` — `Unknown` handling: all `Unknown` returns `AllPassed`; `Unknown` mixed with `Passed` returns `AllPassed`; `Unknown` mixed with `Failed` returns `SomeFailed`

### shouldRetry

- [ ] [test] Write tests for `shouldRetry`: attempt 0 with max 2 returns true; attempt 1 with max 2 returns true; attempt 2 with max 2 returns false; attempt 5 with max 2 returns false; max retries 0 always returns false

### buildRecoveryPrompt

- [ ] [test] Write tests for `buildRecoveryPrompt`: single failed check includes name and status; multiple failed checks includes all names; checks with URLs include the URLs; checks without URLs produce a valid prompt

## Implementation

- [ ] [impl] Implement `CICheckStatus` enum with values: `Passed`, `Failed`, `Pending`, `Cancelled`, `Unknown`
- [ ] [impl] Implement `CICheckResult` case class: `name: String`, `status: CICheckStatus`, `url: Option[String] = None`
- [ ] [impl] Implement `CIVerdict` enum: `AllPassed`, `SomeFailed(failedChecks: List[CICheckResult])`, `StillRunning`, `NoChecksFound`, `TimedOut`
- [ ] [impl] Implement `PhaseMergeConfig` case class with defaults: `timeoutMs: Long = 1_800_000`, `pollIntervalMs: Long = 30_000`, `maxRetries: Int = 2`
- [ ] [impl] Implement `PhaseMerge.evaluateChecks(checks: List[CICheckResult]): CIVerdict` — empty → `NoChecksFound`; any `Failed`/`Cancelled` → `SomeFailed`; any `Pending` → `StillRunning`; otherwise → `AllPassed`
- [ ] [impl] Implement `PhaseMerge.shouldRetry(attempt: Int, config: PhaseMergeConfig): Boolean` — true when `attempt < config.maxRetries`
- [ ] [impl] Implement `PhaseMerge.buildRecoveryPrompt(failedChecks: List[CICheckResult]): String` — list each check's name, status, and URL (when present)

## Verification

- [ ] [verify] Compile with `-Werror`: `scala-cli compile --scalac-option -Werror .iw/core/`
- [ ] [verify] Run unit tests: `./iw test unit`
- [ ] [verify] Confirm no I/O imports in `PhaseMerge.scala` (no `scala.sys.process`, `java.io`, `adapters.*`)
