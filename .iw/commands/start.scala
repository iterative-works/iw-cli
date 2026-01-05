// PURPOSE: Creates an isolated worktree for a specific issue with a tmux session
// USAGE: iw start <issue-id>

import iw.core.*
import iw.core.infrastructure.ServerClient

@main def start(args: String*): Unit =
  if args.isEmpty then
    Output.error("Missing issue ID")
    Output.info("Usage: iw start <issue-id>")
    sys.exit(1)

  val rawIssueId = args.head
  val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"

  // Read config to check tracker type and team prefix
  val config = ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("Cannot read configuration")
      Output.info("Run './iw init' to initialize the project")
      sys.exit(1)
      throw RuntimeException("unreachable") // for type checker
    case Some(c) => c

  // Parse issue ID, applying team prefix for GitHub if needed
  val teamPrefix = if config.trackerType == IssueTrackerType.GitHub then
    config.teamPrefix
  else
    None
  val issueIdResult = IssueId.parse(rawIssueId, teamPrefix, Some(config.trackerType))

  issueIdResult match
    case Left(error) =>
      Output.error(error)
      sys.exit(1)
    case Right(issueId) =>
      createWorktreeForIssue(issueId, config)

def createWorktreeForIssue(issueId: IssueId, config: ProjectConfiguration): Unit =
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

  // Register worktree with dashboard (best-effort)
  ServerClient.registerWorktree(
    issueId.value,
    targetPath.toString,
    config.trackerType.toString,
    issueId.team
  ) match
    case Left(error) =>
      Output.warning(s"Failed to register worktree with dashboard: $error")
    case Right(_) =>
      () // Silent success

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
