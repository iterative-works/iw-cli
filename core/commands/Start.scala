// PURPOSE: Start command logic: create worktree + tmux session for an issue, run hooks
// PURPOSE: Handles branch creation/reuse, dashboard registration, cleanup on tmux failure

package iw.core.commands

import iw.core.adapters.SessionHookResult
import iw.core.model.{
  ConfigSerializer,
  Constants,
  IssueId,
  IssueTrackerType,
  ProjectConfiguration,
  SessionContext,
  TrackerUrlBuilder,
  WorktreePath
}

object Start:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    extractPrompt(args.toList, env) match
      case None                             => CommandResult.error
      case Some((promptOpt, remainingArgs)) =>
        if remainingArgs.isEmpty then
          env.console.err("Error: Missing issue ID")
          env.console.out("Usage: iw start [--prompt <text>] <issue-id>")
          CommandResult.error
        else
          readConfig(env) match
            case Left(err) =>
              env.console.err(s"Error: $err")
              env.console.out("Run './iw init' to initialize the project")
              CommandResult.error
            case Right(config) =>
              parseIssueId(remainingArgs.head, config) match
                case Left(err) =>
                  env.console.err(s"Error: $err")
                  CommandResult.error
                case Right(issueId) =>
                  createWorktreeForIssue(issueId, config, promptOpt, env)

  private def extractPrompt(
      args: List[String],
      env: CommandEnv
  ): Option[(Option[String], List[String])] =
    args.indexOf("--prompt") match
      case -1  => Some((None, args))
      case idx =>
        if idx + 1 >= args.length then
          env.console.err("Error: --prompt requires a text argument")
          env.console.out("Usage: iw start [--prompt <text>] <issue-id>")
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

  private def parseIssueId(
      rawIssueId: String,
      config: ProjectConfiguration
  ): Either[String, IssueId] =
    val teamPrefix = config.trackerType match
      case IssueTrackerType.GitHub | IssueTrackerType.GitLab =>
        config.teamPrefix
      case _ => None
    IssueId.parse(rawIssueId, teamPrefix)

  private def createWorktreeForIssue(
      issueId: IssueId,
      config: ProjectConfiguration,
      promptOpt: Option[String],
      env: CommandEnv
  ): CommandResult =
    val worktreePath = WorktreePath(config.projectName, issueId)
    val targetPath = worktreePath.resolve(env.cwd)
    val sessionName = worktreePath.sessionName
    val branchName = issueId.toBranchName

    if env.fs.exists(targetPath) then
      env.console.err(
        s"Error: Directory ${worktreePath.directoryName} already exists"
      )
      if env.worktree.exists(targetPath, env.cwd) then
        env.console.out(
          s"Use './iw open ${issueId.value}' to open existing worktree"
        )
      CommandResult.error
    else if env.tmux.sessionExists(sessionName) then
      env.console.err(s"Error: Tmux session '$sessionName' already exists")
      env.console.out(
        s"Use './iw open ${issueId.value}' to attach to existing session"
      )
      CommandResult.error
    else
      env.console.out(s"Creating worktree ${worktreePath.directoryName}...")
      createWorktree(targetPath, branchName, env) match
        case Left(err) =>
          env.console.err(s"Error: $err")
          CommandResult.error
        case Right(_) =>
          env.console.out(s"Worktree created at $targetPath")
          registerWithDashboard(issueId, config, targetPath, env)
          createSessionAndRunHooks(
            sessionName,
            targetPath,
            issueId,
            promptOpt,
            env
          )

  private def createWorktree(
      targetPath: os.Path,
      branchName: String,
      env: CommandEnv
  ): Either[String, Unit] =
    if env.worktree.branchExists(branchName, env.cwd) then
      env.console.out(s"Using existing branch '$branchName'")
      env.worktree.createForBranch(targetPath, branchName, env.cwd)
    else
      env.console.out(s"Creating new branch '$branchName'")
      env.worktree.create(targetPath, branchName, env.cwd)

  private def registerWithDashboard(
      issueId: IssueId,
      config: ProjectConfiguration,
      targetPath: os.Path,
      env: CommandEnv
  ): Unit =
    env.server.registerWorktree(
      issueId.value,
      targetPath.toString,
      config.trackerType.toString,
      issueId.team
    ) match
      case Left(err) =>
        env.console.out(
          s"Warning: Failed to register worktree with dashboard: $err"
        )
      case Right(_) => ()

    val trackerUrl = TrackerUrlBuilder.buildTrackerUrl(config)
    env.server.registerProject(
      config.projectName,
      env.cwd.toString,
      config.trackerType.toString,
      config.teamIdentifier,
      trackerUrl
    ) match
      case Left(err) =>
        env.console.out(
          s"Warning: Failed to register parent project with dashboard: $err"
        )
      case Right(_) => ()

  private def createSessionAndRunHooks(
      sessionName: String,
      targetPath: os.Path,
      issueId: IssueId,
      promptOpt: Option[String],
      env: CommandEnv
  ): CommandResult =
    env.console.out(s"Creating tmux session '$sessionName'...")
    env.tmux.createSession(sessionName, targetPath) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        env.console.out("Cleaning up worktree...")
        env.worktree.remove(targetPath, env.cwd, force = false)
        CommandResult.error
      case Right(_) =>
        env.console.out("Tmux session created")
        val ctx =
          SessionContext(sessionName, targetPath, issueId.value, promptOpt)
        env.hooks.runSessionHooks(ctx) match
          case SessionHookResult.ActionHandled =>
            env.console.out(
              s"Session '$sessionName' created and hook command sent"
            )
            CommandResult.ok
          case SessionHookResult.Error(message) =>
            env.console.err(s"Error: $message")
            env.console.out(
              s"Session created. Attach manually with: tmux attach -t $sessionName"
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
            s"Session created. Switch manually with: tmux switch-client -t $sessionName"
          )
          CommandResult.error
        case Right(_) => CommandResult.ok
    else
      env.console.out("Attaching to session...")
      env.tmux.attachSession(sessionName) match
        case Left(err) =>
          env.console.err(s"Error: $err")
          env.console.out(
            s"Session created. Attach manually with: tmux attach -t $sessionName"
          )
          CommandResult.error
        case Right(_) => CommandResult.ok
