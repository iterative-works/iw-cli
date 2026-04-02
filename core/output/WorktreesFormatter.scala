// PURPOSE: Formatter for displaying worktrees list
// PURPOSE: Provides format method to create human-readable worktrees overview output

package iw.core.output

import iw.core.model.WorktreeSummary

object WorktreesFormatter:
  def format(worktrees: List[WorktreeSummary]): String =
    if worktrees.isEmpty then "No worktrees found."
    else
      val header = "=== Worktrees ==="
      val lines = worktrees.map { wt =>
        val title = wt.issueTitle.map(truncate(_, 40)).getOrElse("")
        val status = wt.issueStatus.getOrElse("")
        val prSection = wt.prState.map(state => s"  PR: $state").getOrElse("")
        val attention = if wt.needsAttention then "  ⚠ Needs attention" else ""
        val activityIndicator = wt.activity match
          case Some("working") => "  ▶"
          case Some("waiting") => "  ⏸"
          case _               => ""
        val workflowAbbrev = wt.workflowType match
          case Some("agile")      => "  AG"
          case Some("waterfall")  => "  WF"
          case Some("diagnostic") => "  DX"
          case _                  => ""
        val phaseProgress = (wt.currentPhase, wt.totalPhases) match
          case (Some(current), Some(total)) => s"  Phase $current/$total"
          case _                            => ""
        val taskProgress = (wt.completedTasks, wt.totalTasks) match
          case (Some(completed), Some(total)) => s"  $completed/$total tasks"
          case _                              => ""

        val parts = List(
          wt.issueId.padTo(12, ' '),
          title.padTo(42, ' '),
          status.padTo(15, ' '),
          prSection,
          activityIndicator,
          workflowAbbrev,
          phaseProgress,
          taskProgress,
          attention
        ).filter(_.trim.nonEmpty)

        parts.mkString(" ")
      }

      (header :: "" :: lines).mkString("\n")

  private def truncate(text: String, maxLength: Int): String =
    if text.length <= maxLength then text
    else text.take(maxLength - 3) + "..."
