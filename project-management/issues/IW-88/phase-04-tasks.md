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

- [ ] [impl] [ ] [reviewed] Add test for buildSearchIssuesQuery with query and default limit (10)
- [ ] [impl] [ ] [reviewed] Add test for buildSearchIssuesQuery with custom limit
- [ ] [impl] [ ] [reviewed] Add test for parseSearchIssuesResponse with valid response
- [ ] [impl] [ ] [reviewed] Add test for parseSearchIssuesResponse with empty issues array
- [ ] [impl] [ ] [reviewed] Add test for parseSearchIssuesResponse with missing fields
- [ ] [impl] [ ] [reviewed] Add test for searchIssues success case (mocked backend)
- [ ] [impl] [ ] [reviewed] Add test for searchIssues unauthorized (401) response
- [ ] [impl] [ ] [reviewed] Add test for searchIssues network error

### CaskServer Integration Tests

- [ ] [impl] [ ] [reviewed] Add test for buildSearchFunction routes Linear to LinearClient

## Implementation

### LinearClient Implementation

- [ ] [impl] [ ] [reviewed] Add buildSearchIssuesQuery method (pure function)
- [ ] [impl] [ ] [reviewed] Add parseSearchIssuesResponse method (pure function)
- [ ] [impl] [ ] [reviewed] Add searchIssues method with backend injection

### CaskServer Implementation

- [ ] [impl] [ ] [reviewed] Update buildSearchFunction for Linear tracker case
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
- Linear GraphQL query: `issueSearch(query: "search text", first: 10) { nodes { identifier title state { name } } }`
- Token from environment: `sys.env.get("LINEAR_API_TOKEN")`
- TeamId from config: `config.team` (may not be needed for issueSearch)
- Response path is different: `data.issueSearch.nodes` (not `data.team.issues.nodes`)
- Reuse HTTP execution pattern from listRecentIssues
- Search limit default is 10 (vs 5 for recent)
