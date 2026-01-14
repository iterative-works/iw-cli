# Story-Driven Analysis: Dashboard loads too slowly with multiple worktrees

**Issue:** IW-92
**Created:** 2026-01-14
**Status:** Draft
**Classification:** Feature

## Problem Statement

The dashboard currently loads too slowly when multiple worktrees are registered because it fetches all data synchronously before rendering anything to the user. This creates a poor user experience where the user waits several seconds (or more) staring at a blank page while the server:

1. Loads all worktree registrations
2. For EACH worktree, sequentially fetches:
   - Issue data from Linear/YouTrack/GitHub API (or cache)
   - Workflow progress by parsing filesystem task files
   - Git status by executing git commands
   - PR data from gh/glab CLI (or cache)
   - Review state by reading JSON files

Only after ALL this data is collected does the server render and return the complete HTML page.

**User Impact:**
- Poor perceived performance (blank screen during load)
- No feedback during data fetching
- Frustrating experience, especially with 5+ worktrees
- Mobile users may think the page is broken

**Value of Fixing:**
- Near-instant dashboard load with cached/stale data
- Progressive enhancement as fresh data arrives
- Better mobile experience (critical for the "check status on phone" use case)
- Improved scalability (works well with 20+ worktrees)

## User Stories

### Story 1: Fast initial dashboard load with cached data

```gherkin
Feature: Dashboard instant load
  As a user opening the dashboard
  I want to see worktree cards immediately
  So that I get instant feedback instead of waiting

Scenario: Dashboard renders cached data immediately
  Given I have 5 registered worktrees
  And all worktrees have cached issue data from previous loads
  When I navigate to the dashboard at "/"
  Then I see the dashboard shell within 100ms
  And I see 5 worktree cards with cached issue titles and status
  And I see a visual indicator showing "Loading fresh data..." or similar
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
This requires splitting the dashboard rendering into two phases: (1) immediate render with cached/stale data, and (2) background refresh. The core challenge is changing the rendering pattern from "fetch-all-then-render" to "render-stale-then-update."

Key technical challenges:
- Modifying `DashboardService.renderDashboard()` to accept and render stale data without blocking
- Ensuring cached data is always available (seeding cache on registration)
- Adding UI indicators to distinguish stale vs fresh data

**Acceptance:**
- Dashboard HTML loads in < 100ms with cached data
- All worktree cards are visible immediately
- Visual indicator shows when data is stale/loading
- No blank screen or long wait

---

### Story 2: Background refresh of issue data

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

**Estimated Effort:** 8-12h
**Complexity:** Complex

**Technical Feasibility:**
This is the most complex story because it requires introducing asynchronous data fetching and incremental HTMX updates. The current architecture is purely synchronous request/response.

Key technical challenges:
- New API endpoints for per-worktree data refresh
- HTMX polling or SSE (Server-Sent Events) to trigger updates
- Managing concurrent API requests (rate limiting, error handling)
- Ensuring UI doesn't "flicker" or jump during updates
- Handling API failures gracefully (show stale data instead of errors)

**Acceptance:**
- Fresh issue data fetched in background after initial render
- Each card updates independently (no full page refresh)
- Failed API calls don't block other cards from updating
- User sees timestamp of last successful refresh per card

---

### Story 3: Incremental card updates via HTMX

```gherkin
Feature: Progressive card updates
  As a user viewing the dashboard
  I want each worktree card to update independently
  So that I see fresh data appear progressively

Scenario: Cards update one by one as data becomes available
  Given the dashboard has loaded with 5 cached worktree cards
  When fresh issue data arrives for worktree "IW-92"
  Then only the "IW-92" card re-renders with fresh data
  And other cards remain unchanged
  And the update is smooth without page flicker
  And the card shows "Updated just now"
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
This requires HTMX partial updates and new endpoints that return single worktree card HTML fragments. The main challenge is ensuring updates don't disrupt the user's scrolling or interaction.

