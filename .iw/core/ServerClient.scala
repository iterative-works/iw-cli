// PURPOSE: HTTP client for CLI commands to communicate with CaskServer
// PURPOSE: Provides best-effort registration with lazy server start capability

package iw.core.infrastructure

import iw.core.{ServerConfig, ServerConfigRepository}
import sttp.client4.quick.*
import sttp.model.StatusCode
import scala.util.{Try, Success, Failure}

object ServerClient:

  private def getServerPort(): Int =
    val homeDir = System.getProperty("user.home")
    val configPath = s"$homeDir/.local/share/iw/server/config.json"
    ServerConfigRepository.getOrCreateDefault(configPath) match
      case Right(config) => config.port
      case Left(_) => ServerConfig.DefaultPort

  /**
   * Checks if the server is healthy.
   *
   * @param port The port to check (uses config file if not specified)
   * @return true if server responds with 200 OK to /health
   */
  def isHealthy(port: Int = -1): Boolean =
    val actualPort = if port == -1 then getServerPort() else port
    try
      val response = quickRequest
        .get(uri"http://localhost:$actualPort/health")
        .send()
      response.code == StatusCode.Ok
    catch
      case _: Exception => false

  /**
   * Ensures the server is running, starting it if necessary.
   *
   * @param statePath Path to state.json file for server
   * @return Right(()) if server is running, Left(error) if start fails
   */
  private def ensureServerRunning(statePath: String): Either[String, Unit] =
    val port = getServerPort()
    if isHealthy() then
      Right(())
    else
      // Start server in daemon thread
      val serverThread = new Thread(() => {
        CaskServer.start(statePath, port)
      })
      serverThread.setDaemon(true)
      serverThread.start()

      // Wait for server to be ready with health check polling
      if waitForServer(port, timeoutSeconds = 5) then
        Right(())
      else
        Left("Server failed to start within timeout")

  /**
   * Waits for server to become healthy with polling.
   *
   * @param port Port to check
   * @param timeoutSeconds Maximum seconds to wait
   * @return true if server became healthy, false if timeout
   */
  private def waitForServer(port: Int, timeoutSeconds: Int): Boolean =
    val endTime = System.currentTimeMillis() + (timeoutSeconds * 1000)
    while System.currentTimeMillis() < endTime do
      if isHealthy(port) then
        return true
      Thread.sleep(200)
    false

  /**
   * Registers or updates a worktree with the server.
   *
   * @param issueId The issue identifier (e.g., "IWLE-123")
   * @param path The filesystem path to the worktree
   * @param trackerType The tracker system (e.g., "Linear", "YouTrack")
   * @param team The team identifier
   * @param statePath Path to state.json for lazy start (default: ~/.local/share/iw/server/state.json)
   * @return Right(()) on success, Left(error message) on failure
   */
  def registerWorktree(
    issueId: String,
    path: String,
    trackerType: String,
    team: String,
    statePath: String = s"${System.getProperty("user.home")}/.local/share/iw/server/state.json"
  ): Either[String, Unit] =
    val port = getServerPort()
    // Ensure server is running
    ensureServerRunning(statePath) match
      case Left(error) => return Left(s"Failed to start server: $error")
      case Right(_) => ()

    // Send registration request
    try
      val requestBody = ujson.Obj(
        "path" -> path,
        "trackerType" -> trackerType,
        "team" -> team
      )

      val response = quickRequest
        .put(uri"http://localhost:$port/api/v1/worktrees/$issueId")
        .body(ujson.write(requestBody))
        .header("Content-Type", "application/json")
        .send()

      response.code match
        case StatusCode.Ok | StatusCode.Created => Right(())
        case _ =>
          val errorMsg = Try(ujson.read(response.body)("message").str).getOrElse(response.body)
          Left(s"Server returned ${response.code.code}: $errorMsg")

    catch
      case e: Exception => Left(s"Failed to register worktree: ${e.getMessage}")

  /**
   * Updates the lastSeenAt timestamp for an existing worktree.
   *
   * This reuses registerWorktree since the PUT endpoint handles updates.
   * The endpoint will update lastSeenAt and preserve registeredAt.
   *
   * @param issueId The issue identifier
   * @param path The filesystem path to the worktree
   * @param trackerType The tracker system
   * @param team The team identifier
   * @param statePath Path to state.json for lazy start
   * @return Right(()) on success, Left(error message) on failure
   */
  def updateLastSeen(
    issueId: String,
    path: String,
    trackerType: String,
    team: String,
    statePath: String = s"${System.getProperty("user.home")}/.local/share/iw/server/state.json"
  ): Either[String, Unit] =
    // Reuse registerWorktree - the PUT endpoint handles updates
    registerWorktree(issueId, path, trackerType, team, statePath)
