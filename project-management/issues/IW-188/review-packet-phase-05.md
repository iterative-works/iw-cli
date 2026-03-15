---
generated_from: 827f452b99466d3ff774d4d1238714110294bce8
generated_at: 2026-03-15T11:59:10Z
branch: IW-188-phase-05
issue_id: IW-188
phase: 5
files_analyzed:
  - .iw/core/dashboard/CaskServer.scala
  - .iw/core/dashboard/presentation/views/WorktreeDetailView.scala
  - .iw/core/test/CaskServerTest.scala
  - .iw/core/test/WorktreeDetailViewTest.scala
  - .iw/test/dashboard-dev-mode.bats
---

# Review Packet: Phase 5 - HTMX Auto-Refresh for Worktree Detail Content

## Goals

This phase adds automatic polling-based refresh to the worktree detail page so that data (issue status, git status, workflow progress, PR state) stays current without requiring a manual page reload. It follows the same HTMX polling pattern already used by worktree cards on the dashboard.

Key objectives:

- Extract a `renderContent` method from `WorktreeDetailView` that returns only the content section (no breadcrumb, no page shell), suitable for use as an HTMX fragment response
- Add HTMX polling attributes (`hx-get`, `hx-trigger`, `hx-swap`) to the content wrapper div in the full `render` method so the page polls automatically every 30 seconds
- Add a `GET /worktrees/:issueId/detail-content` endpoint to `CaskServer` that returns the content fragment with fresh data, mirroring the pattern of the existing card endpoint
- Provide complete test coverage at unit, integration, and E2E levels

## Scenarios

- [ ] Detail page content refreshes automatically every 30 seconds via HTMX polling
- [ ] Fresh data from API is fetched on each poll (not just cached data), consistent with card refresh behavior
- [ ] Only the content area is replaced — breadcrumb and page shell remain stable across refreshes
- [ ] Page does not flicker or lose scroll position during refresh (`innerHTML` swap on the content div only)
- [ ] Visibility-based refresh works: when a browser tab becomes visible, a `refresh` event fires and the content updates
- [ ] Fragment endpoint returns 200 with an HTML fragment (no `<html>` or `<head>` tags) for a registered worktree
- [ ] Fragment endpoint returns 404 with empty body for an unknown worktree

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/dashboard/CaskServer.scala` | `worktreeDetailContent()` at `@cask.get("/worktrees/:issueId/detail-content")` | New endpoint — the HTTP entry point for the auto-refresh fragment |
| `.iw/core/dashboard/presentation/views/WorktreeDetailView.scala` | `renderContent()` | New method that produces the content-only fragment returned by the endpoint |
| `.iw/core/dashboard/presentation/views/WorktreeDetailView.scala` | `render()` | Modified to add HTMX attributes to the content wrapper div |

## Diagrams

### Request Flow: Initial Page Load vs. Auto-Refresh Poll

```
Browser                         CaskServer
  |                                |
  |  GET /worktrees/:issueId       |
  |------------------------------> |
  |                        WorktreeDetailView.render()
  |                          ├── renderBreadcrumb()      (static, not refreshed)
  |                          └── div.worktree-detail-content
  |                               hx-get=".../detail-content"
  |                               hx-trigger="every 30s, refresh from:body"
  |                               hx-swap="innerHTML"
  |                               renderContent()        (initial data)
  |  <-- Full HTML page (PageLayout) --|
  |                                |
  |  [30 seconds later / tab visible] |
  |                                |
  |  GET /worktrees/:issueId/detail-content   |
  |------------------------------> |
  |                        WorktreeCardService.renderCard()  (triggers fresh fetch + cache update)
  |                        WorktreeDetailView.renderContent() (reads updated cache)
  |  <-- HTML fragment only -------|
  |  (replaces innerHTML of content div, breadcrumb untouched)
```

### Data Flow: Fragment Endpoint

```
worktreeDetailContent()
  ├── state.worktrees.get(issueId)
  │     └── None → 404 empty response
  └── Some(worktree)
        ├── buildFetchFunction()       (tracker-type-aware API client)
        ├── buildUrlBuilder()
        ├── PullRequestCacheService.fetchPR()
        ├── WorktreeCardService.renderCard()   ← triggers fresh fetch + throttle
        │     └── updates issueCache, progressCache, prCache, reviewStateCache
        ├── stateService.getState()    ← reads updated state
        └── WorktreeDetailView.renderContent()
              └── renderFull() or renderSkeleton()
```

### Component Relationships

```
CaskServer
  ├── GET /worktrees/:issueId           → WorktreeDetailView.render()
  │                                          └── renderContent() [initial]
  └── GET /worktrees/:issueId/detail-content → WorktreeDetailView.renderContent() [fragment]
                                                    ↑
                                               WorktreeCardService.renderCard()
                                               (shared fetch + cache pattern with card endpoint)

WorktreeDetailView
  ├── render()           full page body (breadcrumb + content div with HTMX attrs)
  ├── renderContent()    content fragment only (no breadcrumb, no shell)
  ├── renderNotFound()   404 page (unchanged)
  └── renderBreadcrumb() (private, unchanged)
