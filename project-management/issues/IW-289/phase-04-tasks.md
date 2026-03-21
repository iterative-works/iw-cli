# Phase 4 Tasks: Timeout and configurable polling

**Issue:** IW-289
**Phase:** 4 of 7

## Setup

- [x] [setup] Review existing `PhaseMergeConfig`, `PhaseMerge` object, and `phase-merge.scala` polling loop to understand current timeout handling
- [x] [setup] Review `PhaseArgs.namedArg` usage patterns in existing commands (e.g., `phase-pr.scala`) for CLI arg extraction
- [x] [setup] Verify Phase 3 code compiles and tests pass: `scala-cli compile --scalac-option -Werror .iw/core/` and `./iw test unit`

## Tests (Write First — TDD)

### Layer 1: Pure duration parser — `parseDuration`

- [x] [test] parseDuration: valid seconds `"30s"` → `Right(30_000L)`
- [x] [test] parseDuration: valid minutes `"5m"` → `Right(300_000L)`
- [x] [test] parseDuration: valid hours `"2h"` → `Right(7_200_000L)`
- [x] [test] parseDuration: zero `"0s"` → `Right(0L)`
- [x] [test] parseDuration: empty string `""` → `Left(...)`
- [x] [test] parseDuration: no number `"abc"` → `Left(...)`
- [x] [test] parseDuration: unknown suffix `"30x"` → `Left(...)`
- [x] [test] parseDuration: negative duration `"-5m"` → `Left(...)`
- [x] [test] parseDuration: bare number `"30"` → decide and test (either seconds or reject)
- [x] [test] Run tests to confirm they fail (function doesn't exist yet)

### Layer 1b: Duration formatter — `formatDuration`

- [x] [test] formatDuration: `30_000L` → `"30s"`
- [x] [test] formatDuration: `300_000L` → `"5m"`
- [x] [test] formatDuration: `7_200_000L` → `"2h"`
- [x] [test] formatDuration: `1_800_000L` → `"30m"`
- [x] [test] formatDuration: non-round value `90_000L` → `"90s"` (stays in seconds)
- [x] [test] formatDuration: `0L` → `"0s"`
- [x] [test] Run tests to confirm they fail (function doesn't exist yet)

## Implementation

### Layer 1: Pure functions in model

- [x] [impl] Add `parseDuration(input: String): Either[String, Long]` to `PhaseMerge` object in `model/PhaseMerge.scala`
- [x] [impl] Accept `Ns`, `Nm`, `Nh` suffixes; reject empty, negative, unknown suffix, non-numeric
- [x] [impl] Add `formatDuration(ms: Long): String` to `PhaseMerge` object — picks most natural unit (hours if divisible by 3_600_000, minutes if divisible by 60_000, else seconds)
- [x] [impl] Run Layer 1 tests to confirm they pass
- [x] [impl] Compile with `-Werror`

### Layer 2: Wire CLI args in phase-merge.scala

- [x] [impl] Extract `--timeout` from `argList` using `PhaseArgs.namedArg`, default `"30m"`
- [x] [impl] Extract `--poll-interval` from `argList` using `PhaseArgs.namedArg`, default `"30s"`
- [x] [impl] Parse each with `PhaseMerge.parseDuration`, exit with clear error message on parse failure
- [x] [impl] Construct `PhaseMergeConfig(timeoutMs = ..., pollIntervalMs = ...)` from parsed values, replacing current `PhaseMergeConfig()`
- [x] [impl] Compile command: `scala-cli compile .iw/commands/phase-merge.scala`

### Layer 3: Improve timeout handling

- [x] [impl] On timeout, update review-state with `activity = Some("waiting")` before exiting
- [x] [impl] Replace raw milliseconds in timeout error message with `PhaseMerge.formatDuration` (e.g., `"30m"` instead of `"1800s"`)
- [x] [impl] Compile with `-Werror`

## E2E Tests (BATS)

- [x] [e2e] Timeout scenario: mock `gh` always returns pending checks; run `iw phase-merge --timeout 2s --poll-interval 1s`; verify non-zero exit, timeout message in output, review-state has `activity: "waiting"`
- [x] [e2e] Custom poll interval: mock `gh` returns pending first call then all-pass second call; run `iw phase-merge --poll-interval 1s --timeout 10s`; verify success (merge completes)
- [x] [e2e] Invalid duration flag: run `iw phase-merge --timeout abc`; verify non-zero exit with parse error message
- [x] [e2e] All E2E tests export `IW_SERVER_DISABLED=1` in setup()
- [x] [e2e] Run E2E tests: `./iw test e2e`

## Integration & Verification

- [x] [verify] Compile core with `-Werror`: `scala-cli compile --scalac-option -Werror .iw/core/`
- [x] [verify] All unit tests pass: `./iw test unit`
- [x] [verify] All E2E tests pass: `./iw test e2e`
- [x] [verify] Existing phase-merge, phase-pr, and batch-implement tests still pass
- [x] [verify] No I/O imports in `PhaseMerge.scala` model (parseDuration and formatDuration are pure)
**Phase Status:** Complete
