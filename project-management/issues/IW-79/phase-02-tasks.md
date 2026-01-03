# Phase 02 Tasks: Worktree Creation from Modal

**Issue:** IW-79
**Phase:** 2 of 4
**Status:** Not Started

## Implementation Tasks

Tasks organized by functional area with TDD approach (tests before implementation):

### Setup
- [ ] [setup] Review existing worktree creation logic in `.iw/commands/start.scala`
- [ ] [setup] Identify reusable functions for branch name generation and path calculation
- [ ] [setup] Review GitWorktreeAdapter, TmuxAdapter, and WorktreeRegistrationService APIs

### Domain Model
- [ ] [test] Write unit test for WorktreeCreationResult value object construction
- [ ] [test] Write unit test for WorktreeCreationResult field validation (issueId, paths, commands)
- [ ] [impl] Create `.iw/core/domain/WorktreeCreationResult.scala` with case class definition
- [ ] [impl] Add fields: issueId, worktreePath, tmuxSessionName, tmuxAttachCommand

### Service Layer
- [ ] [test] Write unit test for WorktreeCreationService.create with all dependencies succeeding
- [ ] [test] Write unit test for WorktreeCreationService.create when fetchIssue fails
- [ ] [test] Write unit test for WorktreeCreationService.create when createWorktree fails
- [ ] [test] Write unit test for WorktreeCreationService.create when createTmuxSession fails
- [ ] [test] Write unit test for branch name generation (prefix-issueId-slug pattern)
- [ ] [test] Write unit test for tmux session name generation from branch name
- [ ] [test] Write unit test for worktree path calculation from branch name
- [ ] [impl] Create `.iw/core/application/WorktreeCreationService.scala`
- [ ] [impl] Implement create method with function injection for all I/O dependencies
- [ ] [impl] Extract and adapt branch name generation logic from start.scala
- [ ] [impl] Extract and adapt worktree path generation logic from start.scala
- [ ] [impl] Extract and adapt tmux session naming logic from start.scala
- [ ] [impl] Implement for-comprehension to sequence all creation steps
- [ ] [impl] Return Either[String, WorktreeCreationResult] with proper error messages

### View Components
- [ ] [test] Write unit test for CreationSuccessView rendering success message
- [ ] [test] Write unit test for CreationSuccessView rendering tmux attach command
- [ ] [test] Write unit test for CreationSuccessView rendering copy button with correct onclick
- [ ] [test] Write unit test for CreationSuccessView rendering close button with HTMX attrs
- [ ] [test] Write unit test for CreationSuccessView rendering worktree path
- [ ] [impl] Create `.iw/core/presentation/views/CreationSuccessView.scala`
- [ ] [impl] Implement render method with success icon (checkmark)
- [ ] [impl] Add tmux command display in code block
- [ ] [impl] Add copy button with clipboard.writeText onclick handler
- [ ] [impl] Add close button with hx-get to dismiss modal
- [ ] [impl] Display worktree path information
- [ ] [test] Write unit test for CreationLoadingView rendering spinner element
- [ ] [test] Write unit test for CreationLoadingView rendering "Creating worktree..." message
- [ ] [impl] Create `.iw/core/presentation/views/CreationLoadingView.scala`
- [ ] [impl] Implement render method with spinner div
- [ ] [impl] Add loading message text
- [ ] [impl] Add CSS for success state (success icon, command box styling)
- [ ] [impl] Add CSS for loading spinner (rotation animation)
- [ ] [impl] Add CSS for copy button hover states

### Search Results Update
- [ ] [test] Write unit test for SearchResultsView with hx-post attribute on result items
- [ ] [test] Write unit test for SearchResultsView with hx-vals containing issueId
- [ ] [test] Write unit test for SearchResultsView with hx-target pointing to modal body
- [ ] [test] Write unit test for SearchResultsView with hx-indicator attribute
- [ ] [impl] Modify `.iw/core/presentation/views/SearchResultsView.scala` to add click handler
- [ ] [impl] Add hx-post="/api/worktrees/create" attribute to result items
- [ ] [impl] Add hx-vals with JSON containing issueId
- [ ] [impl] Add hx-target="#modal-body-content" for content swap
- [ ] [impl] Add hx-swap="innerHTML" for replacement strategy
- [ ] [impl] Add hx-indicator="#creation-spinner" for loading state
- [ ] [impl] Add cursor:pointer CSS for hover feedback

### Modal Update
- [ ] [test] Write unit test for CreateWorktreeModal containing loading indicator element
- [ ] [test] Write unit test for CreateWorktreeModal with htmx-indicator class on spinner
- [ ] [impl] Modify `.iw/core/presentation/views/CreateWorktreeModal.scala`
- [ ] [impl] Add loading indicator div with id="creation-spinner"
- [ ] [impl] Add htmx-indicator class to spinner for automatic show/hide
- [ ] [impl] Wrap modal body content in div with id="modal-body-content" for targeting

### API Endpoint
- [ ] [test] Write integration test for POST /api/worktrees/create returning 200 on success
- [ ] [test] Write integration test for POST /api/worktrees/create returning 500 on invalid issue
- [ ] [test] Write integration test verifying success response contains tmux command
- [ ] [test] Write integration test verifying success response contains worktree path
- [ ] [test] Write integration test verifying error response contains error message
- [ ] [impl] Add POST route "/api/worktrees/create" to `.iw/core/CaskServer.scala`
- [ ] [impl] Parse JSON request body to extract issueId
- [ ] [impl] Load project configuration from ConfigFileRepository
- [ ] [impl] Return 500 error HTML if config not found
- [ ] [impl] Create actual I/O functions for WorktreeCreationService dependencies
- [ ] [impl] Wire fetchIssue function using existing IssueTrackerClient
- [ ] [impl] Wire createWorktree function using GitWorktreeAdapter
- [ ] [impl] Wire createTmuxSession function using TmuxAdapter
- [ ] [impl] Wire registerWorktree function using WorktreeRegistrationService
- [ ] [impl] Call WorktreeCreationService.create with all dependencies
- [ ] [impl] On success: render CreationSuccessView and return 200
- [ ] [impl] On failure: render error message and return 500
- [ ] [impl] Add basic error view for displaying error messages

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
