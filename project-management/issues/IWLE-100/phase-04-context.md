# Phase 4 Context: Show issue details and status from tracker

**Issue:** IWLE-100
**Phase:** 4 of 7
**Story:** Story 3 - Show issue details and status from tracker
**Estimated Effort:** 6-8 hours
**Created:** 2025-12-20

---

## Goals

This phase adds issue tracker integration to the dashboard, displaying real issue data from Linear/YouTrack. The primary objectives are:

1. **Issue data fetching**: Retrieve issue details (title, status, assignee, URL) from Linear/YouTrack APIs
2. **Cache management**: Implement TTL-based caching to avoid excessive API calls
3. **Dashboard enhancement**: Update worktree cards to show issue title, status, and tracker link
4. **Graceful degradation**: Handle API failures by showing stale cache with warning
5. **Cache timestamp display**: Show "cached Xm ago" indicator for transparency
6. **Background refresh**: Refresh expired cache without blocking dashboard render

After this phase, developers will see actual issue information on the dashboard instead of placeholders, providing better context for each worktree.

---

## Scope

### In Scope

**Issue Data Model:**
- `IssueData` domain object (title, status, assignee, url, fetchedAt timestamp)
- Extends existing `Issue` model with URL and cache timestamp
- URL construction from tracker type and issue ID

**Cache Management:**
- `CachedIssue` wrapper (issue data + fetchedAt timestamp)
- TTL: 5 minutes for issue data (as specified in analysis)
- Store cached data in `state.json` (extend ServerState)
- Cache invalidation on TTL expiry
- Stale cache display if API call fails

**Issue Fetching Service:**
- `IssueCacheService` application layer service
- Fetch with TTL: check cache age, refresh if expired
- Reuse existing `LinearClient.fetchIssue()` and `YouTrackClient.fetchIssue()`
- Return Either[String, IssueData] for error handling
- Inject timestamp for FCIS compliance

**State Repository Extension:**
- Add `issueCache: Map[String, CachedIssue]` to ServerState
- Persist/deserialize issue cache in state.json
- Atomic writes continue to work (already implemented)

**Dashboard Enhancements:**
- Update `WorktreeListView` to display issue title instead of placeholder
- Show status badge (color-coded based on status text)
- Add clickable link to Linear/YouTrack issue
- Display cache timestamp ("cached 3m ago")
- Show warning icon if using stale cache due to API failure

**API Integration:**
- No new API endpoints needed
- Dashboard rendering calls `IssueCacheService.fetchWithCache()` for each worktree
- Service handles cache check, API call if needed, error handling

### Out of Scope

**Not in Phase 4 (deferred to later phases):**
- Phase/task progress parsing (Phase 5)
- Git status detection (Phase 6)
- PR link detection (Phase 6)
- Unregister endpoint and auto-pruning (Phase 7)
- Background refresh daemon (cache refresh happens on dashboard load)
- Issue webhook integration (polling only)
- Issue assignment from dashboard (read-only)
- Custom field extraction beyond status/assignee (YAGNI)

**Technical Scope Boundaries:**
- Cache storage: Embedded in state.json, no separate cache file
- Cache eviction: Simple TTL, no LRU or size limits (worktree count is low)
- Concurrency: No explicit locking (state.json atomic writes sufficient)
- Error display: Text warnings only, no retry UI in Phase 4

---

## Dependencies

### Prerequisites from Phase 1, 2, 3

**Must exist and work correctly:**
- `CaskServer` with dashboard rendering on `GET /`
- `ServerState` domain model with worktree map
- `StateRepository` for JSON persistence with atomic writes
- `WorktreeListView` Scalatags template for worktree cards
- `WorktreeRegistration` with issue ID, path, tracker type, team
- `ServerStateService` for state load/save operations
- `DashboardService` for dashboard HTML generation
- `LinearClient.fetchIssue(issueId, token)` returning `Either[String, Issue]`
- `YouTrackClient.fetchIssue(issueId, baseUrl, token)` returning `Either[String, Issue]`
- `Issue` domain model (id, title, status, assignee, description)
- Configuration loading from `.iw/config.conf` (tracker type, API tokens)

**Available for reuse:**
- sttp HTTP client (used by LinearClient/YouTrackClient)
- upickle JSON serialization (used by StateRepository)
- Existing error handling patterns (Either-based)
- Configuration data: trackerType, LINEAR_API_TOKEN, YOUTRACK_BASE_URL, YOUTRACK_API_TOKEN