```

## Test Summary

### Unit Tests (`WorktreeDetailViewTest.scala`)

Phase 5 adds 6 new unit tests:

| Test | Type | Verifies |
|------|------|----------|
| `render output contains hx-get attribute for detail content polling` | Unit | `hx-get="/worktrees/IW-188/detail-content"` is present in `render` output |
| `render output contains hx-trigger attribute with 30s polling interval` | Unit | `hx-trigger="every 30s, refresh from:body"` is present |
| `render output contains hx-swap innerHTML attribute` | Unit | `hx-swap="innerHTML"` is present |
| `renderContent returns content without breadcrumb` | Unit | No `breadcrumb` or `<nav` in fragment output |
| `renderContent returns content with data sections` | Unit | Issue title, `git-status`, `phase-info`, `pr-link` present in fragment |
| `renderContent returns skeleton when issue data is None` | Unit | `skeleton` class and `Loading` text in fragment when no issue data |

Pre-existing unit tests (Phases 1–4) also continue to pass, covering: issue title, status badge, assignee, git status, PR link, workflow progress, review artifacts, breadcrumb, skeleton state, cache/stale indicators, not-found page rendering, and artifact link patterns.

### Integration Tests (`CaskServerTest.scala`)

Phase 5 adds 4 new integration tests:

| Test | Verifies |
|------|----------|
| `GET /worktrees/:issueId/detail-content returns 200 with HTML fragment for registered worktree` | Status 200 and `text/html` content-type |
| `GET /worktrees/:issueId/detail-content returns fragment without html or head tags` | No `<html` or `<head` in response body |
| `GET /worktrees/:issueId/detail-content returns fragment without breadcrumb` | No `breadcrumb` class in response body |
| `GET /worktrees/:issueId/detail-content returns 404 for unknown worktree` | Status 404 for unregistered issue ID |

### E2E Tests (`dashboard-dev-mode.bats`)

Phase 5 adds 3 new BATS tests:

| Test | Verifies |
|------|----------|
| `GET /worktrees/:issueId/detail-content returns HTML fragment` | Registers worktree, hits fragment endpoint, asserts 200, no `<html>`/`<head>`, fragment contains content |
| `GET /worktrees/:issueId contains HTMX polling attributes` | Full page contains `hx-get`, `hx-trigger`, `hx-swap`, and `detail-content` in response body |
| `GET /worktrees/NONEXISTENT-999/detail-content returns 404` | Fragment endpoint returns 404 for unknown issue ID |

Pre-existing E2E tests (Phases 3–4) also continue to pass, covering: not-found page, breadcrumb navigation, and artifact back-navigation.

## Files Changed

| File | Change Type | Description |
|------|-------------|-------------|
| `.iw/core/dashboard/presentation/views/WorktreeDetailView.scala` | Modified | Extracted `renderContent()` as a public method; added `hx-get`, `hx-trigger`, `hx-swap` attributes to the content wrapper div in `render()` |
| `.iw/core/dashboard/CaskServer.scala` | Modified | Added `GET /worktrees/:issueId/detail-content` endpoint (`worktreeDetailContent`) that fetches fresh data via `WorktreeCardService.renderCard` and returns the content fragment |
| `.iw/core/test/WorktreeDetailViewTest.scala` | Modified | Added 6 unit tests for HTMX attributes and `renderContent` behavior |
| `.iw/core/test/CaskServerTest.scala` | Modified | Added 4 integration tests for the fragment endpoint |
| `.iw/test/dashboard-dev-mode.bats` | Modified | Added 3 E2E tests for fragment endpoint and HTMX attributes |

<details>
<summary>Key implementation details</summary>

### WorktreeDetailView — render() method

The content wrapper div now carries HTMX polling attributes:

```scala
div(
  cls := "worktree-detail-content",
  attr("hx-get") := s"/worktrees/${worktree.issueId}/detail-content",
  attr("hx-trigger") := "every 30s, refresh from:body",
  attr("hx-swap") := "innerHTML",
  renderContent(worktree, issueData, progress, gitStatus, prData, reviewStateResult, now, sshHost)
)
```

Using `innerHTML` swap means HTMX replaces the content inside the div but keeps the div element itself (and its HTMX attributes), so polling continues after each refresh without re-attaching attributes.

### CaskServer — worktreeDetailContent() endpoint

The fragment endpoint follows the same pattern as the existing card endpoint (`worktreeCard`):

1. Look up the worktree; return 404 with empty body if not found
2. Build the tracker-specific fetch function and PR fetch function
3. Call `WorktreeCardService.renderCard` to trigger a fresh API fetch (respecting throttling) and update all caches
4. Read the updated state and call `WorktreeDetailView.renderContent` to produce the fragment
5. Return the fragment with `text/html` content-type

The key difference from the card endpoint is the final render call: instead of returning `result.html` (a card HTML fragment), it reads the updated caches and calls `renderContent`, producing the full data section of the detail page.

### Visibility-based refresh

The `hx-trigger="every 30s, refresh from:body"` attribute hooks into the existing `dashboard.js` mechanism, which fires a `refresh` event on `document.body` when the browser tab becomes visible (via the Page Visibility API). This means the detail page content refreshes immediately when a user returns to the tab, in addition to the regular 30-second interval.

</details>
