# Phase 01: Extract CSS/JS to static resources and create shared layout

## Goals

Extract inline CSS (~735 lines) and JS from `DashboardService.renderDashboard` into external static files served by CaskServer. Create a shared page layout component that both the root dashboard and the upcoming project details page can use.

This is a prerequisite refactoring: all subsequent phases depend on the shared layout to avoid duplicating the HTML page shell.

## Scope

### In Scope
- Extract `DashboardService.styles` CSS string to a static CSS file
- Extract inline visibility-change JS to a static JS file
- Add static resource serving route to `CaskServer`
- Create a shared `PageLayout` component that renders the HTML shell (head, CSS links, script tags, body wrapper)
- Refactor `DashboardService.renderDashboard` to use `PageLayout` instead of building the HTML shell directly
- Verify root dashboard renders identically after refactoring

### Out of Scope
- New routes or pages (that's Phase 02+)
- Changing any CSS rules or JS behavior
- Modifying card rendering, worktree list, or main projects views
- CDN URL changes for HTMX

## Dependencies

- No prior phases
- Requires understanding of Cask framework's static resource serving
- Requires Scalatags library (already in use)

## Approach

### 1. Create static resource files

Create directory `.iw/core/dashboard/resources/static/` and extract:
- `dashboard.css` — contents of `DashboardService.styles` (lines 399-1134)
- `dashboard.js` — the visibility-change event listener (lines 77-83)

### 2. Add static resource serving to CaskServer

Add a route to serve static files from the resources directory. Cask supports `@cask.staticResources` for classpath resources or manual file serving. Since this project uses scala-cli (not a JAR), we need to serve files from the filesystem using a route like:

```scala
@cask.get("/static/:path", subpath = true)
def staticFiles(path: String): cask.Response[Array[Byte]] = ...
```

Set appropriate Content-Type headers based on file extension (text/css, application/javascript).

### 3. Create shared PageLayout component

Create `PageLayout.scala` in `.iw/core/dashboard/presentation/views/` with:
- A `render` method that takes a page title and body content (Scalatags Frag)
- Renders the complete HTML shell: DOCTYPE, head (meta, title, HTMX CDN scripts, CSS link, JS link), body with hx-ext
- Parameters for optional elements (dev mode banner, etc.)

### 4. Refactor DashboardService

- Remove `styles` val and inline script from `renderDashboard`
- Use `PageLayout.render` for the HTML shell
- Pass the dashboard-specific body content (header, projects, worktree list, modal container) as the body parameter
- Remove the manual DOCTYPE concatenation

### 5. Verify

- Run existing tests to ensure no regressions
- Manually verify the dashboard looks identical (CSS and JS behavior unchanged)

## Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `.iw/core/dashboard/resources/static/dashboard.css` | Create | Extracted CSS from DashboardService.styles |
| `.iw/core/dashboard/resources/static/dashboard.js` | Create | Extracted visibility-change JS |
| `.iw/core/dashboard/presentation/views/PageLayout.scala` | Create | Shared HTML page shell component |
| `.iw/core/dashboard/DashboardService.scala` | Modify | Remove inline CSS/JS, use PageLayout |
| `.iw/core/dashboard/CaskServer.scala` | Modify | Add static file serving route |

## Testing Strategy

### Unit Tests
- `PageLayout.render` produces valid HTML with expected CSS/JS links
- `PageLayout.render` includes HTMX CDN script tags
- `PageLayout.render` wraps body content correctly
- `DashboardService.renderDashboard` output contains `/static/dashboard.css` link
- `DashboardService.renderDashboard` output contains `/static/dashboard.js` script
- `DashboardService.renderDashboard` output does NOT contain inline `<style>` block

### Integration Tests
- `GET /` still returns complete HTML dashboard
- `GET /static/dashboard.css` returns CSS with correct Content-Type
- `GET /static/dashboard.js` returns JS with correct Content-Type
- Dashboard page links to the static CSS and JS files

### E2E Tests
- Existing dashboard BATS tests pass without modification (the page should render identically)

## Acceptance Criteria

- [ ] Inline CSS removed from DashboardService, served from `/static/dashboard.css`
- [ ] Inline JS removed from DashboardService, served from `/static/dashboard.js`
- [ ] `PageLayout` component renders complete HTML shell with proper head/body structure
- [ ] `DashboardService.renderDashboard` uses `PageLayout` for HTML shell
- [ ] Root dashboard (`GET /`) renders identically to before the refactoring
- [ ] Static files served with correct Content-Type headers
- [ ] All existing tests pass
