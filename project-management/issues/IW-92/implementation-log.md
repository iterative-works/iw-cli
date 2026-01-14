# Implementation Log: Dashboard loads too slowly with multiple worktrees

Issue: IW-92

This log tracks the evolution of implementation across phases.

---

## Phase 1: Fast initial dashboard load with cached data (2026-01-14)

**What was built:**
- Domain: `CachedIssue.scala` - Added `isStale()` method for detecting stale cache data
- Domain: `CachedPR.scala` - Added `isStale()` method for detecting stale PR cache
- Application: `IssueCacheService.scala` - Added `getCachedOnly()` method for non-blocking cache access
- Application: `PullRequestCacheService.scala` - Added `getCachedOnly()` method for non-blocking cache access
- Application: `DashboardService.scala` - Added non-blocking fetch methods and CSS for stale/skeleton indicators
- Presentation: `WorktreeListView.scala` - Added stale badge rendering and skeleton card rendering

**Decisions made:**
- Stale indicator: Show orange "stale" badge when cache age >= TTL (not blocking, just informational)
- Skeleton cards: Show shimmer animation placeholder when cache is empty
- Non-blocking: `getCachedOnly()` returns Option, never calls external APIs
- Return type: Using 3-tuple `(IssueData, Boolean, Boolean)` for (data, fromCache, isStale)

**Patterns applied:**
- Functional Core / Imperative Shell: All cache logic is pure, time is injected as parameter
- Cache-aside pattern: Check cache first, never block on API during initial render

**Testing:**
- Unit tests: 16 tests added across 5 test files
- All 1143 tests passing
- E2E tests marked for manual verification

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260114-215600.md
- Findings: 0 critical, 7 warnings, 8 suggestions
- Major feedback: Consider extracting 3-tuple to named case class for readability

**For next phases:**
- Available utilities: `CachedIssue.isStale()`, `CachedPR.isStale()`, `getCachedOnly()` methods
- Extension points: Dashboard now renders from cache; Phase 2 will add aggressive caching, Phase 3 will add background refresh
- Notes: The current implementation achieves instant render goal but doesn't refresh stale data yet

**Files changed:**
```
M	.iw/core/CachedIssue.scala
M	.iw/core/CachedPR.scala
M	.iw/core/DashboardService.scala
M	.iw/core/IssueCacheService.scala
M	.iw/core/PullRequestCacheService.scala
M	.iw/core/WorktreeListView.scala
M	.iw/core/test/CachedIssueTest.scala
M	.iw/core/test/CachedPRTest.scala
M	.iw/core/test/IssueCacheServiceTest.scala
M	.iw/core/test/PullRequestCacheServiceTest.scala
M	.iw/core/test/WorktreeListViewTest.scala
```

---
