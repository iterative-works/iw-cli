# Phase 4 Context: Search by title - Linear

**Issue:** IW-88
**Phase:** 4 - Search by title - Linear
**Estimated Effort:** 4-6 hours

## User Story

```gherkin
Feature: Title-based issue search for Linear
  As a developer using Linear tracker
  I want to search issues by typing title keywords
  So that I can find issues without knowing their exact ID

Scenario: Search finds Linear issue by title
  Given the project uses Linear tracker
  And there is an issue titled "Dashboard refactoring"
  When I type "dashboard" in the search box
  Then I see the matching issue in the results
  And the search uses Linear's built-in search

Scenario: Linear ID format works (e.g., "IW-88")
  Given the project uses Linear tracker
  And there is issue "IW-88"
  When I type "IW-88" in the search box
  Then I see issue IW-88 as the first result
```

## Acceptance Criteria

- Title search works via Linear's GraphQL search
- ID-based search has priority over title search
- Search limit set to 10 (consistent with GitHub search)
- Same UI rendering as GitHub search (Phase 2)
- Returns empty list on errors (graceful degradation)

## What Was Built in Previous Phases

### Phase 1: Recent issues - GitHub

**Infrastructure Layer:**
- `GitHubClient.buildListRecentIssuesCommand()` - builds gh CLI args
- `GitHubClient.parseListRecentIssuesResponse()` - parses JSON to List[Issue]
- `GitHubClient.listRecentIssues()` - executes with DI for testability

**Application Layer:**
- `IssueSearchService.fetchRecent()` - calls tracker-specific fetch

**Presentation Layer:**
- `GET /api/issues/recent` endpoint

### Phase 2: Search by title - GitHub

**Infrastructure Layer:**
- `GitHubClient.buildSearchIssuesCommand()` - builds search args
- `GitHubClient.parseSearchIssuesResponse()` - reuses parseListRecentIssuesResponse
- `GitHubClient.searchIssues()` - executes with DI

**Application Layer:**
- `IssueSearchService.search()` - unified search with ID priority

**Presentation Layer:**
- `buildSearchFunction()` in CaskServer - creates tracker-specific search function
- Extension point ready for Linear (currently returns "not yet supported")

### Phase 3: Recent issues - Linear

**Infrastructure Layer:**
- `LinearClient.buildListRecentIssuesQuery()` - builds GraphQL query
- `LinearClient.parseListRecentIssuesResponse()` - parses JSON to List[Issue]
- `LinearClient.listRecentIssues()` - executes HTTP with backend injection

**Patterns established:**
- Pure/effectful separation: pure query builders and parsers, effectful HTTP execution
- Backend injection for testability (SyncBackendStub)
- Response parsing designed for reuse (same format expected for search results)

## Available Utilities

From CaskServer (extension point ready for Linear search):
```scala
private def buildSearchFunction(config: ProjectConfiguration): String => Either[String, List[iw.core.Issue]] =
  (query: String) =>
    config.trackerType match
      case IssueTrackerType.Linear =>
        // Linear support will be added in Phase 4
        Left("Title search not yet supported for Linear")
```

From LinearClient (Phase 3 patterns to follow):
```scala
// Pure query builder
def buildListRecentIssuesQuery(teamId: String, limit: Int = 5): String

// Pure response parser (can be reused if response format matches)
def parseListRecentIssuesResponse(json: String): Either[String, List[Issue]]

// Effectful HTTP execution with backend injection
def listRecentIssues(teamId: String, limit: Int, token: ApiToken, backend: SyncBackend): Either[String, List[Issue]]
```

## Technical Constraints

### Linear GraphQL API

**Query for searching issues:**
```graphql
{
  issueSearch(query: "search text", first: 10) {
    nodes {
      identifier
      title
      state { name }
    }
  }
}
```

**Note:** Linear uses `issueSearch` (not `issuesSearch`) for text search, which is different from the team-based `issues` query used for recent issues.

**Requires:**
- `LINEAR_API_TOKEN` environment variable (already used by existing LinearClient)
- Team ID from project configuration (`config.team`)
- Bearer token in Authorization header

**Response format:**
```json
{
  "data": {
    "issueSearch": {
      "nodes": [
        {
          "identifier": "IW-123",
          "title": "Issue title",
          "state": { "name": "In Progress" }
        }
      ]
    }
  }
}
```

**Key difference from listRecentIssues:**
- listRecentIssues: `data.team.issues.nodes`
- searchIssues: `data.issueSearch.nodes`

### Dependencies

- sttp client library (already used by LinearClient)
- ApiToken type (existing)
- PROJECT_CONFIG with team field

## Implementation Approach

1. **Add to LinearClient:**
   - `buildSearchIssuesQuery(query: String, limit: Int)` - builds GraphQL search query
   - `parseSearchIssuesResponse(json: String)` - parses search response (different path from listRecentIssues)
   - `searchIssues(query: String, limit: Int, token: ApiToken, backend: SyncBackend)` - executes query

2. **Update CaskServer:**
   - Modify `buildSearchFunction` to call `LinearClient.searchIssues` for Linear tracker
   - Get token from environment and teamId from config (same as Phase 3)

3. **No changes needed to:**
   - `IssueSearchService.search()` - already accepts any search function
   - `/api/issues/search` endpoint - already routes through buildSearchFunction
   - UI components - same HTML rendering

## Testing Strategy

### Unit Tests for LinearClient

1. `buildSearchIssuesQuery` returns correct GraphQL with query and default limit (10)
2. `buildSearchIssuesQuery` with custom limit
3. `parseSearchIssuesResponse` handles valid response with multiple issues
4. `parseSearchIssuesResponse` handles empty issues array
5. `parseSearchIssuesResponse` handles missing fields gracefully
6. `searchIssues` success case with mocked backend
7. `searchIssues` handles unauthorized (401) response
8. `searchIssues` handles network errors

### Integration Tests

9. CaskServer routes Linear tracker to LinearClient.searchIssues

## Notes

- Follow TDD: write test first, see it fail, implement, see it pass
- Reuse existing patterns from Phase 3 (buildListRecentIssuesQuery, listRecentIssues)
- Response parsing is different: `issueSearch.nodes` vs `team.issues.nodes`
- Token is read from environment, not stored in config
- Search limit default is 10 (vs 5 for recent) per analysis.md
