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

## Phase 2: Aggressive caching for instant subsequent loads (2026-01-14)

**What was built:**
- Domain: `CacheConfig.scala` - New pure configuration for cache TTLs with environment variable support (no I/O)
- Domain: `CachedPR.scala` - Added configurable TTL support (ttlMinutes parameter)
- Domain: `CachedIssue.scala` - Already had configurable TTL from Phase 1
- Application: `PullRequestCacheService.scala` - Added stale cache preservation on API failures

**Decisions made:**
- Configurable TTLs: Issue cache default 30 minutes, PR cache default 15 minutes (up from 5 and 2)
- Environment variables: `IW_ISSUE_CACHE_TTL_MINUTES` and `IW_PR_CACHE_TTL_MINUTES` for customization
- Stale-while-revalidate: Always return stale cache on API failure, never discard data
- Pure configuration: CacheConfig accepts environment as Map, no I/O in domain layer
- Graceful degradation: Better to show stale data than errors or blank pages

**Patterns applied:**
- Functional Core / Imperative Shell: CacheConfig is pure, I/O (reading env) happens at caller level
- Stale-while-revalidate: Cache data returned immediately, freshness check separate from availability
- Configuration as domain: TTL values passed through pure configuration object

**Testing:**
- Unit tests: 6 tests added (3 CacheConfig, 2 CachedPR TTL, 1 error preservation)
- All 1149 tests passing (6 new tests added)
- Coverage: TTL validation, zero/negative rejection, API failure fallback

**Code review:**
- Iterations: 2
- Review files: review-phase-02-20260114-232545.md, review-phase-02-20260114-233240.md
- Critical fix: Removed `fromSystemEnv()` I/O method from CacheConfig (FCIS violation)
- Findings: 0 critical (after fix), 7 warnings, 8 suggestions
- Major feedback: Consider extracting TTL parsing to helper function, file organization suggestion

**For next phases:**
- Available utilities: `CacheConfig` for TTL values, `CachedPR.isStale()` with custom TTL
- Extension points: Background refresh can use CacheConfig for timing, error handling pattern established
- Notes: Cache is now reliable and persistent; Phase 3 will add background refresh

**Files changed:**
```
A	.iw/core/CacheConfig.scala
M	.iw/core/CachedPR.scala
M	.iw/core/PullRequestCacheService.scala
A	.iw/core/test/CacheConfigTest.scala
M	.iw/core/test/CachedIssueTest.scala
M	.iw/core/test/CachedPRTest.scala
M	.iw/core/test/PullRequestCacheServiceTest.scala
```

---

## Phase 3: Background refresh of issue data (2026-01-15)

**What was built:**
- Application: `RefreshThrottle.scala` - Per-worktree rate limiting (30s throttle between refreshes)
- Presentation: `TimestampFormatter.scala` - Pure utility for "Updated X ago" formatting
- Application: `WorktreeCardService.scala` - Per-card rendering with refresh logic, API failure handling
- Presentation: `WorktreeListView.scala` - Added HTMX attributes for polling (`hx-get`, `hx-trigger`, `hx-swap`)
- Application: `CaskServer.scala` - Added `/worktrees/:issueId/card` and `/api/worktrees/:issueId/refresh` endpoints

**Decisions made:**
- HTMX polling: Cards use `hx-trigger="load delay:1s, every 30s"` for automatic refresh
- Rate limiting: 30-second throttle per worktree using mutable.Map (pragmatic for single-server CLI)
- Stale-while-revalidate: Always return cached data on API failure (silent degradation)
- Timestamp display: Human-readable "Updated X ago" format in each card
- Function parameter DI: fetchIssue passed as function parameter for testability

**Patterns applied:**
- Functional Core / Imperative Shell: Time passed as parameter (`now: Instant`), pure formatting logic
- HTMX progressive enhancement: Initial render from cache, HTMX handles async updates
- Rate limiting: Per-resource throttle prevents API hammering
- Silent degradation: No error messages to user, stale data preferred over errors

**Testing:**
- Unit tests: 11 tests added across 3 test files
- RefreshThrottleTest: 3 tests for throttle behavior
- TimestampFormatterTest: 4 tests for time formatting
- WorktreeCardServiceTest: 4 tests for card rendering

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20260115-081000.md
- Critical findings: 4 (all accepted as pragmatic choices for CLI tool)
  - Mutable state in RefreshThrottle: Accepted for single-server CLI
  - Side effects in WorktreeCardService: Accepted, function parameters provide DI
- Warnings: 7 (deferred for future refactoring)
- Suggestions: 10 (nice to have)

**For next phases:**
- Available utilities: `RefreshThrottle` for rate limiting, `TimestampFormatter.formatUpdateTimestamp()`, `WorktreeCardService.renderCard()`
- Extension points: HTMX attributes can be customized for different polling intervals, refresh endpoint returns JSON for programmatic use
- Notes: Integration tests deferred, acceptance criteria should be verified manually in Phase 4

