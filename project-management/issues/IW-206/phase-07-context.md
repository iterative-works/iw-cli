# Phase 07: HTMX auto-refresh for project worktree list

## User Story

As a developer, I want the project details page worktree list to auto-refresh, so I see current state without manually reloading.

## Acceptance Criteria

1. Per-card HTMX polling works on the project details page (same as root dashboard)
2. List-level sync detects additions and removals of worktrees within the project scope
3. Cards added to other projects do not appear on this project page

## What Was Built in Previous Phases

- Phase 1: `PageLayout` shared HTML shell, static CSS/JS serving, `/static/:filename` route
- Phase 2: `ProjectDetailsView.render(...)` with filtered worktree cards, `MainProjectService.filterByProjectName`, `/projects/:projectName` route in `CaskServer`
- Phase 3: CSS styles for breadcrumb, project header, metadata
- Phase 4: Create Worktree button and `#modal-container` in `ProjectDetailsView`
- Phase 5: Project name links in overview cards
- Phase 6: Styled 404 page for unknown projects

## Available Utilities

- `WorktreeListSync.detectChanges(oldIds, newIds)` - pure function detecting additions/deletions/reorders
- `WorktreeListSync.generateChangesResponse(...)` - generates HTMX OOB swap HTML
- `MainProjectService.filterByProjectName(worktrees, projectName)` - filters worktrees by project
- `WorktreeCardRenderer` with `HtmxCardConfig.dashboard` for per-card HTMX polling
- Existing `/api/worktrees/changes` endpoint pattern in `CaskServer`

## Technical Approach

### Per-card polling (already works)
Each card rendered by `WorktreeCardRenderer` with `HtmxCardConfig.dashboard` already has `hx-get="/worktrees/{issueId}/card"` and `hx-trigger="every 30s"`. This works automatically on the project page - no changes needed.

### List-level sync (new)
The `#worktree-list` div in `ProjectDetailsView` needs HTMX attributes to poll a project-scoped changes endpoint:
- `hx-get="/api/projects/{projectName}/worktrees/changes"`
- `hx-vals="js:{have: [...document.querySelectorAll('#worktree-list > [id^=\"card-\"]')].map(e => e.id.replace('card-', '')).join(',')}"`
- `hx-trigger="every 30s"`
- `hx-swap="none"` (OOB swaps handle the DOM updates)

A new route `GET /api/projects/:projectName/worktrees/changes` in `CaskServer` will:
1. Get all worktrees from server state
2. Filter by project name using `MainProjectService.filterByProjectName`
3. Extract current IDs
4. Call `WorktreeListSync.detectChanges(clientIds, filteredIds)`
5. Call `WorktreeListSync.generateChangesResponse(...)` to produce OOB swap HTML

## Files to Modify

- `.iw/core/dashboard/presentation/views/ProjectDetailsView.scala` - Add HTMX attributes to `#worktree-list` div
- `.iw/core/dashboard/CaskServer.scala` - Add `/api/projects/:projectName/worktrees/changes` route

## Testing Strategy

- Unit tests for `ProjectDetailsView`: verify HTMX attributes on worktree-list div
- Unit test for `CaskServer` route behavior: verify project-scoped filtering (via existing test patterns)
