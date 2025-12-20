// PURPOSE: Infrastructure layer for HTTP server using Cask framework
// PURPOSE: Provides dashboard HTML route and health check endpoint on port 9876

package iw.core.infrastructure

import iw.core.application.{ServerStateService, DashboardService}
import iw.core.domain.ServerState
import iw.core.service.WorktreeRegistrationService

class CaskServer(statePath: String) extends cask.MainRoutes:
  private val repository = StateRepository(statePath)

  @cask.get("/")
  def dashboard(): cask.Response[String] =
    val stateResult = ServerStateService.load(repository)
    stateResult match
      case Right(state) =>
        val worktrees = ServerStateService.listWorktrees(state)
        val html = DashboardService.renderDashboard(worktrees)
        cask.Response(
          data = html,
          headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
        )
      case Left(error) =>
        cask.Response(
          data = s"Error loading state: $error",
          statusCode = 500
        )

  @cask.get("/health")
  def health(): ujson.Value =
    ujson.Obj("status" -> "ok")

  @cask.put("/api/worktrees/:issueId")
  def registerWorktree(issueId: String, request: cask.Request): cask.Response[ujson.Value] =
    val requestJson = try
      // Parse request body
      val bodyBytes = request.readAllBytes()
      val bodyStr = new String(bodyBytes, "UTF-8")
      ujson.read(bodyStr)
    catch
      case e: ujson.ParseException =>
        return cask.Response(
          data = ujson.Obj("error" -> s"Malformed JSON: ${e.getMessage}"),
          statusCode = 400
        )
      case e: Exception =>
        return cask.Response(
          data = ujson.Obj("error" -> s"Error reading request body: ${e.getMessage}"),
          statusCode = 400
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
          // Register or update worktree
          val registrationResult = WorktreeRegistrationService.register(
            issueId,
            path,
            trackerType,
            team,
            currentState
          )

          registrationResult match
            case Right(newState) =>
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
                    statusCode = 200
                  )
                case Left(error) =>
                  cask.Response(
                    data = ujson.Obj("error" -> s"Failed to save state: $error"),
                    statusCode = 500
                  )

            case Left(error) =>
              cask.Response(
                data = ujson.Obj("error" -> error),
                statusCode = 400
              )

        case Left(error) =>
          cask.Response(
            data = ujson.Obj("error" -> s"Failed to load state: $error"),
            statusCode = 500
          )

    catch
      case e: ujson.ParseException =>
        cask.Response(
          data = ujson.Obj("error" -> s"Malformed JSON: ${e.getMessage}"),
          statusCode = 400
        )
      case e: NoSuchElementException =>
        cask.Response(
          data = ujson.Obj("error" -> s"Missing required field: ${e.getMessage}"),
          statusCode = 400
        )
      case e: Exception =>
        cask.Response(
          data = ujson.Obj("error" -> s"Internal error: ${e.getMessage}"),
          statusCode = 500
        )

  initialize()

object CaskServer:
  def start(statePath: String, port: Int = 9876): Unit =
    val server = new CaskServer(statePath)
    io.undertow.Undertow.builder
      .addHttpListener(port, "localhost")
      .setHandler(server.defaultHandler)
      .build
      .start()
