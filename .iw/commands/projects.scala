// PURPOSE: Lists all registered projects from server state
// PURPOSE: Supports --json flag for machine-readable output
// USAGE: iw projects [--json]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def projects(args: String*): Unit =
  // Parse arguments
  val jsonFlag = args.contains("--json")

  // Read state from state.json
  val state = StateReader.read() match
    case Left(error) =>
      Output.error(s"Failed to read state: $error")
      sys.exit(1)
    case Right(s) => s

  // Group worktrees by main project path
  val worktreesByProject = state.worktrees.values
    .groupBy { wt =>
      ProjectPath.deriveMainProjectPath(wt.path).getOrElse(wt.path)
    }

  // Build ProjectSummary for each group
  val projectSummaries = worktreesByProject.map { case (mainPath, worktrees) =>
    // Derive project name from path
    val projectName = mainPath.split('/').lastOption.getOrElse(mainPath)

    // Try to read project config
    val configPath = os.Path(mainPath) / Constants.Paths.IwDir / "config.conf"
    val (trackerType, team) = ConfigFileRepository.read(configPath) match
      case Some(config) =>
        val teamStr = config.trackerType match
          case IssueTrackerType.Linear =>
            config.teamPrefix.getOrElse("unknown")
          case IssueTrackerType.GitHub | IssueTrackerType.GitLab | IssueTrackerType.YouTrack =>
            config.teamPrefix.getOrElse("unknown")
        (config.trackerType.toString.toLowerCase, teamStr)
      case None =>
        ("unknown", "unknown")

    ProjectSummary(
      name = projectName,
      path = mainPath,
      trackerType = trackerType,
      team = team,
      worktreeCount = worktrees.size
    )
  }.toList.sortBy(_.name)

  // Output
  if jsonFlag then
    println(upickle.default.write(projectSummaries))
  else
    println(ProjectsFormatter.format(projectSummaries))
