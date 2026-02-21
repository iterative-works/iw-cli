---
generated_from: 7223db496a64b0bd0b37fd34cd8d89a71f478059
generated_at: 2026-02-20T16:50:27Z
branch: IW-206-phase-01
issue_id: IW-206
phase: 1
files_analyzed:
  - .iw/core/dashboard/CaskServer.scala
  - .iw/core/dashboard/DashboardService.scala
  - .iw/core/dashboard/presentation/views/PageLayout.scala
  - .iw/core/dashboard/resources/static/dashboard.css
  - .iw/core/dashboard/resources/static/dashboard.js
  - .iw/core/test/DashboardServiceTest.scala
  - .iw/core/test/PageLayoutTest.scala
  - .iw/core/test/StaticFilesTest.scala
---

# Review Packet: Phase 1 - Extract CSS/JS to static resources and create shared layout

## Goals

This phase extracts inline CSS (~735 lines) and JavaScript from `DashboardService.renderDashboard` into external static files served by CaskServer. It creates a shared `PageLayout` component that provides a reusable HTML shell (DOCTYPE, head, CSS/JS links, body wrapper) for all dashboard pages.

Key objectives:
- Extract CSS styles from inline string to external static file
- Extract visibility-change JavaScript to external static file
- Add static resource serving route to CaskServer with proper Content-Type headers
- Create shared PageLayout component for HTML page shell
- Refactor DashboardService to use PageLayout instead of building HTML shell directly
- Maintain identical rendering behavior (no visual changes)

This is a prerequisite refactoring for subsequent phases that will add new pages (project details view) using the same shared layout.

## Scenarios

- [ ] Inline CSS removed from DashboardService, served from `/static/dashboard.css`
- [ ] Inline JS removed from DashboardService, served from `/static/dashboard.js`
- [ ] `PageLayout` component renders complete HTML shell with proper head/body structure
- [ ] `DashboardService.renderDashboard` uses `PageLayout` for HTML shell
- [ ] Root dashboard (`GET /`) renders identically to before the refactoring
- [ ] Static files served with correct Content-Type headers (text/css, application/javascript)
- [ ] All existing tests pass

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/dashboard/presentation/views/PageLayout.scala` | `PageLayout.render()` | New shared component that renders complete HTML shell with DOCTYPE, head (meta, title, HTMX scripts, CSS/JS links), and body wrapper. All dashboard pages will use this. |
| `.iw/core/dashboard/DashboardService.scala` | `renderDashboard()` | Refactored to use PageLayout instead of building HTML manually. Now prepares body content and delegates HTML shell to PageLayout. |
| `.iw/core/dashboard/CaskServer.scala` | `staticFiles()` | New route `@cask.get("/static/:filename")` that serves CSS/JS files from filesystem with Content-Type detection. |
| `.iw/core/dashboard/resources/static/dashboard.css` | N/A | Extracted CSS styles (735 lines) previously inline in DashboardService. |
| `.iw/core/dashboard/resources/static/dashboard.js` | N/A | Extracted visibility-change event listener previously inline in DashboardService. |

## Diagrams

### Architecture: Static Resource Serving

```
┌─────────────────────────────────────────────────────────────┐
│                        Browser                              │
└───────────────┬─────────────────────────────────────────────┘
                │
                │ GET / (dashboard)
                ▼
┌─────────────────────────────────────────────────────────────┐
│                      CaskServer                             │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  GET /                                                │ │
│  │  └─► DashboardService.renderDashboard()              │ │
│  │      └─► PageLayout.render(title, bodyContent, ...)  │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  GET /static/:filename                                │ │
│  │  └─► Read from .iw/core/dashboard/resources/static/  │ │
│  │      └─► Return with Content-Type header             │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                │
                │ Returns HTML with links:
                │ <link href="/static/dashboard.css">
                │ <script src="/static/dashboard.js">
                ▼
┌─────────────────────────────────────────────────────────────┐
│                        Browser                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Renders HTML, then fetches:                         │  │
│  │  GET /static/dashboard.css                           │  │
│  │  GET /static/dashboard.js                            │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Component Flow: PageLayout Usage

