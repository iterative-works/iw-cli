// PURPOSE: Presentation layer for rendering worktree cards with Scalatags
// PURPOSE: Generates HTML for worktree list with issue ID, title placeholder, and relative timestamps

package iw.core.presentation.views

import iw.core.domain.WorktreeRegistration
import scalatags.Text.all.*
import java.time.Instant
import java.time.Duration

object WorktreeListView:
  def render(worktrees: List[WorktreeRegistration]): Frag =
    if worktrees.isEmpty then
      div(
        cls := "empty-state",
        p("No worktrees registered yet")
      )
    else
      div(
        cls := "worktree-list",
        worktrees.map(renderWorktreeCard)
      )

  private def renderWorktreeCard(worktree: WorktreeRegistration): Frag =
    div(
      cls := "worktree-card",
      h3(worktree.issueId),
      p(cls := "title", "Issue title not yet loaded"),
      p(cls := "last-activity", s"Last activity: ${formatRelativeTime(worktree.lastSeenAt)}")
    )

  private def formatRelativeTime(instant: Instant): String =
    val now = Instant.now()
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
