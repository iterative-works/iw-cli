// PURPOSE: Opens an existing worktree tmux session, creating session if needed.
// PURPOSE: Infers issue ID from current branch when no parameter given.
// USAGE: iw open [--prompt <text>] [issue-id]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def open(args: String*): Unit =
  // Parse --prompt flag if present
  val (promptOpt, remainingArgs) = extractPrompt(args.toList)

  // Load config first to get team prefix
  val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"
  val config = ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("Cannot read configuration")
      Output.info("Run './iw init' to initialize the project")
      sys.exit(1)
    case Some(c) => c

  // Resolve issue ID (from args or current branch)
  val issueIdResult = remainingArgs.headOption match
    case Some(rawId) =>
      // Parse explicit issue ID with team prefix from config (for GitHub/GitLab trackers)
      val teamPrefix = config.trackerType match
        case IssueTrackerType.GitHub | IssueTrackerType.GitLab =>
          config.teamPrefix
        case _ => None
      IssueId.parse(rawId, teamPrefix)
    case None => inferIssueFromBranch(config)

  issueIdResult match
    case Left(error) =>
      Output.error(error)
      sys.exit(1)
    case Right(issueId) =>
      openWorktreeSession(issueId, config, promptOpt)

def extractPrompt(args: List[String]): (Option[String], List[String]) =
  args.indexOf("--prompt") match
    case -1 => (None, args)
    case idx =>
      if idx + 1 >= args.length then
        Output.error("--prompt requires a text argument")
        Output.info("Usage: iw open [--prompt <text>] [issue-id]")
        sys.exit(1)
      val prompt = args(idx + 1)
      val remaining = args.take(idx) ++ args.drop(idx + 2)
      (Some(prompt), remaining)

def inferIssueFromBranch(config: ProjectConfiguration): Either[String, IssueId] =
  val currentDir = os.pwd
  GitAdapter.getCurrentBranch(currentDir).flatMap(IssueId.fromBranch(_))

def handleSessionJoin(sessionName: String, promptOpt: Option[String]): Unit =
  promptOpt match
    case Some(prompt) =>
      // Send claude command instead of attaching
      // Use single-quote wrapping for shell safety (protects all metacharacters)
      val shellSafe = "'" + prompt.replace("'", "'\\''") + "'"
      val claudeCommand = s"claude --dangerously-skip-permissions --prompt $shellSafe"
      TmuxAdapter.sendKeys(sessionName, claudeCommand) match
        case Left(error) =>
          Output.error(s"Failed to send claude command: $error")
          Output.info(s"Session ready. Attach manually with: tmux attach -t $sessionName")
          sys.exit(1)
        case Right(_) =>
          Output.success(s"Session '$sessionName' ready and claude agent launched")
    case None =>
      // Normal attach/switch behavior
      if TmuxAdapter.isInsideTmux then
        Output.info(s"Switching to session '$sessionName'...")
        TmuxAdapter.switchSession(sessionName) match
          case Left(error) =>
            Output.error(error)
            Output.info(s"Switch manually with: tmux switch-client -t $sessionName")
            sys.exit(1)
          case Right(_) =>
            () // Successfully switched
      else
        Output.info(s"Attaching to session '$sessionName'...")
        TmuxAdapter.attachSession(sessionName) match
          case Left(error) =>
            Output.error(error)
            sys.exit(1)
          case Right(_) =>
            () // Successfully attached and detached

def openWorktreeSession(issueId: IssueId, config: ProjectConfiguration, promptOpt: Option[String]): Unit =
      val currentDir = os.pwd
      val worktreePath = WorktreePath(config.projectName, issueId)
      val targetPath = worktreePath.resolve(currentDir)
      val sessionName = worktreePath.sessionName

      // Update dashboard lastSeenAt timestamp (best-effort)
      ServerClient.updateLastSeen(
        issueId.value,
        targetPath.toString,
        config.trackerType.toString,
        issueId.team
      ) match
        case Left(error) =>
          Output.warning(s"Failed to update dashboard: $error")
        case Right(_) =>
          () // Silent success

      // Check worktree exists
      if !os.exists(targetPath) then
        Output.error(s"Worktree not found: ${worktreePath.directoryName}")
        Output.info(s"Use './iw start ${issueId.value}' to create a new worktree")
        sys.exit(1)

      // Handle session joining (switch if inside tmux, attach if outside)
      // Special handling when --prompt is NOT provided and already in target session
      if promptOpt.isEmpty && TmuxAdapter.isInsideTmux then
        TmuxAdapter.currentSessionName match
          case Some(current) if current == sessionName =>
            Output.info(s"Already in session '$sessionName'")
            sys.exit(0)
          case _ => ()

      // Ensure session exists (create if needed)
      if !TmuxAdapter.sessionExists(sessionName) then
        Output.info(s"Creating session '$sessionName' for existing worktree...")
        TmuxAdapter.createSession(sessionName, targetPath) match
          case Left(error) =>
            Output.error(s"Failed to create session: $error")
            sys.exit(1)
          case Right(_) =>
            Output.success("Session created")

      // Join session or send prompt
      handleSessionJoin(sessionName, promptOpt)
