# Phase 5 Context: Recent issues - YouTrack

**Issue:** IW-88
**Phase:** 5 of 7
**Story:** View recent issues on modal open (YouTrack)

---

## Goals

Add `listRecentIssues` functionality to YouTrackClient following the established pattern from GitHub (Phase 1) and Linear (Phase 3). When the Create Worktree modal opens in a project configured with YouTrack, it should show the 5 most recent open issues.

---

## Scope

### In Scope

1. **Infrastructure Layer - YouTrackClient.scala:**
   - `buildListRecentIssuesUrl(baseUrl, limit)` - pure function to build REST API URL
   - `parseListRecentIssuesResponse(json)` - pure function to parse JSON response
   - `listRecentIssues(baseUrl, limit, token)` - effectful function with HTTP call

2. **Presentation Layer - CaskServer.scala:**
   - Update `buildFetchRecentFunction` to route YouTrack tracker to `YouTrackClient.listRecentIssues`

3. **Tests:**
   - Unit tests for URL building
   - Unit tests for JSON parsing
   - Integration tests with mocked HTTP backend

### Out of Scope

- Search by title functionality (Phase 6)
- UI changes to the modal (Phase 7)
- Authentication handling changes (existing pattern works)

---

## Dependencies

### From Previous Phases

- `IssueSearchService.fetchRecent()` - already implemented in Phase 1
- `/api/issues/recent` endpoint - already implemented in Phase 1
- `SearchResultsView` rendering - already implemented in Phase 1

### Required for This Phase

- YouTrack API token from `YOUTRACK_API_TOKEN` environment variable (existing pattern)
- YouTrack base URL from project configuration `config.youtrackBaseUrl` (existing)

---

## Technical Approach

### API Endpoint

YouTrack REST API for listing issues:
```
GET {baseUrl}/api/issues?fields=idReadable,summary,customFields(name,value(name))&$top={limit}&$orderBy=created%20desc
```

**Response format:**
```json
[
  {
    "idReadable": "PROJ-123",
    "summary": "Issue title here",
    "customFields": [
      {
        "name": "State",
        "value": { "name": "Open" }
      }
    ]
  }
]
```

### Implementation Pattern

Follow the established pattern from YouTrackClient.fetchIssue:

1. **Pure URL builder:**
   ```scala
   def buildListRecentIssuesUrl(baseUrl: String, limit: Int): String =
     val fields = "idReadable,summary,customFields(name,value(name))"
     s"$baseUrl/api/issues?fields=$fields&$$top=$limit&$$orderBy=created%20desc"
   ```

2. **Pure response parser:**
   ```scala
   def parseListRecentIssuesResponse(json: String): Either[String, List[Issue]] =
     // Parse JSON array
     // Extract idReadable, summary, State from customFields
     // Return List[Issue] with id, title, status
   ```

3. **Effectful fetch:**
   ```scala
   def listRecentIssues(baseUrl: String, limit: Int, token: ApiToken): Either[String, List[Issue]] =
     // Build URL
     // Make HTTP GET request with Authorization header
     // Parse response
   ```

### HTTP Pattern

YouTrackClient uses `quickRequest` from sttp4 (different from LinearClient's `basicRequest`). Maintain consistency with existing YouTrackClient code:

```scala
import sttp.client4.quick.*

val response = quickRequest
  .get(uri"$url")
  .header("Authorization", s"Bearer ${token.value}")
  .header("Accept", "application/json")
  .send()
```

### CaskServer Integration

Update `buildFetchRecentFunction` in CaskServer.scala:

```scala
case IssueTrackerType.YouTrack =>
  val baseUrl = config.youtrackBaseUrl.getOrElse("https://youtrack.example.com")
  ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken) match
    case Some(token) =>
      YouTrackClient.listRecentIssues(baseUrl, limit, token)
    case None =>
      Left("YOUTRACK_API_TOKEN environment variable not set")
```

---

## Files to Modify

1. **`.iw/core/YouTrackClient.scala`**
   - Add `buildListRecentIssuesUrl()` method
   - Add `parseListRecentIssuesResponse()` method
   - Add `listRecentIssues()` method

2. **`.iw/core/CaskServer.scala`**
   - Update `buildFetchRecentFunction` YouTrack case (line ~553-555)

3. **`.iw/core/test/YouTrackClientTest.scala`** (create or extend)
   - Tests for URL building
   - Tests for response parsing
   - Tests for error handling

---

## Testing Strategy

### Unit Tests

1. **URL Building:**
   - Default limit (5 issues)
   - Custom limit
   - URL encoding verification

2. **Response Parsing:**
   - Valid response with multiple issues
   - Empty array response
   - Missing required fields (idReadable, summary)
   - Missing State in customFields
   - Malformed JSON

3. **Integration (mocked HTTP):**
   - Successful fetch returns List[Issue]
   - Unauthorized (401) returns error
   - Network error handling
   - Empty response body

### Test Data

```json
[
  {
    "idReadable": "PROJ-1",
    "summary": "First issue",
    "customFields": [
      {"name": "State", "value": {"name": "Open"}}
    ]
  },
  {
    "idReadable": "PROJ-2",
    "summary": "Second issue",
    "customFields": [
      {"name": "State", "value": {"name": "In Progress"}}
    ]
  }
]
```

---

## Acceptance Criteria

1. ✅ `YouTrackClient.buildListRecentIssuesUrl()` returns correct REST API URL with fields and ordering
2. ✅ `YouTrackClient.parseListRecentIssuesResponse()` correctly parses YouTrack JSON array format
3. ✅ `YouTrackClient.listRecentIssues()` fetches issues using REST API with bearer token auth
4. ✅ CaskServer routes YouTrack tracker to `listRecentIssues` in `buildFetchRecentFunction`
5. ✅ Modal shows 5 recent YouTrack issues on open (requires YOUTRACK_API_TOKEN)
6. ✅ Empty list returned on errors (graceful degradation, same as GitHub/Linear)
7. ✅ All tests pass

---

## Notes

- YouTrack uses REST API (not GraphQL like Linear), so the pattern is closer to GitHub
- The response format is a JSON array directly (not nested in data.team.issues like Linear)
- Status is extracted from customFields array, same as existing fetchIssue implementation
- Keep consistent with existing YouTrackClient code style (quickRequest pattern)
