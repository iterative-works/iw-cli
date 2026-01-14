# Phase 1 Tasks: Fast initial dashboard load with cached data

**Issue:** IW-92
**Phase:** 1 of 5
**Story:** Story 1 - Dashboard instant load

## Task Groups

### Setup (0 tasks - use existing test infrastructure)

No setup needed - existing test infrastructure in `.iw/core/` is sufficient.

### Domain Layer Tests & Implementation

- [ ] [test] Write test: `CachedIssue.isStale()` returns true when cache older than TTL
- [ ] [test] Write test: `CachedIssue.isStale()` returns false when cache newer than TTL
- [ ] [impl] Add `isStale(now: Instant): Boolean` method to `CachedIssue` object
- [ ] [test] Write test: `CachedPR.isStale()` returns true when cache older than TTL
- [ ] [test] Write test: `CachedPR.isStale()` returns false when cache newer than TTL
- [ ] [impl] Add `isStale(now: Instant): Boolean` method to `CachedPR` object

### Application Layer Tests & Implementation

- [ ] [test] Write test: `IssueCacheService.getCachedOnly()` returns cached data when cache exists
- [ ] [test] Write test: `IssueCacheService.getCachedOnly()` returns None when cache is empty
- [ ] [test] Write test: `IssueCacheService.getCachedOnly()` does NOT call fetch function (verify mock not invoked)
- [ ] [impl] Add `getCachedOnly()` method to `IssueCacheService`
- [ ] [test] Write test: `PullRequestCacheService.getCachedOnly()` returns cached PR when cache exists
- [ ] [test] Write test: `PullRequestCacheService.getCachedOnly()` returns None when cache is empty
- [ ] [test] Write test: `PullRequestCacheService.getCachedOnly()` does NOT call CLI (verify mock not invoked)
- [ ] [impl] Add `getCachedOnly()` method to `PullRequestCacheService`

### Presentation Layer Tests & Implementation

- [ ] [test] Write test: `WorktreeListView` renders stale badge when `isStale` is true
- [ ] [test] Write test: `WorktreeListView` does NOT render stale badge when `isStale` is false
- [ ] [test] Write test: `WorktreeListView` renders skeleton card when issue data is None
- [ ] [impl] Update `WorktreeListView.render()` to accept stale flag per card
- [ ] [impl] Add skeleton card rendering for cache misses in `WorktreeListView`
- [ ] [impl] Add CSS styles for `.stale-indicator` badge in `DashboardService`
- [ ] [impl] Add CSS styles for `.skeleton-card` placeholder in `DashboardService`

### Integration: DashboardService

- [ ] [test] Write test: `DashboardService.renderDashboard()` uses `getCachedOnly()` (no API blocking)
- [ ] [test] Write test: Dashboard renders in < 100ms with cached data (mock slow API)
- [ ] [test] Write test: Dashboard renders skeleton cards for worktrees with no cache
- [ ] [impl] Modify `DashboardService.renderDashboard()` to use non-blocking cache methods
- [ ] [impl] Pass stale indicator info from DashboardService to WorktreeListView

### E2E Verification (manual or BATS)

- [ ] [e2e] Verify dashboard with cached data renders cards immediately
- [ ] [e2e] Verify stale indicator shows when cache is outdated
- [ ] [e2e] Verify skeleton cards appear for cache misses

## Notes

- `getCachedOnly()` returns `Option[T]` - never calls external APIs
- `isStale()` is separate from `isValid()` - stale data can still be displayed with indicator
- Stale threshold: show indicator when cache age > TTL (even if we're still displaying it)
- Skeleton cards should look like regular cards but with placeholder content
- Focus on rendering speed - goal is < 100ms for cached dashboard load
