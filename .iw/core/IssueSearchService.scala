// PURPOSE: Application service for searching issues across different trackers
// PURPOSE: Implements ID-based search by parsing query and fetching issue details

package iw.core.application

import iw.core.{IssueId, ProjectConfiguration, Issue}
import iw.core.domain.IssueSearchResult

object IssueSearchService:
  /** Search for issues by ID or text.
    *
    * Phase 2 implementation: Supports exact ID match (priority) and text search fallback.
    * - If query parses as issue ID and fetch succeeds: return that issue
    * - If query parses as issue ID but fetch fails: fall back to text search
    * - If query doesn't parse as issue ID: do text search
    *
    * @param query Search query (issue ID like "IW-79" or text like "fix bug")
    * @param config Project configuration with tracker type and settings
    * @param fetchIssue Function to fetch an issue by ID from the tracker
    * @param searchIssues Function to search issues by text query
    * @param checkWorktreeExists Function to check if issue already has a registered worktree
    * @return Either error message or list of search results
    */
  def search(
    query: String,
    config: ProjectConfiguration,
    fetchIssue: IssueId => Either[String, Issue],
    searchIssues: String => Either[String, List[Issue]],
    checkWorktreeExists: String => Boolean = _ => false
  ): Either[String, List[IssueSearchResult]] =
    // Validate query is not empty
    val trimmedQuery = query.trim
    if trimmedQuery.isEmpty then
      return Right(List.empty)

    // Try to parse query as issue ID (priority)
    IssueId.parse(trimmedQuery, config.teamPrefix) match
      case Right(issueId) =>
        // Fetch the issue
        fetchIssue(issueId) match
          case Right(issue) =>
            // Build URL for the issue
            val url = buildIssueUrl(issueId.value, config)

            // Check if worktree already exists for this issue
            val hasWorktree = checkWorktreeExists(issue.id)

            // Convert to search result
            val result = IssueSearchResult(
              id = issue.id,
              title = issue.title,
              status = issue.status,
              url = url,
              hasWorktree = hasWorktree
            )
            Right(List(result))

          case Left(_) =>
            // ID parsed but issue not found - fall through to text search
            searchByText(trimmedQuery, config, searchIssues, checkWorktreeExists)

      case Left(_) =>
        // Not a valid ID format - do text search
        searchByText(trimmedQuery, config, searchIssues, checkWorktreeExists)

  /** Search issues by text query.
    *
    * @param query Search text
    * @param config Project configuration
    * @param searchIssues Function to search issues by text
    * @param checkWorktreeExists Function to check if issue has worktree
    * @return Either error message or list of search results
    */
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

  /** Build issue URL based on tracker type.
    *
    * @param issueId Issue identifier
    * @param config Project configuration
    * @return Issue URL for the tracker
    */
  private def buildIssueUrl(issueId: String, config: ProjectConfiguration): String =
    config.trackerType.toString.toLowerCase match
      case "linear" =>
        // Linear URL format: https://linear.app/team/issue/TEAM-123
        val team = config.team.toLowerCase
        s"https://linear.app/$team/issue/$issueId"

      case "github" =>
        // GitHub URL format: https://github.com/owner/repo/issues/123
        config.repository match
          case Some(repo) =>
            // Extract issue number from ID (e.g., "IW-79" -> "79")
            val number = extractGitHubIssueNumber(issueId)
            s"https://github.com/$repo/issues/$number"
          case None =>
            // Fallback if repository not configured
            s"https://github.com/issues/$issueId"

      case "youtrack" =>
        // YouTrack URL format: https://youtrack.example.com/issue/PROJ-123
        config.youtrackBaseUrl match
          case Some(baseUrl) =>
            val cleanBaseUrl = baseUrl.stripSuffix("/")
            s"$cleanBaseUrl/issue/$issueId"
          case None =>
            // Fallback if base URL not configured
            s"https://youtrack.example.com/issue/$issueId"

      case _ =>
        // Unknown tracker - return generic URL
        s"#$issueId"

  /** Extract GitHub issue number from issue ID.
    *
    * Handles formats like:
    * - "79" -> "79"
    * - "IW-79" -> "79"
    * - "#79" -> "79"
    *
    * @param issueId Issue identifier
    * @return Numeric issue number as string
    */
  private def extractGitHubIssueNumber(issueId: String): String =
    // Remove # prefix if present
    val withoutHash = if issueId.startsWith("#") then issueId.drop(1) else issueId
    // Extract number after hyphen if present (e.g., "IW-79" -> "79")
    val hyphenIndex = withoutHash.lastIndexOf('-')
    if hyphenIndex >= 0 then
      withoutHash.substring(hyphenIndex + 1)
    else
      withoutHash

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
  ): Either[String, List[IssueSearchResult]] =
    // Fetch recent issues (limit 5)
    fetchRecentIssues(5) match
      case Right(issues) =>
        // Convert Issues to IssueSearchResults
        val results = issues.map { issue =>
          // Build URL for the issue
          val url = buildIssueUrl(issue.id, config)

          // Check if worktree exists for this issue
          val hasWorktree = checkWorktreeExists(issue.id)

          IssueSearchResult(
            id = issue.id,
            title = issue.title,
            status = issue.status,
            url = url,
            hasWorktree = hasWorktree
          )
        }
        Right(results)

      case Left(error) =>
        Left(error)
