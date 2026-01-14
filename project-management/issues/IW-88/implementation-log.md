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
