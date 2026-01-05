// PURPOSE: Application service for searching issues across different trackers
// PURPOSE: Implements ID-based search by parsing query and fetching issue details

package iw.core.application

import iw.core.{IssueId, ProjectConfiguration, Issue}
import iw.core.domain.IssueSearchResult

object IssueSearchService:
  /** Search for issues by ID.
    *
    * Phase 1 implementation: Only supports exact ID match.
    * Title/text search will be added in future phases.
    *
    * @param query Search query (expected to be an issue ID like "IW-79")
    * @param config Project configuration with tracker type and settings
    * @param fetchIssue Function to fetch an issue by ID from the tracker
    * @param checkWorktreeExists Function to check if issue already has a registered worktree
    * @return Either error message or list of search results (max 1 for Phase 1)
    */
  def search(
    query: String,
    config: ProjectConfiguration,
    fetchIssue: IssueId => Either[String, Issue],
    checkWorktreeExists: String => Boolean = _ => false
  ): Either[String, List[IssueSearchResult]] =
    // Validate query is not empty
    val trimmedQuery = query.trim
    if trimmedQuery.isEmpty then
      return Right(List.empty)

    // Try to parse query as issue ID
    IssueId.parse(trimmedQuery, config.teamPrefix, Some(config.trackerType)) match
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
            // Issue not found or fetch failed - return empty list
            Right(List.empty)

      case Left(_) =>
        // Not a valid issue ID - return empty list
        // Future: Implement title/text search here
        Right(List.empty)

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

      case "gitlab" =>
        // GitLab URL format: https://gitlab.com/{group}/{project}/-/issues/{number}
        val number = extractGitHubIssueNumber(issueId) // Same extraction logic
        val baseUrl = config.youtrackBaseUrl.getOrElse("https://gitlab.com").stripSuffix("/")
        config.repository match
          case Some(repo) =>
            s"$baseUrl/$repo/-/issues/$number"
          case None =>
            // Fallback if repository not configured
            s"$baseUrl/unknown/repo/-/issues/$number"

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
