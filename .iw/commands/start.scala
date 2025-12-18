// PURPOSE: Creates an isolated worktree for a specific issue with a tmux session
// USAGE: iw start <issue-id>

import iw.core.*

@main def start(args: String*): Unit =
  if args.isEmpty then
    Output.error("Missing issue ID")
    Output.info("Usage: iw start <issue-id>")
    sys.exit(1)

  val rawIssueId = args.head

  // Validate issue ID format
  IssueId.parse(rawIssueId) match
    case Left(error) =>
      Output.error(error)
      sys.exit(1)
    case Right(issueId) =>
      createWorktreeForIssue(issueId)

def createWorktreeForIssue(issueId: IssueId): Unit =
  val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"

  // Read project config to get project name
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
      val branchName = issueId.toBranchName

      // Check for collisions
      if os.exists(targetPath) then
        Output.error(s"Directory ${worktreePath.directoryName} already exists")
        if GitWorktreeAdapter.worktreeExists(targetPath, currentDir) then
          Output.info(s"Use './iw open ${issueId.value}' to open existing worktree")
        sys.exit(1)

      if TmuxAdapter.sessionExists(sessionName) then
        Output.error(s"Tmux session '$sessionName' already exists")
        Output.info(s"Use './iw open ${issueId.value}' to attach to existing session")
        sys.exit(1)

      // Create worktree (with new branch or existing)
      Output.info(s"Creating worktree ${worktreePath.directoryName}...")

      val worktreeResult =
        if GitWorktreeAdapter.branchExists(branchName, currentDir) then
          Output.info(s"Using existing branch '$branchName'")
          GitWorktreeAdapter.createWorktreeForBranch(targetPath, branchName, currentDir)
        else
          Output.info(s"Creating new branch '$branchName'")
          GitWorktreeAdapter.createWorktree(targetPath, branchName, currentDir)

      worktreeResult match
        case Left(error) =>
          Output.error(error)
          sys.exit(1)
        case Right(_) =>
          Output.success(s"Worktree created at ${targetPath}")

      // Create tmux session
      Output.info(s"Creating tmux session '$sessionName'...")
      TmuxAdapter.createSession(sessionName, targetPath) match
        case Left(error) =>
          Output.error(error)
          // Cleanup: remove worktree on tmux failure
          Output.info("Cleaning up worktree...")
          ProcessAdapter.run(Seq("git", "worktree", "remove", targetPath.toString))
          sys.exit(1)
        case Right(_) =>
          Output.success(s"Tmux session created")

      // Join session (switch if inside tmux, attach if outside)
      if TmuxAdapter.isInsideTmux then
        Output.info(s"Switching to session '$sessionName'...")
        TmuxAdapter.switchSession(sessionName) match
          case Left(error) =>
            Output.error(error)
            Output.info(s"Session created. Switch manually with: tmux switch-client -t $sessionName")
            sys.exit(1)
          case Right(_) =>
            () // Successfully switched
      else
        Output.info(s"Attaching to session...")
        TmuxAdapter.attachSession(sessionName) match
          case Left(error) =>
            Output.error(error)
            Output.info(s"Session created. Attach manually with: tmux attach -t $sessionName")
            sys.exit(1)
          case Right(_) =>
            () // Successfully attached and detached
