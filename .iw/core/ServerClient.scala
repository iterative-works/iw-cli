// PURPOSE: HTTP client for CLI commands to communicate with CaskServer
// PURPOSE: Provides best-effort registration with lazy server start capability

package iw.core.infrastructure

import iw.core.{ServerConfig, ServerConfigRepository, ProcessManager}
import sttp.client4.quick.*
import sttp.model.StatusCode
import scala.util.{Try, Success, Failure}

object ServerClient:

  private val homeDir = System.getProperty("user.home")
  private val serverDir = s"$homeDir/.local/share/iw/server"
  private val configPath = s"$serverDir/config.json"
  private val pidPath = s"$serverDir/server.pid"

  /** Check if server communication is disabled via environment variable.
    * Useful for testing to prevent tests from registering worktrees in production dashboard.
    */
  private def isServerDisabled: Boolean =
    Option(System.getenv("IW_SERVER_DISABLED")).exists(v => v == "1" || v.toLowerCase == "true")

  private def getServerConfig(): ServerConfig =
    ServerConfigRepository.getOrCreateDefault(configPath) match
      case Right(config) => config
      case Left(_) => ServerConfig.default

  private def getServerPort(): Int =
    getServerConfig().port

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
   * Uses ProcessManager to spawn a persistent background process.
   *
   * @param statePath Path to state.json file for server
   * @return Right(()) if server is running, Left(error) if start fails
   */
  private def ensureServerRunning(statePath: String): Either[String, Unit] =
    val port = getServerPort()
    if isHealthy() then
      Right(())
    else
      // Check if there's already a PID file with a running process
      ProcessManager.readPidFile(pidPath) match
        case Right(Some(pid)) if ProcessManager.isProcessAlive(pid) =>
          // Process exists but not healthy yet, wait for it
          if waitForServer(port, timeoutSeconds = 5) then
            Right(())
          else
            Left("Server process exists but is not responding")
        case _ =>
          // No running process, spawn a new one
          val config = getServerConfig()
          ProcessManager.spawnServerProcess(statePath, config.port, config.hosts) match
            case Left(error) => Left(s"Failed to spawn server: $error")
            case Right(pid) =>
              // Write PID file
              ProcessManager.writePidFile(pid, pidPath) match
                case Left(error) =>
                  // Server started but PID file failed - still try to use it
                  ()
                case Right(_) => ()

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
    if isServerDisabled then return Right(())

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

  /**
   * Unregisters a worktree from the server.
   *
   * @param issueId The issue identifier
   * @return Right(()) on success, Left(error message) on failure
   */
  def unregisterWorktree(issueId: String): Either[String, Unit] =
    if isServerDisabled then return Right(())

    val port = getServerPort()
    try
      val response = quickRequest
        .delete(uri"http://localhost:$port/api/v1/worktrees/$issueId")
        .send()

      response.code match
        case StatusCode.Ok => Right(())
        case StatusCode.NotFound => Right(()) // Already removed - treat as success
        case _ =>
          val errorMsg = Try(ujson.read(response.body)("message").str).getOrElse(response.body)
          Left(s"Server returned ${response.code.code}: $errorMsg")

    catch
      case e: Exception => Left(s"Failed to unregister worktree: ${e.getMessage}")
