# Phase 2 Tasks: Remove flat worktree list from root page

## Setup

- [x] [setup] Read `DashboardService.scala` lines 57-68 (worktreesWithData block) and line 101 (WorktreeListView.render call) to confirm exact removal targets
- [x] [setup] Verify `ProjectDetailsView` does NOT depend on `WorktreeListView.render` (it uses `WorktreeCardRenderer` directly)

## Tests (Red) - Negative assertions that currently FAIL

- [x] [test] Add `DashboardServiceTest`: "renderDashboard root page does NOT contain worktree-list div" -- `assert(!html.contains("worktree-list"))` (fails now because WorktreeListView renders `#worktree-list`)
- [x] [test] Add `DashboardServiceTest`: "renderDashboard root page does NOT contain worktree-card HTML" -- `assert(!html.contains("worktree-card"))` (fails now because skeleton cards are rendered)
- [x] [test] Add `DashboardServiceTest`: "renderDashboard root page does NOT poll /api/worktrees/changes" -- `assert(!html.contains("/api/worktrees/changes"))` (fails now because WorktreeListView adds hx-get polling)
- [x] [test] Run tests, confirm these 3 new tests fail (red)

## Implementation (Green) - Remove worktree list rendering

- [x] [impl] In `DashboardService.scala`, remove `worktreesWithData` computation block (lines 57-68)
- [x] [impl] In `DashboardService.scala`, remove `WorktreeListView.render(worktreesWithData, now, sshHost)` call (line 101)
- [x] [impl] Run the 3 new negative-assertion tests, confirm they now pass (green)

## Test Updates - Fix tests broken by removal

- [x] [test] Update "renderDashboard includes review state when present in cache" (line 45): replace `assert(html.contains("IWLE-123"))` with assertion on `<!DOCTYPE html>` and `iw Dashboard` (already asserted) -- remove the issue-ID assertion
- [x] [test] Update "renderDashboard handles missing review state gracefully" (line 80): remove `assert(html.contains("IWLE-456"))`, keep `<!DOCTYPE html>` assertion
- [x] [test] Update "renderDashboard with multiple worktrees and mixed review state availability" (line 112): remove `assert(html.contains("IWLE-100"))`, `IWLE-200`, `IWLE-300` assertions; assert `main-projects-section` or `No main projects found` instead
- [x] [test] Update "renderDashboard review state cache is keyed by issue ID" (line 149): remove `assert(html.contains("IWLE-789"))`, keep `<!DOCTYPE html>` assertion
- [x] [test] Update "fetchReviewStateForWorktree with missing state file doesn't crash dashboard" (line 184): remove `assert(html.contains("IWLE-MISSING"))`, keep `<!DOCTYPE html>` assertion
- [x] [test] Update "fetchReviewStateForWorktree returns Some(Left) when JSON invalid" (line 206): remove `assert(html.contains("IWLE-INVALID"))`
- [x] [test] Update "fetchReviewStateForWorktree with fake paths renders dashboard" (line 231): remove `assert(html.contains("IWLE-VALID"))`, keep `<!DOCTYPE html>` assertion
- [x] [test] Update "renderDashboard does not crash with invalid review state" (line 251): remove `assert(html.contains("IWLE-OK"))` and `assert(html.contains("IWLE-BAD"))`, keep `<!DOCTYPE html>` assertion
- [x] [test] Update "renderDashboard accepts sshHost parameter" (line 292): remove `assert(html.contains("IWLE-SSH-1"))`, keep `<!DOCTYPE html>` assertion
- [x] [test] Remove "renderDashboard sorts worktrees by priority" test (line 419) -- worktree ordering is no longer a root page concern
- [x] [test] Remove "renderDashboard sort is stable for equal priorities" test (line 469) -- same reason
- [x] [test] Remove "renderDashboard includes Zed button with configured SSH host" test (line 342) -- Zed buttons are in worktree cards only, not root page
- [x] [test] Remove "renderDashboard Zed button uses correct SSH host for multiple worktrees" test (line 360) -- same reason

## Integration

- [x] [integration] Run full unit test suite (`./iw test unit`) -- all tests pass
- [ ] [integration] Run E2E tests (`./iw test e2e`) -- no regressions (tests are slow, manual verification recommended)
- [ ] [integration] Manually verify root page (`/`) shows only header + project cards + modal container
- [ ] [integration] Manually verify project details page (`/projects/:projectName`) still shows worktree cards
