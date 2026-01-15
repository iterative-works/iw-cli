# Refactoring R1: Centralized State Management

**Phase:** 6
**Created:** 2026-01-15
**Status:** Planned

## Decision Summary

Race condition in state management causes:
1. File corruption (multiple writers to same temp file)
2. Data loss (read-modify-write without synchronization)
3. Missing updates (reviewStateCache not refreshed per-card)

Fix by centralizing all state access through a synchronized service.

## Current State

### StateRepository (`.iw/core/StateRepository.scala`)
- Uses fixed temp file name `state.json.tmp` for all writes
- Multiple concurrent writes corrupt the temp file
- Atomic move is useless when temp file is shared

### CaskServer (`.iw/core/CaskServer.scala`)
- Dashboard endpoint: loads state, writes twice (prune + reviewStateCache)
- Per-card endpoint: loads state, writes issueCache only
- Register/unregister: load state, modify, write
- Each endpoint does independent read-modify-write

### DashboardService (`.iw/core/DashboardService.scala`)
- Computes entire `reviewStateCache` by reading filesystem
- Returns new cache for bulk replacement
- Per-card refresh never updates reviewStateCache

### WorktreeCardService (`.iw/core/WorktreeCardService.scala`)
- Only returns `fetchedIssue` for caching
- Does not refresh progressCache, prCache, or reviewStateCache
- Missing the most important update (review state)

## Target State

### StateRepository
- Use unique temp file: `state.json.tmp-{UUID}`
- Delete temp file after successful move (cleanup)

### ServerStateService (NEW)
```scala
class ServerStateService(repository: StateRepository):
  private var state: ServerState = _
  private val lock = new ReentrantLock()

  def initialize(): Either[String, Unit]  // Load from disk
  def getState: ServerState               // Read (no lock needed)

  // Per-entry updates (all use lock internally)
  def updateWorktree(issueId: String)(f: Option[WorktreeRegistration] => Option[WorktreeRegistration]): Unit
  def updateIssueCache(issueId: String)(f: Option[CachedIssue] => Option[CachedIssue]): Unit
  def updateProgressCache(issueId: String)(f: Option[CachedProgress] => Option[CachedProgress]): Unit
  def updatePRCache(issueId: String)(f: Option[CachedPR] => Option[CachedPR]): Unit
  def updateReviewStateCache(issueId: String)(f: Option[CachedReviewState] => Option[CachedReviewState]): Unit

  // Bulk operations (with lock)
  def pruneWorktrees(isValid: WorktreeRegistration => Boolean): Set[String]
```

### CaskServer
- Create single ServerStateService instance at startup
- All endpoints use service methods, no direct repository access
- Dashboard: read-only, no writes
- Per-card: call service.updateXxxCache() for each cache type

### WorktreeCardService
- Return all refreshed cache entries (issue, progress, PR, reviewState)
- Caller (CaskServer) updates each via ServerStateService

### DashboardService
- Remove reviewStateCache computation
- Just render from provided cached state
- Become pure view renderer

## Constraints

- PRESERVE: All existing tests must pass
- PRESERVE: Domain model structure (ServerState, CachedXxx types)
- PRESERVE: HTMX/CSS presentation layer
- PRESERVE: API contract (endpoints, response format)
- DO NOT TOUCH: Pure domain services (WorktreeRegistrationService, etc.)

## Tasks

### 6a: Fix Immediate Corruption
- [ ] [impl] StateRepository: Use `state.json.tmp-${UUID.randomUUID()}` for temp file
- [ ] [impl] StateRepository: Delete temp file on failure (cleanup)
- [ ] [test] Add test for concurrent writes don't corrupt

### 6b: Create ServerStateService
- [ ] [impl] Create ServerStateService class with ReentrantLock
- [ ] [impl] Add initialize() to load state from repository
- [ ] [impl] Add getState for reads
- [ ] [impl] Add updateWorktree() with lock + persist
- [ ] [impl] Add updateIssueCache() with lock + persist
- [ ] [impl] Add updateProgressCache() with lock + persist
- [ ] [impl] Add updatePRCache() with lock + persist
- [ ] [impl] Add updateReviewStateCache() with lock + persist
- [ ] [impl] Add pruneWorktrees() with lock + persist
- [ ] [test] Unit tests for each update method
- [ ] [test] Test concurrent updates don't lose data

### 6c: Migrate CaskServer
- [ ] [impl] Create ServerStateService instance at startup, call initialize()
- [ ] [impl] Dashboard endpoint: use getState, remove all writes
- [ ] [impl] Per-card endpoint: use update methods for all caches
- [ ] [impl] Register endpoint: use updateWorktree
- [ ] [impl] Unregister endpoint: use updateWorktree (remove)
- [ ] [impl] Remove direct repository usage from all endpoints

### 6d: Update WorktreeCardService
- [ ] [impl] Add reviewState refresh to renderCard
- [ ] [impl] Return all cache updates (issue, progress, PR, reviewState)
- [ ] [impl] CaskServer calls update methods for each returned cache entry
- [ ] [test] Test that card refresh returns reviewState

### 6e: Simplify DashboardService
- [ ] [impl] Remove reviewStateCache computation from renderDashboard
- [ ] [impl] Just render from cached data
- [ ] [impl] Return only HTML, not (HTML, cache) tuple
- [ ] [test] Update tests for new signature

### 6f: Verification
- [ ] [test] Integration test: concurrent requests don't corrupt state
- [ ] [test] Integration test: card refresh updates review state
- [ ] [manual] Start server, register 5 worktrees, reload page rapidly
- [ ] [manual] Verify state.json is valid JSON after reload
- [ ] [manual] Verify review state changes appear without full refresh

## Verification Checklist

- [ ] All 1186+ existing tests pass
- [ ] No JSON corruption after rapid page reloads
- [ ] No worktree data loss after server restart
- [ ] Review state badge updates via HTMX (no full refresh needed)
- [ ] Dashboard still loads in < 100ms
- [ ] Per-card refresh still works with 30s throttle

## Migration Notes

The migration can be done incrementally:
1. First fix temp file naming (immediate safety)
2. Create ServerStateService with tests
3. Migrate endpoints one by one
4. Update WorktreeCardService last (most complex)

Each step should be a separate commit for easy rollback.
