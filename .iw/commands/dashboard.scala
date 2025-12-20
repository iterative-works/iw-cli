#!/usr/bin/env -S scala-cli shebang
// PURPOSE: Command to start the iw dashboard server and open it in a browser
// PURPOSE: Handles health checks, server startup, and platform-specific browser opening

//> using file "../core/project.scala"
//> using file "../core"

import iw.core.infrastructure.CaskServer
import iw.core.{ServerConfig, ServerConfigRepository}
import scala.util.{Try, Success, Failure}
import sttp.client4.quick.*
import java.nio.file.Paths

@main def dashboard(): Unit =
  val homeDir = sys.env.get("HOME") match
    case Some(home) => home
    case None =>
      System.err.println("ERROR: HOME environment variable not set")
      sys.exit(1)

  val serverDir = s"$homeDir/.local/share/iw/server"
  val statePath = s"$serverDir/state.json"
  val configPath = s"$serverDir/config.json"

  // Read or create default config
  val config = ServerConfigRepository.getOrCreateDefault(configPath) match
    case Right(c) => c
    case Left(err) =>
      System.err.println(s"ERROR: Failed to read config: $err")
      sys.exit(1)

  val port = config.port
  val url = s"http://localhost:$port"

  // Check if server is already running
  if !isServerRunning(s"$url/health") then
    println("Starting dashboard server...")
    // Start server in current process (foreground for Phase 1)
    startServerAndOpenBrowser(statePath, port, url)
  else
    println(s"Server already running at $url")
    openBrowser(url)

def isServerRunning(healthUrl: String): Boolean =
  Try {
    val response = quickRequest.get(uri"$healthUrl").send()
    response.code.code == 200
  }.getOrElse(false)

def startServerAndOpenBrowser(statePath: String, port: Int, url: String): Unit =
  // Start server in a separate thread
  val serverThread = new Thread(() => {
    CaskServer.start(statePath, port)
  })
  serverThread.setDaemon(false)
  serverThread.start()

  // Wait for server to be ready
  if waitForServer(s"$url/health", timeoutSeconds = 5) then
    println(s"Server started successfully at $url")
    openBrowser(url)
    println("Press Ctrl+C to stop the server")
    // Keep main thread alive
    serverThread.join()
  else
    System.err.println("ERROR: Server failed to start within 5 seconds")
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
    println(s"Unable to detect platform. Please open $url manually")
    return

  Try {
    val pb = new ProcessBuilder(command: _*)
    pb.start()
  } match
    case Success(_) =>
      println(s"Opening browser to $url")
    case Failure(ex) =>
      println(s"Failed to open browser: ${ex.getMessage}")
      println(s"Please open $url manually")
