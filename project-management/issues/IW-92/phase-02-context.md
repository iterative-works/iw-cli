# Phase 2 Context: Aggressive caching for instant subsequent loads

**Issue:** IW-92
**Phase:** 2 of 5
**Story:** Story 4 - Instant dashboard reload

## Goals

This phase ensures the cache is always available for instant dashboard loads by:

1. Ensuring cached data persists and is always available
2. Adjusting cache TTLs for longer lifetime (stale-while-revalidate pattern)
3. Preventing cache invalidation on API errors (preserve stale data)
4. Ensuring subsequent dashboard loads render in < 50ms

## Scope

### In Scope

- Adjust cache TTL constants for longer lifetime
- Implement stale-while-revalidate pattern (always render cache, mark stale)
- Ensure cache persists across server restarts
- Prevent cache clearing on API failures (keep stale data)
- Add cache warming on worktree registration (optional, if time permits)

### Out of Scope (Later Phases)

- Background refresh after initial render (Phase 3)
- Per-card HTMX polling (Phase 4)
- Priority-based refresh (Phase 5)

## Dependencies

### From Previous Phases

- **Phase 1**: `getCachedOnly()` methods in `IssueCacheService` and `PullRequestCacheService`
- **Phase 1**: `isStale()` methods in `CachedIssue` and `CachedPR`
- **Phase 1**: Stale indicators in `WorktreeListView`
- **Phase 1**: Dashboard non-blocking render from cache

### External Dependencies

- Existing `StateRepository` for cache persistence
- Existing `ServerState.json` for state storage

## Technical Approach

### Current Caching Behavior

The current cache implementation has these characteristics:
- Issue cache TTL: 5 minutes (configured in IssueCacheService)
- PR cache TTL: 2 minutes (configured in PullRequestCacheService)
- Cache cleared on fetch failures (API errors)
- Cache persists in ServerState.json

### Target Caching Behavior

After this phase:
- Issue cache TTL: configurable, default 30 minutes (display TTL)
- PR cache TTL: configurable, default 15 minutes (display TTL)
- "Refresh TTL": 30 seconds (how often to attempt background refresh)
- Cache NEVER cleared on fetch failures (keep stale data)
- Cache always persisted to ServerState.json after updates

### Key Changes

1. **Cache TTL separation**
   - **Display TTL**: How long before showing "stale" indicator (longer)
   - **Refresh TTL**: How long before attempting background refresh (shorter)
   - Keep displaying cached data regardless of age (stale-while-revalidate)

2. **Error handling changes**
   - On API failure: log error, keep existing cache, mark as "stale"
   - Never clear cache on errors
   - Track last successful fetch timestamp separately from cache age

3. **Cache persistence**
   - Verify ServerState.json is written after every cache update
   - Ensure cache survives server restarts
   - Consider lazy initialization (don't block startup on cache load)

4. **Configuration**
   - Add optional environment variables:
     - `IW_ISSUE_CACHE_TTL_MINUTES` (default: 30)
     - `IW_PR_CACHE_TTL_MINUTES` (default: 15)
     - `IW_CACHE_REFRESH_SECONDS` (default: 30)

### Stale-While-Revalidate Pattern

```
Request → Check cache
        → IF cache exists (any age):
            → Return cached data immediately
            → Mark as "stale" if cache > display TTL
        → IF no cache:
            → Return skeleton/placeholder
        → BACKGROUND (later phases):
            → If cache > refresh TTL: trigger refresh
```

## Files to Modify

### Core Changes

| File | Change |
|------|--------|
| `.iw/core/IssueCacheService.scala` | Adjust TTL, add error preservation logic |
| `.iw/core/PullRequestCacheService.scala` | Adjust TTL, add error preservation logic |
| `.iw/core/CachedIssue.scala` | Add configurable TTL support |
| `.iw/core/CachedPR.scala` | Add configurable TTL support |
| `.iw/core/StateRepository.scala` | Ensure cache persists after updates |

### Configuration

| File | Change |
|------|--------|
| `.iw/core/Config.scala` (if exists) | Add cache TTL configuration |
| `.iw/core/CacheConfig.scala` (new) | Optional: centralize cache configuration |

## Testing Strategy

### Unit Tests

1. **Cache TTL behavior**
   - Verify `isStale()` uses configurable TTL
   - Verify stale data is still returned (not discarded)

2. **Error handling**
   - Simulate API failure
   - Verify cache is preserved (not cleared)
   - Verify subsequent reads return stale data

3. **Cache persistence**
   - Write to cache
   - Verify StateRepository.save() is called
   - Verify data survives simulated "restart" (clear in-memory, reload from file)

### Integration Tests

1. **Dashboard loads after API failure**
   - Populate cache
   - Make API unavailable
   - Load dashboard
   - Verify cached data displayed (not error)

2. **Dashboard loads after server restart**
   - Populate cache
   - Simulate restart (reload StateRepository from file)
   - Load dashboard
   - Verify cached data displayed

### E2E Tests (BATS)

1. **Second dashboard load is instant**
   - Load dashboard (populates cache)
   - Load dashboard again
   - Verify response time < 50ms

2. **Cache survives restart**
   - Start server, load dashboard
   - Stop server, start server
   - Load dashboard
   - Verify cached data displayed

## Acceptance Criteria

- [ ] Subsequent dashboard loads render in < 50ms with cached data
- [ ] Cache persists across server restarts
- [ ] API failures don't clear existing cache
- [ ] Stale data displayed with indicator (not blank/error)
- [ ] Cache TTLs are configurable via environment variables
- [ ] All existing Phase 1 functionality preserved

## Notes

- This phase focuses on cache reliability and longevity
- The "always render cache" pattern established in Phase 1 is extended here
- Background refresh (actually triggering API calls) comes in Phase 3
- The key insight: better to show stale data than no data or errors
- Configuration should be optional - sensible defaults should work for most cases

## Success Metrics

- Second page load: < 50ms (vs 100ms target for first load)
- Cache hit rate: near 100% after initial population
- Zero dashboard errors due to API failures (graceful degradation)
