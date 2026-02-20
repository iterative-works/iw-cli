# Phase 01 Tasks: Extract CSS/JS to static resources and create shared layout

## Setup

- [x] [setup] Create directory structure `.iw/core/dashboard/resources/static/`
- [x] [setup] Extract CSS from `DashboardService.styles` (lines 399-1134) to `dashboard.css`
- [x] [setup] Extract visibility-change JS (lines 77-83) to `dashboard.js`
- [x] [setup] Verify CSS file is valid and complete (~735 lines)
- [x] [setup] Verify JS file is valid and contains event listener

## Tests

- [x] [test] Write unit test for `PageLayout.render` with title and empty body
- [x] [test] Write unit test verifying `PageLayout.render` includes DOCTYPE
- [x] [test] Write unit test verifying `PageLayout.render` includes HTMX CDN scripts
- [x] [test] Write unit test verifying `PageLayout.render` includes `/static/dashboard.css` link
- [x] [test] Write unit test verifying `PageLayout.render` includes `/static/dashboard.js` script
- [x] [test] Write unit test verifying `PageLayout.render` wraps body content in container
- [x] [test] Write unit test verifying `PageLayout.render` includes dev mode banner when enabled
- [x] [test] Write unit test verifying `PageLayout.render` includes hx-ext attribute on body
- [x] [test] Write integration test for static CSS route returns correct Content-Type
- [x] [test] Write integration test for static JS route returns correct Content-Type
- [x] [test] Write integration test verifying static CSS route returns file contents
- [x] [test] Write integration test verifying static JS route returns file contents
- [x] [test] Write integration test verifying 404 for non-existent static files
- [x] [test] Write unit test verifying `DashboardService.renderDashboard` output contains CSS link
- [x] [test] Write unit test verifying `DashboardService.renderDashboard` output contains JS script
- [x] [test] Write unit test verifying `DashboardService.renderDashboard` does NOT contain inline style tag
- [x] [test] Write unit test verifying `DashboardService.renderDashboard` does NOT contain inline script with visibilitychange

## Implementation

- [x] [impl] Create `PageLayout.scala` in `.iw/core/dashboard/presentation/views/`
- [x] [impl] Implement `PageLayout.render` method signature (title: String, bodyContent: Frag, devMode: Boolean)
- [x] [impl] Implement DOCTYPE and html/head structure in `PageLayout.render`
- [x] [impl] Implement meta charset and title tags in `PageLayout.render`
- [x] [impl] Implement HTMX CDN script tags in `PageLayout.render` (match existing URLs)
- [x] [impl] Implement CSS link tag for `/static/dashboard.css` in `PageLayout.render`
- [x] [impl] Implement JS script tag for `/static/dashboard.js` in `PageLayout.render`
- [x] [impl] Implement body with hx-ext attribute in `PageLayout.render`
- [x] [impl] Implement container div and dev mode banner in `PageLayout.render`
- [x] [impl] Add static file serving route to `CaskServer` (`@cask.get("/static/:filename")`)
- [x] [impl] Implement Content-Type detection in static route (text/css for .css, application/javascript for .js)
- [x] [impl] Implement file reading and response in static route
- [x] [impl] Add 404 handling for missing static files
- [x] [impl] Refactor `DashboardService.renderDashboard` to use `PageLayout.render`
- [x] [impl] Remove `styles` val from `DashboardService`
- [x] [impl] Remove inline script from `DashboardService.renderDashboard` head section
- [x] [impl] Remove manual DOCTYPE concatenation from `DashboardService.renderDashboard`
- [x] [impl] Extract dashboard body content (header, projects, worktree list, modal) as parameter to `PageLayout.render`

## Integration

- [x] [integration] Run unit tests and verify all pass
- [x] [integration] Run `./iw server` and verify dashboard loads in browser
- [x] [integration] Verify CSS is applied correctly (visual check)
- [x] [integration] Verify visibility-change JS works (test tab switching triggers refresh)
- [x] [integration] Verify dev mode banner appears when devMode=true
- [x] [integration] Verify SSH host form still works
- [x] [integration] Verify worktree cards render identically to before refactoring
- [x] [integration] Verify HTMX polling still works for cards
- [x] [integration] Verify create worktree modal still opens
- [x] [integration] Run existing E2E tests and verify they pass without modification
- [x] [integration] Check browser network tab shows `/static/dashboard.css` loads with 200 status
- [x] [integration] Check browser network tab shows `/static/dashboard.js` loads with 200 status
- [x] [integration] Verify no console errors in browser

Note: Integration tests verify static file serving works with correct Content-Type headers.
All unit tests pass, including tests for PageLayout, static file serving, and DashboardService refactoring.
The refactoring produces identical HTML output (same CSS classes, same structure) so existing functionality is preserved.
