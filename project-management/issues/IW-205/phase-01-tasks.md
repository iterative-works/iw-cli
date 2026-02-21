# Phase 1 Tasks: Project cards show worktree count and summary status

## Setup

- [ ] [setup] Read and understand existing `MainProjectsView`, `MainProjectService`, and `DashboardService` code

## Tests First (TDD)

- [ ] [test] Write `ProjectSummaryTest` - test `computeSummaries` with empty inputs
- [ ] [test] Write `ProjectSummaryTest` - test single project with multiple worktrees returns correct count
- [ ] [test] Write `ProjectSummaryTest` - test multiple projects return correct per-project counts
- [ ] [test] Write `ProjectSummaryTest` - test worktree with `needsAttention == Some(true)` increments attention count
- [ ] [test] Write `ProjectSummaryTest` - test worktree with no review state does NOT count as needing attention
- [ ] [test] Write `ProjectSummaryTest` - test worktree with `needsAttention == Some(false)` does NOT count
- [ ] [test] Update `MainProjectsViewTest` - existing tests use `ProjectSummary` wrapper
- [ ] [test] Write `MainProjectsViewTest` - test worktree count text appears in card HTML
- [ ] [test] Write `MainProjectsViewTest` - test attention indicator appears when count > 0
- [ ] [test] Write `MainProjectsViewTest` - test attention indicator absent when count == 0
- [ ] [test] Write `DashboardServiceTest` - test dashboard HTML contains worktree count for projects

## Implementation

- [ ] [impl] Create `ProjectSummary` case class and `computeSummaries` function
- [ ] [impl] Update `MainProjectsView.render` signature to accept `List[ProjectSummary]`
- [ ] [impl] Add worktree count and attention indicator to `renderProjectCard`
- [ ] [impl] Update `DashboardService.renderDashboard` to compute and pass summaries

## Integration

- [ ] [integration] Run full test suite and verify all tests pass
- [ ] [integration] Verify existing E2E tests still pass
