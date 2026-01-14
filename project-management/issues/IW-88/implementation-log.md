# Implementation Log: Improve issue search in Create Worktree modal

Issue: IW-88

This log tracks the evolution of implementation across phases.

---

## Phase 1: Recent issues - GitHub (2026-01-14)

**What was built:**
- Infrastructure: `GitHubClient.scala` - Added `buildListRecentIssuesCommand()`, `parseListRecentIssuesResponse()`, `listRecentIssues()` methods for fetching recent open issues via gh CLI
- Application: `IssueSearchService.scala` - Added `fetchRecent()` method that calls tracker-specific fetch and converts to IssueSearchResult
- Presentation: `CaskServer.scala` - Added `/api/issues/recent` endpoint returning HTML fragment via SearchResultsView

**Decisions made:**
- Used gh CLI `--json number,title,state,updatedAt` format for listing issues (matches existing fetchIssue pattern)
- Hardcoded limit to 5 issues per analysis decision
- Returns empty list on errors rather than error messages to UI (graceful degradation)
- Used enum pattern matching for tracker type selection (compile-time safety)

**Patterns applied:**
- Dependency injection for testability: `listRecentIssues` accepts `isCommandAvailable` and `execCommand` functions
- Pure core functions: `buildListRecentIssuesCommand` and `parseListRecentIssuesResponse` are pure functions with no I/O

**Testing:**
- Unit tests: 11 tests added
  - GitHubClientTest: 7 tests (command building, JSON parsing, integration with mocks)
  - IssueSearchServiceTest: 4 tests (fetchRecent success, worktree check, errors, empty)
- Integration tests: Via mocked command execution

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260114-103000.md
- Major findings: Fixed enum pattern matching in CaskServer (was using string matching); noted architectural suggestions for future phases

**For next phases:**
- Available utilities:
  - `GitHubClient.listRecentIssues(repository, limit)` - fetches recent issues
  - `IssueSearchService.fetchRecent(config, fetchFn)` - application service for recent issues
  - `/api/issues/recent` endpoint - returns HTML fragment
- Extension points:
  - `buildFetchRecentFunction` in CaskServer supports Linear and YouTrack (currently returns "not yet supported")
  - `fetchRecent` accepts any fetch function, so new trackers just need to provide their implementation
- Notes: The pattern established here (buildCommand, parseResponse, execute with DI) should be replicated for Linear (Phase 3) and YouTrack (Phase 5)

**Files changed:**
```
M  .iw/core/CaskServer.scala
M  .iw/core/GitHubClient.scala
M  .iw/core/IssueSearchService.scala
M  .iw/core/test/GitHubClientTest.scala
M  .iw/core/test/IssueSearchServiceTest.scala
```

---

## Phase 2: Search by title - GitHub (2026-01-14)

**What was built:**
- Infrastructure: `GitHubClient.scala` - Added `buildSearchIssuesCommand()`, `parseSearchIssuesResponse()`, `searchIssues()` methods for searching issues by text via `gh issue list --search`
- Application: `IssueSearchService.scala` - Modified `search()` to accept `searchIssues` parameter and fall back to text search when query is not an ID; added `searchByText()` private helper
- Presentation: `CaskServer.scala` - Added `buildSearchFunction()` helper method; updated `/api/issues/search` endpoint to use new search function

**Decisions made:**
- Reused `parseListRecentIssuesResponse` for JSON parsing (same format as listRecentIssues)
- ID search has priority - only fall back to text search if ID lookup fails or query isn't a valid ID
- Search limit set to 10 (vs 5 for recent) per analysis.md
- gh search queries across title and body (GitHub API behavior)

**Patterns applied:**
- Same dependency injection pattern as Phase 1: `searchIssues` accepts `isCommandAvailable` and `execCommand`
- Pure core functions: `buildSearchIssuesCommand` and `parseSearchIssuesResponse` are pure
- Function composition: search() now composes ID fetch with text search fallback

**Testing:**
- Unit tests: 13 tests added
  - GitHubClientTest: 7 tests (command building with limits, search execution, error cases)
  - IssueSearchServiceTest: 6 tests (ID priority, text search fallback, empty query, errors)
- All tests follow established mocking pattern

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20260114-112800.md
- Major findings: No critical issues; noted duplication of `extractGitHubIssueNumber` across files (tech debt for future)

**For next phases:**
- Available utilities:
  - `GitHubClient.searchIssues(repository, query, limit)` - searches issues by text
  - `IssueSearchService.search(query, config, fetchIssue, searchIssues)` - unified search with ID priority
  - `buildSearchFunction` in CaskServer - creates tracker-specific search function
