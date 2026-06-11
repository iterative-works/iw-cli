// PURPOSE: Doctor command logic: run environment + quality checks, optionally invoke fix actions
// PURPOSE: Aggregates base checks with plugin-discovered Check/FixAction hooks

package iw.core.commands

import iw.core.model.{
  BuildSystem,
  Check,
  CheckResult,
  ConfigSerializer,
  Constants,
  DoctorChecks,
  DoctorCliFlags,
  DoctorFixContext,
  IssueTrackerType,
  ProjectConfiguration
}
import iw.core.output.DoctorOutput

object Doctor:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val configPath = env.cwd / Constants.Paths.IwDir / "config.conf"
    val config = readConfig(configPath, env).getOrElse(
      ProjectConfiguration.create(
        IssueTrackerType.Linear,
        "UNKNOWN",
        "unknown"
      )
    )

    val flags = DoctorCliFlags.parse(args)

    val allChecks = baseChecks(configPath, env) ++ env.hooks.discoverChecks
    val checksToRun =
      DoctorChecks.filterByCategory(allChecks, flags.filterCategory)
    val results = DoctorChecks.runAll(checksToRun, config)

    val rendered = DoctorOutput.render(
      results,
      showHeaders = flags.filterCategory.isEmpty
    )
    rendered.lines.foreach(env.console.out)

    if flags.fixMode then runFixMode(results, config, env)
    else CommandResult(rendered.exitCode)

  private def readConfig(
      configPath: os.Path,
      env: CommandEnv
  ): Option[ProjectConfiguration] =
    if !env.fs.exists(configPath) then None
    else
      env.fs
        .read(configPath)
        .flatMap(ConfigSerializer.fromHocon)
        .toOption

  private def baseChecks(configPath: os.Path, env: CommandEnv): List[Check] =
    List(
      Check(
        "Git repository",
        { _ =>
          if env.git.isRepository(env.cwd) then CheckResult.Success("Found")
          else
            CheckResult
              .Error("Not found", "Initialize git repository: git init")
        }
      ),
      Check(
        "Configuration",
        { _ =>
          if !env.fs.exists(configPath) then
            CheckResult.Error("Missing or invalid", "Run: iw init")
          else
            env.fs
              .read(configPath)
              .flatMap(ConfigSerializer.fromHocon) match
              case Right(_) =>
                CheckResult.Success(s"${Constants.Paths.ConfigFile} valid")
              case Left(_) =>
                CheckResult.Error("Missing or invalid", "Run: iw init")
        }
      )
    )

  private def runFixMode(
      results: List[(String, CheckResult, String)],
      config: ProjectConfiguration,
      env: CommandEnv
  ): CommandResult =
    val errorCount = results.count(_._2.isInstanceOf[CheckResult.Error])
    if errorCount == 0 then
      env.console.out("All quality gate checks pass. Nothing to fix.")
      CommandResult.ok
    else
      val fixActions = env.hooks.discoverFixActions
      if fixActions.isEmpty then
        env.console.out(
          "Warning: No fix provider installed. Install a plugin that provides a FixAction hook."
        )
        CommandResult.error
      else
        val failedChecks = results.collect {
          case (name, CheckResult.Error(_, _), _) => name
        }
        val buildSystem = BuildSystem.detectWith(env.fs.exists)
        val ciPlatform = config.tracker.trackerType match
          case IssueTrackerType.GitHub => "GitHub Actions"
          case IssueTrackerType.GitLab => "GitLab CI"
          case _                       => "Unknown"

        val ctx =
          DoctorFixContext(failedChecks, buildSystem, ciPlatform, config)
        CommandResult(fixActions.head.fix(ctx))
