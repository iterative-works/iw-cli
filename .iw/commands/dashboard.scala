#!/usr/bin/env -S scala-cli shebang
// PURPOSE: Command to start the iw dashboard server and open it in a browser
// PURPOSE: Handles health checks, server startup, and platform-specific browser opening

import iw.core.dashboard.{CaskServer, StateRepository}
import iw.core.dashboard.SampleDataGenerator
import iw.core.output.Output
import iw.core.model.{ServerConfig, ServerConfigRepository}
import scala.util.{Try, Success, Failure}
import sttp.client4.quick.*
import java.nio.file.Paths
import java.net.ServerSocket

@main def dashboard(args: String*): Unit =
  // Parse command line arguments
  var statePath: Option[String] = None
  var sampleData: Boolean = false
  var devMode: Boolean = false

  var i = 0
  while i < args.length do
    args(i) match
      case "--state-path" if i + 1 < args.length =>
        statePath = Some(args(i + 1))
        i += 2
      case "--sample-data" =>
        sampleData = true
        i += 1
      case "--dev" =>
        devMode = true
        i += 1
      case "--help" | "-h" =>
        printHelp()
        sys.exit(0)
      case other =>
        Output.error(s"Unknown argument: $other")
        Output.info("Usage: ./iw dashboard [--state-path <path>] [--sample-data] [--dev] [--help]")
        sys.exit(1)

  val homeDir = sys.env.get("HOME") match
    case Some(home) => home
    case None =>
      Output.error("HOME environment variable not set")
      sys.exit(1)

  val serverDir = s"$homeDir/.local/share/iw/server"
  val defaultStatePath = s"$serverDir/state.json"
  val defaultConfigPath = s"$serverDir/config.json"

  // Handle dev mode: create timestamped temp directory and auto-enable sample data
  val (effectiveStatePath, effectiveConfigPath, effectiveDevMode) = if devMode then
    val timestamp = System.currentTimeMillis()
    val tempDir = s"/tmp/iw-dev-$timestamp"
    val tempStatePath = s"$tempDir/state.json"
    val tempConfigPath = s"$tempDir/config.json"

    // Create temp directory
    os.makeDir.all(os.Path(tempDir))

    // Auto-enable sample data in dev mode
    sampleData = true

    // Find available port dynamically (enables parallel test runs)
    val devPort = findAvailablePort()
    val defaultConfig = ServerConfig(port = devPort, hosts = List("localhost"))
    ServerConfigRepository.write(defaultConfig, tempConfigPath) match
      case Right(_) =>
        Output.info(s"Created dev mode config at $tempConfigPath")
      case Left(err) =>
        Output.error(s"Failed to create dev config: $err")
        sys.exit(1)

    Output.info(s"Dev mode enabled:")
    Output.info(s"  - Temp directory: $tempDir")
    Output.info(s"  - State file: $tempStatePath")
    Output.info(s"  - Config file: $tempConfigPath")
    Output.info(s"  - Port: $devPort")
    Output.info(s"  - Sample data: enabled")

    // Explicit state-path takes precedence
    val finalStatePath = statePath.getOrElse(tempStatePath)
    (finalStatePath, tempConfigPath, true)
  else
    // Normal mode: use custom state path if provided, otherwise use default path
    val finalStatePath = statePath.getOrElse(defaultStatePath)
    (finalStatePath, defaultConfigPath, false)

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
  val config = ServerConfigRepository.getOrCreateDefault(effectiveConfigPath) match
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
    // Start server in current process (foreground for Phase 1)
    startServerAndOpenBrowser(effectiveStatePath, port, url, effectiveDevMode)
  else
    Output.info(s"Server already running at $url")
    openBrowser(url)

def isServerRunning(healthUrl: String): Boolean =
  Try {
    val response = quickRequest.get(uri"$healthUrl").send()
    response.code.code == 200
  }.getOrElse(false)

def startServerAndOpenBrowser(statePath: String, port: Int, url: String, devMode: Boolean = false): Unit =
  // Start server in a separate thread
  val serverThread = new Thread(() => {
    CaskServer.start(statePath, port, devMode = devMode)
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

def printHelp(): Unit =
  println("""Usage: ./iw dashboard [OPTIONS]
    |
    |Start the iw dashboard server and open it in a browser.
    |
    |Options:
    |  --state-path <path>  Use a custom state file location
    |  --sample-data        Load sample data for demonstration
    |  --dev                Development mode with complete isolation
    |  --help, -h           Show this help message
    |
    |Development Mode (--dev):
    |  Creates a completely isolated environment for safe experimentation:
    |  - Uses temporary directory: /tmp/iw-dev-<timestamp>/
    |  - State file: <temp-dir>/state.json
    |  - Config file: <temp-dir>/config.json
    |  - Automatically enables sample data
    |  - Uses dynamically assigned port (avoids conflicts)
    |  - Production files are NEVER modified or accessed
    |
    |Isolation Guarantees:
    |  When using --dev mode, your production data remains untouched:
    |  - Production state file (~/.local/share/iw/server/state.json) is never read or written
    |  - Production config file (~/.local/share/iw/server/config.json) is never modified
    |  - All operations happen in isolated temporary directory
    |  - Safe to experiment without affecting real worktree registrations
    |
    |Examples:
    |  ./iw dashboard                    # Start with default production data
    |  ./iw dashboard --dev              # Start in isolated dev mode with sample data
    |  ./iw dashboard --sample-data      # Start with sample data in production location
    |  ./iw dashboard --state-path /tmp/test.json  # Use custom state file
    |""".stripMargin)

def findAvailablePort(): Int =
  val socket = new ServerSocket(0)
  val port = socket.getLocalPort
  socket.close()
  port