**Files changed:**
```
M	.iw/core/CaskServer.scala
A	.iw/core/RefreshThrottle.scala
A	.iw/core/TimestampFormatter.scala
A	.iw/core/WorktreeCardService.scala
M	.iw/core/WorktreeListView.scala
A	.iw/core/test/RefreshThrottleTest.scala
A	.iw/core/test/TimestampFormatterTest.scala
A	.iw/core/test/WorktreeCardServiceTest.scala
```

---

## Phase 4: CSS transitions and tab visibility (2026-01-15)

**What was built:**
- Presentation: `DashboardService.scala` - Added CSS transition styles (.htmx-swapping/.htmx-settling), tab visibility script, mobile-responsive styles
- Presentation: `WorktreeListView.scala` - Updated HTMX attributes (hx-swap with transition:true, hx-trigger with refresh from:body)

**Decisions made:**
- Transition timing: 200ms opacity fade for smooth but fast perceived performance
- Tab visibility: `visibilitychange` event triggers `htmx.trigger(document.body, 'refresh')` for instant refresh on tab focus
- Mobile breakpoint: 768px for responsive card layout
- Touch targets: 44px minimum height for accessibility compliance
- Layout stability: min-height on cards prevents layout shift during updates

**Patterns applied:**
- Progressive enhancement: CSS transitions are graceful degradation (no JS required for basic functionality)
- HTMX native classes: Using built-in `.htmx-swapping` and `.htmx-settling` classes for transition hooks
- View Transitions API: `hx-swap="outerHTML transition:true"` enables browser-native smooth swaps

**Testing:**
- Unit tests: 10 tests added
- DashboardServiceTest: 7 tests for CSS transitions, visibility script, mobile styles
- WorktreeListViewTest: 2 tests for HTMX attribute updates
- All 1186 tests passing

**Code review:**
- Iterations: 1
- Review file: review-phase-04-20260115-091500.md
- Findings: 0 critical, 4 warnings, 4 suggestions
- Major feedback: Tests verify CSS/HTML strings (acceptable for unit tests, supplement with E2E)

**For next phases:**
- Available utilities: CSS transition classes, visibility refresh mechanism
- Extension points: Breakpoint can be customized, transition timing adjustable
- Notes: Phase is CSS/JavaScript polish - no new Scala business logic

**Files changed:**
```
M	.iw/core/DashboardService.scala
M	.iw/core/WorktreeListView.scala
M	.iw/core/test/DashboardServiceTest.scala
M	.iw/core/test/WorktreeListViewTest.scala
```

---

## Phase 5: Visible-items-first optimization (2026-01-15)

**What was built:**
- Domain: `WorktreePriority.scala` - Pure priority score calculation based on last activity timestamp
- Application: `DashboardService.scala` - Added sorting of worktrees by priority before rendering
- Presentation: `WorktreeListView.scala` - Added position-based staggered HTMX polling delays

**Decisions made:**
- Priority score: Negative seconds since last activity (`-Duration.between(lastSeenAt, now).getSeconds`) - more recent = higher score
- Sorting: `sortBy` with `Ordering[Long].reverse` to render most recently active worktrees first
- Staggered delays: Position 1-3 = 500ms, 4-8 = 2s, 9+ = 5s (reduces initial API burst)
- Position as visibility proxy: Cards at top of list assumed to be "above the fold"

**Patterns applied:**
- Functional Core / Imperative Shell: Time passed as parameter (`now: Instant`), priority calculation is pure
- Position-based optimization: Using list position as proxy for viewport visibility (simpler than JavaScript-based detection)
- Graceful scaling: Dashboard remains fast even with 10+ worktrees

**Testing:**
- Unit tests: 10 tests added across 3 test files
- WorktreePriorityTest: 4 tests for priority score calculation
- DashboardServiceTest: 2 tests for sorted rendering
- WorktreeListViewTest: 4 tests for staggered polling delays

**Code review:**
- Iterations: 1
- Review file: review-phase-05-20260115-105000.md
- Findings: 0 critical, 3 warnings, 9 suggestions
- Major feedback: Test fragility (string substring extraction), edge cases (boundary positions, future timestamps)

**For next phases:**
- Available utilities: `WorktreePriority.priorityScore()` for any priority-based sorting
- Extension points: Delay tiers can be customized, priority formula can evolve
- Notes: This is the final phase (stretch goal optimization) - core dashboard functionality complete

**Files changed:**
```
A	.iw/core/WorktreePriority.scala
M	.iw/core/DashboardService.scala
M	.iw/core/WorktreeListView.scala
A	.iw/core/test/WorktreePriorityTest.scala
M	.iw/core/test/DashboardServiceTest.scala
M	.iw/core/test/WorktreeListViewTest.scala
```

---
