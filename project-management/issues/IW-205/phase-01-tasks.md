# Phase 1 Tasks: Project cards show worktree count and summary status

## Setup

- [x] [setup] Read and understand existing `MainProjectsView`, `MainProjectService`, and `DashboardService` code

## Tests First (TDD)

- [x] [test] Write `ProjectSummaryTest` - test `computeSummaries` with empty inputs
- [x] [test] Write `ProjectSummaryTest` - test single project with multiple worktrees returns correct count
- [x] [test] Write `ProjectSummaryTest` - test multiple projects return correct per-project counts
- [x] [test] Write `ProjectSummaryTest` - test worktree with `needsAttention == Some(true)` increments attention count
- [x] [test] Write `ProjectSummaryTest` - test worktree with no review state does NOT count as needing attention
- [x] [test] Write `ProjectSummaryTest` - test worktree with `needsAttention == Some(false)` does NOT count
- [x] [test] Update `MainProjectsViewTest` - existing tests use `ProjectSummary` wrapper
- [x] [test] Write `MainProjectsViewTest` - test worktree count text appears in card HTML
- [x] [test] Write `MainProjectsViewTest` - test attention indicator appears when count > 0
- [x] [test] Write `MainProjectsViewTest` - test attention indicator absent when count == 0
- [x] [test] Write `DashboardServiceTest` - test dashboard HTML contains worktree count for projects

## Implementation

- [x] [impl] Create `ProjectSummary` case class and `computeSummaries` function
- [x] [impl] Update `MainProjectsView.render` signature to accept `List[ProjectSummary]`
- [x] [impl] Add worktree count and attention indicator to `renderProjectCard`
- [x] [impl] Update `DashboardService.renderDashboard` to compute and pass summaries

## Integration

- [x] [integration] Run full test suite and verify all tests pass
- [x] [integration] Verify existing E2E tests still pass
