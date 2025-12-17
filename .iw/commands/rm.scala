// PURPOSE: Remove a worktree for a completed issue
// PURPOSE: Kills tmux session and removes worktree with safety checks

import iw.core.*

@main def rm(args: String*): Unit =
  // Parse arguments
  val (issueIdArg, forceFlag) = parseArgs(args.toList)

  issueIdArg match
    case None =>
      Output.error("Missing issue ID")
      Output.info("Usage: ./iw rm <issue-id> [--force]")
      sys.exit(1)
    case Some(rawId) =>
      IssueId.parse(rawId) match
        case Left(error) =>
          Output.error(error)
          sys.exit(1)
        case Right(issueId) =>
          removeWorktree(issueId, forceFlag)

def parseArgs(args: List[String]): (Option[String], Boolean) =
  val forceFlag = args.contains("--force")
  val issueIdArg = args.find(arg => !arg.startsWith("--"))
  (issueIdArg, forceFlag)

def removeWorktree(issueId: IssueId, force: Boolean): Unit =
  val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"

  // Read project config
  ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("Cannot read configuration")
      Output.info("Run './iw init' to initialize the project")
      sys.exit(1)
    case Some(config) =>
      val currentDir = os.pwd
      val worktreePath = WorktreePath(config.projectName, issueId)
      val targetPath = worktreePath.resolve(currentDir)
      val sessionName = worktreePath.sessionName

      // Check if worktree exists
      if !GitWorktreeAdapter.worktreeExists(targetPath, currentDir) then
        Output.error(s"Worktree not found: ${worktreePath.directoryName}")
        sys.exit(1)

      // Check if currently in target session
      if TmuxAdapter.isCurrentSession(sessionName) then
        Output.error(s"Cannot remove worktree - you are in its tmux session")
        Output.info("Detach from session first: Ctrl+B, D")
        sys.exit(1)

      // Check for uncommitted changes
      if !force then
        GitAdapter.hasUncommittedChanges(targetPath) match
          case Left(error) =>
            Output.error(s"Failed to check for uncommitted changes: $error")
            sys.exit(1)
          case Right(true) =>
            Output.warning("Worktree has uncommitted changes")
            if !Prompt.confirm("Continue with removal?", default = false) then
              Output.info("Removal cancelled")
              sys.exit(0)
          case Right(false) =>
            // No uncommitted changes, proceed

      // Kill tmux session if it exists
      if TmuxAdapter.sessionExists(sessionName) then
        Output.info(s"Killing tmux session '$sessionName'...")
        TmuxAdapter.killSession(sessionName) match
          case Left(error) =>
            Output.warning(s"Failed to kill session: $error")
            Output.info("Continuing with worktree removal...")
          case Right(_) =>
            Output.success("Session killed")

      // Remove worktree
      Output.info(s"Removing worktree '${worktreePath.directoryName}'...")
      GitWorktreeAdapter.removeWorktree(targetPath, currentDir, force = force) match
        case Left(error) =>
          Output.error(s"Failed to remove worktree: $error")
          sys.exit(1)
        case Right(_) =>
          Output.success("Worktree removed")
          Output.info(s"Branch '${issueId.value}' was not deleted (delete manually if needed)")
