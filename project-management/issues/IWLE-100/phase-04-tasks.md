# Phase 4: Show issue details and status from tracker

**Issue:** IWLE-100
**Estimated Effort:** 6-8 hours
**Prerequisites:** Phases 1, 2, and 3 complete

## Overview

This phase adds issue tracker integration to the dashboard, displaying real issue data from Linear/YouTrack. The implementation includes TTL-based caching (5 minutes), graceful degradation on API failures, and enhanced worktree cards showing issue title, status badge, assignee, cache indicator, and clickable issue links.

---

## Setup

- [ ] Verify existing LinearClient and YouTrackClient implementations are working
- [ ] Ensure test environment has temporary directories for state.json

---

## 1. Domain Models - IssueData (30-45 min)

### RED - Write Failing Tests

- [x] [impl] [ ] [reviewed] Create test file: `.iw/core/test/IssueDataTest.scala`
- [x] [impl] [ ] [reviewed] Write test: `fromIssue` creates IssueData with correct url and timestamp
- [x] [impl] [ ] [reviewed] Write test: IssueData preserves all Issue fields (id, title, status, assignee, description)
- [x] [impl] [ ] [reviewed] Write test: fetchedAt timestamp is set to provided Instant
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests fail (no IssueData yet)

### GREEN - Implement IssueData

- [x] [impl] [ ] [reviewed] Create domain model: `.iw/core/IssueData.scala`
- [x] [impl] [ ] [reviewed] Implement IssueData case class with fields: id, title, status, assignee, description, url, fetchedAt
- [x] [impl] [ ] [reviewed] Implement `IssueData.fromIssue(issue: Issue, url: String, fetchedAt: Instant): IssueData`
- [ ] [impl] [ ] [reviewed] Add upickle ReadWriter for JSON serialization
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify IssueData tests pass

### REFACTOR - Clean Up

- [x] [impl] [ ] [reviewed] Add scaladoc comments to IssueData and fromIssue method
- [x] [impl] [ ] [reviewed] Ensure immutability (all fields are vals)
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests still pass

**Success Criteria:**
- IssueData correctly wraps Issue with URL and timestamp
- Factory method creates IssueData from Issue
- JSON serialization works correctly
- All fields properly preserved

---

## 2. Domain Models - CachedIssue (45-60 min)

### RED - Write Failing Tests

- [x] [impl] [ ] [reviewed] Create test file: `.iw/core/test/CachedIssueTest.scala`
- [x] [impl] [ ] [reviewed] Write test: `isValid` returns true for fresh cache (2 minutes ago, TTL 5)
- [x] [impl] [ ] [reviewed] Write test: `isValid` returns false for expired cache (6 minutes ago, TTL 5)
- [x] [impl] [ ] [reviewed] Write test: `isValid` returns true at exactly TTL boundary (5 minutes ago, TTL 5)
- [x] [impl] [ ] [reviewed] Write test: `age` calculates duration correctly (3 minutes = 180 seconds)
- [x] [impl] [ ] [reviewed] Write test: Default TTL is 5 minutes
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests fail

### GREEN - Implement CachedIssue

- [x] [impl] [ ] [reviewed] Create domain model: `.iw/core/CachedIssue.scala`
- [x] [impl] [ ] [reviewed] Implement CachedIssue case class with: data (IssueData), ttlMinutes (default 5)
- [x] [impl] [ ] [reviewed] Implement `CachedIssue.isValid(cached: CachedIssue, now: Instant): Boolean`
- [x] [impl] [ ] [reviewed] Implement `CachedIssue.age(cached: CachedIssue, now: Instant): Duration`
- [ ] [impl] [ ] [reviewed] Add upickle ReadWriter for JSON serialization
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify CachedIssue tests pass

### REFACTOR - Clean Up

- [x] [impl] [ ] [reviewed] Extract TTL default (5 minutes) as constant
- [x] [impl] [ ] [reviewed] Add scaladoc comments explaining cache validation logic
- [x] [impl] [ ] [reviewed] Ensure all functions are pure (no side effects)
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests still pass

**Success Criteria:**
- Cache validation correctly checks TTL expiry
- Age calculation returns accurate duration
- Default TTL is 5 minutes as specified
- Pure functions with no side effects

---

