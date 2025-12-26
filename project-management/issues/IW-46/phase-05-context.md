# Phase 5 Context: Review State Caching

**Issue:** #46
**Phase:** 5 of 6
**Story:** Story 4 - Review state cache prevents redundant file reads
**Estimated Effort:** 4-6 hours

---

## Goals

This phase focuses on **optimizing caching performance** for review state data. While basic caching infrastructure was established in Phase 1, this phase adds:

1. **Cache persistence across server restarts** via StateRepository
2. **Cache update on successful parse** to populate the cache
3. **Test coverage for cache behavior** (hit/miss scenarios, invalidation)
4. **Performance verification** that caching actually prevents redundant file reads

The goal is to ensure the dashboard loads quickly even with many worktrees by avoiding repeated JSON parsing when files haven't changed.

---

## Current State Analysis

### What Already Exists (from Phase 1)

✅ **Domain Models:**
- `CachedReviewState` with `filesMtime` map for validation
- `CachedReviewState.isValid()` logic for mtime-based invalidation

✅ **Infrastructure:**
- `ServerState.reviewStateCache: Map[String, CachedReviewState]`
- `StateRepository` serialization for `reviewStateCache` field

✅ **Service Layer:**
- `ReviewStateService.fetchReviewState()` checks cache validity
- Returns cached state if valid, re-parses if invalid/missing

✅ **Integration:**
- `DashboardService.fetchReviewStateForWorktree()` calls ReviewStateService
- Cache passed from ServerState through to service

### What's Missing (Phase 5 Gaps)

❌ **Cache Population:**
- `ReviewStateService.fetchReviewState()` currently returns `Either[String, ReviewState]`
- **Does not return updated cache** when it re-parses the file
- DashboardService cannot update ServerState.reviewStateCache after fetch

❌ **Cache Update Flow:**
- No mechanism for DashboardService to update cache in ServerState
- No atomic update of both state and cache
- Cache stays empty because it's never populated after parsing

❌ **Test Coverage:**
- No tests for cache hit scenario (file unchanged, cache used)
- No tests for cache miss scenario (file changed, re-parse triggered)
- No tests for cache persistence across server restarts
- No performance verification tests

### Root Cause

The **fundamental issue** is that `ReviewStateService.fetchReviewState()` is read-only. It checks the cache but never **updates** it. This means:

1. First load: cache empty → parse file → return ReviewState ✅
2. Second load: cache still empty → parse file again ❌ (should use cache)

**Comparison with WorkflowProgressService:**

Looking at the existing implementation, `WorkflowProgressService.fetchProgress()` has the same signature limitation. It likely has the same issue.

However, the caching pattern used elsewhere (e.g., `IssueCacheService`, `PullRequestCacheService`) shows a different approach:
- These services return `Either[String, CachedData]` (not just the data)
- Caller (DashboardService) extracts data AND updates ServerState cache
- This separates cache management (in caller) from data fetching (in service)

---

## Technical Approach

### Strategy: Service Returns CachedReviewState

**Pattern to follow:** Align with `IssueCacheService` and `PullRequestCacheService`:

1. **Change return type:**
   ```scala
   // OLD (Phase 1):
   def fetchReviewState(...): Either[String, ReviewState]
   
   // NEW (Phase 5):
   def fetchReviewState(...): Either[String, CachedReviewState]
   ```

2. **Service creates CachedReviewState:**
   - On cache hit: return existing `CachedReviewState`
   - On cache miss: parse file → create `CachedReviewState(state, filesMtime)` → return

3. **Caller updates ServerState:**
   - DashboardService extracts `.state` for rendering
   - DashboardService updates `reviewStateCache` map
   - ServerState persists via StateRepository (already wired up)

### Why This Approach?

✅ **Functional Core principle:** Service remains pure, caller handles state mutation
✅ **Consistent with existing patterns:** Matches IssueCacheService/PullRequestCacheService
✅ **Minimal changes:** Only signature change + wrapper construction
✅ **Testable:** Easy to verify CachedReviewState construction in unit tests

### Alternative Considered: Service Mutates Cache

**Rejected approach:** Pass mutable cache reference, service updates it directly

```scala
// REJECTED:
def fetchReviewState(
  cache: scala.collection.mutable.Map[String, CachedReviewState],
  ...
): Either[String, ReviewState]
```