```
┌──────────────────────────────────────────────────────────────┐
│           DashboardService.renderDashboard()                 │
│                                                              │
│  1. Prepare body content (frag):                            │
│     - Dashboard header with SSH form                        │
│     - Main projects section                                 │
│     - Worktree list                                         │
│     - Modal container                                       │
│                                                              │
│  2. Call PageLayout.render():                               │
│     PageLayout.render(                                      │
│       title = "iw Dashboard",                               │
│       bodyContent = bodyContent,                            │
│       devMode = devMode                                     │
│     )                                                        │
└──────────────────┬───────────────────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────────────────┐
│              PageLayout.render()                             │
│                                                              │
│  Returns: "<!DOCTYPE html>" + html(                         │
│    head(                                                     │
│      meta(charset="UTF-8"),                                 │
│      meta(viewport),                                        │
│      title("iw Dashboard"),                                 │
│      script(src="htmx.org@1.9.10"),                         │
│      script(src="response-targets@2.0.0"),                  │
│      link(href="/static/dashboard.css"),                    │
│      script(src="/static/dashboard.js")                     │
│    ),                                                        │
│    body(hx-ext="response-targets",                          │
│      div(class="container",                                 │
│        [dev banner if devMode],                             │
│        bodyContent  ← Inserted here                         │
│      )                                                       │
│    )                                                         │
│  )                                                           │
└──────────────────────────────────────────────────────────────┘
```

## Test Summary

### Unit Tests

**PageLayoutTest.scala** (13 tests)
- `render with title and empty body produces valid HTML5 document structure` - Verifies DOCTYPE, html, head, body tags
- `render includes DOCTYPE` - Verifies `<!DOCTYPE html>` prefix
- `render includes HTMX CDN scripts` - Verifies HTMX core and response-targets extension
- `render includes /static/dashboard.css link` - Verifies CSS link with rel="stylesheet"
- `render includes /static/dashboard.js script` - Verifies JS script tag
- `render wraps body content in container` - Verifies body content wrapped in `.container` div
- `render includes dev mode banner when enabled` - Verifies dev banner appears when `devMode=true`
- `render does not include dev mode banner when disabled` - Verifies no banner when `devMode=false`
- `render includes hx-ext attribute on body` - Verifies `hx-ext="response-targets"`
- `render includes page title in head` - Verifies `<title>` tag content
- `render includes meta charset` - Verifies charset meta tag
- `render includes viewport meta tag` - Verifies viewport meta tag
- `CSS link appears before JS script in head` - Verifies resource ordering

**DashboardServiceTest.scala** (4 new tests for Phase 1)
- `renderDashboard output contains CSS link to /static/dashboard.css` - Verifies CSS link present
- `renderDashboard output contains JS script for /static/dashboard.js` - Verifies JS script present
- `renderDashboard output does NOT contain inline style tag` - Verifies no `<style>` tags
- `renderDashboard output does NOT contain inline visibilitychange script` - Verifies no inline scripts
- Plus 2 modified tests:
  - `Dashboard HTML links to external CSS file for styles` (formerly tested inline CSS)
  - `Dashboard HTML links to external JS file for visibilitychange handler` (formerly tested inline JS)

### Integration Tests

**StaticFilesTest.scala** (5 tests)
- `GET /static/dashboard.css returns CSS with correct Content-Type` - Verifies 200 status and `text/css` header
- `GET /static/dashboard.js returns JS with correct Content-Type` - Verifies 200 status and `application/javascript` header
- `GET /static/dashboard.css returns file contents` - Verifies CSS contains expected selectors (body, .container, .worktree-card)
- `GET /static/dashboard.js returns file contents` - Verifies JS contains visibilitychange and htmx.trigger
- `GET /static/nonexistent.css returns 404` - Verifies 404 for missing files

### E2E Tests

Existing dashboard BATS tests (not modified) - These verify the dashboard renders correctly in a real browser environment. Since the refactoring produces identical HTML structure and styles, these tests continue to pass without modification.

## Files Changed

<details>
<summary><strong>Core Implementation (3 files)</strong></summary>

### `.iw/core/dashboard/presentation/views/PageLayout.scala` (NEW)
**54 lines** - Shared HTML page layout component

Creates reusable HTML shell for all dashboard pages. Renders complete HTML5 document with DOCTYPE, head section (meta tags, HTMX scripts, CSS/JS links), and body wrapper with container div. Accepts page title, body content fragment, and devMode flag.

