# Phase 04: Project-scoped Create Worktree button

## Goals

Add a "Create Worktree" button to the project details page that opens the existing create-worktree modal, scoped to the current project. The modal must show the project name in its title and scope issue search/creation to the project's configuration.

## What Was Built in Prior Phases

**Phase 1 (Extract CSS/JS to static resources):**
- `PageLayout.render(title, bodyContent, devMode)` -- shared HTML page shell
- `/static/:filename` route in CaskServer serving CSS/JS
- `dashboard.css` and `dashboard.js` extracted as static resources
- DashboardService refactored to use PageLayout

**Phase 2 (Project details page with filtered worktree cards):**
- `ProjectDetailsView.render(projectName, mainProject, worktreesWithData, now, sshHost)` -- renders project page body content
- `MainProjectService.filterByProjectName(worktrees, projectName)` -- filters worktrees by project name
- Route `GET /projects/:projectName` in CaskServer
- Breadcrumb navigation rendered by ProjectDetailsView
- Note: ProjectDetailsView does NOT currently include a `#modal-container` div

**Phase 3 (Breadcrumb navigation and project page styling):**
- CSS styles for `.breadcrumb`, `.project-details`, `.project-header`, `.project-metadata`, `.tracker-type-badge`, `.team-info`

## Scope

### In Scope
- "Create Worktree" button in ProjectDetailsView's project-header section
- `#modal-container` div appended to the ProjectDetailsView body (required for HTMX modal rendering)
- Unit tests for the new button and modal container

### Out of Scope
- Project cards linking to project details (Phase 05)
- Unknown project / 404 handling (Phase 06)
- HTMX auto-refresh for project worktree list (Phase 07)
- Changes to CreateWorktreeModal (already supports projectPath)
- Changes to CaskServer (the `/api/modal/create-worktree?project=...` endpoint already exists)
- Changes to DashboardService or root dashboard

### Already Complete
- `CreateWorktreeModal.render(projectPath: Option[String])` accepts an optional project path, shows project name in title, scopes search/recent endpoints to the project (CreateWorktreeModal.scala:20)
- `GET /api/modal/create-worktree` endpoint in CaskServer accepts optional `project` query parameter (CaskServer.scala:551-558)
- `GET /api/modal/close` endpoint in CaskServer clears the modal container (CaskServer.scala:560)
- Root dashboard has `div(id := "modal-container")` in DashboardService (DashboardService.scala:96) -- this pattern must be replicated in ProjectDetailsView
- `MainProjectsView.renderProjectCard` has a working create button with HTMX attributes (MainProjectsView.scala:53-58) -- this is the reference pattern
- CSS for `.create-worktree-button` and `.create-worktree-button:hover` already exists in dashboard.css (lines 674, 687)

## Dependencies

- Phase 2: ProjectDetailsView component and CaskServer route
- Existing: CreateWorktreeModal with projectPath support
- Existing: `/api/modal/create-worktree` endpoint
- Existing: CSS for `.create-worktree-button`

## Approach

### 1. Add Create Worktree Button to ProjectDetailsView

In the `project-header` section of `ProjectDetailsView.render`, add a button using the same HTMX pattern as `MainProjectsView.renderProjectCard`:

```scala
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Inside the project-header div, after h1 and project-metadata:
val encodedPath = URLEncoder.encode(mainProject.path.toString, StandardCharsets.UTF_8.toString)
button(
  cls := "create-worktree-button",
  attr("hx-get") := s"/api/modal/create-worktree?project=$encodedPath",
  attr("hx-target") := "#modal-container",
  attr("hx-swap") := "innerHTML",
  "+ Create Worktree"
)
```

The `mainProject.path` field (type `os.Path`) is already available in the render method signature.

### 2. Add Modal Container Div

After the worktree cards section (at the end of the outer div in `render`), add:

```scala
div(id := "modal-container")
```

This is required because the button's `hx-target="#modal-container"` needs a target element on the page. Without it, HTMX will silently fail to render the modal. The root dashboard already has this (DashboardService.scala:96).

### 3. No Other Changes Needed

- **No CaskServer changes** -- the `/api/modal/create-worktree` endpoint already handles the `project` query parameter
- **No CreateWorktreeModal changes** -- it already extracts the project name from the path, shows it in the title, and scopes search/recent to the project
- **No CSS changes** -- `.create-worktree-button` styles already exist in dashboard.css

