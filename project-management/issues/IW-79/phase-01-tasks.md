# Phase 1 Tasks: Modal UI + Issue Search

**Issue:** IW-79
**Phase:** 1 of 4
**Status:** 0/52 tasks complete

## Setup Tasks

- [ ] [setup] Create domain package structure for issue search models
- [ ] [setup] Create application package structure for search service
- [ ] [setup] Create presentation/views package structure for modal components

## Domain Model Tasks (TDD)

- [ ] [test] Write test for IssueSearchResult value object creation
- [ ] [test] Write test for IssueSearchResult with empty fields validation
- [ ] [test] Write test for IssueSearchResult URL format validation
- [ ] [impl] Implement IssueSearchResult value object in domain layer
- [ ] [verify] Run domain model tests to confirm all pass

## Issue Search Service Tasks (TDD)

- [ ] [test] Write test for IssueSearchService with valid issue ID search
- [ ] [test] Write test for IssueSearchService with invalid issue ID returns empty
- [ ] [test] Write test for IssueSearchService with Linear tracker configuration
- [ ] [test] Write test for IssueSearchService with GitHub tracker configuration
- [ ] [test] Write test for IssueSearchService with YouTrack tracker configuration
- [ ] [test] Write test for IssueSearchService with empty query returns empty
- [ ] [test] Write test for IssueSearchService with very long query (>100 chars) truncation
- [ ] [impl] Create IssueSearchService.scala in application layer
- [ ] [impl] Implement search method with ID parsing and fetchIssue delegation
- [ ] [impl] Implement result mapping from IssueData to IssueSearchResult
- [ ] [impl] Implement query validation (empty check, length limit)
- [ ] [impl] Implement tracker-specific URL building for each tracker type
- [ ] [verify] Run search service tests to confirm all pass

## View Component Tasks (TDD)

### CreateWorktreeModal Component

- [ ] [test] Write test for CreateWorktreeModal renders modal structure
- [ ] [test] Write test for CreateWorktreeModal includes search input with correct attributes
- [ ] [test] Write test for CreateWorktreeModal includes close button with HTMX attributes
- [ ] [test] Write test for CreateWorktreeModal includes results container div
- [ ] [impl] Create CreateWorktreeModal.scala in presentation/views
- [ ] [impl] Implement modal HTML structure with ScalaTags
- [ ] [impl] Add HTMX attributes for modal interactions
- [ ] [impl] Add CSS classes for modal styling
- [ ] [verify] Run modal component tests to confirm all pass

### SearchResultsView Component

- [ ] [test] Write test for SearchResultsView with empty results shows "No issues found"
- [ ] [test] Write test for SearchResultsView with single result renders correctly
- [ ] [test] Write test for SearchResultsView with multiple results renders all items
- [ ] [test] Write test for SearchResultsView includes issue ID, title, and status
- [ ] [test] Write test for SearchResultsView limits results to maximum 10 items
- [ ] [impl] Create SearchResultsView.scala in presentation/views
- [ ] [impl] Implement renderResults method for list rendering
- [ ] [impl] Implement renderResultItem method for individual items
- [ ] [impl] Implement empty state message
- [ ] [impl] Add result limit logic (max 10)
- [ ] [verify] Run search results view tests to confirm all pass

## API Endpoint Tasks (TDD)

### Search Endpoint

- [ ] [test] Write integration test for GET /api/issues/search with valid query
- [ ] [test] Write integration test for GET /api/issues/search returns JSON array
- [ ] [test] Write integration test for GET /api/issues/search with empty query
- [ ] [test] Write integration test for GET /api/issues/search with missing config returns 500
- [ ] [impl] Add searchIssues route to CaskServer with @cask.get annotation
- [ ] [impl] Implement config loading in search endpoint
- [ ] [impl] Implement IssueSearchService invocation
- [ ] [impl] Implement error handling for missing config
- [ ] [impl] Implement JSON response serialization
- [ ] [verify] Run search endpoint integration tests to confirm all pass

### Modal Endpoint

- [ ] [test] Write integration test for GET /api/modal/create-worktree returns HTML
- [ ] [test] Write integration test for modal HTML contains search input
- [ ] [test] Write integration test for modal HTML contains HTMX attributes
- [ ] [impl] Add modalCreateWorktree route to CaskServer
- [ ] [impl] Implement CreateWorktreeModal.render invocation
- [ ] [impl] Implement HTML response with correct content-type
- [ ] [verify] Run modal endpoint integration tests to confirm all pass

## Dashboard Integration Tasks

- [ ] [impl] Add HTMX script tag to DashboardService head section
- [ ] [impl] Add "Create Worktree" button to dashboard header with HTMX attributes
- [ ] [impl] Update dashboard header layout to flex container
- [ ] [impl] Add modal container div to dashboard body
- [ ] [impl] Add modal CSS styles to DashboardService styles
- [ ] [impl] Add search input CSS styles
- [ ] [impl] Add search results CSS styles
- [ ] [impl] Add button CSS styles
- [ ] [verify] Start server and verify dashboard loads without errors

## Manual Verification Tasks

- [ ] [verify] Open dashboard in browser and verify "Create Worktree" button visible
- [ ] [verify] Click button and verify modal opens with search input
- [ ] [verify] Type valid issue ID in search and verify results appear
- [ ] [verify] Verify search has 300ms debounce (no immediate API calls)
- [ ] [verify] Type invalid issue ID and verify "No issues found" message
- [ ] [verify] Click modal close button and verify modal closes
- [ ] [verify] Test with Linear tracker configuration
- [ ] [verify] Test with GitHub tracker configuration
- [ ] [verify] Verify modal styling (centered, shadow, rounded corners)
- [ ] [verify] Verify search input focus styling
- [ ] [verify] Verify result items have hover effect
- [ ] [verify] Verify button has hover effect

## Phase Completion Criteria

All tasks above must be completed and:
- All unit tests passing
- All integration tests passing
- Manual verification checklist completed
- Code follows functional programming principles
- No compilation warnings
- All files have PURPOSE comments