**Configuration data available:**
- `config.trackerType`: Linear or YouTrack enum
- `config.linear.apiToken`: LINEAR_API_TOKEN environment variable
- `config.youtrack.baseUrl`: YOUTRACK_BASE_URL
- `config.youtrack.apiToken`: YOUTRACK_API_TOKEN
- Issue ID embedded in WorktreeRegistration

### External Dependencies

**No new dependencies required:**
- sttp.client4 already in project.scala
- upickle already in project.scala
- java.time.Instant for timestamps (JDK standard library)

**External APIs:**
- Linear GraphQL API: https://api.linear.app/graphql
- YouTrack REST API: {baseUrl}/api/issues/{issueId}

---

## Technical Approach

### High-Level Strategy

Phase 4 follows the **Functional Core / Imperative Shell** pattern:

**Domain Layer (Pure):**
- `IssueData` case class (Issue + url + fetchedAt)
- `CachedIssue` case class (IssueData + TTL validation logic)
- URL construction from tracker type and issue ID

**Application Layer (Pure):**
- `IssueCacheService` with pure functions:
  - `fetchWithCache(issueId, cache, now, fetchFn): Either[String, (IssueData, Boolean)]`
  - `isCacheValid(cached, now, ttl): Boolean`
  - `buildIssueUrl(issueId, trackerType, baseUrl): String`
- Pure logic receives `now: Instant` from callers (FCIS)
- Returns `(IssueData, Boolean)` where Boolean = cacheWasUsed

**Infrastructure Layer (Effects):**
- `StateRepository` extension to serialize/deserialize issue cache
- `LinearClient.fetchIssue()` reused (already exists)
- `YouTrackClient.fetchIssue()` reused (already exists)

**Presentation Layer:**
- `DashboardService` calls `IssueCacheService` for each worktree
- `WorktreeListView` enhanced to render issue data
- Cache timestamp formatting ("3m ago", "1h ago", "2d ago")

### Architecture Overview

```
DashboardService (Presentation)
       ↓
   IssueCacheService (Application)
       ↓
   Check cache validity
       ↓
   [Cache valid?] → Yes → Return cached IssueData
       ↓ No
   Call fetchFn (LinearClient/YouTrackClient)
       ↓
   [API success?] → Yes → Return fresh IssueData + update cache
       ↓ No
   [Has stale cache?] → Yes → Return stale IssueData + warning
       ↓ No
   Return error
```

**Error handling philosophy:**
- Prefer stale data over no data (better UX)
- Show warnings but keep dashboard functional
- Log API errors for debugging
- Graceful fallback: "Issue data unavailable"

### Key Components

#### 1. IssueData (Domain Layer)

**File:** `.iw/core/IssueData.scala`

**Purpose:** Extended issue model with URL and cache metadata.

**Interface:**
```scala
case class IssueData(
  id: String,
  title: String,
  status: String,
  assignee: Option[String],
  description: Option[String],
  url: String,
  fetchedAt: java.time.Instant
)

object IssueData:
  def fromIssue(issue: Issue, url: String, fetchedAt: Instant): IssueData
```

**Implementation notes:**
- Simple data class, no business logic
- `fromIssue` factory for conversion from Issue model
- URL is tracker-specific (Linear vs YouTrack format)

#### 2. CachedIssue (Domain Layer)

**File:** `.iw/core/CachedIssue.scala`

**Purpose:** Cache wrapper with TTL validation.

**Interface:**
```scala
case class CachedIssue(
  data: IssueData,
  ttlMinutes: Int = 5
)

object CachedIssue:
  def isValid(cached: CachedIssue, now: Instant): Boolean =
    val age = java.time.Duration.between(cached.data.fetchedAt, now)
    age.toMinutes < cached.ttlMinutes
  
  def age(cached: CachedIssue, now: Instant): java.time.Duration =
    java.time.Duration.between(cached.data.fetchedAt, now)
```

**Implementation notes:**
- Pure functions for cache validation
- TTL default: 5 minutes (from analysis)
- Age calculation for "cached 3m ago" display

#### 3. IssueCacheService (Application Layer)

**File:** `.iw/core/IssueCacheService.scala`

**Purpose:** Pure business logic for cache-aware issue fetching.

**Interface:**
```scala
object IssueCacheService:
  /** Fetch issue with cache support.
    * Returns (IssueData, fromCache: Boolean) where fromCache indicates if cached data was used.
    * If API fails but stale cache exists, returns stale data with fromCache=true.
    */
  def fetchWithCache(
    issueId: String,
    cache: Map[String, CachedIssue],
    now: Instant,
    fetchFn: String => Either[String, Issue],
    urlBuilder: String => String
  ): Either[String, (IssueData, Boolean)]
  
  /** Build issue URL from tracker type and issue ID */
  def buildIssueUrl(issueId: String, trackerType: String, baseUrl: Option[String]): String
```

