// PURPOSE: Application service for rendering the complete dashboard HTML
// PURPOSE: Generates full HTML page with header, project cards, and styling

package iw.core.dashboard

import iw.core.model.{Issue, IssueId, ApiToken, ProjectConfiguration, Constants, WorktreeRegistration, IssueData, WorkflowProgress, GitStatus, PullRequestData, ReviewState, CachedIssue, CachedProgress, CachedPR, CachedReviewState}
import iw.core.adapters.{LinearClient, YouTrackClient, GitHubClient, ConfigFileRepository, CommandRunner}
import iw.core.dashboard.application.MainProjectService
import iw.core.dashboard.presentation.views.{MainProjectsView, PageLayout, ProjectSummary}
import scalatags.Text.all.*
import java.time.Instant
import scala.util.Try

object DashboardService:
  /** Render dashboard with cached data only (read-only).
    *
    * Dashboard no longer computes or updates caches. Per-card refresh handles all cache updates.
    *
    * @param worktrees List of registered worktrees
    * @param issueCache Current issue cache
    * @param progressCache Current progress cache
    * @param prCache Current PR cache
    * @param reviewStateCache Current review state cache
    * @param config Project configuration (for tracker type and team)
    * @param sshHost SSH hostname for Zed editor remote connections
    * @param devMode Whether to show DEV MODE banner (default: false)
    * @return HTML page as string
    */
  def renderDashboard(
    worktrees: List[WorktreeRegistration],
    issueCache: Map[String, CachedIssue],
    progressCache: Map[String, CachedProgress],
    prCache: Map[String, CachedPR],
    reviewStateCache: Map[String, CachedReviewState],
    config: Option[ProjectConfiguration],
    sshHost: String,
    devMode: Boolean = false
  ): String =
    val now = Instant.now()

    // Sort worktrees by issue ID (alphabetical ascending)
    val sortedWorktrees = worktrees.sortBy(_.issueId)

    // Derive main projects from registered worktrees
    val mainProjects = MainProjectService.deriveFromWorktrees(
      sortedWorktrees,
      MainProjectService.loadConfig
    )

    // Compute project summaries with worktree counts and attention indicators
    val projectSummaries = ProjectSummary.computeSummaries(
      sortedWorktrees,
      mainProjects,
      reviewStateCache
    )

    // Prepare body content for PageLayout
    val bodyContent = frag(
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
      // Main projects section
      MainProjectsView.render(projectSummaries),
      // Modal container (empty by default)
      div(id := "modal-container")
    )

    // Use PageLayout to render complete HTML shell
    PageLayout.render(
      title = "iw Dashboard",
      bodyContent = bodyContent,
      devMode = devMode
    )

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
  private[dashboard] def fetchIssueForWorktreeCachedOnly(
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
          val tokenOpt = ApiToken.fromEnv(iw.core.model.Constants.EnvVars.LinearApiToken)
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
          val tokenOpt = ApiToken.fromEnv(iw.core.model.Constants.EnvVars.YouTrackApiToken)
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
  private[dashboard] def fetchProgressForWorktree(
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
      None, // No explicit task list paths, use discovery
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
  private[dashboard] def fetchGitStatusForWorktree(
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
  private[dashboard] def fetchPRForWorktreeCachedOnly(
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