Key technical challenges:
- New endpoint: `GET /worktrees/:issueId/card` returning single card HTML
- HTMX attributes for polling or event-driven updates
- CSS transitions for smooth card updates
- Managing update timing (don't spam with updates)

**Acceptance:**
- Each card updates independently via HTMX swap
- Updates are smooth and don't cause page jump
- User can interact with other cards while one updates
- Cards show "Updated X seconds ago" timestamp

---

### Story 4: Aggressive caching for instant subsequent loads

```gherkin
Feature: Instant dashboard reload
  As a user returning to the dashboard
  I want to see data instantly from cache
  So that I don't wait for APIs every time

Scenario: Second dashboard load is instant
  Given I loaded the dashboard 2 minutes ago
  And all issue data was cached successfully
  When I navigate to the dashboard again
  Then I see all worktree cards within 50ms
  And cached data is displayed immediately
  And background refresh starts automatically
  And cards update with fresh data as it arrives
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
This is mostly configuration and TTL tuning. The caching infrastructure already exists, but TTLs may be too short for instant loads.

Key technical challenges:
- Adjusting cache TTLs (currently 5min for issues, 2min for PRs)
- Ensuring cache is populated even on error (don't clear cache on API failure)
- Adding "always render from cache first" logic to dashboard

**Acceptance:**
- Subsequent dashboard loads render instantly (< 50ms)
- Cached data displayed even if slightly stale
- Background refresh happens automatically
- User can force refresh if needed

---

### Story 5: Visible-items-first optimization (stretch goal)

```gherkin
Feature: Prioritize visible worktrees
  As a user viewing the dashboard on mobile
  I want the first few cards to load fastest
  So that I see useful data immediately

Scenario: First 3 worktrees refresh before others
  Given I have 10 registered worktrees
  When the dashboard loads
  Then the first 3 cards refresh data first
  And remaining 7 cards refresh after the first 3
  And I see "Loading..." on cards below the fold
  And priority is based on last activity (most recent first)
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
This is a nice-to-have optimization that prioritizes refresh order. It requires sorting worktrees by priority and implementing a queue for background refresh.

Key technical challenges:
- Sorting worktrees by last activity timestamp
- Implementing priority queue for background refresh
- Detecting "above the fold" vs "below the fold" (viewport detection)

**Acceptance:**
- Most recently active worktrees refresh first
- User sees useful data quickly even with many worktrees
- Background refresh completes for all cards eventually
- No race conditions or ordering bugs

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Fast initial dashboard load with cached data

**Application Layer:**
- `DashboardService.renderDashboardWithCache()` - Renders dashboard using only cached data, no API calls
- `IssueCacheService.getCached()` - Retrieves cached issue data without TTL check

**Domain Layer:**
- `CachedIssue.isStale()` - Checks if cached data is stale (for UI indicators)

**Presentation Layer:**
- `WorktreeListView.renderWithStaleIndicator()` - Renders cards with "Loading..." badge if stale
- CSS: `.stale-indicator` styles

**Infrastructure Layer:**
- No changes needed (reuse existing StateRepository and cache)

---

### For Story 2: Background refresh of issue data

**Application Layer:**
- `DashboardRefreshService.refreshAll()` - Orchestrates background refresh for all worktrees
- `DashboardRefreshService.refreshOne()` - Fetches fresh data for a single worktree

**Infrastructure Layer:**
- `CaskServer.get("/api/worktrees/:issueId/refresh")` - API endpoint to trigger single worktree refresh
- Background thread/task to run refresh asynchronously

**Presentation Layer:**
- HTMX polling attributes on dashboard root (`hx-trigger="every 30s"` or similar)
- Loading indicators and timestamps in cards

---

### For Story 3: Incremental card updates via HTMX

**Infrastructure Layer:**
- `CaskServer.get("/worktrees/:issueId/card")` - Returns single worktree card HTML fragment

**Application Layer:**
- `WorktreeCardService.renderCard()` - Renders single card with fresh data

**Presentation Layer:**
- `WorktreeListView.renderCard()` - Renders single card HTML
- HTMX attributes: `hx-get`, `hx-target`, `hx-swap` on each card
- CSS transitions for smooth updates

---

### For Story 4: Aggressive caching for instant subsequent loads

**Application Layer:**
- `IssueCacheService` - Adjust TTL constants (longer cache lifetime)
- `DashboardService` - Change to "always render cache first, then refresh"

**Infrastructure Layer:**
- `StateRepository` - Ensure cache persists across server restarts

---

### For Story 5: Visible-items-first optimization (stretch goal)

**Application Layer:**
- `DashboardRefreshService.refreshPriority()` - Refreshes worktrees by priority order

**Domain Layer:**
- `WorktreeRegistration.priorityScore()` - Computes priority based on last activity

**Presentation Layer:**
- JavaScript (optional): Detect viewport and trigger refresh for visible cards first

---

## Technical Risks & Uncertainties

### CLARIFY: Asynchronous refresh strategy

The current architecture is purely synchronous request/response. Introducing background refresh requires deciding on an async strategy.

**Questions to answer:**
1. Should we use HTMX polling (`hx-trigger="every 30s"`) or Server-Sent Events (SSE)?
2. Should refresh happen per-card (N API calls) or batched (single endpoint refreshes all)?
3. How do we handle concurrency (rate limiting, max parallel requests)?

**Options:**

- **Option A: HTMX polling per card**
  - Each card polls its own endpoint: `GET /worktrees/:issueId/card`
  - Simple to implement, works with existing Cask server
  - Cons: Many HTTP requests (N cards = N polls every 30s)

- **Option B: HTMX polling for full list**
  - Dashboard polls: `GET /worktrees` returns updated list HTML
  - Fewer HTTP requests, simpler server logic
  - Cons: Re-renders entire list (more DOM churn, potential flicker)

- **Option C: Server-Sent Events (SSE)**
  - Server pushes updates to client via SSE stream
  - Most efficient (no polling overhead), instant updates
  - Cons: More complex implementation, requires SSE support in Cask

**Impact:** Affects Stories 2 and 3. Determines API endpoint design and HTMX patterns.

---

### CLARIFY: Cache persistence and seeding

Currently, cache lives in `ServerState.json` and may be empty on first load or after server restart.

**Questions to answer:**
1. Should cache persist across server restarts? (Currently: yes, via JSON file)
2. Should we seed cache on worktree registration? (Currently: no)
3. What happens if cache is empty? (Currently: synchronous fetch blocks render)

**Options:**

- **Option A: Seed cache on registration**
  - When worktree is registered via `PUT /api/v1/worktrees/:issueId`, fetch and cache issue data
  - Pros: Dashboard always has cached data available
  - Cons: Registration becomes slower (blocks on API call)

- **Option B: Lazy cache population**
  - First dashboard load still fetches synchronously, but subsequent loads use cache
  - Pros: Simple, no changes to registration flow
  - Cons: First load is still slow (doesn't solve UX problem for new worktrees)

- **Option C: Render empty cards with "Loading..." on cache miss**
  - Dashboard renders empty/skeleton cards immediately, then refreshes
  - Pros: Always instant render, even with empty cache
  - Cons: User sees empty cards briefly (could be jarring)

**Impact:** Affects Story 1 and 4. Determines whether first load is fast or slow.

---

### CLARIFY: TTL values for caching

Current TTL values:
- Issue data: 5 minutes
- PR data: 2 minutes

**Questions to answer:**
1. Are these TTLs optimal for the "instant load" use case?
2. Should we have different TTLs for synchronous vs background refresh?
3. Should TTLs be configurable per tracker type?

**Options:**

- **Option A: Increase TTLs across the board**
  - Issue cache: 15 minutes, PR cache: 10 minutes
  - Pros: More likely to have cached data for instant loads
  - Cons: Stale data shown for longer

- **Option B: Separate "stale-while-revalidate" TTL**
  - Render stale data immediately (up to 1 hour old), refresh in background
  - Pros: Always instant, fresh data arrives quickly
  - Cons: More complex cache logic

- **Option C: Zero TTL on render, background refresh updates cache**
  - Always render from cache (ignore TTL), always refresh in background
  - Pros: Simplest logic, always instant
  - Cons: May show very stale data if server was down

**Impact:** Affects Stories 1, 2, and 4. Determines perceived freshness vs speed tradeoff.

---

### CLARIFY: Error handling for background refresh

If background refresh fails (API down, rate limited, timeout), what should happen?

**Questions to answer:**
1. Should we show error indicators on cards?
2. Should we retry failed refreshes automatically?
3. Should we log errors for debugging?

**Options:**

- **Option A: Silent failure, keep stale data**
  - If refresh fails, keep showing cached data with "Last updated X ago"
  - Pros: User experience uninterrupted
  - Cons: User may not know data is stale

- **Option B: Show error badge on failed cards**
  - Display "⚠ Failed to refresh" on cards where API call failed
  - Pros: User is aware of staleness
  - Cons: Could be noisy if APIs are flaky

- **Option C: Retry with exponential backoff**
  - Automatically retry failed refreshes (30s, 1m, 2m intervals)
  - Pros: Recovers from transient failures
  - Cons: More complex retry logic

**Impact:** Affects Story 2. Determines UX for API failures.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Fast initial load with cache): 6-8 hours
- Story 2 (Background refresh): 8-12 hours
- Story 3 (Incremental card updates): 6-8 hours
- Story 4 (Aggressive caching): 4-6 hours
- Story 5 (Visible-items-first, stretch): 4-6 hours

**Total Range:** 28-40 hours

**Confidence:** Medium

**Reasoning:**
- **Moderate complexity:** This is primarily an architectural refactoring (synchronous → asynchronous), not new features
- **Unknown: HTMX patterns:** Team may not have experience with HTMX polling/SSE, could add learning time
- **Unknown: Concurrency handling:** Managing background refresh with rate limiting and error handling is non-trivial
- **Well-understood domain:** The data model (worktrees, issues, cache) is stable and well-understood
- **Existing patterns:** Cache infrastructure exists, we're adapting not building from scratch

**Assumptions baked into estimate:**
- CLARIFY markers will be resolved before task generation
- We choose HTMX polling (Option A or B) rather than SSE (simpler)
- We choose "seed cache on registration" (Option A) for instant loads
- We use "stale-while-revalidate" caching pattern (Option B for TTLs)
- Story 5 is optional/stretch (exclude from MVP if time is tight)

---

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure domain logic, cache logic, rendering logic
2. **Integration Tests**: HTTP endpoints, HTMX behavior, cache persistence
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenario

**Story-Specific Testing Notes:**

**Story 1 (Fast initial load):**
- Unit: `DashboardService.renderDashboardWithCache()` uses only cache, no API calls
- Unit: `CachedIssue.isStale()` correctly identifies stale data
- Integration: `GET /` responds within 100ms when cache is populated
- E2E: Load dashboard with cached data, verify < 100ms response time, verify cards render

**Story 2 (Background refresh):**
- Unit: `DashboardRefreshService.refreshAll()` calls API clients correctly
- Unit: Error handling for failed API calls (doesn't crash, keeps stale data)
- Integration: `GET /api/worktrees/:issueId/refresh` fetches fresh data and returns updated HTML
- E2E: Load dashboard, wait for background refresh, verify cards update with fresh data

**Story 3 (Incremental updates):**
- Unit: `WorktreeListView.renderCard()` renders single card HTML correctly
- Integration: `GET /worktrees/:issueId/card` returns valid HTML fragment
- Integration: HTMX swap updates card without full page reload
- E2E: Load dashboard, trigger card refresh, verify only target card updates

**Story 4 (Aggressive caching):**
- Unit: Cache TTL logic (stale-while-revalidate behavior)
- Integration: Cache persists across server restarts (load from JSON)
- E2E: Load dashboard, restart server, load again, verify instant load from cache

**Story 5 (Visible-items-first):**
- Unit: `WorktreeRegistration.priorityScore()` sorts by last activity
- Unit: `DashboardRefreshService.refreshPriority()` respects priority order
- E2E: Load dashboard with 10 worktrees, verify first 3 refresh before others

**Test Data Strategy:**
- Use in-memory test repositories for unit tests (no filesystem I/O)
- Use fixture JSON files for cached issue data
- Mock API clients (Linear, GitHub, YouTrack) for predictable responses
- Use BATS for E2E tests (existing test framework in project)

**Regression Coverage:**
- Ensure existing dashboard functionality still works (full synchronous load as fallback)
- Verify worktree registration/unregistration still works
- Test with 0, 1, 5, 20 worktrees to ensure scalability
- Test with empty cache, partial cache, full cache scenarios

---

## Deployment Considerations

### Database Changes

No database schema changes needed. All state is stored in `ServerState.json` (already supports caching).

**Story 1 migrations:**
- None (reuses existing cache structure)

**Story 2 migrations:**
- None (cache updates are in-place)

### Configuration Changes

**Environment variables (new):**
- `IW_DASHBOARD_CACHE_TTL` - Override default cache TTL (optional)
- `IW_DASHBOARD_REFRESH_INTERVAL` - Override default refresh polling interval (optional)

**Configuration file changes:**
- None needed

### Rollout Strategy

Stories can be deployed incrementally:

1. **Deploy Story 1 + 4 together (fast cache-based load)**
   - Low risk, improves UX immediately
   - Backwards compatible (if no cache, falls back to synchronous fetch)

2. **Deploy Story 2 + 3 together (background refresh + HTMX updates)**
   - Medium risk (new endpoints, HTMX patterns)
   - Requires HTMX library in HTML (already present)

3. **Deploy Story 5 separately (optional optimization)**
   - Low risk, purely additive

**Feature flag (optional):**
- `IW_DASHBOARD_LAZY_LOAD=true` enables new behavior, `false` uses old synchronous load

### Rollback Plan

If background refresh causes issues (API rate limiting, server overload, buggy updates):

1. **Disable background refresh via feature flag** (`IW_DASHBOARD_LAZY_LOAD=false`)
2. **Revert to synchronous load pattern** (roll back to previous version)
3. **Fix caching bugs** (if cache corruption detected, delete `ServerState.json` and rebuild)

**Monitoring:**
- Log background refresh timing and errors to stderr
- Track dashboard load times (add timing logs)
- Monitor API rate limit errors (Linear, GitHub, YouTrack)

---

## Dependencies

### Prerequisites

- **HTMX library:** Already included in dashboard HTML (v1.9.10)
- **Cask server:** Already running with HTTP endpoints
- **Cache infrastructure:** Already exists (`ServerState.json`, `IssueCacheService`)

### Story Dependencies

**Sequential dependencies:**
- Story 1 must complete before Story 2 (render from cache before adding refresh)
- Story 2 must complete before Story 3 (background refresh before incremental updates)
- Story 4 can be done in parallel with Story 1 (both touch caching logic)
- Story 5 depends on Story 2 (requires background refresh to prioritize)

**Can be parallelized:**
- Story 1 and 4 (both improve caching, minimal overlap)
- Story 3 can start after Story 2 begins (different endpoints)

**Recommended sequence:**
1. Story 1 → 4 (fast cache load + aggressive caching)
2. Story 2 (background refresh)
3. Story 3 (incremental updates)
4. Story 5 (optional priority optimization)

### External Blockers

- None identified
- APIs (Linear, GitHub, YouTrack) may have rate limits, but already handled by existing cache

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Fast initial load with cache** - Establishes foundation for instant rendering, highest user value
2. **Story 4: Aggressive caching** - Builds on Story 1, ensures cache is always available for instant loads
3. **Story 2: Background refresh** - Adds async refresh after instant load is working
4. **Story 3: Incremental updates** - Polishes UX with smooth per-card updates
5. **Story 5: Visible-items-first (optional)** - Nice-to-have optimization if time permits

**Iteration Plan:**

- **Iteration 1** (Stories 1 + 4): Fast cache-based dashboard load (14-14h)
  - Deliverable: Dashboard loads instantly with cached data
  - User sees worktree cards in < 100ms
  - Subsequent loads are instant

- **Iteration 2** (Story 2): Background data refresh (8-12h)
  - Deliverable: Fresh data fetched after initial render
  - User sees "Loading..." then updated cards
  - Cache stays fresh without blocking initial load

- **Iteration 3** (Story 3): Incremental HTMX updates (6-8h)
  - Deliverable: Smooth per-card updates without page refresh
  - Professional UX with progressive enhancement
  - Mobile-friendly (no full page reloads)

- **Iteration 4 (optional)** (Story 5): Priority-based refresh (4-6h)
  - Deliverable: Most important worktrees refresh first
  - Optimized for many worktrees (10+)

**MVP:** Iterations 1 + 2 (22-26h) solves the core problem

**Full feature:** Iterations 1 + 2 + 3 (28-34h) provides polished UX

**Stretch goal:** All iterations (32-40h) includes priority optimization

---

## Documentation Requirements

- [ ] Gherkin scenarios serve as living documentation
- [ ] API documentation for new endpoints:
  - `GET /worktrees/:issueId/card` - Single card refresh
  - `GET /api/worktrees/:issueId/refresh` - Trigger background refresh
- [ ] Architecture documentation:
  - Update `docs/plans/2025-12-19-server-dashboard-design.md` with async refresh strategy
  - Document caching strategy (TTLs, stale-while-revalidate)
- [ ] User-facing docs:
  - Add note in README about dashboard performance improvements
  - Document configuration options (`IW_DASHBOARD_CACHE_TTL`, etc.)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with Michal:
   - Async refresh strategy (HTMX polling vs SSE)
   - Cache seeding approach (on registration vs lazy)
   - TTL configuration (fixed vs stale-while-revalidate)
   - Error handling strategy (silent vs visible)
2. Run `/iterative-works:ag-create-tasks IW-92` to map stories to implementation phases
3. Run `/iterative-works:ag-implement IW-92` for iterative story-by-story implementation
