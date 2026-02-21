---
generated_from: a26473acf29f9e417a92c642e960669e9ac4a60d
generated_at: 2026-02-20T17:16:30Z
branch: IW-206-phase-02
issue_id: IW-206
phase: 2
files_analyzed:
  - .iw/core/dashboard/CaskServer.scala
  - .iw/core/dashboard/DashboardService.scala
  - .iw/core/dashboard/application/MainProjectService.scala
  - .iw/core/dashboard/presentation/views/ProjectDetailsView.scala
  - .iw/core/test/ProjectDetailsViewTest.scala
  - .iw/core/test/MainProjectServiceTest.scala
  - .iw/core/test/ProjectFilteringTest.scala
---

# Review Packet: Phase 2 - Project Details Page

## Goals

This phase implements a dedicated project details page that displays project metadata and filtered worktree cards. The page allows users to view all worktrees belonging to a specific project at `/projects/:projectName`.

Key objectives:
- Enable hierarchical navigation with a dedicated project view
- Filter and display only worktrees belonging to the specified project
- Provide project metadata (name, tracker type, team, tracker URL)
- Maintain consistency with existing dashboard patterns (HTMX polling, card rendering)
- Support breadcrumb navigation back to the root dashboard

## Scenarios

- [ ] User navigates to `/projects/:projectName` and sees a full HTML page
- [ ] Page displays project name in heading and breadcrumb
- [ ] Page shows project metadata (tracker type, team, tracker URL link)
- [ ] Breadcrumb "Projects > projectName" links back to root dashboard
- [ ] Only worktree cards for the specified project are displayed
- [ ] Worktree cards have HTMX polling behavior (same as root dashboard)
- [ ] Empty state is shown when project has no worktrees
- [ ] Cards refresh every 30 seconds via HTMX
- [ ] Existing root dashboard functionality remains unchanged

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/dashboard/CaskServer.scala` | `projectDetails()` route handler (line 66) | HTTP entry point for `/projects/:projectName` route |
| `.iw/core/dashboard/application/MainProjectService.scala` | `filterByProjectName()` (line 22) | Core filtering logic that selects worktrees by project name |
| `.iw/core/dashboard/presentation/views/ProjectDetailsView.scala` | `render()` (line 22) | View component that generates the project details page HTML |

## Test Summary

### Unit Tests

**ProjectFilteringTest.scala** - Tests for `filterByProjectName()` logic:
- Unit: `filterByProjectName returns only worktrees matching project name`
- Unit: `filterByProjectName returns empty list when no worktrees match`
- Unit: `filterByProjectName handles worktrees without valid main project path`
- Unit: `filterByProjectName handles multiple projects with similar names`
- Unit: `filterByProjectName is case-sensitive on project names`

**ProjectDetailsViewTest.scala** - Tests for `ProjectDetailsView` rendering:
- Unit: `render includes project name in heading`
- Unit: `render includes breadcrumb with link to root`
- Unit: `render includes tracker type in metadata`
- Unit: `render includes team info in metadata`
- Unit: `render includes tracker URL link when available`
- Unit: `render includes worktree cards for matching worktrees`
- Unit: `render shows empty state when no worktrees`
- Unit: `render cards have HTMX polling attributes`

**MainProjectServiceTest.scala** - Tests for `deriveFromWorktrees()` logic:
- Unit: `deriveFromWorktrees with empty list returns empty list`
- Unit: `deriveFromWorktrees with single worktree returns one project`
- Unit: `deriveFromWorktrees with multiple worktrees from same project returns one project`
- Unit: `deriveFromWorktrees with multiple worktrees from different projects returns multiple projects`
- Unit: `deriveFromWorktrees filters out worktrees without valid issue ID suffix`
- Unit: `deriveFromWorktrees filters out projects with missing config`

All tests verify the core functionality of filtering, rendering, and project derivation.

## Files Changed

### Modified Files

**`.iw/core/dashboard/CaskServer.scala`** (+72 lines)
- Added new `GET /projects/:projectName` route handler (lines 66-132)
- Filters worktrees using `MainProjectService.filterByProjectName()`
- Derives project metadata using `MainProjectService.deriveFromWorktrees()`
- Fetches cached data for filtered worktrees
- Renders page using `PageLayout` + `ProjectDetailsView`
- Returns 404 when project not found

**`.iw/core/dashboard/application/MainProjectService.scala`** (+22 lines)
- Added `filterByProjectName()` function (lines 12-32)
- Uses `MainProject.deriveMainProjectPath()` to extract main project path
- Compares last path component to requested project name
- Returns filtered list of worktrees

**`.iw/core/dashboard/DashboardService.scala`** (+8 lines, -8 lines)
- Minor refactoring to support project details view (no functional changes to root dashboard)

### New Files

**`.iw/core/dashboard/presentation/views/ProjectDetailsView.scala`** (137 lines)
- `render()` - Generates project details page with breadcrumb, metadata, and cards
- `renderWorktreeCard()` - Delegates to `WorktreeCardRenderer` for individual cards
- `capitalizeTrackerType()` - Helper for display formatting
- Includes breadcrumb navigation ("Projects > projectName")
- Displays project metadata header (tracker type, team, tracker URL)
- Shows empty state when no worktrees
- Reuses `WorktreeCardRenderer` with HTMX polling config

**`.iw/core/test/ProjectFilteringTest.scala`** (132 lines)
- Comprehensive unit tests for `filterByProjectName()` function
- Tests exact name matching, empty results, invalid paths, similar names, case sensitivity

**`.iw/core/test/ProjectDetailsViewTest.scala`** (210 lines)
- Unit tests for `ProjectDetailsView.render()` function
- Tests breadcrumb, metadata, cards, empty state, HTMX attributes

**`.iw/core/test/MainProjectServiceTest.scala`** (194 lines)
- Unit tests for `MainProjectService.deriveFromWorktrees()`
- Tests deduplication, config loading, multi-project scenarios

<details>
<summary>File Change Statistics</summary>

```
 .iw/core/dashboard/CaskServer.scala                | 72 +++++++++++++++-
 .iw/core/dashboard/DashboardService.scala          |  8 +-
 .iw/core/dashboard/application/MainProjectService.scala | 22 +++++
 project-management/issues/IW-206/phase-02-tasks.md | 96 ++++++++++----------
 project-management/issues/IW-206/review-state.json | 12 +--
 5 files changed, 150 insertions(+), 60 deletions(-)
```
</details>
