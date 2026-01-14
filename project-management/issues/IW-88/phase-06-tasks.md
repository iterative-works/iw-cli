# Phase 6 Tasks: Search by title - YouTrack

**Issue:** IW-88
**Phase:** 6 of 7
**Story:** As a user with a YouTrack-configured project, I can search issues by title text

## Phase Success Criteria

- [x] `buildSearchIssuesUrl` builds correct YouTrack search URL
- [x] `searchIssues` executes HTTP request and returns results
- [x] CaskServer routes YouTrack search to new function
- [x] All tests pass
- [x] Search with text returns matching issues
- [x] Empty query returns empty results

---

## Task 1: Add buildSearchIssuesUrl to YouTrackClient

### 1.1 Write test for URL building
- [x] [impl] [x] [reviewed] Add test `buildSearchIssuesUrl returns correct URL with query and limit`

### 1.2 Write test for query encoding
- [x] [impl] [x] [reviewed] Add test `buildSearchIssuesUrl encodes special characters in query`

### 1.3 Implement buildSearchIssuesUrl
- [x] [impl] [x] [reviewed] Add `buildSearchIssuesUrl(baseUrl: String, query: String, limit: Int): String` method
- [x] [impl] [x] [reviewed] Use same fields as listRecentIssues: `idReadable,summary,customFields(name,value(name))`
- [x] [impl] [x] [reviewed] Add `query` parameter with URL encoding
- [x] [impl] [x] [reviewed] Add `$top` parameter for limit

---

## Task 2: Add searchIssues to YouTrackClient

### 2.1 Write test for successful search
- [x] [impl] [x] [reviewed] Add test `searchIssues returns issues for valid query` (using pure function tests)

### 2.2 Implement searchIssues method
- [x] [impl] [x] [reviewed] Add `searchIssues(baseUrl: String, query: String, limit: Int = 10, token: ApiToken): Either[String, List[Issue]]`
- [x] [impl] [x] [reviewed] Follow same HTTP pattern as `listRecentIssues`
- [x] [impl] [x] [reviewed] Reuse `parseListRecentIssuesResponse` for parsing (same JSON format)
- [x] [impl] [x] [reviewed] Handle 401 Unauthorized with clear error message
- [x] [impl] [x] [reviewed] Handle network errors gracefully

---

## Task 3: Update CaskServer buildSearchFunction

### 3.1 Update YouTrack case in buildSearchFunction
- [x] [impl] [x] [reviewed] Replace placeholder with call to `YouTrackClient.searchIssues`
- [x] [impl] [x] [reviewed] Get baseUrl from `config.youtrackBaseUrl`
- [x] [impl] [x] [reviewed] Get token from `ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken)`
- [x] [impl] [x] [reviewed] Return appropriate error if token not set

---

## Task 4: Run Tests and Verify

### 4.1 Run all tests
- [x] [impl] [x] [reviewed] Run `./iw test unit` and verify all tests pass
- [x] [impl] [x] [reviewed] Verify no regressions in existing functionality

---

**Phase Status:** Complete