**Implementation logic for `fetchWithCache`:**
```scala
1. Check if issueId in cache
2. If in cache && CachedIssue.isValid(cached, now):
     Return (cached.data, fromCache=true)
3. Else:
     Call fetchFn(issueId) to fetch fresh data
     Match result:
       Success(issue):
         url = urlBuilder(issueId)
         issueData = IssueData.fromIssue(issue, url, now)
         Return (issueData, fromCache=false)
       Failure(error):
         If cache exists (stale):
           Return (cached.data, fromCache=true) with warning logged
         Else:
           Return Left(error)
```

**Implementation notes:**
- Completely pure: receives `now` and `fetchFn` from caller
- Returns tuple `(IssueData, Boolean)` for caller to know cache status
- No I/O, no side effects
- Caller (DashboardService) provides fetchFn that wraps LinearClient/YouTrackClient

#### 4. ServerState Extension

**File:** `.iw/core/ServerState.scala` (modify existing)

**Changes:**
```scala
case class ServerState(
  worktrees: Map[String, WorktreeRegistration],
  issueCache: Map[String, CachedIssue] = Map.empty  // NEW
)
```

**JSON format (state.json):**
```json
{
  "worktrees": { /* existing */ },
  "issueCache": {
    "IWLE-123": {
      "data": {
        "id": "IWLE-123",
        "title": "Add user authentication",
        "status": "In Progress",
        "assignee": "Jane Doe",
        "description": "...",
        "url": "https://linear.app/team/issue/IWLE-123",
        "fetchedAt": "2025-12-20T10:30:00Z"
      },
      "ttlMinutes": 5
    }
  }
}
```

**Implementation notes:**
- StateRepository already handles serialization via upickle
- Add ReadWriter for CachedIssue and IssueData
- Atomic writes continue to work (no changes to StateRepository)

#### 5. DashboardService Integration

**File:** `.iw/core/DashboardService.scala` (modify existing)

**Changes:**
```scala
object DashboardService:
  def renderDashboard(
    worktrees: List[WorktreeRegistration],
    issueCache: Map[String, CachedIssue],
    config: Config  // For tracker type, API tokens
  ): Tag =
    val now = Instant.now()
    
    val worktreesWithIssues = worktrees.map { wt =>
      val issueData = fetchIssueForWorktree(wt, issueCache, now, config)
      (wt, issueData)
    }
    
    WorktreeListView.render(worktreesWithIssues, now)
  
  private def fetchIssueForWorktree(
    wt: WorktreeRegistration,
    cache: Map[String, CachedIssue],
    now: Instant,
    config: Config
  ): Option[(IssueData, Boolean)] =
    val fetchFn = buildFetchFunction(wt.trackerType, config)
    val urlBuilder = buildUrlBuilder(wt.trackerType, config)
    
    IssueCacheService.fetchWithCache(
      wt.issueId,
      cache,
      now,
      fetchFn,
      urlBuilder
    ).toOption
```

**Implementation notes:**
- Calls IssueCacheService for each worktree
- Builds fetch function that wraps LinearClient/YouTrackClient
- Passes result to WorktreeListView for rendering
- Handles errors by showing "Issue data unavailable" on card

#### 6. WorktreeListView Enhancement

**File:** `.iw/core/WorktreeListView.scala` (modify existing)

**Changes:**
```scala
def renderWorktreeCard(
  wt: WorktreeRegistration,
  issueData: Option[(IssueData, Boolean)],
  now: Instant
): Tag =
  div(cls := "worktree-card")(
    h3(issueData.map(_._1.title).getOrElse("Issue data unavailable")),
    p(cls := "issue-id")(
      a(href := issueData.map(_._1.url).getOrElse("#"))(wt.issueId)
    ),
    issueData.map { case (data, fromCache) =>
      div(cls := "issue-details")(
        span(cls := s"status-badge status-${statusClass(data.status)}")(data.status),
        data.assignee.map(a => span(cls := "assignee")(s"Assigned: $a")),
        if fromCache then
          span(cls := "cache-indicator")(
            s"cached ${formatCacheAge(data.fetchedAt, now)}"
          )
        else
          ()
      )
    },
    p(cls := "last-seen")(s"Last activity: ${formatRelativeTime(wt.lastSeenAt, now)}")
  )

private def statusClass(status: String): String =
  status.toLowerCase match
    case s if s.contains("progress") || s.contains("active") => "in-progress"
    case s if s.contains("done") || s.contains("complete") => "done"
    case s if s.contains("blocked") => "blocked"
    case _ => "default"

private def formatCacheAge(fetchedAt: Instant, now: Instant): String =
  val duration = java.time.Duration.between(fetchedAt, now)
  val minutes = duration.toMinutes
  if minutes < 1 then "just now"
  else if minutes < 60 then s"${minutes}m ago"
  else if minutes < 1440 then s"${minutes / 60}h ago"
  else s"${minutes / 1440}d ago"
```

