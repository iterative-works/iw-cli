# Phase 3 Tasks: Background refresh of issue data

**Issue:** IW-92
**Phase:** 3 of 5
**Story:** Story 2 - Background refresh of issue data
**Goal:** Background refresh keeps data fresh, cards update progressively

## Task Groups

### Setup

- [ ] [impl] [ ] [reviewed] Review existing `DashboardService.scala` render methods
- [ ] [impl] [ ] [reviewed] Review existing `WorktreeListView.scala` card rendering
- [ ] [impl] [ ] [reviewed] Review existing `CaskServer.scala` endpoint patterns

### Tests - Rate Limiting (RefreshThrottle)

- [ ] [impl] [ ] [reviewed] Test refresh blocked if < 30s since last refresh
- [ ] [impl] [ ] [reviewed] Test refresh allowed if >= 30s since last refresh
- [ ] [impl] [ ] [reviewed] Test each worktree tracked independently

### Implementation - Rate Limiting

- [ ] [impl] [ ] [reviewed] Create `RefreshThrottle.scala` with per-worktree tracking
- [ ] [impl] [ ] [reviewed] Add `shouldRefresh(issueId)` method returning Boolean
- [ ] [impl] [ ] [reviewed] Add `recordRefresh(issueId)` method to update timestamp

### Tests - Timestamp Formatting

- [ ] [impl] [ ] [reviewed] Test "Updated just now" for < 30s ago
- [ ] [impl] [ ] [reviewed] Test "Updated X seconds ago" for 30s-60s ago
- [ ] [impl] [ ] [reviewed] Test "Updated X minutes ago" for 1m-60m ago
- [ ] [impl] [ ] [reviewed] Test "Updated X hours ago" for > 60m ago

### Implementation - Timestamp Formatting

- [ ] [impl] [ ] [reviewed] Add `formatTimestamp(instant)` method to format relative time
- [ ] [impl] [ ] [reviewed] Add timestamp display to worktree card HTML

### Tests - Per-Card Endpoint

- [ ] [impl] [ ] [reviewed] Test `GET /worktrees/:issueId/card` returns HTML fragment
- [ ] [impl] [ ] [reviewed] Test card endpoint returns 404 for unknown worktree
- [ ] [impl] [ ] [reviewed] Test card endpoint includes HTMX attributes
- [ ] [impl] [ ] [reviewed] Test card endpoint returns cached data on API failure

### Implementation - Per-Card Endpoint

- [ ] [impl] [ ] [reviewed] Create `WorktreeCardService.scala` for per-card rendering
- [ ] [impl] [ ] [reviewed] Add `renderCard(issueId)` method returning card HTML
- [ ] [impl] [ ] [reviewed] Add `GET /worktrees/:issueId/card` endpoint to CaskServer
- [ ] [impl] [ ] [reviewed] Integrate with `RefreshThrottle` for rate limiting

### Tests - HTMX Integration

- [ ] [impl] [ ] [reviewed] Test card HTML includes `hx-get` attribute
- [ ] [impl] [ ] [reviewed] Test card HTML includes `hx-trigger="load, every 30s"` attribute
- [ ] [impl] [ ] [reviewed] Test card HTML includes `hx-swap="outerHTML"` attribute

### Implementation - HTMX Integration

- [ ] [impl] [ ] [reviewed] Update `WorktreeListView.renderCard()` to include HTMX attributes
- [ ] [impl] [ ] [reviewed] Ensure cards have unique IDs for HTMX targeting

### Tests - Refresh Endpoint

- [ ] [impl] [ ] [reviewed] Test `GET /api/worktrees/:issueId/refresh` returns JSON status
- [ ] [impl] [ ] [reviewed] Test refresh endpoint respects throttle (returns throttled status)
- [ ] [impl] [ ] [reviewed] Test refresh endpoint updates cache on success

### Implementation - Refresh Endpoint

- [ ] [impl] [ ] [reviewed] Add `GET /api/worktrees/:issueId/refresh` endpoint to CaskServer
- [ ] [impl] [ ] [reviewed] Return `{"status": "refreshed"}` or `{"status": "throttled"}`

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

**Phase Status:** Not Started
