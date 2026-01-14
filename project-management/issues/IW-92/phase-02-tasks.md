# Phase 2 Tasks: Aggressive caching for instant subsequent loads

**Issue:** IW-92
**Phase:** 2 of 5
**Story:** Story 4 - Instant dashboard reload
**Goal:** Cache always available, instant subsequent loads (< 50ms)

## Task Groups

### Setup

- [x] [setup] Review existing cache implementation in `IssueCacheService.scala` and `PullRequestCacheService.scala`
- [x] [setup] Review `CachedIssue.scala` and `CachedPR.scala` TTL configuration

### Tests - Cache Configuration

- [x] [test] Test `CachedIssue.isStale()` respects configurable TTL parameter
- [x] [test] Test `CachedPR.isStale()` respects configurable TTL parameter
- [x] [test] Test cache returns stale data (not discarded when past TTL)

### Implementation - Cache Configuration

- [x] [impl] Add configurable TTL support to `CachedIssue.isStale()` method
- [x] [impl] Add configurable TTL support to `CachedPR.isStale()` method
- [x] [impl] Add `CacheConfig` object with environment variable support for TTLs

### Tests - Error Handling

- [x] [test] Test `IssueCacheService` preserves cache on API failure
- [x] [test] Test `PullRequestCacheService` preserves cache on API failure
- [x] [test] Test stale data is returned after API failure

### Implementation - Error Handling

- [x] [impl] Modify `IssueCacheService.getOrFetch()` to preserve cache on API errors
- [x] [impl] Modify `PullRequestCacheService.getOrFetch()` to preserve cache on API errors

### Tests - Cache Persistence

- [x] [test] Test `StateRepository` saves cache after updates
- [x] [test] Test cache survives simulated restart (clear in-memory, reload from file)

### Implementation - Cache Persistence

- [x] [impl] Verify `StateRepository.save()` is called after cache updates
- [x] [impl] Add explicit save after cache write operations if missing

### Integration

- [ ] [integration] Integration test: Dashboard loads after API failure (shows cached data)
- [ ] [integration] Integration test: Dashboard loads after simulated restart

## Acceptance Criteria Checklist

- [ ] Subsequent dashboard loads render in < 50ms with cached data (needs integration test verification)
- [x] Cache persists across server restarts (verified via StateRepository tests)
- [x] API failures don't clear existing cache (implemented in PullRequestCacheService, already existed in IssueCacheService)
- [x] Stale data displayed with indicator (not blank/error) (Phase 1 implementation still works)
- [x] Cache TTLs are configurable via environment variables (CacheConfig implemented with env var support)
- [x] All existing Phase 1 functionality preserved (all tests passing)

## Notes

- Build on Phase 1's `getCachedOnly()` and `isStale()` methods
- Don't implement background refresh yet (Phase 3)
- Focus on cache reliability and longevity
- Key insight: better to show stale data than no data or errors
