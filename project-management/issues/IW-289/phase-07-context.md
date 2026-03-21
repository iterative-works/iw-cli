# Phase 7: batch-implement integration

## Goals

Replace the inline merge logic in `batch-implement.scala` with a subprocess call to `iw phase-merge`. After this phase, the batch-implement loop becomes: `phase-start -> agent -> phase-pr (no --batch) -> phase-merge`. CI is verified before each phase is merged, and the existing `handleMergePR` function is removed.

## Scope

### In scope

- Modify `batch-implement.scala` to invoke `iw phase-merge` as a subprocess after the agent creates a PR
- Remove `handleMergePR` function entirely — `phase-merge` handles CI polling, merge, branch advance, and review-state update
- Change `PhaseOutcome.MergePR` handling to call `phase-merge` subprocess instead of inline merge
- Update `BatchImplement.decideOutcome` — `"awaiting_review"` should now trigger `phase-merge` subprocess call (the outcome enum may need a rename or the handler changes)
- Ensure `phase-merge` failure (non-zero exit) stops the batch loop with a clear message
- Add `--timeout`, `--poll-interval`, and `--max-retries` passthrough flags (or use defaults)
- Fix the two pre-existing BATS test failures in `phase-merge.bats` (tests 7-8: single-quoted heredoc preventing `$TEST_DIR` expansion)
- BATS E2E tests for the new batch-implement + phase-merge integration

### Out of scope

- Changes to `phase-merge.scala` itself (complete as of Phase 6)
- Changes to `phase-pr.scala` behavior (the `--batch` flag remains available for manual use, just no longer used by batch-implement)
- Changes to pure model types (`PhaseMerge`, `CICheckResult`, etc.)
- New `phase-merge` CLI flags

## Dependencies

- **Phase 1-6** — `phase-merge` command is complete, works on both GitHub and GitLab
- **Existing** — `BatchImplement.decideOutcome` in `.iw/core/model/BatchImplement.scala`
- **Existing** — `PhaseOutcome` enum with `MergePR`, `MarkDone`, `Recover`, `Fail` cases
- **Existing** — `batch-implement.scala` orchestration loop with `handleOutcome`, `handleMergePR`, `attemptRecovery`
- **Existing** — `ProcessAdapter.runStreaming` for subprocess execution with timeout

## Approach

### 1. Change how the agent's `phase-pr` is invoked

Currently the agent (claude) is given a prompt that includes `phase-pr`. The agent decides internally whether to use `--batch` or not. In the batch-implement context, the agent calls `phase-pr` which creates a PR and sets review-state to `awaiting_review`. This part does NOT need to change — the agent already calls `phase-pr` without `--batch` in the normal flow and the status becomes `awaiting_review`.

The key change is what happens AFTER the agent finishes and the status is `awaiting_review`.

### 2. Replace `handleMergePR` with `phase-merge` subprocess call

Currently `handleMergePR` (lines 201-248 of `batch-implement.scala`):
1. Reads PR URL from review-state.json
2. Validates the URL
3. Builds merge command (`gh pr merge` or `glab mr merge`)
4. Runs the merge (no CI check)
5. Checks out and advances the feature branch
6. Updates review-state to `phase_merged`
7. Calls `markAndCommitPhase`

Replace this with a new function (e.g., `invokePhaseMerge`) that:
1. Runs `./iw phase-merge` as a subprocess via `ProcessAdapter.runStreaming`
2. `phase-merge` handles everything: CI polling, retry, merge, branch advance, review-state update
3. On success (exit 0), `batch-implement` calls `markAndCommitPhase`
4. On failure (non-zero exit), `batch-implement` logs the error and stops

```scala
def invokePhaseMerge(phaseNum: Int): Unit =
  log(s"[phase $phaseNum] Invoking phase-merge to wait for CI and merge...")
  val phaseMergeCmd = Seq(
    (cwd / "iw").toString, "phase-merge"
  )
  // phase-merge has its own internal timeout (default 30m) plus may run recovery agents.
  // Use a very large outer timeout to avoid killing it prematurely.
  val phaseMergeTimeoutMs = 4L * 60 * 60 * 1000 // 4 hours
  val exitCode = ProcessAdapter.runStreaming(phaseMergeCmd, phaseMergeTimeoutMs)
  if exitCode != 0 then
    log(s"[phase $phaseNum] phase-merge failed (exit code $exitCode). Stopping batch.")
    log(s"[phase $phaseNum] Check the PR status and resolve manually, then re-run batch-implement.")
    logWriter.close()
    sys.exit(1)
  log(s"[phase $phaseNum] phase-merge completed successfully")
  markAndCommitPhase(phaseNum)
```

### 3. Update `handleOutcome` to use the new function

In `handleOutcome`, change the `MergePR` case:

```scala
case PhaseOutcome.MergePR =>
  invokePhaseMerge(phaseNum)   // was: handleMergePR(phaseNum)
```

### 4. Remove `handleMergePR` entirely

The entire `handleMergePR` function (lines 201-248) can be deleted. All its responsibilities are now handled by `phase-merge`:
- CI polling (phases 3-4)
- CI failure recovery (phase 5)
- Merge with `--merge --delete-branch` (phases 3, 6)
- Branch advance (phase 3)
- Review-state update to `phase_merged` (phase 3)

### 5. Remove the local `readPrUrl` closure

The local `readPrUrl()` closure in `batch-implement.scala` (lines 149-154) was only used by `handleMergePR`. It can be removed. Note: `ReviewStateAdapter.readPrUrl` in the adapter layer is a separate method used by `phase-merge.scala` and must NOT be touched.

### 6. Consider merge strategy consistency

