---
generated_from: 94b63394c0d5441f50fe3f157faacde90df4035b
generated_at: 2026-03-20T21:56:15Z
branch: IW-289-phase-03
issue_id: IW-289
phase: 3
files_analyzed:
  - .iw/commands/phase-merge.scala
  - .iw/core/model/PhaseMerge.scala
  - .iw/core/adapters/GitHubClient.scala
  - .iw/core/model/PhaseOutput.scala
  - .iw/core/test/PhaseMergeTest.scala
  - .iw/core/test/GitHubClientTest.scala
  - .iw/test/phase-merge.bats
---

# Review Packet: Phase 3 - GitHub CI Polling and Auto-Merge

## Goals

This phase delivers the `iw phase-merge` command, which bridges the gap between PR creation and
merge by polling GitHub CI check statuses and auto-merging on success. It connects the pure
decision logic from Phase 1 (`evaluateChecks`) and the PR number extraction from Phase 2
(`extractPrNumber`) with real I/O, completing the core happy-path automation loop.

Key objectives:

- Add `PhaseMerge.parseGhChecksJson` — pure function mapping `gh pr checks` JSON to domain types
- Add `GitHubClient.fetchCheckStatuses` — adapter that calls `gh pr checks` and returns parsed results
- Implement `phase-merge.scala` — command that polls CI, merges on pass, advances the feature branch
- Add `PhaseOutput.MergeOutput` — JSON output type for the merge result
- Cover all paths with unit, integration (injected command), and E2E (BATS) tests

## Scenarios

- [ ] `phase-merge` only runs on a phase sub-branch; exits with error if invoked on any other branch
- [ ] `phase-merge` requires a config file; exits with helpful error if missing
- [ ] `phase-merge` detects GitLab forge and exits with "not yet supported" message
- [ ] `phase-merge` exits with error when `review-state.json` is absent
- [ ] `phase-merge` exits with error when `review-state.json` has no `pr_url` field
- [ ] Happy path: all CI checks pass on first poll, PR is merged with `--merge --delete-branch`, feature branch is advanced, `review-state` transitions to `phase_merged`, JSON output is printed
- [ ] CI failure path: `SomeFailed` verdict causes non-zero exit and lists the failed check names
- [ ] `parseGhChecksJson` maps SUCCESS/FAILURE/PENDING/CANCELLED states correctly; unknown states map to `Unknown`
- [ ] `parseGhChecksJson` maps empty link to `url = None`; non-empty link to `url = Some(link)`
- [ ] `parseGhChecksJson` returns `Left` on invalid JSON or a non-array JSON value
- [ ] `GitHubClient.fetchCheckStatuses` propagates `gh` errors as `Left`; returns `Right(Nil)` for empty array

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/commands/phase-merge.scala` | `phaseMerge()` | Top-level command entry point; orchestrates the entire CI poll and merge flow |
| `.iw/core/model/PhaseMerge.scala` | `parseGhChecksJson()` | New pure function added this phase; start here to understand JSON-to-domain mapping |
| `.iw/core/adapters/GitHubClient.scala` | `fetchCheckStatuses()` | New adapter function; calls `gh pr checks`, delegates parsing to `parseGhChecksJson` |
| `.iw/core/model/PhaseOutput.scala` | `MergeOutput` | New output type; shows what the command returns on success |

## Diagrams

### Component Architecture

```
phase-merge.scala (command)
│
├── reads/writes review-state.json  via ReviewStateAdapter
├── reads branch context            via GitAdapter.getCurrentBranch
├── extracts PR number              via PhaseMerge.extractPrNumber     (Phase 2)
│
├── [polling loop]
│   └── GitHubClient.fetchCheckStatuses(prNumber, repository)
│           └── execCommand("gh", ["pr","checks",...])
│           └── PhaseMerge.parseGhChecksJson(json)           ← NEW
│               └── returns List[CICheckResult]
│   └── PhaseMerge.evaluateChecks(checks)                    (Phase 1)
│       ├── AllPassed / NoChecksFound → break, merge
│       ├── StillRunning              → sleep, loop
│       └── SomeFailed                → exit 1
│
├── ProcessAdapter.run(["gh","pr","merge","--merge","--delete-branch", prUrl])
├── GitAdapter.checkoutBranch(featureBranch)
└── GitAdapter.fetchAndReset(featureBranch)
```

### Review-State Transitions

```
awaiting_review
      │
      │  (phase-merge starts)
      ▼
  ci_pending
      │
      │  (all checks pass, merge succeeds)
      ▼
 phase_merged
```

### Data Flow for `parseGhChecksJson`

```
gh pr checks --json name,state,link,bucket
      │
      │  JSON array
      ▼
[{"name":"test","state":"SUCCESS","link":"https://...","bucket":"pass"}, ...]
      │
      │  PhaseMerge.parseGhChecksJson
      ▼
