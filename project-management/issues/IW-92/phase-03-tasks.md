# Phase 3 Tasks: Background refresh of issue data

**Issue:** IW-92
**Phase:** 3 of 5
**Story:** Story 2 - Background refresh of issue data
**Goal:** Background refresh keeps data fresh, cards update progressively

## Task Groups

### Setup

- [x] [impl] [x] [reviewed] Review existing `DashboardService.scala` render methods
- [x] [impl] [x] [reviewed] Review existing `WorktreeListView.scala` card rendering
- [x] [impl] [x] [reviewed] Review existing `CaskServer.scala` endpoint patterns

### Tests - Rate Limiting (RefreshThrottle)

- [x] [impl] [x] [reviewed] Test refresh blocked if < 30s since last refresh
- [x] [impl] [x] [reviewed] Test refresh allowed if >= 30s since last refresh
- [x] [impl] [x] [reviewed] Test each worktree tracked independently

### Implementation - Rate Limiting

- [x] [impl] [x] [reviewed] Create `RefreshThrottle.scala` with per-worktree tracking
- [x] [impl] [x] [reviewed] Add `shouldRefresh(issueId)` method returning Boolean
- [x] [impl] [x] [reviewed] Add `recordRefresh(issueId)` method to update timestamp

### Tests - Timestamp Formatting

- [x] [impl] [x] [reviewed] Test "Updated just now" for < 30s ago
- [x] [impl] [x] [reviewed] Test "Updated X seconds ago" for 30s-60s ago
- [x] [impl] [x] [reviewed] Test "Updated X minutes ago" for 1m-60m ago
- [x] [impl] [x] [reviewed] Test "Updated X hours ago" for > 60m ago

### Implementation - Timestamp Formatting

- [x] [impl] [x] [reviewed] Add `formatTimestamp(instant)` method to format relative time
- [x] [impl] [x] [reviewed] Add timestamp display to worktree card HTML

### Tests - Per-Card Endpoint

- [x] [impl] [x] [reviewed] Test `GET /worktrees/:issueId/card` returns HTML fragment
- [x] [impl] [x] [reviewed] Test card endpoint returns 404 for unknown worktree
- [x] [impl] [x] [reviewed] Test card endpoint includes HTMX attributes
- [x] [impl] [x] [reviewed] Test card endpoint returns cached data on API failure

### Implementation - Per-Card Endpoint

- [x] [impl] [x] [reviewed] Create `WorktreeCardService.scala` for per-card rendering
- [x] [impl] [x] [reviewed] Add `renderCard(issueId)` method returning card HTML
- [x] [impl] [x] [reviewed] Add `GET /worktrees/:issueId/card` endpoint to CaskServer
- [x] [impl] [x] [reviewed] Integrate with `RefreshThrottle` for rate limiting

### Tests - HTMX Integration

- [x] [impl] [x] [reviewed] Test card HTML includes `hx-get` attribute
- [x] [impl] [x] [reviewed] Test card HTML includes `hx-trigger="load, every 30s"` attribute
- [x] [impl] [x] [reviewed] Test card HTML includes `hx-swap="outerHTML"` attribute

### Implementation - HTMX Integration

- [x] [impl] [x] [reviewed] Update `WorktreeListView.renderCard()` to include HTMX attributes
- [x] [impl] [x] [reviewed] Ensure cards have unique IDs for HTMX targeting

### Tests - Refresh Endpoint

- [x] [impl] [x] [reviewed] Test `GET /api/worktrees/:issueId/refresh` returns JSON status
- [x] [impl] [x] [reviewed] Test refresh endpoint respects throttle (returns throttled status)
- [x] [impl] [x] [reviewed] Test refresh endpoint updates cache on success

### Implementation - Refresh Endpoint

- [x] [impl] [x] [reviewed] Add `GET /api/worktrees/:issueId/refresh` endpoint to CaskServer
- [x] [impl] [x] [reviewed] Return `{"status": "refreshed"}` or `{"status": "throttled"}`

### Integration

- [ ] [integration] Integration test: Dashboard triggers HTMX card refresh on load
- [ ] [integration] Integration test: Card updates without full page reload
- [ ] [integration] Integration test: Failed API returns cached data with old timestamp

## Acceptance Criteria Checklist

- [ ] Fresh issue data fetched in background after initial render
- [ ] Each card updates independently (no full page refresh)
- [ ] Failed API calls don't block other cards from updating
- [ ] User sees timestamp of last successful refresh per card
- [ ] 30s throttle prevents API hammering
- [ ] All existing Phase 1+2 functionality preserved

## Notes

- Build on Phase 1's `getCachedOnly()` and Phase 2's error handling patterns
- HTMX handles async complexity - keep server code simple
- Rate limiting is critical - don't hammer external APIs
- Key insight: silent degradation is better than visible errors

**Phase Status:** Complete
