# Phase 5: HTMX auto-refresh for worktree detail content

## Goals

Add automatic polling-based refresh to the worktree detail page so that displayed data (issue status, git status, workflow progress, PR link) stays current without requiring a manual page reload. This follows the same HTMX polling pattern already used by worktree cards on the dashboard.

1. Extract a `renderContent` method from `WorktreeDetailView` that returns only the content section (no breadcrumb, no page shell)
2. Add HTMX polling attributes to the content wrapper div in the full `render` method
3. Create a dedicated fragment endpoint `GET /worktrees/:issueId/detail-content` that returns just the content HTML
4. Add test coverage at all levels (unit, integration, E2E)

## Current State (after Phases 1-4)

### WorktreeDetailView (`WorktreeDetailView.scala`)

The `render` method produces a full detail page containing:
- Breadcrumb navigation (via `renderBreadcrumb`)
- Content section in a div with `cls := "worktree-detail-content"` (via `renderFull` or `renderSkeleton`)
- Data sections: issue title/status/assignee, git status, workflow progress, PR link, Zed editor link, review artifacts

Currently there is no separation between the full page render and just the content area. The content div has no HTMX attributes — it's a static page that requires manual refresh.

### CaskServer (`CaskServer.scala`)

Existing endpoints:
- `GET /worktrees/:issueId` — returns full HTML page via `PageLayout.render()`
- `GET /worktrees/:issueId/card` — returns card HTML fragment (used for card auto-refresh)

No endpoint exists for returning just the detail page content section.

### Existing HTMX polling pattern (cards)

Cards use this pattern:
```html
<div hx-get="/worktrees/:issueId/card"
     hx-trigger="every 30s, refresh from:body"
     hx-swap="outerHTML transition:true">
  ...card content...
</div>
```

The card endpoint fetches fresh data (triggers cache update, not just reads cache) and returns the card HTML fragment. There is also a visibility-based refresh via `dashboard.js` that fires a `refresh` event on `body` when the browser tab becomes visible.

### HTMX setup

HTMX v1.9.10 loaded from CDN, with `hx-ext="response-targets"` on body. The `ProjectDetailsView` uses list-level polling with OOB swaps.

## Gap Analysis

### What exists
1. Full detail page rendering — **implemented** (Phase 1)
2. Breadcrumb navigation — **implemented** (Phase 2)
3. Unknown worktree handling — **implemented** (Phase 3)
4. Artifact back-navigation — **implemented** (Phase 4)
5. Card auto-refresh via HTMX polling — **implemented** (existing dashboard feature)

### What is missing
1. **No content-only render method**: `WorktreeDetailView` can only render the full page (breadcrumb + content), not just the content section
2. **No HTMX attributes on content div**: The detail page content div is static, has no `hx-get`, `hx-trigger`, or `hx-swap` attributes
3. **No fragment endpoint**: No `GET /worktrees/:issueId/detail-content` route exists in `CaskServer`
4. **No tests for auto-refresh behavior**: No tests verify HTMX attributes or fragment endpoint responses

## Scope

### Must do
- Add `renderContent` method to `WorktreeDetailView` that returns just the content section (everything inside `worktree-detail-content` div, without breadcrumb or page wrapper)
- Add HTMX attributes to the content wrapper div in `render`: `hx-get`, `hx-trigger="every 30s, refresh from:body"`, `hx-swap="innerHTML"`
- Add `GET /worktrees/:issueId/detail-content` endpoint to `CaskServer` that returns the content fragment
- Fragment endpoint must fetch fresh data (same refresh pattern as card endpoint)
- Fragment endpoint must return 404 with empty body for unknown worktrees
- Unit tests for HTMX attributes in rendered output
- Unit tests for `renderContent` producing content without breadcrumb
- Integration test for fragment endpoint
- E2E test for fragment endpoint

### Out of scope
- Changing the existing card refresh behavior
- Adding SSE or WebSocket (polling is the established pattern)
- Adding breadcrumb to the fragment response
- Wrapping the fragment in PageLayout
- Scroll position preservation JS (HTMX `innerHTML` swap preserves scroll by default)
- Visual transition effects (can be added later if needed)

## Dependencies

- Phase 1 (complete): `WorktreeDetailView.render`, `CaskServer` worktree detail route, `WorktreeCardService`
- Phase 3 (complete): Unknown worktree handling (404 pattern to reuse for fragment endpoint)
- Existing card refresh pattern in `CaskServer` (fetch-and-update logic to replicate)

