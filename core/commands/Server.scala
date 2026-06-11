// PURPOSE: Server command logic: start/stop/status of the iw dashboard server process
// PURPOSE: PID file + health check + status RPC routed via ProcessLifecycle capability

package iw.core.commands

import iw.core.model.{ServerConfig, ServerLifecycleService}

object Server:
  private val HealthAttempts: Int = 50
  private val HealthIntervalMs: Long = 100L
  private val StopTimeoutSeconds: Int = 10
  private val FallbackPort: Int = 9876

  def run(args: Seq[String], env: CommandEnv): CommandResult =
    if args.isEmpty then
      usage(env)
      CommandResult.error
    else
      args.head match
        case "start"  => startServer(env)
        case "stop"   => stopServer(env)
        case "status" => showStatus(env)
        case cmd      =>
          env.console.err(s"Error: Unknown command: $cmd")
          usage(env)
          CommandResult.error

  private def usage(env: CommandEnv): Unit =
    env.console.err("Usage: iw server <start|stop|status>")

  private def serverDir(env: CommandEnv): String =
    env.envVars
      .get("HOME")
      .getOrElse(System.getProperty("user.home"))
      + "/.local/share/iw/server"

  private def configPath(env: CommandEnv): String =
    s"${serverDir(env)}/config.json"
  private def pidPath(env: CommandEnv): String =
    s"${serverDir(env)}/server.pid"
  private def statePath(env: CommandEnv): String =
    s"${serverDir(env)}/state.json"

  private def startServer(env: CommandEnv): CommandResult =
    env.serverConfig.getOrCreateDefault(configPath(env)) match
      case Left(err) =>
        env.console.err(s"Error reading config: $err")
        CommandResult.error
      case Right(config) =>
        ensureNotRunning(config, env) match
          case Left(result) => result
          case Right(_)     =>
            spawnAndConfirm(config, env)

  private def ensureNotRunning(
      config: ServerConfig,
      env: CommandEnv
  ): Either[CommandResult, Unit] =
    env.processLifecycle.readPidFile(pidPath(env)) match
      case Right(Some(pid)) if env.processLifecycle.isProcessAlive(pid) =>
        env.console.out(
          s"Server is already running on port ${config.port} (PID: $pid)"
        )
        Left(CommandResult.error)
      case Right(Some(pid)) =>
        env.console.out(
          s"Removing stale PID file (process $pid is not running)"
        )
        env.processLifecycle.removePidFile(pidPath(env))
        Right(())
      case Right(None) => Right(())
      case Left(err)   =>
        env.console.err(s"Error reading PID file: $err")
        Left(CommandResult.error)

  private def spawnAndConfirm(
      config: ServerConfig,
      env: CommandEnv
  ): CommandResult =
    env.processLifecycle.spawnServerProcess(
      statePath(env),
      config.port,
      config.hosts
    ) match
      case Left(err) =>
        env.console.err(s"Failed to start server: $err")
        CommandResult.error
      case Right(pid) =>
        env.processLifecycle.writePidFile(pid, pidPath(env)) match
          case Left(err) =>
            env.console.err(
              s"Server started but failed to write PID file: $err"
            )
            env.console.out(s"Server PID: $pid")
          case Right(_) => ()

        val healthUrl = s"http://localhost:${config.port}/health"
        val healthy = env.processLifecycle
          .waitForHealth(healthUrl, HealthAttempts, HealthIntervalMs)
        if healthy then
          val securityAnalysis = ServerConfig.analyzeHostsSecurity(config.hosts)
          ServerLifecycleService
            .formatSecurityWarning(securityAnalysis)
            .foreach { warning =>
              env.console.out(warning)
              env.console.out("")
            }
          val addresses =
            config.hosts.map(host => s"$host:${config.port}").mkString(", ")
          env.console.out(s"Server started on $addresses")
          CommandResult.ok
        else
          val logPath = env.processLifecycle.serverLogPath(statePath(env))
          env.console.err(
            s"Server process started (PID: $pid) but health check failed"
          )
          env.console.err(s"Check logs: $logPath")
          CommandResult.error

  private def stopServer(env: CommandEnv): CommandResult =
    env.processLifecycle.readPidFile(pidPath(env)) match
      case Left(err) =>
        env.console.err(s"Error reading PID file: $err")
        CommandResult.error
      case Right(None) =>
        env.console.out("Server is not running")
        CommandResult.ok
      case Right(Some(pid)) =>
        if !env.processLifecycle.isProcessAlive(pid) then
          env.console.out("Server is not running (stale PID file)")
          env.processLifecycle.removePidFile(pidPath(env))
          CommandResult.ok
        else
          env.processLifecycle.stopProcess(pid, StopTimeoutSeconds) match
            case Right(_) =>
              env.processLifecycle.removePidFile(pidPath(env))
              env.console.out("Server stopped")
              CommandResult.ok
            case Left(err) =>
              env.console.err(s"Failed to stop server: $err")
              env.console.err(s"You may need to manually kill process $pid")
              CommandResult.error

  private def showStatus(env: CommandEnv): CommandResult =
    env.processLifecycle.readPidFile(pidPath(env)) match
      case Left(err) =>
        env.console.err(s"Error reading PID file: $err")
        CommandResult.error
      case Right(None) =>
        env.console.out("Server is not running")
        CommandResult.ok
      case Right(Some(pid)) =>
        if !env.processLifecycle.isProcessAlive(pid) then
          env.console.out("Server is not running")
          CommandResult.ok
        else
          val port = env.serverConfig.getOrCreateDefault(configPath(env)) match
            case Right(c) => c.port
            case Left(_)  => FallbackPort
          env.processLifecycle.fetchJson(
            s"http://localhost:$port/api/status"
          ) match
            case Right(body) =>
              try
                val statusJson = ujson.read(body)
                val worktreeCount = statusJson("worktreeCount").num.toInt
                val startedAt =
                  java.time.Instant.parse(statusJson("startedAt").str)
                val now = java.time.Instant.ofEpochMilli(env.clock.now)
                val uptime = ServerLifecycleService.formatUptime(startedAt, now)
                val hosts =
                  if statusJson.obj.contains("hosts") then
                    statusJson("hosts").arr.map(_.str).toSeq
                  else Seq.empty[String]
                val hostDisplay =
                  ServerLifecycleService.formatHostsDisplay(hosts, port)
                env.console.out(hostDisplay)
                env.console.out(s"Tracking $worktreeCount worktrees")
                env.console.out(s"Uptime: $uptime")
                env.console.out(s"PID: $pid")
                CommandResult.ok
              catch
                case e: Exception =>
                  env.console.out(
                    s"Server process running (PID: $pid) but not responding"
                  )
                  env.console.err(s"Error: ${e.getMessage}")
                  CommandResult.ok
            case Left(_) =>
              env.console.out(
                s"Server process running (PID: $pid) but status endpoint failed"
              )
              CommandResult.ok
