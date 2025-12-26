// PURPOSE: Application service for rendering the complete dashboard HTML
// PURPOSE: Generates full HTML page with header, worktree list, and styling

package iw.core.application

import iw.core.{Issue, IssueId, ApiToken, LinearClient, YouTrackClient, ProjectConfiguration}
import iw.core.domain.{WorktreeRegistration, IssueData, CachedIssue, WorkflowProgress, CachedProgress, GitStatus, PullRequestData, CachedPR, ReviewState, CachedReviewState}
import iw.core.infrastructure.CommandRunner
import iw.core.presentation.views.WorktreeListView
import scalatags.Text.all.*
import java.time.Instant
import scala.util.Try

object DashboardService:
  /** Render dashboard with issue data fetched from cache or APIs.
    *
    * @param worktrees List of registered worktrees
    * @param issueCache Current issue cache
    * @param progressCache Current progress cache
    * @param prCache Current PR cache
    * @param reviewStateCache Current review state cache
    * @param config Project configuration (for tracker type and team)
    * @return Complete HTML page as string
    */
  def renderDashboard(
    worktrees: List[WorktreeRegistration],
    issueCache: Map[String, CachedIssue],
    progressCache: Map[String, CachedProgress],
    prCache: Map[String, CachedPR],
    reviewStateCache: Map[String, CachedReviewState],
    config: Option[ProjectConfiguration]
  ): String =
    val now = Instant.now()

    // Fetch issue data, progress, git status, PR data, and review state for each worktree
    val worktreesWithData = worktrees.map { wt =>
      val issueData = fetchIssueForWorktree(wt, issueCache, now, config)
      val progress = fetchProgressForWorktree(wt, progressCache)
      val gitStatus = fetchGitStatusForWorktree(wt)
      val prData = fetchPRForWorktree(wt, prCache, now)
      val reviewState = fetchReviewStateForWorktree(wt, reviewStateCache)
      (wt, issueData, progress, gitStatus, prData, reviewState)
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
          WorktreeListView.render(worktreesWithData, now)
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

  /** Fetch workflow progress for a single worktree.
    *
    * Reads task files from the filesystem and parses progress.
    * Returns None on any error (missing files, read errors, etc.)
    *
    * @param wt Worktree registration
    * @param cache Current progress cache
    * @return Optional WorkflowProgress, None if unavailable
    */
  private def fetchProgressForWorktree(
    wt: WorktreeRegistration,
    cache: Map[String, CachedProgress]
  ): Option[WorkflowProgress] =
    // File I/O wrapper: read file lines
    val readFile = (path: String) => Try {
      val source = scala.io.Source.fromFile(path)
      try source.getLines().toSeq
      finally source.close()
    }.toEither.left.map(_.getMessage)

    // File I/O wrapper: get file modification time
    val getMtime = (path: String) => Try {
      java.nio.file.Files.getLastModifiedTime(
        java.nio.file.Paths.get(path)
      ).toMillis
    }.toEither.left.map(_.getMessage)

    // Call WorkflowProgressService with injected I/O functions
    WorkflowProgressService.fetchProgress(
      wt.issueId,
      wt.path,
      cache,
      readFile,
      getMtime
    ).toOption

  /** Fetch git status for a single worktree.
    *
    * Executes git commands to determine branch name and clean/dirty status.
    * Returns None on any error (not a git repo, command fails, etc.)
    *
    * @param wt Worktree registration
    * @return Optional GitStatus, None if unavailable
    */
  private def fetchGitStatusForWorktree(
    wt: WorktreeRegistration
  ): Option[GitStatus] =
    // Wrapper for CommandRunner.execute that doesn't need workingDir
    val execCommand = (command: String, args: Array[String]) =>
      CommandRunner.execute(command, args)

    // Call GitStatusService with injected command execution
    GitStatusService.getGitStatus(wt.path, execCommand).toOption

  /** Fetch PR data for a single worktree.
    *
    * Uses PR cache with TTL, re-fetches from gh/glab if expired.
    * Returns None on any error (no PR tool, no PR found, etc.)
    *
    * @param wt Worktree registration
    * @param cache Current PR cache
    * @param now Current timestamp for TTL validation
    * @return Optional PullRequestData, None if unavailable
    */
  private def fetchPRForWorktree(
    wt: WorktreeRegistration,
    cache: Map[String, CachedPR],
    now: Instant
  ): Option[PullRequestData] =
    // Wrapper for CommandRunner.execute with worktree path as working directory
    val execCommand = (command: String, args: Array[String]) =>
      CommandRunner.execute(command, args, Some(wt.path))

    // Wrapper for CommandRunner.isCommandAvailable
    val detectTool = (toolName: String) =>
      CommandRunner.isCommandAvailable(toolName)

    // Call PullRequestCacheService with injected functions
    PullRequestCacheService.fetchPR(
      wt.path,
      cache,
      wt.issueId,
      now,
      execCommand,
      detectTool
    ).toOption.flatten

  /** Fetch review state for a single worktree.
    *
    * Reads review-state.json from the worktree and parses review state.
    * Uses cache with mtime validation.
    * Returns None on any error (missing file, read errors, parse errors, etc.)
    *
    * @param wt Worktree registration
    * @param cache Current review state cache
    * @return Optional ReviewState, None if unavailable
    */
  private def fetchReviewStateForWorktree(
    wt: WorktreeRegistration,
    cache: Map[String, CachedReviewState]
  ): Option[ReviewState] =
    // File I/O wrapper: read file content
    val readFile = (path: String) => Try {
      val source = scala.io.Source.fromFile(path)
      try source.mkString
      finally source.close()
    }.toEither.left.map(_.getMessage)

    // File I/O wrapper: get file modification time
    val getMtime = (path: String) => Try {
      java.nio.file.Files.getLastModifiedTime(
        java.nio.file.Paths.get(path)
      ).toMillis
    }.toEither.left.map(_.getMessage)

    // Call ReviewStateService with injected I/O functions
    ReviewStateService.fetchReviewState(
      wt.issueId,
      wt.path,
      cache,
      readFile,
      getMtime
    ).toOption

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

    .phase-info {
      margin: 8px 0;
      font-size: 0.9em;
    }

    .phase-label {
      font-weight: 600;
      color: #495057;
      display: block;
      margin-bottom: 4px;
    }

    .progress-container {
      position: relative;
      background: #e9ecef;
      border-radius: 4px;
      height: 20px;
      overflow: hidden;
    }

    .progress-bar {
      background: linear-gradient(90deg, #51cf66, #37b24d);
      height: 100%;
      transition: width 0.3s ease;
    }

    .progress-text {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      text-align: center;
      line-height: 20px;
      font-size: 0.85em;
      color: #212529;
      font-weight: 600;
    }

    .git-status {
      margin: 10px 0;
      font-size: 0.9em;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .git-branch {
      color: #495057;
      font-weight: 500;
    }

    .git-indicator {
      padding: 2px 8px;
      border-radius: 3px;
      font-size: 0.85em;
      font-weight: 500;
    }

    .git-clean {
      background: #d3f9d8;
      color: #2b8a3e;
    }

    .git-dirty {
      background: #fff3bf;
      color: #e67700;
    }

    .pr-link {
      margin: 10px 0;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .pr-button {
      padding: 4px 12px;
      background: #228be6;
      color: white;
      text-decoration: none;
      border-radius: 4px;
      font-size: 0.9em;
      font-weight: 500;
      transition: background 0.2s;
    }

    .pr-button:hover {
      background: #1c7ed6;
    }

    .pr-badge {
      padding: 2px 8px;
      border-radius: 3px;
      font-size: 0.85em;
      font-weight: 500;
      color: white;
    }

    .pr-open {
      background: #228be6;
    }

    .pr-merged {
      background: #9775fa;
    }

    .pr-closed {
      background: #868e96;
    }

    .review-artifacts {
      margin: 15px 0;
      padding: 15px;
      background: #f8f9fa;
      border-radius: 6px;
    }

    .review-artifacts h4 {
      margin: 0 0 10px 0;
      font-size: 0.95em;
      font-weight: 600;
      color: #495057;
    }

    .artifact-list {
      list-style: none;
      padding: 0;
      margin: 0;
    }

    .artifact-list li {
      margin: 4px 0;
    }

    .artifact-list a {
      color: #0066cc;
      text-decoration: none;
      font-size: 0.9em;
    }

    .artifact-list a:hover {
      text-decoration: underline;
    }
  """
