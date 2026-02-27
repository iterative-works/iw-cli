// PURPOSE: Formatter for displaying worktrees list
// PURPOSE: Provides format method to create human-readable worktrees overview output

package iw.core.output

import iw.core.model.WorktreeSummary

object WorktreesFormatter:
  def format(worktrees: List[WorktreeSummary]): String =
    if worktrees.isEmpty then
      "No worktrees found."
    else
      val header = "=== Worktrees ==="
      val lines = worktrees.map { wt =>
        val title = wt.issueTitle.map(truncate(_, 40)).getOrElse("")
        val status = wt.issueStatus.getOrElse("")
        val prSection = wt.prState.map(state => s"  PR: $state").getOrElse("")
        val attention = if wt.needsAttention then "  ⚠ Needs attention" else ""

        val parts = List(
          wt.issueId.padTo(12, ' '),
          title.padTo(42, ' '),
          status.padTo(15, ' '),
          prSection,
          attention
        ).filter(_.trim.nonEmpty)

        parts.mkString(" ")
      }

      (header :: "" :: lines).mkString("\n")

  private def truncate(text: String, maxLength: Int): String =
    if text.length <= maxLength then text
    else text.take(maxLength - 3) + "..."
