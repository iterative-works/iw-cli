# Diagnostic Analysis: phase-pr leaves review-state.json uncommitted

**Issue:** IW-292
**Created:** 2026-03-21
**Status:** Draft
**Severity:** High

## Problem Statement

`iw phase-pr` updates `review-state.json` (setting status, PR URL, badges, actions) but never stages or commits the change. This leaves a dirty working tree after every phase PR creation.

The downstream impact is that `batch-implement` (and any workflow runner) performs a clean-working-tree check (`GitAdapter.hasUncommittedChanges`) at startup and exits with an error when it finds the uncommitted `review-state.json`. This blocks continuation to the next phase without manual intervention (`git add` + `git commit` or `git stash`).

Observed consistently on two consecutive phases of DEVDOCS-110.

## Affected Components

- `.iw/commands/phase-pr.scala` (lines 97-123): Updates review-state.json in both batch and non-batch paths without any subsequent git add/commit.
- `.iw/commands/phase-merge.scala` (lines 92-96, 150-163, 199-205): Updates review-state.json at multiple points without committing. After the final update (line 199), the command has checked out the feature branch via `fetchAndReset`, so the file is modified on the feature branch.
- `.iw/commands/phase-advance.scala` (lines 92-101): Updates review-state.json after `fetchAndReset` without committing.
- `.iw/commands/batch-implement.scala` (line 81-84): The guard that fails when the working tree is dirty.

**Blast radius:** Every phase PR creation leaves a dirty tree. Any automated workflow that checks working tree cleanliness before proceeding is blocked. This affects all users running phased implementation workflows.

---

## Defect 1: phase-pr does not commit review-state.json update

### Reproduction Steps

**Environment:** Any OS with iw-cli, git, and gh/glab CLI installed. Requires a repository with GitHub/GitLab remote and a valid `.iw/config.conf`.

**Prerequisites:**
- A feature branch with at least one phase sub-branch (e.g., `ISSUE-100-phase-01`)
- A `project-management/issues/ISSUE-100/review-state.json` file exists and is committed
- Working tree is clean before running `phase-pr`

**Steps to reproduce:**
1. Check out a phase sub-branch: `git checkout ISSUE-100-phase-01`
2. Run `iw phase-pr --title "Phase 1: Implementation"`
3. After the command completes successfully, run `git status`

**Expected behavior:** Working tree is clean; review-state.json changes are committed.
**Actual behavior:** `review-state.json` is modified but unstaged/uncommitted.

**Reproducibility:** Always

### Root Cause Hypotheses

#### Hypothesis 1: Missing git add + commit after ReviewStateAdapter.update() -- Most Likely

**Evidence:**
- `phase-pr.scala` calls `ReviewStateAdapter.update()` at lines 98-106 (batch) and 111-123 (non-batch) but has zero git operations after those calls.
- `ReviewStateAdapter.update()` only reads, merges, validates, and writes the JSON file to disk -- it does not interact with git (by design, per FCIS architecture).
- `phase-commit.scala` (which does commit) explicitly calls `GitAdapter.stageAll()` then `GitAdapter.commit()` -- this pattern is completely absent from `phase-pr.scala`.
- The issue is 100% reproducible, confirming it is a missing operation rather than a race condition or edge case.

**Likelihood:** High

**Investigation approach:**
1. This is confirmed by code inspection. No further investigation needed.

**Fix strategy:**
- After `ReviewStateAdapter.update()` succeeds in both the batch and non-batch paths, add:
  1. `GitAdapter.stageAll(os.pwd)` (or a targeted stage of just `reviewStatePath`)
  2. `GitAdapter.commit("chore(<issueId>): update review-state for phase <N>", os.pwd)`
- Note: `GitAdapter` currently has `stageAll` but no `stageFiles` method. A targeted `stageFiles` method would be cleaner (avoids staging unrelated files), but `stageAll` is acceptable if the working tree is expected to be clean at this point (which it should be -- `phase-commit` already ran).
- Adding a `stageFiles(paths: Seq[os.Path], dir: os.Path)` method to `GitAdapter` would be the more precise approach.
- In the **non-batch** path, we are still on the phase sub-branch, so the commit goes there and gets pushed (or needs a follow-up push).
- In the **batch** path, we have already switched to the feature branch (line 93) and done `fetchAndReset` (line 95), so the commit goes on the feature branch. This is the correct branch for a "phase_merged" state update.
- Estimated effort: 2-4 hours (including adding `stageFiles` to GitAdapter, updating both paths in phase-pr, and writing tests)

**Risk of fix:** Low -- Adding a commit is an additive operation. The only risk is committing at the wrong time relative to the push (non-batch path: the push happens at line 54 *before* the review-state update, so the review-state commit would need a follow-up push or the commit stays local until next push). This needs careful handling.

---

#### Hypothesis 2: The review-state update was intended to be committed by the caller (workflow script)

**Evidence:**
- Multiple commands (`phase-merge`, `phase-advance`) exhibit the same pattern of updating review-state without committing, suggesting this might have been a deliberate design choice where the caller is expected to commit.
- However, `batch-implement.scala` (the primary caller) checks for a clean working tree *before* proceeding, creating a chicken-and-egg problem.

**Likelihood:** Low -- Even if this was the original intent, it does not work in practice because the callers check for clean working trees.

**Investigation approach:**
1. Check if any workflow script commits review-state after calling phase-pr. (Already confirmed: `batch-implement` does not; it checks for clean tree instead.)

