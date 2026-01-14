# Phase 1 Context: Recent issues - GitHub

**Issue:** IW-88
**Phase:** 1 - Recent issues - GitHub
**Story Reference:** Story 1 from analysis.md

## Goals

Implement the ability to fetch and display 5 most recent open issues from GitHub when the Create Worktree modal opens. This establishes the foundation pattern that will be replicated for Linear and YouTrack in later phases.

**User Story:**
> As a developer using GitHub tracker, I want to see recent issues when I open the Create Worktree modal, so that I can quickly start work without remembering issue IDs.

## Scope

### In Scope
- Add `GitHubClient.listRecentIssues()` method to fetch recent issues via `gh` CLI
- Add `IssueSearchService.fetchRecent()` application service method
- Add `/api/issues/recent` API endpoint returning HTML fragment
- Unit tests for all new code
- Integration with existing `SearchResultsView` for rendering

### Out of Scope
- Title-based search (Phase 2)
- Linear support (Phase 3)
- YouTrack support (Phase 5)
- UI trigger for loading recent issues on modal open (Phase 7)
- Caching of recent issues

## Dependencies

### From Previous Phases
None - this is the first phase.

### External Dependencies
- `gh` CLI must be installed and authenticated (existing validation in `GitHubClient.validateGhPrerequisites`)
- GitHub repository must be configured in `.iw/config.conf`

## Technical Approach

### 1. Infrastructure Layer: GitHubClient Extension

Add to `GitHubClient.scala`:

```scala
/** Build gh CLI command for listing recent issues.
  *
  * @param repository GitHub repository in owner/repo format
  * @param limit Maximum number of issues to return
  * @return Array of command arguments for gh CLI
  */
def buildListRecentIssuesCommand(
  repository: String,
  limit: Int = 5
): Array[String] =
  Array(
    "issue", "list",
    "--repo", repository,
    "--state", "open",
    "--limit", limit.toString,
    "--json", "number,title,state,updatedAt"
  )

/** Parse JSON response from gh issue list command.
  *
  * Expected format: [{"number": 132, "title": "...", "state": "OPEN", "updatedAt": "..."}, ...]
  *
  * @param jsonOutput JSON array string from gh CLI
  * @return Right(List[Issue]) on success, Left(error message) on failure
  */
def parseListRecentIssuesResponse(jsonOutput: String): Either[String, List[Issue]]

/** Fetch recent open issues from GitHub.
  *
  * @param repository GitHub repository in owner/repo format
  * @param limit Maximum number of issues to return (default 5)
  * @return Either error message or list of recent issues
  */
def listRecentIssues(
  repository: String,
  limit: Int = 5,
  isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
  execCommand: (String, Array[String]) => Either[String, String] = ...
): Either[String, List[Issue]]
```

The `gh issue list` command returns issues sorted by most recently updated by default (descending). This aligns with "recent = updated date" decision from analysis.

### 2. Application Layer: IssueSearchService Extension

Add to `IssueSearchService.scala`:

```scala
/** Fetch recent open issues for quick access.
  *
  * @param config Project configuration with tracker type
  * @param fetchRecentIssues Function to fetch recent issues from tracker
  * @param checkWorktreeExists Function to check if issue has worktree
  * @return Either error message or list of recent issues as search results
  */
def fetchRecent(
  config: ProjectConfiguration,
  fetchRecentIssues: Int => Either[String, List[Issue]],
  checkWorktreeExists: String => Boolean = _ => false
): Either[String, List[IssueSearchResult]]
```

This method:
1. Calls the tracker-specific `fetchRecentIssues` function with limit=5
2. Converts `Issue` objects to `IssueSearchResult` objects
3. Sets `hasWorktree` flag by calling `checkWorktreeExists`
4. Builds URLs using existing `buildIssueUrl` helper

### 3. Presentation Layer: API Endpoint

Add to `CaskServer.scala`:

```scala
@cask.get("/api/issues/recent")
def recentIssues(project: Option[String] = None): cask.Response[String] =
  // Load config, build fetch function, call IssueSearchService.fetchRecent
  // Return SearchResultsView.render(results, project)
```

The endpoint follows the same pattern as `/api/issues/search`:
- Accepts optional `project` query parameter
- Loads config from project path
- Builds tracker-specific fetch function
- Returns HTML fragment via `SearchResultsView.render()`

### 4. Testing Strategy

**Unit Tests:**
- `GitHubClientTest.scala`: Test `buildListRecentIssuesCommand()` and `parseListRecentIssuesResponse()`
- `IssueSearchServiceTest.scala`: Test `fetchRecent()` with mocked fetch function
- `CaskServerTest.scala`: Test `/api/issues/recent` endpoint

**Integration Tests:**
- Manual testing with real GitHub repository (requires `gh` CLI)

## Files to Modify

| File | Changes |
|------|---------|
| `.iw/core/GitHubClient.scala` | Add `buildListRecentIssuesCommand`, `parseListRecentIssuesResponse`, `listRecentIssues` |
| `.iw/core/IssueSearchService.scala` | Add `fetchRecent` method |
| `.iw/core/CaskServer.scala` | Add `/api/issues/recent` endpoint |
| `.iw/core/test/GitHubClientTest.scala` | Add tests for new methods |
| `.iw/core/test/IssueSearchServiceTest.scala` | Add tests for `fetchRecent` |
| `.iw/core/test/CaskServerTest.scala` | Add test for new endpoint |

## Testing Strategy

### Unit Tests
1. **GitHubClient.buildListRecentIssuesCommand**
   - Verify command structure with default limit
   - Verify command structure with custom limit

2. **GitHubClient.parseListRecentIssuesResponse**
   - Parse valid JSON array with multiple issues
   - Parse empty array returns empty list
   - Handle malformed JSON gracefully
   - Handle missing fields gracefully

3. **GitHubClient.listRecentIssues**
   - Success case with mocked command execution
   - gh CLI not installed error
   - gh CLI not authenticated error
   - API error handling

4. **IssueSearchService.fetchRecent**
   - Success case with GitHub tracker
   - Success case with worktree check integration
   - Handle fetch errors gracefully

5. **CaskServer /api/issues/recent**
   - Returns HTML fragment with results
   - Returns empty state when no config
   - Returns empty state on API error

### Acceptance Criteria

- [ ] `GitHubClient.listRecentIssues()` fetches 5 recent open issues
- [ ] Issues are sorted by most recently updated (gh CLI default)
- [ ] `/api/issues/recent` endpoint returns HTML fragment
- [ ] Empty state renders when no issues found
- [ ] Error cases don't crash the server
- [ ] All unit tests pass

## Notes

- The `gh issue list` command returns issues sorted by `updatedAt` descending by default, which matches the "recent = updated date" decision from analysis.md
- Using `--state open` filter as per analysis decision "only open issues"
- The `SearchResultsView` already handles rendering and worktree badge display
- No caching needed per analysis decision (rely on HTMX debouncing only)
