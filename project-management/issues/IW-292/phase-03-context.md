# Phase 3: phase-advance does not commit review-state.json update

## Defect Description

`iw phase-advance` (`.iw/commands/phase-advance.scala`) updates `review-state.json` via `ReviewStateAdapter.update()` at lines 93-101 after `fetchAndReset` on the feature branch, but never stages or commits the change. This leaves a dirty working tree on the feature branch.

### Symptoms

- After running `iw phase-advance`, `git status` shows `review-state.json` as modified but unstaged on the feature branch.
- Any downstream workflow checking `GitAdapter.hasUncommittedChanges` fails because it finds the dirty working tree.
- Same root cause pattern as Phase 1 (phase-pr) and Phase 2 (phase-merge), both now fixed.

## Reproduction Steps

1. Have a feature branch with a phase sub-branch that has a merged PR (e.g., `ISSUE-100-phase-01`).
2. Ensure `project-management/issues/ISSUE-100/review-state.json` exists and is committed.
3. Check out the phase sub-branch: `git checkout ISSUE-100-phase-01`
4. Run `iw phase-advance`
5. After advance completes, check `git status` on the feature branch.
6. **Expected:** Working tree is clean; review-state.json "phase_merged" update is committed.
7. **Actual:** `review-state.json` is modified but unstaged/uncommitted on the feature branch.

## Root Cause (Confirmed by Code Inspection)

`phase-advance.scala` lines 92-101 update review-state after `fetchAndReset` (line 87) without any git staging or commit. The `ReviewStateAdapter.update()` writes the file to disk but does not interact with git — identical pattern to Phase 1 and Phase 2.

## Fix Strategy

After the `ReviewStateAdapter.update()` call at lines 93-101 (the "phase_merged" update), add:
1. Stage `review-state.json` using `GitAdapter.stageFiles` (available from Phase 1).
2. Commit with message like `chore(<issueId>): update review-state for phase <N>`.

The commit pattern (stage + commit, failures treated as warnings) is established in Phase 1 and Phase 2 and can be reused identically.

## What Was Fixed in Previous Phases

- **Phase 1:** Added `GitAdapter.stageFiles(paths: Seq[os.Path], dir: os.Path)` method. Fixed `phase-pr.scala` in both batch and non-batch paths.
- **Phase 2:** Fixed `phase-merge.scala` final "phase_merged" update to commit review-state.json.

## Affected Files

- `.iw/commands/phase-advance.scala` — Add git stage + commit after review-state update (line 101)

## Testing Requirements

1. **E2E regression test**: A BATS test that sets up a scenario where phase-advance runs (with mocked gh for merged PR check), then asserts `git status --porcelain` is empty and the review-state.json changes are in a commit.
2. **Existing test suite**: Run `./iw test` to ensure no regressions.

## Acceptance Criteria

- [x] After `iw phase-advance`, `git status` is clean on the feature branch.
- [x] The "phase_merged" review-state.json update is committed on the feature branch.
- [x] All existing tests pass (`./iw test`).

## Investigation Notes (Phase 3)

Root cause confirmed by code inspection: `phase-advance.scala` lines 99-101 had the `Right(_) => ()` case which discarded the successful update result without staging or committing. The fix adds a `stageFiles` + `commit` call inside the `Right(_)` case, matching exactly the pattern from phase-merge.scala. The failing test reproduced the defect (dirty working tree assertion failed), and the test passed after the fix.