❌ **Violates FCIS:** Service should be pure, not mutate external state
❌ **Harder to test:** Need to inspect mutable state after call
❌ **Inconsistent:** Other services don't mutate caches

---

## Scope

### In Scope

✅ **Change ReviewStateService signature** to return `CachedReviewState`
✅ **Update service logic** to construct CachedReviewState on parse
✅ **Update DashboardService** to extract state and update cache
✅ **Update CaskServer** to pass updated cache back to ServerState
✅ **Add cache hit/miss tests** for ReviewStateService
✅ **Add cache persistence tests** for StateRepository (verify round-trip)
✅ **Update existing tests** for new return type

### Out of Scope

❌ **Performance benchmarks:** No need for detailed profiling (trust mtime validation works)
❌ **Cache eviction policy:** No LRU/expiry needed (state file grows slowly)
❌ **Multi-file caching:** review-state.json is single file, not multiple artifacts
❌ **Concurrent access:** Dashboard is single-threaded, no race conditions

---

## Dependencies

### Prerequisites from Previous Phases

- ✅ Phase 1: CachedReviewState model, validation logic, basic service
- ✅ Phase 1: ServerState.reviewStateCache field
- ✅ Phase 1: StateRepository serialization

### External Dependencies

- None (all work internal to existing modules)

---

## Files to Modify

### Core Service Layer

**`.iw/core/ReviewStateService.scala`**
- Change return type: `Either[String, ReviewState]` → `Either[String, CachedReviewState]`
- On cache hit: return existing `CachedReviewState`
- On cache miss: construct `CachedReviewState(parsedState, mtimeMap)` and return

### Application Layer

**`.iw/core/DashboardService.scala`**
- Update `fetchReviewStateForWorktree()` to handle `CachedReviewState`
- Extract `.state` for rendering
- Update cache map with new `CachedReviewState`
- Pass updated cache to next iteration (requires accumulator pattern)

**`.iw/core/CaskServer.scala`**
- Update route handlers to accept updated `reviewStateCache`
- Pass updated cache back to ServerState
- Ensure StateRepository.write() persists cache

### Tests

**`.iw/core/test/ReviewStateServiceTest.scala`**
- Update existing tests for new return type (extract `.state`)
- Add cache hit test: file unchanged → cached state returned
- Add cache miss test: file changed → new state parsed and cached
- Add cache construction test: verify filesMtime map populated correctly

**`.iw/core/test/DashboardServiceTest.scala`**
- Verify cache update flow
- Test that cache persists across multiple dashboard renders

**`.iw/core/test/StateRepositoryTest.scala`**
- Test cache round-trip: write state with cache → read → verify cache intact
- Existing tests should pass (serialization already implemented in Phase 1)

---

## Implementation Plan

### Step 1: Update ReviewStateService (30-60 min)

1. Change return type to `Either[String, CachedReviewState]`
2. On cache hit path: return cached value directly (already CachedReviewState)
3. On cache miss path: after parsing, construct CachedReviewState:
   ```scala
   parseReviewStateJson(content).map { state =>
     CachedReviewState(state, currentMtimes)
   }
   ```
4. Compile, fix type errors

### Step 2: Update DashboardService (45-90 min)

1. Change `fetchReviewStateForWorktree()` to return `Option[CachedReviewState]`
2. Extract `.state` when passing to WorktreeListView
3. Accumulate updated caches during worktree iteration:
   ```scala
   val (worktreesWithData, updatedCache) = worktrees.foldLeft((List.empty, reviewStateCache)) {
     case ((acc, cache), wt) =>
       val result = fetchReviewStateForWorktree(wt, cache)
       result match {
         case Some(cached) => 
           ((wt, ..., Some(cached.state)) :: acc, cache + (wt.issueId -> cached))
         case None => 
           ((wt, ..., None) :: acc, cache)
       }
   }
   ```
4. Return updated cache to caller

### Step 3: Update CaskServer Route (30-45 min)

1. Extract updated cache from DashboardService
2. Update ServerState with new cache
3. Persist via StateRepository.write()
4. Test end-to-end: dashboard load → cache populated → server restart → cache still present

### Step 4: Update Tests (90-120 min)

