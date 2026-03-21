---
generated_from: 30d0b87e9102b019a9722e8a4044922335748c0a
generated_at: 2026-03-21T13:14:36Z
branch: IW-289-phase-07
issue_id: IW-289
phase: 7
files_analyzed:
  - .iw/commands/batch-implement.scala
  - .iw/test/batch-implement.bats
  - .iw/test/phase-merge.bats
---

# Review Packet: Phase 7 - batch-implement integration

## Goals

This phase wires the completed `phase-merge` command (Phases 1-6) into the
`batch-implement` orchestration loop, so that every phase in a batch run waits
for CI and auto-merges rather than bypassing CI entirely.

Key objectives:

- Remove inline PR merge logic (`handleMergePR`) from `batch-implement.scala`
  and replace it with a subprocess call to `./iw phase-merge`
- Remove the local `readPrUrl` closure that was only used by `handleMergePR`
- Ensure batch-implement stops cleanly when `phase-merge` exits non-zero
- Fix two pre-existing BATS test failures in `phase-merge.bats` (tests 7-8)
  caused by a single-quoted heredoc preventing `$TEST_DIR` expansion

After this phase the batch loop is: `agent (claude) -> phase-pr creates PR ->
phase-merge polls CI + merges -> mark phase done -> next phase`.

## Scenarios

- [x] `batch-implement` invokes `iw phase-merge` subprocess when review-state status is `awaiting_review`
- [x] `batch-implement` does NOT contain inline `gh pr merge` / `glab mr merge` calls
- [x] `handleMergePR` function is removed from `batch-implement.scala`
- [x] Local `readPrUrl` closure is removed from `batch-implement.scala`
- [x] When `phase-merge` exits 0, batch-implement marks the phase done and continues
- [x] When `phase-merge` exits non-zero, batch-implement stops with a clear error message
- [x] Pre-existing BATS failures in `phase-merge.bats` tests 7-8 are fixed
- [x] New BATS E2E test: batch-implement calls `phase-merge` (verifies call log)
- [x] New BATS E2E test: batch-implement stops on `phase-merge` failure (no phase check-off)
- [x] Existing batch-implement BATS tests still pass (no regressions)
- [x] Core compiles with `-Werror`

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/commands/batch-implement.scala` | `invokePhaseMerge()` | New function; replaces removed `handleMergePR`; this is the heart of the change |
| `.iw/commands/batch-implement.scala` | `handleOutcome()` | Calls `invokePhaseMerge` for the `MergePR` case — verify the dispatch is wired correctly |
| `.iw/test/batch-implement.bats` | tests 10-11 | New E2E tests for the phase-merge integration path |
| `.iw/test/phase-merge.bats` | tests 7-8 (lines 215, 271) | Fixed heredoc quoting — verify `$TEST_DIR` now expands in mock `gh` scripts |

## Diagrams

### Batch-implement phase loop (after Phase 7)

```
runPhases()
  │
  ├─ invoke claude (--phase N)
  │    └─ claude runs: phase-start -> implement -> phase-pr
  │         └─ review-state: "awaiting_review"
  │
  ├─ commitLeftovers()
  ├─ readStatus()  →  "awaiting_review"
  │
  └─ handleOutcome()
       └─ PhaseOutcome.MergePR
            └─ invokePhaseMerge()
                 ├─ runs: ./iw phase-merge  (4-hour outer timeout)
                 │    ├─ polls CI (30m internal timeout)
                 │    ├─ on CI failure: invokes claude recovery agent
                 │    └─ on CI pass: merges PR, advances feature branch,
                 │         updates review-state → "phase_merged"
                 ├─ exit != 0  →  log error + sys.exit(1)
                 └─ exit 0    →  markAndCommitPhase()
                                   └─ checks off phase in tasks.md, commits
```

### What was removed

```
batch-implement.scala (before Phase 7)        batch-implement.scala (after Phase 7)
──────────────────────────────────────        ──────────────────────────────────────
readPrUrl()  ──────────────────────────────── REMOVED
handleMergePR()                               REMOVED
  reads PR URL from review-state
  runs gh pr merge / glab mr merge
  checks out + advances feature branch
  updates review-state → phase_merged
  calls markAndCommitPhase()
```

### BATS test mock architecture (new tests)

```
Test setup
  TEST_DIR/iw  (wrapper script)
    ├─ if $1 == "phase-merge"
    │    ├─ records call to phase-merge-calls.log
    │    ├─ writes review-state → "phase_merged"
    │    └─ exits 0 (success test) OR exits 1 (failure test)
    └─ else delegates to $PROJECT_ROOT/iw

  STUB_DIR/claude  (per-test stub)
    └─ writes review-state → "awaiting_review" with pr_url
```

## Test Summary

### Unit tests

No new unit tests. `BatchImplement.decideOutcome` is unchanged — `"awaiting_review"`
still maps to `PhaseOutcome.MergePR`; existing unit tests cover this.

### E2E tests (BATS)

| Test file | Test name | Type | Status |
|-----------|-----------|------|--------|
| `batch-implement.bats` | "batch-implement invokes iw phase-merge when review-state status is awaiting_review after agent runs" | E2E | New |
| `batch-implement.bats` | "batch-implement stops immediately when phase-merge exits non-zero" | E2E | New |
| `phase-merge.bats` | "phase-merge happy path merges PR and updates review-state to phase_merged" (test 7) | E2E | Fixed |
| `phase-merge.bats` | "phase-merge with failing CI checks exits non-zero and reports failed checks" (test 8) | E2E | Fixed |

### Regression coverage

All 9 existing `batch-implement.bats` tests and all 16 `phase-merge.bats` tests
were confirmed passing after the changes.

## Files Changed

| File | Change type | Summary |
|------|-------------|---------|
| `.iw/commands/batch-implement.scala` | Modified | Removed `handleMergePR` (lines 201-248) and local `readPrUrl` closure (lines 149-154); added `invokePhaseMerge`; updated `handleOutcome` MergePR case |
| `.iw/test/batch-implement.bats` | Modified | Added two new E2E tests for phase-merge integration |
| `.iw/test/phase-merge.bats` | Modified | Fixed heredoc quoting in tests 7-8: `<< 'GHEOF'` -> `<< GHEOF`, escaped `\$1 \$2 \$*` |
| `project-management/issues/IW-289/phase-07-tasks.md` | Modified | All tasks marked complete |
| `project-management/issues/IW-289/review-state.json` | Modified | Phase 7 state tracking |

<details>
<summary>batch-implement.scala — key structural change</summary>

The `invokePhaseMerge` function (lines 192-205) is the direct replacement for
`handleMergePR`. The outer timeout is intentionally large (4 hours) because
`phase-merge` itself has a 30-minute CI polling timeout and may additionally
invoke a claude recovery agent. The `markAndCommitPhase` call is still
performed by `batch-implement` after `phase-merge` returns — `phase-merge`
updates review-state to `phase_merged`, while tasks.md check-off remains a
`batch-implement` responsibility.

</details>

<details>
<summary>phase-merge.bats — heredoc fix rationale</summary>

When a heredoc uses a single-quoted delimiter (`<< 'GHEOF'`), the shell does
not expand any variables inside the body. The mock `gh` script body contained
`$TEST_DIR/gh-calls.log`, which was written literally instead of being
expanded to the actual temp directory path, so the log was never written where
the test expected it. Removing the quotes and escaping `\$1`, `\$2`, `\$*`
(which should remain as runtime shell variables inside the mock script) fixes
the expansion order.

</details>
