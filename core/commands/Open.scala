// PURPOSE: Open command logic: open or create a tmux session for an existing worktree
// PURPOSE: Resolves issue ID from args or current branch, then routes attach/switch via TmuxOps

package iw.core.commands

import iw.core.adapters.SessionHookResult
import iw.core.model.{
  ConfigSerializer,
  Constants,
  IssueId,
  IssueTrackerType,
  ProjectConfiguration,
  SessionContext,
  WorktreePath
}

object Open:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    extractPrompt(args.toList, env) match
      case None                             => CommandResult.error
      case Some((promptOpt, remainingArgs)) =>
        readConfig(env) match
          case Left(err) =>
            env.console.err(s"Error: $err")
            env.console.out("Run './iw init' to initialize the project")
            CommandResult.error
          case Right(config) =>
            resolveIssueId(remainingArgs, config, env) match
              case Left(err) =>
                env.console.err(s"Error: $err")
                CommandResult.error
              case Right(issueId) =>
                openWorktreeSession(issueId, config, promptOpt, env)

  private def extractPrompt(
      args: List[String],
      env: CommandEnv
  ): Option[(Option[String], List[String])] =
    args.indexOf("--prompt") match
      case -1  => Some((None, args))
      case idx =>
        if idx + 1 >= args.length then
          env.console.err("Error: --prompt requires a text argument")
          env.console.out("Usage: iw open [--prompt <text>] [issue-id]")
          None
        else
          val prompt = args(idx + 1)
          val remaining = args.take(idx) ++ args.drop(idx + 2)
          Some((Some(prompt), remaining))

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

  private def resolveIssueId(
      args: List[String],
      config: ProjectConfiguration,
      env: CommandEnv
  ): Either[String, IssueId] =
    args.headOption match
      case Some(rawId) =>
        val teamPrefix = config.trackerType match
          case IssueTrackerType.GitHub | IssueTrackerType.GitLab =>
            config.teamPrefix
          case _ => None
        IssueId.parse(rawId, teamPrefix)
      case None =>
        env.git
          .getCurrentBranch(env.cwd)
          .flatMap(IssueId.fromBranch)

  private def openWorktreeSession(
      issueId: IssueId,
      config: ProjectConfiguration,
      promptOpt: Option[String],
      env: CommandEnv
  ): CommandResult =
    val worktreePath = WorktreePath(config.projectName, issueId)
    val targetPath = worktreePath.resolve(env.cwd)
    val sessionName = worktreePath.sessionName

    env.server.updateLastSeen(
      issueId.value,
      targetPath.toString,
      config.trackerType.toString,
      issueId.team
    ) match
      case Left(error) =>
        env.console.out(s"Warning: Failed to update dashboard: $error")
      case Right(_) => ()

    val alreadyInTargetSession =
      promptOpt.isEmpty && env.tmux.isInsideTmux &&
        env.tmux.currentSessionName.contains(sessionName)

    if !env.fs.exists(targetPath) then
      env.console.err(
        s"Error: Worktree not found: ${worktreePath.directoryName}"
      )
      env.console.out(
        s"Use './iw start ${issueId.value}' to create a new worktree"
      )
      CommandResult.error
    else if alreadyInTargetSession then
      env.console.out(s"Already in session '$sessionName'")
      CommandResult.ok
    else
      val sessionReady =
        if env.tmux.sessionExists(sessionName) then Right(())
        else
          env.console.out(
            s"Creating session '$sessionName' for existing worktree..."
          )
          env.tmux.createSession(sessionName, targetPath) match
            case Left(err) =>
              env.console.err(s"Error: Failed to create session: $err")
              Left(err)
            case Right(_) =>
              env.console.out("Session created")
              Right(())

      sessionReady match
        case Left(_)  => CommandResult.error
        case Right(_) =>
          val hookCtx =
            SessionContext(sessionName, targetPath, issueId.value, promptOpt)
          env.hooks.runSessionHooks(hookCtx) match
            case SessionHookResult.ActionHandled =>
              env.console.out(
                s"Session '$sessionName' ready and hook command sent"
              )
              CommandResult.ok
            case SessionHookResult.Error(message) =>
              env.console.err(s"Error: $message")
              env.console.out(
                s"Session ready. Attach manually with: tmux attach -t $sessionName"
              )
              CommandResult.error
            case SessionHookResult.SetupOnly =>
              joinSession(sessionName, env)
            case SessionHookResult.NoHooks =>
              if promptOpt.isDefined then
                env.console.out(
                  "Warning: --prompt ignored: no session action hook installed"
                )
                CommandResult.ok
              else joinSession(sessionName, env)

  private def joinSession(sessionName: String, env: CommandEnv): CommandResult =
    if env.tmux.isInsideTmux then
      env.console.out(s"Switching to session '$sessionName'...")
      env.tmux.switchSession(sessionName) match
        case Left(err) =>
          env.console.err(s"Error: $err")
          env.console.out(
            s"Switch manually with: tmux switch-client -t $sessionName"
          )
          CommandResult.error
        case Right(_) => CommandResult.ok
    else
      env.console.out(s"Attaching to session '$sessionName'...")
      env.tmux.attachSession(sessionName) match
        case Left(err) =>
          env.console.err(s"Error: $err")
          CommandResult.error
        case Right(_) => CommandResult.ok
