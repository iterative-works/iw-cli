# Phase 02 Tasks: Project Details Page with Filtered Worktree Cards

**Issue:** IW-206
**Phase:** 02
**Goal:** Create `/projects/:projectName` route showing project metadata and filtered worktree cards

---

## Setup

- [x] [setup] Verify Phase 1 components are available (PageLayout, static serving)
- [x] [setup] Review existing worktree filtering patterns in MainProjectService
- [x] [setup] Review WorktreeCardRenderer for card HTML structure

---

## Tests

### Worktree Filtering Logic

- [x] [test] Write test: filterByProjectName returns only worktrees matching project name
- [x] [test] Write test: filterByProjectName returns empty list when no worktrees match
- [x] [test] Write test: filterByProjectName handles worktrees without valid main project path
- [x] [test] Write test: filterByProjectName handles multiple projects with similar names
- [x] [test] Write test: filterByProjectName is case-sensitive on project names

### ProjectDetailsView Component

- [x] [test] Write test: ProjectDetailsView.render includes project name in heading
- [x] [test] Write test: ProjectDetailsView.render includes breadcrumb with link to root (/)
- [x] [test] Write test: ProjectDetailsView.render includes tracker type in metadata
- [x] [test] Write test: ProjectDetailsView.render includes team info in metadata
- [x] [test] Write test: ProjectDetailsView.render includes tracker URL link when available
- [x] [test] Write test: ProjectDetailsView.render includes worktree cards for matching worktrees
- [x] [test] Write test: ProjectDetailsView.render shows empty state when no worktrees
- [x] [test] Write test: ProjectDetailsView.render cards have HTMX polling attributes (hx-get, hx-trigger)

### Route Integration

- [ ] [test] Write test: GET /projects/:projectName returns 200 status
- [ ] [test] Write test: GET /projects/:projectName returns HTML content type
- [ ] [test] Write test: GET /projects/:projectName returns full HTML page (with PageLayout)
- [ ] [test] Write test: GET /projects/:projectName returns only cards for the specified project
- [ ] [test] Write test: GET /projects/:projectName excludes cards from other projects
- [ ] [test] Write test: GET /projects/:projectName with no matching worktrees shows empty state
- [ ] [test] Write test: GET /projects/:projectName includes project metadata in response

---

## Implementation

### Worktree Filtering Function

- [x] [impl] Add filterByProjectName function to MainProjectService
- [x] [impl] Implement project name matching using MainProject.deriveMainProjectPath
- [x] [impl] Run tests and verify all filtering tests pass

### ProjectDetailsView Component

- [x] [impl] Create ProjectDetailsView.scala in presentation/views/
- [x] [impl] Implement render method signature (projectName, mainProject, worktreesWithData, now, sshHost)
- [x] [impl] Implement breadcrumb HTML ("Projects > projectName" with link to /)
- [x] [impl] Implement project metadata header (name, tracker type, team, tracker URL)
- [x] [impl] Implement worktree card grid using WorktreeCardRenderer
- [x] [impl] Implement empty state message for no worktrees
- [x] [impl] Run tests and verify all ProjectDetailsView tests pass

### Route Handler

- [x] [impl] Add GET /projects/:projectName route to CaskServer
- [x] [impl] Extract projectName from URL path parameter
- [x] [impl] Get all registered worktrees from server state
- [x] [impl] Filter worktrees using filterByProjectName
- [x] [impl] Derive project metadata using MainProjectService.deriveFromWorktrees
- [x] [impl] Fetch cached data for each filtered worktree
- [x] [impl] Render page using PageLayout.render + ProjectDetailsView.render
- [x] [impl] Run tests and verify all route integration tests pass

### Card HTMX Attributes

- [x] [impl] Verify WorktreeCardRenderer includes hx-get="/worktrees/:issueId/card"
- [x] [impl] Verify WorktreeCardRenderer includes hx-trigger="every 30s, refresh from:body"
- [x] [impl] Run manual test: verify individual cards poll correctly on project page

---

## Integration

- [x] [integration] Run all unit tests and verify they pass
- [x] [integration] Start dashboard server and verify /projects/:projectName route works
- [x] [integration] Register worktrees for two different projects via API
- [x] [integration] Verify /projects/project-one only shows project-one worktrees
- [x] [integration] Verify /projects/project-two only shows project-two worktrees
- [x] [integration] Verify breadcrumb link navigates back to root dashboard
- [x] [integration] Verify HTMX polling updates individual cards on project page
- [x] [integration] Verify empty state displays when project has no worktrees
- [x] [integration] Verify existing root dashboard (GET /) still shows all worktrees
- [x] [integration] Verify existing per-card refresh (GET /worktrees/:issueId/card) still works
- [x] [integration] Run full test suite (./iw test) and verify all tests pass

---

## Notes

- Each task should take 15-30 minutes
- Follow TDD: write tests before implementation
- Use existing WorktreeCardRenderer for card HTML
- Reuse PageLayout from Phase 1 for page shell
- Project name matching uses last component of derived main project path
- Empty state is important for UX when project has no worktrees
