# Phase 2 Tasks: Aggressive caching for instant subsequent loads

**Issue:** IW-92
**Phase:** 2 of 5
**Story:** Story 4 - Instant dashboard reload
**Goal:** Cache always available, instant subsequent loads (< 50ms)

## Task Groups

### Setup

- [ ] [setup] Review existing cache implementation in `IssueCacheService.scala` and `PullRequestCacheService.scala`
- [ ] [setup] Review `CachedIssue.scala` and `CachedPR.scala` TTL configuration

### Tests - Cache Configuration

- [ ] [test] Test `CachedIssue.isStale()` respects configurable TTL parameter
- [ ] [test] Test `CachedPR.isStale()` respects configurable TTL parameter
- [ ] [test] Test cache returns stale data (not discarded when past TTL)

### Implementation - Cache Configuration

- [ ] [impl] Add configurable TTL support to `CachedIssue.isStale()` method
- [ ] [impl] Add configurable TTL support to `CachedPR.isStale()` method
- [ ] [impl] Add `CacheConfig` object with environment variable support for TTLs

### Tests - Error Handling

- [ ] [test] Test `IssueCacheService` preserves cache on API failure
- [ ] [test] Test `PullRequestCacheService` preserves cache on API failure
- [ ] [test] Test stale data is returned after API failure

### Implementation - Error Handling

- [ ] [impl] Modify `IssueCacheService.getOrFetch()` to preserve cache on API errors
- [ ] [impl] Modify `PullRequestCacheService.getOrFetch()` to preserve cache on API errors

### Tests - Cache Persistence

- [ ] [test] Test `StateRepository` saves cache after updates
- [ ] [test] Test cache survives simulated restart (clear in-memory, reload from file)

### Implementation - Cache Persistence

- [ ] [impl] Verify `StateRepository.save()` is called after cache updates
- [ ] [impl] Add explicit save after cache write operations if missing

### Integration

- [ ] [integration] Integration test: Dashboard loads after API failure (shows cached data)
- [ ] [integration] Integration test: Dashboard loads after simulated restart

## Acceptance Criteria Checklist

- [ ] Subsequent dashboard loads render in < 50ms with cached data
- [ ] Cache persists across server restarts
- [ ] API failures don't clear existing cache
- [ ] Stale data displayed with indicator (not blank/error)
- [ ] Cache TTLs are configurable via environment variables
- [ ] All existing Phase 1 functionality preserved

## Notes

- Build on Phase 1's `getCachedOnly()` and `isStale()` methods
- Don't implement background refresh yet (Phase 3)
- Focus on cache reliability and longevity
- Key insight: better to show stale data than no data or errors