**Styling (inline CSS):**
```scala
style(raw("""
  .status-badge {
    padding: 2px 8px;
    border-radius: 3px;
    font-size: 0.9em;
  }
  .status-in-progress { background: #ffd43b; color: #000; }
  .status-done { background: #51cf66; color: #fff; }
  .status-blocked { background: #ff6b6b; color: #fff; }
  .status-default { background: #adb5bd; color: #fff; }
  
  .cache-indicator {
    font-size: 0.85em;
    color: #868e96;
    margin-left: 8px;
  }
  
  .issue-id a { color: #228be6; text-decoration: none; }
  .issue-id a:hover { text-decoration: underline; }
"""))
```

---

## Files to Modify/Create

### New Files

**Domain Layer:**
- `.iw/core/IssueData.scala` - Extended issue model with URL and timestamp
- `.iw/core/CachedIssue.scala` - Cache wrapper with TTL validation

**Application Layer:**
- `.iw/core/IssueCacheService.scala` - Pure business logic for cache-aware fetching

**Tests:**
- `.iw/core/test/IssueDataTest.scala` - Unit tests for IssueData model
- `.iw/core/test/CachedIssueTest.scala` - Unit tests for cache validation logic
- `.iw/core/test/IssueCacheServiceTest.scala` - Unit tests for cache service

### Modified Files

**Domain Layer:**
- `.iw/core/ServerState.scala`:
  - Add `issueCache: Map[String, CachedIssue]` field
  - Add upickle ReadWriter instances for new types

**Application Layer:**
- `.iw/core/DashboardService.scala`:
  - Add issue fetching logic for each worktree
  - Pass issue data to WorktreeListView
  - Handle API errors gracefully

**Presentation Layer:**
- `.iw/core/WorktreeListView.scala`:
  - Update `renderWorktreeCard` to display issue data
  - Add status badge styling
  - Add cache timestamp display
  - Add clickable issue link
  - Add CSS for status badges and cache indicator

**Infrastructure Layer:**
- `.iw/core/StateRepository.scala`:
  - Add upickle serializers for IssueData and CachedIssue (implicit instances)

---

## Testing Strategy

### Unit Tests

**IssueDataTest:**
```scala
test("fromIssue creates IssueData with url") {
  val issue = Issue("IWLE-123", "Test", "Open", Some("Jane"), None)
  val url = "https://linear.app/team/issue/IWLE-123"
  val now = Instant.now()
  
  val issueData = IssueData.fromIssue(issue, url, now)
  
  assertEquals(issueData.id, "IWLE-123")
  assertEquals(issueData.url, url)
  assertEquals(issueData.fetchedAt, now)
}
```

**CachedIssueTest:**
```scala
test("isValid returns true for fresh cache") {
  val now = Instant.now()
  val fetchedAt = now.minusSeconds(120) // 2 minutes ago
  val issueData = IssueData(/* ... */, fetchedAt = fetchedAt)
  val cached = CachedIssue(issueData, ttlMinutes = 5)
  
  assert(CachedIssue.isValid(cached, now))
}

test("isValid returns false for expired cache") {
  val now = Instant.now()
  val fetchedAt = now.minusSeconds(360) // 6 minutes ago
  val issueData = IssueData(/* ... */, fetchedAt = fetchedAt)
  val cached = CachedIssue(issueData, ttlMinutes = 5)
  
  assert(!CachedIssue.isValid(cached, now))
}

test("age calculates duration correctly") {
  val now = Instant.now()
  val fetchedAt = now.minusSeconds(180) // 3 minutes ago
  val issueData = IssueData(/* ... */, fetchedAt = fetchedAt)
  val cached = CachedIssue(issueData)
  
  val age = CachedIssue.age(cached, now)
  assertEquals(age.toMinutes, 3L)
}
```

