# Phase 4 Tasks: Search by title - Linear

**Issue:** IW-88
**Phase:** 4 - Search by title - Linear
**Context:** phase-04-context.md

## Setup

- [ ] [impl] [ ] [reviewed] Read existing LinearClient.scala to understand Phase 3 patterns
- [ ] [impl] [ ] [reviewed] Read existing GitHubClient.searchIssues to understand search pattern
- [ ] [impl] [ ] [reviewed] Read CaskServer.buildSearchFunction extension point

## Tests First (TDD)

### LinearClient Tests

- [x] [impl] [x] [reviewed] Add test for buildSearchIssuesQuery with query and default limit (10)
- [x] [impl] [x] [reviewed] Add test for buildSearchIssuesQuery with custom limit
- [x] [impl] [x] [reviewed] Add test for parseSearchIssuesResponse with valid response
- [x] [impl] [x] [reviewed] Add test for parseSearchIssuesResponse with empty issues array
- [x] [impl] [x] [reviewed] Add test for parseSearchIssuesResponse with missing fields
- [x] [impl] [x] [reviewed] Add test for searchIssues success case (mocked backend)
- [x] [impl] [x] [reviewed] Add test for searchIssues unauthorized (401) response
- [x] [impl] [x] [reviewed] Add test for searchIssues network error

### CaskServer Integration Tests

- [ ] [impl] [ ] [reviewed] Add test for buildSearchFunction routes Linear to LinearClient

## Implementation

### LinearClient Implementation

- [x] [impl] [x] [reviewed] Add buildSearchIssuesQuery method (pure function)
- [x] [impl] [x] [reviewed] Add parseSearchIssuesResponse method (pure function)
- [x] [impl] [x] [reviewed] Add searchIssues method with backend injection

### CaskServer Implementation

- [x] [impl] [x] [reviewed] Update buildSearchFunction for Linear tracker case
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
- Linear GraphQL query: `issueSearch(query: "search text", first: 10) { nodes { identifier title state { name } } }`
- Token from environment: `sys.env.get("LINEAR_API_TOKEN")`
- TeamId from config: `config.team` (may not be needed for issueSearch)
- Response path is different: `data.issueSearch.nodes` (not `data.team.issues.nodes`)
- Reuse HTTP execution pattern from listRecentIssues
- Search limit default is 10 (vs 5 for recent)
