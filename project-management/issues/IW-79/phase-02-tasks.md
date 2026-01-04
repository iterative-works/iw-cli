# Phase 02 Tasks: Worktree Creation from Modal

**Issue:** IW-79
**Phase:** 2 of 4
**Status:** Complete

## Implementation Tasks

Tasks organized by functional area with TDD approach (tests before implementation):

### Setup
- [ ] [setup] Review existing worktree creation logic in `.iw/commands/start.scala`
- [ ] [setup] Identify reusable functions for branch name generation and path calculation
- [ ] [setup] Review GitWorktreeAdapter, TmuxAdapter, and WorktreeRegistrationService APIs

### Domain Model
- [x] [test] Write unit test for WorktreeCreationResult value object construction
- [x] [test] Write unit test for WorktreeCreationResult field validation (issueId, paths, commands)
- [x] [impl] Create `.iw/core/domain/WorktreeCreationResult.scala` with case class definition
- [x] [impl] Add fields: issueId, worktreePath, tmuxSessionName, tmuxAttachCommand

### Service Layer
- [x] [test] Write unit test for WorktreeCreationService.create with all dependencies succeeding
- [x] [test] Write unit test for WorktreeCreationService.create when fetchIssue fails
- [x] [test] Write unit test for WorktreeCreationService.create when createWorktree fails
- [x] [test] Write unit test for WorktreeCreationService.create when createTmuxSession fails
- [x] [test] Write unit test for branch name generation (prefix-issueId-slug pattern)
- [x] [test] Write unit test for tmux session name generation from branch name
- [x] [test] Write unit test for worktree path calculation from branch name
- [x] [impl] Create `.iw/core/application/WorktreeCreationService.scala`
- [x] [impl] Implement create method with function injection for all I/O dependencies
- [x] [impl] Extract and adapt branch name generation logic from start.scala
- [x] [impl] Extract and adapt worktree path generation logic from start.scala
- [x] [impl] Extract and adapt tmux session naming logic from start.scala
- [x] [impl] Implement for-comprehension to sequence all creation steps
- [x] [impl] Return Either[String, WorktreeCreationResult] with proper error messages

### View Components
- [x] [test] Write unit test for CreationSuccessView rendering success message
- [x] [test] Write unit test for CreationSuccessView rendering tmux attach command
- [x] [test] Write unit test for CreationSuccessView rendering copy button with correct onclick
- [x] [test] Write unit test for CreationSuccessView rendering close button with HTMX attrs
- [x] [test] Write unit test for CreationSuccessView rendering worktree path
- [x] [impl] Create `.iw/core/presentation/views/CreationSuccessView.scala`
- [x] [impl] Implement render method with success icon (checkmark)
- [x] [impl] Add tmux command display in code block
- [x] [impl] Add copy button with clipboard.writeText onclick handler
- [x] [impl] Add close button with hx-get to dismiss modal
- [x] [impl] Display worktree path information
- [x] [test] Write unit test for CreationLoadingView rendering spinner element
- [x] [test] Write unit test for CreationLoadingView rendering "Creating worktree..." message
- [x] [impl] Create `.iw/core/presentation/views/CreationLoadingView.scala`
- [x] [impl] Implement render method with spinner div
- [x] [impl] Add loading message text
- [ ] [impl] Add CSS for success state (success icon, command box styling)
- [ ] [impl] Add CSS for loading spinner (rotation animation)
- [ ] [impl] Add CSS for copy button hover states

### Search Results Update
- [x] [test] Write unit test for SearchResultsView with hx-post attribute on result items
- [x] [test] Write unit test for SearchResultsView with hx-vals containing issueId
- [x] [test] Write unit test for SearchResultsView with hx-target pointing to modal body
- [x] [test] Write unit test for SearchResultsView with hx-indicator attribute
- [x] [impl] Modify `.iw/core/presentation/views/SearchResultsView.scala` to add click handler
- [x] [impl] Add hx-post="/api/worktrees/create" attribute to result items
- [x] [impl] Add hx-vals with JSON containing issueId
- [x] [impl] Add hx-target="#modal-body-content" for content swap
- [x] [impl] Add hx-swap="innerHTML" for replacement strategy
- [x] [impl] Add hx-indicator="#creation-spinner" for loading state
- [ ] [impl] Add cursor:pointer CSS for hover feedback

### Modal Update
- [x] [test] Write unit test for CreateWorktreeModal containing loading indicator element
- [x] [test] Write unit test for CreateWorktreeModal with htmx-indicator class on spinner
- [x] [impl] Modify `.iw/core/presentation/views/CreateWorktreeModal.scala`
- [x] [impl] Add loading indicator div with id="creation-spinner"
- [x] [impl] Add htmx-indicator class to spinner for automatic show/hide
- [x] [impl] Wrap modal body content in div with id="modal-body-content" for targeting

### API Endpoint
- [ ] [test] Write integration test for POST /api/worktrees/create returning 200 on success
- [ ] [test] Write integration test for POST /api/worktrees/create returning 500 on invalid issue
- [ ] [test] Write integration test verifying success response contains tmux command
- [ ] [test] Write integration test verifying success response contains worktree path
- [ ] [test] Write integration test verifying error response contains error message
- [x] [impl] Add POST route "/api/worktrees/create" to `.iw/core/CaskServer.scala`
- [x] [impl] Parse JSON request body to extract issueId
- [x] [impl] Load project configuration from ConfigFileRepository
- [x] [impl] Return 500 error HTML if config not found
- [x] [impl] Create actual I/O functions for WorktreeCreationService dependencies
- [x] [impl] Wire fetchIssue function using existing IssueTrackerClient
- [x] [impl] Wire createWorktree function using GitWorktreeAdapter
- [x] [impl] Wire createTmuxSession function using TmuxAdapter
- [x] [impl] Wire registerWorktree function using ServerClient
- [x] [impl] Call WorktreeCreationService.create with all dependencies
- [x] [impl] On success: render CreationSuccessView and return 200
- [x] [impl] On failure: render error message and return 500
- [x] [impl] Add basic error view for displaying error messages

### Integration
- [ ] [test] Write E2E test: click search result triggers creation endpoint
- [ ] [test] Write E2E test: loading spinner appears during creation
- [ ] [test] Write E2E test: success message appears after creation
- [ ] [test] Write E2E test: created worktree exists in filesystem
- [ ] [test] Write E2E test: tmux session exists (detached)
- [ ] [test] Write E2E test: worktree registered in state file
- [ ] [test] Write E2E test: dashboard list shows new worktree
- [ ] [test] Write E2E test: copy button copies command to clipboard
- [ ] [integration] Run full creation flow with real dependencies
- [ ] [integration] Verify git worktree created in correct location
- [ ] [integration] Verify branch named correctly (prefix-issueId-slug)
- [ ] [integration] Verify tmux session created detached
- [ ] [integration] Verify state file updated with worktree info
- [ ] [integration] Test with GitHub tracker configuration
- [ ] [integration] Test with Linear tracker configuration
- [ ] [integration] Test with YouTrack tracker configuration

## Notes

- WorktreeCreationService must be pure function with all I/O injected
- Reuse existing logic from start.scala for branch naming and paths
- HTMX hx-indicator automatically shows/hides loading spinner
- Keep error handling basic in this phase (detailed errors in Phase 3)
- Dashboard refresh can be manual in this phase (auto-refresh in Phase 4)
- Test both happy path and basic error scenarios
- Ensure branch name generation handles special characters (slugify)
- Tmux session should be created detached (user attaches manually)
- Success view should make tmux command easy to copy
