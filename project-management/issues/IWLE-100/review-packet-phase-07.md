---
generated_from: a885c9f78445e7845ffb18cc552a7c0ed5c22329
generated_at: 2025-12-20T17:20:54Z
branch: IWLE-100-phase-07
issue_id: IWLE-100
phase: 7
files_analyzed:
  - .iw/commands/rm.scala
  - .iw/core/CaskServer.scala
  - .iw/core/ServerClient.scala
  - .iw/core/ServerState.scala
  - .iw/core/WorktreeUnregistrationService.scala
  - .iw/core/test/CaskServerTest.scala
  - .iw/core/test/ServerStateTest.scala
  - .iw/core/test/WorktreeUnregistrationServiceTest.scala
---

# Phase 7: Unregister worktrees when removed

## Goals

This phase completes the dashboard feature by ensuring worktrees are removed from the dashboard when they are deleted. This is the cleanup phase that keeps the dashboard current and accurate.

Key objectives:
- Add DELETE endpoint (`/api/v1/worktrees/:issueId`) for explicit worktree unregistration
- Integrate unregistration into the `iw rm` command as best-effort cleanup
- Implement auto-pruning of non-existent worktrees when dashboard loads
- Clean removal of associated caches (issue cache, progress cache, PR cache)

## Scenarios

Review that these scenarios work correctly:

- [ ] User runs `iw rm ISSUE-123` and worktree disappears from dashboard immediately
- [ ] When unregistration fails (server down), `iw rm` command still succeeds with warning
- [ ] Manually deleted worktree directories are automatically pruned from dashboard on next page load
- [ ] DELETE endpoint returns 404 for non-existent worktrees (graceful handling)
- [ ] Unregistering a worktree removes all associated caches (issue, progress, PR)
- [ ] Auto-pruning only removes worktrees whose filesystem paths no longer exist

## Entry Points

Start your review from these locations:

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/WorktreeUnregistrationService.scala` | `unregister()` | Pure function implementing core unregistration logic |
| `.iw/core/CaskServer.scala` | `@cask.delete("/api/v1/worktrees/:issueId")` | HTTP endpoint for explicit worktree removal |
| `.iw/commands/rm.scala` | Lines 89-94 | CLI integration showing best-effort unregistration |
| `.iw/core/CaskServer.scala` | `dashboard()` (lines 19-27) | Auto-pruning integration on state load |
| `.iw/core/ServerState.scala` | `removeWorktree()` | Domain method removing worktree and all caches |

## Architecture Overview

This diagram shows where the unregistration components fit within the system architecture.

```mermaid
C4Context
    title Phase 7: Worktree Unregistration Architecture

    Person(user, "Developer", "Removes completed worktree")
    
    System_Boundary(cli, "iw CLI") {
        Component(rm_cmd, "rm.scala", "Command", "Removes worktree via git")
    }
    
    System_Boundary(server, "Dashboard Server") {
        Component(client, "ServerClient", "HTTP Client", "Sends DELETE request")
        Component(endpoint, "DELETE endpoint", "API", "Unregisters worktree<br/><i>new</i>")
        Component(unreg_svc, "UnregistrationService", "Service", "Unregister logic<br/><i>new</i>")
        Component(prune, "Auto-pruning", "Feature", "Prunes stale worktrees<br/><i>new</i>")
        Component(state_repo, "StateRepository", "Repository", "Persists state")
    }
    
    System_Boundary(domain, "Domain") {
        Component(server_state, "ServerState", "Model", "removeWorktree()<br/><i>modified</i>")
    }

    Rel(user, rm_cmd, "Runs iw rm")
    Rel(rm_cmd, client, "Best-effort unregister")
    Rel(client, endpoint, "HTTP DELETE")
    Rel(endpoint, unreg_svc, "Uses")
    Rel(unreg_svc, server_state, "Calls removeWorktree()")
    Rel(endpoint, state_repo, "Saves updated state")
    Rel(prune, unreg_svc, "Uses pruneNonExistent()")
    Rel(prune, state_repo, "Saves pruned state")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="2")
