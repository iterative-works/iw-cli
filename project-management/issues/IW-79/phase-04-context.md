# Phase 4: Concurrent Creation Protection

**Issue:** IW-79
**Phase:** 4 of 4
**Estimated:** 2-3 hours

## Goals

This phase adds protection against concurrent worktree creation attempts to prevent race conditions and duplicate creation:

1. UI disabled during creation (prevent user clicking other issues)
2. Server-side locking per issue ID (prevent concurrent creation requests)
3. Lock cleanup on completion, failure, or timeout (30s)

## Scope

### In Scope
- UI disabling during worktree creation (HTMX attributes)
- Server-side in-memory lock per issue ID
- Lock acquisition before creation starts
- Lock release on success, failure, or timeout
- "Creation already in progress" error when lock cannot be acquired
- Modal interaction prevention during creation

### Out of Scope
- Distributed locking (not needed for single-server local tool)
- Persistent lock state (in-memory is sufficient)
- UI blocking beyond modal (dashboard remains usable)
- Lock for search operations (only creation needs protection)

## Dependencies

### From Phase 1
- `SearchResultsView.render()` for result display with HTMX attributes
- `CreateWorktreeModal.render()` for modal structure

### From Phase 2
- `WorktreeCreationService.create()` for creation orchestration
- `POST /api/worktrees/create` endpoint
- `CreationLoadingView` for loading state

### From Phase 3
- `WorktreeCreationError` enum for error types
- `CreationErrorView` for displaying errors
- HTTP status code mapping in CaskServer

## Technical Approach

### 1. Creation Lock Model

Create a simple lock tracking structure:
```scala
// Domain model for tracking in-progress creations
case class CreationLock(
  issueId: String,
  startedAt: java.time.Instant
)
```

### 2. Creation Lock Registry

In-memory lock registry using concurrent data structure:
```scala
// Thread-safe registry for active locks
import java.util.concurrent.ConcurrentHashMap

object CreationLockRegistry:
  private val locks = new ConcurrentHashMap[String, CreationLock]()

  def tryAcquire(issueId: String): Boolean
  def release(issueId: String): Unit
  def isLocked(issueId: String): Boolean
  def cleanupExpired(maxAge: Duration): Unit
```

### 3. New Error Case

Add to `WorktreeCreationError`:
```scala
case CreationInProgress(issueId: String) extends WorktreeCreationError
```

With mapping:
```scala
case CreationInProgress(_) => UserFriendlyError(
  title = "Creation In Progress",
  message = "A worktree is already being created for this issue.",
  suggestion = Some("Please wait for the current creation to complete."),
  canRetry = true,
  issueId = Some(issueId)
)
```

HTTP status: 423 Locked

### 4. Service Layer Integration

Wrap creation in lock acquisition/release:
```scala
def createWithLock(issueId: String, ...): Either[WorktreeCreationError, WorktreeCreationResult] =
  if !CreationLockRegistry.tryAcquire(issueId) then
    Left(WorktreeCreationError.CreationInProgress(issueId))
  else
    try
      create(issueId, ...)
    finally
      CreationLockRegistry.release(issueId)
```

### 5. UI Disabling

Use HTMX attributes for UI state management:
```html
<!-- During creation, disable other items -->
<div class="results-list"
     hx-on::before-request="this.classList.add('disabled')"
     hx-on::after-request="this.classList.remove('disabled')">
```

CSS:
```css
.disabled {
  pointer-events: none;
  opacity: 0.5;
}
```

### 6. Lock Timeout/Cleanup

Background cleanup (optional - can be triggered on each request):
```scala
// Clean up locks older than 30 seconds
CreationLockRegistry.cleanupExpired(Duration.ofSeconds(30))
```

## Files to Modify

### New Files
- `.iw/core/domain/CreationLock.scala` - Lock case class
- `.iw/core/infrastructure/CreationLockRegistry.scala` - Thread-safe lock registry
- `.iw/core/test/CreationLockTest.scala` - Lock model tests
- `.iw/core/test/CreationLockRegistryTest.scala` - Registry tests

### Existing Files to Modify
- `.iw/core/domain/WorktreeCreationError.scala` - Add CreationInProgress case
- `.iw/core/application/WorktreeCreationService.scala` - Add createWithLock method
- `.iw/core/CaskServer.scala` - Use createWithLock, add 423 status handling
- `.iw/core/presentation/views/SearchResultsView.scala` - Add hx-on attributes for UI disabling
- `.iw/core/DashboardService.scala` - Add CSS for disabled state
- `.iw/core/test/WorktreeCreationErrorTest.scala` - Test new error case
- `.iw/core/test/WorktreeCreationErrorMappingTest.scala` - Test new error mapping
- `.iw/core/test/WorktreeCreationServiceTest.scala` - Test locking behavior

## Testing Strategy

### Unit Tests
1. `CreationLock` case class construction
2. `CreationLockRegistry.tryAcquire` returns true when not locked
3. `CreationLockRegistry.tryAcquire` returns false when already locked
4. `CreationLockRegistry.release` allows subsequent acquire
5. `CreationLockRegistry.cleanupExpired` removes old locks
6. `CreationLockRegistry.isLocked` returns correct state
7. `WorktreeCreationError.CreationInProgress` pattern matching
8. `CreationInProgress.toUserFriendly` mapping

### Integration Tests
1. `POST /api/worktrees/create` returns 423 when creation in progress
2. Concurrent requests to same issue → only one succeeds
3. Lock released after failure → retry works
4. Lock released after success → new creation works

### Manual Tests
1. Click issue to create → UI disabled during creation
2. Try to click another issue during creation → no action
3. Creation completes → UI re-enabled
4. Creation fails → UI re-enabled, can retry
5. Rapid double-click → only one creation attempt

## Acceptance Criteria

- [ ] UI prevents interaction during worktree creation
- [ ] Server prevents concurrent creation for same issue
- [ ] Lock cleaned up on completion (success or failure)
- [ ] Lock cleaned up on timeout (30 seconds)
- [ ] "Creation in progress" error shows appropriate message
- [ ] HTTP 423 status returned for locked creation attempts
- [ ] All unit and integration tests passing

## User Story Reference

From analysis.md Story 3: Concurrent creation protection

```gherkin
Scenario: UI disabled during creation
  Given I click on issue "IW-79" to create worktree
  Then the modal shows "Creating..." state
  And I cannot click other issues during creation
  And I cannot close the modal until creation completes or fails

Scenario: Server rejects duplicate creation
  Given worktree creation for "IW-79" is in progress
  When another request tries to create worktree for "IW-79"
  Then the second request fails with "Creation already in progress"
  And the first creation continues normally
```
