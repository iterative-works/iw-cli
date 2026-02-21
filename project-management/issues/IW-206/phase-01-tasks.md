# Phase 01 Tasks: Extract CSS/JS to static resources and create shared layout

## Setup

- [ ] [setup] Create directory structure `.iw/core/dashboard/resources/static/`
- [ ] [setup] Extract CSS from `DashboardService.styles` (lines 399-1134) to `dashboard.css`
- [ ] [setup] Extract visibility-change JS (lines 77-83) to `dashboard.js`
- [ ] [setup] Verify CSS file is valid and complete (~735 lines)
- [ ] [setup] Verify JS file is valid and contains event listener

## Tests

- [ ] [test] Write unit test for `PageLayout.render` with title and empty body
- [ ] [test] Write unit test verifying `PageLayout.render` includes DOCTYPE
- [ ] [test] Write unit test verifying `PageLayout.render` includes HTMX CDN scripts
- [ ] [test] Write unit test verifying `PageLayout.render` includes `/static/dashboard.css` link
- [ ] [test] Write unit test verifying `PageLayout.render` includes `/static/dashboard.js` script
- [ ] [test] Write unit test verifying `PageLayout.render` wraps body content in container
- [ ] [test] Write unit test verifying `PageLayout.render` includes dev mode banner when enabled
- [ ] [test] Write unit test verifying `PageLayout.render` includes hx-ext attribute on body
- [ ] [test] Write integration test for static CSS route returns correct Content-Type
- [ ] [test] Write integration test for static JS route returns correct Content-Type
- [ ] [test] Write integration test verifying static CSS route returns file contents
- [ ] [test] Write integration test verifying static JS route returns file contents
- [ ] [test] Write integration test verifying 404 for non-existent static files
- [ ] [test] Write unit test verifying `DashboardService.renderDashboard` output contains CSS link
- [ ] [test] Write unit test verifying `DashboardService.renderDashboard` output contains JS script
- [ ] [test] Write unit test verifying `DashboardService.renderDashboard` does NOT contain inline style tag
- [ ] [test] Write unit test verifying `DashboardService.renderDashboard` does NOT contain inline script with visibilitychange

## Implementation

- [ ] [impl] Create `PageLayout.scala` in `.iw/core/dashboard/presentation/views/`
- [ ] [impl] Implement `PageLayout.render` method signature (title: String, bodyContent: Frag, devMode: Boolean)
- [ ] [impl] Implement DOCTYPE and html/head structure in `PageLayout.render`
- [ ] [impl] Implement meta charset and title tags in `PageLayout.render`
- [ ] [impl] Implement HTMX CDN script tags in `PageLayout.render` (match existing URLs)
- [ ] [impl] Implement CSS link tag for `/static/dashboard.css` in `PageLayout.render`
- [ ] [impl] Implement JS script tag for `/static/dashboard.js` in `PageLayout.render`
- [ ] [impl] Implement body with hx-ext attribute in `PageLayout.render`
- [ ] [impl] Implement container div and dev mode banner in `PageLayout.render`
- [ ] [impl] Add static file serving route to `CaskServer` (`@cask.get("/static/:filename")`)
- [ ] [impl] Implement Content-Type detection in static route (text/css for .css, application/javascript for .js)
- [ ] [impl] Implement file reading and response in static route
- [ ] [impl] Add 404 handling for missing static files
- [ ] [impl] Refactor `DashboardService.renderDashboard` to use `PageLayout.render`
- [ ] [impl] Remove `styles` val from `DashboardService`
- [ ] [impl] Remove inline script from `DashboardService.renderDashboard` head section
- [ ] [impl] Remove manual DOCTYPE concatenation from `DashboardService.renderDashboard`
- [ ] [impl] Extract dashboard body content (header, projects, worktree list, modal) as parameter to `PageLayout.render`

## Integration

- [ ] [integration] Run unit tests and verify all pass
- [ ] [integration] Run `./iw server` and verify dashboard loads in browser
- [ ] [integration] Verify CSS is applied correctly (visual check)
- [ ] [integration] Verify visibility-change JS works (test tab switching triggers refresh)
- [ ] [integration] Verify dev mode banner appears when devMode=true
- [ ] [integration] Verify SSH host form still works
- [ ] [integration] Verify worktree cards render identically to before refactoring
- [ ] [integration] Verify HTMX polling still works for cards
- [ ] [integration] Verify create worktree modal still opens
- [ ] [integration] Run existing E2E tests and verify they pass without modification
- [ ] [integration] Check browser network tab shows `/static/dashboard.css` loads with 200 status
- [ ] [integration] Check browser network tab shows `/static/dashboard.js` loads with 200 status
- [ ] [integration] Verify no console errors in browser
