// PURPOSE: Presentation layer for rendering worktree cards with Scalatags
// PURPOSE: Generates HTML for worktree list with issue ID, title, status badge, and relative timestamps

package iw.core.presentation.views

import iw.core.domain.{WorktreeRegistration, IssueData, WorkflowProgress, GitStatus, PullRequestData, ReviewState}
import scalatags.Text.all.*
import java.time.Instant
import java.time.Duration

object WorktreeListView:
  /** Render worktree list with issue data, progress, git status, PR data, and review state.
    *
    * @param worktreesWithData List of tuples (worktree, optional issue data with cache flag, optional progress, optional git status, optional PR data, optional review state)
    * @param now Current timestamp for relative time formatting
    * @return HTML fragment
    */
  def render(
    worktreesWithData: List[(WorktreeRegistration, Option[(IssueData, Boolean)], Option[WorkflowProgress], Option[GitStatus], Option[PullRequestData], Option[ReviewState])],
    now: Instant
  ): Frag =
    if worktreesWithData.isEmpty then
      div(
        cls := "empty-state",
        p("No worktrees registered yet")
      )
    else
      div(
        cls := "worktree-list",
        worktreesWithData.map { case (wt, issueData, progress, gitStatus, prData, reviewState) =>
          renderWorktreeCard(wt, issueData, progress, gitStatus, prData, reviewState, now)
        }
      )

  private def renderWorktreeCard(
    worktree: WorktreeRegistration,
    issueData: Option[(IssueData, Boolean)],
    progress: Option[WorkflowProgress],
    gitStatus: Option[GitStatus],
    prData: Option[PullRequestData],
    reviewState: Option[ReviewState],
    now: Instant
  ): Frag =
    div(
      cls := "worktree-card",
      // Issue title (or fallback)
      h3(issueData.map(_._1.title).getOrElse("Issue data unavailable")),
      // Issue ID as clickable link
      p(
        cls := "issue-id",
        a(
          href := issueData.map(_._1.url).getOrElse("#"),
          worktree.issueId
        )
      ),
      // Git status section (if available)
      gitStatus.map { status =>
        div(
          cls := "git-status",
          span(cls := "git-branch", s"Branch: ${status.branchName}"),
          span(
            cls := s"git-indicator ${status.statusCssClass}",
            status.statusIndicator
          )
        )
      },
      // PR link section (if available)
      prData.map { pr =>
        div(
          cls := "pr-link",
          a(
            cls := "pr-button",
            href := pr.url,
            target := "_blank",
            "View PR ↗"
          ),
          span(
            cls := s"pr-badge ${pr.stateBadgeClass}",
            pr.stateBadgeText
          )
        )
      },
      // Phase info and progress bar (if available)
      progress.flatMap(_.currentPhaseInfo).map { phaseInfo =>
        div(
          cls := "phase-info",
          span(
            cls := "phase-label",
            s"Phase ${phaseInfo.phaseNumber}/${progress.get.totalPhases}: ${phaseInfo.phaseName}"
          ),
          div(
            cls := "progress-container",
            div(
              cls := "progress-bar",
              attr("style") := s"width: ${phaseInfo.progressPercentage}%"
            ),
            span(
              cls := "progress-text",
              s"${phaseInfo.completedTasks}/${phaseInfo.totalTasks} tasks"
            )
          )
        )
      },
      // Issue details (status, assignee, cache indicator)
      issueData.map { case (data, fromCache) =>
        div(
          cls := "issue-details",
          // Status badge
          span(
            cls := s"status-badge status-${statusClass(data.status)}",
            data.status
          ),
          // Assignee (if present)
          data.assignee.map(a =>
            span(cls := "assignee", s" · Assigned: $a")
          ),
          // Cache indicator (if from cache)
          if fromCache then
            span(
              cls := "cache-indicator",
              s" · cached ${formatCacheAge(data.fetchedAt, now)}"
            )
          else
            ()
        )
      },
      // Review artifacts section (if available)
      reviewState.filter(_.artifacts.nonEmpty).map { state =>
        div(
          cls := "review-artifacts",
          h4("Review Artifacts"),
          ul(
            cls := "artifact-list",
            state.artifacts.map { artifact =>
              li(artifact.label)
            }
          )
        )
      },
      // Last activity
      p(
        cls := "last-activity",
        s"Last activity: ${formatRelativeTime(worktree.lastSeenAt, now)}"
      )
    )

  /** Map status text to CSS class for color coding.
    *
    * @param status Issue status string
    * @return CSS class suffix (e.g., "in-progress", "done")
    */
  private def statusClass(status: String): String =
    status.toLowerCase match
      case s if s.contains("progress") || s.contains("active") => "in-progress"
      case s if s.contains("done") || s.contains("complete") || s.contains("closed") => "done"
      case s if s.contains("blocked") => "blocked"
      case _ => "default"

  /** Format cache age as human-readable string.
    *
    * @param fetchedAt When issue data was fetched
    * @param now Current timestamp
    * @return Formatted string (e.g., "3m ago", "2h ago")
    */
  private def formatCacheAge(fetchedAt: Instant, now: Instant): String =
    val duration = Duration.between(fetchedAt, now)
    val minutes = duration.toMinutes

    if minutes < 1 then
      "just now"
    else if minutes < 60 then
      s"${minutes}m ago"
    else if minutes < 1440 then
      s"${minutes / 60}h ago"
    else
      s"${minutes / 1440}d ago"

  /** Format relative time for worktree activity.
    *
    * @param instant Timestamp to format
    * @param now Current timestamp
    * @return Formatted string (e.g., "2h ago")
    */
  private def formatRelativeTime(instant: Instant, now: Instant): String =
    val duration = Duration.between(instant, now)
    val seconds = duration.getSeconds
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    if days > 0 then
      s"${days}d ago"
    else if hours > 0 then
      s"${hours}h ago"
    else if minutes > 0 then
      s"${minutes}m ago"
    else
      "just now"
