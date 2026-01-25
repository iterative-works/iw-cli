#!/usr/bin/env -S scala-cli shebang
// PURPOSE: Manage iw dashboard server lifecycle (start/stop/status)
// PURPOSE: Uses ServerClient for health checks and ProcessManager for process control

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*
import sttp.client4.quick.*
import java.nio.file.Paths

def getServerDir(): String =
  val homeDir = System.getProperty("user.home")
  s"$homeDir/.local/share/iw/server"

def getConfigPath(): String =
  s"${getServerDir()}/config.json"

def getPidPath(): String =
  s"${getServerDir()}/server.pid"

def getStatePath(): String =
  s"${getServerDir()}/state.json"

def startServer(): Unit =
  val configPath = getConfigPath()
  val pidPath = getPidPath()
  val statePath = getStatePath()

  // Read or create default config
  val configResult = ServerConfigRepository.getOrCreateDefault(configPath)
  val config = configResult match
    case Right(c) => c
    case Left(err) =>
      System.err.println(s"Error reading config: $err")
      System.exit(1)
      return

  // Check if server is already running
  ProcessManager.readPidFile(pidPath) match
    case Right(Some(pid)) if ProcessManager.isProcessAlive(pid) =>
      println(s"Server is already running on port ${config.port} (PID: $pid)")
      System.exit(1)
    case Right(Some(pid)) =>
      // Stale PID file - process dead
      println(s"Removing stale PID file (process $pid is not running)")
      ProcessManager.removePidFile(pidPath)
    case Right(None) =>
      // No PID file, continue
      ()
    case Left(err) =>
      System.err.println(s"Error reading PID file: $err")
      System.exit(1)

  // Spawn server process
  val spawnResult = ProcessManager.spawnServerProcess(statePath, config.port, config.hosts)
  val pid = spawnResult match
    case Right(p) => p
    case Left(err) =>
      System.err.println(s"Failed to start server: $err")
      System.exit(1)
      return

  // Write PID file
  ProcessManager.writePidFile(pid, pidPath) match
    case Left(err) =>
      System.err.println(s"Server started but failed to write PID file: $err")
      println(s"Server PID: $pid")
    case Right(_) => ()

  // Wait for health check
  var retries = 0
  var healthy = false
  while retries < 50 && !healthy do
    Thread.sleep(100)
    try
      val response = quickRequest.get(uri"http://localhost:${config.port}/health").send()
      if response.code.code == 200 then
        healthy = true
    catch
      case _: Exception => ()
    retries += 1

  if healthy then
    // Display security warning if applicable
    val securityAnalysis = ServerConfig.analyzeHostsSecurity(config.hosts)
    ServerLifecycleService.formatSecurityWarning(securityAnalysis).foreach { warning =>
      println(warning)
      println()
    }

    val addresses = config.hosts.map(host => s"$host:${config.port}").mkString(", ")
    println(s"Server started on $addresses")
  else
    System.err.println(s"Server process started (PID: $pid) but health check failed")
    System.err.println(s"Check logs for errors")
    System.exit(1)

def stopServer(): Unit =
  val pidPath = getPidPath()

  // Read PID file
  val pidResult = ProcessManager.readPidFile(pidPath)
  val maybePid = pidResult match
    case Right(p) => p
    case Left(err) =>
      System.err.println(s"Error reading PID file: $err")
      System.exit(1)
      return

  maybePid match
    case None =>
      println("Server is not running")
      return
    case Some(pid) =>
      // Check if process is alive
      if !ProcessManager.isProcessAlive(pid) then
        println("Server is not running (stale PID file)")
        ProcessManager.removePidFile(pidPath)
        return

      // Stop the process
      ProcessManager.stopProcess(pid, 10) match
        case Right(_) =>
          ProcessManager.removePidFile(pidPath)
          println("Server stopped")
        case Left(err) =>
          System.err.println(s"Failed to stop server: $err")
          System.err.println(s"You may need to manually kill process $pid")
          System.exit(1)

def showStatus(): Unit =
  val pidPath = getPidPath()
  val configPath = getConfigPath()

  // Read PID file
  val pidResult = ProcessManager.readPidFile(pidPath)
  val maybePid = pidResult match
    case Right(p) => p
    case Left(err) =>
      System.err.println(s"Error reading PID file: $err")
      System.exit(1)
      return

  maybePid match
    case None =>
      println("Server is not running")
    case Some(pid) =>
      // Check if process is alive
      if !ProcessManager.isProcessAlive(pid) then
        println("Server is not running")
        return

      // Read config to get port
      val configResult = ServerConfigRepository.read(configPath)
      val port = configResult match
        case Right(c) => c.port
        case Left(_) => 9876 // fallback to default

      // Call status endpoint
      try
        val response = quickRequest.get(uri"http://localhost:$port/api/status").send()
        if response.code.code == 200 then
          val statusJson = ujson.read(response.body)
          val worktreeCount = statusJson("worktreeCount").num.toInt
          val startedAt = java.time.Instant.parse(statusJson("startedAt").str)
          val now = java.time.Instant.now()
          val uptime = ServerLifecycleService.formatUptime(startedAt, now)

          // Get hosts from status response, fall back to empty if missing
          val hosts = if statusJson.obj.contains("hosts") then
            statusJson("hosts").arr.map(_.str).toSeq
          else
            Seq.empty[String]

          val hostDisplay = ServerLifecycleService.formatHostsDisplay(hosts, port)

          println(hostDisplay)
          println(s"Tracking $worktreeCount worktrees")
          println(s"Uptime: $uptime")
          println(s"PID: $pid")
        else
          println(s"Server process running (PID: $pid) but status endpoint failed")
      catch
        case e: Exception =>
          println(s"Server process running (PID: $pid) but not responding")
          System.err.println(s"Error: ${e.getMessage}")

@main
def main(args: String*): Unit =
  if args.isEmpty then
    System.err.println("Usage: iw server <start|stop|status>")
    System.exit(1)

  args(0) match
    case "start" => startServer()
    case "stop" => stopServer()
    case "status" => showStatus()
    case cmd =>
      System.err.println(s"Unknown command: $cmd")
      System.err.println("Usage: iw server <start|stop|status>")
      System.exit(1)
