# Phase 1: Pure decision logic for CI check results

**Issue:** IW-289
**Phase:** 1 of 7
**Story:** Pure decision logic for CI check results (Story 2 from analysis)

## Goals

Create pure domain types and decision functions for evaluating CI check statuses. This is the foundation phase that all later phases depend on. No I/O — just types and pure functions that classify CI check results into actionable verdicts.

Follow the exact same pattern as `BatchImplement.scala` (pure object with decision functions, no I/O imports) and `DoctorChecks.scala` (enum structure with associated data).

## Scope

### In Scope

- `CICheckStatus` enum: Passed, Failed, Pending, Cancelled, Unknown
- `CICheckResult` case class: name, status, optional URL
- `CIVerdict` enum: AllPassed, SomeFailed(failedChecks), StillRunning, NoChecksFound, TimedOut
- `PhaseMergeConfig` case class: timeout, poll interval, max retries (with sensible defaults)
- `PhaseMerge` object with pure decision functions:
  - `evaluateChecks(checks: List[CICheckResult]): CIVerdict` — main decision function
  - `shouldRetry(attempt: Int, config: PhaseMergeConfig): Boolean`
  - `buildRecoveryPrompt(failedChecks: List[CICheckResult]): String`

### Out of Scope

- Any I/O (shell commands, file reading, network calls)
- CI polling logic (Phase 3)
- PR number extraction (Phase 2)
- GitHub/GitLab specific JSON parsing (Phase 3/6)
- Command script or argument parsing
- review-state.json updates

## Dependencies

- No prior phases needed (this is Phase 1)
- Uses existing codebase patterns from `model/` layer
- IW-274 (activity field) and IW-275 (batch-implement) are already merged

## Approach

1. **TDD:** Write failing tests first in `PhaseMergeTest.scala`, then implement in `PhaseMerge.scala`
2. **Follow `BatchImplement.scala` pattern:** Pure object with decision functions, no I/O imports
3. **Follow `DoctorChecks.scala` pattern:** Enum structure with associated data (e.g., `SomeFailed` carries failed check list)
4. All types and functions go in a single file: `.iw/core/model/PhaseMerge.scala`
5. All tests go in `.iw/core/test/PhaseMergeTest.scala`
6. Package: `iw.core.model`

**File headers:**

`.iw/core/model/PhaseMerge.scala`:
```scala
// PURPOSE: Pure decision functions for phase-merge CI check evaluation
// PURPOSE: Given CI check statuses, determines verdict (pass/fail/pending) without I/O
```

`.iw/core/test/PhaseMergeTest.scala`:
```scala
// PURPOSE: Unit tests for PhaseMerge model object
// PURPOSE: Tests pure decision functions for CI check evaluation
```

**Design decisions from analysis:**
- PRs with no CI checks configured → immediate merge (NoChecksFound triggers merge in later phases)
- `PhaseMergeConfig` defaults: timeout 30 minutes, poll interval 30 seconds, max retries 2
- `TimedOut` is a verdict the caller sets — `evaluateChecks` itself does not track time (it's pure)

## Files to Create/Modify

### Create

- `.iw/core/model/PhaseMerge.scala` — Domain types (`CICheckStatus`, `CICheckResult`, `CIVerdict`, `PhaseMergeConfig`) and pure decision functions (`PhaseMerge` object)
- `.iw/core/test/PhaseMergeTest.scala` — Comprehensive unit tests using munit

### No files to modify

This phase is purely additive.

## Testing Strategy

Pure unit tests only — no I/O, no mocking needed. Uses munit `FunSuite` matching existing test patterns (see `BatchImplementTest.scala`).

**`evaluateChecks` scenarios:**
- All checks Passed → `AllPassed`
- Some checks Failed, others Passed → `SomeFailed` with the failed checks listed
- Some checks Pending, none Failed → `StillRunning`
- Empty check list → `NoChecksFound`
- All checks Cancelled → `SomeFailed` (Cancelled treated as failure)
- Mix of Pending and Failed → `SomeFailed` (failure takes precedence over pending)
- Unknown status checks with no failures or pending → `AllPassed` (Unknown treated as non-blocking)
- Single check scenarios for each status

**`shouldRetry` scenarios:**
- Attempt 0, max retries 2 → true
- Attempt 1, max retries 2 → true
- Attempt 2, max retries 2 → false (reached limit)
- Attempt 5, max retries 2 → false
- Max retries 0 → never retry

**`buildRecoveryPrompt` scenarios:**
- Single failed check → includes check name and status
- Multiple failed checks → includes all names and statuses
- Failed checks with URLs → includes URLs in prompt
- Failed checks without URLs → still produces valid prompt

**`PhaseMergeConfig` defaults:**
- Verify default timeout is 30 minutes (in milliseconds or appropriate unit)
- Verify default poll interval is 30 seconds
- Verify default max retries is 2

**Verification commands:**
- `scala-cli compile --scalac-option -Werror .iw/core/` — must pass with no warnings
- `./iw test unit` — all tests pass

## Acceptance Criteria

- [ ] `PhaseMerge.scala` exists at `.iw/core/model/PhaseMerge.scala` with `package iw.core.model`
- [ ] Contains enums: `CICheckStatus`, `CIVerdict`; case class: `CICheckResult`, `PhaseMergeConfig`
- [ ] Contains `PhaseMerge` object with `evaluateChecks`, `shouldRetry`, `buildRecoveryPrompt`
- [ ] All `CIVerdict` outcomes covered by tests
- [ ] `evaluateChecks` correctly classifies all status combinations (including edge cases)
- [ ] `shouldRetry` respects max retries config
- [ ] `buildRecoveryPrompt` includes failed check names, statuses, and URLs when available
- [ ] `PhaseMergeConfig` has sensible defaults (30m timeout, 30s poll, 2 retries)
- [ ] No I/O imports in the model file (no `scala.sys.process`, no `java.io`, no `adapters.*`)
- [ ] Code compiles with `scala-cli compile --scalac-option -Werror .iw/core/`
- [ ] All unit tests pass via `./iw test unit`
