#!/usr/bin/env -S scala-cli shebang
// PURPOSE: Command to start the iw dashboard server and open it in a browser
// PURPOSE: Handles health checks, server startup, and platform-specific browser opening

import iw.core.infrastructure.{CaskServer, StateRepository}
import iw.core.domain.SampleDataGenerator
import iw.core.{Output, ServerConfig, ServerConfigRepository}
import scala.util.{Try, Success, Failure}
import sttp.client4.quick.*
import java.nio.file.Paths

@main def dashboard(args: String*): Unit =
  // Parse command line arguments
  var statePath: Option[String] = None
  var sampleData: Boolean = false
  var projectPath: Option[String] = None

  var i = 0
  while i < args.length do
    args(i) match
      case "--state-path" if i + 1 < args.length =>
        statePath = Some(args(i + 1))
        i += 2
      case "--sample-data" =>
        sampleData = true
        i += 1
      case "--project" if i + 1 < args.length =>
        projectPath = Some(args(i + 1))
        i += 2
      case other =>
        Output.error(s"Unknown argument: $other")
        Output.info("Usage: ./iw dashboard [--state-path <path>] [--sample-data] [--project <path>]")
        sys.exit(1)

  val homeDir = sys.env.get("HOME") match
    case Some(home) => home
    case None =>
      Output.error("HOME environment variable not set")
      sys.exit(1)

  val serverDir = s"$homeDir/.local/share/iw/server"
  val defaultStatePath = s"$serverDir/state.json"
  val configPath = s"$serverDir/config.json"

  // Use custom state path if provided, otherwise use default path
  val effectiveStatePath = statePath.getOrElse(defaultStatePath)

  // If sample data flag is set, generate and persist sample data
  if sampleData then
    Output.info("Generating sample data...")
    val sampleState = SampleDataGenerator.generateSampleState()
    Output.info(s"Generated state with ${sampleState.worktrees.size} worktrees")
    val repository = StateRepository(effectiveStatePath)
    repository.write(sampleState) match
      case Right(_) =>
        Output.success(s"Sample data written to $effectiveStatePath")
        // Verify the write by reading back
        repository.read() match
          case Right(readBack) =>
            Output.success(s"Verified: Read back ${readBack.worktrees.size} worktrees from file")
          case Left(err) =>
            Output.warning(s"Failed to verify write: $err")
      case Left(err) =>
        Output.error(s"Failed to write sample data: $err")
        sys.exit(1)

  // Read or create default config
  val config = ServerConfigRepository.getOrCreateDefault(configPath) match
    case Right(c) => c
    case Left(err) =>
      Output.error(s"Failed to read config: $err")
      sys.exit(1)

  val port = config.port
  val url = s"http://localhost:$port"

  // Check if server is already running
  if !isServerRunning(s"$url/health") then
    Output.info("Starting dashboard server...")
    if statePath.isDefined || sampleData then
      Output.info(s"Using state file: $effectiveStatePath")
    if projectPath.isDefined then
      Output.info(s"Using project directory: ${projectPath.get}")
    // Start server in current process (foreground for Phase 1)
    startServerAndOpenBrowser(effectiveStatePath, port, url, projectPath)
  else
    Output.info(s"Server already running at $url")
    openBrowser(url)

def isServerRunning(healthUrl: String): Boolean =
  Try {
    val response = quickRequest.get(uri"$healthUrl").send()
    response.code.code == 200
  }.getOrElse(false)

def startServerAndOpenBrowser(statePath: String, port: Int, url: String, projectPath: Option[String]): Unit =
  // Start server in a separate thread
  val serverThread = new Thread(() => {
    CaskServer.start(statePath, port, projectPath = projectPath.map(p => os.Path(p)))
  })
  serverThread.setDaemon(false)
  serverThread.start()

  // Wait for server to be ready
  if waitForServer(s"$url/health", timeoutSeconds = 5) then
    Output.success(s"Server started successfully at $url")
    openBrowser(url)
    Output.info("Press Ctrl+C to stop the server")
    // Keep main thread alive
    serverThread.join()
  else
    Output.error("Server failed to start within 5 seconds")
    sys.exit(1)

def waitForServer(healthUrl: String, timeoutSeconds: Int): Boolean =
  val start = System.currentTimeMillis()
  val timeoutMillis = timeoutSeconds * 1000

  while System.currentTimeMillis() - start < timeoutMillis do
    if isServerRunning(healthUrl) then
      return true
    Thread.sleep(200)

  false

def openBrowser(url: String): Unit =
  val os = System.getProperty("os.name").toLowerCase

  val command = if os.contains("mac") then
    Seq("open", url)
  else if os.contains("nix") || os.contains("nux") || os.contains("aix") then
    Seq("xdg-open", url)
  else if os.contains("win") then
    Seq("cmd", "/c", "start", url)
  else
    Output.warning(s"Unable to detect platform. Please open $url manually")
    return

  Try {
    val pb = new ProcessBuilder(command: _*)
    pb.start()
  } match
    case Success(_) =>
      Output.info(s"Opening browser to $url")
    case Failure(ex) =>
      Output.warning(s"Failed to open browser: ${ex.getMessage}")
      Output.info(s"Please open $url manually")
