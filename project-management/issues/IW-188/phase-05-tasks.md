# Phase 5 Tasks: HTMX auto-refresh for worktree detail content

## Setup

- [ ] [setup] Read `WorktreeDetailView.scala` and understand the `render` method (line 26-45): note that `renderBreadcrumb` is called on line 39, and content is produced by either `renderSkeleton` (line 42) or `renderFull` (line 44) — these are what `renderContent` needs to wrap
- [ ] [setup] Read `CaskServer.scala` worktree detail endpoint (line 143-194): note that it reads from caches only (`fetchIssueForWorktreeCachedOnly` on line 164, `fetchPRForWorktreeCachedOnly` on line 167) — the new fragment endpoint must trigger fresh fetches instead
- [ ] [setup] Read `CaskServer.scala` card endpoint (line 231-309): understand the fresh-fetch pattern using `buildFetchFunction`, `buildUrlBuilder`, `WorktreeCardService.renderCard`, and the cache update block (lines 293-304) — the fragment endpoint will replicate this data-fetching pattern
- [ ] [setup] Read `WorktreeDetailViewTest.scala` (lines 1-301) and `CaskServerTest.scala` to understand existing test patterns and fixtures

## Tests

### Unit Tests — WorktreeDetailView (TDD red phase)

- [ ] [test] Add test in `WorktreeDetailViewTest.scala`: "render output contains hx-get attribute for detail content polling" — assert `renderDefault()` output contains `hx-get="/worktrees/IW-188/detail-content"`
- [ ] [test] Add test: "render output contains hx-trigger attribute with 30s polling interval" — assert output contains `hx-trigger="every 30s, refresh from:body"`
- [ ] [test] Add test: "render output contains hx-swap innerHTML attribute" — assert output contains `hx-swap="innerHTML"`
- [ ] [test] Add test: "renderContent returns content without breadcrumb" — call `WorktreeDetailView.renderContent(...)` with `sampleWorktree` and issue data, assert result does NOT contain `breadcrumb` class and does NOT contain `<nav`
- [ ] [test] Add test: "renderContent returns content with data sections" — call `renderContent` with full data (issue data, git status, progress, PR), assert result contains `worktree-detail-content`, issue title, `git-status`, `phase-info`, `pr-link`
- [ ] [test] Add test: "renderContent returns skeleton when issue data is None" — call `renderContent` with `issueData = None`, assert result contains `skeleton` class and `Loading`
- [ ] [test] Run `WorktreeDetailViewTest` and confirm the new tests fail (TDD red phase — `renderContent` does not exist yet, HTMX attributes not present)

### Integration Tests — CaskServer (TDD red phase)

- [ ] [test] Add test in `CaskServerTest.scala`: "GET /worktrees/:issueId/detail-content returns 200 with HTML fragment for registered worktree" — register a worktree, hit `/worktrees/:issueId/detail-content`, assert 200 status and `text/html` content type
- [ ] [test] Add test: "GET /worktrees/:issueId/detail-content returns fragment without html or head tags" — assert response body does NOT contain `<html` or `<head`
- [ ] [test] Add test: "GET /worktrees/:issueId/detail-content returns fragment without breadcrumb" — assert response body does NOT contain `breadcrumb`
- [ ] [test] Add test: "GET /worktrees/:issueId/detail-content returns 404 for unknown worktree" — hit endpoint for non-existent issue ID, assert 404 status
- [ ] [test] Run `CaskServerTest` and confirm the new tests fail (TDD red phase — endpoint does not exist yet)

## Implementation

### Extract `renderContent` method (WorktreeDetailView.scala)

- [ ] [impl] Add public `renderContent` method to `WorktreeDetailView` (after `render`, before `renderNotFound` around line 46) — takes the same parameters as `render`, returns `Frag` containing just the content section (dispatches to `renderFull` or `renderSkeleton` based on `issueData`)
- [ ] [impl] Refactor `render` method (line 26-45) to call `renderContent` internally — the `render` method wraps `renderContent` result in the outer `worktree-detail` div with breadcrumb, and adds HTMX attributes (`hx-get`, `hx-trigger`, `hx-swap`) to the content wrapper div
- [ ] [impl] Run `WorktreeDetailViewTest` and confirm all tests pass (TDD green phase)

### Add fragment endpoint (CaskServer.scala)

- [ ] [impl] Add `@cask.get("/worktrees/:issueId/detail-content")` endpoint in `CaskServer.scala` after the existing `/worktrees/:issueId/card` endpoint (after line 309) — for unknown worktrees, return 404 with empty body; for known worktrees, fetch fresh data using the same pattern as the card endpoint (lines 249-304: `buildFetchFunction`, `buildUrlBuilder`, PR fetch, `WorktreeCardService.renderCard` for cache updates) then call `WorktreeDetailView.renderContent(...)` and return the HTML fragment
- [ ] [impl] The fragment endpoint must update caches after fetching (same pattern as card endpoint lines 293-304) so that fresh data is persisted
- [ ] [impl] Run `CaskServerTest` and confirm all tests pass (TDD green phase)
- [ ] [impl] Run full unit test suite (`./iw test unit`) to verify no regressions

## Integration

- [ ] [integration] Add E2E test in `dashboard-dev-mode.bats`: "GET /worktrees/:issueId/detail-content returns HTML fragment" — start dev server, register a worktree via API, hit `/worktrees/:issueId/detail-content`, assert 200 status, assert response does NOT contain `<html` or `<head`, assert response contains `worktree-detail-content`
- [ ] [integration] Add E2E test: "GET /worktrees/:issueId contains HTMX polling attributes" — start dev server, register a worktree, hit `/worktrees/:issueId`, assert response contains `hx-get=`, `hx-trigger=`, `hx-swap=`
- [ ] [integration] Add E2E test: "GET /worktrees/NONEXISTENT-999/detail-content returns 404" — hit the detail-content endpoint for a non-existent worktree, assert 404 status
- [ ] [integration] Run full test suite (`./iw test`) to confirm all tests pass
