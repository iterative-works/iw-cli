# Phase 3 Context: Recent issues - Linear

**Issue:** IW-88
**Phase:** 3 - Recent issues - Linear
**Estimated Effort:** 4-6 hours

## User Story

```gherkin
Feature: Recent issues display for Linear tracker
  As a developer using Linear tracker
  I want to see recent issues when I open the Create Worktree modal
  So that I can quickly start work on relevant issues

Scenario: Modal opens with 5 recent Linear issues
  Given the project uses Linear as tracker
  And the team has 15 issues
  When I open the Create Worktree modal
  Then I see 5 most recent issues from my team
  And each issue shows its ID, title, and state
  And the issues are sorted by creation date (newest first)
```

## Acceptance Criteria

- Modal shows 5 recent Linear issues on open
- Issues fetched via Linear GraphQL API
- Respects team configuration from project config
- Same UI rendering as GitHub story (Phase 1)
- Returns empty list on errors (graceful degradation)

## What Was Built in Previous Phases

### Phase 1: Recent issues - GitHub

**Infrastructure Layer:**
- `GitHubClient.buildListRecentIssuesCommand()` - builds gh CLI args
- `GitHubClient.parseListRecentIssuesResponse()` - parses JSON to List[Issue]
- `GitHubClient.listRecentIssues()` - executes with DI for testability

**Application Layer:**
- `IssueSearchService.fetchRecent()` - calls tracker-specific fetch, converts to IssueSearchResult

**Presentation Layer:**
- `GET /api/issues/recent` endpoint - returns HTML fragment via SearchResultsView
- `buildFetchRecentFunction()` in CaskServer - routes to tracker-specific implementation

**Patterns established:**
- Dependency injection: methods accept `isCommandAvailable` and `execCommand` functions
- Pure core functions: `buildCommand` and `parseResponse` are pure, no I/O
- Function composition: service accepts fetch function, routes based on tracker type

### Phase 2: Search by title - GitHub

**Infrastructure Layer:**
- `GitHubClient.buildSearchIssuesCommand()` - builds search args
- `GitHubClient.parseSearchIssuesResponse()` - reuses parseListRecentIssuesResponse
- `GitHubClient.searchIssues()` - executes with DI

**Application Layer:**
- Modified `IssueSearchService.search()` to accept `searchIssues` parameter
- Added `searchByText()` helper for text search fallback

**Patterns established:**
- Reuse parsing logic when formats match
- ID search has priority, text search as fallback

## Available Utilities

From CaskServer (extension points ready for Linear):
```scala
private def buildFetchRecentFunction(config: ProjectConfiguration): Int => Either[String, List[iw.core.Issue]] =
  (limit: Int) =>
    config.trackerType match
      case IssueTrackerType.Linear =>
        // Linear support will be added in Phase 3
        Left("Recent issues not yet supported for Linear")
```

From IssueSearchService:
```scala
def fetchRecent(
  config: ProjectConfiguration,
  fetchRecentIssues: Int => Either[String, List[Issue]],
  checkWorktreeExists: String => Boolean = _ => false
): Either[String, List[IssueSearchResult]]
```

From LinearClient (existing patterns):
```scala
// GraphQL API endpoint
private val apiUrl = "https://api.linear.app/graphql"

// Query building pattern
def buildLinearQuery(issueId: IssueId): String =
  val graphql = s"""{
    "query": "query { issue(id: \\"${issueId.value}\\") { identifier title state { name } } }"
  }"""

// Response parsing pattern
def parseLinearResponse(json: String): Either[String, Issue]

// HTTP execution with backend injection
def fetchIssue(issueId: IssueId, token: ApiToken, backend: SyncBackend = defaultBackend): Either[String, Issue]
```

## Technical Constraints

### Linear GraphQL API

**Query for recent issues:**
```graphql
{
  team(id: "TEAM_ID") {
    issues(first: 5, orderBy: createdAt) {
      nodes {
        identifier
        title
        state { name }
      }
    }
  }
}
```

**Requires:**
- `LINEAR_API_TOKEN` environment variable (already used by existing LinearClient)
- Team ID from project configuration (`config.teamId`)
- Bearer token in Authorization header

**Response format:**
```json
{
  "data": {
    "team": {
      "issues": {
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
}
```

### Dependencies

- sttp client library (already used by LinearClient)
- ApiToken type (existing)
- PROJECT_CONFIG with teamId field

## Implementation Approach

1. **Add to LinearClient:**
   - `buildListRecentIssuesQuery(teamId: String, limit: Int)` - builds GraphQL query
   - `parseListRecentIssuesResponse(json: String)` - parses response to List[Issue]
   - `listRecentIssues(teamId: String, limit: Int, token: ApiToken, backend: SyncBackend)` - executes query

2. **Update CaskServer:**
   - Modify `buildFetchRecentFunction` to call `LinearClient.listRecentIssues` for Linear tracker
   - Get token from environment and teamId from config

3. **No changes needed to:**
   - `IssueSearchService.fetchRecent()` - already accepts any fetch function
   - `/api/issues/recent` endpoint - already routes through buildFetchRecentFunction
   - UI components - same HTML rendering

## Testing Strategy

### Unit Tests for LinearClient

1. `buildListRecentIssuesQuery` returns correct GraphQL with teamId and limit
2. `parseListRecentIssuesResponse` handles valid response with multiple issues
3. `parseListRecentIssuesResponse` handles empty issues array
4. `parseListRecentIssuesResponse` handles missing fields gracefully
5. `listRecentIssues` success case with mocked backend
6. `listRecentIssues` handles unauthorized (401) response
7. `listRecentIssues` handles network errors

### Integration Tests

8. CaskServer routes Linear tracker to LinearClient.listRecentIssues
9. Missing teamId configuration returns appropriate error

## Notes

- Follow TDD: write test first, see it fail, implement, see it pass
- Reuse existing patterns from GitHubClient where applicable
- Linear API differs from GitHub: GraphQL vs CLI, team-based vs repository-based
- Token is read from environment, not stored in config
- Consider: Phase 4 (Linear search) will reuse parsing logic, so design for reuse
