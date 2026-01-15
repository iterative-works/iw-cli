# Phase 2 Context: Search by title - GitHub

**Issue:** IW-88
**Phase:** 2 - Search by title - GitHub
**Story Reference:** Story 2 from analysis.md

## Goals

Extend the issue search functionality to support title-based search for GitHub issues. When a user types text that doesn't match an exact issue ID, the system should search GitHub issues by title and return matching results.

**User Story:**
> As a developer using GitHub tracker, I want to search issues by typing title keywords, so that I can find issues without knowing their exact ID.

## Scope

### In Scope
- Add `GitHubClient.searchIssues()` method to search issues via `gh issue list --search`
- Modify `IssueSearchService.search()` to fall back to title search when query is not an ID
- Unit tests for all new code
- ID search still works with priority (exact ID match first)

### Out of Scope
- Linear title search (Phase 4)
- YouTrack title search (Phase 6)
- Changes to the search endpoint (already exists at `/api/issues/search`)
- Changes to the modal UI (already has "Search by issue ID or title..." placeholder)
- Debouncing (already handled by HTMX with 300ms delay)

## Dependencies

### From Previous Phases
- Phase 1 established the pattern: `buildCommand`, `parseResponse`, `execute` with dependency injection
- `GitHubClient.listRecentIssues()` pattern to follow
- `IssueSearchService.fetchRecent()` structure to reference
- `/api/issues/search` endpoint already exists in CaskServer

### External Dependencies
- `gh` CLI must be installed and authenticated (existing validation)
- GitHub repository must be configured in `.iw/config.conf`

## Technical Approach

### 1. Infrastructure Layer: GitHubClient Extension

Add to `GitHubClient.scala`:

```scala
/** Build gh CLI command for searching issues by text.
  *
  * Uses `gh issue list --search "text"` which searches across title and body.
  *
  * @param repository GitHub repository in owner/repo format
  * @param query Search text (will be quoted for gh CLI)
  * @param limit Maximum number of issues to return
  * @return Array of command arguments for gh CLI
  */
def buildSearchIssuesCommand(
  repository: String,
  query: String,
  limit: Int = 10
): Array[String] =
  Array(
    "issue", "list",
    "--repo", repository,
    "--search", query,
    "--state", "open",
    "--limit", limit.toString,
    "--json", "number,title,state,updatedAt"
  )

/** Parse JSON response from gh issue search command.
  *
  * Reuses the same format as listRecentIssues.
  *
  * @param jsonOutput JSON array string from gh CLI
  * @return Right(List[Issue]) on success, Left(error message) on failure
  */
def parseSearchIssuesResponse(jsonOutput: String): Either[String, List[Issue]] =
  // Can reuse parseListRecentIssuesResponse - same JSON format
  parseListRecentIssuesResponse(jsonOutput)

/** Search GitHub issues by text (title and body).
  *
  * @param repository GitHub repository in owner/repo format
  * @param query Search text
  * @param limit Maximum number of issues to return (default 10)
  * @return Either error message or list of matching issues
  */
def searchIssues(
  repository: String,
  query: String,
  limit: Int = 10,
  isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
  execCommand: (String, Array[String]) => Either[String, String] = ...
): Either[String, List[Issue]]
```

### 2. Application Layer: IssueSearchService Extension

Modify `IssueSearchService.search()`:

```scala
def search(
  query: String,
  config: ProjectConfiguration,
  fetchIssue: IssueId => Either[String, Issue],
  searchIssues: String => Either[String, List[Issue]], // NEW parameter
  checkWorktreeExists: String => Boolean = _ => false
): Either[String, List[IssueSearchResult]] =
  val trimmedQuery = query.trim
  if trimmedQuery.isEmpty then
    return Right(List.empty)

  // Try exact ID match first (priority)
  IssueId.parse(trimmedQuery, config.teamPrefix) match
    case Right(issueId) =>
      fetchIssue(issueId) match
        case Right(issue) =>
          // ... convert to IssueSearchResult as before
          Right(List(result))
        case Left(_) =>
          // ID parsed but issue not found - fall through to text search
          searchByText(trimmedQuery, config, searchIssues, checkWorktreeExists)

    case Left(_) =>
      // Not a valid ID format - do text search
      searchByText(trimmedQuery, config, searchIssues, checkWorktreeExists)

private def searchByText(
  query: String,
  config: ProjectConfiguration,
  searchIssues: String => Either[String, List[Issue]],
  checkWorktreeExists: String => Boolean
): Either[String, List[IssueSearchResult]] =
  searchIssues(query) match
    case Right(issues) =>
      val results = issues.map { issue =>
        IssueSearchResult(
          id = issue.id,
          title = issue.title,
          status = issue.status,
          url = buildIssueUrl(issue.id, config),
          hasWorktree = checkWorktreeExists(issue.id)
        )
      }
      Right(results)
    case Left(error) =>
      Left(error)
```

### 3. Presentation Layer: CaskServer Updates

Update `buildFetchFunction` style helper to include search:

```scala
private def buildSearchFunction(config: ProjectConfiguration): String => Either[String, List[iw.core.Issue]] =
  (query: String) =>
    config.trackerType match
      case IssueTrackerType.GitHub =>
        config.repository match
          case Some(repository) =>
            GitHubClient.searchIssues(repository, query)
          case None =>
            Left("GitHub repository not configured")

      case IssueTrackerType.Linear =>
        Left("Title search not yet supported for Linear")

      case IssueTrackerType.YouTrack =>
        Left("Title search not yet supported for YouTrack")
```

Update `/api/issues/search` endpoint to pass the search function.

## Files to Modify

| File | Changes |
|------|---------|
| `.iw/core/GitHubClient.scala` | Add `buildSearchIssuesCommand`, `parseSearchIssuesResponse`, `searchIssues` |
| `.iw/core/IssueSearchService.scala` | Modify `search` to accept `searchIssues` param, add `searchByText` |
| `.iw/core/CaskServer.scala` | Add `buildSearchFunction`, update search endpoint to use it |
| `.iw/core/test/GitHubClientTest.scala` | Add tests for new search methods |
| `.iw/core/test/IssueSearchServiceTest.scala` | Add tests for text search fallback |

## Testing Strategy

### Unit Tests
1. **GitHubClient.buildSearchIssuesCommand**
   - Verify command structure with default limit
   - Verify command structure with custom limit
   - Verify query is passed correctly

2. **GitHubClient.searchIssues**
   - Success case with mocked command execution
   - gh CLI not installed error
   - gh CLI not authenticated error
   - Empty results
   - API error handling

3. **IssueSearchService.search with text fallback**
   - Exact ID match returns that issue (priority)
   - Invalid ID format triggers text search
   - Valid ID but issue not found triggers text search
   - Text search returns matching issues
   - Text search error handling

### Acceptance Criteria

- [ ] `GitHubClient.searchIssues()` searches issues by text via gh CLI
- [ ] Exact ID match returns that specific issue (priority behavior)
- [ ] Non-ID text searches issues by title
- [ ] Search results display issue ID, title, status
- [ ] Empty query returns empty results
- [ ] Error cases don't crash the server
- [ ] All unit tests pass

## Notes

- The `gh issue list --search "text"` command searches across issue title and body
- Results are returned in relevance order by GitHub
- The JSON format is the same as `listRecentIssues` so we can reuse `parseListRecentIssuesResponse`
- Keep the limit at 10 for search (vs 5 for recent) per analysis.md
- The search endpoint already exists and returns HTML via SearchResultsView
- Debouncing is already handled by HTMX (300ms delay)
