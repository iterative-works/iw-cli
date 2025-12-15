// PURPOSE: Opens an existing worktree tmux session, creating session if needed.
// PURPOSE: Infers issue ID from current branch when no parameter given.

import iw.core.*
import java.nio.file.{Files, Paths}

@main def open(args: String*): Unit =
  // Resolve issue ID (from args or current branch)
  val issueIdResult = args.headOption match
    case Some(rawId) => IssueId.parse(rawId)
    case None => inferIssueFromBranch()

  issueIdResult match
    case Left(error) =>
      Output.error(error)
      sys.exit(1)
    case Right(issueId) =>
      openWorktreeSession(issueId)

def inferIssueFromBranch(): Either[String, IssueId] =
  val currentDir = Paths.get(".").toAbsolutePath.normalize
  GitAdapter.getCurrentBranch(currentDir).flatMap(IssueId.fromBranch)

def openWorktreeSession(issueId: IssueId): Unit =
  val configPath = Paths.get(".iw/config.conf")

  // Read project config
  ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("Cannot read configuration")
      Output.info("Run './iw init' to initialize the project")
      sys.exit(1)
    case Some(config) =>
      val currentDir = Paths.get(".").toAbsolutePath.normalize
      val worktreePath = WorktreePath(config.projectName, issueId)
      val targetPath = worktreePath.resolve(currentDir)
      val sessionName = worktreePath.sessionName

      // Check worktree exists
      if !Files.exists(targetPath) then
        Output.error(s"Worktree not found: ${worktreePath.directoryName}")
        Output.info(s"Use './iw start ${issueId.value}' to create a new worktree")
        sys.exit(1)

      // Handle nested tmux scenario
      if TmuxAdapter.isInsideTmux then
        TmuxAdapter.currentSessionName match
          case Some(current) if current == sessionName =>
            Output.info(s"Already in session '$sessionName'")
            sys.exit(0)
          case Some(current) =>
            Output.error(s"Already inside tmux session '$current'")
            Output.info("Detach first with: Ctrl+B, D")
            Output.info(s"Then run: ./iw open ${issueId.value}")
            sys.exit(1)
          case None =>
            Output.error("Inside tmux but cannot determine session name")
            sys.exit(1)

      // Check if session exists, create if not
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
                ()
