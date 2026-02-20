# Phase 05: Project cards on overview link to project details

## Goals

Make project cards on the root dashboard overview page clickable, linking to the project details page at `/projects/:projectName`. The Create button must continue to work independently without triggering navigation.

## What Was Built in Prior Phases

**Phase 2**: `GET /projects/:projectName` route in CaskServer - the target page for these links
**Phase 4**: Create Worktree button on project details page - confirms the target page is fully functional

## Scope

### In Scope
- Make project name on overview cards a clickable link to `/projects/:projectName`
- Ensure Create button continues to work independently
- Unit tests for the link

### Out of Scope
- Handle unknown project name (Phase 06)
- HTMX auto-refresh (Phase 07)
- Changes to project details page

## User Story

```gherkin
Funkce: Propojení projektových karet na přehledové stránce s detailem projektu
  Jako vývojář
  Chci kliknout na projekt na přehledové stránce
  Aby jsem se dostal na stránku detailu projektu

Scénář: Kliknutí na projektovou kartu
  Pokud jsem na přehledové stránce dashboardu
  A existuje projekt "iw-cli"
  Když kliknu na kartu projektu "iw-cli"
  Pak jsem přesměrován na /projects/iw-cli

Scénář: Projektová karta stále zobrazuje tlačítko Create
  Pokud jsem na přehledové stránce dashboardu
  Když se stránka načte
  Pak projektová karta "iw-cli" má odkaz na detail projektu
  A projektová karta "iw-cli" stále obsahuje tlačítko "Create"
```

## Approach

In `MainProjectsView.renderProjectCard`, wrap the `h3(project.projectName)` in an `<a>` element linking to `/projects/{projectName}`. The Create button is a sibling element (not nested inside the link), so no event propagation issues.

Current structure:
```scala
div(cls := "main-project-card",
  h3(project.projectName),          // ← make this a link
  div(cls := "project-info", ...),
  button(cls := "create-worktree-button", ...)
)
```

Target structure:
```scala
div(cls := "main-project-card",
  a(href := s"/projects/${project.projectName}",  // ← wrap h3 in link
    h3(project.projectName)
  ),
  div(cls := "project-info", ...),
  button(cls := "create-worktree-button", ...)
)
```

## Files to Modify

| File | Description |
|------|-------------|
| `.iw/core/dashboard/presentation/views/MainProjectsView.scala` | Wrap project name in link |

## Files to Create

| File | Description |
|------|-------------|
| `.iw/core/test/MainProjectsViewLinkTest.scala` | Tests for project detail link |

## Acceptance Criteria

- [ ] Clicking project name navigates to `/projects/:projectName`
- [ ] Create button still works (opens modal)
- [ ] Link uses project name (not encoded path) for the URL path
- [ ] All existing tests pass