**IssueCacheServiceTest:**
```scala
test("fetchWithCache uses valid cache") {
  val now = Instant.now()
  val cachedData = IssueData(/* ... */, fetchedAt = now.minusSeconds(60))
  val cache = Map("IWLE-123" -> CachedIssue(cachedData))
  
  val fetchFnCalled = scala.collection.mutable.ArrayBuffer[String]()
  val fetchFn = (id: String) => {
    fetchFnCalled += id
    Right(Issue(/* ... */))
  }
  val urlBuilder = (id: String) => s"https://linear.app/$id"
  
  val result = IssueCacheService.fetchWithCache(
    "IWLE-123", cache, now, fetchFn, urlBuilder
  )
  
  assert(result.isRight)
  assertEquals(result.map(_._2).getOrElse(false), true) // fromCache = true
  assert(fetchFnCalled.isEmpty) // fetchFn NOT called
}

test("fetchWithCache refreshes expired cache") {
  val now = Instant.now()
  val cachedData = IssueData(/* ... */, fetchedAt = now.minusSeconds(360)) // 6 min
  val cache = Map("IWLE-123" -> CachedIssue(cachedData))
  
  val freshIssue = Issue("IWLE-123", "Fresh title", "Open", None, None)
  val fetchFn = (_: String) => Right(freshIssue)
  val urlBuilder = (id: String) => s"https://linear.app/$id"
  
  val result = IssueCacheService.fetchWithCache(
    "IWLE-123", cache, now, fetchFn, urlBuilder
  )
  
  assert(result.isRight)
  val (issueData, fromCache) = result.getOrElse((null, true))
  assertEquals(fromCache, false) // Fresh fetch
  assertEquals(issueData.title, "Fresh title")
}

test("fetchWithCache falls back to stale cache on API error") {
  val now = Instant.now()
  val cachedData = IssueData(/* ... */, fetchedAt = now.minusSeconds(360)) // stale
  val cache = Map("IWLE-123" -> CachedIssue(cachedData))
  
  val fetchFn = (_: String) => Left("API error")
  val urlBuilder = (id: String) => s"https://linear.app/$id"
  
  val result = IssueCacheService.fetchWithCache(
    "IWLE-123", cache, now, fetchFn, urlBuilder
  )
  
  assert(result.isRight) // Still returns data (stale)
  val (issueData, fromCache) = result.getOrElse((null, false))
  assertEquals(fromCache, true) // Used stale cache
  assertEquals(issueData.id, cachedData.id)
}

test("fetchWithCache returns error if no cache and API fails") {
  val now = Instant.now()
  val cache = Map.empty[String, CachedIssue]
  
  val fetchFn = (_: String) => Left("API error")
  val urlBuilder = (id: String) => s"https://linear.app/$id"
  
  val result = IssueCacheService.fetchWithCache(
    "IWLE-123", cache, now, fetchFn, urlBuilder
  )
  
  assert(result.isLeft)
}

test("buildIssueUrl generates Linear URL") {
  val url = IssueCacheService.buildIssueUrl("IWLE-123", "Linear", None)
  assert(url.contains("linear.app"))
  assert(url.contains("IWLE-123"))
}

test("buildIssueUrl generates YouTrack URL") {
  val baseUrl = "https://youtrack.example.com"
  val url = IssueCacheService.buildIssueUrl("PROJ-456", "YouTrack", Some(baseUrl))
  assert(url.contains("youtrack.example.com"))
  assert(url.contains("PROJ-456"))
}
```

### Integration Tests

**StateRepositoryTest (extend existing):**
```scala
test("serialize and deserialize ServerState with issue cache") {
  val issueData = IssueData(
    "IWLE-123", "Test issue", "Open", Some("Jane"), None,
    "https://linear.app/issue/IWLE-123", Instant.now()
  )
  val cached = CachedIssue(issueData)
  
  val state = ServerState(
    worktrees = Map(/* ... */),
    issueCache = Map("IWLE-123" -> cached)
  )
  
  // Write to temp file
  val repo = StateRepository(tempFile)
  repo.write(state)
  
  // Read back
  val loaded = repo.read()
  assert(loaded.isRight)
  assertEquals(loaded.map(_.issueCache.size).getOrElse(0), 1)
  assertEquals(loaded.map(_.issueCache("IWLE-123").data.title).getOrElse(""), "Test issue")
}
```

