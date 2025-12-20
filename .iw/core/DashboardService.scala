// PURPOSE: Application service for rendering the complete dashboard HTML
// PURPOSE: Generates full HTML page with header, worktree list, and styling

package iw.core.application

import iw.core.{Issue, IssueId, ApiToken, LinearClient, YouTrackClient, ProjectConfiguration}
import iw.core.domain.{WorktreeRegistration, IssueData, CachedIssue}
import iw.core.presentation.views.WorktreeListView
import scalatags.Text.all.*
import java.time.Instant

object DashboardService:
  /** Render dashboard with issue data fetched from cache or APIs.
    *
    * @param worktrees List of registered worktrees
    * @param issueCache Current issue cache
    * @param config Project configuration (for tracker type and team)
    * @return Complete HTML page as string
    */
  def renderDashboard(
    worktrees: List[WorktreeRegistration],
    issueCache: Map[String, CachedIssue],
    config: Option[ProjectConfiguration]
  ): String =
    val now = Instant.now()

    // Fetch issue data for each worktree
    val worktreesWithIssues = worktrees.map { wt =>
      val issueData = fetchIssueForWorktree(wt, issueCache, now, config)
      (wt, issueData)
    }

    val page = html(
      head(
        meta(charset := "UTF-8"),
        tag("title")("iw Dashboard"),
        tag("style")(raw(styles))
      ),
      body(
        div(
          cls := "container",
          h1("iw Dashboard"),
          WorktreeListView.render(worktreesWithIssues, now)
        )
      )
    )

    "<!DOCTYPE html>\n" + page.render

  /** Fetch issue data for a single worktree using cache or API.
    *
    * @param wt Worktree registration
    * @param cache Current issue cache
    * @param now Current timestamp
    * @param config Optional project configuration
    * @return Optional tuple of (IssueData, fromCache flag)
    */
  private def fetchIssueForWorktree(
    wt: WorktreeRegistration,
    cache: Map[String, CachedIssue],
    now: Instant,
    config: Option[ProjectConfiguration]
  ): Option[(IssueData, Boolean)] =
    // Build fetch function based on tracker type
    val fetchFn: String => Either[String, Issue] = id =>
      buildFetchFunction(wt.trackerType, config)(id)

    // Build URL builder
    val urlBuilder: String => String = id =>
      buildUrlBuilder(wt.trackerType, config)(id)

    // Use IssueCacheService to fetch with cache
    IssueCacheService.fetchWithCache(
      wt.issueId,
      cache,
      now,
      fetchFn,
      urlBuilder
    ).toOption

  /** Build fetch function based on tracker type.
    *
    * @param trackerType Tracker type string ("Linear" or "YouTrack")
    * @param config Optional project configuration
    * @return Function that fetches issue by ID
    */
  private def buildFetchFunction(
    trackerType: String,
    config: Option[ProjectConfiguration]
  ): String => Either[String, Issue] =
    (issueId: String) =>
      trackerType.toLowerCase match
        case "linear" =>
          // Get Linear API token from environment
          val tokenOpt = ApiToken.fromEnv(iw.core.Constants.EnvVars.LinearApiToken)
          val issueIdResult = IssueId.parse(issueId)

          (tokenOpt, issueIdResult) match
            case (Some(token), Right(issId)) =>
              LinearClient.fetchIssue(issId, token)
            case (None, _) =>
              Left("LINEAR_API_TOKEN environment variable not set")
            case (_, Left(error)) =>
              Left(error)

        case "youtrack" =>
          // Get YouTrack API token and base URL
          val tokenOpt = ApiToken.fromEnv(iw.core.Constants.EnvVars.YouTrackApiToken)
          val baseUrl = config.flatMap(_.youtrackBaseUrl).getOrElse("https://youtrack.example.com")
          val issueIdResult = IssueId.parse(issueId)

          (tokenOpt, issueIdResult) match
            case (Some(token), Right(issId)) =>
              YouTrackClient.fetchIssue(issId, baseUrl, token)
            case (None, _) =>
              Left("YOUTRACK_API_TOKEN environment variable not set")
            case (_, Left(error)) =>
              Left(error)

        case _ =>
          Left(s"Unknown tracker type: $trackerType")

  /** Build URL builder based on tracker type.
    *
    * @param trackerType Tracker type string
    * @param config Optional project configuration
    * @return Function that builds issue URL from ID
    */
  private def buildUrlBuilder(
    trackerType: String,
    config: Option[ProjectConfiguration]
  ): String => String =
    (issueId: String) =>
      val baseUrl = config.flatMap(_.youtrackBaseUrl)
      IssueCacheService.buildIssueUrl(issueId, trackerType, baseUrl)

  private val styles = """
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      margin: 0;
      padding: 20px;
      background-color: #f5f5f5;
    }

    .container {
      max-width: 1200px;
      margin: 0 auto;
    }

    h1 {
      color: #333;
      margin-bottom: 30px;
    }

    .worktree-list {
      display: grid;
      gap: 20px;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    }

    .worktree-card {
      background: white;
      border: 1px solid #ddd;
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .worktree-card h3 {
      margin: 0 0 10px 0;
      color: #333;
      font-size: 18px;
    }

    .worktree-card .issue-id {
      margin: 0 0 10px 0;
      font-size: 14px;
    }

    .worktree-card .issue-id a {
      color: #0066cc;
      text-decoration: none;
    }

    .worktree-card .issue-id a:hover {
      text-decoration: underline;
    }

    .worktree-card .issue-details {
      margin: 0 0 10px 0;
      font-size: 14px;
      line-height: 1.6;
    }

    .status-badge {
      padding: 2px 8px;
      border-radius: 3px;
      font-size: 0.9em;
      font-weight: 500;
    }

    .status-in-progress {
      background: #ffd43b;
      color: #000;
    }

    .status-done {
      background: #51cf66;
      color: #fff;
    }

    .status-blocked {
      background: #ff6b6b;
      color: #fff;
    }

    .status-default {
      background: #adb5bd;
      color: #fff;
    }

    .assignee {
      color: #666;
    }

    .cache-indicator {
      font-size: 0.85em;
      color: #868e96;
    }

    .worktree-card .last-activity {
      color: #999;
      font-size: 14px;
      margin: 0;
    }

    .empty-state {
      text-align: center;
      padding: 60px 20px;
      color: #999;
    }

    .empty-state p {
      font-size: 18px;
    }
  """
