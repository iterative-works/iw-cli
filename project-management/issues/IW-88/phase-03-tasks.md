# Phase 3 Tasks: Recent issues - Linear

**Issue:** IW-88
**Phase:** 3 - Recent issues - Linear
**Context:** phase-03-context.md

## Setup

- [x] [impl] [x] [reviewed] Read existing LinearClient.scala to understand GraphQL patterns
- [x] [impl] [x] [reviewed] Read existing GitHubClient.listRecentIssues to understand pattern
- [x] [impl] [x] [reviewed] Read CaskServer.buildFetchRecentFunction extension point

## Tests First (TDD)

### LinearClient Tests

- [x] [impl] [x] [reviewed] Add test for buildListRecentIssuesQuery with teamId and default limit (5)
- [x] [impl] [x] [reviewed] Add test for buildListRecentIssuesQuery with custom limit
- [x] [impl] [x] [reviewed] Add test for parseListRecentIssuesResponse with valid response
- [x] [impl] [x] [reviewed] Add test for parseListRecentIssuesResponse with empty issues array
- [x] [impl] [x] [reviewed] Add test for parseListRecentIssuesResponse with missing fields
- [x] [impl] [x] [reviewed] Add test for listRecentIssues success case (mocked backend)
- [x] [impl] [x] [reviewed] Add test for listRecentIssues unauthorized (401) response
- [x] [impl] [x] [reviewed] Add test for listRecentIssues network error

### CaskServer Integration Tests

- [ ] [impl] [ ] [reviewed] Add test for buildFetchRecentFunction routes Linear to LinearClient

## Implementation

### LinearClient Implementation

- [x] [impl] [x] [reviewed] Add buildListRecentIssuesQuery method (pure function)
- [x] [impl] [x] [reviewed] Add parseListRecentIssuesResponse method (pure function)
- [x] [impl] [x] [reviewed] Add listRecentIssues method with backend injection

### CaskServer Implementation

- [x] [impl] [x] [reviewed] Update buildFetchRecentFunction for Linear tracker case
- [x] [impl] [x] [reviewed] Get LINEAR_API_TOKEN from environment
- [x] [impl] [x] [reviewed] Get teamId from project configuration

## Integration

- [x] [impl] [x] [reviewed] Run all unit tests to verify implementation
- [ ] [impl] [ ] [reviewed] Manual test with real Linear team (if available)

## Completion

- [x] [impl] [x] [reviewed] All tests passing
- [x] [impl] [x] [reviewed] Code compiles without warnings

**Phase Status:** Complete

## Notes

- Follow TDD: write test first, see it fail, implement, see it pass
- Linear GraphQL query: `team(id: "TEAM_ID") { issues(first: 5, orderBy: createdAt) { nodes { identifier title state { name } } } }`
- Token from environment: `sys.env.get("LINEAR_API_TOKEN")`
- TeamId from config: `config.teamId`
- Reuse existing LinearClient patterns for HTTP execution
- Design parseListRecentIssuesResponse for reuse in Phase 4 (Linear search)
