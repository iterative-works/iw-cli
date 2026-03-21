# Phase 1: phase-pr does not commit review-state.json update

## Defect Description

`iw phase-pr` (`.iw/commands/phase-pr.scala`) updates `review-state.json` via `ReviewStateAdapter.update()` in both batch and non-batch paths but never stages or commits the change. This leaves a dirty working tree after every phase PR creation.

### Symptoms

- After running `iw phase-pr`, `git status` shows `review-state.json` as modified but unstaged.
- `batch-implement` (and any workflow that checks `GitAdapter.hasUncommittedChanges`) fails on the next iteration because it finds a dirty working tree.
- Observed consistently on two consecutive phases of DEVDOCS-110.

## Reproduction Steps

1. Have a feature branch with at least one phase sub-branch (e.g., `ISSUE-100-phase-01`).
2. Ensure `project-management/issues/ISSUE-100/review-state.json` exists and is committed.
3. Ensure working tree is clean.
4. Check out the phase sub-branch: `git checkout ISSUE-100-phase-01`
5. Run `iw phase-pr --title "Phase 1: Implementation"`
6. After the command completes, run `git status`.
7. **Expected:** Working tree is clean; review-state.json changes are committed.
8. **Actual:** `review-state.json` is modified but unstaged/uncommitted.

## Root Cause (Confirmed by Code Inspection)

`phase-pr.scala` calls `ReviewStateAdapter.update()` at lines 97-106 (batch path) and lines 110-123 (non-batch path). `ReviewStateAdapter.update()` only reads, merges, validates, and writes the JSON file to disk — it does not interact with git. No git staging or committing follows the update in either path.

For comparison, `phase-commit.scala` explicitly calls `GitAdapter.stageAll()` then `GitAdapter.commit()` after its changes — this pattern is completely absent from `phase-pr.scala`.

## Fix Strategy

### Shared prerequisite: Add `stageFiles` to GitAdapter

`GitAdapter` currently has `stageAll` but no targeted file staging method. Add `stageFiles(paths: Seq[os.Path], dir: os.Path): Either[String, Unit]` to avoid accidentally staging unrelated files. This method will be reused by Phases 2 and 3.

### Batch path (lines 93-106)

After `ReviewStateAdapter.update()` succeeds on line 97-106:
1. Stage `review-state.json` using `GitAdapter.stageFiles`.
2. Commit with message like `chore(ISSUE-ID): update review-state for phase N`.

At this point we are on the feature branch (after `checkoutBranch` + `fetchAndReset`), so the commit goes on the feature branch. This is correct — the "phase_merged" state belongs on the feature branch.

### Non-batch path (lines 110-123)

After `ReviewStateAdapter.update()` succeeds on lines 110-123:
1. Stage `review-state.json` using `GitAdapter.stageFiles`.
2. Commit with message like `chore(ISSUE-ID): update review-state for phase N`.
3. Push the commit (we are still on the phase sub-branch, and the push already happened at line 54 *before* the review-state update, so a follow-up push is needed).

## Affected Files

- `.iw/core/adapters/Git.scala` — Add `stageFiles` method
- `.iw/commands/phase-pr.scala` — Add git stage + commit after review-state update in both paths

## Testing Requirements

1. **Unit test for `GitAdapter.stageFiles`**: Verify it stages specific files (visible in `git diff --cached`).
2. **E2E regression test**: A BATS test that runs `iw phase-pr`, then asserts `git status --porcelain` is empty and the review-state changes appear in a commit.

## Acceptance Criteria

- [ ] `GitAdapter.stageFiles` method exists and is tested.
- [ ] After `iw phase-pr` (batch mode), `git status` is clean and review-state.json is committed on the feature branch.
- [ ] After `iw phase-pr` (non-batch mode), `git status` is clean and review-state.json is committed and pushed on the phase sub-branch.
- [ ] All existing tests pass (`./iw test`).
