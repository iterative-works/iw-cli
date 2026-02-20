# Phase 02: Project details page with filtered worktree cards

## Goals

Create a dedicated project details page at `/projects/:projectName` that shows project metadata (name, tracker type, team) and only the worktree cards belonging to that project. This is the core page for the hierarchical navigation feature.

## What Was Built in Phase 1

- `PageLayout.render(title, bodyContent, devMode)` — shared HTML shell with HTMX CDN scripts, CSS/JS links
- `/static/:filename` route in CaskServer — serves CSS/JS from `.iw/core/dashboard/resources/static/`
- `dashboard.css` and `dashboard.js` extracted as static resources
- DashboardService refactored to use PageLayout

## Scope

### In Scope
- New route `GET /projects/:projectName` in CaskServer
- New view component `ProjectDetailsView` for rendering the project page
- Worktree filtering: given a project name, find all worktrees whose derived main project path ends with that name
- Project metadata display (name, tracker type, team, tracker URL)
- Breadcrumb navigation "Projects > projectName" with link back to root dashboard
- Empty state when project has no worktrees
- Reuse existing `WorktreeCardRenderer` for individual cards
- HTMX per-card polling (same attributes as root dashboard)

### Out of Scope
- Project-scoped "Create Worktree" button (Phase 04)
- Unknown project handling / 404 page (Phase 06)
- Project-scoped list-level sync / changes endpoint (Phase 07)
- Linking project cards on overview to project details (Phase 05)

## Dependencies

- Phase 1: PageLayout component and static file serving
- Existing: `MainProject.deriveMainProjectPath` for path-to-project mapping
- Existing: `MainProjectService.deriveFromWorktrees` for project metadata
- Existing: `WorktreeCardRenderer.renderCard` for card HTML
- Existing: `WorktreeListView` patterns for card list rendering

## Approach

### 1. Worktree Filtering Logic

Add a pure function to filter worktrees by project name:

```scala
def filterByProjectName(
  worktrees: List[WorktreeRegistration],
  projectName: String
): List[WorktreeRegistration] =
  worktrees.filter { wt =>
    MainProject.deriveMainProjectPath(wt.path)
      .exists(_.last == projectName)
  }
```

This uses the existing `deriveMainProjectPath` heuristic which strips issue ID suffixes from worktree paths (e.g., `/home/user/projects/iw-cli-IW-79` → `/home/user/projects/iw-cli`). The last path component is compared to the requested project name.

### 2. ProjectDetailsView Component

Create `ProjectDetailsView.scala` in `.iw/core/dashboard/presentation/views/` with:

- `render(projectName, mainProject, worktreesWithData, now, sshHost)` — renders the full page body:
  - Breadcrumb: "Projects > projectName" (Projects links to `/`)
  - Project metadata header (name, tracker type, team, tracker URL link)
  - Worktree card grid using existing `WorktreeCardRenderer`
  - Empty state message when no worktrees

### 3. New Route in CaskServer

Add `GET /projects/:projectName` route that:
1. Resolves the project name from the URL path
2. Gets all registered worktrees from state
3. Filters worktrees by project name using the filtering function
4. Derives project metadata using `MainProjectService.deriveFromWorktrees`
5. Fetches cached data for each filtered worktree (same as root dashboard)
6. Renders the page using `PageLayout.render` + `ProjectDetailsView.render`

### 4. Card Rendering

Reuse `WorktreeCardRenderer.renderCard` with the same HTMX polling config as the root dashboard. Each card gets:
- `hx-get="/worktrees/:issueId/card"` for per-card refresh
- `hx-trigger="every 30s, refresh from:body"` for polling
- Same card structure (issue title, git status, PR link, etc.)

## Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `.iw/core/dashboard/presentation/views/ProjectDetailsView.scala` | Create | Project page view component |
| `.iw/core/dashboard/CaskServer.scala` | Modify | Add `/projects/:projectName` route |
| `.iw/core/dashboard/application/MainProjectService.scala` | Modify | Add `filterByProjectName` function |

## Testing Strategy

### Unit Tests
- `filterByProjectName` returns only worktrees matching the project name
- `filterByProjectName` returns empty list when no worktrees match
- `filterByProjectName` handles worktrees without valid issue ID suffix
- `ProjectDetailsView.render` includes project name in heading
- `ProjectDetailsView.render` includes breadcrumb with link to root
- `ProjectDetailsView.render` includes worktree cards for matching worktrees
- `ProjectDetailsView.render` shows empty state when no worktrees
- `ProjectDetailsView.render` includes tracker type and team info

### Integration Tests
- `GET /projects/:projectName` returns HTML with filtered worktree cards
- `GET /projects/:projectName` returns only cards for the specified project
- `GET /projects/:projectName` with no matching worktrees shows empty state

### E2E Tests
- Register worktrees for two different projects, verify `/projects/{name}` only shows relevant ones

## Acceptance Criteria

- [ ] `GET /projects/:projectName` returns a full HTML page
- [ ] Page shows project metadata (name, tracker type, team)
- [ ] Page shows breadcrumb "Projects > projectName" linking back to `/`
- [ ] Only worktree cards belonging to the project are shown
- [ ] Worktree cards have the same HTMX polling behavior as root dashboard
- [ ] Empty state shown when project has no worktrees
- [ ] All existing tests pass (root dashboard unchanged)
