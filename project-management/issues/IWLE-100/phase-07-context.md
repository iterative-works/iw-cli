# Phase 7 Context: Unregister worktrees when removed

**Issue:** IWLE-100
**Phase:** 7 of 7
**Status:** Ready for Implementation

---

## Goals

This phase completes the dashboard feature by ensuring worktrees are removed from the dashboard when they are deleted. This is the cleanup phase that keeps the dashboard current and accurate.

**Primary objectives:**
1. Add unregister API endpoint (`DELETE /api/v1/worktrees/:issueId`)
2. Integrate unregistration into the `iw rm` command
3. Implement auto-pruning of non-existent worktrees on dashboard load

---

## Scope

### In Scope
- DELETE endpoint for explicit worktree unregistration
- Modify `rm.scala` command to call unregister after successful worktree removal
- Auto-prune logic that removes stale worktrees on state load (directory doesn't exist)
- Best-effort unregistration (failures don't break `iw rm`)
- Clean removal of associated caches (issue cache, progress cache, PR cache)

### Out of Scope
- Manual dashboard UI for unregistering worktrees (no delete button)
- Archiving worktrees instead of deleting
- Batch unregistration APIs
- Worktree recovery/undo functionality

---

## Dependencies

### From Previous Phases
- **Phase 1:** `ServerState`, `StateRepository`, `WorktreeRegistration` - state management and persistence
- **Phase 2:** `ServerClient`, PUT endpoint pattern, best-effort registration pattern
- **Phase 3:** Port configuration, server lifecycle
- **Phase 4:** Issue cache in state
- **Phase 5:** Progress cache in state
- **Phase 6:** PR cache in state

### Required Files
- `.iw/commands/rm.scala` - exists, needs modification for unregistration
- `.iw/core/CaskServer.scala` - exists, needs DELETE endpoint
- `.iw/core/ServerState.scala` - exists, needs removeWorktree method
- `.iw/core/ServerClient.scala` - exists, needs unregisterWorktree method
- `.iw/core/StateRepository.scala` - exists, no changes needed

---

## Technical Approach

### 1. Domain Layer: ServerState Extension

Add method to remove a worktree and its associated caches:

```scala
// In ServerState.scala
def removeWorktree(issueId: String): ServerState =
  copy(
    worktrees = worktrees - issueId,
    issueCache = issueCache - issueId,
    progressCache = progressCache - issueId,
    prCache = prCache - issueId
  )
```

### 2. Application Layer: Unregistration Service

Create pure functions for unregistration logic:

```scala
// WorktreeUnregistrationService.scala
object WorktreeUnregistrationService:

  /** Remove a worktree from state */
  def unregister(state: ServerState, issueId: String): Either[String, ServerState] =
    if state.worktrees.contains(issueId) then
      Right(state.removeWorktree(issueId))
    else
      Left(s"Worktree not found: $issueId")

  /** Prune worktrees whose paths no longer exist */
  def pruneNonExistent(
    state: ServerState,
    pathExists: String => Boolean
  ): ServerState =
    val validWorktrees = state.worktrees.filter { (_, wt) =>
      pathExists(wt.path)
    }
    val removedIds = state.worktrees.keySet -- validWorktrees.keySet

    removedIds.foldLeft(state.copy(worktrees = validWorktrees)) { (s, id) =>
      s.copy(
        issueCache = s.issueCache - id,
        progressCache = s.progressCache - id,
        prCache = s.prCache - id
      )
    }
```

### 3. Infrastructure Layer: DELETE Endpoint

Add to CaskServer:

```scala
@cask.delete("/api/v1/worktrees/:issueId")
def unregisterWorktree(issueId: String): cask.Response[ujson.Value] =
  stateRepository.load() match
    case Right(state) =>
      WorktreeUnregistrationService.unregister(state, issueId) match
        case Right(newState) =>
          stateRepository.save(newState) match
            case Right(_) =>
              cask.Response(
                ujson.Obj("status" -> "ok", "issueId" -> issueId),
                statusCode = 200
              )
            case Left(err) =>
              cask.Response(
                ujson.Obj("code" -> "SAVE_ERROR", "message" -> err),
                statusCode = 500
              )
        case Left(err) =>
          cask.Response(
            ujson.Obj("code" -> "NOT_FOUND", "message" -> err),
            statusCode = 404
          )
    case Left(err) =>
      cask.Response(
        ujson.Obj("code" -> "LOAD_ERROR", "message" -> err),
        statusCode = 500
      )
```

### 4. Infrastructure Layer: ServerClient Extension

Add unregistration method to ServerClient:

```scala
// In ServerClient.scala
def unregisterWorktree(issueId: String): Either[String, Unit] =
  for
    port <- loadPort()
    response <- request(s"http://localhost:$port/api/v1/worktrees/$issueId", "DELETE")
  yield ()
```

### 5. CLI Integration: rm.scala Modification

Add unregistration call after successful worktree removal:

```scala
// After worktree is successfully removed
ServerClient.unregisterWorktree(issueId) match
  case Right(_) =>
    println(s"Unregistered worktree from dashboard")
  case Left(err) =>
    System.err.println(s"Warning: Failed to unregister from dashboard: $err")
// Continue with successful exit - unregister is best-effort
```

### 6. Auto-Pruning Integration

Integrate pruning into state loading in DashboardService:

```scala
// In DashboardService, when loading state for dashboard render
val rawState = stateRepository.load().getOrElse(ServerState.empty)
val prunedState = WorktreeUnregistrationService.pruneNonExistent(
  rawState,
  path => os.exists(os.Path(path))
)
if prunedState != rawState then
  stateRepository.save(prunedState) // Best-effort save
prunedState // Use pruned state for rendering
```

---

## Files to Modify

### Existing Files
1. `.iw/core/ServerState.scala` - Add `removeWorktree()` method
2. `.iw/core/CaskServer.scala` - Add DELETE endpoint
3. `.iw/core/ServerClient.scala` - Add `unregisterWorktree()` method
4. `.iw/core/DashboardService.scala` - Add auto-prune on state load
5. `.iw/commands/rm.scala` - Add unregistration call

### New Files
1. `.iw/core/WorktreeUnregistrationService.scala` - Unregistration logic
2. `.iw/core/test/WorktreeUnregistrationServiceTest.scala` - Unit tests

---

## Testing Strategy

### Unit Tests

**WorktreeUnregistrationServiceTest.scala:**
1. `unregister` returns Right with updated state when worktree exists
2. `unregister` returns Left with error when worktree not found
3. `unregister` removes worktree from state.worktrees
4. `unregister` removes associated issue cache entry
5. `unregister` removes associated progress cache entry
6. `unregister` removes associated PR cache entry
7. `pruneNonExistent` removes worktrees with missing paths
8. `pruneNonExistent` keeps worktrees with existing paths
9. `pruneNonExistent` removes associated caches for pruned worktrees
10. `pruneNonExistent` handles empty state gracefully

**ServerStateTest.scala (additions):**
1. `removeWorktree` removes entry from worktrees map
2. `removeWorktree` removes entry from all cache maps
3. `removeWorktree` is idempotent for non-existent issueId

### Integration Tests

**CaskServerTest.scala (additions):**
1. DELETE `/api/v1/worktrees/:issueId` returns 200 and removes worktree
2. DELETE `/api/v1/worktrees/:issueId` returns 404 for non-existent worktree
3. DELETE endpoint removes associated cache entries

**ServerClientTest.scala (additions):**
1. `unregisterWorktree` returns Right on successful DELETE
2. `unregisterWorktree` returns Left on 404
3. `unregisterWorktree` returns Left on server unavailable

### E2E Scenarios

1. Run `iw rm ISSUE-123` → worktree disappears from dashboard
2. Manually delete worktree directory → refresh dashboard → worktree pruned
3. Remove worktree with server stopped → command succeeds with warning

---

## Acceptance Criteria

From Story 7 in analysis.md:

1. ✓ `iw rm` unregisters worktree after successful removal
2. ✓ Non-existent paths pruned automatically on dashboard load
3. ✓ Manually deleted worktrees disappear within 30s (next auto-refresh triggers prune)
4. ✓ Failed unregistration doesn't break `iw rm` command

---

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Server unavailable during `iw rm` | Warning printed, command succeeds |
| DELETE returns 404 | Silent success (already removed) |
| DELETE returns 500 | Warning printed, command succeeds |
| State load fails during prune | Skip pruning, render with last known state |
| State save fails after prune | Log warning, continue with pruned state in memory |

---

## Implementation Order

1. **ServerState.removeWorktree()** - Pure function, no dependencies
2. **WorktreeUnregistrationService** - Pure functions for unregister and prune
3. **Unit tests** - Test pure logic
4. **CaskServer DELETE endpoint** - Infrastructure layer
5. **ServerClient.unregisterWorktree()** - Client method
6. **Integration tests** - Test endpoint
7. **DashboardService auto-prune** - Integrate pruning
8. **rm.scala modification** - CLI integration
9. **E2E verification** - Manual testing scenarios

---

## Notes for Implementation

- All new code should follow FCIS (Functional Core, Imperative Shell) pattern
- Pruning should be efficient - only check paths for worktrees we're rendering
- The prune-on-load approach means no separate cleanup timer is needed
- Cache cleanup is atomic with worktree removal to prevent orphaned entries
- Consider logging pruned worktrees for debugging (not user-facing)

---

## Refactoring Decisions

### R1: Fix path handling and build system (2025-12-20)

**Trigger:** When testing the dashboard server, it crashed with `os.PathError$InvalidSegment` because `Constants.Paths.ConfigFile = ".iw/config.conf"` contains a `/` character, which `os-lib` doesn't allow in a single path segment. Additionally, the `//> using file` directives in command scripts use relative paths that resolve incorrectly when scripts are not run from the expected directory.

**Decision:** Two related fixes:
1. Fix path construction in `CaskServer.scala` to properly handle multi-segment paths
2. Replace `//> using file` directives with classpath-based approach for more reliable dependency resolution

**Scope:**
- Files affected: `CaskServer.scala`, `Constants.scala`, all command scripts in `.iw/commands/`
- Components: Path construction, build/dependency resolution
- Boundaries: Do NOT change business logic, only build and path handling

**Approach:**
1. Split `Constants.Paths.ConfigFile` into separate directory and filename components
2. Update path construction in CaskServer.scala to use proper `os-lib` path operations
3. Remove ALL `//> using file` directives from command scripts - the `./iw` wrapper already passes core files via glob
4. Update `./iw` wrapper to exclude test directory from glob (use `find -maxdepth 1` instead of `*.scala`)
