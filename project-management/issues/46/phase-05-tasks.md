# Phase 5 Tasks: Review State Caching

**Issue:** #46
**Phase:** 5 of 6
**Story:** Review state cache prevents redundant file reads
**Estimated Effort:** 4-6 hours

---

## Task Breakdown

### Setup (15 min)

- [ ] Review Phase 5 context document and understand current state
- [ ] Identify files to modify: ReviewStateService, DashboardService, CaskServer
- [ ] Review existing cache patterns (CachedProgress, CachedIssue) for consistency

---

### Tests: ReviewStateService Cache Behavior (60-75 min)

#### Cache Hit Tests (20-25 min)

- [ ] Write test: cache hit returns cached state without re-parsing file
  - Setup: cached ReviewState with mtime 1000
  - Mock: getMtime returns 1000 (unchanged)
  - Mock: readFile fails if called
  - Assert: returns cached CachedReviewState, readFile never called

- [ ] Write test: cache hit preserves exact cached state
  - Setup: cached state with specific artifacts/status
  - Assert: returned state matches cached state exactly

#### Cache Miss Tests (20-25 min)

- [ ] Write test: cache miss re-parses when mtime changes
  - Setup: cached state with mtime 1000
  - Mock: getMtime returns 2000 (changed)
  - Mock: readFile returns new JSON content
  - Assert: returns new CachedReviewState with updated mtime
  - Assert: readFile called once

- [ ] Write test: cache miss constructs correct filesMtime map
  - Setup: valid JSON content
  - Mock: getMtime returns 2000
  - Assert: returned CachedReviewState.filesMtime contains correct entry
  - Assert: filesMtime["review-state.json"] == 2000

#### Cache Construction Tests (20-25 min)

- [ ] Write test: first fetch (no cache) parses and creates CachedReviewState
  - Setup: empty cache
  - Mock: readFile returns valid JSON
  - Mock: getMtime returns 1000
  - Assert: returns CachedReviewState with correct state and mtime

- [ ] Write test: cache entry contains all parsed fields (status, phase, message, artifacts)
  - Setup: JSON with all optional fields
  - Assert: CachedReviewState.state contains all parsed values

---

### Implementation: ReviewStateService Signature Change (30-45 min)

- [ ] Change fetchReviewState return type from Either[String, ReviewState] to Either[String, CachedReviewState]
  - Update method signature
  - Update return statements

- [ ] Update cache hit path to return existing CachedReviewState directly
  - Check if cache entry exists and isValid
  - If valid, return Right(cachedEntry)

- [ ] Update cache miss path to construct CachedReviewState after parsing
  - After successful JSON parse, get current mtime
  - Create Map("review-state.json" -> currentMtime)
  - Wrap ReviewState in CachedReviewState(state, mtimeMap)
  - Return Right(cachedReviewState)

- [ ] Compile and fix type errors in ReviewStateService

---

### Tests: Update Existing Tests (30-45 min)

- [ ] Update ReviewStateServiceTest existing tests to extract .state from CachedReviewState
  - Find all assertions on ReviewState
  - Update to result.map(_.state) or equivalent pattern
  - Verify tests still pass

- [ ] Run ReviewStateServiceTest suite and verify all tests pass
  - Fix any failing tests
  - Ensure new cache tests pass

---

### Tests: DashboardService Cache Accumulation (45-60 min)

- [ ] Write test: DashboardService updates cache on successful fetch
  - Setup: empty cache, one worktree
  - Mock: ReviewStateService returns CachedReviewState
  - Call: fetchReviewStateForWorktree
  - Assert: returned cache contains entry for issueId

- [ ] Write test: multiple dashboard renders reuse cache for unchanged files
  - Setup: first render with empty cache
  - Assert: file parsed once (track readFile call count)
  - Setup: second render with cache from first render
  - Assert: file not re-parsed (readFile call count unchanged)

- [ ] Write test: cache accumulates across multiple worktrees
  - Setup: three worktrees, empty cache
  - Render dashboard with all worktrees
  - Assert: cache contains three entries after render

---

### Implementation: DashboardService Cache Update Flow (60-90 min)

