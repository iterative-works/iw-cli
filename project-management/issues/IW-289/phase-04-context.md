# Phase 4: Timeout and configurable polling

**Issue:** IW-289
**Phase:** 4 of 7
**Story:** Configurable timeout and polling interval (Story 5 from analysis)

## Goals

Add `--timeout` and `--poll-interval` CLI flags to `iw phase-merge` so users can tune the polling loop for different CI speeds. On timeout, set review-state activity to `"waiting"` and exit non-zero with a human-readable message.

## Scope

### In Scope

- **Pure duration parser** `PhaseMerge.parseDuration(input: String): Either[String, Long]` in `model/PhaseMerge.scala` — converts `"30s"`, `"5m"`, `"2h"` to milliseconds
- **CLI arg parsing** in `phase-merge.scala` — `--timeout <duration>` (default `"30m"`) and `--poll-interval <duration>` (default `"30s"`)
- **Improved timeout behavior** — on timeout, update review-state with `activity: "waiting"` before exiting
- **Human-readable timeout message** — print duration in the timeout error (e.g., "30m" not "1800000ms")
- **Unit tests** for `parseDuration`
- **E2E tests** for timeout and custom poll interval scenarios

### Out of Scope

- CI failure recovery / agent re-invocation (Phase 5)
- GitLab support (Phase 6)
- Any changes to the polling loop logic itself (already working from Phase 3)
- Retry configuration via CLI (maxRetries stays at default)

## Dependencies

- **Phase 1** — `PhaseMergeConfig`, `CIVerdict.TimedOut`
- **Phase 3** — `phase-merge.scala` command with working polling loop, `GitHubClient.fetchCheckStatuses`
- **Existing code:**
  - `PhaseArgs.namedArg(argList, "--flag-name")` for extracting named CLI arguments
  - `ReviewStateAdapter.update` for review-state transitions
  - `ReviewStateUpdater.UpdateInput` for specifying update fields (including `activity`)

## Approach

### Layer 1: Pure duration parser in model (TDD first)

Add `parseDuration` to `PhaseMerge` object in `.iw/core/model/PhaseMerge.scala`:

```scala
def parseDuration(input: String): Either[String, Long]
```

Accepted formats:
- `"30s"` → 30,000 ms
- `"5m"` → 300,000 ms
- `"2h"` → 7,200,000 ms
- Numeric-only (no suffix) → treat as seconds for ergonomics, or reject — decide during implementation
- Invalid input → `Left("Invalid duration: ...")` with clear error message

Add a companion formatting function for human-readable output:

```scala
def formatDuration(ms: Long): String
```

This converts milliseconds back to the most natural unit string (e.g., 1,800,000 → `"30m"`, 45,000 → `"45s"`).

### Layer 2: Wire CLI args in phase-merge.scala

1. Extract `--timeout` and `--poll-interval` from `argList` using `PhaseArgs.namedArg`
2. Parse each with `PhaseMerge.parseDuration`, exiting on parse error
3. Construct `PhaseMergeConfig(timeoutMs = ..., pollIntervalMs = ...)` from parsed values (falling back to defaults when flags are absent)
4. Replace `val mergeConfig = PhaseMergeConfig()` with the configured instance

### Layer 3: Improve timeout handling

In the timeout branch of the polling loop:
1. Update review-state with `activity = Some("waiting")` before exiting
2. Use `PhaseMerge.formatDuration` in the error message instead of raw milliseconds

Current timeout code (to be modified):
```scala
if elapsed > mergeConfig.timeoutMs then
  Output.error(s"Timed out waiting for CI checks after ${mergeConfig.timeoutMs / 1000}s.")
  Output.error(s"PR is at $prUrl. You can merge manually once CI passes.")
  sys.exit(1)
```

## Files to Create/Modify

### Modify

- `.iw/core/model/PhaseMerge.scala` — Add `parseDuration` and `formatDuration` pure functions
- `.iw/commands/phase-merge.scala` — Add `--timeout` and `--poll-interval` arg parsing, construct `PhaseMergeConfig` from parsed values, improve timeout handling with review-state update
- `.iw/core/test/PhaseMergeTest.scala` — Add unit tests for `parseDuration` and `formatDuration`
- `.iw/test/phase-merge.bats` — Add E2E tests for timeout and poll-interval flags

### No new files expected

This is a small phase — all changes fit into existing files.

## Testing Strategy

### Unit tests (pure, in PhaseMergeTest.scala)

**`parseDuration` scenarios:**
- `"30s"` → `Right(30_000L)`
- `"5m"` → `Right(300_000L)`
- `"2h"` → `Right(7_200_000L)`
- `"0s"` → `Right(0L)` (edge case — zero is valid)
- `""` → `Left(...)` (empty input)
- `"abc"` → `Left(...)` (no number)
- `"30x"` → `Left(...)` (unknown suffix)
- `"-5m"` → `Left(...)` (negative duration)
- `"30"` → decide: either `Left(...)` or treat as seconds

**`formatDuration` scenarios:**
- `1_800_000L` → `"30m"`
- `30_000L` → `"30s"`
- `7_200_000L` → `"2h"`
- `90_000L` → `"90s"` or `"1m 30s"` (decide during implementation)

### E2E tests (BATS, in phase-merge.bats)

**Timeout scenario:**
- Setup: mock `gh` that always returns pending checks JSON
- Run `iw phase-merge --timeout 2s --poll-interval 1s`
- Verify: exits non-zero, output contains timeout message
- Verify: review-state.json has `activity: "waiting"`

**Custom poll interval:**
- Setup: mock `gh` that returns pending first, then all-pass
- Run `iw phase-merge --poll-interval 1s --timeout 10s`
- Verify: succeeds (merges), took at least 1s but less than 10s

**Invalid duration flag:**
- Run `iw phase-merge --timeout abc`
- Verify: exits non-zero with parse error message

**All E2E tests must export `IW_SERVER_DISABLED=1` in setup().**

### Verification commands

- `scala-cli compile --scalac-option -Werror .iw/core/` — no warnings
- `./iw test unit` — all tests pass
- `./iw test e2e` — all E2E tests pass

## Acceptance Criteria

- [ ] `PhaseMerge.parseDuration` correctly parses `Ns`, `Nm`, `Nh` formats to milliseconds
- [ ] `PhaseMerge.parseDuration` returns `Left` for invalid inputs (empty, no number, unknown suffix, negative)
- [ ] `PhaseMerge.formatDuration` converts milliseconds back to human-readable duration string
- [ ] `phase-merge` accepts `--timeout <duration>` flag (default `"30m"`)
- [ ] `phase-merge` accepts `--poll-interval <duration>` flag (default `"30s"`)
- [ ] Invalid duration flags produce clear error messages and non-zero exit
- [ ] On timeout, review-state is updated with `activity: "waiting"` before exit
- [ ] Timeout error message uses human-readable duration (e.g., `"30m"` not `"1800s"`)
- [ ] No compilation warnings with `-Werror`
- [ ] All existing tests still pass
