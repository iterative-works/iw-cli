// PURPOSE: Presentation layer for rendering the worktree detail page
// PURPOSE: Full-page view showing all available context for a single worktree

package iw.core.dashboard.presentation.views

import iw.core.model.{WorktreeRegistration, IssueData, WorkflowProgress, GitStatus, PullRequestData, ReviewState}
import scalatags.Text.all.*
import scalatags.Text.tags2.nav
import java.time.Instant
import iw.core.dashboard.WorktreeListView

object WorktreeDetailView:
  /** Render the full worktree detail page body content.
    *
    * @param worktree Worktree registration
    * @param issueData Optional issue data with cache flag and stale flag; None = skeleton state
    * @param progress Optional workflow progress
    * @param gitStatus Optional git status
    * @param prData Optional PR data
    * @param reviewStateResult Optional review state (Left = error message, Right = valid state)
    * @param projectName Optional project name derived from worktree path
    * @param now Current timestamp for relative time formatting
    * @param sshHost SSH hostname for Zed editor links
    * @return Scalatags Frag for the page body content
    */
  def render(
    worktree: WorktreeRegistration,
    issueData: Option[(IssueData, Boolean, Boolean)],
    progress: Option[WorkflowProgress],
    gitStatus: Option[GitStatus],
    prData: Option[PullRequestData],
    reviewStateResult: Option[Either[String, ReviewState]],
    projectName: Option[String],
    now: Instant,
    sshHost: String
  ): Frag =
    div(
      cls := "worktree-detail",
      renderBreadcrumb(worktree.issueId, projectName),
      div(
        cls := "worktree-detail-content",
        attr("hx-get") := s"/worktrees/${worktree.issueId}/detail-content",
        attr("hx-trigger") := "every 30s, refresh from:body",
        attr("hx-swap") := "innerHTML",
        renderContent(worktree, issueData, progress, gitStatus, prData, reviewStateResult, now, sshHost)
      )
    )

  /** Render only the content section of the worktree detail page, without breadcrumb or page shell.
    *
    * Used by the HTMX polling endpoint to return refreshed content that replaces the
    * inner HTML of the content wrapper div on the full page.
    *
    * @param worktree Worktree registration
    * @param issueData Optional issue data with cache flag and stale flag; None = skeleton state
    * @param progress Optional workflow progress
    * @param gitStatus Optional git status
    * @param prData Optional PR data
    * @param reviewStateResult Optional review state (Left = error message, Right = valid state)
    * @param now Current timestamp for relative time formatting
    * @param sshHost SSH hostname for Zed editor links
    * @return Scalatags Frag for the content section only
    */
  def renderContent(
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
        renderSkeleton(worktree, gitStatus, now)
      case Some((data, fromCache, isStale)) =>
        renderFull(worktree, data, fromCache, isStale, progress, gitStatus, prData, reviewStateResult, now, sshHost)

  /** Render a not-found page for an unknown worktree issue ID.
    *
    * @param issueId The issue ID that was requested but not found
    * @return Scalatags Frag for the not-found page body content
    */
  def renderNotFound(issueId: String): Frag =
    div(
      cls := "worktree-detail",
      nav(
        cls := "breadcrumb",
        a(href := "/", "Projects"),
        span(" > "),
        span(issueId)
      ),
      div(
        cls := "empty-state",
        h2("Worktree Not Found"),
        p(s"Worktree '$issueId' is not registered."),
        p("Run './iw register' from the worktree directory to register it."),
        p(
          a(href := "/", "Back to Projects Overview")
        )
      )
    )

  private def renderBreadcrumb(issueId: String, projectName: Option[String]): Frag =
    projectName match
      case Some(name) =>
        nav(
          cls := "breadcrumb",
          a(href := "/", "Projects"),
          span(" > "),
          a(href := s"/projects/$name", name),
          span(" > "),
          span(issueId)
        )
      case None =>
        nav(
          cls := "breadcrumb",
          a(href := "/", "Projects"),
          span(" > "),
          span(issueId)
        )

  private def renderSkeleton(
    worktree: WorktreeRegistration,
    gitStatus: Option[GitStatus],
    now: Instant
  ): Frag =
    frag(
      h1(cls := "skeleton-title", "Loading..."),
      p(cls := "issue-id", span(worktree.issueId)),
      // Git status section (may be available before issue data)
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
      p(
        cls := "last-activity",
        s"Last activity: ${WorktreeListView.formatRelativeTime(worktree.lastSeenAt, now)}"
      )
    )

  private def renderFull(
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
    frag(
      // Header: issue title and issue ID link
      h1(data.title),
      p(
        cls := "issue-id",
        a(href := data.url, target := "_blank", worktree.issueId)
      ),
      // Issue metadata: status, assignee, cache indicators
      div(
        cls := "issue-details",
        span(
          cls := s"status-badge status-${WorktreeListView.statusClass(data.status)}",
          data.status
        ),
        data.assignee.map(assignee =>
          span(cls := "assignee", s" · Assigned: $assignee")
        ),
        if fromCache then
          span(
            cls := "cache-indicator",
            s" · cached ${WorktreeListView.formatCacheAge(data.fetchedAt, now)}"
          )
        else
          (),
        if isStale then
          span(cls := "stale-indicator", " · stale")
        else
          ()
      ),
      // Git status section
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
      // Workflow progress section
      progress.flatMap(p => p.currentPhaseInfo.map(info => (p, info))).map { case (prog, phaseInfo) =>
        div(
          cls := "phase-info",
          span(
            cls := "phase-label",
            s"Phase ${phaseInfo.phaseNumber}/${prog.totalPhases}: ${phaseInfo.phaseName}"
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
      // Pull request section
      prData.map { pr =>
        div(
          cls := "pr-link",
          a(
            cls := "pr-button",
            href := pr.url,
            target := "_blank",
            s"PR #${pr.number}"
          ),
          span(
            cls := s"pr-badge ${pr.stateBadgeClass}",
            pr.stateBadgeText
          )
        )
      },
      // Zed editor link
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
      // Review artifacts section
      WorktreeCardRenderer.renderReviewArtifacts(worktree.issueId, reviewStateResult)
    )
