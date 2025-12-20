// PURPOSE: Infrastructure layer for HTTP server using Cask framework
// PURPOSE: Provides dashboard HTML route and health check endpoint on port 9876

package iw.core.infrastructure

import iw.core.application.{ServerStateService, DashboardService}
import iw.core.domain.ServerState

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

  initialize()

object CaskServer:
  def start(statePath: String, port: Int = 9876): Unit =
    val server = new CaskServer(statePath)
    io.undertow.Undertow.builder
      .addHttpListener(port, "localhost")
      .setHandler(server.defaultHandler)
      .build
      .start()
