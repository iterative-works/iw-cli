# Story-Driven Analysis: Dashboard caches (progressCache, prCache) never populated, causing data loss on refresh

**Issue:** IW-164
**Created:** 2026-01-26
**Status:** Draft
**Classification:** Simple

## Problem Statement

Progress bars and PR links vanish from the dashboard after HTMX card refresh. Users lose visibility into workflow progress and pull request status when cards reload.

The bug occurs because:
- Initial dashboard render reads progress and PR data from the filesystem
- Card refresh only reads from server caches (progressCache, prCache)
- These caches are never populated, so refreshed cards show no progress or PR data
- Review state works correctly because it always reads from the filesystem

Evidence from codebase:
- `~/.local/share/iw/server/state.json` shows empty caches: `progressCache: {}` and `prCache: {}`
- `WorktreeCardService.renderCard` (line 100-102) reads progress/PR from cache only
- `CardRenderResult` returned (line 127) has `None` for `fetchedProgress` and `fetchedPR`
- `DashboardService.fetchProgressForWorktree` (lines 303-328) reads from filesystem on initial render but doesn't return cached data to populate the cache

## User Stories

### Story 1: Progress bars persist across card refresh

```gherkin
Feature: Dashboard progress persistence
  As a developer
  I want to see workflow progress bars after card refresh
  So that I don't lose track of my work completion status

Scenario: Progress bar remains visible after HTMX card refresh
  Given a worktree has task files with progress data
  And the initial dashboard render shows the progress bar
  When the worktree card refreshes via HTMX
  Then the progress bar still displays the correct completion percentage
  And the progress data matches the task files on disk
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
This is a straightforward bug fix. The fix pattern already exists in the codebase (review state works correctly by reading from filesystem on refresh). We need to either:
1. Populate progress cache when reading from filesystem (simpler), OR
2. Read from filesystem on refresh like review state does (more consistent)

The key technical challenge is deciding which approach. Review state always reads from filesystem (it's cheap with mtime-based caching). Progress parsing is also relatively cheap. PR fetching from CLI is expensive, which may warrant different treatment.

**Acceptance:**
- Progress bars visible on initial render AND after card refresh
- Progress data correctly reflects task file state
- No console errors or missing data warnings
- Test validates progress persists across multiple refreshes

---

### Story 2: PR links persist across card refresh

```gherkin
Feature: Dashboard PR link persistence
  As a developer
  I want to see pull request links after card refresh
  So that I can quickly access my PR without losing the link

Scenario: PR link remains visible after HTMX card refresh
  Given a worktree has an associated pull request
  And the PR data is cached from previous CLI fetch
  When the worktree card refreshes via HTMX
  Then the PR link still displays with correct URL and state
  And no unnecessary CLI calls are made (respects cache TTL)
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
Same fix pattern as Story 1. PR cache has TTL-based validation already implemented (`CachedPR.isValid`), so we just need to ensure that when PR data is fetched (either fresh from CLI or from valid cache), it gets returned in `CardRenderResult` so the server can populate the cache.

The PR CLI call (`gh pr view` or `glab mr view`) is expensive, so caching is critical here. The existing TTL logic should be preserved.

**Acceptance:**
- PR links visible on initial render AND after card refresh
- PR state (Open/Merged/Closed) displays correctly
- Cache TTL respected (no excessive CLI calls)
- Test validates PR link persists when cache is valid

---

### Story 3: Verify review state continues working correctly

```gherkin
Feature: Review state persistence (regression test)
  As a developer
  I want review state artifacts to remain visible after card refresh
  So that I can access analysis documents and other review artifacts

Scenario: Review state persists after card refresh (regression check)
  Given a worktree has review-state.json with artifacts
  And the initial dashboard render shows review artifacts
  When the worktree card refreshes via HTMX
  Then the review artifacts still display correctly
  And the review state matches the JSON file on disk
```

**Estimated Effort:** 1h
**Complexity:** Straightforward

**Technical Feasibility:**
This story is a regression test - review state already works correctly because `WorktreeCardService.renderCard` (lines 96-113) always reads from filesystem with mtime-based caching and returns the fetched data in `CardRenderResult` (line 127 as `reviewStateCacheUpdate`).

This story documents and tests the working pattern that Stories 1-2 should follow.

**Acceptance:**
- Review state artifacts remain visible after refresh
- No regressions introduced by fixes to progress/PR caching
- Test validates the working filesystem-read pattern

## Architectural Sketch

**Purpose:** Identify WHAT components are involved in cache population, not HOW to fix them.

### For Story 1: Progress bar persistence

**Model Layer (Pure Domain):**
- `WorkflowProgress` - Domain type for progress data
- `CachedProgress` - Cached progress wrapper with mtime validation
- `PhaseInfo` - Individual phase data with task counts

