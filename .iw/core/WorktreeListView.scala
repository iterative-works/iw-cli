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
    * Review state parameter can be:
    * - None: No review state file (no review section shown)
    * - Some(Left(error)): Invalid review state file (error message shown)
    * - Some(Right(state)): Valid review state (artifacts shown if present)
    *
    * @param worktreesWithData List of tuples (worktree, optional issue data with cache flag and stale flag, optional progress, optional git status, optional PR data, optional review state result)
    * @param now Current timestamp for relative time formatting
    * @param sshHost SSH hostname for Zed editor remote connections
    * @return HTML fragment
    */
  def render(
    worktreesWithData: List[(WorktreeRegistration, Option[(IssueData, Boolean, Boolean)], Option[WorkflowProgress], Option[GitStatus], Option[PullRequestData], Option[Either[String, ReviewState]])],
    now: Instant,
    sshHost: String
  ): Frag =
    if worktreesWithData.isEmpty then
      div(
        cls := "empty-state",
        p("No worktrees registered yet")
      )
    else
      div(
        cls := "worktree-list",
        worktreesWithData.map { case (wt, issueData, progress, gitStatus, prData, reviewStateResult) =>
          renderWorktreeCard(wt, issueData, progress, gitStatus, prData, reviewStateResult, now, sshHost)
        }
      )

  private def renderWorktreeCard(
    worktree: WorktreeRegistration,
    issueData: Option[(IssueData, Boolean, Boolean)],
    progress: Option[WorkflowProgress],
    gitStatus: Option[GitStatus],
    prData: Option[PullRequestData],
    reviewStateResult: Option[Either[String, ReviewState]],
    now: Instant,
    sshHost: String
  ): Frag =
    issueData match
      case None =>
        // Skeleton card for cache miss
        renderSkeletonCard(worktree, progress, gitStatus, prData, reviewStateResult, now)
      case Some((data, fromCache, isStale)) =>
        renderNormalCard(worktree, data, fromCache, isStale, progress, gitStatus, prData, reviewStateResult, now, sshHost)

  private def renderSkeletonCard(
    worktree: WorktreeRegistration,
    progress: Option[WorkflowProgress],
    gitStatus: Option[GitStatus],
    prData: Option[PullRequestData],
    reviewStateResult: Option[Either[String, ReviewState]],
    now: Instant
  ): Frag =
    div(
      cls := "worktree-card skeleton-card",
      id := s"worktree-${worktree.issueId}",
      attr("hx-get") := s"/worktrees/${worktree.issueId}/card",
      attr("hx-trigger") := "load delay:1s, every 30s, refresh from:body",
      attr("hx-swap") := "outerHTML transition:true",
      // Issue ID as non-clickable placeholder
      h3(cls := "skeleton-title", "Loading..."),
      p(
        cls := "issue-id",
        span(worktree.issueId)
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
      // Last activity
      p(
        cls := "last-activity",
        s"Last activity: ${formatRelativeTime(worktree.lastSeenAt, now)}"
      )
    )

  private def renderNormalCard(
    worktree: WorktreeRegistration,
    data: IssueData,
    fromCache: Boolean,
    isStale: Boolean,
    progress: Option[WorkflowProgress],
    gitStatus: Option[GitStatus],
    prData: Option[PullRequestData],
    reviewStateResult: Option[Either[String, ReviewState]],
    now: Instant,
    sshHost: String
  ): Frag =
    div(
      cls := "worktree-card",
      id := s"worktree-${worktree.issueId}",
      attr("hx-get") := s"/worktrees/${worktree.issueId}/card",
      attr("hx-trigger") := "load delay:1s, every 30s, refresh from:body",
      attr("hx-swap") := "outerHTML transition:true",
      // Issue title
      h3(data.title),
      // Issue ID as clickable link
      p(
        cls := "issue-id",
        a(
          href := data.url,
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
      // Zed editor button
      div(
        cls := "zed-link",
        a(
          cls := "zed-button",
          href := s"zed://ssh/$sshHost${worktree.path}",
          attr("title") := "Open in Zed",
          img(
            src := "https://raw.githubusercontent.com/zed-industries/zed/main/crates/zed/resources/app-icon.png",
            alt := "Zed",
            attr("width") := "18",
            attr("height") := "18"
          )
        )
      ),
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
      // Issue details (status, assignee, cache indicator, stale indicator)
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
          (),
        // Stale indicator (if data is stale)
        if isStale then
          span(
            cls := "stale-indicator",
            s" · stale"
          )
        else
          ()
      ),
      // Review artifacts section (based on review state result)
      reviewStateResult match {
        case None =>
          // No review state file - don't show anything
          ()
        case Some(Left(error)) =>
          // Invalid review state file - show error message
          div(
            cls := "review-artifacts review-error",
            h4("Review Artifacts"),
            p(cls := "review-error-message", "⚠ Review state unavailable"),
            p(cls := "review-error-detail", "The review state file exists but could not be loaded. Check for JSON syntax errors.")
          )
        case Some(Right(state)) if state.artifacts.nonEmpty =>
          // Valid review state with artifacts - show them
          div(
            cls := "review-artifacts",
            // Header with phase number (if available)
            h4(
              "Review Artifacts",
              state.phase.map { phaseNum =>
                span(cls := "review-phase", s" (Phase $phaseNum)")
              }
            ),
            // Status badge (if available)
            state.status.map { statusValue =>
              div(
                cls := s"review-status ${statusBadgeClass(statusValue)}",
                span(cls := "review-status-label", formatStatusLabel(statusValue))
              )
            },
            // Message (if available)
            state.message.map { msg =>
              p(cls := "review-message", msg)
            },
            // Artifacts list
            ul(
              cls := "artifact-list",
              state.artifacts.map { artifact =>
                li(
                  a(
                    href := s"/worktrees/${worktree.issueId}/artifacts?path=${artifact.path}",
                    artifact.label
                  )
                )
              }
            )
          )
        case Some(Right(state)) =>
          // Valid review state but no artifacts - don't show anything
          ()
      },
      // Update timestamp
      p(
        cls := "update-timestamp",
        TimestampFormatter.formatUpdateTimestamp(data.fetchedAt, now)
      ),
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
  def statusClass(status: String): String =
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
  def formatCacheAge(fetchedAt: Instant, now: Instant): String =
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
  def formatRelativeTime(instant: Instant, now: Instant): String =
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

  /** Map status value to CSS class for badge styling.
    *
    * @param status Status string from review-state.json
    * @return CSS class name (e.g., "review-status-awaiting-review")
    */
  def statusBadgeClass(status: String): String =
    status.toLowerCase.replace(" ", "-") match
      case "awaiting_review" | "awaiting-review" => "review-status-awaiting-review"
      case "in_progress" | "in-progress" => "review-status-in-progress"
      case "completed" | "complete" => "review-status-completed"
      case _ => "review-status-default"

  /** Format status value as human-readable label.
    *
    * @param status Status string from review-state.json
    * @return Formatted label (e.g., "Awaiting Review")
    */
  def formatStatusLabel(status: String): String =
    status.toLowerCase.replace("_", " ").split(" ").map(_.capitalize).mkString(" ")
