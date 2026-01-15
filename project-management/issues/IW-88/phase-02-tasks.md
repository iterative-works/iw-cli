# Phase 2 Tasks: Search by title - GitHub

**Issue:** IW-88
**Phase:** 2 - Search by title - GitHub
**Context:** phase-02-context.md

## Setup

- [x] [impl] [x] [reviewed] Read existing GitHubClient.scala to understand listRecentIssues pattern
- [x] [impl] [x] [reviewed] Read existing IssueSearchService.scala to understand search() method
- [x] [impl] [x] [reviewed] Read existing CaskServer.scala to understand /api/issues/search endpoint

## Tests First (TDD)

### GitHubClient Tests

- [x] [impl] [x] [reviewed] Add test for buildSearchIssuesCommand with default limit (10)
- [x] [impl] [x] [reviewed] Add test for buildSearchIssuesCommand with custom limit
- [x] [impl] [x] [reviewed] Add test for buildSearchIssuesCommand query parameter
- [x] [impl] [x] [reviewed] Add test for parseSearchIssuesResponse (reuses parseListRecentIssuesResponse)
- [x] [impl] [x] [reviewed] Add test for searchIssues success case (mocked command)
- [x] [impl] [x] [reviewed] Add test for searchIssues when gh CLI not available
- [x] [impl] [x] [reviewed] Add test for searchIssues empty results

### IssueSearchService Tests

- [x] [impl] [x] [reviewed] Add test for search() exact ID match returns that issue (priority)
- [x] [impl] [x] [reviewed] Add test for search() invalid ID format triggers text search
- [x] [impl] [x] [reviewed] Add test for search() valid ID but not found triggers text search
- [x] [impl] [x] [reviewed] Add test for search() text search returns matching issues
- [x] [impl] [x] [reviewed] Add test for search() empty query returns empty results
- [x] [impl] [x] [reviewed] Add test for search() text search error handling

## Implementation

### GitHubClient Implementation

- [x] [impl] [x] [reviewed] Add buildSearchIssuesCommand method
- [x] [impl] [x] [reviewed] Add parseSearchIssuesResponse method (delegate to parseListRecentIssuesResponse)
- [x] [impl] [x] [reviewed] Add searchIssues method with dependency injection

### IssueSearchService Implementation

- [x] [impl] [x] [reviewed] Add searchIssues parameter to search() method signature
- [x] [impl] [x] [reviewed] Add searchByText private helper method
- [x] [impl] [x] [reviewed] Modify search() to fall back to text search when query is not an ID

### CaskServer Implementation

- [x] [impl] [x] [reviewed] Add buildSearchFunction helper method
- [x] [impl] [x] [reviewed] Update /api/issues/search endpoint to use new search function

## Integration

- [x] [impl] [x] [reviewed] Run all unit tests to verify implementation
- [ ] [impl] [ ] [reviewed] Manual test with real GitHub repository

## Completion

- [x] [impl] [x] [reviewed] All tests passing
- [x] [impl] [x] [reviewed] Code compiles without warnings

**Phase Status:** Complete

## Notes

- Follow TDD: write test first, see it fail, implement, see it pass
- Reuse parseListRecentIssuesResponse for JSON parsing (same format)
- gh CLI command: `gh issue list --repo owner/repo --search "query" --state open --limit 10 --json number,title,state,updatedAt`
- ID search has priority - only fall back to text search if ID lookup fails
- Keep limit at 10 for search (vs 5 for recent) per analysis.md