## 3. Application Layer - IssueCacheService (90-120 min)

### RED - Write Failing Tests

- [x] [impl] [ ] [reviewed] Create test file: `.iw/core/test/IssueCacheServiceTest.scala`
- [x] [impl] [ ] [reviewed] Write test: `fetchWithCache` uses valid cache (2 min ago, no API call)
- [x] [impl] [ ] [reviewed] Write test: Verify fetchFn NOT called when cache is valid
- [x] [impl] [ ] [reviewed] Write test: `fetchWithCache` refreshes expired cache (6 min ago, API called)
- [x] [impl] [ ] [reviewed] Write test: Fresh data returned when cache expired and API succeeds
- [x] [impl] [ ] [reviewed] Write test: Stale cache returned when API fails but cache exists
- [x] [impl] [ ] [reviewed] Write test: fromCache=true when stale fallback used
- [x] [impl] [ ] [reviewed] Write test: Error returned when no cache and API fails
- [x] [impl] [ ] [reviewed] Write test: `buildIssueUrl` generates Linear URL correctly
- [x] [impl] [ ] [reviewed] Write test: `buildIssueUrl` generates YouTrack URL correctly with baseUrl
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests fail

### GREEN - Implement IssueCacheService

- [x] [impl] [ ] [reviewed] Create service: `.iw/core/IssueCacheService.scala`
- [x] [impl] [ ] [reviewed] Implement `fetchWithCache(issueId, cache, now, fetchFn, urlBuilder): Either[String, (IssueData, Boolean)]`
- [x] [impl] [ ] [reviewed] Check cache validity using `CachedIssue.isValid`
- [x] [impl] [ ] [reviewed] Return cached data if valid (no API call)
- [x] [impl] [ ] [reviewed] Call fetchFn if cache expired or missing
- [x] [impl] [ ] [reviewed] On API success: create IssueData with URL and timestamp, return with fromCache=false
- [x] [impl] [ ] [reviewed] On API failure: return stale cache if exists with fromCache=true, otherwise return error
- [x] [impl] [ ] [reviewed] Implement `buildIssueUrl(issueId, trackerType, baseUrl): String`
- [x] [impl] [ ] [reviewed] Linear URL format: Extract team from issueId (e.g., IWLE-123 → IWLE), construct linear.app URL
- [x] [impl] [ ] [reviewed] YouTrack URL format: `{baseUrl}/issue/{issueId}`
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify IssueCacheService tests pass

### REFACTOR - Improve Quality

- [x] [impl] [ ] [reviewed] Extract URL patterns to constants or helper functions
- [x] [impl] [ ] [reviewed] Add detailed scaladoc explaining cache fallback logic
- [x] [impl] [ ] [reviewed] Ensure all functions are pure (receive `now` as parameter)
- [x] [impl] [ ] [reviewed] Add error context to Left(error) messages for debugging
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- Valid cache used without API call
- Expired cache triggers API refresh
- Stale cache fallback works on API failure
- Error returned only when no cache and API fails
- URL construction correct for both Linear and YouTrack

---

## 4. State Repository Extension (30-45 min)

### RED - Extend StateRepository Tests

- [x] [impl] [ ] [reviewed] Add to existing `.iw/core/test/StateRepositoryTest.scala`
- [x] [impl] [ ] [reviewed] Write test: Serialize ServerState with issueCache map
- [x] [impl] [ ] [reviewed] Write test: Deserialize ServerState with issueCache correctly
- [x] [impl] [ ] [reviewed] Write test: Empty issueCache serializes as empty map
- [x] [impl] [ ] [reviewed] Write test: Multiple cached issues serialize/deserialize correctly
- [x] [impl] [ ] [reviewed] Write test: Instant timestamps preserved in serialization
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests fail

### GREEN - Extend ServerState

- [x] [impl] [ ] [reviewed] Modify `.iw/core/ServerState.scala`:
- [x] [impl] [ ] [reviewed] Add field: `issueCache: Map[String, CachedIssue] = Map.empty`
- [x] [impl] [ ] [reviewed] Ensure upickle ReadWriter includes issueCache field
- [x] [impl] [ ] [reviewed] Add implicit ReadWriter for IssueData in StateRepository
- [x] [impl] [ ] [reviewed] Add implicit ReadWriter for CachedIssue in StateRepository
- [x] [impl] [ ] [reviewed] Add implicit ReadWriter for Instant (ISO-8601 format)
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify state serialization tests pass

