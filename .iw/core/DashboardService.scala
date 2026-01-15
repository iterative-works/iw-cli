// PURPOSE: Application service for rendering the complete dashboard HTML
// PURPOSE: Generates full HTML page with header, worktree list, and styling

package iw.core.application

import iw.core.{Issue, IssueId, ApiToken, LinearClient, YouTrackClient, GitHubClient, ProjectConfiguration, ConfigFileRepository, Constants}
import iw.core.domain.{WorktreeRegistration, IssueData, CachedIssue, WorkflowProgress, CachedProgress, GitStatus, PullRequestData, CachedPR, ReviewState, CachedReviewState}
import iw.core.infrastructure.CommandRunner
import iw.core.presentation.views.{WorktreeListView, MainProjectsView}
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
    * @param sshHost SSH hostname for Zed editor remote connections
    * @return Tuple of (HTML page as string, updated review state cache)
    */
  def renderDashboard(
    worktrees: List[WorktreeRegistration],
    issueCache: Map[String, CachedIssue],
    progressCache: Map[String, CachedProgress],
    prCache: Map[String, CachedPR],
    reviewStateCache: Map[String, CachedReviewState],
    config: Option[ProjectConfiguration],
    sshHost: String
  ): (String, Map[String, CachedReviewState]) =
    val now = Instant.now()

    // Derive main projects from registered worktrees
    val mainProjects = MainProjectService.deriveFromWorktrees(
      worktrees,
      MainProjectService.loadConfig
    )

    // Fetch data for each worktree and accumulate updated review state cache
    val (worktreesWithData, updatedReviewStateCache) = worktrees.foldLeft(
      (List.empty[(WorktreeRegistration, Option[(IssueData, Boolean, Boolean)], Option[WorkflowProgress], Option[GitStatus], Option[PullRequestData], Option[Either[String, ReviewState]])], reviewStateCache)
    ) { case ((acc, cache), wt) =>
      val issueData = fetchIssueForWorktreeCachedOnly(wt, issueCache, now)
      val progress = fetchProgressForWorktree(wt, progressCache)
      val gitStatus = fetchGitStatusForWorktree(wt)
      val prData = fetchPRForWorktreeCachedOnly(wt, prCache, now)
      val cachedReviewStateResult = fetchReviewStateForWorktree(wt, cache)

      // Extract ReviewState for view and update cache
      // Only update cache for valid states (Some(Right))
      val (reviewStateResult, newCache) = cachedReviewStateResult match {
        case None =>
          // No review state file
          (None, cache)
        case Some(Left(error)) =>
          // Invalid review state - pass error to view, don't update cache
          (Some(Left(error)), cache)
        case Some(Right(cached)) =>
          // Valid review state - pass to view and update cache
          (Some(Right(cached.state)), cache + (wt.issueId -> cached))
      }

      val worktreeData = (wt, issueData, progress, gitStatus, prData, reviewStateResult)
      (worktreeData :: acc, newCache)
    }

    val page = html(
      head(
        meta(charset := "UTF-8"),
        tag("title")("iw Dashboard"),
        // Add HTMX library
        tag("script")(
          src := "https://unpkg.com/htmx.org@1.9.10",
          attr("integrity") := "sha384-D1Kt99CQMDuVetoL1lrYwg5t+9QdHe7NLX/SoJYkXDFfX37iInKRy5xLSi8nO7UC",
          attr("crossorigin") := "anonymous"
        ),
        tag("script")(raw("""
          document.addEventListener('visibilitychange', function() {
            if (document.visibilityState === 'visible') {
              htmx.trigger(document.body, 'refresh');
            }
          });
        """)),
        tag("style")(raw(styles))
      ),
      body(
        div(
          cls := "container",
          // Header with title and SSH host configuration
          div(
            cls := "dashboard-header",
            h1("iw Dashboard"),
            // SSH host configuration form
            tag("form")(
              cls := "ssh-host-form",
              attr("method") := "get",
              tag("label")(
                attr("for") := "ssh-host-input",
                "SSH Host:"
              ),
              input(
                `type` := "text",
                cls := "ssh-host-input",
                id := "ssh-host-input",
                name := "sshHost",
                value := sshHost,
                placeholder := "hostname"
              ),
              button(
                `type` := "submit",
                cls := "ssh-host-submit",
                "Set"
              )
            )
          ),
          // Main projects section (above worktree list)
          MainProjectsView.render(mainProjects),
          WorktreeListView.render(worktreesWithData.reverse, now, sshHost),
          // Modal container (empty by default)
          div(id := "modal-container")
        )
      )
    )

    ("<!DOCTYPE html>\n" + page.render, updatedReviewStateCache)

  /** Fetch issue data for a single worktree from cache only (non-blocking).
    *
    * Returns cached data without calling API, regardless of cache age.
    * Calculates whether data is stale for display indicator.
    *
    * @param wt Worktree registration
    * @param cache Current issue cache
    * @param now Current timestamp for staleness check
    * @return Optional tuple of (IssueData, fromCache flag, isStale flag)
    */
  private def fetchIssueForWorktreeCachedOnly(
    wt: WorktreeRegistration,
    cache: Map[String, CachedIssue],
    now: Instant
  ): Option[(IssueData, Boolean, Boolean)] =
    cache.get(wt.issueId) match
      case Some(cached) =>
        val isStale = CachedIssue.isStale(cached, now)
        Some((cached.data, true, isStale))
      case None =>
        None

  /** Fetch issue data for a single worktree using cache or API.
    *
    * Loads config from the worktree's path to get correct tracker settings
    * (e.g., youtrackBaseUrl for YouTrack, repository for GitHub).
    *
    * @param wt Worktree registration
    * @param cache Current issue cache
    * @param now Current timestamp
    * @return Optional tuple of (IssueData, fromCache flag)
    */
  private def fetchIssueForWorktree(
    wt: WorktreeRegistration,
    cache: Map[String, CachedIssue],
    now: Instant
  ): Option[(IssueData, Boolean)] =
    // Load config from worktree's path to get correct tracker settings
    val configPath = os.Path(wt.path) / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
    val config = ConfigFileRepository.read(configPath)

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

        case "github" =>
          // Get repository from config
          val repositoryOpt = config.flatMap(_.repository)
          // Extract issue number from issueId (handles "72", "IW-72", "#72")
          val issueNumber = extractGitHubIssueNumber(issueId)

          repositoryOpt match
            case Some(repository) =>
              GitHubClient.fetchIssue(issueNumber, repository)
            case None =>
              Left("GitHub repository not configured")

        case _ =>
          Left(s"Unknown tracker type: $trackerType")

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
      // Pass appropriate config value based on tracker type
      val configValue = trackerType.toLowerCase match
        case "github" => config.flatMap(_.repository)
        case "youtrack" => config.flatMap(_.youtrackBaseUrl)
        case "gitlab" =>
          // GitLab needs both repository and optional baseUrl
          // Format: "repository" or "repository|baseUrl"
          config.flatMap(_.repository).map { repo =>
            config.flatMap(_.youtrackBaseUrl) match
              case Some(baseUrl) => s"$repo|$baseUrl"
              case None => repo
          }
        case _ => None
      IssueCacheService.buildIssueUrl(issueId, trackerType, configValue)

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

  /** Fetch PR data for a single worktree from cache only (non-blocking).
    *
    * Returns cached PR data without calling CLI, regardless of cache age.
    * PR cache doesn't need isStale flag in phase 1 (not displayed separately).
    *
    * @param wt Worktree registration
    * @param cache Current PR cache
    * @param now Current timestamp (not used but kept for consistency)
    * @return Optional PullRequestData, None if not cached
    */
  private def fetchPRForWorktreeCachedOnly(
    wt: WorktreeRegistration,
    cache: Map[String, CachedPR],
    now: Instant
  ): Option[PullRequestData] =
    PullRequestCacheService.getCachedOnly(wt.issueId, cache)

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
    *
    * Returns:
    * - None: No review state file (normal case, not an error)
    * - Some(Left(error)): File exists but is invalid (parse error, malformed JSON)
    * - Some(Right(cached)): Valid review state
    *
    * Invalid states are logged to stderr for debugging.
    *
    * @param wt Worktree registration
    * @param cache Current review state cache
    * @return Option[Either[String, CachedReviewState]]
    */
  private def fetchReviewStateForWorktree(
    wt: WorktreeRegistration,
    cache: Map[String, CachedReviewState]
  ): Option[Either[String, CachedReviewState]] =
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
    val reviewStatePath = s"${wt.path}/project-management/issues/${wt.issueId}/review-state.json"

    ReviewStateService.fetchReviewState(
      wt.issueId,
      wt.path,
      cache,
      readFile,
      getMtime
    ) match {
      case Left(err) if err == reviewStatePath || err.contains("NoSuchFileException") || err.contains("File not found") =>
        // Normal case - no review state file (error message is just the path or explicit "not found")
        None
      case Left(err) =>
        // Invalid state file - log warning and return error
        System.err.println(s"[WARN] Failed to load review state for ${wt.issueId}: $err")
        Some(Left(err))
      case Right(cached) =>
        // Valid state
        Some(Right(cached))
    }

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
      transition: opacity 200ms ease-in-out;
      min-height: 200px;
    }

    .htmx-swapping {
      opacity: 0;
    }

    .htmx-settling {
      opacity: 1;
    }

    .skeleton-card {
      background: linear-gradient(90deg, #f0f0f0 25%, #f8f8f8 50%, #f0f0f0 75%);
      background-size: 200% 100%;
      animation: shimmer 1.5s infinite;
      opacity: 0.7;
    }

    .skeleton-card .skeleton-title {
      background: #e0e0e0;
      color: transparent;
      border-radius: 4px;
    }

    @keyframes shimmer {
      0% {
        background-position: 200% 0;
      }
      100% {
        background-position: -200% 0;
      }
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

    .stale-indicator {
      font-size: 0.85em;
      color: #e67700;
      font-weight: 600;
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

    .zed-link {
      margin: 10px 0;
      display: flex;
      align-items: center;
    }

    .zed-button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 4px;
      background: transparent;
      border: 1px solid #e9ecef;
      border-radius: 4px;
      transition: all 0.2s;
      text-decoration: none;
    }

    .zed-button:hover {
      background: #f8f9fa;
      border-color: #228be6;
    }

    .zed-button img {
      display: block;
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

    /* Review phase number */
    .review-phase {
      font-size: 0.85em;
      color: #666;
      font-weight: normal;
      margin-left: 8px;
    }

    /* Review status badge */
    .review-status {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 0.85em;
      font-weight: 600;
      margin: 8px 0;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .review-status-label {
      color: white;
    }

    /* Status-specific colors */
    .review-status-awaiting-review {
      background-color: #28a745;
    }

    .review-status-in-progress {
      background-color: #ffc107;
    }

    .review-status-in-progress .review-status-label {
      color: #333;
    }

    .review-status-completed {
      background-color: #6c757d;
    }

    .review-status-default {
      background-color: #007bff;
    }

    /* Review message */
    .review-message {
      margin: 8px 0;
      padding: 8px 12px;
      background: #f8f9fa;
      border-left: 3px solid #007bff;
      font-size: 0.9em;
      color: #495057;
      border-radius: 4px;
    }

    /* Error state for invalid review state files */
    .review-error {
      background-color: #fff3cd;
      border-left: 4px solid #ffc107;
      padding: 12px;
      margin-top: 12px;
    }

    .review-error-message {
      font-weight: bold;
      color: #856404;
      margin: 0 0 8px 0;
    }

    .review-error-detail {
      color: #856404;
      font-size: 0.9em;
      margin: 0;
    }

    /* Dashboard header with Create Worktree button */
    .dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 30px;
    }

    .dashboard-header h1 {
      margin: 0;
    }

    /* SSH host configuration form */
    .ssh-host-form {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .ssh-host-form label {
      font-size: 14px;
      color: #495057;
      font-weight: 500;
    }

    .ssh-host-input {
      padding: 6px 12px;
      font-size: 14px;
      border: 1px solid #ced4da;
      border-radius: 4px;
      outline: none;
      transition: border-color 0.2s;
      width: 200px;
    }

    .ssh-host-input:focus {
      border-color: #228be6;
      box-shadow: 0 0 0 2px rgba(34, 139, 230, 0.1);
    }

    .ssh-host-submit {
      padding: 6px 14px;
      background: #228be6;
      color: white;
      border: none;
      border-radius: 4px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.2s;
    }

    .ssh-host-submit:hover {
      background: #1c7ed6;
    }

    .create-worktree-btn {
      padding: 10px 20px;
      background: #228be6;
      color: white;
      border: none;
      border-radius: 6px;
      font-size: 16px;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.2s;
    }

    .create-worktree-btn:hover {
      background: #1c7ed6;
    }

    /* Modal overlay */
    .modal {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .modal-backdrop {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
    }

    .modal-content {
      position: relative;
      background: white;
      border-radius: 8px;
      padding: 0;
      max-width: 600px;
      width: 90%;
      max-height: 80vh;
      overflow: hidden;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      z-index: 1001;
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 20px;
      border-bottom: 1px solid #e9ecef;
    }

    .modal-header h2 {
      margin: 0;
      font-size: 20px;
      color: #333;
    }

    .modal-close {
      background: none;
      border: none;
      font-size: 28px;
      color: #999;
      cursor: pointer;
      padding: 0;
      width: 32px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 4px;
    }

    .modal-close:hover {
      background: #f5f5f5;
      color: #333;
    }

    .modal-body {
      padding: 20px;
      max-height: calc(80vh - 80px);
      overflow-y: auto;
    }

    /* Search input */
    #issue-search-input {
      width: 100%;
      padding: 12px 16px;
      font-size: 16px;
      border: 2px solid #e9ecef;
      border-radius: 6px;
      outline: none;
      transition: border-color 0.2s;
      box-sizing: border-box;
    }

    #issue-search-input:focus {
      border-color: #228be6;
    }

    /* Search results */
    .search-results {
      margin-top: 16px;
    }

    .search-result-item {
      padding: 12px 16px;
      border: 1px solid #e9ecef;
      border-radius: 6px;
      margin-bottom: 8px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .search-result-item:hover {
      border-color: #228be6;
      background: #f8f9fa;
    }

    .disabled {
      pointer-events: none;
      opacity: 0.5;
    }

    .search-result-id {
      font-size: 14px;
      color: #228be6;
      font-weight: 600;
      margin-bottom: 4px;
    }

    .search-result-title {
      font-size: 16px;
      color: #333;
      margin-bottom: 4px;
    }

    .search-result-status {
      font-size: 13px;
      color: #666;
    }

    .search-empty-state {
      text-align: center;
      padding: 20px;
      color: #999;
      font-size: 14px;
    }

    /* Main projects section */
    .main-projects-section {
      margin-bottom: 40px;
    }

    .main-projects-section h2 {
      margin: 0 0 20px 0;
      font-size: 22px;
      color: #333;
    }

    .main-projects-list {
      display: grid;
      gap: 20px;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    }

    .main-project-card {
      background: white;
      border: 2px solid #e9ecef;
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.08);
      transition: all 0.2s;
    }

    .main-project-card:hover {
      border-color: #228be6;
      box-shadow: 0 4px 8px rgba(0,0,0,0.12);
    }

    .main-project-card h3 {
      margin: 0 0 12px 0;
      color: #333;
      font-size: 18px;
      font-weight: 600;
    }

    .project-info {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin-bottom: 16px;
      font-size: 14px;
    }

    .tracker-type {
      color: #495057;
      font-weight: 500;
    }

    .team-info {
      color: #868e96;
    }

    .create-worktree-button {
      width: 100%;
      padding: 10px 16px;
      background: #228be6;
      color: white;
      border: none;
      border-radius: 6px;
      font-size: 15px;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.2s;
    }

    .create-worktree-button:hover {
      background: #1c7ed6;
    }

    .main-projects-empty {
      background: white;
      border: 2px dashed #dee2e6;
      border-radius: 8px;
      padding: 40px;
      text-align: center;
      margin-bottom: 40px;
    }

    .main-projects-empty h3 {
      margin: 0 0 10px 0;
      color: #495057;
      font-size: 18px;
    }

    .main-projects-empty p {
      margin: 0;
      color: #868e96;
      font-size: 14px;
    }

    /* Modal project name in title */
    .modal-project-name {
      color: #868e96;
      font-weight: normal;
      font-size: 16px;
    }

    /* Mobile-friendly styling */
    @media (max-width: 768px) {
      .worktree-list {
        grid-template-columns: 1fr;
      }

      .main-projects-list {
        grid-template-columns: 1fr;
      }
    }

    /* Touch-friendly buttons and links */
    button, .pr-button, .create-worktree-btn, .create-worktree-button, .modal-close {
      min-height: 44px;
      touch-action: manipulation;
    }
  """
