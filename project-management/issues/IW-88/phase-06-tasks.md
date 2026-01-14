# Phase 6 Tasks: Search by title - YouTrack

**Issue:** IW-88
**Phase:** 6 of 7
**Story:** As a user with a YouTrack-configured project, I can search issues by title text

## Phase Success Criteria

- [ ] `buildSearchIssuesUrl` builds correct YouTrack search URL
- [ ] `searchIssues` executes HTTP request and returns results
- [ ] CaskServer routes YouTrack search to new function
- [ ] All tests pass
- [ ] Search with text returns matching issues
- [ ] Empty query returns empty results

---

## Task 1: Add buildSearchIssuesUrl to YouTrackClient

### 1.1 Write test for URL building
- [ ] [impl] [ ] [reviewed] Add test `buildSearchIssuesUrl returns correct URL with query and limit`

### 1.2 Write test for query encoding
- [ ] [impl] [ ] [reviewed] Add test `buildSearchIssuesUrl encodes special characters in query`

### 1.3 Implement buildSearchIssuesUrl
- [ ] [impl] [ ] [reviewed] Add `buildSearchIssuesUrl(baseUrl: String, query: String, limit: Int): String` method
- [ ] [impl] [ ] [reviewed] Use same fields as listRecentIssues: `idReadable,summary,customFields(name,value(name))`
- [ ] [impl] [ ] [reviewed] Add `query` parameter with URL encoding
- [ ] [impl] [ ] [reviewed] Add `$top` parameter for limit

---

## Task 2: Add searchIssues to YouTrackClient

### 2.1 Write test for successful search
- [ ] [impl] [ ] [reviewed] Add test `searchIssues returns issues for valid query` (using pure function tests)

### 2.2 Implement searchIssues method
- [ ] [impl] [ ] [reviewed] Add `searchIssues(baseUrl: String, query: String, limit: Int = 10, token: ApiToken): Either[String, List[Issue]]`
- [ ] [impl] [ ] [reviewed] Follow same HTTP pattern as `listRecentIssues`
- [ ] [impl] [ ] [reviewed] Reuse `parseListRecentIssuesResponse` for parsing (same JSON format)
- [ ] [impl] [ ] [reviewed] Handle 401 Unauthorized with clear error message
- [ ] [impl] [ ] [reviewed] Handle network errors gracefully

---

## Task 3: Update CaskServer buildSearchFunction

### 3.1 Update YouTrack case in buildSearchFunction
- [ ] [impl] [ ] [reviewed] Replace placeholder with call to `YouTrackClient.searchIssues`
- [ ] [impl] [ ] [reviewed] Get baseUrl from `config.youtrackBaseUrl`
- [ ] [impl] [ ] [reviewed] Get token from `ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken)`
- [ ] [impl] [ ] [reviewed] Return appropriate error if token not set

---

## Task 4: Run Tests and Verify

### 4.1 Run all tests
- [ ] [impl] [ ] [reviewed] Run `./iw test unit` and verify all tests pass
- [ ] [impl] [ ] [reviewed] Verify no regressions in existing functionality

---

**Phase Status:** Not Started