- Extension points:
  - `buildSearchFunction` supports Linear and YouTrack (currently returns "not yet supported")
  - `searchIssues` parameter can be provided by any tracker implementation
- Notes: Phase 4 (Linear title search) and Phase 6 (YouTrack title search) can follow this pattern

**Files changed:**
```
M  .iw/core/CaskServer.scala
M  .iw/core/GitHubClient.scala
M  .iw/core/IssueSearchService.scala
M  .iw/core/test/GitHubClientTest.scala
M  .iw/core/test/IssueSearchServiceTest.scala
```

---

## Phase 4: Search by title - Linear (2026-01-14)

**What was built:**
- Infrastructure: `LinearClient.scala` - Added `buildSearchIssuesQuery()`, `parseSearchIssuesResponse()`, `searchIssues()` methods for searching issues via Linear GraphQL `issueSearch` API
- Presentation: `CaskServer.scala` - Updated `buildSearchFunction` to route Linear tracker to LinearClient.searchIssues

**Decisions made:**
- Used Linear GraphQL query: `issueSearch(query: "search text", first: 10) { nodes { identifier title state { name } } }`
- Response path is different from listRecentIssues: `data.issueSearch.nodes` (not `data.team.issues.nodes`)
- Search limit set to 10 (same as GitHub search, vs 5 for recent)
- Token read from environment `LINEAR_API_TOKEN` (consistent with existing pattern)

**Patterns applied:**
- Same pure/effectful separation as Phase 3: pure `buildSearchIssuesQuery` and `parseSearchIssuesResponse`, effectful `searchIssues`
- Backend injection for testability: `searchIssues` accepts `backend: SyncBackend` parameter
- Follows HTTP execution pattern established in Phase 3

**Testing:**
- Unit tests: 8 tests added
  - LinearClientMockTest: 8 tests (query building, response parsing, HTTP execution with mocked backend)
- Tests follow same pattern as Phase 3 using SyncBackendStub

**Code review:**
- Iterations: 1
- Review file: review-phase-04-20260114-190500.md
- Major findings: No critical issues; noted HTTP duplication (pre-existing tech debt), test naming suggestions for future

**For next phases:**
- Available utilities:
  - `LinearClient.searchIssues(query, limit, token)` - searches Linear issues by text
  - `buildSearchFunction` now routes Linear tracker correctly
- Extension points:
  - YouTrack phases (5-6) can follow similar GraphQL pattern
- Notes: Linear tracker now fully supports both recent issues (Phase 3) and title search (Phase 4)

**Files changed:**
```
M  .iw/core/CaskServer.scala
M  .iw/core/LinearClient.scala
M  .iw/core/test/LinearClientMockTest.scala
```

---

## Phase 3: Recent issues - Linear (2026-01-14)

**What was built:**
- Infrastructure: `LinearClient.scala` - Added `buildListRecentIssuesQuery()`, `parseListRecentIssuesResponse()`, `listRecentIssues()` methods for fetching recent issues via Linear GraphQL API
- Presentation: `CaskServer.scala` - Updated `buildFetchRecentFunction` to route Linear tracker to LinearClient.listRecentIssues

**Decisions made:**
- Used Linear GraphQL query: `team(id: "TEAM_ID") { issues(first: 5, orderBy: createdAt) { nodes { identifier title state { name } } } }`
- Token read from environment `LINEAR_API_TOKEN` (consistent with existing LinearClient pattern)
- Team ID from project configuration `config.team`
- Returns empty list on errors (graceful degradation, same as GitHub)

**Patterns applied:**
- Same pure/effectful separation as GitHub: pure `buildListRecentIssuesQuery` and `parseListRecentIssuesResponse`, effectful `listRecentIssues`
- Backend injection for testability: `listRecentIssues` accepts `backend: SyncBackend` parameter
- Reuse existing LinearClient HTTP patterns for consistency

**Testing:**
- Unit tests: 8 tests added
  - LinearClientMockTest: 8 tests (query building, response parsing, HTTP execution with mocked backend)
- Tests use SyncBackendStub for mocked HTTP responses
- Pure function tests (buildQuery, parseResponse) test logic without HTTP

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20260114-184500.md
- Major findings: No critical issues; noted HTTP duplication pattern and test organization suggestions for future phases

**For next phases:**
- Available utilities:
  - `LinearClient.listRecentIssues(teamId, limit, token)` - fetches recent Linear issues
  - `buildFetchRecentFunction` now routes Linear tracker correctly
- Extension points:
  - Phase 4 can add `LinearClient.searchIssues()` following same pattern
  - `parseListRecentIssuesResponse` designed for reuse (same format expected for search results)
- Notes: Phase 4 (Linear title search) can follow this pattern; YouTrack phases (5-6) need similar implementation