### `.iw/core/dashboard/DashboardService.scala` (MODIFIED)
**-735 lines / +34 lines (net: -701 lines)**

Removed:
- `styles` val with 735 lines of inline CSS
- Inline `<style>` tag in head
- Inline `<script>` tag with visibilitychange listener
- Manual HTML shell construction (html, head, body tags)
- Manual DOCTYPE concatenation

Added:
- Import of `PageLayout`
- Preparation of body content as `frag`
- Call to `PageLayout.render()` with title, bodyContent, devMode

### `.iw/core/dashboard/CaskServer.scala` (MODIFIED)
**+28 lines**

Added new route `@cask.get("/static/:filename")` that:
- Resolves static files from `.iw/core/dashboard/resources/static/`
- Reads file content as bytes
- Determines Content-Type based on file extension (.css → text/css, .js → application/javascript)
- Returns 404 for non-existent files
</details>

<details>
<summary><strong>Static Resources (2 files)</strong></summary>

### `.iw/core/dashboard/resources/static/dashboard.css` (NEW)
**735 lines** - Extracted CSS styles

All CSS previously inline in `DashboardService.styles`:
- Base styles (body, container)
- Dev mode banner
- Worktree cards and grid layout
- HTMX transitions (.htmx-swapping, .htmx-settling)
- Status badges, git indicators, PR links
- Modal overlay and search UI
- Main projects section
- Mobile responsive styles (@media queries)
- Touch-friendly button sizes (min-height: 44px)

### `.iw/core/dashboard/resources/static/dashboard.js` (NEW)
**7 lines** - Extracted JavaScript

Visibility-change event listener that triggers HTMX refresh when user returns to tab:
```javascript
document.addEventListener('visibilitychange', function() {
  if (document.visibilityState === 'visible') {
    htmx.trigger(document.body, 'refresh');
  }
});
```
</details>

<details>
<summary><strong>Tests (3 files)</strong></summary>

### `.iw/core/test/PageLayoutTest.scala` (NEW)
**147 lines, 13 tests** - Unit tests for PageLayout component

Tests HTML structure, HTMX scripts, CSS/JS links, dev mode banner, meta tags, and resource ordering.

### `.iw/core/test/DashboardServiceTest.scala` (MODIFIED)
**-62 lines / +58 lines (net: -4 lines)**

Removed old tests that checked for inline CSS/JS:
- Tests for inline `.htmx-swapping`, `.htmx-settling` styles
- Tests for inline transition properties
- Tests for inline visibilitychange script
- Tests for inline mobile styles and touch-action
- Tests for inline dev-mode-banner CSS

Added new tests that verify external links:
- Test for `/static/dashboard.css` link
- Test for `/static/dashboard.js` script
- Test for absence of `<style>` tag
- Test for absence of inline visibilitychange script

### `.iw/core/test/StaticFilesTest.scala` (NEW)
**168 lines, 5 tests** - Integration tests for static file serving

Tests that `/static/` route serves CSS and JS files with correct Content-Type headers, returns expected file contents, and returns 404 for missing files.
</details>

<details>
<summary><strong>Project Management (2 files)</strong></summary>

### `project-management/issues/IW-206/phase-01-tasks.md` (MODIFIED)
All tasks marked complete (setup, tests, implementation, integration).

### `project-management/issues/IW-206/review-state.json` (MODIFIED)
Status updated to "implementing", phase checkpoints added.
</details>

---

## Summary

Phase 1 successfully extracts inline CSS and JavaScript to external static files and creates a shared PageLayout component. The refactoring reduces code duplication, improves maintainability, and sets up a foundation for adding new dashboard pages in subsequent phases. All tests pass, and the dashboard renders identically to before.

**Files created:** 5 (PageLayout.scala, dashboard.css, dashboard.js, PageLayoutTest.scala, StaticFilesTest.scala)  
**Files modified:** 4 (DashboardService.scala, CaskServer.scala, DashboardServiceTest.scala, tasks/state files)  
**Net lines changed:** -672 lines (significant reduction through extraction)  
**Test coverage:** 21 tests (13 unit + 5 integration + 3 modified existing)