## Current State of Key Files

### ProjectDetailsView.scala (to modify)

```
.iw/core/dashboard/presentation/views/ProjectDetailsView.scala
```

Current `render` method signature:
```scala
def render(
  projectName: String,
  mainProject: MainProject,
  worktreesWithData: List[(WorktreeRegistration, Option[(IssueData, Boolean, Boolean)], Option[WorkflowProgress], Option[GitStatus], Option[PullRequestData], Option[Either[String, ReviewState]])],
  now: Instant,
  sshHost: String
): Frag
```

Current structure of the rendered HTML (outer div):
1. `nav.breadcrumb` -- breadcrumb navigation
2. `div.project-header` -- contains `h1` and `div.project-metadata`
3. Either `div.empty-state` (no worktrees) or `div#worktree-list.worktree-list` (with cards)

Missing: no `#modal-container` div, no create button.

### Reference Pattern: MainProjectsView.renderProjectCard (read only)

```
.iw/core/dashboard/presentation/views/MainProjectsView.scala
```

Lines 53-58 -- the create button pattern to replicate:
```scala
button(
  cls := "create-worktree-button",
  attr("hx-get") := s"/api/modal/create-worktree?project=$encodedPath",
  attr("hx-target") := "#modal-container",
  attr("hx-swap") := "innerHTML",
  "+ Create"
)
```

### Reference: DashboardService modal container (read only)

```
.iw/core/dashboard/DashboardService.scala
```

Line 96 -- the modal container pattern to replicate:
```scala
div(id := "modal-container")
```

## Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `.iw/core/dashboard/presentation/views/ProjectDetailsView.scala` | Modify | Add create button in project-header, add `#modal-container` div |

## Files to Create (Tests)

| File | Action | Description |
|------|--------|-------------|
| `.iw/core/test/ProjectDetailsCreateButtonTest.scala` | Create | Unit tests for create button and modal container rendering |

## Testing Strategy

### Unit Tests (ProjectDetailsCreateButtonTest.scala)

- `render includes create worktree button` -- verify button with `create-worktree-button` class is present
- `render create button has correct hx-get URL with encoded project path` -- verify the `hx-get` attribute contains `/api/modal/create-worktree?project=` with the URL-encoded project path
- `render create button targets modal container` -- verify `hx-target="#modal-container"` attribute
- `render includes modal container div` -- verify `div` with `id="modal-container"` is present
- `render create button uses innerHTML swap` -- verify `hx-swap="innerHTML"` attribute

Test fixture: use the same `MainProject` fixture from `ProjectDetailsViewTest.scala`:
```scala
val project = MainProject(
  path = os.Path("/home/user/projects/iw-cli"),
  projectName = "iw-cli",
  trackerType = "github",
  team = "iterative-works/iw-cli"
)
```

### Integration Tests

Already covered by existing integration patterns -- the CaskServer route at `/projects/:projectName` renders ProjectDetailsView, and the modal endpoint at `/api/modal/create-worktree?project=...` already works. No new integration tests needed.

### E2E Tests

Covered by existing E2E infrastructure -- the create modal flow from project page uses the same endpoints as from the root dashboard. No new E2E tests needed for this phase (the E2E coverage for the create modal flow applies to both entry points).

## Acceptance Criteria

- [ ] "Create Worktree" button visible on the project details page in the project-header section
- [ ] Button has HTMX attributes: `hx-get` with encoded project path, `hx-target="#modal-container"`, `hx-swap="innerHTML"`
- [ ] `#modal-container` div present on the project details page
- [ ] Clicking the button opens the create-worktree modal showing the project name
- [ ] Issue search in the modal is scoped to the project
- [ ] Root dashboard create button functionality is unchanged
- [ ] All existing tests pass

## Constraints

- Reuse existing `CreateWorktreeModal` -- do NOT create a new modal
- Button must use the same HTMX pattern as `MainProjectsView.renderProjectCard`
- The `#modal-container` div must be present on the page for the modal to render into
- Must not break the existing root dashboard create button functionality
- Minimal changes: only `ProjectDetailsView.scala` needs modification (plus new test file)
