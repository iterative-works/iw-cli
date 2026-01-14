# Phase 1 Context: Fast initial dashboard load with cached data

**Issue:** IW-92
**Phase:** 1 of 5
**Story:** Story 1 - Dashboard instant load

## Goals

This phase establishes the foundation for instant dashboard loading by changing the rendering pattern from "fetch-all-then-render" to "render-cache-immediately":

1. Dashboard renders immediately using cached data (no API calls during initial render)
2. Cards show visual indicator when displaying stale data
3. Dashboard loads in < 100ms when cache is populated
4. Skeleton cards displayed for worktrees with no cache

## Scope

### In Scope

- Modify `DashboardService.renderDashboard()` to use only cached data (no blocking API calls)
- Add "stale" indicator to WorktreeListView cards when cached data is outdated
- Add skeleton card rendering for cache misses
- Ensure dashboard always renders immediately regardless of API availability
- Add CSS styles for stale indicators and skeleton cards

### Out of Scope (Later Phases)

- Background refresh after initial render (Phase 3)
- Per-card HTMX polling (Phase 4)
- Cache TTL tuning (Phase 2)
- Priority-based refresh (Phase 5)

## Dependencies

### From Previous Phases

- None (this is Phase 1)

### External Dependencies

- Existing cache infrastructure (`StateRepository`, `IssueCacheService`, etc.)
- Existing `WorktreeListView` for card rendering
- HTMX library (already included in dashboard)

## Technical Approach

### Current Architecture (Blocking)

```
GET / → CaskServer
      → DashboardService.renderDashboard()
         → For EACH worktree:
            → IssueCacheService.fetchWithCache() ← BLOCKS on API if cache expired
            → WorkflowProgressService.fetchProgress() ← Reads files
            → GitStatusService.getGitStatus() ← Runs git commands
            → PullRequestCacheService.fetchPR() ← BLOCKS on CLI if cache expired
            → ReviewStateService.fetchReviewState() ← Reads file
      → WorktreeListView.render()
      → Return HTML (only after ALL data fetched)
```

### Target Architecture (Non-blocking)

```
GET / → CaskServer
      → DashboardService.renderDashboardFromCache()
         → For EACH worktree:
            → IssueCacheService.getCachedOnly() ← Returns cached or None (NO API CALL)
            → WorkflowProgressService.fetchProgress() ← Reads files (fast, keep)
            → GitStatusService.getGitStatus() ← Runs git commands (fast, keep)
            → PullRequestCacheService.getCachedOnly() ← Returns cached or None (NO CLI CALL)
            → ReviewStateService.fetchReviewState() ← Reads file (fast, keep)
      → WorktreeListView.renderWithStaleIndicators()
      → Return HTML (immediately with cached/skeleton data)
```

### Key Changes

1. **New method: `IssueCacheService.getCachedOnly()`**
   - Returns `Option[IssueData]` from cache
   - Does NOT call API even if cache is stale
   - Returns `None` if no cache entry exists

2. **New method: `PullRequestCacheService.getCachedOnly()`**
   - Returns `Option[PullRequestData]` from cache
   - Does NOT call CLI even if cache is stale
   - Returns `None` if no cache entry exists

3. **Modify `DashboardService.renderDashboard()`**
   - Use `getCachedOnly` instead of `fetchWithCache`
   - Pass stale indicator info to view
   - Handle cache misses gracefully (skeleton cards)

4. **Modify `WorktreeListView.render()`**
   - Accept stale indicator flag per card
   - Render skeleton cards for cache misses
   - Add CSS classes for stale/skeleton states

5. **Add CSS styles**
   - `.stale-indicator` - visual badge showing data is stale
   - `.skeleton-card` - placeholder card while data loading
   - Animations for skeleton shimmer effect

## Files to Modify

### Core Changes

| File | Change |
|------|--------|
| `.iw/core/IssueCacheService.scala` | Add `getCachedOnly()` method |
| `.iw/core/PullRequestCacheService.scala` | Add `getCachedOnly()` method |
| `.iw/core/DashboardService.scala` | Use non-blocking cache methods |
| `.iw/core/WorktreeListView.scala` | Add stale indicators, skeleton cards |
| `.iw/core/CaskServer.scala` | Minor: ensure immediate response |

### Domain Model Updates

| File | Change |
|------|--------|
| `.iw/core/CachedIssue.scala` | Add `isStale(now: Instant): Boolean` method |
| `.iw/core/CachedPR.scala` | Add `isStale(now: Instant): Boolean` method |

### New CSS (if separate file)

| File | Change |
|------|--------|
| CSS in `CaskServer.scala` or separate file | Add stale/skeleton styles |

## Testing Strategy

### Unit Tests

1. **IssueCacheService.getCachedOnly()**
   - Returns cached data when cache exists (regardless of age)
   - Returns None when cache is empty
   - Does NOT call API (mock should not be invoked)

2. **PullRequestCacheService.getCachedOnly()**
   - Returns cached data when cache exists (regardless of age)
   - Returns None when cache is empty
   - Does NOT call CLI (mock should not be invoked)

3. **CachedIssue.isStale()**
   - Returns true when cache older than TTL
   - Returns false when cache newer than TTL

4. **WorktreeListView with stale indicators**
   - Renders stale badge when data is stale
   - Renders skeleton card when data is missing
   - Does not show stale badge for fresh data

### Integration Tests

1. **Dashboard loads with cached data only**
   - Mock API to be slow/failing
   - Verify dashboard returns in < 100ms
   - Verify cached data is displayed

2. **Dashboard handles empty cache**
   - Clear all caches
   - Verify skeleton cards are rendered
   - Verify no 500 errors

### E2E Tests (BATS)

1. **Dashboard with cached data renders fast**
   - Pre-populate cache
   - Load dashboard
   - Verify response time < 100ms
   - Verify cards show cached issue titles

2. **Dashboard with stale cache shows indicators**
   - Pre-populate cache with old timestamp
   - Load dashboard
   - Verify stale indicator visible on cards

## Acceptance Criteria

- [ ] Dashboard HTML loads in < 100ms with cached data (no API blocking)
- [ ] All worktree cards are visible immediately
- [ ] Stale data shows visual indicator ("Last updated X ago" or similar)
- [ ] Cache misses show skeleton/placeholder cards
- [ ] No blank screen or loading spinner for initial page load
- [ ] Existing functionality preserved (cards still show all info when cached)

## Notes

- This phase focuses ONLY on instant rendering with cached data
- Background refresh will be added in Phase 3
- Per-card updates will be added in Phase 4
- The goal is zero blocking API calls during initial dashboard render
- Git status and file reads are fast enough to remain synchronous