List[CICheckResult(name, CICheckStatus, Option[String])]

State mapping:
  "SUCCESS"   → Passed
  "FAILURE"   → Failed
  "PENDING"   → Pending
  "CANCELLED" → Cancelled
  anything    → Unknown

Link mapping:
  ""          → None
  non-empty   → Some(link)
```

## Test Summary

### Unit Tests (`PhaseMergeTest.scala`)

New tests added this phase (in addition to Phase 1 and 2 tests already present):

| Test | Type | Covers |
|------|------|--------|
| `parseGhChecksJson all-passing checks returns Right` | Unit | Happy path parsing |
| `parseGhChecksJson maps SUCCESS, FAILURE, PENDING to correct statuses` | Unit | State mapping |
| `parseGhChecksJson maps CANCELLED state to CICheckStatus.Cancelled` | Unit | State mapping |
| `parseGhChecksJson maps unknown state string to CICheckStatus.Unknown` | Unit | Unknown state |
| `parseGhChecksJson empty array returns Right(Nil)` | Unit | Edge case: no checks |
| `parseGhChecksJson invalid JSON returns Left with error` | Unit | Error handling |
| `parseGhChecksJson JSON object (not array) returns Left with error` | Unit | Error handling |
| `parseGhChecksJson array entry missing name field returns Left with error` | Unit | Error handling |
| `parseGhChecksJson check with empty link returns None for url` | Unit | Link → url mapping |
| `parseGhChecksJson check with non-empty link returns Some(link) for url` | Unit | Link → url mapping |

### Integration Tests (`GitHubClientTest.scala`)

New tests added this phase:

| Test | Type | Covers |
|------|------|--------|
| `buildCheckStatusesCommand builds correct gh pr checks command` | Integration | Command shape |
| `buildMergePrWithDeleteCommand builds command with --merge and --delete-branch` | Integration | Command shape |
| `fetchCheckStatuses returns parsed checks on successful gh execution` | Integration | Happy path with mock exec |
| `fetchCheckStatuses returns Left when gh command returns error` | Integration | Error propagation |
| `fetchCheckStatuses returns Right(Nil) when gh returns empty array` | Integration | Empty result |

### E2E Tests (`.iw/test/phase-merge.bats`)

| Test | Scenario |
|------|----------|
| `phase-merge when not on a phase branch exits with error` | Branch validation |
| `phase-merge without config file exits with error about missing config` | Config validation |
| `phase-merge with GitLab forge type exits with not-supported error` | GitLab guard |
| `phase-merge with missing review-state.json exits with error` | review-state validation |
| `phase-merge with review-state.json missing pr_url exits with error` | pr_url validation |
| `phase-merge happy path merges PR and updates review-state to phase_merged` | Full happy path with mock `gh` |
| `phase-merge with failing CI checks exits non-zero and reports failed checks` | CI failure path |

All E2E tests export `IW_SERVER_DISABLED=1` in `setup()`, consistent with project conventions.

## Files Changed

| File | Change | Description |
|------|--------|-------------|
| `.iw/commands/phase-merge.scala` | NEW | Command script: branch validation, CI polling loop, merge, branch advance, review-state updates, JSON output |
| `.iw/core/model/PhaseMerge.scala` | MODIFIED | Added `parseGhChecksJson` pure function |
| `.iw/core/adapters/GitHubClient.scala` | MODIFIED | Added `buildCheckStatusesCommand`, `buildMergePrWithDeleteCommand`, `fetchCheckStatuses` |
| `.iw/core/model/PhaseOutput.scala` | MODIFIED | Added `MergeOutput` case class |
| `.iw/core/test/PhaseMergeTest.scala` | MODIFIED | Added 10 new tests for `parseGhChecksJson` |
| `.iw/core/test/GitHubClientTest.scala` | MODIFIED | Added 5 new tests for check status fetching |
| `.iw/test/phase-merge.bats` | NEW | 7 E2E tests covering all command-level scenarios |

<details>
<summary>Notable implementation details</summary>

**Merge strategy:** Uses `--merge --delete-branch` (merge commit, not squash). This is intentional
— squash on phase PRs would force resets on the feature branch. See analysis.md "Merge strategy"
decision.

**GitLab guard:** The command detects `ForgeType.GitLab` early and exits with a clear message.
GitLab support is deferred to Phase 6.

**Timeout handling:** Uses hardcoded `PhaseMergeConfig` defaults (30 min timeout, 30 s poll
interval). Configurable polling via CLI flags is deferred to Phase 4.

**`StillRunning` display:** When still polling, the command prints which checks are pending by
name, making it easier to track progress in long CI runs.

**`fetchCheckStatuses` goes through `validateGhPrerequisites`** like all other `GitHubClient`
functions, so authentication errors are handled consistently before any `gh` call is made.

</details>
