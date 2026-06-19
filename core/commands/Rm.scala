// PURPOSE: Rm command logic: remove a worktree (and its tmux session) for an issue
// PURPOSE: Prompts on uncommitted changes; unregisters from dashboard best-effort

package iw.core.commands

import iw.core.model.{
  CleanupContext,
  ConfigSerializer,
  Constants,
  IssueId,
  IssueTrackerType,
  ProjectConfiguration,
  WorktreePath
}
import scala.util.control.NonFatal

object Rm:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val (issueIdArg, forceFlag) = parseArgs(args.toList)

    readConfig(env) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        env.console.out("Run './iw init' to initialize the project")
        CommandResult.error
      case Right(config) =>
        issueIdArg match
          case None =>
            env.console.err("Error: Missing issue ID")
            env.console.out("Usage: ./iw rm <issue-id> [--force]")
            CommandResult.error
          case Some(rawId) =>
            parseIssueId(rawId, config) match
              case Left(err) =>
                env.console.err(s"Error: $err")
                CommandResult.error
              case Right(issueId) =>
                removeWorktree(issueId, forceFlag, config, env)

  private def parseArgs(args: List[String]): (Option[String], Boolean) =
    val forceFlag = args.contains("--force")
    val issueIdArg = args.find(arg => !arg.startsWith("--"))
    (issueIdArg, forceFlag)

  private def readConfig(
      env: CommandEnv
  ): Either[String, ProjectConfiguration] =
    val configPath = env.cwd / Constants.Paths.IwDir / "config.conf"
    if !env.fs.exists(configPath) then Left("Cannot read configuration")
    else
      env.fs
        .read(configPath)
        .flatMap(ConfigSerializer.fromHocon)
        .left
        .map(_ => "Cannot read configuration")

  private def parseIssueId(
      rawId: String,
      config: ProjectConfiguration
  ): Either[String, IssueId] =
    val teamPrefix = config.trackerType match
      case IssueTrackerType.GitHub | IssueTrackerType.GitLab =>
        config.teamPrefix
      case _ => None
    IssueId.parse(rawId, teamPrefix)

  private def removeWorktree(
      issueId: IssueId,
      force: Boolean,
      config: ProjectConfiguration,
      env: CommandEnv
  ): CommandResult =
    val worktreePath = WorktreePath(config.projectName, issueId)
    val targetPath = worktreePath.resolve(env.cwd)
    val sessionName = worktreePath.sessionName

    if !env.worktree.exists(targetPath, env.cwd) then
      env.console.err(
        s"Error: Worktree not found: ${worktreePath.directoryName}"
      )
      CommandResult.error
    else if env.tmux.isCurrentSession(sessionName) then
      env.console.err(
        "Error: Cannot remove worktree - you are in its tmux session"
      )
      env.console.out("Detach from session first: Ctrl+B, D")
      CommandResult.error
    else
      decideForce(targetPath, force, env) match
        case Left(result)       => result
        case Right(forceRemove) =>
          val ctx = CleanupContext(
            worktreePath = targetPath,
            issueId = issueId.value,
            config = config,
            force = forceRemove
          )
          runCleanupHooks(ctx, env) match
            case Left(result) =>
              result // worktree preserved: a hook signalled abort
            case Right(hookWarnings) =>
              // Project hooks run first (above), then the built-in teardown, so
              // a hook can observe live daemon state before the built-in stops it.
              val builtinWarnings =
                if config.cleanup.builtin then
                  BuildToolCleanupRunner.run(ctx, env)
                else Nil
              val warnings = hookWarnings ++ builtinWarnings
              warnings.foreach(w => env.console.out(s"Warning: $w"))
              killSessionIfPresent(sessionName, env)
              env.console.out(
                s"Removing worktree '${worktreePath.directoryName}'..."
              )
              env.worktree.remove(
                targetPath,
                env.cwd,
                force = forceRemove
              ) match
                case Left(err) =>
                  env.console.err(s"Error: Failed to remove worktree: $err")
                  CommandResult.error
                case Right(_) =>
                  env.console.out("Worktree removed")
                  unregisterBestEffort(issueId.value, env)
                  env.console.out(
                    s"Branch '${issueId.value}' was not deleted (delete manually if needed)"
                  )
                  CommandResult.ok

  private def runCleanupHooks(
      ctx: CleanupContext,
      env: CommandEnv
  ): Either[CommandResult, List[String]] =
    // CleanupAction contract: throwing = abort (preserve worktree, exit non-zero);
    // a returned list = warnings. See CleanupAction scaladoc.
    env.hooks.cleanupActions
      .foldLeft[Either[CommandResult, List[String]]](Right(Nil)) {
        case (Left(result), _) =>
          Left(result) // already aborted; skip remaining hooks
        case (Right(acc), action) =>
          try Right(acc ++ action.cleanup(ctx))
          catch
            case NonFatal(e) =>
              val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
              env.console.err(s"Error: Cleanup hook failed: $msg")
              Left(CommandResult.error)
      }

  private def decideForce(
      targetPath: os.Path,
      force: Boolean,
      env: CommandEnv
  ): Either[CommandResult, Boolean] =
    if force then Right(true)
    else
      env.git.hasUncommittedChanges(targetPath) match
        case Left(err) =>
          env.console.err(
            s"Error: Failed to check for uncommitted changes: $err"
          )
          Left(CommandResult.error)
        case Right(true) =>
          env.console.out("Warning: Worktree has uncommitted changes")
          if env.prompt.confirm("Continue with removal?", default = false) then
            Right(true)
          else
            env.console.out("Removal cancelled")
            Left(CommandResult.ok)
        case Right(false) => Right(false)

  private def killSessionIfPresent(sessionName: String, env: CommandEnv): Unit =
    if env.tmux.sessionExists(sessionName) then
      env.console.out(s"Killing tmux session '$sessionName'...")
      env.tmux.killSession(sessionName) match
        case Left(err) =>
          env.console.out(s"Warning: Failed to kill session: $err")
          env.console.out("Continuing with worktree removal...")
        case Right(_) =>
          env.console.out("Session killed")

  private def unregisterBestEffort(issueId: String, env: CommandEnv): Unit =
    env.server.unregisterWorktree(issueId) match
      case Right(_) =>
        env.console.out("Unregistered from dashboard")
      case Left(err) =>
        env.console.err(s"Warning: Failed to unregister from dashboard: $err")