**DashboardServiceTest (new integration test):**
```scala
test("renderDashboard includes issue data for registered worktrees") {
  val wt = WorktreeRegistration("IWLE-123", "/path", "Linear", "IWLE", now, now)
  val issueData = IssueData(/* ... */)
  val cache = Map("IWLE-123" -> CachedIssue(issueData))
  
  val html = DashboardService.renderDashboard(List(wt), cache, mockConfig)
  
  val htmlString = html.toString
  assert(htmlString.contains(issueData.title))
  assert(htmlString.contains(issueData.status))
  assert(htmlString.contains(issueData.url))
}

test("renderDashboard shows fallback for missing issue data") {
  val wt = WorktreeRegistration("IWLE-123", "/path", "Linear", "IWLE", now, now)
  val cache = Map.empty[String, CachedIssue]
  
  // Mock fetchIssue to fail
  val html = DashboardService.renderDashboard(List(wt), cache, mockConfig)
  
  val htmlString = html.toString
  assert(htmlString.contains("Issue data unavailable"))
}
```

### Manual Testing Scenarios

**Scenario 1: Fresh issue data displayed**
1. Clear issue cache from state.json
2. Register worktree IWLE-123
3. Load dashboard
4. Verify: Issue title from Linear/YouTrack appears
5. Verify: Status badge shows with correct color
6. Verify: Assignee name displayed
7. Verify: "cached just now" indicator shown

**Scenario 2: Cached data used (no API call)**
1. Load dashboard (populates cache)
2. Wait 2 minutes
3. Reload dashboard
4. Verify: Same issue data shown
5. Verify: "cached 2m ago" displayed
6. Verify: No new API call made (check logs)

**Scenario 3: Cache refresh after TTL expiry**
1. Load dashboard (populates cache)
2. Manually edit state.json to set fetchedAt to 10 minutes ago
3. Reload dashboard
4. Verify: Fresh API call made
5. Verify: "cached just now" appears
6. Verify: Updated issue data shown if changed in tracker

**Scenario 4: Stale cache used on API failure**
1. Load dashboard (populates cache)
2. Invalidate API token or disable network
3. Wait 6 minutes (expire cache)
4. Reload dashboard
5. Verify: Stale issue data still displayed
6. Verify: Warning indicator shown ("cached 6m ago" in different color)
7. Verify: Dashboard doesn't crash

**Scenario 5: Clickable issue links**
1. Load dashboard with registered worktrees
2. Click on issue ID link
3. Verify: Opens Linear/YouTrack in new tab
4. Verify: Correct issue page loaded

---

## Acceptance Criteria

Phase 4 is complete when:

### Functional Requirements

- [ ] Dashboard displays actual issue titles from Linear/YouTrack
- [ ] Issue status shown as color-coded badge (In Progress = yellow, Done = green, Blocked = red)
- [ ] Assignee name displayed if present
- [ ] Issue ID is clickable link to tracker (Linear or YouTrack)
- [ ] Cache timestamp displayed ("cached 3m ago")
- [ ] Fresh data fetched if cache older than 5 minutes
- [ ] Cached data used if age < 5 minutes (no API call)
- [ ] Stale cache displayed with warning if API fails
- [ ] "Issue data unavailable" shown if no cache and API fails
- [ ] Issue cache persisted in state.json
- [ ] Multiple worktrees with different trackers (Linear + YouTrack) work correctly

### Non-Functional Requirements

- [ ] All unit tests passing (IssueData, CachedIssue, IssueCacheService)
- [ ] All integration tests passing (StateRepository, DashboardService)
- [ ] Manual scenarios verified (fresh fetch, cache use, API failure handling)
- [ ] Dashboard loads within 1 second with 10 cached worktrees
- [ ] API failures don't crash dashboard
- [ ] Code follows FCIS pattern (pure domain/application, effects in infrastructure)
- [ ] No new compilation warnings
- [ ] Git commits follow TDD: test → implementation → refactor

### Quality Checks

- [ ] Code review self-check: Are timestamps injected (not generated in pure functions)?
- [ ] Code review self-check: Does cache fallback work correctly?
- [ ] Code review self-check: Are error messages user-friendly?
- [ ] Documentation: Update implementation-log.md with Phase 4 summary
- [ ] Documentation: Comment complex parts (cache validation, URL building)

---

## Implementation Sequence

**Recommended order (TDD):**

### Step 1: Domain Models (1-2h)

1. Write `IssueDataTest.scala` with factory method tests
2. Implement `IssueData.scala` case class and factory
3. Write `CachedIssueTest.scala` with TTL validation tests
4. Implement `CachedIssue.scala` with validation logic
5. Verify all unit tests pass
6. Commit: "feat(IWLE-100): Add IssueData and CachedIssue domain models"

### Step 2: Cache Service Logic (1-2h)

