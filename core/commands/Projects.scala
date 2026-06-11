// PURPOSE: Projects command logic: list registered projects derived from server state
// PURPOSE: Reads .iw/config.conf for each worktree-derived project to surface tracker info

package iw.core.commands

import iw.core.model.{
  ConfigSerializer,
  Constants,
  IssueTrackerType,
  ProjectPath,
  ProjectSummary,
  ServerState,
  WorktreeRegistration
}
import iw.core.output.ProjectsFormatter

object Projects:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val jsonFlag = args.contains("--json")

    env.state.read() match
      case Left(err) =>
        env.console.err(s"Error: Failed to read state: $err")
        CommandResult.error
      case Right(state) =>
        val summaries = build(state, env.fs)
        if jsonFlag then env.console.out(upickle.default.write(summaries))
        else env.console.out(ProjectsFormatter.format(summaries))
        CommandResult.ok

  private def build(state: ServerState, fs: FileSystem): List[ProjectSummary] =
    val worktreesByProject =
      state.worktrees.values.groupBy { wt =>
        ProjectPath.deriveMainProjectPath(wt.path).getOrElse(wt.path)
      }

    val worktreeSummaries =
      worktreesByProject.map { case (mainPath, worktrees) =>
        val projectName = mainPath.split('/').lastOption.getOrElse(mainPath)
        val (trackerType, team) = readTrackerInfo(mainPath, fs)
        ProjectSummary(
          name = projectName,
          path = mainPath,
          trackerType = trackerType,
          team = team,
          worktreeCount = worktrees.size
        )
      }

    val worktreePaths = worktreeSummaries.map(_.path).toSet
    val registeredSummaries =
      state.projects.values
        .filterNot(reg => worktreePaths.contains(reg.path))
        .map { reg =>
          ProjectSummary(
            name = reg.projectName,
            path = reg.path,
            trackerType = reg.trackerType,
            team = reg.team,
            worktreeCount = 0
          )
        }

    (worktreeSummaries ++ registeredSummaries).toList.sortBy(_.name)

  private def readTrackerInfo(
      mainPath: String,
      fs: FileSystem
  ): (String, String) =
    val configPath = os.Path(mainPath) / Constants.Paths.IwDir / "config.conf"
    if !fs.exists(configPath) then ("unknown", "unknown")
    else
      fs.read(configPath).flatMap(ConfigSerializer.fromHocon) match
        case Right(config) =>
          val team = config.teamPrefix.getOrElse("unknown")
          (config.trackerType.toString.toLowerCase, team)
        case Left(_) => ("unknown", "unknown")
