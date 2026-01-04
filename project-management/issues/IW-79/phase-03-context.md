# Phase 3: Error Handling

**Issue:** IW-79
**Phase:** 3 of 4
**Estimated:** 2-3 hours

## Goals

This phase enhances error handling throughout the worktree creation flow to provide user-friendly error messages and graceful degradation:

1. Map all error scenarios to clear, actionable messages
2. Handle "already has worktree" case with appropriate UI
3. Add retry capability after errors
4. Detect existing worktrees in search results

## Scope

### In Scope
- Error view component for displaying user-friendly error messages
- Error mapping service for translating technical errors
- "Already has worktree" badge in search results
- Retry button in error states
- Specific error scenarios:
  - Directory already exists on disk
  - Issue already has registered worktree
  - Issue not found during search
  - API error during search
  - Git operations failure
  - Tmux session creation failure

### Out of Scope
- Concurrent creation protection (Phase 4)
- Network timeout handling beyond basic error message
- Retry with exponential backoff
- Error logging/telemetry

## Dependencies

### From Phase 1
- `IssueSearchResult` value object
- `IssueSearchService.search()` for issue lookup
- `SearchResultsView.render()` for result display
- `/api/issues/search` endpoint

### From Phase 2
- `WorktreeCreationService.create()` for worktree creation
- `WorktreeCreationResult` value object
- `CreationSuccessView` for success state
- `POST /api/worktrees/create` endpoint

## Technical Approach

### 1. Error Domain Model

Create a domain error type hierarchy:
```scala
// Domain error types
sealed trait WorktreeCreationError
object WorktreeCreationError:
  case class DirectoryExists(path: String) extends WorktreeCreationError
  case class AlreadyHasWorktree(issueId: String, existingPath: String) extends WorktreeCreationError
  case class GitError(message: String) extends WorktreeCreationError
  case class TmuxError(message: String) extends WorktreeCreationError
  case class IssueNotFound(issueId: String) extends WorktreeCreationError
  case class ApiError(message: String) extends WorktreeCreationError
```

### 2. User-Friendly Error Mapping

```scala
// Map errors to user-friendly messages with suggestions
case class UserFriendlyError(
  title: String,
  message: String,
  suggestion: Option[String],
  canRetry: Boolean
)
```

### 3. Error View Component

Create `CreationErrorView.scala`:
- Display error title and message
- Show suggestion if available
- Retry button (calls `/api/worktrees/create` again)
- Dismiss button (closes modal)

### 4. Enhanced Search Results

Modify `SearchResultsView` to show "Already has worktree" badge:
- Check registered worktrees before displaying results
- Show badge and different click action for existing worktrees
- "Open existing" instead of "Create" for these items

### 5. HTMX Error Handling

Use `hx-on::error` and HTTP status codes:
- 409 Conflict: Already has worktree
- 422 Unprocessable: Directory exists
- 500 Server Error: Git/Tmux failures
- Response body contains HTML error view

## Files to Modify

### New Files
- `.iw/core/domain/WorktreeCreationError.scala` - Error type hierarchy
- `.iw/core/domain/UserFriendlyError.scala` - User-facing error model
- `.iw/core/presentation/views/CreationErrorView.scala` - Error view component
- `.iw/core/test/WorktreeCreationErrorTest.scala` - Error mapping tests
- `.iw/core/test/UserFriendlyErrorTest.scala` - Error model tests
- `.iw/core/test/CreationErrorViewTest.scala` - Error view tests

### Existing Files to Modify
- `.iw/core/application/WorktreeCreationService.scala` - Return proper error types
- `.iw/core/CaskServer.scala` - Map errors to HTTP status codes + HTML
- `.iw/core/presentation/views/SearchResultsView.scala` - Add "has worktree" badge
- `.iw/core/test/WorktreeCreationServiceTest.scala` - Update for new error types
- `.iw/core/test/SearchResultsViewTest.scala` - Badge tests

## Testing Strategy

### Unit Tests
1. `WorktreeCreationError` → `UserFriendlyError` mapping (all error types)
2. `CreationErrorView` renders correctly for each error type
3. Error view includes retry button when `canRetry = true`
4. Error view includes suggestion when present
5. "Already has worktree" badge renders in search results

### Integration Tests
1. `POST /api/worktrees/create` returns 409 for existing worktree
2. `POST /api/worktrees/create` returns 422 for existing directory
3. `POST /api/worktrees/create` returns 500 for git errors
4. Error responses contain proper HTML error view

### Manual Tests
1. Create worktree for issue that already has one
2. Create worktree when directory exists on disk
3. Search for non-existent issue ID
4. Click retry after error and verify it works
5. Verify "has worktree" badge appears correctly

## Acceptance Criteria

- [ ] All error scenarios display user-friendly messages (not raw exceptions)
- [ ] Errors include remediation suggestions where applicable
- [ ] Modal remains usable after errors (can retry or dismiss)
- [ ] Existing worktrees detected and shown in search results with badge
- [ ] Clicking "already has worktree" item shows attach command, not creation
- [ ] HTTP status codes correctly reflect error types
- [ ] All tests passing

## User Story Reference

From analysis.md Story 2: Handle worktree creation errors gracefully

```gherkin
Scenario: Worktree directory already exists
  Given I search for issue "IW-79" in the create modal
  And directory "../iw-cli-IW-79/" already exists on disk
  When I click on issue "IW-79" to create worktree
  Then I see error message "Directory iw-cli-IW-79 already exists"
  And I see suggestion "Remove the directory or register existing worktree with './iw open IW-79'"
  And I can try again or dismiss the modal

Scenario: Issue already has a worktree
  Given issue "IW-79" already has a registered worktree
  When I search for "IW-79" in the create modal
  Then the issue shows "Already has worktree" badge
  And clicking it shows "Open existing worktree?" option
  And I can copy the tmux attach command directly

Scenario: Issue not found
  Given I type "INVALID-999" in the search field
  When the search completes
  Then I see "No issues found" message
  And I can modify my search

Scenario: API error during search
  Given the issue tracker API is unavailable
  When I type in the search field
  Then I see "Could not search issues. Check your connection." message
  And I can retry the search
```
