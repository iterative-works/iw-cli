// PURPOSE: Register command logic: register worktree or project with the dashboard server
// PURPOSE: Branch decides — issue branch registers worktree + parent project; otherwise project

package iw.core.commands

import iw.core.model.{
  ConfigSerializer,
  Constants,
  IssueId,
  ProjectConfiguration,
  ProjectPath,
  TrackerUrlBuilder
}

object Register:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    env.git.getCurrentBranch(env.cwd) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(branch) =>
        readConfig(env.cwd, env) match
          case Left(err) =>
            env.console.err(s"Error: $err")
            env.console.out("Run './iw init' to initialize the project")
            CommandResult.error
          case Right(config) =>
            IssueId.fromBranch(branch) match
              case Left(_)        => registerProject(config, env)
              case Right(issueId) =>
                registerWorktreeAndParent(issueId, config, env)

  private def readConfig(
      dir: os.Path,
      env: CommandEnv
  ): Either[String, ProjectConfiguration] =
    val configPath = dir / Constants.Paths.IwDir / "config.conf"
    if !env.fs.exists(configPath) then Left("Cannot read configuration")
    else
      env.fs
        .read(configPath)
        .flatMap(ConfigSerializer.fromHocon)
        .left
        .map(_ => "Cannot read configuration")

  private def registerProject(
      config: ProjectConfiguration,
      env: CommandEnv
  ): CommandResult =
    val trackerUrl = TrackerUrlBuilder.buildTrackerUrl(config)
    env.server.registerProject(
      config.projectName,
      env.cwd.toString,
      config.trackerType.toString,
      config.teamIdentifier,
      trackerUrl
    ) match
      case Left(err) =>
        env.console.err(
          s"Error: Failed to register project with dashboard: $err"
        )
        CommandResult.error
      case Right(_) =>
        env.console.out(
          s"Registered project '${config.projectName}' at ${env.cwd}"
        )
        CommandResult.ok

  private def registerWorktreeAndParent(
      issueId: IssueId,
      config: ProjectConfiguration,
      env: CommandEnv
  ): CommandResult =
    env.server.registerWorktree(
      issueId.value,
      env.cwd.toString,
      config.trackerType.toString,
      issueId.team
    ) match
      case Left(err) =>
        env.console.err(
          s"Error: Failed to register worktree with dashboard: $err"
        )
        CommandResult.error
      case Right(_) =>
        registerParentProjectBestEffort(env)
        env.console.out(
          s"Registered worktree for ${issueId.value} at ${env.cwd}"
        )
        CommandResult.ok

  private def registerParentProjectBestEffort(env: CommandEnv): Unit =
    ProjectPath.deriveMainProjectPath(env.cwd.toString) match
      case None =>
        env.console.out(
          "Warning: Could not derive parent project path from current directory"
        )
      case Some(parentPath) =>
        readConfig(os.Path(parentPath), env) match
          case Left(_) =>
            env.console.out(
              s"Warning: Could not read parent project config at $parentPath/.iw/config.conf, skipping project registration"
            )
          case Right(parentConfig) =>
            val trackerUrl = TrackerUrlBuilder.buildTrackerUrl(parentConfig)
            env.server.registerProject(
              parentConfig.projectName,
              parentPath,
              parentConfig.trackerType.toString,
              parentConfig.teamIdentifier,
              trackerUrl
            ) match
              case Left(err) =>
                env.console.out(
                  s"Warning: Failed to register parent project with dashboard: $err"
                )
              case Right(_) => ()
