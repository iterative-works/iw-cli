---
generated_from: af2947ec5bbdf02960142da4a312663f6f7d2119
generated_at: 2026-03-21T09:57:46Z
branch: IW-289-phase-04
issue_id: IW-289
phase: 4
files_analyzed:
  - .iw/commands/phase-merge.scala
  - .iw/core/model/PhaseMerge.scala
  - .iw/core/test/PhaseMergeTest.scala
  - .iw/test/phase-merge.bats
---

# Review Packet: Phase 4 - Timeout and Configurable Polling

## Goals

This phase adds `--timeout` and `--poll-interval` flags to `iw phase-merge`, making the polling
loop tunable for different CI speeds. It also improves the timeout exit path: review-state is
updated to `activity: "waiting"` before exiting, and the error message prints a human-readable
duration instead of raw milliseconds.

Key objectives:

- Add `PhaseMerge.parseDuration` — pure function converting `"Ns"`, `"Nm"`, `"Nh"` strings to
  milliseconds, with clear `Left` errors for invalid input
- Add `PhaseMerge.formatDuration` — companion pure function converting milliseconds back to the
  most natural unit string for human-readable output
- Wire `--timeout` and `--poll-interval` CLI flags in `phase-merge.scala`, parsing with
  `parseDuration` and constructing `PhaseMergeConfig` from the results
- On timeout, update `review-state.json` with `activity: "waiting"` before exiting non-zero

## Scenarios

- [ ] `phase-merge --timeout 30m` stops polling after 30 minutes and exits non-zero
- [ ] `phase-merge --poll-interval 60s` waits 60 seconds between CI polls
- [ ] On timeout, review-state is updated with `activity: "waiting"` before exit
- [ ] Timeout error message prints the human-readable duration (e.g., `"30m"` not `"1800000ms"`)
- [ ] `phase-merge --timeout abc` exits non-zero with a parse error message
- [ ] `phase-merge --poll-interval -5m` exits non-zero with a parse error message
- [ ] `parseDuration` accepts `Ns`, `Nm`, `Nh` formats and rejects empty, no-number, unknown suffix, and negative inputs
- [ ] `formatDuration` picks the most natural unit (hours if exact, minutes if exact, otherwise seconds)
- [ ] All defaults remain unchanged: timeout 30m, poll interval 30s

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/model/PhaseMerge.scala` | `parseDuration`, `formatDuration` | Pure logic added this phase; verify correctness and edge-case handling |
| `.iw/commands/phase-merge.scala` | `phaseMerge` — arg parsing block | Where `--timeout` and `--poll-interval` are extracted and validated |
| `.iw/commands/phase-merge.scala` | `poll()` — timeout branch | Where `activity: "waiting"` update and `formatDuration` message are applied |
| `.iw/core/test/PhaseMergeTest.scala` | `parseDuration` / `formatDuration` test suite | Verify all unit-test cases, especially edge cases |
| `.iw/test/phase-merge.bats` | timeout and poll-interval tests | Verify E2E behaviour with mock `gh` |

## Diagrams

### Data flow: flag parsing to polling loop

```
CLI args
   │
   ├─ PhaseArgs.namedArg("--timeout")      → default "30m"
   │       │
   │       └─ PhaseMerge.parseDuration ──────────────────────┐
   │                                                          │
   ├─ PhaseArgs.namedArg("--poll-interval") → default "30s"  │
   │       │                                                  │
   │       └─ PhaseMerge.parseDuration                        │
   │                                                          ▼
   └──────────────────────────────────────► PhaseMergeConfig(timeoutMs, pollIntervalMs)
                                                     │
                                                     ▼
                                              poll() loop
                                            ┌───────────────┐
                                            │ elapsed check  │──timeout──► ReviewStateAdapter.update(activity="waiting")
                                            │               │              PhaseMerge.formatDuration(timeoutMs) in msg
                                            │ fetch checks  │              sys.exit(1)
                                            │               │
                                            │ Thread.sleep  │
                                            │ (pollInterval)│
                                            └───────────────┘
```

### `parseDuration` decision tree

```
input
  ├─ empty            → Left("must not be empty")
  ├─ last char = 's'  → parse number → multiply × 1_000
  ├─ last char = 'm'  → parse number → multiply × 60_000
  ├─ last char = 'h'  → parse number → multiply × 3_600_000
  └─ other suffix     → Left("unknown suffix ...")
         │
         └─ number part:
              ├─ not parseable as Long → Left("numeric value required")
              └─ negative              → Left("must not be negative")
