// PURPOSE: Infrastructure layer for HTTP server using Cask framework
// PURPOSE: Provides dashboard HTML route and health check endpoint on port 9876

package iw.core.dashboard

import iw.core.adapters.{ConfigFileRepository, LinearClient, GitHubClient, YouTrackClient, GitWorktreeAdapter, TmuxAdapter, CommandRunner}
import iw.core.model.{Constants, ProjectConfiguration, IssueId, ApiToken, WorktreePath, IssueTrackerType, ServerState, IssueData}
import iw.core.dashboard.{ServerStateService, DashboardService, WorktreeRegistrationService, WorktreeUnregistrationService, ArtifactService, IssueSearchService, WorktreeCardService, RefreshThrottle, WorktreeListSync, CardRenderResult}
import iw.core.dashboard.application.{WorktreeCreationService, MainProjectService}
import iw.core.dashboard.domain.{WorktreeCreationError, WorktreeCreationResult}
import iw.core.dashboard.presentation.views.{ArtifactView, CreateWorktreeModal, SearchResultsView, CreationSuccessView, CreationErrorView, ProjectDetailsView, PageLayout}
import java.time.Instant

class CaskServer(statePath: String, port: Int, hosts: Seq[String], startedAt: Instant, devMode: Boolean = false) extends cask.MainRoutes:
  private val repository = StateRepository(statePath)
  private val stateService = new ServerStateService(repository)
  private val refreshThrottle = RefreshThrottle()

  // Initialize state service at startup
  stateService.initialize() match
    case Left(error) =>
      System.err.println(s"Failed to initialize state service: $error")
      // Continue with empty state
    case Right(_) =>
      // State loaded successfully
      ()

  @cask.get("/")
  def dashboard(sshHost: Option[String] = None): cask.Response[String] =
    // Resolve effective SSH host: use query param or default to server hostname
    val effectiveSshHost = sshHost.getOrElse(
      java.net.InetAddress.getLocalHost().getHostName()
    )

    // Get current state (read-only)
    val state = stateService.getState

    // Auto-prune non-existent worktrees
    val prunedIds = stateService.pruneWorktrees(
      wt => os.exists(os.Path(wt.path, os.pwd))
    )

    val worktrees = state.listByIssueId

    // Load project configuration
    val configPath = os.pwd / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
    val config = ConfigFileRepository.read(configPath)

    // Render dashboard with cached data only (read-only, no writes)
    val html = DashboardService.renderDashboard(
      worktrees,
      state.issueCache,
      state.progressCache,
      state.prCache,
      state.reviewStateCache,
      config,
      sshHost = effectiveSshHost,
      devMode = devMode
    )

    cask.Response(
      data = html,
      headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
    )

  @cask.get("/projects/:projectName")
  def projectDetails(projectName: String, sshHost: Option[String] = None): cask.Response[String] =
    // Resolve effective SSH host: use query param or default to server hostname
    val effectiveSshHost = sshHost.getOrElse(
      java.net.InetAddress.getLocalHost().getHostName()
    )

    // Get current state (read-only)
    val state = stateService.getState

    // Get all registered worktrees
    val allWorktrees = state.listByIssueId

    // Filter worktrees by project name
    val filteredWorktrees = MainProjectService.filterByProjectName(allWorktrees, projectName)

    // Derive project metadata using MainProjectService
    val projects = MainProjectService.deriveFromWorktrees(
      filteredWorktrees,
      MainProjectService.loadConfig
    )

    // Get the main project (should be exactly one since we filtered by name)
    val mainProjectOpt = projects.headOption

    mainProjectOpt match
      case None =>
        // Project not found - return styled 404 page
        val bodyContent = ProjectDetailsView.renderNotFound(projectName)
        val html = PageLayout.render(
          title = s"$projectName - Not Found",
          bodyContent = bodyContent,
          devMode = devMode
        )
        cask.Response(
          data = html,
          statusCode = 404,
          headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
        )

      case Some(mainProject) =>
        // Fetch cached data for each filtered worktree
        val now = Instant.now()
        val worktreesWithData = filteredWorktrees.map { wt =>
          val issueData = DashboardService.fetchIssueForWorktreeCachedOnly(wt, state.issueCache, now)
          val progress = DashboardService.fetchProgressForWorktree(wt, state.progressCache)
          val gitStatus = DashboardService.fetchGitStatusForWorktree(wt)
          val prData = DashboardService.fetchPRForWorktreeCachedOnly(wt, state.prCache, now)
          val reviewStateResult = state.reviewStateCache.get(wt.issueId).map(cached => Right(cached.state))

          (wt, issueData, progress, gitStatus, prData, reviewStateResult)
        }

        // Render page body using ProjectDetailsView
        val bodyContent = ProjectDetailsView.render(
          projectName,
          mainProject,
          worktreesWithData,
          now,
          effectiveSshHost
        )

        // Wrap in PageLayout
        val html = PageLayout.render(
          title = s"$projectName - iw Dashboard",
          bodyContent = bodyContent,
          devMode = devMode
        )

        cask.Response(
          data = html,
          headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
        )

  @cask.get("/worktrees/:issueId/artifacts")
  def artifactPage(issueId: String, path: String): cask.Response[String] =
    // path comes from query param via cask binding
    val artifactPath = path

    // Get current server state (read-only)
    val state = stateService.getState

    // File I/O wrapper: read file content
    val readFile = (filePath: java.nio.file.Path) => scala.util.Try {
      val source = scala.io.Source.fromFile(filePath.toFile)
      try source.mkString
      finally source.close()
    }.toEither.left.map(_.getMessage)

    // Load and render artifact
    ArtifactService.loadArtifact(issueId, artifactPath, state, readFile) match
      case Right((label, html, worktreePath)) =>
        val page = ArtifactView.render(label, html, issueId)
        cask.Response(
          data = page,
          headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
        )

      case Left(error) =>
        // Log error for debugging (may contain filesystem info)
        System.err.println(s"Artifact error for $issueId/$artifactPath: $error")

        // Return generic error to user (secure)
        cask.Response(
          data = ArtifactView.renderError(issueId, error),
          statusCode = 404,
          headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
        )

  @cask.get("/worktrees/:issueId/card")
  def worktreeCard(issueId: String, have: Option[String] = None): cask.Response[String] =
    // Note: `have` param is inherited from parent's hx-vals due to HTMX bug #1119
    // (hx-disinherit doesn't work for hx-vals). Remove this param when bug is fixed.
    // See: https://github.com/bigskysoftware/htmx/issues/1119
    // Get current server state (read-only)
    val state = stateService.getState

    // Build fetch function and URL builder based on worktree's tracker type
    val worktreeOpt = state.worktrees.get(issueId)
    worktreeOpt match
      case None =>
        // Worktree not found
        cask.Response(
          data = "",
          statusCode = 404
        )

      case Some(worktree) =>
        // Load project configuration from worktree's path (not os.pwd)
        // This ensures we get the correct tracker settings (e.g., youtrackBaseUrl)
        val configPath = os.Path(worktree.path) / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
        val config = ConfigFileRepository.read(configPath)

        // Build fetch function based on tracker type
        val fetchFn = buildFetchFunction(worktree.trackerType, config)
        val urlBuilder = buildUrlBuilder(worktree.trackerType, config)

        // Build PR fetch function using CommandRunner
        val fetchPRFn = () =>
          val execCommand = (command: String, args: Array[String]) =>
            CommandRunner.execute(command, args, Some(worktree.path))
          val detectTool = (toolName: String) =>
            CommandRunner.isCommandAvailable(toolName)
          PullRequestCacheService.fetchPR(
            worktree.path,
            state.prCache,
            issueId,
            Instant.now(),
            execCommand,
            detectTool
          )

        // Render the card
        val now = Instant.now()
        val sshHost = java.net.InetAddress.getLocalHost().getHostName()
        val result = WorktreeCardService.renderCard(
          issueId,
          state.worktrees,
          state.issueCache,
          state.progressCache,
          state.prCache,
          state.reviewStateCache,
          refreshThrottle,
          now,
          sshHost,
          fetchFn,
          urlBuilder,
          fetchPRFn
        )

        // Update all caches with freshly fetched data
        result.fetchedIssue.foreach { cachedIssue =>
          stateService.updateIssueCache(issueId)(_ => Some(cachedIssue))
        }
        result.fetchedProgress.foreach { cachedProgress =>
          stateService.updateProgressCache(issueId)(_ => Some(cachedProgress))
        }
        result.fetchedPR.foreach { cachedPR =>
          stateService.updatePRCache(issueId)(_ => Some(cachedPR))
        }
        result.fetchedReviewState.foreach { cachedReviewState =>
          stateService.updateReviewStateCache(issueId)(_ => Some(cachedReviewState))
        }

        cask.Response(
          data = result.html,
          headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
        )

  @cask.get("/api/worktrees/:issueId/refresh")
  def refreshWorktree(issueId: String): ujson.Value =
    val now = Instant.now()

    if refreshThrottle.shouldRefresh(issueId, now) then
      // Not throttled - record refresh
      refreshThrottle.recordRefresh(issueId, now)
      ujson.Obj("status" -> "refreshed")
    else
      // Throttled - too soon since last refresh
      ujson.Obj("status" -> "throttled")

  @cask.get("/api/worktrees/changes")
  def worktreeChanges(have: Option[String] = None): cask.Response[String] =
    // Resolve effective SSH host from server hostname
    val sshHost = java.net.InetAddress.getLocalHost().getHostName()

    // Get current server state
    val state = stateService.getState
    val now = Instant.now()

    // Get current worktree IDs ordered by issue ID
    val currentWorktrees = state.listByIssueId
    val currentIds = currentWorktrees.map(_.issueId)

    // Parse client's known IDs from `have` param (comma-separated)
    val clientIds = have
      .map(_.split(",").map(_.trim).filter(_.nonEmpty).toList)
      .getOrElse(List.empty)

    // Detect changes between client's list and server's current list
    val changes = WorktreeListSync.detectChanges(clientIds, currentIds)

    // Generate OOB response for any changes
    val html = WorktreeListSync.generateChangesResponse(
      changes,
      state.worktrees,
      state.issueCache,
      state.progressCache,
      state.prCache,
      state.reviewStateCache,
      now,
      sshHost,
      currentIds
    )

    cask.Response(
      data = html,
      headers = Seq(
        "Content-Type" -> "text/html; charset=UTF-8"
      )
    )

  @cask.get("/api/projects/:projectName/worktrees/changes")
  def projectWorktreeChanges(projectName: String, have: Option[String] = None): cask.Response[String] =
    // Resolve effective SSH host from server hostname
    val sshHost = java.net.InetAddress.getLocalHost().getHostName()

    // Get current server state
    val state = stateService.getState
    val now = Instant.now()

    // Get worktrees filtered by project name
    val allWorktrees = state.listByIssueId
    val filteredWorktrees = MainProjectService.filterByProjectName(allWorktrees, projectName)
    val currentIds = filteredWorktrees.map(_.issueId)

    // Parse client's known IDs from `have` param (comma-separated)
    val clientIds = have
      .map(_.split(",").map(_.trim).filter(_.nonEmpty).toList)
      .getOrElse(List.empty)

    // Detect changes between client's list and server's current filtered list
    val changes = WorktreeListSync.detectChanges(clientIds, currentIds)

    // Generate OOB response for any changes
    val html = WorktreeListSync.generateChangesResponse(
      changes,
      state.worktrees,
      state.issueCache,
      state.progressCache,
      state.prCache,
      state.reviewStateCache,
      now,
      sshHost,
      currentIds
    )

    cask.Response(
      data = html,
      headers = Seq(
        "Content-Type" -> "text/html; charset=UTF-8"
      )
    )

  @cask.get("/health")
  def health(): ujson.Value =
    ujson.Obj("status" -> "ok")

  @cask.get("/static/:filename")
  def staticFiles(filename: String): cask.Response[Array[Byte]] =
    // Determine static files directory relative to CWD (project root)
    val staticDir = os.pwd / ".iw" / "core" / "dashboard" / "resources" / "static"
    val filePath = staticDir / filename

    // Check if file exists
    if os.exists(filePath) && os.isFile(filePath) then
      // Read file content
      val content = os.read.bytes(filePath)

      // Determine Content-Type based on file extension
      val contentType = filename match
        case f if f.endsWith(".css") => "text/css; charset=UTF-8"
        case f if f.endsWith(".js") => "application/javascript; charset=UTF-8"
        case _ => "application/octet-stream"

      cask.Response(
        data = content,
        headers = Seq("Content-Type" -> contentType)
      )
    else
      // File not found
      cask.Response(
        data = Array.empty[Byte],
        statusCode = 404
      )

  @cask.get("/api/status")
  def status(): ujson.Value =
    val state = stateService.getState
    val worktreeCount = state.worktrees.size

    ujson.Obj(
      "status" -> "running",
      "port" -> port,
      "hosts" -> ujson.Arr.from(hosts),
      "worktreeCount" -> worktreeCount,
      "startedAt" -> startedAt.toString
    )

  @cask.put("/api/v1/worktrees/:issueId")
  def registerWorktree(issueId: String, request: cask.Request): cask.Response[ujson.Value] =
    val parsedBody: Either[cask.Response[ujson.Value], ujson.Value] = try
      // Parse request body
      val bodyBytes = request.readAllBytes()
      val bodyStr = new String(bodyBytes, "UTF-8")
      Right(ujson.read(bodyStr))
    catch
      case e: Throwable if e.getClass.getName.contains("ParseException") ||
                           e.getClass.getName.contains("Abort") ||
                           e.getClass.getName.contains("TraceException") =>
        Left(cask.Response(
          data = ujson.Obj(
            "code" -> "MALFORMED_JSON",
            "message" -> s"Malformed JSON: ${e.getMessage}"
          ),
          statusCode = 400
        ))
      case e: Exception =>
        System.err.println(s"Error reading request body: ${e.getClass.getName}: ${e.getMessage}")
        Left(cask.Response(
          data = ujson.Obj(
            "code" -> "INTERNAL_ERROR",
            "message" -> "Internal server error"
          ),
          statusCode = 500
        ))

    parsedBody match
      case Left(errorResponse) => errorResponse
      case Right(requestJson) =>
        try
          // Extract fields from request
          val path = requestJson("path").str
          val trackerType = requestJson("trackerType").str
          val team = requestJson("team").str

          // Get current state
          val currentState = stateService.getState

          // Register or update worktree with current timestamp
          val now = Instant.now()
          val registrationResult = WorktreeRegistrationService.register(
            issueId,
            path,
            trackerType,
            team,
            now,
            currentState
          )

          registrationResult match
            case Right((newState, wasCreated)) =>
              // Update worktree via service
              val registration = newState.worktrees(issueId)
              stateService.updateWorktree(issueId)(_ => Some(registration))

              cask.Response(
                data = ujson.Obj(
                  "status" -> "registered",
                  "issueId" -> issueId,
                  "lastSeenAt" -> registration.lastSeenAt.toString
                ),
                statusCode = if wasCreated then 201 else 200
              )

            case Left(error) =>
              cask.Response(
                data = ujson.Obj(
                  "code" -> "VALIDATION_ERROR",
                  "message" -> error
                ),
                statusCode = 400
              )

        catch
          case e: Throwable if e.getClass.getName.contains("ParseException") ||
                               e.getClass.getName.contains("Abort") ||
                               e.getClass.getName.contains("TraceException") =>
            cask.Response(
              data = ujson.Obj(
                "code" -> "MALFORMED_JSON",
                "message" -> s"Malformed JSON: ${e.getMessage}"
              ),
              statusCode = 400
            )
          case e: NoSuchElementException =>
            cask.Response(
              data = ujson.Obj(
                "code" -> "MISSING_FIELD",
                "message" -> s"Missing required field: ${e.getMessage}"
              ),
              statusCode = 400
            )
          case e: Exception =>
            System.err.println(s"Internal error: ${e.getMessage}")
            cask.Response(
              data = ujson.Obj(
                "code" -> "INTERNAL_ERROR",
                "message" -> "Internal server error"
              ),
              statusCode = 500
            )

  @cask.delete("/api/v1/worktrees/:issueId")
  def unregisterWorktree(issueId: String): cask.Response[ujson.Value] =
    val state = stateService.getState
    WorktreeUnregistrationService.unregister(state, issueId) match
      case Right(newState) =>
        // Remove worktree via service
        stateService.updateWorktree(issueId)(_ => None)
        cask.Response(
          ujson.Obj("status" -> "ok", "issueId" -> issueId),
          statusCode = 200
        )
      case Left(err) =>
        cask.Response(
          ujson.Obj("code" -> "NOT_FOUND", "message" -> err),
          statusCode = 404
        )

  @cask.get("/api/issues/search")
  def searchIssues(q: String, project: Option[String] = None): cask.Response[String] =
    // Load project configuration from specified path or CWD
    val projectPath = project.map(p => os.Path(p)).getOrElse(os.pwd)
    val configPath = projectPath / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
    val configOpt = ConfigFileRepository.read(configPath)

    configOpt match
      case None =>
        // No config - return empty results
        val html = SearchResultsView.render(List.empty, project).render
        cask.Response(
          data = html,
          statusCode = 200,
          headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
        )

      case Some(config) =>
        // Build fetch function based on tracker type
        val fetchIssue = buildFetchFunction(config)

        // Build search function based on tracker type
        val searchIssues = buildSearchFunction(config)

        // Call search service with both fetch and search functions
        IssueSearchService.search(q, config, fetchIssue, searchIssues) match
          case Right(results) =>
            val html = SearchResultsView.render(results, project).render
            cask.Response(
              data = html,
              statusCode = 200,
              headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
            )

          case Left(error) =>
            System.err.println(s"Search error: $error")
            val html = SearchResultsView.render(List.empty, project).render
            cask.Response(
              data = html,
              statusCode = 200,
              headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
            )

  @cask.get("/api/issues/recent")
  def recentIssues(project: Option[String] = None): cask.Response[String] =
    // Load project configuration from specified path or CWD
    val projectPath = project.map(p => os.Path(p)).getOrElse(os.pwd)
    val configPath = projectPath / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
    val configOpt = ConfigFileRepository.read(configPath)

    configOpt match
      case None =>
        // No config - return empty results
        val html = SearchResultsView.render(List.empty, project).render
        cask.Response(
          data = html,
          statusCode = 200,
          headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
        )

      case Some(config) =>
        // Build fetch recent function based on tracker type
        val fetchRecentIssues = buildFetchRecentFunction(config)

        // Call fetchRecent service
        IssueSearchService.fetchRecent(config, fetchRecentIssues) match
          case Right(results) =>
            val html = SearchResultsView.render(results, project).render
            cask.Response(
              data = html,
              statusCode = 200,
              headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
            )

          case Left(error) =>
            System.err.println(s"Fetch recent issues error: $error")
            val html = SearchResultsView.render(List.empty, project).render
            cask.Response(
              data = html,
              statusCode = 200,
              headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
            )

  @cask.get("/api/modal/create-worktree")
  def createWorktreeModal(project: Option[String] = None): cask.Response[String] =
    val html = CreateWorktreeModal.render(project).render
    cask.Response(
      data = html,
      statusCode = 200,
      headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
    )

  @cask.get("/api/modal/close")
  def closeModal(): cask.Response[String] =
    // Return empty content to clear the modal container
    cask.Response(
      data = "",
      statusCode = 200,
      headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
    )

  @cask.postForm("/api/worktrees/create")
  def createWorktree(issueId: String, projectPath: Option[String] = None): cask.Response[String] =
    // Load project configuration from specified path or CWD
    val projectPathResolved = projectPath.filter(_.nonEmpty).map(p => os.Path(p)).getOrElse(os.pwd)
    val configPath = projectPathResolved / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
    val configOpt = ConfigFileRepository.read(configPath)

    configOpt match
      case None =>
        cask.Response(
          data = renderErrorView("Project not configured. Run './iw init' first."),
          statusCode = 500,
          headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
        )

      case Some(config) =>
        // Build I/O functions for WorktreeCreationService
        // Use the specified project path instead of CWD

        // Fetch issue function
        val fetchIssue = (id: String) =>
          IssueId.parse(id, config.teamPrefix).flatMap { parsedId =>
            val issueResult = buildFetchFunction(config)(parsedId)
            issueResult.map { issue =>
              // Convert Issue to IssueData
              val url = buildIssueUrl(parsedId, config)
              IssueData.fromIssue(issue, url, Instant.now())
            }
          }

        // Create worktree function
        val createWorktreeOp = (path: String, branchName: String) =>
          val actualPath = projectPathResolved / os.up / os.RelPath(path.stripPrefix("../"))
          GitWorktreeAdapter.createWorktree(actualPath, branchName, projectPathResolved)

        // Create tmux session function
        val createTmuxOp = (sessionName: String, workPath: String) =>
          val actualPath = projectPathResolved / os.up / os.RelPath(workPath.stripPrefix("../"))
          TmuxAdapter.createSession(sessionName, actualPath)

        // Register worktree function
        val registerWorktreeOp = (issueId: String, path: String, trackerType: String, team: String) =>
          val actualPath = projectPathResolved / os.up / os.RelPath(path.stripPrefix("../"))
          ServerClient.registerWorktree(issueId, actualPath.toString, trackerType, team)

        // Call WorktreeCreationService with lock protection
        WorktreeCreationService.createWithLock(
          issueId,
          config,
          fetchIssue,
          createWorktreeOp,
          createTmuxOp,
          registerWorktreeOp
        ) match
          case Right(result) =>
            val html = CreationSuccessView.render(result).render
            cask.Response(
              data = html,
              statusCode = 200,
              headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
            )

          case Left(error) =>
            // Log error type only (not details) to avoid information disclosure
            System.err.println(s"Worktree creation error for $issueId: ${error.getClass.getSimpleName}")
            val statusCode = errorToStatusCode(error)
            val userFriendlyError = WorktreeCreationError.toUserFriendly(error, issueId)
            val html = CreationErrorView.render(userFriendlyError).render
            cask.Response(
              data = html,
              statusCode = statusCode,
              headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
            )

  /** Map domain error to appropriate HTTP status code.
    *
    * @param error Domain error from worktree creation
    * @return HTTP status code
    */
  private def errorToStatusCode(error: WorktreeCreationError): Int =
    error match
      case WorktreeCreationError.DirectoryExists(_) => 422 // Unprocessable Entity
      case WorktreeCreationError.AlreadyHasWorktree(_, _) => 409 // Conflict
      case WorktreeCreationError.GitError(_) => 500 // Internal Server Error
      case WorktreeCreationError.TmuxError(_) => 500 // Internal Server Error
      case WorktreeCreationError.IssueNotFound(_) => 404 // Not Found
      case WorktreeCreationError.ApiError(_) => 502 // Bad Gateway
      case WorktreeCreationError.CreationInProgress(_) => 423 // Locked

  /** Render simple error message as HTML fragment.
    *
    * Used for validation errors and other simple error cases outside of
    * worktree creation flow.
    *
    * @param message Error message
    * @return HTML string
    */
  private def renderErrorView(message: String): String =
    import scalatags.Text.all.*
    div(
      cls := "creation-error",
      h3("Error"),
      p(message)
    ).render

  /** Build issue URL based on tracker type.
    *
    * @param issueId Parsed issue ID
    * @param config Project configuration
    * @return Issue URL
    */
  private def buildIssueUrl(issueId: IssueId, config: ProjectConfiguration): String =
    config.trackerType.toString.toLowerCase match
      case "linear" =>
        s"https://linear.app/issue/${issueId.value}"
      case "github" =>
        config.repository match
          case Some(repo) =>
            val number = extractGitHubIssueNumber(issueId.value)
            s"https://github.com/$repo/issues/$number"
          case None =>
            s"https://github.com/issues/${issueId.value}"
      case "youtrack" =>
        val baseUrl = config.youtrackBaseUrl.getOrElse("https://youtrack.example.com")
        s"$baseUrl/issue/${issueId.value}"
      case _ =>
        s"https://example.com/issue/${issueId.value}"

  /** Build fetch function for WorktreeCardService based on tracker type.
    *
    * Takes raw string ID and returns Issue.
    *
    * @param trackerType Tracker type string
    * @param config Optional project configuration
    * @return Function that fetches issue by string ID
    */
  private def buildFetchFunction(trackerType: String, config: Option[ProjectConfiguration]): String => Either[String, iw.core.model.Issue] =
    (issueId: String) =>
      trackerType.toLowerCase match
        case "linear" =>
          ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
            case Some(token) =>
              IssueId.parse(issueId).flatMap(LinearClient.fetchIssue(_, token))
            case None =>
              Left("LINEAR_API_TOKEN environment variable not set")

        case "github" =>
          config.flatMap(_.repository) match
            case Some(repository) =>
              val number = extractGitHubIssueNumber(issueId)
              GitHubClient.fetchIssue(number, repository)
            case None =>
              Left("GitHub repository not configured")

        case "youtrack" =>
          val baseUrl = config.flatMap(_.youtrackBaseUrl).getOrElse("https://youtrack.example.com")
          ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken) match
            case Some(token) =>
              IssueId.parse(issueId).flatMap(YouTrackClient.fetchIssue(_, baseUrl, token))
            case None =>
              Left("YOUTRACK_API_TOKEN environment variable not set")

        case _ =>
          Left(s"Unknown tracker type: $trackerType")

  /** Build URL builder for WorktreeCardService based on tracker type.
    *
    * @param trackerType Tracker type string
    * @param config Optional project configuration
    * @return Function that builds issue URL from string ID
    */
  private def buildUrlBuilder(trackerType: String, config: Option[ProjectConfiguration]): (String, String, Option[String]) => String =
    (issueId: String, _: String, _: Option[String]) =>
      trackerType.toLowerCase match
        case "linear" =>
          s"https://linear.app/issue/$issueId"
        case "github" =>
          config.flatMap(_.repository) match
            case Some(repo) =>
              val number = extractGitHubIssueNumber(issueId)
              s"https://github.com/$repo/issues/$number"
            case None =>
              s"https://github.com/issues/$issueId"
        case "youtrack" =>
          val baseUrl = config.flatMap(_.youtrackBaseUrl).getOrElse("https://youtrack.example.com")
          s"$baseUrl/issue/$issueId"
        case _ =>
          s"https://example.com/issue/$issueId"

  /** Build fetch function for IssueSearchService based on tracker type.
    *
    * @param config Project configuration
    * @return Function that fetches issue by ID
    */
  private def buildFetchFunction(config: ProjectConfiguration): IssueId => Either[String, iw.core.model.Issue] =
    (issueId: IssueId) =>
      config.trackerType.toString.toLowerCase match
        case "linear" =>
          ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
            case Some(token) =>
              LinearClient.fetchIssue(issueId, token)
            case None =>
              Left("LINEAR_API_TOKEN environment variable not set")

        case "github" =>
          config.repository match
            case Some(repository) =>
              // Extract issue number from issueId (e.g., "IW-79" -> "79")
              val number = extractGitHubIssueNumber(issueId.value)
              GitHubClient.fetchIssue(number, repository)
            case None =>
              Left("GitHub repository not configured")

        case "youtrack" =>
          val baseUrl = config.youtrackBaseUrl.getOrElse("https://youtrack.example.com")
          ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken) match
            case Some(token) =>
              YouTrackClient.fetchIssue(issueId, baseUrl, token)
            case None =>
              Left("YOUTRACK_API_TOKEN environment variable not set")

        case _ =>
          Left(s"Unknown tracker type: ${config.trackerType}")

  /** Build fetch recent function for IssueSearchService based on tracker type.
    *
    * @param config Project configuration
    * @return Function that fetches recent issues with given limit
    */
  private def buildFetchRecentFunction(config: ProjectConfiguration): Int => Either[String, List[iw.core.model.Issue]] =
    (limit: Int) =>
      config.trackerType match
        case IssueTrackerType.GitHub =>
          config.repository match
            case Some(repository) =>
              GitHubClient.listRecentIssues(repository, limit)
            case None =>
              Left("GitHub repository not configured")

        case IssueTrackerType.Linear =>
          ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
            case Some(token) =>
              LinearClient.listRecentIssues(config.team, limit, token)
            case None =>
              Left("LINEAR_API_TOKEN environment variable not set")

        case IssueTrackerType.YouTrack =>
          val baseUrl = config.youtrackBaseUrl.getOrElse("https://youtrack.example.com")
          ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken) match
            case Some(token) =>
              YouTrackClient.listRecentIssues(baseUrl, config.team, limit, token)
            case None =>
              Left("YOUTRACK_API_TOKEN environment variable not set")

        case IssueTrackerType.GitLab =>
          Left("Recent issues not yet implemented for GitLab")

  /** Build search function for IssueSearchService based on tracker type.
    *
    * @param config Project configuration
    * @return Function that searches issues by text query
    */
  private def buildSearchFunction(config: ProjectConfiguration): String => Either[String, List[iw.core.model.Issue]] =
    (query: String) =>
      config.trackerType match
        case IssueTrackerType.GitHub =>
          config.repository match
            case Some(repository) =>
              GitHubClient.searchIssues(repository, query)
            case None =>
              Left("GitHub repository not configured")

        case IssueTrackerType.Linear =>
          ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
            case Some(token) =>
              LinearClient.searchIssues(query, 10, token)
            case None =>
              Left("LINEAR_API_TOKEN environment variable not set")

        case IssueTrackerType.YouTrack =>
          (config.youtrackBaseUrl, ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken)) match
            case (Some(baseUrl), Some(token)) =>
              YouTrackClient.searchIssues(baseUrl, query, 10, token)
            case (None, _) =>
              Left("YouTrack base URL not configured")
            case (_, None) =>
              Left("YOUTRACK_API_TOKEN environment variable not set")

        case IssueTrackerType.GitLab =>
          Left("Search not yet implemented for GitLab")

  /** Extract GitHub issue number from issue ID.
    *
    * @param issueId Issue identifier (may have prefix)
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

  initialize()

object CaskServer:
  def start(statePath: String, port: Int = 9876, hosts: Seq[String] = Seq("localhost"), devMode: Boolean = false): Unit =
    val startedAt = Instant.now()
    val server = new CaskServer(statePath, port, hosts, startedAt, devMode)

    // Create builder and add listener for each host
    val builder = hosts.foldLeft(io.undertow.Undertow.builder) { (b, host) =>
      b.addHttpListener(port, host)
    }

    builder
      .setHandler(server.defaultHandler)
      .build
      .start()