1. **ReviewStateServiceTest:**
   - Fix existing tests to extract `.state` from returned CachedReviewState
   - Add cache hit test
   - Add cache miss test
   - Add filesMtime construction test

2. **DashboardServiceTest:**
   - Add cache accumulation test
   - Verify multiple renders don't re-parse unchanged files

3. **StateRepositoryTest:**
   - Verify cache round-trip (should already pass from Phase 1)

### Step 5: Manual Verification (30 min)

1. Start server, register worktree with review-state.json
2. Load dashboard → verify parse happens (add debug log)
3. Load dashboard again → verify cache used (no re-parse)
4. Modify review-state.json
5. Load dashboard → verify re-parse triggered
6. Restart server → verify cache still present

---

## Testing Strategy

### Unit Tests

**ReviewStateService cache behavior:**

```scala
test("cache hit: returns cached state without re-parsing"):
  val state = ReviewState(Some("awaiting_review"), Some(8), Some("Ready"), List(...))
  val cached = CachedReviewState(state, Map("review-state.json" -> 1000L))
  val cache = Map("IWLE-123" -> cached)
  
  val readFile = (_: String) => fail("Should not read file on cache hit")
  val getMtime = (_: String) => Right(1000L) // Same mtime
  
  val result = ReviewStateService.fetchReviewState("IWLE-123", "/path", cache, readFile, getMtime)
  
  assertEquals(result, Right(cached))

test("cache miss: re-parses when mtime changes"):
  val oldState = ReviewState(Some("in_progress"), Some(7), None, List(...))
  val cached = CachedReviewState(oldState, Map("review-state.json" -> 1000L))
  val cache = Map("IWLE-123" -> cached)
  
  val newJson = """{"status": "awaiting_review", "phase": 8, "artifacts": [...]}"""
  val readFile = (_: String) => Right(newJson)
  val getMtime = (_: String) => Right(2000L) // Changed mtime
  
  val result = ReviewStateService.fetchReviewState("IWLE-123", "/path", cache, readFile, getMtime)
  
  result match {
    case Right(newCached) =>
      assertEquals(newCached.state.status, Some("awaiting_review"))
      assertEquals(newCached.filesMtime("review-state.json"), 2000L)
    case Left(err) => fail(s"Unexpected error: $err")
  }
```

### Integration Tests

**DashboardService cache accumulation:**

```scala
test("multiple dashboard renders use cache for unchanged files"):
  var parseCount = 0
  val readFile = (_: String) => {
    parseCount += 1
    Right("""{"artifacts": []}""")
  }
  val getMtime = (_: String) => Right(1000L)
  
  // First render
  val cache1 = Map.empty[String, CachedReviewState]
  val (_, updatedCache1) = renderDashboard(..., cache1)
  assertEquals(parseCount, 1) // File parsed
  
  // Second render with cache
  val (_, updatedCache2) = renderDashboard(..., updatedCache1)
  assertEquals(parseCount, 1) // File NOT re-parsed (cache hit)
```

**StateRepository persistence:**

```scala
test("reviewStateCache persists across write/read"):
  val state = ReviewState(Some("awaiting_review"), Some(8), None, List(...))
  val cached = CachedReviewState(state, Map("review-state.json" -> 1000L))
  val serverState = ServerState(
    worktrees = Map(...),
    reviewStateCache = Map("IWLE-123" -> cached)
  )
  
  val repo = StateRepository(tmpFile)
  repo.write(serverState)
  
  val loaded = repo.read()
  loaded match {
    case Right(state) =>
      assertEquals(state.reviewStateCache.size, 1)
      assertEquals(state.reviewStateCache("IWLE-123").state.status, Some("awaiting_review"))
      assertEquals(state.reviewStateCache("IWLE-123").filesMtime("review-state.json"), 1000L)
    case Left(err) => fail(s"Failed to read: $err")
  }
```

### Manual Testing Checklist

- [ ] Start server, load dashboard with worktree
- [ ] Verify review artifacts displayed
- [ ] Add debug log to confirm file parsed on first load
- [ ] Reload dashboard, verify cache hit (no re-parse)
- [ ] Modify review-state.json (change status)
- [ ] Reload dashboard, verify re-parse triggered
- [ ] Verify updated content displayed
- [ ] Restart server (kill and restart)
- [ ] Load dashboard, verify cache still present (no re-parse on first load)

