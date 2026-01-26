// PURPOSE: Presentation layer for rendering worktree list with Scalatags
// PURPOSE: Coordinates card rendering and handles list-level concerns like staggered loading

package iw.core.dashboard

import iw.core.model.{WorktreeRegistration, IssueData, WorkflowProgress, GitStatus, PullRequestData, ReviewState}
import iw.core.dashboard.presentation.views.{WorktreeCardRenderer, HtmxCardConfig}
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
        id := "worktree-list",
        cls := "worktree-list",
        attr("hx-get") := "/api/worktrees/changes",
        attr("hx-vals") := "js:{have: [...document.querySelectorAll('#worktree-list > [id^=\"card-\"]')].map(e => e.id.replace('card-', '')).join(',')}",
        attr("hx-trigger") := "every 30s",
        attr("hx-swap") := "none",
        worktreesWithData.zipWithIndex.map { case ((wt, issueData, progress, gitStatus, prData, reviewStateResult), index) =>
          val position = index + 1 // Position is 1-based
          renderWorktreeCard(wt, issueData, progress, gitStatus, prData, reviewStateResult, now, sshHost, position)
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
    sshHost: String,
    position: Int
  ): Frag =
    issueData match
      case None =>
        // Skeleton card with staggered loading delay
        val delay = calculateDelay(position)
        val skeletonConfig = HtmxCardConfig(
          trigger = s"load delay:$delay, every 30s, refresh from:body",
          swap = "outerHTML transition:true"
        )
        WorktreeCardRenderer.renderSkeletonCard(worktree, gitStatus, now, skeletonConfig)
      case Some((data, fromCache, isStale)) =>
        WorktreeCardRenderer.renderCard(
          worktree, data, fromCache, isStale, progress, gitStatus, prData,
          reviewStateResult, now, sshHost, HtmxCardConfig.dashboard
        )

  /** Calculate HTMX polling delay based on card position.
    *
    * Priority-based staggered delays:
    * - Position 1-3: 500ms (highest priority, refresh first)
    * - Position 4-8: 2s (medium priority)
    * - Position 9+: 5s (lower priority)
    *
    * @param position Card position in list (1-based)
    * @return Delay string for HTMX trigger (e.g., "500ms", "2s", "5s")
    */
  def calculateDelay(position: Int): String =
    position match
      case p if p <= 3 => "500ms"
      case p if p <= 8 => "2s"
      case _ => "5s"

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
