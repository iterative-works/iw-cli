// PURPOSE: Shows detailed status for a specific worktree
// PURPOSE: Queries the dashboard server for live git, review, progress, and cached issue/PR data
// USAGE: iw status [issue-id] [--json]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def status(args: String*): Unit =
  // Parse arguments
  val (issueIdArg, jsonFlag) = args.foldLeft[(Option[String], Boolean)]((None, false)) {
    case ((id, json), "--json") => (id, true)
    case ((None, json), arg) if !arg.startsWith("--") => (Some(arg), json)
    case (acc, _) => acc
  }

  // Load config for team prefix
  val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"
  val configOpt = ConfigFileRepository.read(configPath)

  // Resolve issue ID from args or current branch
  val issueIdResult = issueIdArg match
    case Some(rawId) =>
      // Parse explicit issue ID with team prefix from config
      val teamPrefix = configOpt.flatMap { config =>
        config.trackerType match
          case IssueTrackerType.GitHub | IssueTrackerType.GitLab | IssueTrackerType.YouTrack =>
            config.teamPrefix
          case _ => None
      }
      IssueId.parse(rawId, teamPrefix)
    case None =>
      // Infer from current branch
      GitAdapter.getCurrentBranch(os.pwd).flatMap(IssueId.fromBranch(_))

  val issueId = issueIdResult match
    case Left(error) =>
      Output.error(error)
      Output.info("Usage: iw status [issue-id] [--json]")
      sys.exit(1)
    case Right(id) => id

  // Query the dashboard server for status
  ServerClient.getWorktreeStatus(issueId.value) match
    case Left(error) =>
      Output.error(error)
      sys.exit(1)
    case Right(worktreeStatus) =>
      if jsonFlag then
        println(upickle.default.write(worktreeStatus))
      else
        println(StatusFormatter.format(worktreeStatus))