**Dashboard Services:**
- `WorktreeCardService.renderCard` - Renders single card, needs to return fetched progress
- `WorkflowProgressService.fetchProgress` - Fetches progress from filesystem with caching
- `DashboardService.fetchProgressForWorktree` - Initial render progress fetching

**Server State:**
- `ServerStateService` - Updates `progressCache` map
- `CaskServer` - `/worktrees/:issueId/card` endpoint that should populate cache

**Files:**
- `CardRenderResult` case class - Needs to carry `fetchedProgress` data
- Cache population happens at lines 150-152 in CaskServer.scala (already there, but receives `None`)

---

### For Story 2: PR link persistence

**Model Layer:**
- `PullRequestData` - Domain type for PR data
- `CachedPR` - Cached PR wrapper with TTL validation
- `PRState` - Enum (Open/Merged/Closed)

**Dashboard Services:**
- `WorktreeCardService.renderCard` - Renders single card, needs to return fetched PR
- `PullRequestCacheService.fetchPR` - Fetches PR from CLI with TTL-based caching
- `PullRequestCacheService.getCachedOnly` - Current approach (cache-only read)

**Server State:**
- `ServerStateService` - Updates `prCache` map
- `CaskServer` - `/worktrees/:issueId/card` endpoint that should populate cache

**Files:**
- `CardRenderResult` case class - Needs to carry `fetchedPR` data
- Cache population happens at lines 153-155 in CaskServer.scala (already there, but receives `None`)

---

### For Story 3: Review state regression test

**Model Layer:**
- `ReviewState` - Domain type for review state
- `CachedReviewState` - Cached review state with mtime validation
- `ReviewArtifact` - Individual artifact data

**Dashboard Services:**
- `WorktreeCardService.renderCard` - Already returns `fetchedReviewState` correctly
- `ReviewStateService.fetchReviewState` - Fetches from filesystem with mtime caching

**Server State:**
- Already working - serves as reference pattern for Stories 1-2

## Technical Risks & Uncertainties

### CLARIFY: Should progress and PR use filesystem reads or cache-only reads on refresh?

There are two distinct patterns in the codebase:

**Pattern A: Always read from filesystem (like review state)**
- Read from filesystem on every card refresh
- Use mtime-based caching to avoid re-parsing if files unchanged
- Simple, consistent behavior
- Works well for cheap operations (file reads, JSON parsing)

**Pattern B: Cache in server state, read from cache (intended for progress/PR)**
- Populate server-side cache on first read
- Card refresh reads from cache only
- Requires cache invalidation strategy
- Better for expensive operations (CLI calls, API requests)

**Questions to answer:**
1. Should progress use Pattern A (filesystem + mtime) or Pattern B (server cache)?
2. Should PR use Pattern A or Pattern B?
3. If Pattern B: When should caches be invalidated/refreshed?

**Options:**

**Option A: Both use filesystem reads (like review state)**
- Pros: Simple, consistent, no cache invalidation needed
- Cons: Filesystem I/O on every refresh (but mitigated by mtime caching)
- Best for: Progress (task file parsing is cheap)

**Option B: Both use server cache (fix existing cache population)**
- Pros: Faster card refresh (no I/O), explicit cache control
- Cons: Cache invalidation complexity, stale data risk
- Best for: PR data (CLI calls are expensive)

**Option C: Hybrid approach**
- Progress uses Pattern A (filesystem + mtime)
- PR uses Pattern B (server cache with TTL)
- Pros: Optimizes each data type appropriately
- Cons: Two different patterns to maintain

**Impact:** Affects implementation approach for both stories. Story 1 (progress) likely benefits from Pattern A. Story 2 (PR) likely needs Pattern B due to expensive CLI calls.

**Recommendation:** Option C (hybrid). Progress should follow review state pattern (filesystem + mtime). PR should use fixed server cache (populate cache in CardRenderResult).

---

### CLARIFY: Should initial dashboard render also populate caches?

Currently, `DashboardService.renderDashboard` reads progress/PR data from filesystem/CLI but doesn't populate caches. Only card refresh should populate caches (via `CardRenderResult`).

