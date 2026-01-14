# Phase 3 Tasks: Recent issues - Linear

**Issue:** IW-88
**Phase:** 3 - Recent issues - Linear
**Context:** phase-03-context.md

## Setup

- [ ] [impl] [ ] [reviewed] Read existing LinearClient.scala to understand GraphQL patterns
- [ ] [impl] [ ] [reviewed] Read existing GitHubClient.listRecentIssues to understand pattern
- [ ] [impl] [ ] [reviewed] Read CaskServer.buildFetchRecentFunction extension point

## Tests First (TDD)

### LinearClient Tests

- [ ] [impl] [ ] [reviewed] Add test for buildListRecentIssuesQuery with teamId and default limit (5)
- [ ] [impl] [ ] [reviewed] Add test for buildListRecentIssuesQuery with custom limit
- [ ] [impl] [ ] [reviewed] Add test for parseListRecentIssuesResponse with valid response
- [ ] [impl] [ ] [reviewed] Add test for parseListRecentIssuesResponse with empty issues array
- [ ] [impl] [ ] [reviewed] Add test for parseListRecentIssuesResponse with missing fields
- [ ] [impl] [ ] [reviewed] Add test for listRecentIssues success case (mocked backend)
- [ ] [impl] [ ] [reviewed] Add test for listRecentIssues unauthorized (401) response
- [ ] [impl] [ ] [reviewed] Add test for listRecentIssues network error

### CaskServer Integration Tests

- [ ] [impl] [ ] [reviewed] Add test for buildFetchRecentFunction routes Linear to LinearClient

## Implementation

### LinearClient Implementation

- [ ] [impl] [ ] [reviewed] Add buildListRecentIssuesQuery method (pure function)
- [ ] [impl] [ ] [reviewed] Add parseListRecentIssuesResponse method (pure function)
- [ ] [impl] [ ] [reviewed] Add listRecentIssues method with backend injection

### CaskServer Implementation

- [ ] [impl] [ ] [reviewed] Update buildFetchRecentFunction for Linear tracker case
- [ ] [impl] [ ] [reviewed] Get LINEAR_API_TOKEN from environment
- [ ] [impl] [ ] [reviewed] Get teamId from project configuration

## Integration

- [ ] [impl] [ ] [reviewed] Run all unit tests to verify implementation
- [ ] [impl] [ ] [reviewed] Manual test with real Linear team (if available)

## Completion

- [ ] [impl] [ ] [reviewed] All tests passing
- [ ] [impl] [ ] [reviewed] Code compiles without warnings

**Phase Status:** Not Started

## Notes

- Follow TDD: write test first, see it fail, implement, see it pass
- Linear GraphQL query: `team(id: "TEAM_ID") { issues(first: 5, orderBy: createdAt) { nodes { identifier title state { name } } } }`
- Token from environment: `sys.env.get("LINEAR_API_TOKEN")`
- TeamId from config: `config.teamId`
- Reuse existing LinearClient patterns for HTTP execution
- Design parseListRecentIssuesResponse for reuse in Phase 4 (Linear search)
