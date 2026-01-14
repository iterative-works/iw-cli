# Phase 6 Context: Search by title - YouTrack

**Issue:** IW-88
**Phase:** 6 of 7
**Story:** As a user with a YouTrack-configured project, I can search issues by title text

## User Story

When I type text in the issue search field (not an issue ID), the modal should search YouTrack
for issues matching that text and display the results. This complements the recent issues
feature (Phase 5) by allowing targeted search when the user knows what they're looking for.

## Acceptance Criteria

1. When user types non-ID text in search field, YouTrack API is queried with the search text
2. Search results show up to 10 matching issues (consistent with GitHub/Linear search limits)
3. Each result displays: issue ID, title, and status
4. Empty search returns empty results (not an error)
5. API errors are handled gracefully (return empty list)
6. Search uses YouTrack's query parameter for text matching

## What Was Built in Previous Phases

### Phase 5: Recent issues - YouTrack (direct predecessor)
- `YouTrackClient.buildListRecentIssuesUrl(baseUrl, limit)` - Pure URL builder
- `YouTrackClient.parseListRecentIssuesResponse(json)` - Pure JSON array parser
- `YouTrackClient.listRecentIssues(baseUrl, limit, token)` - Effectful HTTP call
- Updated `buildFetchRecentFunction` in CaskServer for YouTrack routing

### Phase 4: Search by title - Linear (pattern to follow)
- `LinearClient.buildSearchIssuesQuery(query, limit)` - Pure GraphQL query builder
- `LinearClient.parseSearchIssuesResponse(json)` - Pure JSON parser (different path than listRecent)
- `LinearClient.searchIssues(query, limit, token)` - Effectful HTTP call
- Updated `buildSearchFunction` in CaskServer for Linear routing

### Phase 2: Search by title - GitHub (original pattern)
- `GitHubClient.buildSearchIssuesCommand(repository, query, limit)` - Pure command builder
- `GitHubClient.searchIssues(repository, query, limit)` - Effectful execution
- `IssueSearchService.search()` - Application service with ID priority, text search fallback

## Available Utilities

### From YouTrackClient (Phase 5)
```scala
// URL building pattern - can be adapted for search
def buildListRecentIssuesUrl(baseUrl: String, limit: Int): String =
  val fields = "idReadable,summary,customFields(name,value(name))"
  s"$baseUrl/api/issues?fields=$fields&$$top=$limit&$$orderBy=created%20desc"

// JSON parsing - can be reused directly (same response format)
def parseListRecentIssuesResponse(json: String): Either[String, List[Issue]]

// HTTP execution pattern
def listRecentIssues(baseUrl: String, limit: Int = 5, token: ApiToken): Either[String, List[Issue]]
```

### From CaskServer
```scala
// Currently returns "not yet supported" for YouTrack
private def buildSearchFunction(config: ProjectConfiguration): String => Either[String, List[Issue]] =
  (query: String) =>
    config.trackerType match
      case IssueTrackerType.YouTrack =>
        // YouTrack support will be added in Phase 6
        Left("Title search not yet supported for YouTrack")
```

## Technical Approach

### YouTrack Search API

YouTrack REST API supports text search via the `query` parameter:
```
GET /api/issues?fields={fields}&query={searchText}&$top={limit}
```

The `query` parameter accepts:
- Plain text: searches in issue summary and description
- YouTrack query language: e.g., `summary: "search text"` for title-only search

For consistency with GitHub/Linear (which search title and body), we'll use plain text search.

### Implementation Plan

1. **Add `buildSearchIssuesUrl(baseUrl, query, limit)`** - Pure URL builder
   - Similar to `buildListRecentIssuesUrl` but adds `query` parameter
   - Encode query string for URL safety

2. **Reuse `parseListRecentIssuesResponse`** - Same JSON format
   - Search results return the same JSON array structure as recent issues
   - No need for separate parser (unlike Linear which had different response paths)

3. **Add `searchIssues(baseUrl, query, limit, token)`** - Effectful HTTP call
   - Same pattern as `listRecentIssues`
   - Returns `Either[String, List[Issue]]`

4. **Update CaskServer `buildSearchFunction`**
   - Route YouTrack case to `YouTrackClient.searchIssues`
   - Get baseUrl from config, token from environment

## Testing Strategy

### Unit Tests (YouTrackClientTest.scala)
1. `buildSearchIssuesUrl` returns correct URL with query
2. `buildSearchIssuesUrl` encodes special characters in query
3. `buildSearchIssuesUrl` handles empty query
4. `searchIssues` integration test with mocked response (if feasible)

### Response Format (same as listRecentIssues)
```json
[
  {
    "idReadable": "PROJ-123",
    "summary": "Issue title containing search text",
    "customFields": [
      {"name": "State", "value": {"name": "Open"}}
    ]
  }
]
```

## Constraints

- Search limit: 10 (consistent with GitHub/Linear search)
- Use `quickRequest` pattern (consistent with existing YouTrackClient)
- Return empty list on errors (graceful degradation)
- URL-encode search query for safety

## Dependencies

- Phase 5 complete (YouTrack recent issues infrastructure)
- `YOUTRACK_API_TOKEN` environment variable
- `youtrackBaseUrl` in project configuration

## Notes

- This is the final YouTrack-specific phase
- After this, YouTrack will have full parity with GitHub and Linear
- Phase 7 (load recent on modal open) applies to all trackers
