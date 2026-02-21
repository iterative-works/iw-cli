---
generated_from: 8783f60970f5cd83b4f86982b3458775975921ad
generated_at: 2026-02-21T08:00:34Z
branch: IW-206
issue_id: IW-206
phase: 6
files_analyzed:
  - .iw/core/dashboard/CaskServer.scala
  - .iw/core/dashboard/presentation/views/ProjectDetailsView.scala
  - .iw/core/test/ProjectDetailsViewTest.scala
---

# Review Packet: Phase 6 - Handle unknown project name gracefully

## Goals

This phase replaces the plain text "Project not found" 404 response with a user-friendly HTML page that uses the shared PageLayout, shows a clear message, and includes a link back to the projects overview.

Key objectives:
- Provide consistent UI experience for error cases
- Help users navigate back to a working state
- Maintain visual consistency with the rest of the dashboard

## Scenarios

- [ ] When navigating to `/projects/nonexistent-project`, user sees a styled 404 page
- [ ] The 404 page includes the project name that was requested
- [ ] The 404 page includes a breadcrumb showing "Projects > {projectName}"
- [ ] The 404 page includes a link back to the projects overview
- [ ] The 404 page uses the same PageLayout as other dashboard pages

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/dashboard/presentation/views/ProjectDetailsView.scala` | `renderNotFound()` | New method that generates the 404 page content |
| `.iw/core/dashboard/CaskServer.scala` | `projectDetails()` endpoint (None case) | HTTP endpoint that now returns styled 404 instead of plain text |

## Diagrams

### Component Flow

```
GET /projects/unknown
        ↓
CaskServer.projectDetails()
        ↓
MainProjectService.filterByProjectName() → empty list
        ↓
mainProjectOpt = None
        ↓
ProjectDetailsView.renderNotFound(projectName)
        ↓
PageLayout.render(title, bodyContent, devMode)
        ↓
cask.Response(html, 404)
```

### View Structure

```
PageLayout (shared shell)
    ↓
ProjectDetailsView.renderNotFound()
    ├── Breadcrumb: "Projects > {projectName}"
    ├── Empty State Container
    │   ├── Heading: "Project Not Found"
    │   ├── Message: "No worktrees are registered..."
    │   └── Link: "Back to Projects Overview"
```

## Test Summary

### Unit Tests (3 new tests)

All tests in `.iw/core/test/ProjectDetailsViewTest.scala`:

- [x] `renderNotFound includes project name` - Verifies the requested project name appears in the output
- [x] `renderNotFound includes link back to overview` - Verifies `href="/"` link is present
- [x] `renderNotFound includes breadcrumb` - Verifies breadcrumb navigation is rendered

### Existing Tests

The file also contains 12 existing tests for the main `render()` method:
- render includes project name in heading
- render includes breadcrumb with link to root
- render includes tracker type in metadata
- render includes team info in metadata
- render includes tracker URL link when available
- render includes worktree cards for matching worktrees
- render shows empty state when no worktrees
- render cards have HTMX polling attributes
- worktree-list div has hx-get for project-scoped changes endpoint
- worktree-list div has hx-trigger with polling interval
- worktree-list div has hx-swap none for OOB swaps
- worktree-list div has hx-vals with JS expression for card IDs

## Files Changed

### Modified Files (3)

<details>
<summary>.iw/core/dashboard/CaskServer.scala</summary>

**Changes:**
- Updated 404 response in `projectDetails()` endpoint (lines 92-104)
- Changed from plain text response to styled HTML using `ProjectDetailsView.renderNotFound()` and `PageLayout.render()`
- Maintains 404 status code while improving user experience

**Key diff:**
```scala
// Before:
cask.Response(
  data = "Project not found",
  statusCode = 404,
  headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
)

// After:
val bodyContent = ProjectDetailsView.renderNotFound(projectName)
val html = PageLayout.render(
  title = s"$projectName - Not Found",
  bodyContent = bodyContent,
  devMode = devMode
)
cask.Response(
  data = html,
  statusCode = 404,
  headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
)
```
</details>

<details>
<summary>.iw/core/dashboard/presentation/views/ProjectDetailsView.scala</summary>

**Changes:**
- Added new `renderNotFound(projectName: String): Frag` method (lines 147-170)
- Returns a Scalatags fragment with breadcrumb, heading, message, and link
- Reuses existing CSS classes: `project-details`, `breadcrumb`, `empty-state`

**Implementation:**
```scala
def renderNotFound(projectName: String): Frag =
  div(
    cls := "project-details",
    // Breadcrumb navigation
    nav(
      cls := "breadcrumb",
      a(href := "/", "Projects"),
      span(" > "),
      span(projectName)
    ),
    div(
      cls := "empty-state",
      h2("Project Not Found"),
      p(s"No worktrees are registered for project '$projectName'."),
      p(
        a(href := "/", "Back to Projects Overview")
      )
    )
  )
```
</details>

<details>
<summary>.iw/core/test/ProjectDetailsViewTest.scala</summary>

**Changes:**
- Added 3 new unit tests for `renderNotFound()` method (lines 375-388)
- Tests verify project name inclusion, link to overview, and breadcrumb presence
- All tests use simple string assertions on rendered HTML

**Test structure:**
```scala
test("renderNotFound includes project name"):
  val html = ProjectDetailsView.renderNotFound("nonexistent-project").render
  assert(html.contains("nonexistent-project"), "Should include the project name")
```
</details>

---

**Summary:** This phase improves the user experience when navigating to a non-existent project by replacing a plain text error with a properly styled HTML page that maintains visual consistency with the rest of the dashboard and provides clear navigation back to safety.