7. Write `IssueCacheServiceTest.scala` with cache scenarios
8. Implement `IssueCacheService.scala` pure functions
9. Test all cache paths: valid, expired, stale fallback, no cache
10. Verify unit tests pass
11. Commit: "feat(IWLE-100): Add IssueCacheService for cache-aware fetching"

### Step 3: State Repository Extension (0.5-1h)

12. Extend `ServerState` with issueCache field
13. Add upickle ReadWriter instances in StateRepository
14. Write integration test for serialization/deserialization
15. Verify state.json correctly stores and loads cache
16. Commit: "feat(IWLE-100): Extend ServerState with issue cache"

### Step 4: Dashboard Integration (2-3h)

17. Modify `DashboardService.renderDashboard()` to fetch issue data
18. Build fetch function wrappers for LinearClient/YouTrackClient
19. Pass issue data to WorktreeListView
20. Write integration test for DashboardService with issue data
21. Verify tests pass
22. Commit: "feat(IWLE-100): Integrate issue fetching in dashboard"

23. Modify `WorktreeListView.renderWorktreeCard()` to display issue data
24. Add status badge rendering with color coding
25. Add cache timestamp display
26. Add clickable issue link
27. Add inline CSS for badges and cache indicator
28. Manual test: Load dashboard, verify issue data appears
29. Commit: "feat(IWLE-100): Enhance worktree cards with issue details"

### Step 5: URL Building (0.5h)

30. Implement `IssueCacheService.buildIssueUrl()` for Linear
31. Implement for YouTrack (with baseUrl)
32. Write unit tests for URL construction
33. Verify URLs are correct format
34. Commit: "feat(IWLE-100): Add issue URL construction"

### Step 6: Error Handling & Edge Cases (1h)

35. Test API failure scenarios (invalid token, network error)
36. Verify stale cache fallback works
37. Test missing cache + API failure shows "unavailable"
38. Test mixed Linear/YouTrack worktrees
39. Fix any issues found
40. Commit fixes if needed

### Step 7: Manual E2E Verification (0.5-1h)

41. Run all manual test scenarios (see Testing Strategy)
42. Verify cache TTL behavior (use sleep or manual timestamp editing)
43. Verify clickable links open correct pages
44. Test with real Linear/YouTrack APIs
45. Fix any issues found
46. Commit fixes if needed

### Step 8: Documentation (0.5h)

47. Update `implementation-log.md` with Phase 4 summary
48. Document cache TTL and fallback behavior
49. Add comments for complex logic (cache validation, URL building)
50. Commit: "docs(IWLE-100): Document Phase 4 implementation"

**Total estimated time: 6-8 hours**

---

## Risk Assessment

### Risk: API rate limits exceed threshold

**Likelihood:** Low
**Impact:** Medium (cache helps, but could still hit limits)

**Mitigation:**
- 5-minute TTL reduces API calls significantly
- Typical usage: 5-10 worktrees = 1 API call per 5 min per worktree (12 calls/hour per worktree max)
- Linear rate limit: 300 requests/minute (well above our usage)
- YouTrack rate limit varies by plan (typically 100-1000/min)
- If rate limit hit, stale cache fallback keeps dashboard working

### Risk: Stale cache shows outdated information

**Likelihood:** Medium
**Impact:** Low (informational only, not critical)

**Mitigation:**
- 5-minute TTL is short enough for most use cases
- Cache indicator shows age, user knows data may be stale
- Manual refresh reloads page and fetches fresh data
- Future: Add refresh button if user feedback requests it

### Risk: Large state.json with many worktrees

**Likelihood:** Low
**Impact:** Low (performance degradation)

**Mitigation:**
- Typical usage: 5-10 worktrees, ~2KB per cached issue = 10-20KB total
- StateRepository atomic writes handle this size easily
- If becomes issue, future phase can add cache pruning (remove worktrees not seen in 30 days)

### Risk: API credentials missing or invalid

**Likelihood:** Medium
**Impact:** Medium (no issue data displayed)

**Mitigation:**
- Graceful fallback: "Issue data unavailable"
- Clear error message in logs: "API token invalid or missing"
- User can still use dashboard for basic worktree tracking
- Future: Add config validation command to check API credentials

### Risk: URL construction breaks for non-standard issue IDs

