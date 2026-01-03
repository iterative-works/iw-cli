# Phase 4: Concurrent Creation Protection - Tasks

**Issue:** IW-79
**Phase:** 4 of 4
**Status:** Complete
**Estimated:** 2-3 hours

## Task Groups

### Group A: Domain Lock Model
- [x] [test] Write tests for CreationLock case class construction
- [x] [impl] Create CreationLock.scala with issueId and startedAt fields

### Group B: Error Type Extension
- [x] [test] Write tests for WorktreeCreationError.CreationInProgress pattern matching
- [x] [impl] Add CreationInProgress case to WorktreeCreationError enum
- [x] [test] Write tests for CreationInProgress → UserFriendlyError mapping
- [x] [impl] Add toUserFriendly mapping for CreationInProgress

### Group C: Lock Registry
- [x] [test] Write tests for CreationLockRegistry.tryAcquire returns true when not locked
- [x] [test] Write tests for CreationLockRegistry.tryAcquire returns false when already locked
- [x] [test] Write tests for CreationLockRegistry.release allows subsequent acquire
- [x] [test] Write tests for CreationLockRegistry.isLocked returns correct state
- [x] [test] Write tests for CreationLockRegistry.cleanupExpired removes old locks
- [x] [impl] Create CreationLockRegistry.scala with ConcurrentHashMap-based registry

### Group D: Service Layer Integration
- [x] [test] Write tests for WorktreeCreationService.createWithLock acquiring lock
- [x] [test] Write tests for WorktreeCreationService.createWithLock returning CreationInProgress when locked
- [x] [test] Write tests for WorktreeCreationService.createWithLock releasing lock on success
- [x] [test] Write tests for WorktreeCreationService.createWithLock releasing lock on failure
- [x] [impl] Add createWithLock method to WorktreeCreationService wrapping create with lock

### Group E: API Layer Integration
- [x] [test] Write tests for POST /api/worktrees/create returning 423 for CreationInProgress
- [x] [test] Write tests for error response containing CreationErrorView HTML for 423
- [x] [impl] Update CaskServer to use createWithLock instead of create
- [x] [impl] Add 423 Locked status mapping for CreationInProgress error

### Group F: UI State Management
- [x] [test] Write tests for SearchResultsView adding hx-on::before-request for disabling
- [x] [test] Write tests for SearchResultsView adding hx-on::after-request for re-enabling
- [x] [impl] Add hx-on attributes to SearchResultsView for UI disabling during creation
- [x] [impl] Add CSS disabled state styles to DashboardService

### Group G: Integration and Manual Testing
- [ ] [test] E2E test: Click issue → UI disabled during creation
- [ ] [test] E2E test: Creation completes → UI re-enabled
- [ ] [test] E2E test: Rapid double-click → only one creation attempt
- [ ] [manual] Manually test UI disabling in browser
- [ ] [manual] Verify lock prevents concurrent API requests (curl test)
- [ ] [manual] Verify lock cleanup after timeout

## Notes

### TDD Workflow
Each group follows TDD cycle:
1. Write failing test
2. Run test to confirm failure
3. Write minimal implementation to pass
4. Run test to confirm success
5. Refactor if needed

### HTTP Status Mapping
```scala
CreationInProgress → 423 Locked
```

### HTMX Event Attributes
```html
hx-on::before-request="this.classList.add('disabled')"
hx-on::after-request="this.classList.remove('disabled')"
```

### CSS Disabled State
```css
.disabled {
  pointer-events: none;
  opacity: 0.5;
}
```

### Key Files
- `.iw/core/domain/CreationLock.scala` (new)
- `.iw/core/infrastructure/CreationLockRegistry.scala` (new)
- `.iw/core/domain/WorktreeCreationError.scala` (modify)
- `.iw/core/application/WorktreeCreationService.scala` (modify)
- `.iw/core/CaskServer.scala` (modify)
- `.iw/core/presentation/views/SearchResultsView.scala` (modify)
- `.iw/core/DashboardService.scala` (modify)

### Acceptance Criteria
- [ ] UI prevents interaction during worktree creation
- [ ] Server prevents concurrent creation for same issue
- [ ] Lock cleaned up on completion (success or failure)
- [ ] Lock cleaned up on timeout (30 seconds)
- [ ] "Creation in progress" error shows appropriate message
- [ ] HTTP 423 status returned for locked creation attempts
- [ ] All unit, integration, and E2E tests passing

### Estimated Time Breakdown
- Group A (Lock Model): 10 min
- Group B (Error Type): 15 min
- Group C (Lock Registry): 30 min
- Group D (Service Layer): 25 min
- Group E (API Layer): 20 min
- Group F (UI State): 20 min
- Group G (Integration): 20 min
**Total: ~2.5 hours**
