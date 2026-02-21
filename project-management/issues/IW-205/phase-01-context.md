# Phase 1: Project cards show worktree count and summary status

## Goals

Enhance project cards on the dashboard root page to display worktree count and attention indicators for each project. This makes the root page useful as a projects overview, showing at a glance how many worktrees each project has and whether any need attention.

## Scope

### In Scope
- Create a `ProjectSummary` view model pairing `MainProject` with worktree count and attention count
- Create a pure function to compute project summaries from worktrees and review state cache
- Modify `MainProjectsView.render` to accept and display summary data (worktree count badge, attention indicator)
- Update `DashboardService.renderDashboard` to compute and pass summaries to `MainProjectsView`
- Unit tests for the pure summary computation function
- Unit tests for `MainProjectsView` rendering with summary data
- Integration tests for `DashboardService` output with summary data

### Out of Scope
- Removing the flat worktree list from the root page (Phase 2)
- Simplifying `DashboardService` signature (Phase 3)
- CSS styling changes (use existing card styles, add minimal new classes)
- Any new API endpoints

## Dependencies

### Prior Work (Already Done)
- `MainProject` domain model exists at `.iw/core/dashboard/domain/MainProject.scala`
- `MainProjectService.deriveFromWorktrees` exists at `.iw/core/dashboard/application/MainProjectService.scala`
- `ReviewState.needsAttention` field exists in `.iw/core/model/ReviewState.scala`
- `CachedReviewState` wraps `ReviewState` in `.iw/core/model/CachedReviewState.scala`
- `DashboardService.renderDashboard` already receives `reviewStateCache: Map[String, CachedReviewState]` and `worktrees: List[WorktreeRegistration]`
- `MainProjectsView.render` already renders project cards

### Required By This Phase
- None external

## Approach

### 1. Create `ProjectSummary` view model

Location: `.iw/core/dashboard/presentation/views/ProjectSummary.scala`

A simple case class pairing a `MainProject` with computed summary data:
```scala
case class ProjectSummary(
  project: MainProject,
  worktreeCount: Int,
  attentionCount: Int
)
```

This is a presentation-layer type (view model), so it lives in the `presentation/views` package.

### 2. Create pure summary computation function

Location: `.iw/core/dashboard/presentation/views/ProjectSummary.scala` (companion object)

A pure function that takes:
- `worktrees: List[WorktreeRegistration]`
- `projects: List[MainProject]`
- `reviewStateCache: Map[String, CachedReviewState]`

And returns `List[ProjectSummary]`.

Logic:
1. Group worktrees by their derived main project path (using `MainProject.deriveMainProjectPath`)
2. For each project, count total worktrees
3. For each project, count worktrees where `reviewStateCache.get(wt.issueId).exists(_.state.needsAttention == Some(true))`
4. Return `ProjectSummary` for each project

### 3. Modify `MainProjectsView.render`

Change the signature from:
```scala
def render(projects: List[MainProject]): Frag
```
To:
```scala
def render(summaries: List[ProjectSummary]): Frag
```

Update `renderProjectCard` to accept `ProjectSummary` and render:
- Worktree count text (e.g., "3 worktrees") with a `worktree-count` CSS class
- Attention indicator (e.g., "1 needs attention") with an `attention-count` CSS class, only shown when > 0

### 4. Update `DashboardService.renderDashboard`

After deriving `mainProjects`, compute summaries using the pure function and pass them to `MainProjectsView.render(summaries)` instead of `MainProjectsView.render(mainProjects)`.

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/dashboard/presentation/views/ProjectSummary.scala` | **NEW** - View model + pure computation |
| `.iw/core/dashboard/presentation/views/MainProjectsView.scala` | Change render signature, add summary display |
| `.iw/core/dashboard/DashboardService.scala` | Compute summaries, pass to view |
| `.iw/core/test/ProjectSummaryTest.scala` | **NEW** - Unit tests for summary computation |
| `.iw/core/test/MainProjectsViewTest.scala` | Update existing tests for new signature |
| `.iw/core/test/DashboardServiceTest.scala` | Add integration tests for summary display |

## Testing Strategy

### Unit Tests (ProjectSummaryTest)
- Empty worktrees list returns empty summaries
- Single project with multiple worktrees returns correct count
- Multiple projects return correct per-project counts
- Worktree with `needsAttention == Some(true)` is counted
- Worktree with `needsAttention == Some(false)` is NOT counted
- Worktree with no review state cache entry is NOT counted as needing attention
- Zero attention count when no worktrees need attention
- Correctly associates worktrees with projects via path derivation

### Unit Tests (MainProjectsViewTest - updated)
- Render with summary showing "N worktrees" text
- Render with zero worktrees shows "0 worktrees"
- Render with attention count > 0 shows attention indicator
- Render with attention count == 0 does not show attention indicator
- Existing tests updated to use `ProjectSummary` wrapper

### Integration Tests (DashboardServiceTest)
- Dashboard HTML contains worktree count text for projects
- Dashboard HTML contains attention indicator when review state has needsAttention

## Acceptance Criteria

- [ ] Project cards display worktree count (e.g., "3 worktrees")
- [ ] Project cards display attention count when > 0 (e.g., "1 needs attention")
- [ ] Counts are computed from actual registered worktree data and review state cache
- [ ] Zero-worktree projects show "0 worktrees"
- [ ] All existing tests pass (updated for new signature)
- [ ] New unit tests for `ProjectSummary` computation
- [ ] New/updated tests for `MainProjectsView` with summary data