**Questions to answer:**
1. Should initial dashboard render populate caches?
2. If yes, how? (CardRenderResult pattern doesn't apply to full dashboard render)
3. If no, is initial render + first refresh acceptable UX?

**Options:**

**Option A: Initial render populates caches**
- Pros: Caches warm immediately, consistent behavior
- Cons: Requires different cache population path for initial render
- Implementation: `DashboardService.renderDashboard` would need to call `stateService.updateProgressCache`/`updatePRCache`

**Option B: Only card refresh populates caches (current design)**
- Pros: Single code path for cache population (via CardRenderResult)
- Cons: First refresh after initial render reads empty cache (acceptable if using Pattern A)
- Implementation: No changes to `DashboardService`

**Impact:** Affects whether we need to modify `DashboardService.renderDashboard` or only fix `WorktreeCardService.renderCard`.

**Recommendation:** If using Pattern A (filesystem reads), Option B is fine. If using Pattern B (server cache), Option A is better for UX.

## Total Estimates

**Story Breakdown:**
- Story 1 (Progress persistence): 2-3 hours
- Story 2 (PR persistence): 1-2 hours
- Story 3 (Review state regression test): 1 hour

**Total Range:** 4-6 hours

**Confidence:** High

**Reasoning:**
- Well-understood bug with clear root cause
- Working reference pattern exists (review state)
- Small, focused changes to existing code
- Main uncertainty is design decision (CLARIFY markers), not technical difficulty
- Testing approach is straightforward (verify data persists across refresh)
- No complex domain logic or external dependencies

The upper bound (6h) assumes Option C (hybrid approach) and Option A (initial render populates caches), which requires changes to multiple services. The lower bound (4h) assumes simpler approaches following existing patterns.

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Cache validity logic, data fetching with mocked I/O
2. **Integration Tests**: Filesystem reads, cache population, CardRenderResult construction
3. **E2E Scenario Tests**: Full dashboard render + HTMX refresh cycle

**Story-Specific Testing Notes:**

**Story 1: Progress persistence**
- Unit: `CachedProgress.isValid`, `WorkflowProgressService.fetchProgress` with mocked file I/O
- Integration: `WorktreeCardService.renderCard` populates `fetchedProgress` in CardRenderResult
- E2E: Create worktree with task files -> render dashboard -> verify progress bar -> trigger HTMX refresh -> verify progress bar still present

**Story 2: PR persistence**
- Unit: `CachedPR.isValid` with TTL validation, `PullRequestCacheService.fetchPR` with mocked CLI
- Integration: `WorktreeCardService.renderCard` populates `fetchedPR` in CardRenderResult, respects cache TTL
- E2E: Create worktree with PR -> mock `gh pr view` -> render dashboard -> verify PR link -> trigger HTMX refresh -> verify PR link still present, no duplicate CLI calls

**Story 3: Review state regression test**
- Unit: Existing tests for `ReviewStateService.fetchReviewState` should pass
- Integration: Verify `CardRenderResult.fetchedReviewState` is populated correctly
- E2E: Create worktree with review-state.json -> render dashboard -> verify artifacts -> trigger HTMX refresh -> verify artifacts still present

**Test Data Strategy:**
- Use temporary directories with real task files (`phase-01-tasks.md`, etc.)
- Mock CLI commands (`gh pr view`) with fixtures
- Use munit for Scala unit/integration tests
- Use BATS for E2E dashboard tests (if BATS framework exists)
- Fixtures: Sample task files with known completion percentages, PR JSON responses

**Regression Coverage:**
- Review state must continue working (Story 3)
- Issue cache population should not be affected
- Dashboard initial render should continue to work
- Card refresh throttling (30s) should continue to work

## Deployment Considerations

### Database Changes
None - uses JSON file storage (`state.json`)

### Configuration Changes
None required

### Rollout Strategy
- Single atomic fix (all stories together)
- No feature flags needed (bug fix)
- No incremental rollout required (local dashboard server)

### Rollback Plan
- Revert commit if data loss continues
- Check `state.json` for cache population after fix
- Monitor browser console for HTMX errors

## Dependencies

### Prerequisites
- Dashboard server must be running (`./iw dashboard`)
- Worktrees must be registered with valid paths
- Task files must exist in `project-management/issues/{issueId}/` directory
- For PR testing: `gh` or `glab` CLI available

### Story Dependencies
- Story 1 and Story 2 are independent (can be parallelized)
- Story 3 (regression test) should run after Stories 1-2 to verify no regressions

### External Blockers
None

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 3: Review state regression test** - Establish working reference pattern, ensure no regressions
2. **Story 1: Progress persistence** - Higher user impact (progress bars more visible than PR links)
3. **Story 2: PR persistence** - Lower impact but follows same fix pattern

**Iteration Plan:**

- **Iteration 1** (Story 3): Write regression tests for review state, document working pattern
- **Iteration 2** (Story 1): Apply working pattern to progress data, verify cache population
- **Iteration 3** (Story 2): Apply pattern to PR data, respect TTL, verify no excessive CLI calls

**Alternative: Single Iteration**
All three stories could be implemented together in 4-6 hours since they follow the same fix pattern. Iteration breakdown is conservative to ensure each data type works correctly before moving to the next.

## Documentation Requirements

- [ ] Code comments explaining cache population in `WorktreeCardService.renderCard`
- [ ] Update `DashboardService` comment (line 15-17) if design changes
- [ ] Document cache population strategy in `CaskServer` endpoint comments
- [ ] No user-facing docs needed (internal bug fix)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers (filesystem vs cache strategy)
2. Implement Story 3 (regression test) to establish reference pattern
3. Implement Stories 1-2 following the chosen pattern
4. Verify all three data types (progress, PR, review state) persist across refresh
