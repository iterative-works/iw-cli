// PURPOSE: Status command logic: resolve issue id from arg or branch, fetch from server, format
// PURPOSE: All I/O via CommandEnv (GitOps + ServerOps + FileSystem)

package iw.core.commands

import iw.core.model.{ConfigSerializer, Constants, IssueId, IssueTrackerType}
import iw.core.output.StatusFormatter

object Status:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val (issueIdArg, jsonFlag) =
      args.foldLeft[(Option[String], Boolean)]((None, false)) {
        case ((id, json), "--json")                       => (id, true)
        case ((None, json), arg) if !arg.startsWith("--") => (Some(arg), json)
        case (acc, _)                                     => acc
      }

    val configPath = env.cwd / Constants.Paths.IwDir / "config.conf"
    val configOpt =
      if !env.fs.exists(configPath) then None
      else env.fs.read(configPath).flatMap(ConfigSerializer.fromHocon).toOption

    val issueIdResult = issueIdArg match
      case Some(rawId) =>
        val teamPrefix = configOpt.flatMap { config =>
          config.trackerType match
            case IssueTrackerType.GitHub | IssueTrackerType.GitLab |
                IssueTrackerType.YouTrack =>
              config.teamPrefix
            case _ => None
        }
        IssueId.parse(rawId, teamPrefix)
      case None =>
        env.git.getCurrentBranch(env.cwd).flatMap(IssueId.fromBranch(_))

    issueIdResult match
      case Left(err) =>
        env.console.err(s"Error: $err")
        env.console.out("Usage: iw status [issue-id] [--json]")
        CommandResult.error
      case Right(id) =>
        env.server.getWorktreeStatus(id.value) match
          case Left(err) =>
            env.console.err(s"Error: $err")
            CommandResult.error
          case Right(status) =>
            if jsonFlag then env.console.out(upickle.default.write(status))
            else env.console.out(StatusFormatter.format(status))
            CommandResult.ok
