// PURPOSE: Infrastructure layer for HTTP server using Cask framework
// PURPOSE: Provides dashboard HTML route and health check endpoint on port 9876

package iw.core.infrastructure

import iw.core.application.{ServerStateService, DashboardService, WorktreeRegistrationService}
import iw.core.domain.ServerState
import java.time.Instant

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
        System.err.println(s"Error loading state: $error")
        cask.Response(
          data = "Internal server error",
          statusCode = 500
        )

  @cask.get("/health")
  def health(): ujson.Value =
    ujson.Obj("status" -> "ok")

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

  initialize()

object CaskServer:
  def start(statePath: String, port: Int = 9876): Unit =
    val server = new CaskServer(statePath)
    io.undertow.Undertow.builder
      .addHttpListener(port, "localhost")
      .setHandler(server.defaultHandler)
      .build
      .start()