```

## Test Summary

### Unit tests (`PhaseMergeTest.scala`)

| Test | Type | Status |
|------|------|--------|
| `parseDuration seconds: 30s returns Right(30_000L)` | Unit | New |
| `parseDuration minutes: 5m returns Right(300_000L)` | Unit | New |
| `parseDuration hours: 2h returns Right(7_200_000L)` | Unit | New |
| `parseDuration zero: 0s returns Right(0L)` | Unit | New |
| `parseDuration empty string returns Left` | Unit | New |
| `parseDuration no number: abc returns Left` | Unit | New |
| `parseDuration unknown suffix: 30x returns Left` | Unit | New |
| `parseDuration negative duration: -5m returns Left` | Unit | New |
| `parseDuration bare number without suffix: 30 returns Left` | Unit | New |
| `formatDuration 30_000L returns 30s` | Unit | New |
| `formatDuration 300_000L returns 5m` | Unit | New |
| `formatDuration 7_200_000L returns 2h` | Unit | New |
| `formatDuration 1_800_000L returns 30m` | Unit | New |
| `formatDuration non-round 90_000L stays in seconds: 90s` | Unit | New |
| `formatDuration 0L returns 0s` | Unit | New |

### E2E tests (`phase-merge.bats`)

| Test | Type | Status |
|------|------|--------|
| `phase-merge --timeout triggers timeout and sets review-state activity to waiting` | E2E | New |
| `phase-merge --poll-interval succeeds with pending then passing checks` | E2E | New |
| `phase-merge --timeout with invalid duration exits non-zero with parse error` | E2E | New |

All existing tests from Phase 3 are unchanged and must still pass.

## Files Changed

| File | Change |
|------|--------|
| `.iw/core/model/PhaseMerge.scala` | Added `parseDuration` and `formatDuration` pure functions |
| `.iw/commands/phase-merge.scala` | Added `--timeout` / `--poll-interval` arg parsing; updated `PhaseMergeConfig` construction; improved timeout branch to update review-state and use `formatDuration` |
| `.iw/core/test/PhaseMergeTest.scala` | Added 15 unit tests covering `parseDuration` and `formatDuration` |
| `.iw/test/phase-merge.bats` | Added 3 E2E tests for timeout, custom poll interval, and invalid duration flag |

<details>
<summary>Diff summary</summary>

### `.iw/core/model/PhaseMerge.scala`

Two pure functions added to the `PhaseMerge` object:

- `parseDuration(input: String): Either[String, Long]` — parses `Ns`/`Nm`/`Nh` suffixed strings;
  rejects empty input, missing suffix, unknown suffix, and negative values with descriptive `Left`
  messages.
- `formatDuration(ms: Long): String` — converts milliseconds to the most natural unit: hours if
  evenly divisible by 3,600,000; minutes if evenly divisible by 60,000; otherwise seconds.

### `.iw/commands/phase-merge.scala`

Early in `phaseMerge`:
- Extracts `--timeout` (default `"30m"`) and `--poll-interval` (default `"30s"`) using
  `PhaseArgs.namedArg`.
- Parses each with `PhaseMerge.parseDuration`; exits non-zero on `Left` with a clear message.
- Replaces `PhaseMergeConfig()` with `PhaseMergeConfig(timeoutMs = timeoutMs, pollIntervalMs = pollIntervalMs)`.

In the `poll()` timeout branch:
- Calls `ReviewStateAdapter.update(reviewStatePath, UpdateInput(activity = Some("waiting")))` if
  the review-state file exists; logs a warning on update failure rather than crashing.
- Uses `PhaseMerge.formatDuration(mergeConfig.timeoutMs)` in the error message.

### `.iw/core/test/PhaseMergeTest.scala`

15 new unit tests appended after the `buildRecoveryPrompt` block, organized in sections:
`parseDuration` valid inputs, `parseDuration` invalid inputs, and `formatDuration`.

### `.iw/test/phase-merge.bats`

3 new BATS tests appended to the existing suite:
1. Timeout test — mock `gh` always returns `PENDING`; expects exit 1, timeout message in output,
   and `"waiting"` in review-state.json.
2. Poll interval test — mock `gh` returns `PENDING` on first call then `SUCCESS`; expects exit 0.
3. Invalid flag test — passes `--timeout abc`; expects exit 1 and "Invalid" in output.

</details>
