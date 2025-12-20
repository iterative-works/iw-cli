# Phase 7 Tasks: Unregister worktrees when removed

**Issue:** IWLE-100
**Phase:** 7 of 7
**Created:** 2025-12-20
**Status:** Complete

---

## Task Checklist

### Setup (1 task)

- [ ] [setup] Review existing ServerState, StateRepository, ServerClient implementations
- [ ] [setup] Verify rm.scala command structure and worktree removal flow

### Domain Layer: ServerState Extension (3 tasks)

- [x] [test] Write unit test: ServerState.removeWorktree removes entry from worktrees map
- [x] [test] Write unit test: ServerState.removeWorktree removes entry from all cache maps (issueCache, progressCache, prCache)
- [x] [test] Write unit test: ServerState.removeWorktree is idempotent for non-existent issueId
- [x] [impl] Implement ServerState.removeWorktree method

### Application Layer: Unregistration Service (10 tasks)

- [x] [setup] Create WorktreeUnregistrationService.scala file with purpose comment
- [x] [test] Write unit test: unregister returns Right with updated state when worktree exists
- [x] [test] Write unit test: unregister returns Left with error when worktree not found
- [x] [test] Write unit test: unregister removes worktree from state.worktrees
- [x] [test] Write unit test: unregister removes associated issue cache entry
- [x] [test] Write unit test: unregister removes associated progress cache entry
- [x] [test] Write unit test: unregister removes associated PR cache entry
- [x] [impl] Implement WorktreeUnregistrationService.unregister method
- [x] [test] Write unit test: pruneNonExistent removes worktrees with missing paths
- [x] [test] Write unit test: pruneNonExistent keeps worktrees with existing paths
- [x] [test] Write unit test: pruneNonExistent removes associated caches for pruned worktrees
- [x] [test] Write unit test: pruneNonExistent handles empty state gracefully
- [x] [impl] Implement WorktreeUnregistrationService.pruneNonExistent method

### Infrastructure Layer: DELETE Endpoint (4 tasks)

- [x] [test] Write integration test: DELETE /api/v1/worktrees/:issueId returns 200 and removes worktree
- [x] [test] Write integration test: DELETE /api/v1/worktrees/:issueId returns 404 for non-existent worktree
- [x] [test] Write integration test: DELETE endpoint removes associated cache entries
- [x] [impl] Implement DELETE /api/v1/worktrees/:issueId endpoint in CaskServer

### Infrastructure Layer: ServerClient Extension (4 tasks)

- [x] [test] Write integration test: ServerClient.unregisterWorktree returns Right on successful DELETE
- [x] [test] Write integration test: ServerClient.unregisterWorktree returns Left on 404
- [x] [test] Write integration test: ServerClient.unregisterWorktree returns Left on server unavailable
- [x] [impl] Implement ServerClient.unregisterWorktree method

### Application Layer: Auto-Pruning Integration (3 tasks)

- [x] [test] Write integration test: DashboardService auto-prunes non-existent worktrees on state load
- [x] [test] Write integration test: DashboardService saves pruned state when changes detected
- [x] [impl] Integrate pruneNonExistent into DashboardService state loading

### CLI Integration: rm.scala Modification (3 tasks)

- [x] [test] Write E2E test: iw rm unregisters worktree after successful removal
- [x] [test] Write E2E test: iw rm succeeds with warning when server unavailable
- [x] [impl] Add ServerClient.unregisterWorktree call to rm.scala after worktree removal

### E2E Verification (3 tasks)

- [x] [test] E2E scenario: Run iw rm ISSUE-123, verify worktree disappears from dashboard
- [x] [test] E2E scenario: Manually delete worktree directory, refresh dashboard, verify auto-pruned
- [x] [test] E2E scenario: Remove worktree with server stopped, verify command succeeds with warning

---

## Task Summary

**Total Tasks:** 31
- Setup: 2 tasks
- Tests: 22 tasks (unit + integration + E2E)
- Implementation: 7 tasks

**Estimated Duration:** 3-4 hours

---

## Implementation Notes

- Follow TDD: Write failing test before implementation
- All pure functions should be in WorktreeUnregistrationService
- Effects (HTTP, filesystem) should be in infrastructure layer
- Unregistration is best-effort - failures don't break iw rm
- Auto-pruning runs on every dashboard state load
- Cache cleanup is atomic with worktree removal

---

## Success Criteria

- [x] DELETE endpoint removes worktree and all associated caches
- [x] iw rm command calls unregister after successful removal
- [x] Failed unregistration prints warning but doesn't fail command
- [x] Dashboard auto-prunes non-existent worktrees on load
- [x] All unit tests pass
- [x] All integration tests pass
- [x] All E2E tests pass

---

## Refactoring

- [ ] [impl] [ ] [reviewed] Refactoring R1: Fix path handling and build system

---

**Next Step:** Start with Setup tasks, then follow TDD for each component.
