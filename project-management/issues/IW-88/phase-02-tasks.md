# Phase 2 Tasks: Search by title - GitHub

**Issue:** IW-88
**Phase:** 2 - Search by title - GitHub
**Context:** phase-02-context.md

## Setup

- [ ] [impl] [ ] [reviewed] Read existing GitHubClient.scala to understand listRecentIssues pattern
- [ ] [impl] [ ] [reviewed] Read existing IssueSearchService.scala to understand search() method
- [ ] [impl] [ ] [reviewed] Read existing CaskServer.scala to understand /api/issues/search endpoint

## Tests First (TDD)

### GitHubClient Tests

- [ ] [impl] [ ] [reviewed] Add test for buildSearchIssuesCommand with default limit (10)
- [ ] [impl] [ ] [reviewed] Add test for buildSearchIssuesCommand with custom limit
- [ ] [impl] [ ] [reviewed] Add test for buildSearchIssuesCommand query parameter
- [ ] [impl] [ ] [reviewed] Add test for parseSearchIssuesResponse (reuses parseListRecentIssuesResponse)
- [ ] [impl] [ ] [reviewed] Add test for searchIssues success case (mocked command)
- [ ] [impl] [ ] [reviewed] Add test for searchIssues when gh CLI not available
- [ ] [impl] [ ] [reviewed] Add test for searchIssues empty results

### IssueSearchService Tests

- [ ] [impl] [ ] [reviewed] Add test for search() exact ID match returns that issue (priority)
- [ ] [impl] [ ] [reviewed] Add test for search() invalid ID format triggers text search
- [ ] [impl] [ ] [reviewed] Add test for search() valid ID but not found triggers text search
- [ ] [impl] [ ] [reviewed] Add test for search() text search returns matching issues
- [ ] [impl] [ ] [reviewed] Add test for search() empty query returns empty results
- [ ] [impl] [ ] [reviewed] Add test for search() text search error handling

## Implementation

### GitHubClient Implementation

- [ ] [impl] [ ] [reviewed] Add buildSearchIssuesCommand method
- [ ] [impl] [ ] [reviewed] Add parseSearchIssuesResponse method (delegate to parseListRecentIssuesResponse)
- [ ] [impl] [ ] [reviewed] Add searchIssues method with dependency injection

### IssueSearchService Implementation

- [ ] [impl] [ ] [reviewed] Add searchIssues parameter to search() method signature
- [ ] [impl] [ ] [reviewed] Add searchByText private helper method
- [ ] [impl] [ ] [reviewed] Modify search() to fall back to text search when query is not an ID

### CaskServer Implementation

- [ ] [impl] [ ] [reviewed] Add buildSearchFunction helper method
- [ ] [impl] [ ] [reviewed] Update /api/issues/search endpoint to use new search function

## Integration

- [ ] [impl] [ ] [reviewed] Run all unit tests to verify implementation
- [ ] [impl] [ ] [reviewed] Manual test with real GitHub repository

## Completion

- [ ] [impl] [ ] [reviewed] All tests passing
- [ ] [impl] [ ] [reviewed] Code compiles without warnings

**Phase Status:** Not Started

## Notes

- Follow TDD: write test first, see it fail, implement, see it pass
- Reuse parseListRecentIssuesResponse for JSON parsing (same format)
- gh CLI command: `gh issue list --repo owner/repo --search "query" --state open --limit 10 --json number,title,state,updatedAt`
- ID search has priority - only fall back to text search if ID lookup fails
- Keep limit at 10 for search (vs 5 for recent) per analysis.md
