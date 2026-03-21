---
generated_from: 129a519c4b5ea9daf6fb687213872c2dfd6bed02
generated_at: 2026-03-20T19:49:48Z
branch: IW-289-phase-01
issue_id: IW-289
phase: 1
files_analyzed:
  - .iw/core/model/PhaseMerge.scala
  - .iw/core/test/PhaseMergeTest.scala
---

# Review Packet: Phase 1 - Pure CI Check Decision Logic

## Goals

This phase establishes the pure domain foundation for the `iw phase-merge` command. It
introduces domain types and decision functions for classifying CI check results into
actionable verdicts, with no I/O — only data and logic.

Key objectives:

- Define the `CICheckStatus`, `CICheckResult`, `CIVerdict`, and `PhaseMergeConfig` domain types
- Implement `PhaseMerge.evaluateChecks` as the central decision function mapping check
  results to a verdict
- Implement `PhaseMerge.shouldRetry` for bounded recovery attempts
- Implement `PhaseMerge.buildRecoveryPrompt` to generate agent-facing failure descriptions
- Provide comprehensive unit test coverage for all decision paths

All later phases (CI polling, recovery loop, merge, GitLab support) depend on these
types and functions.

## Scenarios

- [ ] All CI checks passed → verdict is `AllPassed`
- [ ] At least one CI check failed → verdict is `SomeFailed` carrying the failing checks
- [ ] At least one CI check is pending (no failures) → verdict is `StillRunning`
- [ ] No CI checks configured → verdict is `NoChecksFound`
- [ ] Cancelled checks are treated as failures → `SomeFailed`
- [ ] Mixed Failed and Pending → failure takes precedence → `SomeFailed`
- [ ] Unknown status checks behave as non-blocking → `AllPassed` when no failures/pending
- [ ] Retry allowed when attempt index is below `maxRetries`
- [ ] Retry denied when attempt index equals or exceeds `maxRetries`
- [ ] `maxRetries = 0` → never retry
- [ ] Recovery prompt includes each failed check's name and status
- [ ] Recovery prompt includes URLs when present, omits them when absent
- [ ] `PhaseMergeConfig` defaults: 30-minute timeout, 30-second poll interval, 2 max retries

## Entry Points

| File | Symbol | Why Start Here |
|------|--------|----------------|
| `.iw/core/model/PhaseMerge.scala` | `PhaseMerge.evaluateChecks` | Central decision function; all later phases call this to determine what to do next |
| `.iw/core/model/PhaseMerge.scala` | `CIVerdict` | Enum of possible outcomes; reviewing its variants shows the full state space the command must handle |
| `.iw/core/model/PhaseMerge.scala` | `PhaseMergeConfig` | Configuration defaults define the operational contract (timeout, polling cadence, retry budget) |
| `.iw/core/test/PhaseMergeTest.scala` | `PhaseMergeTest` | Start here to verify scenario coverage; the test names directly mirror the acceptance criteria |

## Diagrams

### Domain Model

```
CICheckStatus (enum)
  Passed | Failed | Pending | Cancelled | Unknown

CICheckResult (case class)
  name: String
  status: CICheckStatus
  url: Option[String]

CIVerdict (enum)
  AllPassed
  SomeFailed(failedChecks: List[CICheckResult])
  StillRunning
  NoChecksFound
  TimedOut          ← set by caller (e.g., polling loop), never by evaluateChecks

PhaseMergeConfig (case class)
  timeoutMs: Long       = 1_800_000  (30 minutes)
  pollIntervalMs: Long  =    30_000  (30 seconds)
  maxRetries: Int       =         2
```

### Decision Flow — `evaluateChecks`

```
List[CICheckResult]
        │
        ▼
   isEmpty? ──── yes ──→ NoChecksFound
        │
       no
        │
        ▼
  any Failed or
  Cancelled? ────yes──→ SomeFailed(failing checks)
        │
       no
        │
        ▼
  any Pending? ──yes──→ StillRunning
        │
       no
        │
        ▼
    AllPassed
    (Passed + Unknown checks reach here)
```

### Verdict-to-Action Map (for future phases)

```
AllPassed      → squash-merge the PR
SomeFailed     → invoke agent recovery (up to maxRetries times)
StillRunning   → sleep pollIntervalMs, poll again
NoChecksFound  → squash-merge immediately (no CI configured)
TimedOut       → set activity="waiting", exit non-zero
```

## Test Summary

All tests are pure unit tests. No mocking, no I/O, no fixtures needed.

| Test | Type | Covers |
|------|------|--------|
| `CICheckStatus has all five values` | Unit | Enum completeness |
| `CICheckResult can be constructed with name and status` | Unit | Default URL is None |
| `CICheckResult can be constructed with name, status, and URL` | Unit | Optional URL field |
| `PhaseMergeConfig default timeout is 30 minutes` | Unit | Config defaults |
| `PhaseMergeConfig default poll interval is 30 seconds` | Unit | Config defaults |
| `PhaseMergeConfig default max retries is 2` | Unit | Config defaults |
| `evaluateChecks empty list returns NoChecksFound` | Unit | Edge case: no checks |
| `evaluateChecks all Passed returns AllPassed` | Unit | Happy path |
| `evaluateChecks single Passed returns AllPassed` | Unit | Single-check edge case |
| `evaluateChecks some Failed with others Passed returns SomeFailed` | Unit | Partial failure; carries only failing checks |
| `evaluateChecks all Cancelled returns SomeFailed` | Unit | Cancelled treated as failure |
| `evaluateChecks mix of Failed and Cancelled returns SomeFailed with both` | Unit | Both failure kinds collected |
| `evaluateChecks some Pending with none Failed returns StillRunning` | Unit | Pending precedence |
| `evaluateChecks single Pending returns StillRunning` | Unit | Single-check edge case |
| `evaluateChecks mix of Pending and Failed returns SomeFailed` | Unit | Failure beats pending |
| `evaluateChecks all Unknown returns AllPassed` | Unit | Unknown is non-blocking |
| `evaluateChecks Unknown mixed with Passed returns AllPassed` | Unit | Unknown + Passed |
| `evaluateChecks Unknown mixed with Failed returns SomeFailed` | Unit | Unknown + failure |
| `shouldRetry attempt 0 with max 2 returns true` | Unit | Within limit |
| `shouldRetry attempt 1 with max 2 returns true` | Unit | Within limit |
| `shouldRetry attempt 2 with max 2 returns false` | Unit | Boundary: at limit |
| `shouldRetry attempt 5 with max 2 returns false` | Unit | Beyond limit |
| `shouldRetry max retries 0 always returns false` | Unit | Zero-retry config |
| `buildRecoveryPrompt single failed check includes name and status` | Unit | Minimal prompt |
| `buildRecoveryPrompt multiple failed checks includes all names` | Unit | Multiple checks listed |
| `buildRecoveryPrompt checks with URLs includes the URLs` | Unit | URL surfaced to agent |
| `buildRecoveryPrompt checks without URLs produces a valid prompt` | Unit | Graceful omission |

**Total: 27 unit tests. Integration and E2E tests are not applicable for this phase (pure functions, no I/O).**

## Files Changed

| File | Status | Description |
|------|--------|-------------|
| `.iw/core/model/PhaseMerge.scala` | New | Domain types (`CICheckStatus`, `CICheckResult`, `CIVerdict`, `PhaseMergeConfig`) and the `PhaseMerge` object with three pure decision functions |
| `.iw/core/test/PhaseMergeTest.scala` | New | 27 unit tests covering all verdict outcomes, retry boundary conditions, and recovery prompt generation |

This phase is purely additive. No existing files were modified.
