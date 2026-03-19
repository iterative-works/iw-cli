// PURPOSE: Lists worktrees for current project or all projects
// PURPOSE: Supports --json and --all flags for machine-readable and cross-project output
// USAGE: iw worktrees [--all] [--json]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def worktrees(args: String*): Unit =
  // Parse arguments
  val jsonFlag = args.contains("--json")
  val allFlag = args.contains("--all")

  // Read state from state.json
  val state = StateReader.read() match
    case Left(error) =>
      Output.error(s"Failed to read state: $error")
      sys.exit(1)
    case Right(s) => s

  // Get all worktrees
  val allWorktrees = state.worktrees.values.toList

  // Filter to current project unless --all flag
  val filteredWorktrees = if allFlag then
    allWorktrees
  else
    // Determine current project's main path
    val currentPath = os.pwd.toString
    val currentMainPath = ProjectPath.deriveMainProjectPath(currentPath).getOrElse(currentPath)

    // Filter worktrees that belong to this project
    allWorktrees.filter { wt =>
      ProjectPath.deriveMainProjectPath(wt.path).getOrElse(wt.path) == currentMainPath
    }

  // Build WorktreeSummary for each worktree
  val worktreeSummaries = filteredWorktrees.map { wt =>
    val issueId = wt.issueId
    val issueEntry = state.issueCache.get(issueId)
    val prEntry = state.prCache.get(issueId)
    val reviewEntry = state.reviewStateCache.get(issueId)
    val progressEntry = state.progressCache.get(issueId)

    val issueTitle = issueEntry.map(_.data.title)
    val issueStatus = issueEntry.map(_.data.status)
    val issueUrl = issueEntry.map(_.data.url)
    val prUrl = prEntry.map(_.pr.url)
    val prState = prEntry.map(_.pr.stateBadgeText)
    val activity = reviewEntry.flatMap(_.state.activity)
    val workflowType = reviewEntry.flatMap(_.state.workflowType)
    val workflowDisplay = reviewEntry.flatMap(_.state.display.map(_.text))
    val needsAttention = reviewEntry.flatMap(_.state.needsAttention).getOrElse(false)
    val currentPhase = progressEntry.flatMap(_.progress.currentPhase)
    val totalPhases = progressEntry.map(_.progress.totalPhases)
    val completedTasks = progressEntry.map(_.progress.overallCompleted)
    val totalTasks = progressEntry.map(_.progress.overallTotal)
    val registeredAt = Some(wt.registeredAt.toString)
    val lastActivityAt = Some(wt.lastSeenAt.toString)

    WorktreeSummary(
      issueId = issueId,
      path = wt.path,
      issueTitle = issueTitle,
      issueStatus = issueStatus,
      issueUrl = issueUrl,
      prUrl = prUrl,
      prState = prState,
      activity = activity,
      workflowType = workflowType,
      workflowDisplay = workflowDisplay,
      needsAttention = needsAttention,
      currentPhase = currentPhase,
      totalPhases = totalPhases,
      completedTasks = completedTasks,
      totalTasks = totalTasks,
      registeredAt = registeredAt,
      lastActivityAt = lastActivityAt
    )
  }.sortBy(_.issueId)

  // Output
  if jsonFlag then
    println(upickle.default.write(worktreeSummaries))
  else
    println(WorktreesFormatter.format(worktreeSummaries))
