// PURPOSE: Opens an existing worktree tmux session, creating session if needed.
// PURPOSE: Infers issue ID from current branch when no parameter given.
// USAGE: iw open [issue-id]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*
import iw.core.dashboard.ServerClient

@main def open(args: String*): Unit =
  // Load config first to get team prefix
  val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"
  val config = ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("Cannot read configuration")
      Output.info("Run './iw init' to initialize the project")
      sys.exit(1)
    case Some(c) => c

  // Resolve issue ID (from args or current branch)
  val issueIdResult = args.headOption match
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
      openWorktreeSession(issueId, config)

def inferIssueFromBranch(config: ProjectConfiguration): Either[String, IssueId] =
  val currentDir = os.pwd
  GitAdapter.getCurrentBranch(currentDir).flatMap(IssueId.fromBranch(_))

def openWorktreeSession(issueId: IssueId, config: ProjectConfiguration): Unit =
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
      if TmuxAdapter.isInsideTmux then
        // Check if we're already in the target session
        TmuxAdapter.currentSessionName match
          case Some(current) if current == sessionName =>
            Output.info(s"Already in session '$sessionName'")
            sys.exit(0)
          case _ =>
            // Inside tmux but in different session - switch to target
            if TmuxAdapter.sessionExists(sessionName) then
              Output.info(s"Switching to session '$sessionName'...")
              TmuxAdapter.switchSession(sessionName) match
                case Left(error) =>
                  Output.error(error)
                  Output.info(s"Switch manually with: tmux switch-client -t $sessionName")
                  sys.exit(1)
                case Right(_) =>
                  () // Successfully switched
            else
              // Session doesn't exist, create it then switch
              Output.info(s"Creating session '$sessionName' for existing worktree...")
              TmuxAdapter.createSession(sessionName, targetPath) match
                case Left(error) =>
                  Output.error(s"Failed to create session: $error")
                  sys.exit(1)
                case Right(_) =>
                  Output.success("Session created")
                  Output.info(s"Switching to session '$sessionName'...")
                  TmuxAdapter.switchSession(sessionName) match
                    case Left(error) =>
                      Output.error(error)
                      Output.info(s"Switch manually with: tmux switch-client -t $sessionName")
                      sys.exit(1)
                    case Right(_) =>
                      () // Successfully switched
      else
        // Not inside tmux - use attach logic
        if TmuxAdapter.sessionExists(sessionName) then
          Output.info(s"Attaching to session '$sessionName'...")
          TmuxAdapter.attachSession(sessionName) match
            case Left(error) =>
              Output.error(error)
              sys.exit(1)
            case Right(_) =>
              () // Successfully attached and detached
        else
          Output.info(s"Creating session '$sessionName' for existing worktree...")
          TmuxAdapter.createSession(sessionName, targetPath) match
            case Left(error) =>
              Output.error(s"Failed to create session: $error")
              sys.exit(1)
            case Right(_) =>
              Output.success("Session created")
              TmuxAdapter.attachSession(sessionName) match
                case Left(error) =>
                  Output.error(error)
                  Output.info(s"Attach manually with: tmux attach -t $sessionName")
                  sys.exit(1)
                case Right(_) =>
                  () // Successfully attached and detached
