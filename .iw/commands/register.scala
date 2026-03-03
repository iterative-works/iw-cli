// PURPOSE: Registers current directory with the dashboard server
// PURPOSE: Context-aware: registers worktree+project from issue branch, or project alone from main branch

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

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

  // Determine context from branch: issue worktree or main project directory
  IssueId.fromBranch(branch) match
    case Left(_) =>
      // Main project directory (not an issue branch) — register the project
      val trackerUrl = TrackerUrlBuilder.buildTrackerUrl(config)
      ServerClient.registerProject(
        config.projectName,
        currentDir.toString,
        config.trackerType.toString,
        config.teamIdentifier,
        trackerUrl
      ) match
        case Left(error) =>
          Output.error(s"Failed to register project with dashboard: $error")
          sys.exit(1)
        case Right(_) =>
          Output.success(s"Registered project '${config.projectName}' at ${currentDir}")

    case Right(issueId) =>
      // Issue worktree — register the worktree
      ServerClient.registerWorktree(
        issueId.value,
        currentDir.toString,
        config.trackerType.toString,
        issueId.team
      ) match
        case Left(error) =>
          Output.error(s"Failed to register worktree with dashboard: $error")
          sys.exit(1)
        case Right(_) =>
          // Also register the parent project (best-effort)
          ProjectPath.deriveMainProjectPath(currentDir.toString) match
            case None =>
              Output.warning("Could not derive parent project path from current directory")
            case Some(parentPath) =>
              val parentConfigPath = os.Path(parentPath) / Constants.Paths.IwDir / "config.conf"
              ConfigFileRepository.read(parentConfigPath) match
                case None =>
                  Output.warning(s"Could not read parent project config at $parentConfigPath, skipping project registration")
                case Some(parentConfig) =>
                  val trackerUrl = TrackerUrlBuilder.buildTrackerUrl(parentConfig)
                  ServerClient.registerProject(
                    parentConfig.projectName,
                    parentPath,
                    parentConfig.trackerType.toString,
                    parentConfig.teamIdentifier,
                    trackerUrl
                  ) match
                    case Left(error) =>
                      Output.warning(s"Failed to register parent project with dashboard: $error")
                    case Right(_) =>
                      () // Silent success for parent project dashboard call

          Output.success(s"Registered worktree for ${issueId.value} at ${currentDir}")
