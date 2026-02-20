# Phase 06: Handle unknown project name gracefully

## Goals

Replace the plain text "Project not found" 404 response with a user-friendly HTML page that uses the shared PageLayout, shows a clear message, and includes a link back to the projects overview.

## What Was Built in Prior Phases

**Phase 1**: `PageLayout.render(title, bodyContent, devMode)` - shared HTML page shell
**Phase 2**: `GET /projects/:projectName` route in CaskServer - currently returns plain "Project not found" text on 404

## Scope

### In Scope
- Add `renderNotFound(projectName)` method to `ProjectDetailsView`
- Modify CaskServer 404 response to use PageLayout with the not-found view
- Unit tests for the not-found rendering

### Out of Scope
- HTMX auto-refresh (Phase 07)
- URL encoding/decoding (Cask handles this automatically)

## User Story

```gherkin
Funkce: Zpracování neexistujícího projektu
  Jako vývojář
  Chci vidět srozumitelnou chybovou zprávu
  Aby jsem věděl že projekt neexistuje nebo nemá žádné worktree

Scénář: Neznámý projekt
  Pokud neexistuje žádný registrovaný worktree pro projekt "neexistujici-projekt"
  Když otevřu /projects/neexistujici-projekt
  Pak vidím stránku s informací že projekt nebyl nalezen
  A vidím odkaz zpět na přehled projektů
```

## Approach

1. **`ProjectDetailsView.renderNotFound(projectName: String): Frag`** - Returns a styled not-found page body with:
   - Breadcrumb: "Projects > {projectName}"
   - Heading: "Project Not Found"
   - Message: "No worktrees are registered for project '{projectName}'"
   - Link: "Back to Projects Overview" linking to "/"

2. **CaskServer** - Replace plain text 404 with:
   ```scala
   val bodyContent = ProjectDetailsView.renderNotFound(projectName)
   val html = PageLayout.render(
     title = s"$projectName - Not Found",
     bodyContent = bodyContent,
     devMode = devMode
   )
   cask.Response(data = html, statusCode = 404, headers = ...)
   ```

## Files to Modify

| File | Description |
|------|-------------|
| `.iw/core/dashboard/presentation/views/ProjectDetailsView.scala` | Add `renderNotFound` method |
| `.iw/core/dashboard/CaskServer.scala` | Use PageLayout for 404 response |

## Testing

- Test `renderNotFound` includes project name
- Test `renderNotFound` includes link back to overview
- Test `renderNotFound` includes breadcrumb
