# Phase 1 Tasks: Modal UI + Issue Search

**Issue:** IW-79
**Phase:** 1 of 4
**Status:** Complete ✓

## Setup Tasks

- [ ] [setup] Create domain package structure for issue search models
- [ ] [setup] Create application package structure for search service
- [ ] [setup] Create presentation/views package structure for modal components

## Domain Model Tasks (TDD)

- [x] [test] Write test for IssueSearchResult value object creation
- [x] [test] Write test for IssueSearchResult with empty fields validation
- [x] [test] Write test for IssueSearchResult URL format validation
- [x] [impl] Implement IssueSearchResult value object in domain layer
- [x] [verify] Run domain model tests to confirm all pass

## Issue Search Service Tasks (TDD)

- [x] [test] Write test for IssueSearchService with valid issue ID search
- [x] [test] Write test for IssueSearchService with invalid issue ID returns empty
- [x] [test] Write test for IssueSearchService with Linear tracker configuration
- [x] [test] Write test for IssueSearchService with GitHub tracker configuration
- [x] [test] Write test for IssueSearchService with YouTrack tracker configuration
- [x] [test] Write test for IssueSearchService with empty query returns empty
- [x] [test] Write test for IssueSearchService with very long query (>100 chars) truncation
- [x] [impl] Create IssueSearchService.scala in application layer
- [x] [impl] Implement search method with ID parsing and fetchIssue delegation
- [x] [impl] Implement result mapping from IssueData to IssueSearchResult
- [x] [impl] Implement query validation (empty check, length limit)
- [x] [impl] Implement tracker-specific URL building for each tracker type
- [x] [verify] Run search service tests to confirm all pass

## View Component Tasks (TDD)

### CreateWorktreeModal Component

- [x] [test] Write test for CreateWorktreeModal renders modal structure
- [x] [test] Write test for CreateWorktreeModal includes search input with correct attributes
- [x] [test] Write test for CreateWorktreeModal includes close button with HTMX attributes
- [x] [test] Write test for CreateWorktreeModal includes results container div
- [x] [impl] Create CreateWorktreeModal.scala in presentation/views
- [x] [impl] Implement modal HTML structure with ScalaTags
- [x] [impl] Add HTMX attributes for modal interactions
- [x] [impl] Add CSS classes for modal styling
- [x] [verify] Run modal component tests to confirm all pass

### SearchResultsView Component

- [x] [test] Write test for SearchResultsView with empty results shows "No issues found"
- [x] [test] Write test for SearchResultsView with single result renders correctly
- [x] [test] Write test for SearchResultsView with multiple results renders all items
- [x] [test] Write test for SearchResultsView includes issue ID, title, and status
- [x] [test] Write test for SearchResultsView limits results to maximum 10 items
- [x] [impl] Create SearchResultsView.scala in presentation/views
- [x] [impl] Implement renderResults method for list rendering
- [x] [impl] Implement renderResultItem method for individual items
- [x] [impl] Implement empty state message
- [x] [impl] Add result limit logic (max 10)
- [x] [verify] Run search results view tests to confirm all pass

## API Endpoint Tasks (TDD)

### Search Endpoint

- [x] [test] Write integration test for GET /api/issues/search with valid query
- [x] [test] Write integration test for GET /api/issues/search returns JSON array
- [x] [test] Write integration test for GET /api/issues/search with empty query
- [x] [test] Write integration test for GET /api/issues/search with missing config returns 500
- [x] [impl] Add searchIssues route to CaskServer with @cask.get annotation
- [x] [impl] Implement config loading in search endpoint
- [x] [impl] Implement IssueSearchService invocation
- [x] [impl] Implement error handling for missing config
- [x] [impl] Implement JSON response serialization
- [x] [verify] Run search endpoint integration tests to confirm all pass

### Modal Endpoint

- [x] [test] Write integration test for GET /api/modal/create-worktree returns HTML
- [x] [test] Write integration test for modal HTML contains search input
- [x] [test] Write integration test for modal HTML contains HTMX attributes
- [x] [impl] Add modalCreateWorktree route to CaskServer
- [x] [impl] Implement CreateWorktreeModal.render invocation
- [x] [impl] Implement HTML response with correct content-type
- [x] [verify] Run modal endpoint integration tests to confirm all pass

## Dashboard Integration Tasks

- [x] [impl] Add HTMX script tag to DashboardService head section
- [x] [impl] Add "Create Worktree" button to dashboard header with HTMX attributes
- [x] [impl] Update dashboard header layout to flex container
- [x] [impl] Add modal container div to dashboard body
- [x] [impl] Add modal CSS styles to DashboardService styles
- [x] [impl] Add search input CSS styles
- [x] [impl] Add search results CSS styles
- [x] [impl] Add button CSS styles
- [x] [verify] Start server and verify dashboard loads without errors

## Manual Verification Tasks

- [x] [impl] [verify] Open dashboard in browser and verify "Create Worktree" button visible
- [x] [impl] [verify] Click button and verify modal opens with search input
- [x] [impl] [verify] Type valid issue ID in search and verify results appear
- [x] [impl] [verify] Verify search has 300ms debounce (no immediate API calls)
- [x] [impl] [verify] Type invalid issue ID and verify "No issues found" message
- [x] [impl] [verify] Click modal close button and verify modal closes
- [x] [impl] [verify] Test with Linear tracker configuration
- [x] [impl] [verify] Test with GitHub tracker configuration
- [x] [impl] [verify] Verify modal styling (centered, shadow, rounded corners)
- [x] [impl] [verify] Verify search input focus styling
- [x] [impl] [verify] Verify result items have hover effect
- [x] [impl] [verify] Verify button has hover effect

## Phase Completion Criteria

All tasks above must be completed and:
- All unit tests passing
- All integration tests passing
- Manual verification checklist completed
- Code follows functional programming principles
- No compilation warnings
- All files have PURPOSE comments