### REFACTOR - Clean Up

- [x] [impl] [ ] [reviewed] Verify existing state.json files can still load (backward compatibility)
- [x] [impl] [ ] [reviewed] Ensure atomic writes still work with larger state (cache adds ~2KB per issue)
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- ServerState includes issueCache field
- JSON serialization/deserialization works correctly
- Timestamps preserved accurately
- Backward compatible with existing state.json files

---

## 5. Dashboard Integration - Fetch Issue Data (60-90 min)

### RED - Write Integration Tests

- [ ] [impl] [ ] [reviewed] Create test file: `.iw/core/test/DashboardServiceTest.scala` (if doesn't exist)
- [ ] [impl] [ ] [reviewed] Write test: `renderDashboard` includes issue title for cached issue
- [ ] [impl] [ ] [reviewed] Write test: Issue status appears on card
- [ ] [impl] [ ] [reviewed] Write test: Issue URL is clickable link
- [ ] [impl] [ ] [reviewed] Write test: "Issue data unavailable" shown when no cache and API fails
- [ ] [impl] [ ] [reviewed] Write test: Stale cache displayed when API fails (graceful degradation)
- [ ] [impl] [ ] [reviewed] Write test: Mixed Linear and YouTrack worktrees handled correctly
- [ ] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests fail

### GREEN - Modify DashboardService

- [ ] [impl] [ ] [reviewed] Modify `.iw/core/DashboardService.scala`:
- [ ] [impl] [ ] [reviewed] Add parameter `issueCache: Map[String, CachedIssue]` to renderDashboard
- [ ] [impl] [ ] [reviewed] Add parameter `config: Config` for tracker type and API tokens
- [ ] [impl] [ ] [reviewed] Implement `fetchIssueForWorktree(wt, cache, now, config): Option[(IssueData, Boolean)]`
- [ ] [impl] [ ] [reviewed] Build fetchFn that wraps LinearClient.fetchIssue or YouTrackClient.fetchIssue based on trackerType
- [ ] [impl] [ ] [reviewed] Build urlBuilder using IssueCacheService.buildIssueUrl
- [ ] [impl] [ ] [reviewed] Call `IssueCacheService.fetchWithCache` for each worktree
- [ ] [impl] [ ] [reviewed] Map worktrees to (WorktreeRegistration, Option[(IssueData, Boolean)])
- [ ] [impl] [ ] [reviewed] Pass issue data to WorktreeListView.render
- [ ] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify dashboard integration tests pass

### GREEN - Update CaskServer Route

- [ ] [impl] [ ] [reviewed] Modify `.iw/core/CaskServer.scala` GET / route:
- [ ] [impl] [ ] [reviewed] Load config from ConfigRepository
- [ ] [impl] [ ] [reviewed] Pass issueCache from state to DashboardService
- [ ] [impl] [ ] [reviewed] Pass config to DashboardService
- [ ] [impl] [ ] [reviewed] Run manual test: Start server, verify dashboard renders without errors

### REFACTOR - Error Handling

- [ ] [impl] [ ] [reviewed] Add logging for API failures (show in console/log file)
- [ ] [impl] [ ] [reviewed] Ensure API failures don't crash dashboard rendering
- [ ] [impl] [ ] [reviewed] Handle missing API tokens gracefully (show "unavailable" message)
- [ ] [impl] [ ] [reviewed] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- DashboardService fetches issue data for each worktree
- API calls use correct client (Linear vs YouTrack)
- Cache is checked before API calls
- Errors handled gracefully (no crashes)
- Dashboard renders with issue data

---

## 6. Presentation Layer - WorktreeListView Enhancement (90-120 min)

### RED - Write View Tests

- [ ] [impl] [ ] [reviewed] Add to existing WorktreeListView tests (or create if missing)
- [ ] [impl] [ ] [reviewed] Write test: Issue title appears in card when issueData present
- [ ] [impl] [ ] [reviewed] Write test: "Issue data unavailable" shown when issueData is None
- [ ] [impl] [ ] [reviewed] Write test: Status badge rendered with correct CSS class
- [ ] [impl] [ ] [reviewed] Write test: Cache indicator shows "cached Xm ago" when fromCache=true
- [ ] [impl] [ ] [reviewed] Write test: No cache indicator when fromCache=false (fresh data)
- [ ] [impl] [ ] [reviewed] Write test: Issue ID is clickable link with correct URL
- [ ] [impl] [ ] [reviewed] Write test: Assignee displayed if present
- [ ] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests fail

### GREEN - Enhance WorktreeListView

- [ ] [impl] [ ] [reviewed] Modify `.iw/core/WorktreeListView.scala`:
- [ ] [impl] [ ] [reviewed] Update `renderWorktreeCard` signature: add `issueData: Option[(IssueData, Boolean)]` parameter
- [ ] [impl] [ ] [reviewed] Add `now: Instant` parameter for cache age calculation
- [ ] [impl] [ ] [reviewed] Show issue title: `issueData.map(_._1.title).getOrElse("Issue data unavailable")`
- [ ] [impl] [ ] [reviewed] Make issue ID a clickable link: `a(href := issueData.map(_._1.url).getOrElse("#"))(wt.issueId)`
- [ ] [impl] [ ] [reviewed] Render status badge: `span(cls := s"status-badge status-${statusClass(data.status)}")(data.status)`
- [ ] [impl] [ ] [reviewed] Implement `statusClass(status: String): String` helper (in-progress, done, blocked, default)
- [ ] [impl] [ ] [reviewed] Show assignee if present: `data.assignee.map(a => span(cls := "assignee")(s"Assigned: $a"))`
- [ ] [impl] [ ] [reviewed] Show cache indicator if fromCache=true: `span(cls := "cache-indicator")(s"cached ${formatCacheAge(data.fetchedAt, now)}")`
- [ ] [impl] [ ] [reviewed] Implement `formatCacheAge(fetchedAt: Instant, now: Instant): String` helper
- [ ] [impl] [ ] [reviewed] Format: < 1min → "just now", < 60min → "Xm ago", < 24h → "Xh ago", else → "Xd ago"
- [ ] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify view tests pass

### GREEN - Add Styling

- [ ] [impl] [ ] [reviewed] Add CSS for status badges in WorktreeListView inline styles:
- [ ] [impl] [ ] [reviewed] `.status-badge` base style (padding, border-radius, font-size)
- [ ] [impl] [ ] [reviewed] `.status-in-progress` (yellow background: #ffd43b, black text)
- [ ] [impl] [ ] [reviewed] `.status-done` (green background: #51cf66, white text)
- [ ] [impl] [ ] [reviewed] `.status-blocked` (red background: #ff6b6b, white text)
- [ ] [impl] [ ] [reviewed] `.status-default` (gray background: #adb5bd, white text)
- [ ] [impl] [ ] [reviewed] `.cache-indicator` style (smaller font, gray color: #868e96)
- [ ] [impl] [ ] [reviewed] `.issue-id a` link style (blue: #228be6, no underline, underline on hover)
- [ ] [impl] [ ] [reviewed] `.assignee` style (if needed)
- [ ] [impl] [ ] [reviewed] Run manual test: View dashboard, verify styling looks correct

### REFACTOR - Clean Up View Code

- [ ] [impl] [ ] [reviewed] Extract status color mapping to constant map or separate function
- [ ] [impl] [ ] [reviewed] Ensure time formatting is consistent with existing code (lastSeenAt)
- [ ] [impl] [ ] [reviewed] Add scaladoc comments to helper functions
- [ ] [impl] [ ] [reviewed] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- Issue title displayed prominently on card
- Status badge shows with correct color coding
- Assignee name appears if present
- Cache timestamp displays in human-readable format
- Issue ID is clickable link to tracker
- "Issue data unavailable" shows gracefully when no data
- Styling matches design intent (professional, readable)

---

## 7. Cache State Update After Fetch (30-45 min)

### RED - Write Tests

- [ ] [impl] [ ] [reviewed] Add test to DashboardServiceTest or ServerStateServiceTest
- [ ] [impl] [ ] [reviewed] Write test: Fresh issue data updates cache in state
- [ ] [impl] [ ] [reviewed] Write test: Cache timestamp reflects fetch time
- [ ] [impl] [ ] [reviewed] Write test: Multiple issues cached simultaneously
- [ ] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests fail

### GREEN - Implement Cache Update

- [ ] [impl] [ ] [reviewed] Modify DashboardService or create CacheUpdateService:
- [ ] [impl] [ ] [reviewed] After fetching issue data, build updated issueCache map
- [ ] [impl] [ ] [reviewed] For fresh fetches (fromCache=false), add/update CachedIssue in map
- [ ] [impl] [ ] [reviewed] Write updated state to StateRepository
- [ ] [impl] [ ] [reviewed] Ensure atomic write (no race conditions)
- [ ] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify cache update tests pass

### GREEN - Integrate with CaskServer

- [ ] [impl] [ ] [reviewed] Modify `.iw/core/CaskServer.scala` GET / route:
- [ ] [impl] [ ] [reviewed] After rendering dashboard, collect fresh issue data
- [ ] [impl] [ ] [reviewed] Update state.issueCache with fresh data
- [ ] [impl] [ ] [reviewed] Save updated state via stateRepository.write
- [ ] [impl] [ ] [reviewed] Run manual test: Load dashboard, verify state.json updated with issue cache

### REFACTOR - Optimize

- [ ] [impl] [ ] [reviewed] Only write state if cache actually changed (avoid unnecessary I/O)
- [ ] [impl] [ ] [reviewed] Ensure error handling: cache update failure doesn't crash dashboard
- [ ] [impl] [ ] [reviewed] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- Fresh issue data persisted to state.json
- Cache timestamps reflect actual fetch time
- State file correctly updated after dashboard load
- No crashes if cache update fails

---

## 8. Integration Testing (60-90 min)

### Manual E2E Scenarios

- [ ] [impl] [ ] [reviewed] **Scenario 1: Fresh issue data displayed**
  - Clear issue cache from state.json
  - Register worktree (IWLE-123)
  - Load dashboard
  - Verify: Issue title from Linear/YouTrack appears
  - Verify: Status badge shows with correct color
  - Verify: Assignee name displayed
  - Verify: "cached just now" indicator shown

- [ ] [impl] [ ] [reviewed] **Scenario 2: Cached data used (no API call)**
  - Load dashboard (populates cache)
  - Wait 2 minutes
  - Reload dashboard
  - Verify: Same issue data shown
  - Verify: "cached 2m ago" displayed
  - Verify: No new API call made (check logs)

- [ ] [impl] [ ] [reviewed] **Scenario 3: Cache refresh after TTL expiry**
  - Load dashboard (populates cache)
  - Manually edit state.json to set fetchedAt to 10 minutes ago
  - Reload dashboard
  - Verify: Fresh API call made
  - Verify: "cached just now" appears
  - Verify: Updated issue data shown if changed in tracker

- [ ] [impl] [ ] [reviewed] **Scenario 4: Stale cache used on API failure**
  - Load dashboard (populates cache)
  - Invalidate API token or disable network
  - Wait 6 minutes (expire cache)
  - Reload dashboard
  - Verify: Stale issue data still displayed
  - Verify: Cache indicator shows age (e.g., "cached 6m ago")
  - Verify: Dashboard doesn't crash

- [ ] [impl] [ ] [reviewed] **Scenario 5: Clickable issue links**
  - Load dashboard with registered worktrees
  - Click on issue ID link
  - Verify: Opens Linear/YouTrack in new tab (or current tab)
  - Verify: Correct issue page loaded

- [ ] [impl] [ ] [reviewed] **Scenario 6: Mixed Linear and YouTrack worktrees**
  - Register worktree with Linear issue
  - Register worktree with YouTrack issue
  - Load dashboard
  - Verify: Both issues displayed correctly
  - Verify: Links go to correct tracker

### Edge Cases

- [ ] [impl] [ ] [reviewed] Test: Missing API token shows "Issue data unavailable"
- [ ] [impl] [ ] [reviewed] Test: Invalid issue ID (404 from API) shows error message
- [ ] [impl] [ ] [reviewed] Test: Corrupted cache entry handled gracefully
- [ ] [impl] [ ] [reviewed] Test: Very old cache (30 days) still used on API failure
- [ ] [impl] [ ] [reviewed] Test: Empty assignee field (None) doesn't break rendering

### Unit Test Pass

- [ ] [impl] [ ] [reviewed] Run full unit test suite: `./iw test unit`
- [ ] [impl] [ ] [reviewed] Verify all tests pass (IssueData, CachedIssue, IssueCacheService, StateRepository, DashboardService, WorktreeListView)
- [ ] [impl] [ ] [reviewed] Fix any failing tests
- [ ] [impl] [ ] [reviewed] Verify no new compilation warnings

**Success Criteria:**
- All manual scenarios pass
- Edge cases handled gracefully
- All unit tests pass
- No regressions in existing functionality

---

## 9. Documentation (30-45 min)

- [ ] [doc] [ ] [reviewed] Update `project-management/issues/IWLE-100/implementation-log.md`:
  - Add Phase 4 summary
  - Document cache TTL (5 minutes) and fallback behavior
  - Note any design decisions or trade-offs
  - List files created/modified

- [ ] [doc] [ ] [reviewed] Add comments to complex logic:
  - Cache validation logic in CachedIssue
  - Stale cache fallback in IssueCacheService
  - URL construction patterns (Linear team extraction)

- [ ] [doc] [ ] [reviewed] Ensure scaladoc is complete for:
  - IssueData.fromIssue
  - CachedIssue.isValid, CachedIssue.age
  - IssueCacheService.fetchWithCache, IssueCacheService.buildIssueUrl

- [ ] [doc] [ ] [reviewed] Update tasks.md: Mark Phase 4 as complete

**Success Criteria:**
- Implementation log updated with Phase 4 summary
- Complex logic has explanatory comments
- Public APIs have scaladoc
- Phase 4 marked complete in task index

---

## Final Checklist

### Implementation Complete

- [ ] [impl] [ ] [reviewed] All domain models implemented (IssueData, CachedIssue)
- [ ] [impl] [ ] [reviewed] IssueCacheService handles all cache scenarios correctly
- [ ] [impl] [ ] [reviewed] ServerState extended with issueCache field
- [ ] [impl] [ ] [reviewed] DashboardService fetches and passes issue data
- [ ] [impl] [ ] [reviewed] WorktreeListView enhanced with issue details and styling
- [ ] [impl] [ ] [reviewed] Cache persisted and updated in state.json

### Testing Complete

- [ ] [impl] [ ] [reviewed] All unit tests passing
- [ ] [impl] [ ] [reviewed] All manual E2E scenarios verified
- [ ] [impl] [ ] [reviewed] Edge cases handled gracefully
- [ ] [impl] [ ] [reviewed] No regressions in existing features

### Quality Checks

- [ ] [impl] [ ] [reviewed] Code follows FCIS pattern (pure domain/application, effects in infrastructure)
- [ ] [impl] [ ] [reviewed] Timestamps injected via `now` parameter (not generated in pure functions)
- [ ] [impl] [ ] [reviewed] Cache fallback logic correct (stale data > no data)
- [ ] [impl] [ ] [reviewed] Error messages user-friendly and actionable
- [ ] [impl] [ ] [reviewed] No new compilation warnings
- [ ] [impl] [ ] [reviewed] All scaladoc comments complete

### Documentation Complete

- [ ] [doc] [ ] [reviewed] Implementation log updated
- [ ] [doc] [ ] [reviewed] Complex logic commented
- [ ] [doc] [ ] [reviewed] Phase 4 marked complete in tasks.md

---

## Notes

**TTL Configuration:**
- Issue cache TTL: 5 minutes (as specified in analysis)
- Balances freshness with API rate limits

**Graceful Degradation:**
- Prefer stale data over no data (better UX)
- Show cache age to user for transparency
- Log errors for debugging without crashing dashboard

**URL Construction:**
- Linear: Extract team from issue ID (e.g., IWLE-123 → IWLE), construct linear.app URL
- YouTrack: Use base URL from config + issue ID

**State File Growth:**
- Typical: 5-10 worktrees × ~2KB per cached issue = 10-20KB
- No pruning in Phase 4 (defer to Phase 7 if needed)

---

**Phase Status:** Ready for Implementation

**Next Steps:**
1. Begin with Domain Models (tasks 1-2)
2. Implement Application Layer (task 3)
3. Extend Infrastructure (task 4)
4. Integrate with Dashboard (tasks 5-7)
5. Test thoroughly (task 8)
6. Document (task 9)
