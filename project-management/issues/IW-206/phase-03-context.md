# Phase 03: Breadcrumb navigation from project page back to overview

## Goals

Add CSS styling for the breadcrumb navigation and project details page elements. The breadcrumb HTML and its behavior were already implemented in Phase 2 as part of `ProjectDetailsView.scala`, and a unit test verifies the breadcrumb renders correctly. However, Story 2's acceptance criteria include "Styled consistently with the rest of the dashboard", and the architectural sketch calls for "CSS for breadcrumb styling (add to shared styles)". Currently, `dashboard.css` has **no** styles for `.breadcrumb`, `.project-details`, `.project-header`, `.project-metadata`, or `.tracker-type-badge` -- these elements render with browser defaults only.

## What Was Built in Phase 1 and 2

**Phase 1 (Extract CSS/JS to static resources):**
- `PageLayout.render(title, bodyContent, devMode)` -- shared HTML page shell
- `/static/:filename` route in CaskServer serving CSS/JS
- `dashboard.css` (734 lines) and `dashboard.js` extracted from inline code
- DashboardService refactored to use PageLayout

**Phase 2 (Project details page with filtered worktree cards):**
- `ProjectDetailsView.scala` -- renders project page with breadcrumb `nav`, metadata header, and filtered worktree cards
- `MainProjectService.filterByProjectName` -- pure filtering function
- `GET /projects/:projectName` route in CaskServer
- Breadcrumb HTML: `nav.breadcrumb` with `a(href := "/", "Projects")`, separator `>`, and current project name
- 8 unit tests in `ProjectDetailsViewTest.scala`, including "render includes breadcrumb with link to root"

## Scope

### In Scope
- CSS styles for `.breadcrumb` navigation element (font size, color, spacing, separator styling)
- CSS styles for `.project-details` container
- CSS styles for `.project-header` (alignment, spacing)
- CSS styles for `.project-metadata` (tracker badge and team info layout)
- CSS styles for `.tracker-type-badge` (badge appearance)

### Out of Scope
- Project-scoped "Create Worktree" button (Phase 04)
- Project cards linking to project details (Phase 05)
- Unknown project / 404 handling (Phase 06)
- HTMX auto-refresh for project worktree list (Phase 07)
- Any changes to breadcrumb HTML structure (already correct)
- Any changes to breadcrumb link behavior (already links to `/`)

### Already Complete
- Breadcrumb HTML renders "Projects > {projectName}" on the project details page (Phase 2)
- "Projects" link navigates to the root dashboard at `/` (Phase 2)
- Unit test verifying breadcrumb renders with correct link (Phase 2: `ProjectDetailsViewTest`)
- Project metadata header with tracker type badge and team info (Phase 2)
- Empty state for projects with no worktrees (Phase 2)

## Dependencies

- Phase 1: Static file serving and `dashboard.css`
- Phase 2: `ProjectDetailsView.scala` with breadcrumb HTML and `.breadcrumb` / `.project-details` / `.project-header` / `.project-metadata` / `.tracker-type-badge` CSS classes already referenced in the markup

## Approach

The only remaining work is adding CSS rules to `dashboard.css` for the breadcrumb and project-details-page-specific elements. These are purely visual additions -- no Scala code changes needed, no new routes, no logic changes.

Steps:
1. Add `.breadcrumb` styles (font size, color, margin, link styling)
2. Add `.project-details` container styles
3. Add `.project-header` styles (spacing below breadcrumb, alignment)
4. Add `.project-metadata` styles (flex layout for badge + team info)
5. Add `.tracker-type-badge` styles (pill/badge appearance)
6. Verify the project details page renders correctly with the new styles by running the dashboard and inspecting `/projects/:projectName`
7. All existing tests should continue to pass since CSS-only changes do not affect HTML structure

## Status

Phase 3 breadcrumb **behavior** acceptance criteria (rendering "Projects > {projectName}" and linking to `/`) are fully met by the Phase 2 implementation. The remaining work is CSS styling for visual consistency with the rest of the dashboard, per Story 2's acceptance criterion: "Styled consistently with the rest of the dashboard."