**Likelihood:** Low
**Impact:** Low (link doesn't work, but not critical)

**Mitigation:**
- Use existing IssueId validation (already in codebase)
- Test with various ID formats (TEAM-123, PROJ-1, etc.)
- If URL malformed, still shows issue ID (just not clickable)
- Future: Add URL validation or fallback to team homepage

---

## Open Questions

None. All technical decisions resolved:

**Resolved during analysis:**
- Cache TTL: 5 minutes (from analysis specification)
- Cache storage: Embedded in state.json (no separate file)
- Error handling: Stale cache fallback (graceful degradation)
- URL format: Tracker-specific construction (Linear vs YouTrack)

---

## Notes and Decisions

### Design Decisions

**1. Cache storage in state.json vs separate file**
- Decision: Embed issue cache in state.json
- Rationale: Simpler, atomic writes already work, no synchronization issues
- Alternative considered: Separate cache.json (rejected: adds complexity, no benefit)

**2. TTL of 5 minutes**
- Decision: 5-minute cache TTL for issue data
- Rationale: Balances freshness with API call reduction
- From analysis: Explicitly specified as 5 minutes
- Alternative considered: 2 minutes (rejected: too aggressive), 10 minutes (rejected: too stale)

**3. Stale cache fallback vs error**
- Decision: Show stale data with warning if API fails
- Rationale: Better UX, dashboard remains functional
- User sees old data is better than no data
- Alternative considered: Show error only (rejected: degrades UX)

**4. Synchronous API calls in dashboard render**
- Decision: Call LinearClient/YouTrackClient synchronously for each worktree
- Rationale: Fast local calls (~100-200ms), acceptable for MVP
- Typical dashboard: 5-10 worktrees = 500-1000ms total (half from cache)
- Alternative considered: Async fetch (deferred: YAGNI for Phase 4)

**5. URL construction in service vs domain**
- Decision: URL building in IssueCacheService (application layer)
- Rationale: Requires configuration (tracker type, baseUrl), not pure domain logic
- Domain (IssueData) stores URL, but doesn't construct it
- Alternative considered: URL as computed property (rejected: needs config access)

### Technical Notes

**Cache invalidation strategy:**
- Only TTL-based expiration (no manual invalidation in Phase 4)
- Future consideration: Add "refresh" button for manual cache busting
- Future consideration: Webhook integration to invalidate on issue updates

**Mixed tracker support:**
- Dashboard can show Linear and YouTrack issues simultaneously
- Each worktree registration stores tracker type
- DashboardService routes to correct client based on trackerType

**Performance optimization (future):**
- Phase 4: Sequential API calls (simple, predictable)
- Future: Parallel fetching with Futures (if >10 worktrees become common)
- Future: Background refresh daemon (periodic cache updates)

---

## Links to Related Documents

- **Analysis:** `project-management/issues/IWLE-100/analysis.md` (Story 3, lines 107-157)
- **Phase 1 Context:** `project-management/issues/IWLE-100/phase-01-context.md`
- **Phase 2 Context:** `project-management/issues/IWLE-100/phase-02-context.md`
- **Phase 3 Context:** `project-management/issues/IWLE-100/phase-03-context.md`
- **Implementation Log:** `project-management/issues/IWLE-100/implementation-log.md`
- **Task Index:** `project-management/issues/IWLE-100/tasks.md`

---

## Gherkin Scenarios (from Analysis)

```gherkin
Feature: Dashboard displays issue tracker information
  As a developer reviewing work status
  I want to see issue details from Linear/YouTrack
  So that I understand what each worktree is working on

Scenario: Dashboard shows cached issue data with refresh
  Given worktree IWLE-123 exists for a Linear issue
  And the issue title is "Add user authentication"
  And the issue status is "In Progress"
  When I load the dashboard
  Then the worktree card shows "IWLE-123 · Add user authentication"
  And the card shows status "In Progress"
  And the issue data was fetched from Linear API
  And a clickable link to the Linear issue is displayed

Scenario: Issue data is cached with TTL
  Given issue IWLE-123 was fetched 3 minutes ago
  When I refresh the dashboard
  Then the cached issue data is used (no API call)
  And I see a note "cached 3m ago"

Scenario: Expired cache triggers refresh
  Given issue IWLE-123 was fetched 6 minutes ago
  When I refresh the dashboard
  Then a new API call fetches current issue data
  And the cache timestamp is updated
```

**Test Automation:**
- Phase 4: Manual testing of scenarios (verify with real Linear/YouTrack APIs)
- Future: Mock API responses for automated E2E tests

---

**Phase Status:** Ready for Implementation

**Next Steps:**
1. Begin implementation following Step 1 (Domain Models)
2. Run tests continuously during development (TDD)
3. Manual testing after dashboard integration complete
4. Update implementation-log.md after completion
5. Mark Phase 4 complete in tasks.md