---

## Acceptance Criteria

### Functional Requirements

✅ **Cache populated on parse:** After first dashboard load, `reviewStateCache` contains entry
✅ **Cache hit on unchanged file:** Second load uses cache, doesn't re-parse JSON
✅ **Cache miss on changed file:** Modified mtime triggers re-parse and cache update
✅ **Cache persists:** Server restart preserves cache via StateRepository

### Non-Functional Requirements

✅ **Performance:** Dashboard with 10 worktrees loads in <1s (no repeated parsing)
✅ **Correctness:** Cache validation never serves stale data
✅ **Testability:** All cache paths covered by unit tests

### Definition of Done

- [ ] ReviewStateService returns `CachedReviewState`
- [ ] DashboardService updates cache on each fetch
- [ ] CaskServer persists updated cache
- [ ] All existing tests updated and passing
- [ ] New cache hit/miss tests added and passing
- [ ] Cache persistence test added and passing
- [ ] Manual verification completed (checklist above)
- [ ] No compiler warnings
- [ ] Code review by Michal (if applicable)

---

## Technical Notes

### Cache Invalidation Strategy

**mtime-based validation** (from Phase 1):
- Store `filesMtime: Map[String, Long]` in CachedReviewState
- On fetch, compare current mtime with cached mtime
- If different → invalidate and re-parse
- If same → use cached data

**Why mtime works:**
- review-state.json updated by workflows, not manually edited
- Workflows always write new file (triggers mtime change)
- No need for content hashing (mtime is sufficient signal)

### Comparison with Other Caches

**IssueCacheService pattern:**
```scala
def fetchWithCache(...): Either[String, (IssueData, Boolean)] =
  // Returns data + fromCache flag
  // Caller updates cache separately
```

**PullRequestCacheService pattern:**
```scala
def fetchPR(...): Either[String, Option[PullRequestData]] =
  // Returns data only
  // Caller updates cache in ServerStateService
```

**ReviewStateService pattern (after Phase 5):**
```scala
def fetchReviewState(...): Either[String, CachedReviewState] =
  // Returns cached wrapper (includes data + mtime)
  // Caller extracts data and updates cache
```

**Consistency:** All three services are pure, caller manages state mutation ✅

### Performance Considerations

**Expected impact:**
- **Before Phase 5:** Every dashboard load parses every review-state.json
- **After Phase 5:** Only parse on first load or file change
- **Typical scenario:** 10 worktrees, review state unchanged
  - Before: 10 JSON parses (~10ms total)
  - After: 10 mtime checks (~1ms total)
  - **Savings:** ~9ms per dashboard load (90% reduction)

**Not a bottleneck yet, but good practice:** Caching prevents future scalability issues

---

## Open Questions

**Q1: Should cache have TTL (time-to-live)?**
- A: No. mtime validation is sufficient. Review state changes explicitly by workflow writes.

**Q2: Should cache have size limit (LRU eviction)?**
- A: No. Worktrees are manually registered/removed. Cache grows slowly (~10-50 entries typical).

**Q3: Should we cache artifact file contents too?**
- A: No. Out of scope for Phase 5. Artifact viewing (Phase 3) reads files on-demand. Could cache later if needed.

**Q4: What if review-state.json references missing artifact files?**
- A: Handled by Phase 6 (graceful error handling). Cache only stores ReviewState, not artifact contents.

---

## Related Phases

- **Phase 1:** Established cache infrastructure (this phase completes it)
- **Phase 3:** Artifact viewing (separate concern, no caching yet)
- **Phase 6:** Error handling (handles cache corruption, missing files)

---

## Implementation Checklist

- [ ] Step 1: Update ReviewStateService return type
- [ ] Step 2: Update DashboardService cache accumulation
- [ ] Step 3: Update CaskServer persistence
- [ ] Step 4: Update all tests
- [ ] Step 5: Manual verification
- [ ] Code review (if needed)
- [ ] Update implementation-log.md with Phase 5 summary

**Estimated Total Time:** 4-6 hours (aligns with analysis estimate)

---

**Status:** Ready for Implementation

**Next Command:** `/iterative-works:ag-implement 46 --phase 5`