**Files changed:**
```
M  .iw/core/CaskServer.scala
M  .iw/core/LinearClient.scala
A  .iw/core/test/LinearClientMockTest.scala
```

---

## Phase 5: Recent issues - YouTrack (2026-01-14)

**What was built:**
- Infrastructure: `YouTrackClient.scala` - Added `buildListRecentIssuesUrl()`, `parseListRecentIssuesResponse()`, `listRecentIssues()` methods for fetching recent issues via YouTrack REST API
- Presentation: `CaskServer.scala` - Updated `buildFetchRecentFunction` to route YouTrack tracker to `YouTrackClient.listRecentIssues`

**Decisions made:**
- Used YouTrack REST API: `GET /api/issues?fields=idReadable,summary,customFields(name,value(name))&$top={limit}&$orderBy=created%20desc`
- Response is JSON array directly (unlike Linear which nests in `data.team.issues`)
- State extracted from customFields array, defaults to "Unknown" if missing
- Uses `quickRequest` pattern (consistent with existing `fetchIssue` implementation)

**Patterns applied:**
- Pure/effectful separation: pure `buildListRecentIssuesUrl` and `parseListRecentIssuesResponse`, effectful `listRecentIssues`
- Follows existing YouTrackClient patterns for HTTP execution and error handling
- Reuses custom field extraction logic from `parseYouTrackResponse`

**Testing:**
- Unit tests: 7 tests added
  - YouTrackClientTest: 7 tests (URL building, JSON parsing, error handling)
- Tests cover URL construction, JSON array parsing, State extraction, missing State handling, malformed JSON

**Code review:**
- Iterations: 1
- Review file: review-phase-05-20260114-210600.md
- Major findings: No critical issues; noted pre-existing patterns (client location, port abstraction) as warnings; suggestions for custom field extraction and edge case tests

**For next phases:**
- Available utilities:
  - `YouTrackClient.listRecentIssues(baseUrl, limit, token)` - fetches recent YouTrack issues
  - `buildFetchRecentFunction` now routes YouTrack tracker correctly
- Extension points:
  - Phase 6 can add `YouTrackClient.searchIssues()` following same pattern
- Notes: YouTrack tracker now supports recent issues; Phase 6 will add title search

**Files changed:**
```
M  .iw/core/CaskServer.scala
M  .iw/core/YouTrackClient.scala
A  .iw/core/test/YouTrackClientTest.scala
```

---

## Phase 6: Search by title - YouTrack (2026-01-14)

**What was built:**
- Infrastructure: `YouTrackClient.scala` - Added `buildSearchIssuesUrl()` and `searchIssues()` methods for searching issues via YouTrack REST API
- Presentation: `CaskServer.scala` - Updated `buildSearchFunction` to route YouTrack tracker to `YouTrackClient.searchIssues`

**Decisions made:**
- Used YouTrack REST API: `GET /api/issues?fields=idReadable,summary,customFields(name,value(name))&query={searchText}&$top={limit}`
- Query parameter URL-encoded with special character handling (`%20` for space, `%26` for `&`, `%2B` for `+`)
- Reused `parseListRecentIssuesResponse` for parsing (same JSON array format as Phase 5)
- Search limit set to 10 (same as GitHub and Linear search)

**Patterns applied:**
- Pure/effectful separation: pure `buildSearchIssuesUrl` for URL construction, effectful `searchIssues` for HTTP
- Follows existing YouTrackClient patterns for HTTP execution and error handling
- Code reuse: shares JSON parser with `listRecentIssues` (Phase 5)

**Testing:**
- Unit tests: 3 tests added
  - YouTrackClientTest: URL building with query encoding, special characters, JSON format verification
- Tests focus on pure functions (URL building, parsing) - HTTP execution tested via E2E

**Code review:**
- Iterations: 1
- Review file: review-phase-06-20260114-215200.md
- Major findings: No critical blocking issues; noted pre-existing architectural patterns (client location, pure/impure mixing); test naming suggestions

**For next phases:**
- Available utilities:
  - `YouTrackClient.searchIssues(baseUrl, query, limit, token)` - searches YouTrack issues by text
  - `buildSearchFunction` now routes all three trackers correctly
- Extension points:
  - All tracker implementations complete for both recent and search functionality
- Notes: YouTrack tracker now fully supports both recent issues (Phase 5) and title search (Phase 6)

**Files changed:**
```
M  .iw/core/CaskServer.scala
M  .iw/core/YouTrackClient.scala
M  .iw/core/test/YouTrackClientTest.scala
M  .iw/core/test/YouTrackIssueTrackerTest.scala (trailing newline only)
```

---
