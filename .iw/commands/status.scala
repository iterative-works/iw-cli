// PURPOSE: Shows detailed status for a specific worktree
// PURPOSE: Combines live git state with cached issue, PR, review, and progress data
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

  // Read state from state.json
  val state = StateReader.read() match
    case Left(error) =>
      Output.error(s"Failed to read state: $error")
      sys.exit(1)
    case Right(s) => s

  // Find worktree registration
  val worktree = state.worktrees.get(issueId.value) match
    case None =>
      Output.error(s"Worktree not found in state for ${issueId.value}")
      sys.exit(1)
    case Some(wt) => wt

  // Get live git state
  val worktreePath = os.Path(worktree.path)
  val branchName = GitAdapter.getCurrentBranch(worktreePath).toOption
  val gitClean = GitAdapter.hasUncommittedChanges(worktreePath).map(!_).toOption

  // Populate from caches
  val issueTitle = state.issueCache.get(issueId.value).map(_.data.title)
  val issueStatus = state.issueCache.get(issueId.value).map(_.data.status)
  val issueUrl = state.issueCache.get(issueId.value).map(_.data.url)
  val prUrl = state.prCache.get(issueId.value).map(_.pr.url)
  val prState = state.prCache.get(issueId.value).map(_.pr.stateBadgeText)
  val prNumber = state.prCache.get(issueId.value).map(_.pr.number)
  val reviewDisplay = state.reviewStateCache.get(issueId.value).flatMap(_.state.display.map(_.text))
  val reviewBadges = state.reviewStateCache.get(issueId.value).flatMap(_.state.badges.map(_.map(_.label)))
  val needsAttention = state.reviewStateCache.get(issueId.value).flatMap(_.state.needsAttention).getOrElse(false)
  val currentPhase = state.progressCache.get(issueId.value).flatMap(_.progress.currentPhase)
  val totalPhases = state.progressCache.get(issueId.value).map(_.progress.totalPhases)
  val overallProgress = state.progressCache.get(issueId.value).map(_.progress.overallPercentage)

  // Build WorktreeStatus
  val worktreeStatus = WorktreeStatus(
    issueId = issueId.value,
    path = worktree.path,
    branchName = branchName,
    gitClean = gitClean,
    issueTitle = issueTitle,
    issueStatus = issueStatus,
    issueUrl = issueUrl,
    prUrl = prUrl,
    prState = prState,
    prNumber = prNumber,
    reviewDisplay = reviewDisplay,
    reviewBadges = reviewBadges,
    needsAttention = needsAttention,
    currentPhase = currentPhase,
    totalPhases = totalPhases,
    overallProgress = overallProgress
  )

  // Output
  if jsonFlag then
    println(upickle.default.write(worktreeStatus))
  else
    println(StatusFormatter.format(worktreeStatus))
