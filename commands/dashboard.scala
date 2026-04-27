#!/usr/bin/env -S scala-cli shebang
// PURPOSE: Command to start the iw dashboard server and open it in a browser
// PURPOSE: Handles health checks, server startup, and platform-specific browser opening

import iw.core.adapters.ServerConfigRepository
import iw.core.output.Output
import iw.core.model.ServerConfig
import scala.util.{Try, Success, Failure}
import sttp.client4.quick.*
import java.nio.file.Paths
import java.net.ServerSocket

case class DashboardArgs(
    statePath: Option[String] = None,
    sampleData: Boolean = false,
    devMode: Boolean = false
)

@annotation.tailrec
def parseArgs(
    remaining: List[String],
    acc: DashboardArgs = DashboardArgs()
): DashboardArgs =
  remaining match
    case Nil                             => acc
    case "--state-path" :: value :: rest =>
      parseArgs(rest, acc.copy(statePath = Some(value)))
    case "--sample-data" :: rest =>
      parseArgs(rest, acc.copy(sampleData = true))
    case "--dev" :: rest =>
      parseArgs(rest, acc.copy(devMode = true))
    case ("--help" | "-h") :: _ =>
      printHelp()
      sys.exit(0)
    case other :: _ =>
      Output.error(s"Unknown argument: $other")
      Output.info(
        "Usage: ./iw dashboard [--state-path <path>] [--sample-data] [--dev] [--help]"
      )
      sys.exit(1)

@main def dashboard(args: String*): Unit =
  // Parse command line arguments
  val parsedArgs = parseArgs(args.toList)
  val statePath = parsedArgs.statePath
  val sampleData = parsedArgs.sampleData
  val devMode = parsedArgs.devMode

  // IW_DASHBOARD_JAR is set by iw-run's ensure_dashboard_jar via Mill query.
  val dashboardJar = sys.env.getOrElse(
    "IW_DASHBOARD_JAR", {
      Output.error(
        "IW_DASHBOARD_JAR not set — invoke via ./iw dashboard, not directly"
      )
      sys.exit(1)
    }
  )

  val homeDir = sys.env.get("HOME") match
    case Some(home) => home
    case None       =>
      Output.error("HOME environment variable not set")
      sys.exit(1)

  val serverDir = s"$homeDir/.local/share/iw/server"
  val defaultStatePath = s"$serverDir/state.json"
  val defaultConfigPath = s"$serverDir/config.json"

  // Handle dev mode: create timestamped temp directory and auto-enable sample data
  val (
    effectiveStatePath,
    effectiveConfigPath,
    effectiveDevMode,
    effectiveSampleData
  ) = if devMode then
    val timestamp = System.currentTimeMillis()
    val tempDir = s"/tmp/iw-dev-$timestamp"
    val tempStatePath = s"$tempDir/state.json"
    val tempConfigPath = s"$tempDir/config.json"

    // Create temp directory
    os.makeDir.all(os.Path(tempDir))

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
    (finalStatePath, tempConfigPath, true, true)
  else
    // Normal mode: use custom state path if provided, otherwise use default path
    val finalStatePath = statePath.getOrElse(defaultStatePath)
    (finalStatePath, defaultConfigPath, false, sampleData)

  // If sample data flag is set, generate and persist sample data via SampleDataCli
  if effectiveSampleData then
    Output.info("Generating sample data...")
    val sampleCmd = Seq(
      "java",
      "-cp",
      dashboardJar,
      "iw.dashboard.SampleDataCli",
      effectiveStatePath
    )
    val samplePb = new ProcessBuilder(sampleCmd*)
    samplePb.inheritIO()
    val sampleProcess = samplePb.start()
    val exitCode = sampleProcess.waitFor()
    if exitCode != 0 then
      Output.error(s"Sample data generation failed (exit $exitCode)")
      sys.exit(1)

  // Read or create default config
  val config =
    ServerConfigRepository.getOrCreateDefault(effectiveConfigPath) match
      case Right(c)  => c
      case Left(err) =>
        Output.error(s"Failed to read config: $err")
        sys.exit(1)

  val port = config.port
  val url = s"http://localhost:$port"

  // Check if server is already running
  if !isServerRunning(s"$url/health") then
    Output.info("Starting dashboard server...")
    if statePath.isDefined || effectiveSampleData then
      Output.info(s"Using state file: $effectiveStatePath")
    startServerAndOpenBrowser(
      effectiveStatePath,
      port,
      url,
      config.hosts,
      dashboardJar,
      effectiveDevMode
    )
  else
    Output.info(s"Server already running at $url")
    openBrowser(url)

def isServerRunning(healthUrl: String): Boolean =
  Try {
    val response = quickRequest.get(uri"$healthUrl").send()
    response.code.code == 200
  }.getOrElse(false)

def startServerAndOpenBrowser(
    statePath: String,
    port: Int,
    url: String,
    hosts: Seq[String],
    dashboardJar: String,
    devMode: Boolean = false
): Unit =
  val cmd = Seq(
    "java",
    "-jar",
    dashboardJar,
    statePath,
    port.toString,
    hosts.mkString(",")
  ) ++ (if devMode then Seq("--dev") else Seq.empty)

  val pb = new ProcessBuilder(cmd*)
  // Foreground mode: child stdout/stderr inherit parent TTY.
  // VITE_DEV_URL inherits from the parent process environment.
  pb.inheritIO()
  val process = pb.start()

  // Wait for server to be ready, open browser, then block until child exits
  if waitForServer(s"$url/health", timeoutSeconds = 30) then
    Output.success(s"Server started successfully at $url")
    openBrowser(url)
    Output.info("Press Ctrl+C to stop the server")
    process.waitFor()
  else
    Output.error("Server failed to start within 30 seconds")
    process.destroy()
    sys.exit(1)

def waitForServer(healthUrl: String, timeoutSeconds: Int): Boolean =
  val start = System.currentTimeMillis()
  val timeoutMillis = timeoutSeconds * 1000

  @annotation.tailrec
  def poll(): Boolean =
    if System.currentTimeMillis() - start >= timeoutMillis then false
    else if isServerRunning(healthUrl) then true
    else
      Thread.sleep(200)
      poll()

  poll()

def openBrowser(url: String): Unit =
  val os = System.getProperty("os.name").toLowerCase

  val command =
    if os.contains("mac") then Some(Seq("open", url))
    else if os.contains("nix") || os.contains("nux") || os.contains("aix") then
      Some(Seq("xdg-open", url))
    else if os.contains("win") then Some(Seq("cmd", "/c", "start", url))
    else None

  command match
    case None =>
      Output.warning(s"Unable to detect platform. Please open $url manually")
    case Some(cmd) =>
      Try {
        val pb = new ProcessBuilder(cmd*)
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
    |  - Pass VITE_DEV_URL=http://localhost:5173 to serve assets from Vite dev server
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
    |  VITE_DEV_URL=http://localhost:5173 ./iw dashboard --dev  # Dev mode with Vite HMR
    |""".stripMargin)

def findAvailablePort(): Int =
  val socket = new ServerSocket(0)
  val port = socket.getLocalPort
  socket.close()
  port
