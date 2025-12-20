// PURPOSE: Pure business logic for cache-aware issue fetching
// PURPOSE: Handles TTL validation, stale cache fallback, and URL construction

package iw.core.application

import iw.core.Issue
import iw.core.domain.{IssueData, CachedIssue}
import java.time.Instant

/** Service for fetching issues with caching support.
  *
  * All functions are pure - they receive current time and fetch function
  * as parameters (Functional Core / Imperative Shell pattern).
  */
object IssueCacheService:

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
    * @param issueId Issue identifier (e.g., "IWLE-123", "PROJ-456")
    * @param trackerType Tracker type ("Linear" or "YouTrack")
    * @param baseUrl Optional base URL (required for YouTrack)
    * @return Direct link to issue in tracker
    */
  def buildIssueUrl(issueId: String, trackerType: String, baseUrl: Option[String]): String =
    trackerType match
      case "Linear" =>
        // Linear URL format: https://linear.app/issue/{issueId}
        // Alternative format: https://linear.app/team/{team}/issue/{issueId}
        // We use the simpler format that redirects automatically
        s"https://linear.app/issue/$issueId"

      case "YouTrack" =>
        // YouTrack URL format: {baseUrl}/issue/{issueId}
        baseUrl match
          case Some(url) => s"$url/issue/$issueId"
          case None => s"https://youtrack.example.com/issue/$issueId" // Fallback

      case _ =>
        // Unknown tracker: return generic placeholder
        s"#unknown-tracker-$issueId"
