// PURPOSE: Worktrees command logic: list worktrees from server state, format text or JSON
// PURPOSE: All I/O via CommandEnv (StateReader). Filter to current project unless --all.

package iw.core.commands

import iw.core.model.{ProjectPath, ServerState, WorktreeSummary}
import iw.core.output.WorktreesFormatter

object Worktrees:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val jsonFlag = args.contains("--json")
    val allFlag = args.contains("--all")

    env.state.read() match
      case Left(err) =>
        env.console.err(s"Error: Failed to read state: $err")
        CommandResult.error
      case Right(serverState) =>
        val summaries = build(serverState, allFlag, env.cwd)
        if jsonFlag then env.console.out(upickle.default.write(summaries))
        else env.console.out(WorktreesFormatter.format(summaries))
        CommandResult.ok

  private def build(
      state: ServerState,
      allFlag: Boolean,
      cwd: os.Path
  ): List[WorktreeSummary] =
    val all = state.worktrees.values.toList
    val filtered =
      if allFlag then all
      else
        val currentMain =
          ProjectPath
            .deriveMainProjectPath(cwd.toString)
            .getOrElse(cwd.toString)
        all.filter(wt =>
          ProjectPath.deriveMainProjectPath(wt.path).getOrElse(wt.path) ==
            currentMain
        )
    filtered
      .map { wt =>
        val issueEntry = state.issueCache.get(wt.issueId)
        val prEntry = state.prCache.get(wt.issueId)
        val reviewEntry = state.reviewStateCache.get(wt.issueId)
        val progressEntry = state.progressCache.get(wt.issueId)
        WorktreeSummary(
          issueId = wt.issueId,
          path = wt.path,
          issueTitle = issueEntry.map(_.data.title),
          issueStatus = issueEntry.map(_.data.status),
          issueUrl = issueEntry.map(_.data.url),
          prUrl = prEntry.map(_.pr.url),
          prState = prEntry.map(_.pr.stateBadgeText),
          activity = reviewEntry.flatMap(_.state.activity),
          workflowType = reviewEntry.flatMap(_.state.workflowType),
          workflowDisplay = reviewEntry.flatMap(_.state.display.map(_.text)),
          needsAttention =
            reviewEntry.flatMap(_.state.needsAttention).getOrElse(false),
          currentPhase = progressEntry.flatMap(_.progress.currentPhase),
          totalPhases = progressEntry.map(_.progress.totalPhases),
          completedTasks = progressEntry.map(_.progress.overallCompleted),
          totalTasks = progressEntry.map(_.progress.overallTotal),
          registeredAt = Some(wt.registeredAt.toString),
          lastActivityAt = Some(wt.lastSeenAt.toString)
        )
      }
      .sortBy(_.issueId)
