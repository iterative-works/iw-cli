# Phase 2: phase-merge does not commit review-state.json updates

## Defect Description

`iw phase-merge` (`.iw/commands/phase-merge.scala`) updates `review-state.json` via `tryUpdateState()` at multiple points but never stages or commits the changes. The critical update is the final "phase_merged" status update (lines 199-205) which happens after `fetchAndReset` on the feature branch — this leaves review-state.json dirty on the feature branch.

### Symptoms

- After running `iw phase-merge`, `git status` shows `review-state.json` as modified but unstaged on the feature branch.
- Any downstream workflow checking `GitAdapter.hasUncommittedChanges` fails because it finds the dirty working tree.
- Same root cause pattern as Phase 1 (phase-pr), now confirmed and fixed there.

## Reproduction Steps

1. Have a feature branch with a phase sub-branch that has an open PR (e.g., `ISSUE-100-phase-01`).
2. Ensure `project-management/issues/ISSUE-100/review-state.json` exists and is committed.
3. Ensure CI passes on the PR (or no CI checks exist).
4. Check out the phase sub-branch: `git checkout ISSUE-100-phase-01`
5. Run `iw phase-merge`
6. After merge completes, check `git status` on the feature branch.
7. **Expected:** Working tree is clean; review-state.json "phase_merged" update is committed.
8. **Actual:** `review-state.json` is modified but unstaged/uncommitted on the feature branch.

## Root Cause (Confirmed by Code Inspection)

`phase-merge.scala` updates review-state at three points without any git operations:
- Line 92-96: Sets `ci_pending` status (on phase sub-branch during CI polling — transient)
- Lines 150-163: Sets `ci_fixing` and back to `ci_pending` during recovery (transient)
- Lines 199-205: Sets `phase_merged` status after `fetchAndReset` on feature branch — **this is the critical one**

The intermediate updates (ci_pending, ci_fixing) are transient — they happen on the phase sub-branch which gets squash-merged away. Only the final "phase_merged" update on the feature branch needs committing.

## Fix Strategy

After the `tryUpdateState` call at line 199-205 (the "phase_merged" update), add:
1. Stage `review-state.json` using `GitAdapter.stageFiles` (available from Phase 1).
2. Commit with message like `chore(<issueId>): update review-state for phase <N>`.

The `stageFiles` method and commit pattern are identical to what was done in Phase 1 for phase-pr.

**Important:** Only the final "phase_merged" update needs committing. The intermediate ci_pending/ci_fixing updates are on the phase sub-branch and get squash-merged away — committing them would create noise.

## What Was Fixed in Phase 1

- `GitAdapter.stageFiles(paths: Seq[os.Path], dir: os.Path)` method was added for targeted staging.
- `phase-pr.scala` now commits review-state.json after update in both batch and non-batch paths.
- The commit pattern (stage + commit, with failure treated as warning) is established and can be reused.

## Affected Files

- `.iw/commands/phase-merge.scala` — Add git stage + commit after final review-state update (line 205)

## Testing Requirements

1. **E2E regression test**: A BATS test that sets up a scenario where phase-merge runs (with mocked gh/glab for merge), then asserts `git status --porcelain` is empty and the review-state.json changes are in a commit.
2. **Existing test suite**: Run `./iw test` to ensure no regressions.

## Acceptance Criteria

- [ ] After `iw phase-merge`, `git status` is clean on the feature branch.
- [ ] The "phase_merged" review-state.json update is committed on the feature branch.
- [ ] Intermediate ci_pending/ci_fixing updates are NOT committed (they are transient on the phase sub-branch).
- [ ] All existing tests pass (`./iw test`).