**Fix strategy:**
- Same as Hypothesis 1. The fix belongs in the commands themselves, not in callers.
- Estimated effort: Same as above.

**Risk of fix:** Low

---

## Defect 2: phase-merge does not commit review-state.json updates

### Reproduction Steps

**Environment:** Same as Defect 1.

**Prerequisites:**
- A phase PR exists and CI has passed (or no CI)
- On a phase sub-branch with review-state.json

**Steps to reproduce:**
1. Run `iw phase-merge` on a phase sub-branch with an open PR
2. After merge completes, run `git status` on the feature branch

**Expected behavior:** Working tree is clean after merge.
**Actual behavior:** `review-state.json` is modified but uncommitted on the feature branch.

**Reproducibility:** Always (based on code analysis -- same missing pattern as phase-pr)

### Root Cause Hypotheses

#### Hypothesis 1: Same missing commit pattern as phase-pr -- Most Likely

**Evidence:**
- `phase-merge.scala` updates review-state at three points (lines 92-96 for ci_pending, lines 150-163 for ci_fixing, and lines 199-205 for phase_merged) without any git commit afterward.
- The final update at line 199-205 sets "phase_merged" status after `fetchAndReset` on the feature branch, leaving the file dirty.
- The intermediate updates (ci_pending, ci_fixing) happen while still on the phase sub-branch during CI polling -- these are transient and get squash-merged away, so they are less critical.

**Likelihood:** High

**Investigation approach:**
1. Confirmed by code inspection. The final review-state update at lines 199-205 has no subsequent git operations.

**Fix strategy:**
- Add git add + commit after the final `tryUpdateState` call at line 205, before printing output.
- The intermediate state updates (ci_pending, ci_fixing) during CI polling are debatable -- they happen on the phase sub-branch which gets squash-merged anyway. Committing them would create noise. Consider leaving them uncommitted or committing only the final "phase_merged" update.
- Estimated effort: 1-2 hours

**Risk of fix:** Low

---

## Defect 3: phase-advance does not commit review-state.json update

### Reproduction Steps

**Environment:** Same as Defect 1.

**Prerequisites:**
- A phase PR has been merged externally (not via phase-merge)
- On the phase sub-branch or feature branch

**Steps to reproduce:**
1. Merge a phase PR manually via GitHub UI
2. Run `iw phase-advance`
3. After advance completes, run `git status`

**Expected behavior:** Working tree is clean.
**Actual behavior:** `review-state.json` is modified but uncommitted on the feature branch.

**Reproducibility:** Always (based on code analysis)

### Root Cause Hypotheses

#### Hypothesis 1: Same missing commit pattern -- Most Likely

**Evidence:**
- `phase-advance.scala` lines 92-101 update review-state after `fetchAndReset` (line 87) without any git commit.
- Identical pattern to the other two commands.

**Likelihood:** High

**Fix strategy:**
- Add git add + commit after the review-state update at line 101.
- Estimated effort: 1-2 hours

**Risk of fix:** Low

---

## Technical Decisions

### RESOLVED: Non-batch phase-pr push timing

**Decision:** Push again after committing the review-state update in the non-batch path. This ensures the review-state change appears in the PR diff and the local branch stays in sync with the remote.

### RESOLVED: Targeted staging vs stageAll

**Decision:** Add a `stageFiles(paths: Seq[os.Path], dir: os.Path)` method to `GitAdapter` for targeted staging. This avoids accidentally staging unrelated changes and is generally useful for other commands.

---

## Total Estimates

**Per-Defect Breakdown:**
- Defect 1 (phase-pr uncommitted review-state): 2-4 hours (add stageFiles to GitAdapter + fix both paths + tests)
- Defect 2 (phase-merge uncommitted review-state): 1-2 hours (fix final update path + tests)
- Defect 3 (phase-advance uncommitted review-state): 1-2 hours (fix update path + tests)

**Shared work:**
- Adding `stageFiles` to `GitAdapter`: included in Defect 1 estimate, reused by Defects 2 and 3

**Total Range:** 4 - 8 hours

**Confidence:** High

**Reasoning:**
- Root cause is confirmed by code inspection -- no ambiguity
- Fix pattern is well-established (phase-commit.scala demonstrates the exact git add + commit pattern)
- All three defects share the same root cause pattern, so fixes are nearly identical
- Push timing and staging strategy decisions are resolved

## Testing Strategy

**For each defect:**

1. **Regression test** -- Write a BATS E2E test that:
   - Sets up a git repo with a phase branch and review-state.json
   - Mocks the forge CLI (gh/glab) to avoid real API calls
   - Runs the command
   - Asserts `git status --porcelain` is empty after the command completes
   - Asserts the review-state.json changes are in a commit (via `git log` or `git show`)

2. **Fix verification** -- Confirm the regression test passes after the fix

3. **Side-effect check** -- Run `./iw test` (full test suite) to detect regressions

**Test Data:**
- A minimal git repo with feature branch and phase sub-branch
- A valid `review-state.json` with initial state (e.g., `status: "implementing"`)
- A mock `gh` script that returns a fake PR URL for `phase-pr` tests
- `IW_SERVER_DISABLED=1` in all test setup functions

**Unit tests for GitAdapter.stageFiles:**
- Test staging a specific file (verify it appears in `git diff --cached`)
- Test staging a non-existent file (verify error handling)

## Dependencies

### Prerequisites
- No special access needed -- all code is in the repository
- Test environment only needs git, bash, and bats

### External Blockers
- None

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. Run **dx-create-tasks** with issue IW-292
2. Run **dx-fix** for systematic investigate-and-fix
