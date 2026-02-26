// PURPOSE: Formatter for displaying detailed worktree status
// PURPOSE: Provides format method to create multi-section status output with conditional sections

package iw.core.output

import iw.core.model.WorktreeStatus

object StatusFormatter:
  def format(status: WorktreeStatus): String =
    val title = status.issueTitle match
      case Some(t) => s"=== ${status.issueId}: $t ==="
      case None => s"=== ${status.issueId} ==="

    val sections = List(
      formatGitSection(status),
      formatIssueSection(status),
      formatPRSection(status),
      formatReviewSection(status),
      formatProgressSection(status)
    ).flatten

    (title :: "" :: sections).mkString("\n")

  private def formatGitSection(status: WorktreeStatus): Option[String] =
    if status.branchName.isEmpty && status.gitClean.isEmpty then
      None
    else
      val lines = List(
        Some("Git"),
        status.branchName.map(b => s"  Branch:     $b"),
        status.gitClean.map { clean =>
          if clean then "  Status:     Clean"
          else "  Status:     Uncommitted changes"
        }
      ).flatten

      Some(lines.mkString("\n"))

  private def formatIssueSection(status: WorktreeStatus): Option[String] =
    if status.issueTitle.isEmpty && status.issueStatus.isEmpty && status.issueUrl.isEmpty then
      None
    else
      val lines = List(
        Some("Issue"),
        status.issueStatus.map(s => s"  Status:     $s"),
        status.issueUrl.map(u => s"  URL:        $u")
      ).flatten

      if lines.size > 1 then Some(lines.mkString("\n"))
      else None

  private def formatPRSection(status: WorktreeStatus): Option[String] =
    if status.prState.isEmpty && status.prUrl.isEmpty && status.prNumber.isEmpty then
      None
    else
      val lines = List(
        Some("Pull Request"),
        status.prState.map(s => s"  State:      $s"),
        status.prNumber.map(n => s"  PR:         #$n"),
        status.prUrl.map(u => s"  URL:        $u")
      ).flatten

      if lines.size > 1 then Some(lines.mkString("\n"))
      else None

  private def formatReviewSection(status: WorktreeStatus): Option[String] =
    if status.reviewDisplay.isEmpty && status.reviewBadges.isEmpty && !status.needsAttention then
      None
    else
      val lines = List(
        Some("Review"),
        status.reviewDisplay.map(d => s"  Status:     $d"),
        status.reviewBadges.flatMap { badges =>
          if badges.nonEmpty then
            Some(s"  Badges:     ${badges.map(b => s"[$b]").mkString(" ")}")
          else
            None
        },
        if status.needsAttention then Some("  ⚠ Needs attention") else None
      ).flatten

      if lines.size > 1 then Some(lines.mkString("\n"))
      else None

  private def formatProgressSection(status: WorktreeStatus): Option[String] =
    if status.currentPhase.isEmpty && status.overallProgress.isEmpty then
      None
    else
      val lines = List(
        Some("Progress"),
        (status.currentPhase, status.totalPhases) match
          case (Some(current), Some(total)) => Some(s"  Phase:      $current/$total")
          case _ => None
        ,
        status.overallProgress.map(p => s"  Overall:    $p%")
      ).flatten

      if lines.size > 1 then Some(lines.mkString("\n"))
      else None
