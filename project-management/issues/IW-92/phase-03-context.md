# Phase 3 Context: Background refresh of issue data

**Issue:** IW-92
**Phase:** 3 of 5
**Story:** Story 2 - Background refresh of issue data

## User Story

```gherkin
Feature: Background data refresh
  As a user viewing the dashboard
  I want issue data to refresh in the background
  So that I see current status without waiting

Scenario: Issue data refreshes after initial render
  Given the dashboard has loaded with cached data
  When the page finishes rendering
  Then the server fetches fresh issue data from APIs
  And each worktree card updates with fresh data as it arrives
  And the "Loading..." indicator disappears when refresh completes
  And I see a timestamp showing when data was last updated
```

## Goals

This phase adds asynchronous background refresh to keep cached data fresh:

1. New API endpoints for per-worktree data refresh
2. HTMX-triggered updates after initial render
3. Managing concurrent API requests (rate limiting, error handling)
4. Graceful failure handling (show stale data instead of errors)

## Scope

### In Scope

- New endpoint: `GET /worktrees/:issueId/card` - Returns single worktree card HTML
- New endpoint: `GET /api/worktrees/:issueId/refresh` - Triggers refresh for one worktree
- HTMX polling attributes on worktree cards
- Per-card "Updated X ago" timestamps
- Rate limiting (30s throttle per card)
- Error handling (keep stale data on API failure)

### Out of Scope (Later Phases)

- Smooth CSS transitions for card updates (Phase 4)
- Priority-based refresh ordering (Phase 5)
- Server-Sent Events (keeping simple with polling for now)

## Dependencies

### From Previous Phases

**Phase 1:**
- `getCachedOnly()` methods in `IssueCacheService` and `PullRequestCacheService`
- `isStale()` methods in `CachedIssue` and `CachedPR`
- Stale indicators in `WorktreeListView`
- Dashboard non-blocking render from cache

**Phase 2:**
- `CacheConfig` for configurable TTLs
- `CachedPR.isStale()` with configurable TTL
- Stale cache preservation on API failures
- Error handling that returns stale data instead of errors

### External Dependencies

- HTMX library (already included in dashboard, v1.9.10)
- Existing Cask HTTP server
- Existing cache infrastructure

## Technical Approach

### Current Flow (Phases 1+2)

```
Request → Load all worktrees
        → For each: render from cache (instant)
        → Return complete dashboard HTML
        → User sees cached data with stale indicators
        → No refresh happens (data ages)
```

### Target Flow (Phase 3)

```
Request → Load all worktrees
        → For each: render from cache with HTMX polling attributes
        → Return complete dashboard HTML
        → Browser: HTMX triggers per-card refresh on load
        → Server: Per-card endpoint fetches fresh data, returns HTML
        → Browser: HTMX swaps card content (no page reload)
        → User sees cards update one by one
```

### Key Components

1. **Per-Card Endpoint**
   ```
   GET /worktrees/:issueId/card
   ```
   - Returns single worktree card HTML fragment
   - Fetches fresh issue data from API (with cache fallback)
   - Includes "Updated just now" timestamp
   - Returns stale data on API failure

2. **Refresh Trigger Endpoint**
   ```
   GET /api/worktrees/:issueId/refresh
   ```
   - Forces cache refresh for one worktree
   - Returns JSON status (success/failure)
   - Respects 30s throttle

3. **HTMX Integration**
   - Add `hx-get="/worktrees/{issueId}/card"` to each card
   - Add `hx-trigger="load, every 30s"` for polling
   - Add `hx-swap="outerHTML"` for card replacement

4. **Rate Limiting**
   - Track last refresh time per worktree
   - Skip API calls if < 30s since last refresh
   - Return cached data immediately if throttled

### Timestamp Display

Each card should show:
- "Updated just now" (< 30s ago)
- "Updated X seconds ago" (30s-60s)
- "Updated X minutes ago" (1m-60m)
- "Updated X hours ago" (> 60m)

### Error Handling

On API failure:
- Keep displaying cached data
- Update timestamp to show when last successful
- No error messages (silent degradation)
- Log error to server console for debugging

## Files to Modify

### Core Changes

| File | Change |
|------|--------|
| `.iw/core/DashboardService.scala` | Add per-card rendering method |
| `.iw/core/WorktreeListView.scala` | Add HTMX attributes to cards |
| `.iw/core/CaskServer.scala` | Add new endpoints |

### New Files

| File | Purpose |
|------|---------|
| `.iw/core/WorktreeCardService.scala` | Per-card refresh logic |
| `.iw/core/RefreshThrottle.scala` | Rate limiting logic |

### Test Files

| File | Purpose |
|------|---------|
| `.iw/core/test/WorktreeCardServiceTest.scala` | Unit tests for card refresh |
| `.iw/core/test/RefreshThrottleTest.scala` | Unit tests for rate limiting |

## Testing Strategy

### Unit Tests

1. **Per-card rendering**
   - `WorktreeCardService.renderCard()` returns valid HTML
   - Card includes correct HTMX attributes
   - Timestamp formatting is correct

2. **Rate limiting**
   - Refresh blocked if < 30s since last
   - Refresh allowed if > 30s since last
   - Each worktree tracked independently

3. **Error handling**
   - API failure returns cached data
   - Cached data preserved on error
   - No exceptions propagate to response

### Integration Tests

1. **Card endpoint**
   - `GET /worktrees/:issueId/card` returns HTML fragment
   - Response includes HTMX swap headers if needed
   - 404 for unknown worktree

2. **Refresh endpoint**
   - `GET /api/worktrees/:issueId/refresh` returns JSON
   - Respects throttle (returns throttled status)
   - Updates cache on success

### E2E Tests (BATS)

1. **Background refresh flow**
   - Load dashboard
   - Wait for HTMX to trigger card refresh
   - Verify cards update with fresh data
   - Verify timestamps show "Updated just now"

## Acceptance Criteria

- [ ] Fresh issue data fetched in background after initial render
- [ ] Each card updates independently (no full page refresh)
- [ ] Failed API calls don't block other cards from updating
- [ ] User sees timestamp of last successful refresh per card
- [ ] 30s throttle prevents API hammering
- [ ] All existing Phase 1+2 functionality preserved

## Notes

- This phase introduces async patterns to the previously synchronous dashboard
- HTMX handles the complexity of polling and partial updates
- Rate limiting is per-worktree (each card refreshes independently)
- Error handling follows Phase 2's "stale is better than error" pattern
- Keep UI simple - no loading spinners during refresh, just update when ready

## Success Metrics

- Cards update within 5s of page load
- API calls throttled to max 1 per 30s per card
- No page jumps or flicker during updates
- Dashboard remains responsive during refresh
