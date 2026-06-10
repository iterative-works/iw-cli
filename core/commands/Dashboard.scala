// PURPOSE: Dashboard command logic: parse args, decide dev/prod paths, start server,
// PURPOSE: open browser. Heavy lifecycle (HTTP probe, ProcessBuilder, browser) lives in DashboardLifecycle.

package iw.core.commands

import iw.core.model.ServerConfig

object Dashboard:
  final case class Args(
      statePath: Option[String] = None,
      sampleData: Boolean = false,
      devMode: Boolean = false
  )

  private val ReadyTimeoutMs: Long = 30_000L

  def run(args: Seq[String], env: CommandEnv): CommandResult =
    parseArgs(args.toList, Args(), env) match
      case Left(result)      => result
      case Right(parsedArgs) =>
        env.envVars.get("IW_DASHBOARD_JAR") match
          case None =>
            env.console.err(
              "Error: IW_DASHBOARD_JAR not set — invoke via ./iw dashboard, not directly"
            )
            CommandResult.error
          case Some(jar) =>
            env.envVars.get("HOME") match
              case None =>
                env.console.err("Error: HOME environment variable not set")
                CommandResult.error
              case Some(home) =>
                orchestrate(parsedArgs, jar, home, env)

  @scala.annotation.tailrec
  private def parseArgs(
      remaining: List[String],
      acc: Args,
      env: CommandEnv
  ): Either[CommandResult, Args] =
    remaining match
      case Nil                             => Right(acc)
      case "--state-path" :: value :: rest =>
        parseArgs(rest, acc.copy(statePath = Some(value)), env)
      case "--sample-data" :: rest =>
        parseArgs(rest, acc.copy(sampleData = true), env)
      case "--dev" :: rest =>
        parseArgs(rest, acc.copy(devMode = true), env)
      case ("--help" | "-h") :: _ =>
        printHelp(env)
        Left(CommandResult.ok)
      case other :: _ =>
        env.console.err(s"Error: Unknown argument: $other")
        env.console.out(
          "Usage: ./iw dashboard [--state-path <path>] [--sample-data] [--dev] [--help]"
        )
        Left(CommandResult.error)

  private def orchestrate(
      args: Args,
      jar: String,
      home: String,
      env: CommandEnv
  ): CommandResult =
    val serverDir = s"$home/.local/share/iw/server"
    val defaultStatePath = s"$serverDir/state.json"
    val defaultConfigPath = s"$serverDir/config.json"

    val (statePath, configPath, devMode, sampleData) =
      if args.devMode then setupDevMode(args.statePath, env)
      else
        (
          args.statePath.getOrElse(defaultStatePath),
          defaultConfigPath,
          false,
          args.sampleData
        )

    val sampleResult =
      if sampleData then generateSampleData(jar, statePath, env)
      else Right(())

    sampleResult match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(_) =>
        env.serverConfig.getOrCreateDefault(configPath) match
          case Left(err) =>
            env.console.err(s"Error: Failed to read config: $err")
            CommandResult.error
          case Right(serverConf) =>
            val port = serverConf.port
            val url = s"http://localhost:$port"
            if !env.dashboard.isServerRunning(s"$url/health") then
              env.console.out("Starting dashboard server...")
              if args.statePath.isDefined || sampleData then
                env.console.out(s"Using state file: $statePath")
              startAndWait(
                statePath,
                port,
                url,
                serverConf.hosts,
                jar,
                devMode,
                env
              )
            else
              env.console.out(s"Server already running at $url")
              env.dashboard.openBrowser(url)
              CommandResult.ok

  private def setupDevMode(
      explicitStatePath: Option[String],
      env: CommandEnv
  ): (String, String, Boolean, Boolean) =
    val timestamp = env.clock.now
    val tempDir = s"/tmp/iw-dev-$timestamp"
    val tempStatePath = s"$tempDir/state.json"
    val tempConfigPath = s"$tempDir/config.json"

    env.fs.makeDirAll(os.Path(tempDir))

    val devPort = env.dashboard.findAvailablePort()
    val defaultConfig = ServerConfig(port = devPort, hosts = List("localhost"))
    env.serverConfig.write(defaultConfig, tempConfigPath) match
      case Right(_) =>
        env.console.out(s"Created dev mode config at $tempConfigPath")
      case Left(err) =>
        env.console.err(s"Error: Failed to create dev config: $err")

    env.console.out("Dev mode enabled:")
    env.console.out(s"  - Temp directory: $tempDir")
    env.console.out(s"  - State file: $tempStatePath")
    env.console.out(s"  - Config file: $tempConfigPath")
    env.console.out(s"  - Port: $devPort")
    env.console.out(s"  - Sample data: enabled")

    val finalStatePath = explicitStatePath.getOrElse(tempStatePath)
    (finalStatePath, tempConfigPath, true, true)

  private def generateSampleData(
      jar: String,
      statePath: String,
      env: CommandEnv
  ): Either[String, Unit] =
    env.console.out("Generating sample data...")
    val cmd = Seq(
      "java",
      "-cp",
      jar,
      "iw.dashboard.SampleDataCli",
      statePath
    )
    val code = env.dashboard.runSync(cmd)
    if code == 0 then Right(())
    else Left(s"Sample data generation failed (exit $code)")

  private def startAndWait(
      statePath: String,
      port: Int,
      url: String,
      hosts: Seq[String],
      jar: String,
      devMode: Boolean,
      env: CommandEnv
  ): CommandResult =
    val cmd = Seq(
      "java",
      "-jar",
      jar,
      statePath,
      port.toString,
      hosts.mkString(",")
    ) ++ (if devMode then Seq("--dev") else Seq.empty)

    val onReady: () => Unit = () =>
      env.console.out(s"✓ Server started successfully at $url")
      env.dashboard.openBrowser(url)
      env.console.out("Press Ctrl+C to stop the server")

    env.dashboard
      .startServerAndBlock(cmd, s"$url/health", ReadyTimeoutMs, onReady) match
      case Right(exitCode) => CommandResult(exitCode)
      case Left(err)       =>
        env.console.err(s"Error: $err")
        CommandResult.error

  private def printHelp(env: CommandEnv): Unit =
    val help = """Usage: ./iw dashboard [OPTIONS]
                 |
                 |Start the iw dashboard server and open it in a browser.
                 |
                 |Options:
                 |  --state-path <path>  Use a custom state file location
                 |  --sample-data        Load sample data for demonstration
                 |  --dev                Development mode with complete isolation
                 |  --help, -h           Show this help message""".stripMargin
    help.split('\n').foreach(env.console.out)
