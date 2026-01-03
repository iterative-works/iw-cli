# Phase 3: Error Handling - Tasks

**Issue:** IW-79
**Phase:** 3 of 4
**Status:** Not started
**Estimated:** 2-3 hours

## Task Groups

### Group A: Domain Error Types
- [ ] [test] Write tests for WorktreeCreationError sealed trait hierarchy
- [ ] [impl] Create WorktreeCreationError.scala with all error cases (DirectoryExists, AlreadyHasWorktree, GitError, TmuxError, IssueNotFound, ApiError)
- [ ] [test] Write tests for UserFriendlyError model
- [ ] [impl] Create UserFriendlyError.scala with title, message, suggestion, canRetry fields

### Group B: Error Mapping Service
- [ ] [test] Write tests for DirectoryExists → UserFriendlyError mapping
- [ ] [test] Write tests for AlreadyHasWorktree → UserFriendlyError mapping
- [ ] [test] Write tests for GitError → UserFriendlyError mapping
- [ ] [test] Write tests for TmuxError → UserFriendlyError mapping
- [ ] [test] Write tests for IssueNotFound → UserFriendlyError mapping
- [ ] [test] Write tests for ApiError → UserFriendlyError mapping
- [ ] [impl] Add error mapping function to WorktreeCreationError companion object

### Group C: Error View Component
- [ ] [test] Write tests for CreationErrorView rendering error title and message
- [ ] [test] Write tests for CreationErrorView showing suggestion when present
- [ ] [test] Write tests for CreationErrorView showing retry button when canRetry=true
- [ ] [test] Write tests for CreationErrorView showing dismiss button
- [ ] [impl] Create CreationErrorView.scala with render method
- [ ] [impl] Add HTMX attributes for retry button (hx-post to /api/worktrees/create)
- [ ] [impl] Add HTMX attributes for dismiss button (closes modal)

### Group D: Service Layer Error Handling
- [ ] [test] Write tests for WorktreeCreationService returning DirectoryExists error
- [ ] [test] Write tests for WorktreeCreationService returning AlreadyHasWorktree error
- [ ] [test] Write tests for WorktreeCreationService returning GitError on git failure
- [ ] [test] Write tests for WorktreeCreationService returning TmuxError on tmux failure
- [ ] [impl] Update WorktreeCreationService.create to catch exceptions and map to domain errors
- [ ] [impl] Add directory existence check before worktree creation
- [ ] [impl] Add worktree registration check before creation

### Group E: API Layer Error Handling
- [ ] [test] Write tests for POST /api/worktrees/create returning 409 for AlreadyHasWorktree
- [ ] [test] Write tests for POST /api/worktrees/create returning 422 for DirectoryExists
- [ ] [test] Write tests for POST /api/worktrees/create returning 500 for GitError/TmuxError
- [ ] [test] Write tests for error responses containing CreationErrorView HTML
- [ ] [impl] Update CaskServer POST /api/worktrees/create to map errors to HTTP status codes
- [ ] [impl] Update CaskServer to return CreationErrorView HTML in error responses

### Group F: Search Results Enhancement
- [ ] [test] Write tests for SearchResultsView showing "Already has worktree" badge
- [ ] [test] Write tests for badge appearing only for issues with existing worktrees
- [ ] [test] Write tests for "Open existing" action instead of "Create" for existing worktrees
- [ ] [impl] Add hasWorktree check to SearchResultsView rendering logic
- [ ] [impl] Add badge styling and different click handler for existing worktrees
- [ ] [impl] Update IssueSearchService to include hasWorktree flag in results

### Group G: Integration and Manual Testing
- [ ] [test] E2E test: Create worktree for issue that already has one → sees error
- [ ] [test] E2E test: Create worktree when directory exists → sees error with suggestion
- [ ] [test] E2E test: Click retry button → retries creation
- [ ] [test] E2E test: Search for issue with existing worktree → sees badge
- [ ] [manual] Manually test all error scenarios in browser
- [ ] [manual] Verify retry button functionality
- [ ] [manual] Verify "has worktree" badge appears correctly

## Notes

### TDD Workflow
Each group follows TDD cycle:
1. Write failing test
2. Run test to confirm failure
3. Write minimal implementation to pass
4. Run test to confirm success
5. Refactor if needed

### Error Type Mapping
```scala
DirectoryExists    → 422 Unprocessable Entity
AlreadyHasWorktree → 409 Conflict
GitError           → 500 Internal Server Error
TmuxError          → 500 Internal Server Error
IssueNotFound      → 404 Not Found
ApiError           → 502 Bad Gateway
```

### Key Files
- `.iw/core/domain/WorktreeCreationError.scala` (new)
- `.iw/core/domain/UserFriendlyError.scala` (new)
- `.iw/core/presentation/views/CreationErrorView.scala` (new)
- `.iw/core/application/WorktreeCreationService.scala` (modify)
- `.iw/core/CaskServer.scala` (modify)
- `.iw/core/presentation/views/SearchResultsView.scala` (modify)

### Acceptance Criteria
- [ ] All error scenarios display user-friendly messages (not raw exceptions)
- [ ] Errors include remediation suggestions where applicable
- [ ] Modal remains usable after errors (can retry or dismiss)
- [ ] Existing worktrees detected and shown in search results with badge
- [ ] Clicking "already has worktree" item shows attach command, not creation
- [ ] HTTP status codes correctly reflect error types
- [ ] All unit, integration, and E2E tests passing

### Estimated Time Breakdown
- Group A (Domain Types): 20 min
- Group B (Error Mapping): 30 min
- Group C (Error View): 25 min
- Group D (Service Layer): 30 min
- Group E (API Layer): 25 min
- Group F (Search Enhancement): 30 min
- Group G (Integration): 20 min
**Total: ~3 hours**
