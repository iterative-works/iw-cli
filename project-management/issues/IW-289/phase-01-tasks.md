# Phase 1 Tasks: Pure decision logic for CI check results

## Setup

- [x] [setup] Create `.iw/core/model/PhaseMerge.scala` with file header, package `iw.core.model`, and empty `PhaseMerge` object
- [x] [setup] Create `.iw/core/test/PhaseMergeTest.scala` with file header, package `iw.core.application`, munit imports, and empty `PhaseMergeTest extends FunSuite`

## Tests (write first — TDD)

### Domain types

- [x] [test] Write tests for `CICheckStatus` enum: verify all five values exist (`Passed`, `Failed`, `Pending`, `Cancelled`, `Unknown`)
- [x] [test] Write tests for `CICheckResult` case class: construct with name + status, construct with name + status + URL
- [x] [test] Write tests for `PhaseMergeConfig` defaults: timeout is 30 minutes, poll interval is 30 seconds, max retries is 2

### evaluateChecks

- [x] [test] Write tests for `evaluateChecks` — empty and all-passed scenarios: empty list returns `NoChecksFound`; all `Passed` returns `AllPassed`; single `Passed` returns `AllPassed`
- [x] [test] Write tests for `evaluateChecks` — failure scenarios: some `Failed` with others `Passed` returns `SomeFailed` carrying the failed checks; all `Cancelled` returns `SomeFailed`; mix of `Failed` and `Cancelled` returns `SomeFailed` with both
- [x] [test] Write tests for `evaluateChecks` — pending scenarios: some `Pending` with none `Failed` returns `StillRunning`; single `Pending` returns `StillRunning`
- [x] [test] Write tests for `evaluateChecks` — precedence: mix of `Pending` and `Failed` returns `SomeFailed` (failure takes priority over pending)
- [x] [test] Write tests for `evaluateChecks` — `Unknown` handling: all `Unknown` returns `AllPassed`; `Unknown` mixed with `Passed` returns `AllPassed`; `Unknown` mixed with `Failed` returns `SomeFailed`

### shouldRetry

- [x] [test] Write tests for `shouldRetry`: attempt 0 with max 2 returns true; attempt 1 with max 2 returns true; attempt 2 with max 2 returns false; attempt 5 with max 2 returns false; max retries 0 always returns false

### buildRecoveryPrompt

- [x] [test] Write tests for `buildRecoveryPrompt`: single failed check includes name and status; multiple failed checks includes all names; checks with URLs include the URLs; checks without URLs produce a valid prompt

## Implementation

- [x] [impl] Implement `CICheckStatus` enum with values: `Passed`, `Failed`, `Pending`, `Cancelled`, `Unknown`
- [x] [impl] Implement `CICheckResult` case class: `name: String`, `status: CICheckStatus`, `url: Option[String] = None`
- [x] [impl] Implement `CIVerdict` enum: `AllPassed`, `SomeFailed(failedChecks: List[CICheckResult])`, `StillRunning`, `NoChecksFound`, `TimedOut`
- [x] [impl] Implement `PhaseMergeConfig` case class with defaults: `timeoutMs: Long = 1_800_000`, `pollIntervalMs: Long = 30_000`, `maxRetries: Int = 2`
- [x] [impl] Implement `PhaseMerge.evaluateChecks(checks: List[CICheckResult]): CIVerdict` — empty → `NoChecksFound`; any `Failed`/`Cancelled` → `SomeFailed`; any `Pending` → `StillRunning`; otherwise → `AllPassed`
- [x] [impl] Implement `PhaseMerge.shouldRetry(attempt: Int, config: PhaseMergeConfig): Boolean` — true when `attempt < config.maxRetries`
- [x] [impl] Implement `PhaseMerge.buildRecoveryPrompt(failedChecks: List[CICheckResult]): String` — list each check's name, status, and URL (when present)

## Verification

- [x] [verify] Compile with `-Werror`: `scala-cli compile --scalac-option -Werror .iw/core/`
- [x] [verify] Run unit tests: `./iw test unit`
- [x] [verify] Confirm no I/O imports in `PhaseMerge.scala` (no `scala.sys.process`, `java.io`, `adapters.*`)