- [ ] Change fetchReviewStateForWorktree to handle CachedReviewState
  - Update call to ReviewStateService.fetchReviewState
  - Extract .state from returned CachedReviewState
  - Keep signature returning Option[ReviewState] for view layer

- [ ] Implement cache accumulator pattern in dashboard rendering
  - Change worktree iteration to foldLeft with cache accumulator
  - On successful fetch, add CachedReviewState to accumulator
  - On failure, preserve existing accumulator
  - Return (worktreeData, updatedCache) tuple

- [ ] Update DashboardService.render to return updated cache
  - Change return type to include updated cache
  - Thread cache through all fetch operations
  - Return final cache state

- [ ] Compile and fix type errors in DashboardService

---

### Implementation: CaskServer Cache Persistence (30-45 min)

- [ ] Update dashboard route handler to capture updated cache
  - Extract cache from DashboardService.render result
  - Update ServerState with new reviewStateCache
  - Pass updated ServerState to StateRepository.write()

- [ ] Verify cache persistence wiring
  - Trace ServerState flow from route through persistence
  - Ensure no cache overwrites happen

- [ ] Compile and fix type errors in CaskServer

---

### Tests: StateRepository Cache Persistence (30-45 min)

- [ ] Write test: reviewStateCache round-trip through write/read
  - Setup: ServerState with populated reviewStateCache
  - Write to temp file via StateRepository
  - Read back from file
  - Assert: cache matches original (status, phase, artifacts, mtime)

- [ ] Write test: empty cache persists correctly
  - Setup: ServerState with empty reviewStateCache
  - Write and read back
  - Assert: cache is empty Map (not null)

- [ ] Run StateRepositoryTest suite and verify all tests pass
  - Existing serialization should handle cache (from Phase 1)
  - If failures, fix JSON encoding/decoding

---

### Integration: End-to-End Cache Verification (45-60 min)

- [ ] Write integration test: cache populated after first dashboard load
  - Setup: server with registered worktree
  - Load dashboard once
  - Assert: ServerState.reviewStateCache contains entry

- [ ] Write integration test: cache used on second dashboard load
  - Setup: server with cached review state
  - Mock: track file read operations
  - Load dashboard twice
  - Assert: file read only once (cache hit on second load)

- [ ] Write integration test: cache invalidated when file modified
  - Setup: cached review state
  - Modify review-state.json (change mtime)
  - Load dashboard
  - Assert: file re-read, cache updated

---

### Manual Verification (30-45 min)

- [ ] Start server and register worktree with review-state.json
  - Use existing test worktree or create new one
  - Verify dashboard loads and displays review artifacts

- [ ] Verify cache hit: load dashboard twice, check logs
  - Add debug logging to ReviewStateService (temporary)
  - First load: log "Parsing review-state.json"
  - Second load: log "Using cached review state"
  - Verify second load uses cache

- [ ] Verify cache miss: modify file and reload
  - Touch or edit review-state.json
  - Load dashboard
  - Verify log shows "Parsing review-state.json" (cache invalidated)

- [ ] Verify cache persistence: restart server
  - Kill server process
  - Restart server
  - Load dashboard
  - Verify log shows "Using cached review state" (cache survived restart)

- [ ] Remove debug logging added for verification

---

## Task Summary

**Total Tasks:** 31
**Estimated Time:** 4-6 hours

### Breakdown by Category:
- Setup: 1 task (15 min)
- Tests (write first): 15 tasks (3-4 hours)
- Implementation: 8 tasks (2-3 hours)
- Integration: 3 tasks (45-60 min)
- Manual Verification: 5 tasks (30-45 min)

---

## Notes

- **TDD Approach:** All test tasks come before corresponding implementation
- **Cache Pattern Consistency:** Follow existing CachedProgress/CachedIssue patterns
- **Functional Purity:** Service returns CachedReviewState, caller updates ServerState
- **Minimal Changes:** Only signature change + wrapper construction in service
- **Testing Strategy:** Unit tests for cache logic, integration tests for persistence

---

**Status:** Ready for Implementation

**Next Command:** Start working through tasks in order, TDD style