```

**Key points for reviewer:**
- Unregistration is best-effort - failures don't break `iw rm`
- Auto-pruning happens on every dashboard page load
- All cache cleanup is atomic with worktree removal

## Component Relationships

```mermaid
flowchart TB
    subgraph "Application Layer (Pure Logic)"
        unreg[WorktreeUnregistrationService<br/><i>new</i>]
        unreg_fn["unregister(state, issueId)"]
        prune_fn["pruneNonExistent(state, pathExists)"]
    end

    subgraph "Domain Layer"
        state[ServerState<br/><i>modified</i>]
        remove_fn["removeWorktree(issueId)"]
    end

    subgraph "Infrastructure Layer (Effects)"
        endpoint[DELETE /api/v1/worktrees/:issueId<br/><i>new</i>]
        client[ServerClient.unregisterWorktree<br/><i>new</i>]
        dashboard_route[GET / dashboard route<br/><i>modified</i>]
        repo[(StateRepository)]
    end

    subgraph "CLI Layer"
        rm_cmd[rm.scala<br/><i>modified</i>]
    end

    unreg --> unreg_fn
    unreg --> prune_fn
    unreg_fn --> remove_fn
    prune_fn --> remove_fn
    state --> remove_fn

    endpoint --> unreg_fn
    endpoint --> repo
    dashboard_route --> prune_fn
    dashboard_route --> repo
    client --> endpoint
    rm_cmd --> client

    classDef new fill:#e1f5e1,stroke:#4caf50,stroke-width:2px
    classDef modified fill:#fff3cd,stroke:#ffc107,stroke-width:2px
    
    class unreg,unreg_fn,prune_fn,endpoint,client new
    class state,remove_fn,dashboard_route,rm_cmd modified
```

**Key points for reviewer:**
- Pure functions in WorktreeUnregistrationService delegate to ServerState.removeWorktree
- Infrastructure layer handles effects (HTTP, filesystem checks)
- Functional Core / Imperative Shell pattern maintained

## Key Flow: Explicit Unregistration via iw rm

```mermaid
sequenceDiagram
    actor User
    participant CLI as rm.scala
    participant Client as ServerClient
    participant Server as DELETE endpoint
    participant Service as UnregistrationService
    participant State as ServerState
    participant Repo as StateRepository

    User->>CLI: iw rm ISSUE-123
    CLI->>CLI: Remove git worktree
    Note over CLI: Worktree removed successfully
    
    CLI->>Client: unregisterWorktree("ISSUE-123")
    Client->>Server: DELETE /api/v1/worktrees/ISSUE-123
    Server->>Repo: read()
    Repo-->>Server: Right(state)
    Server->>Service: unregister(state, "ISSUE-123")
    Service->>State: removeWorktree("ISSUE-123")
    State-->>Service: newState (without ISSUE-123)
    Service-->>Server: Right(newState)
    Server->>Repo: write(newState)
    Repo-->>Server: Right(())
    Server-->>Client: 200 OK
    Client-->>CLI: Right(())
    CLI->>User: "Unregistered from dashboard"
```

**Key points for reviewer:**
- Unregistration happens AFTER git worktree removal succeeds
- If unregistration fails, warning is printed but command exits successfully
- State update is atomic - worktree and all caches removed together

## Key Flow: Auto-Pruning on Dashboard Load

```mermaid
sequenceDiagram
    actor User
    participant Browser
    participant Dashboard as GET / route
    participant Service as UnregistrationService
    participant State as ServerState
    participant Repo as StateRepository
    participant FS as Filesystem

    User->>Browser: Refresh dashboard
    Browser->>Dashboard: GET /
    Dashboard->>Repo: read()
    Repo-->>Dashboard: Right(rawState)
    
    Note over Dashboard: Auto-prune stale worktrees
    Dashboard->>Service: pruneNonExistent(rawState, pathExists)
    loop For each worktree
        Service->>FS: os.exists(path)?
        FS-->>Service: true/false
    end
    Service->>State: removeWorktree(id) for missing paths
    State-->>Service: prunedState
    Service-->>Dashboard: prunedState
    
    alt State changed
        Dashboard->>Repo: write(prunedState)
        Note over Dashboard: Best-effort save, ignore errors
    end
    
    Dashboard->>Dashboard: renderDashboard(prunedState)
    Dashboard-->>Browser: HTML
    Browser->>User: Display current worktrees