The current `handleMergePR` in batch-implement uses `--merge` for GitHub (no `--squash`), which is consistent with what `phase-merge` does. The `phase-pr --batch` path uses `--squash`, but that path is no longer invoked by batch-implement. No merge strategy changes needed.

### 7. Branch state consideration

After the agent finishes (claude exits), the working directory is on the phase sub-branch. `phase-merge` expects to be on the phase sub-branch (it reads the branch name to detect phase number and feature branch). This is consistent — no branch switching needed between agent completion and `phase-merge` invocation.

After `phase-merge` succeeds, it switches to the feature branch and advances it. The current branch is now the feature branch. The next iteration of `runPhases` gives the agent a prompt that triggers `phase-start`, which creates a new phase sub-branch from the feature branch.

Note: `markAndCommitPhase` is still needed after `phase-merge` returns — `phase-merge` updates review-state to `phase_merged`, but `markAndCommitPhase` checks off the phase in `tasks.md` and commits that. These are separate concerns.

### 8. Fix pre-existing BATS test failures

Tests 7-8 in `phase-merge.bats` fail because the mock `gh` script heredoc uses single-quoted `<< 'GHEOF'` which prevents `$TEST_DIR` variable expansion inside the heredoc body. The mock writes to literal `$TEST_DIR/gh-calls.log` instead of the actual path. Fix by changing `<< 'GHEOF'` to `<< GHEOF` (unquoted) so `$TEST_DIR` expands. The mock also references `$1`, `$2` — these must be escaped as `\$1`, `\$2` to prevent premature expansion in the unquoted heredoc.

## Files to modify

| File | Changes |
|------|---------|
| `.iw/commands/batch-implement.scala` | Remove `handleMergePR` and local `readPrUrl` closure; add `invokePhaseMerge`; update `handleOutcome` to call `invokePhaseMerge` |
| `.iw/core/model/BatchImplement.scala` | Possibly no changes — `PhaseOutcome.MergePR` still makes sense as a trigger name, just the handler changes |
| `.iw/test/batch-implement.bats` | Add E2E tests for phase-merge integration |
| `.iw/test/phase-merge.bats` | Fix tests 7-8 (single-quoted heredoc `$TEST_DIR` expansion) |

## Testing strategy

### Unit tests

- `BatchImplement.decideOutcome` — verify `"awaiting_review"` still maps to `PhaseOutcome.MergePR` (no change expected, but confirm)
- No new pure model logic is being added, so minimal new unit tests

### E2E BATS tests (batch-implement.bats)

These need careful mock setup because the full batch flow involves: claude stub -> phase-pr stub -> phase-merge subprocess.

**Test 1: batch-implement invokes phase-merge after successful phase-pr**

Setup a stub claude that:
- On first call: simulates implementation, updates review-state to `awaiting_review` with a `pr_url`

Setup a stub `iw` script (or intercept `phase-merge` call) to verify `phase-merge` is invoked. Since `batch-implement` calls `./iw phase-merge`, the test can wrap or replace the `iw` script.

Actually, since `batch-implement` will call `./iw phase-merge` and `phase-merge` needs a real phase branch + CI setup, the simpler approach is:
- Have the claude stub set review-state to `phase_merged` directly (simulating that phase-merge already completed), so `handleOutcome` hits `MarkDone` instead of `MergePR`. This tests that the loop correctly marks and advances.
- OR: Have the claude stub set review-state to `awaiting_review`, then mock `./iw` to intercept the `phase-merge` subcommand and record that it was called.

The second approach is more meaningful. Create a wrapper `iw` script in the test that:
- When called with `phase-merge`, records the call and updates review-state to `phase_merged`
- When called with anything else, delegates to the real `iw`

**Test 2: batch-implement stops when phase-merge fails**

Same setup, but the mock `iw phase-merge` exits with code 1. Verify batch-implement stops and does not proceed to the next phase.

**Test 3 (assertion within Tests 1/2, not standalone):**

Ensure no direct `gh pr merge` or `glab mr merge` calls are made by batch-implement itself — verify the call log does not contain merge commands. This is an assertion within Tests 1 and 2, not a standalone test.

### Fix existing BATS failures

**phase-merge.bats tests 7-8**: Change the single-quoted heredoc delimiter to unquoted, escape literal `$` in mock script bodies where needed.

### What does NOT need new tests

- `phase-merge` behavior — fully tested in phases 3-6
- `phase-pr` behavior — unchanged
- `BatchImplement.decideOutcome` — unchanged, already tested

## Acceptance criteria

- [ ] `batch-implement` invokes `iw phase-merge` subprocess when review-state status is `awaiting_review`
- [ ] `batch-implement` does NOT contain inline PR merge logic (no `gh pr merge` / `glab mr merge` calls)
- [ ] `handleMergePR` function is removed from `batch-implement.scala`
- [ ] Local `readPrUrl` closure is removed from `batch-implement.scala` (only used by `handleMergePR`; `ReviewStateAdapter.readPrUrl` in adapter layer is NOT touched)
- [ ] When `phase-merge` exits successfully (code 0), batch-implement marks the phase done and continues to the next phase
- [ ] When `phase-merge` exits with non-zero code, batch-implement stops with a clear error message
- [ ] The phase loop is: agent (claude) -> phase-pr creates PR -> phase-merge waits for CI + merges -> mark phase done -> next phase
- [ ] Pre-existing BATS test failures in `phase-merge.bats` (tests 7-8) are fixed
- [ ] New BATS E2E tests verify batch-implement calls phase-merge
- [ ] New BATS E2E test verifies batch-implement stops on phase-merge failure
- [ ] Existing batch-implement BATS tests still pass (no regressions)
- [ ] Existing phase-merge BATS tests all pass (including fixed tests 7-8)
- [ ] Core compiles with `-Werror`
