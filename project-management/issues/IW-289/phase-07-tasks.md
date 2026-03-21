# Phase 7 Tasks: batch-implement integration

Replace inline merge logic in `batch-implement.scala` with a subprocess call to `iw phase-merge`,
and fix two pre-existing BATS test failures in `phase-merge.bats`.

## Setup

- [x] Confirm all existing batch-implement and phase-merge BATS tests pass before touching code (`./iw test e2e`)
- [x] Identify the exact lines to change in `batch-implement.scala`: `readPrUrl` closure (lines 149-154), `handleMergePR` function (lines 201-248), and the `MergePR` case in `handleOutcome` (line 276)

## Fix pre-existing BATS failures

- [x] Fix test 7 in `.iw/test/phase-merge.bats` ("phase-merge happy path merges PR and updates review-state to phase_merged", line 235): change `<< 'GHEOF'` to `<< GHEOF` (unquoted) so `$TEST_DIR` expands inside the heredoc; escape `$1` and `$2` as `\$1` and `\$2` to prevent premature expansion
- [x] Fix test 8 in `.iw/test/phase-merge.bats` ("phase-merge with failing CI checks exits non-zero and reports failed checks", line 286): same fix — change `<< 'GHEOF'` to `<< GHEOF` and escape `\$1`, `\$2`
- [x] Run `.iw/test/phase-merge.bats` and confirm tests 7 and 8 now pass

## TDD: write failing E2E tests for batch-implement + phase-merge integration

- [x] Add test to `.iw/test/batch-implement.bats`: "batch-implement invokes iw phase-merge when review-state status is awaiting_review after agent runs"
  - Setup: stub `claude` that sets review-state status to `awaiting_review` with a `pr_url`; create a wrapper `iw` script in mock-bin that, when called with `phase-merge`, records the call in a log file and updates review-state to `phase_merged`, otherwise delegates to the real `iw`
  - Assert: exit code 0; `phase-merge` call log exists; tasks.md has phase 1 checked off; no `gh pr merge` or `glab mr merge` in the gh-calls log
- [x] Add test to `.iw/test/batch-implement.bats`: "batch-implement stops immediately when phase-merge exits non-zero"
  - Setup: same stub `claude` sets `awaiting_review`; wrapper `iw phase-merge` exits with code 1
  - Assert: exit code non-zero; tasks.md does NOT have phase 1 checked off; no further phase processing attempted
- [x] Run new tests and confirm they fail (expected — implementation not changed yet)

## Implementation

- [x] In `.iw/commands/batch-implement.scala`, add `invokePhaseMerge(phaseNum: Int): Unit` function after `markAndCommitPhase`:
  - Logs `"[phase N] Invoking phase-merge to wait for CI and merge..."`
  - Builds command: `Seq((cwd / "iw").toString, "phase-merge")`
  - Calls `ProcessAdapter.runStreaming(phaseMergeCmd, 4 * 60 * 60 * 1000)` (4-hour outer timeout)
  - On non-zero exit: logs error and stop message, closes `logWriter`, calls `sys.exit(1)`
  - On success: logs `"[phase N] phase-merge completed successfully"`, calls `markAndCommitPhase(phaseNum)`
- [x] In `handleOutcome`, replace `handleMergePR(phaseNum)` with `invokePhaseMerge(phaseNum)` in the `MergePR` case (line 276)
- [x] Remove the `handleMergePR` function entirely (lines 201-248)
- [x] Remove the local `readPrUrl` closure (lines 149-154) — it was only used by `handleMergePR`; do NOT touch `ReviewStateAdapter.readPrUrl` in the adapter layer
- [x] Verify no `gh pr merge` or `glab mr merge` calls remain in `batch-implement.scala`

## Integration

- [x] Run `scala-cli compile --scalac-option -Werror .iw/core/` to confirm core still compiles clean
- [x] Run the two new BATS tests and confirm they now pass
- [x] Run the full existing `batch-implement.bats` suite to confirm no regressions
- [x] Run the full `phase-merge.bats` suite to confirm all 16 tests pass (including the two fixed ones)
- [x] Commit: `feat(IW-289): replace handleMergePR with phase-merge subprocess in batch-implement`
**Phase Status:** Complete