```

**Key points for reviewer:**
- Pruning runs on EVERY dashboard page load
- Only worktrees with non-existent paths are removed
- State save is best-effort - dashboard renders even if save fails
- Cache cleanup is automatic when pruning worktrees

## Test Summary

| Test | Type | Verifies |
|------|------|----------|
| `ServerState.removeWorktree removes entry from worktrees map` | Unit | Domain method removes worktree |
| `ServerState.removeWorktree removes entry from all cache maps` | Unit | Cache cleanup (issue, progress, PR) |
| `ServerState.removeWorktree is idempotent for non-existent issueId` | Unit | Safe to call with missing IDs |
| `unregister returns Right when worktree exists` | Unit | Success path for unregister |
| `unregister returns Left when worktree not found` | Unit | Error handling for missing worktree |
| `unregister removes associated issue cache entry` | Unit | Issue cache cleanup |
| `unregister removes associated progress cache entry` | Unit | Progress cache cleanup |
| `unregister removes associated PR cache entry` | Unit | PR cache cleanup |
| `pruneNonExistent removes worktrees with missing paths` | Unit | Auto-pruning removes stale entries |
| `pruneNonExistent keeps worktrees with existing paths` | Unit | Auto-pruning preserves valid entries |
| `pruneNonExistent removes associated caches for pruned worktrees` | Unit | Cache cleanup during pruning |
| `pruneNonExistent handles empty state gracefully` | Unit | Edge case handling |
| `DELETE /api/v1/worktrees/:issueId returns 200 and removes worktree` | Integration | HTTP endpoint success path |
| `DELETE /api/v1/worktrees/:issueId returns 404 for non-existent` | Integration | HTTP 404 error handling |
| `DELETE endpoint removes associated cache entries` | Integration | End-to-end cache removal via API |

**Coverage:** 15 tests (12 unit + 3 integration)
- Pure logic: Comprehensive unit test coverage for all scenarios
- API layer: Integration tests verify HTTP contract and persistence
- E2E: Manual verification via `iw rm` command and dashboard inspection

## Files Changed

**6 files** changed, +318 insertions, -2 deletions

<details>
<summary>Full file list with descriptions</summary>

### New Files

- `.iw/core/WorktreeUnregistrationService.scala` (A) +43 lines
  - Pure functions for unregistering worktrees
  - Implements `unregister(state, issueId)` and `pruneNonExistent(state, pathExists)`
  - No side effects - delegates to ServerState.removeWorktree

- `.iw/core/test/WorktreeUnregistrationServiceTest.scala` (A) +265 lines
  - Unit tests for WorktreeUnregistrationService
  - Tests all unregister scenarios (success, failure, cache cleanup)
  - Tests all pruning scenarios (missing paths, existing paths, empty state)

### Modified Files

- `.iw/core/ServerState.scala` (M) +8 lines
  - Added `removeWorktree(issueId)` method
  - Removes worktree and all associated caches atomically
  - Idempotent operation (safe to call multiple times)

- `.iw/core/CaskServer.scala` (M) +42 lines, -2 lines
  - Added DELETE `/api/v1/worktrees/:issueId` endpoint
  - Integrated auto-pruning into dashboard route (lines 19-27)
  - Returns 200 on success, 404 if not found, 500 on errors

- `.iw/core/ServerClient.scala` (M) +23 lines
  - Added `unregisterWorktree(issueId)` method
  - Sends DELETE request to server
  - Treats 404 as success (already removed)

- `.iw/commands/rm.scala` (M) +9 lines
  - Added best-effort unregistration call after worktree removal
  - Prints warning on failure but doesn't fail command
  - Lines 89-94: ServerClient.unregisterWorktree integration

### Test Files

- `.iw/core/test/ServerStateTest.scala` (M) +74 lines
  - Added 3 tests for ServerState.removeWorktree method
  - Tests worktree removal, cache cleanup, idempotency

- `.iw/core/test/CaskServerTest.scala` (M) +162 lines
  - Added 3 integration tests for DELETE endpoint
  - Tests success (200), not found (404), cache removal

</details>

## Implementation Decisions

### 1. Best-Effort Unregistration

**Decision:** `iw rm` prints a warning but succeeds even if unregistration fails.

**Rationale:**
- The primary goal of `iw rm` is to remove the git worktree
- Dashboard state is secondary - should not block worktree removal
- Server might be down, restarting, or misconfigured
- Auto-pruning will clean up stale entries anyway

**Review points:**
- Check lines 89-94 in `rm.scala` - warning printed on Left, no exit
- Verify warning message is clear and actionable

### 2. Auto-Pruning on Every Dashboard Load

**Decision:** Prune non-existent worktrees on every GET / request, not on a timer.

**Rationale:**
- Simpler implementation - no background jobs or timers needed
- Pruning is fast - just filesystem checks for registered worktrees
- Dashboard is typically only accessed a few times per day
- Ensures dashboard is always current when user views it

**Review points:**
- Check CaskServer.scala lines 19-27 for pruning logic
- Verify `os.exists(os.Path(path, os.pwd))` is correct filesystem check
- Confirm state save is best-effort (line 27 - no error handling blocks render)

### 3. Atomic Cache Cleanup

**Decision:** ServerState.removeWorktree removes ALL caches in one operation.

**Rationale:**
- Prevents orphaned cache entries (issue data, progress, PRs)
- Simpler than tracking which caches exist for each worktree
- Map removal is O(1), so removing from all caches is cheap
- Safe - Map.remove is idempotent for non-existent keys

**Review points:**
- Check ServerState.scala lines 15-21 - single copy() with all cache removals
- Verify idempotency (removing non-existent key is safe)
- Confirm all three caches are removed (issueCache, progressCache, prCache)

### 4. 404 Treated as Success

**Decision:** ServerClient treats 404 from DELETE as Right(()).

**Rationale:**
- Idempotent semantics: "ensure worktree is not registered"
- Handles race conditions (worktree already pruned by dashboard)
- Matches REST semantics - DELETE is idempotent
- Simplifies client code - no special handling needed

**Review points:**
- Check ServerClient.scala line 176 - `case StatusCode.NotFound => Right(())`
- Verify comment explains this is intentional
- Consider if 404 should be logged (currently silent)

### 5. Functional Core / Imperative Shell

**Decision:** Pure logic in WorktreeUnregistrationService, effects in infrastructure layer.

**Rationale:**
- Follows project architecture guidelines
- Pure functions are easier to test (no mocks needed)
- Effects isolated to endpoints and client
- Domain remains pure (ServerState.removeWorktree is a copy())

**Review points:**
- Verify WorktreeUnregistrationService has no side effects
- Check that filesystem exists check is injected as function parameter
- Confirm tests don't mock anything - just call functions with test data

## Review Checklist

Use this checklist to guide your review:

- [ ] **Pure Functions:** WorktreeUnregistrationService functions are pure (no IO, no mutation)
- [ ] **Error Handling:** DELETE endpoint returns proper HTTP codes (200, 404, 500)
- [ ] **Best-Effort:** `iw rm` succeeds even if unregistration fails (lines 89-94 in rm.scala)
- [ ] **Cache Cleanup:** All three caches removed in ServerState.removeWorktree
- [ ] **Auto-Pruning:** Dashboard route integrates pruning before rendering (lines 19-27 in CaskServer)
- [ ] **Idempotency:** ServerState.removeWorktree safe to call with non-existent ID
- [ ] **Test Coverage:** All scenarios covered by unit tests (12 tests total)
- [ ] **Integration Tests:** DELETE endpoint tested end-to-end (3 tests)
- [ ] **Documentation:** Purpose comments explain what files do (not implementation details)

## Notes for Reviewers

This is the final phase of IWLE-100. The complete feature set now includes:

1. **Phase 1-3:** Dashboard server with worktree registration
2. **Phase 4:** Issue data caching
3. **Phase 5:** Progress tracking
4. **Phase 6:** PR status display
5. **Phase 7:** Worktree cleanup (this phase)

The dashboard is now feature-complete:
- Registers worktrees when created (`iw add`)
- Updates last-seen timestamp on `iw cd`
- Displays issue data, progress, and PR status
- Auto-refreshes every 30 seconds
- Cleans up when worktrees are removed (`iw rm`)
- Auto-prunes stale entries

**What to look for in this phase:**
- Clean separation of pure logic and effects
- Graceful degradation (best-effort unregistration)
- Atomic operations (cache cleanup with worktree removal)
- Idempotent operations (safe to retry)
- Comprehensive test coverage

**Potential issues to watch for:**
- Race condition between pruning and user viewing stale data (acceptable tradeoff)
- Performance of pruning on large worktree counts (mitigated by infrequent dashboard access)
- Error handling in auto-pruning (currently best-effort save)
