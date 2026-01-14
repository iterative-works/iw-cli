// PURPOSE: Infrastructure layer for HTTP server using Cask framework
// PURPOSE: Provides dashboard HTML route and health check endpoint on port 9876

package iw.core.infrastructure

import iw.core.{ConfigFileRepository, Constants, ProjectConfiguration, IssueId, ApiToken, LinearClient, GitHubClient, YouTrackClient, GitWorktreeAdapter, TmuxAdapter, WorktreePath, IssueTrackerType}
import iw.core.application.{ServerStateService, DashboardService, WorktreeRegistrationService, WorktreeUnregistrationService, ArtifactService, IssueSearchService, WorktreeCreationService}
import iw.core.domain.{ServerState, IssueData, WorktreeCreationError}
import iw.core.presentation.views.{ArtifactView, CreateWorktreeModal, SearchResultsView, CreationSuccessView, CreationErrorView}
import java.time.Instant

class CaskServer(statePath: String, port: Int, hosts: Seq[String], startedAt: Instant) extends cask.MainRoutes:
  private val repository = StateRepository(statePath)

  @cask.get("/")
  def dashboard(): cask.Response[String] =
    val stateResult = ServerStateService.load(repository)
    stateResult match
      case Right(rawState) =>
        // Auto-prune non-existent worktrees
        val state = WorktreeUnregistrationService.pruneNonExistent(
          rawState,
          path => os.exists(os.Path(path, os.pwd))
        )

        // Save pruned state if changes were made
        if state != rawState then
          repository.write(state) // Best-effort save, ignore errors

        val worktrees = ServerStateService.listWorktrees(state)

        // Load project configuration
        val configPath = os.pwd / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
        val config = ConfigFileRepository.read(configPath)

        // Render dashboard with issue data, progress, PR data, and review state
        val (html, updatedReviewStateCache) = DashboardService.renderDashboard(
          worktrees,
          state.issueCache,
          state.progressCache,
          state.prCache,
          state.reviewStateCache,
          config
        )

        // Update server state with new review state cache and persist
        val updatedState = state.copy(reviewStateCache = updatedReviewStateCache)
        repository.write(updatedState) // Best-effort save, ignore errors

        cask.Response(
          data = html,
          headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
        )
      case Left(error) =>
        System.err.println(s"Error loading state: $error")
        cask.Response(
          data = "Internal server error",
          statusCode = 500
        )

  @cask.get("/worktrees/:issueId/artifacts")
  def artifactPage(issueId: String, path: String): cask.Response[String] =
    // path comes from query param via cask binding
    val artifactPath = path

    // Load current server state
    val stateResult = ServerStateService.load(repository)

    stateResult match
      case Left(error) =>
        System.err.println(s"Failed to load state: $error")
        cask.Response(
          data = "Internal server error",
          statusCode = 500
        )

      case Right(state) =>
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

  @cask.get("/health")
  def health(): ujson.Value =
    ujson.Obj("status" -> "ok")

  @cask.get("/api/status")
  def status(): ujson.Value =
    val stateResult = ServerStateService.load(repository)
    val worktreeCount = stateResult match
      case Right(state) => state.worktrees.size
      case Left(_) => 0

    ujson.Obj(
      "status" -> "running",
      "port" -> port,
      "hosts" -> ujson.Arr.from(hosts),
      "worktreeCount" -> worktreeCount,
      "startedAt" -> startedAt.toString
    )

  @cask.put("/api/v1/worktrees/:issueId")
  def registerWorktree(issueId: String, request: cask.Request): cask.Response[ujson.Value] =
    val requestJson = try
      // Parse request body
      val bodyBytes = request.readAllBytes()
      val bodyStr = new String(bodyBytes, "UTF-8")
      ujson.read(bodyStr)
    catch
      case e: Throwable if e.getClass.getName.contains("ParseException") ||
                           e.getClass.getName.contains("Abort") ||
                           e.getClass.getName.contains("TraceException") =>
        return cask.Response(
          data = ujson.Obj(
            "code" -> "MALFORMED_JSON",
            "message" -> s"Malformed JSON: ${e.getMessage}"
          ),
          statusCode = 400
        )
      case e: Exception =>
        System.err.println(s"Error reading request body: ${e.getClass.getName}: ${e.getMessage}")
        return cask.Response(
          data = ujson.Obj(
            "code" -> "INTERNAL_ERROR",
            "message" -> "Internal server error"
          ),
          statusCode = 500
        )

    try
      // Extract fields from request
      val path = requestJson("path").str
      val trackerType = requestJson("trackerType").str
      val team = requestJson("team").str

      // Load current state
      val stateResult = ServerStateService.load(repository)

      stateResult match
        case Right(currentState) =>
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
              // Persist new state
              val saveResult = ServerStateService.save(newState, repository)

              saveResult match
                case Right(_) =>
                  val registration = newState.worktrees(issueId)
                  cask.Response(
                    data = ujson.Obj(
                      "status" -> "registered",
                      "issueId" -> issueId,
                      "lastSeenAt" -> registration.lastSeenAt.toString
                    ),
                    statusCode = if wasCreated then 201 else 200
                  )
                case Left(error) =>
                  System.err.println(s"Failed to save state: $error")
                  cask.Response(
                    data = ujson.Obj(
                      "code" -> "INTERNAL_ERROR",
                      "message" -> "Internal server error"
                    ),
                    statusCode = 500
                  )

            case Left(error) =>
              cask.Response(
                data = ujson.Obj(
                  "code" -> "VALIDATION_ERROR",
                  "message" -> error
                ),
                statusCode = 400
              )

        case Left(error) =>
          System.err.println(s"Failed to load state: $error")
          cask.Response(
            data = ujson.Obj(
              "code" -> "INTERNAL_ERROR",
              "message" -> "Internal server error"
            ),
            statusCode = 500
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
    repository.read() match
      case Right(state) =>
        WorktreeUnregistrationService.unregister(state, issueId) match
          case Right(newState) =>
            repository.write(newState) match
              case Right(_) =>
                cask.Response(
                  ujson.Obj("status" -> "ok", "issueId" -> issueId),
                  statusCode = 200
                )
              case Left(err) =>
                cask.Response(
                  ujson.Obj("code" -> "SAVE_ERROR", "message" -> err),
                  statusCode = 500
                )
          case Left(err) =>
            cask.Response(
              ujson.Obj("code" -> "NOT_FOUND", "message" -> err),
              statusCode = 404
            )
      case Left(err) =>
        cask.Response(
          ujson.Obj("code" -> "LOAD_ERROR", "message" -> err),
          statusCode = 500
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

        // Call search service
        IssueSearchService.search(q, config, fetchIssue) match
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

  /** Build fetch function for IssueSearchService based on tracker type.
    *
    * @param config Project configuration
    * @return Function that fetches issue by ID
    */
  private def buildFetchFunction(config: ProjectConfiguration): IssueId => Either[String, iw.core.Issue] =
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
  private def buildFetchRecentFunction(config: ProjectConfiguration): Int => Either[String, List[iw.core.Issue]] =
    (limit: Int) =>
      config.trackerType match
        case IssueTrackerType.GitHub =>
          config.repository match
            case Some(repository) =>
              GitHubClient.listRecentIssues(repository, limit)
            case None =>
              Left("GitHub repository not configured")

        case IssueTrackerType.Linear =>
          // Linear support will be added in Phase 3
          Left("Recent issues not yet supported for Linear")

        case IssueTrackerType.YouTrack =>
          // YouTrack support will be added in Phase 5
          Left("Recent issues not yet supported for YouTrack")

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
  def start(statePath: String, port: Int = 9876, hosts: Seq[String] = Seq("localhost")): Unit =
    val startedAt = Instant.now()
    val server = new CaskServer(statePath, port, hosts, startedAt)

    // Create builder and add listener for each host
    val builder = hosts.foldLeft(io.undertow.Undertow.builder) { (b, host) =>
      b.addHttpListener(port, host)
    }

    builder
      .setHandler(server.defaultHandler)
      .build
      .start()
