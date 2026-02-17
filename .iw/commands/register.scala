// PURPOSE: Registers current worktree with the dashboard server
// PURPOSE: Auto-detects issue ID from current branch and sends registration to dashboard
// USAGE: iw register

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*
import iw.core.dashboard.ServerClient

@main def register(args: String*): Unit =
  val currentDir = os.pwd

  // Check we're in a git repository first
  val branch = GitAdapter.getCurrentBranch(currentDir) match
    case Left(error) =>
      Output.error(error)
      sys.exit(1)
    case Right(b) => b

  // Load config
  val configPath = currentDir / Constants.Paths.IwDir / "config.conf"
  val config = ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("Cannot read configuration")
      Output.info("Run './iw init' to initialize the project")
      sys.exit(1)
    case Some(c) => c

  // Parse issue ID from branch
  val issueId = IssueId.fromBranch(branch) match
    case Left(error) =>
      Output.error(error)
      sys.exit(1)
    case Right(id) => id

  // Register worktree with dashboard (best-effort - warn on failure, don't error)
  ServerClient.registerWorktree(
    issueId.value,
    currentDir.toString,
    config.trackerType.toString,
    issueId.team
  ) match
    case Left(error) =>
      Output.warning(s"Failed to register worktree with dashboard: $error")
    case Right(_) =>
      () // Silent success for dashboard call

  // Output success message
  Output.success(s"Registered worktree for ${issueId.value} at ${currentDir}")
