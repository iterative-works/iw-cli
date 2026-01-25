// PURPOSE: Pure business logic for cache-aware issue fetching
// PURPOSE: Handles TTL validation, stale cache fallback, and URL construction

package iw.core.dashboard

import iw.core.model.Issue
import iw.core.model.{IssueData, CachedIssue}
import java.time.Instant

/** Service for fetching issues with caching support.
  *
  * All functions are pure - they receive current time and fetch function
  * as parameters (Functional Core / Imperative Shell pattern).
  */
object IssueCacheService:

  /** Get cached issue data without calling API.
    *
    * Returns cached data regardless of age (even if stale).
    * Returns None if no cache entry exists.
    * Never calls fetch function - purely reads from cache.
    *
    * @param issueId Issue identifier to look up
    * @param cache Current issue cache map
    * @return Optional IssueData from cache (None if not cached)
    */
  def getCachedOnly(
    issueId: String,
    cache: Map[String, CachedIssue]
  ): Option[IssueData] =
    cache.get(issueId).map(_.data)

  /** Fetch issue with cache support.
    *
    * Strategy:
    * 1. Check if issue ID is in cache and still valid (age < TTL)
    * 2. If valid cache exists: return cached data with fromCache=true
    * 3. If cache expired or missing: call fetchFn to get fresh data
    * 4. If API succeeds: return fresh data with fromCache=false
    * 5. If API fails and stale cache exists: return stale data with fromCache=true
    * 6. If API fails and no cache: return error
    *
    * @param issueId Issue identifier to fetch
    * @param cache Current issue cache map
    * @param now Current timestamp for TTL validation
    * @param fetchFn Function to fetch issue from API (injected for purity)
    * @param urlBuilder Function to build issue URL from ID (injected)
    * @return Either error message or (IssueData, fromCache) tuple
    */
  def fetchWithCache(
    issueId: String,
    cache: Map[String, CachedIssue],
    now: Instant,
    fetchFn: String => Either[String, Issue],
    urlBuilder: String => String
  ): Either[String, (IssueData, Boolean)] =
    cache.get(issueId) match
      case Some(cached) if CachedIssue.isValid(cached, now) =>
        // Valid cache: return immediately without API call
        Right((cached.data, true))

      case maybeCached =>
        // Cache expired or missing: fetch fresh data
        fetchFn(issueId) match
          case Right(issue) =>
            // API success: create fresh IssueData
            val url = urlBuilder(issueId)
            val freshData = IssueData.fromIssue(issue, url, now)
            Right((freshData, false))

          case Left(error) =>
            // API failed: fall back to stale cache if exists
            maybeCached match
              case Some(staleCache) =>
                // Return stale data (better than no data)
                Right((staleCache.data, true))
              case None =>
                // No cache to fall back to
                Left(s"Failed to fetch issue $issueId: $error")

  /** Build issue URL from tracker type and issue ID.
    *
    * @param issueId Issue identifier (e.g., "IWLE-123", "PROJ-456", "72")
    * @param trackerType Tracker type ("Linear", "YouTrack", or "GitHub")
    * @param configValue Optional config value (baseUrl for YouTrack, repository for GitHub)
    * @return Direct link to issue in tracker
    */
  def buildIssueUrl(issueId: String, trackerType: String, configValue: Option[String]): String =
    trackerType.toLowerCase match
      case "linear" =>
        // Linear URL format: https://linear.app/issue/{issueId}
        // Alternative format: https://linear.app/team/{team}/issue/{issueId}
        // We use the simpler format that redirects automatically
        s"https://linear.app/issue/$issueId"

      case "youtrack" =>
        // YouTrack URL format: {baseUrl}/issue/{issueId}
        configValue match
          case Some(url) => s"$url/issue/$issueId"
          case None => s"https://youtrack.example.com/issue/$issueId" // Fallback

      case "github" =>
        // GitHub URL format: https://github.com/{repository}/issues/{number}
        val issueNumber = extractGitHubIssueNumber(issueId)
        configValue match
          case Some(repository) => s"https://github.com/$repository/issues/$issueNumber"
          case None => s"https://github.com/unknown/repo/issues/$issueNumber" // Fallback

      case "gitlab" =>
        // GitLab URL format: https://gitlab.com/{group}/{project}/-/issues/{number}
        // configValue format: "repository" or "repository|baseUrl"
        val issueNumber = extractGitHubIssueNumber(issueId) // Same number extraction logic
        configValue match
          case Some(value) if value.contains("|") =>
            // Format: "repository|baseUrl"
            val parts = value.split("\\|", 2)
            val repository = parts(0)
            val baseUrl = parts(1).stripSuffix("/")
            s"$baseUrl/$repository/-/issues/$issueNumber"
          case Some(repository) =>
            // Default to gitlab.com
            s"https://gitlab.com/$repository/-/issues/$issueNumber"
          case None =>
            s"https://gitlab.com/unknown/repo/-/issues/$issueNumber" // Fallback

      case _ =>
        // Unknown tracker: return generic placeholder
        s"#unknown-tracker-$issueId"

  /** Extract GitHub issue number from various issueId formats.
    *
    * Handles formats like:
    * - "72" -> "72"
    * - "IW-72" -> "72"
    * - "#72" -> "72"
    *
    * @param issueId Issue identifier (may have prefix)
    * @return Numeric issue number as string
    */
  private def extractGitHubIssueNumber(issueId: String): String =
    // Remove # prefix if present
    val withoutHash = if issueId.startsWith("#") then issueId.drop(1) else issueId
    // Extract number after hyphen if present (e.g., "IW-72" -> "72")
    val hyphenIndex = withoutHash.lastIndexOf('-')
    if hyphenIndex >= 0 then
      withoutHash.substring(hyphenIndex + 1)
    else
      withoutHash
