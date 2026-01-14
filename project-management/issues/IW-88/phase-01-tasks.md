# Phase 1 Tasks: Recent issues - GitHub

**Issue:** IW-88
**Phase:** 1 - Recent issues - GitHub
**Context:** phase-01-context.md

## Setup

- [x] [setup] Read existing GitHubClient.scala to understand current structure
- [x] [setup] Read existing IssueSearchService.scala to understand patterns
- [x] [setup] Read existing CaskServer.scala to understand API endpoint patterns

## Tests First (TDD)

### GitHubClient Tests

- [x] [test] Add test for buildListRecentIssuesCommand with default limit (5)
- [x] [test] Add test for buildListRecentIssuesCommand with custom limit
- [x] [test] Add test for parseListRecentIssuesResponse with valid JSON array
- [x] [test] Add test for parseListRecentIssuesResponse with empty array
- [x] [test] Add test for parseListRecentIssuesResponse with malformed JSON
- [x] [test] Add test for listRecentIssues success case (mocked command)
- [x] [test] Add test for listRecentIssues when gh CLI not available

### IssueSearchService Tests

- [x] [test] Add test for fetchRecent success case with GitHub tracker
- [x] [test] Add test for fetchRecent with worktree check integration
- [x] [test] Add test for fetchRecent error handling

### CaskServer Tests

- [x] [test] Add test for /api/issues/recent endpoint success case
- [x] [test] Add test for /api/issues/recent with no config
- [x] [test] Add test for /api/issues/recent on API error

## Implementation

### GitHubClient Implementation

- [x] [impl] [ ] [reviewed] Add buildListRecentIssuesCommand method
- [x] [impl] [ ] [reviewed] Add parseListRecentIssuesResponse method
- [x] [impl] [ ] [reviewed] Add listRecentIssues method

### IssueSearchService Implementation

- [x] [impl] [ ] [reviewed] Add fetchRecent method to IssueSearchService

### CaskServer Implementation

- [x] [impl] [ ] [reviewed] Add /api/issues/recent endpoint to CaskServer

## Integration

- [x] [integration] Run all unit tests to verify implementation
- [ ] [integration] Manual test with real GitHub repository

## Completion

- [x] [done] All tests passing
- [x] [done] Code compiles without warnings

## Notes

- Follow TDD: write test first, see it fail, implement, see it pass
- Use existing patterns from GitHubClient.validateGhPrerequisites for command execution
- Reuse SearchResultsView.render() for HTML rendering
- gh CLI command: `gh issue list --repo owner/repo --state open --limit 5 --json number,title,state,updatedAt`