## Approach

### 1. Extract `renderContent` from `WorktreeDetailView`

Refactor `WorktreeDetailView` so that the content rendering (currently done by `renderFull`/`renderSkeleton`) is available as a standalone method:

```scala
def renderContent(worktree: WorktreeState, ...): Frag =
  // Returns the inner content of the worktree-detail-content div
  // Issue info, git status, workflow, PR link, artifacts, etc.
```

The existing `render` method calls `renderContent` internally and wraps the result with:
- Breadcrumb navigation
- Content wrapper div with HTMX attributes

### 2. Add HTMX attributes to content wrapper in `render`

The content wrapper div in the `render` method gets polling attributes:

```scala
div(
  cls := "worktree-detail-content",
  attr("hx-get") := s"/worktrees/$issueId/detail-content",
  attr("hx-trigger") := "every 30s, refresh from:body",
  attr("hx-swap") := "innerHTML",
  renderContent(worktree, ...)
)
```

Using `innerHTML` swap means HTMX replaces the content inside the div but keeps the div itself (and its HTMX attributes), so polling continues working after each refresh.

### 3. Add fragment endpoint to `CaskServer`

```
GET /worktrees/:issueId/detail-content
```

This endpoint:
- Reads current worktree state
- Triggers fresh data fetch (same pattern as the card endpoint — not just cache read)
- Calls `WorktreeDetailView.renderContent(...)` to produce the HTML fragment
- Returns the fragment with `text/html` content type
- Returns 404 with empty body if worktree is not found

### 4. Write tests (TDD)

Start with failing tests, then implement to make them pass.

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/dashboard/presentation/views/WorktreeDetailView.scala` | Extract `renderContent` method; add HTMX `hx-get`, `hx-trigger`, `hx-swap` attributes to content wrapper div in `render` |
| `.iw/core/dashboard/CaskServer.scala` | Add `GET /worktrees/:issueId/detail-content` endpoint returning content fragment |
| `.iw/core/test/WorktreeDetailViewTest.scala` | Tests for HTMX attributes in `render` output; tests for `renderContent` returning content without breadcrumb |
| `.iw/core/test/CaskServerTest.scala` | Integration test for `/worktrees/:issueId/detail-content` endpoint (returns fragment, not full page; returns 404 for unknown worktree) |
| `.iw/test/dashboard-dev-mode.bats` | E2E test: fragment endpoint returns HTML fragment (no `<html>`, no `<head>`), contains expected data sections |

## Testing Strategy

### Unit tests (`WorktreeDetailViewTest.scala`)
- Verify `render` output contains `hx-get="/worktrees/:issueId/detail-content"` attribute
- Verify `render` output contains `hx-trigger="every 30s, refresh from:body"` attribute
- Verify `render` output contains `hx-swap="innerHTML"` attribute
- Verify `renderContent` output contains data sections (issue info, git status, workflow progress)
- Verify `renderContent` output does NOT contain breadcrumb navigation
- Verify `renderContent` output does NOT contain `<html>` or page shell elements

### Integration tests (`CaskServerTest.scala`)
- `GET /worktrees/:issueId/detail-content` returns 200 with HTML fragment for known worktree
- Response contains expected data sections (issue title, status)
- Response does NOT contain `<html>`, `<head>`, or breadcrumb elements
- `GET /worktrees/:issueId/detail-content` returns 404 for unknown worktree

### E2E tests (`dashboard-dev-mode.bats`)
- Hit `/worktrees/:issueId/detail-content` and verify response is an HTML fragment (no `<html>`, no `<head>`)
- Verify fragment contains expected content sections
- Verify detail page (`/worktrees/:issueId`) contains HTMX polling attributes

## Acceptance Criteria

From Story 3:
- [ ] Detail page content refreshes automatically every 30 seconds via HTMX polling
- [ ] Fresh data from API is fetched on each poll (not just cached data)
- [ ] Page does not flicker or lose scroll position during refresh (HTMX `innerHTML` swap on content div only)
- [ ] Only the content area is replaced — breadcrumb and page shell remain stable
- [ ] Visibility-based refresh works (tab becomes visible → `refresh` event → content updates)
- [ ] Fragment endpoint returns 404 for unknown worktrees (consistent with Phase 3 handling)
