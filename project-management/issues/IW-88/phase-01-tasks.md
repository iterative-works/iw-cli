# Phase 1 Tasks: Recent issues - GitHub

**Issue:** IW-88
**Phase:** 1 - Recent issues - GitHub
**Context:** phase-01-context.md

## Setup

- [ ] [setup] Read existing GitHubClient.scala to understand current structure
- [ ] [setup] Read existing IssueSearchService.scala to understand patterns
- [ ] [setup] Read existing CaskServer.scala to understand API endpoint patterns

## Tests First (TDD)

### GitHubClient Tests

- [ ] [test] Add test for buildListRecentIssuesCommand with default limit (5)
- [ ] [test] Add test for buildListRecentIssuesCommand with custom limit
- [ ] [test] Add test for parseListRecentIssuesResponse with valid JSON array
- [ ] [test] Add test for parseListRecentIssuesResponse with empty array
- [ ] [test] Add test for parseListRecentIssuesResponse with malformed JSON
- [ ] [test] Add test for listRecentIssues success case (mocked command)
- [ ] [test] Add test for listRecentIssues when gh CLI not available

### IssueSearchService Tests

- [ ] [test] Add test for fetchRecent success case with GitHub tracker
- [ ] [test] Add test for fetchRecent with worktree check integration
- [ ] [test] Add test for fetchRecent error handling

### CaskServer Tests

- [ ] [test] Add test for /api/issues/recent endpoint success case
- [ ] [test] Add test for /api/issues/recent with no config
- [ ] [test] Add test for /api/issues/recent on API error

## Implementation

### GitHubClient Implementation

- [ ] [impl] Add buildListRecentIssuesCommand method
- [ ] [impl] Add parseListRecentIssuesResponse method
- [ ] [impl] Add listRecentIssues method

### IssueSearchService Implementation

- [ ] [impl] Add fetchRecent method to IssueSearchService

### CaskServer Implementation

- [ ] [impl] Add /api/issues/recent endpoint to CaskServer

## Integration

- [ ] [integration] Run all unit tests to verify implementation
- [ ] [integration] Manual test with real GitHub repository

## Completion

- [ ] [done] All tests passing
- [ ] [done] Code compiles without warnings

## Notes

- Follow TDD: write test first, see it fail, implement, see it pass
- Use existing patterns from GitHubClient.validateGhPrerequisites for command execution
- Reuse SearchResultsView.render() for HTML rendering
- gh CLI command: `gh issue list --repo owner/repo --state open --limit 5 --json number,title,state,updatedAt`
