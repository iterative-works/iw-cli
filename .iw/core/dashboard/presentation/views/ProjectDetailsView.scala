// PURPOSE: Presentation layer for rendering project details page with filtered worktree cards
// PURPOSE: Displays project metadata, breadcrumb navigation, and worktree cards for a single project

package iw.core.dashboard.presentation.views

import iw.core.model.{WorktreeRegistration, IssueData, WorkflowProgress, GitStatus, PullRequestData, ReviewState}
import iw.core.dashboard.domain.MainProject
import scalatags.Text.all.*
import scalatags.Text.tags2.nav
import java.time.Instant
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ProjectDetailsView:
  /** Render project details page with filtered worktree cards.
    *
    * @param projectName Name of the project
    * @param mainProject Main project metadata
    * @param worktreesWithData List of tuples (worktree, optional issue data, optional progress, optional git status, optional PR data, optional review state)
    * @param now Current timestamp for relative time formatting
    * @param sshHost SSH hostname for Zed editor links
    * @return Scalatags Frag for the page body content
    */
  def render(
    projectName: String,
    mainProject: MainProject,
    worktreesWithData: List[(WorktreeRegistration, Option[(IssueData, Boolean, Boolean)], Option[WorkflowProgress], Option[GitStatus], Option[PullRequestData], Option[Either[String, ReviewState]])],
    now: Instant,
    sshHost: String
  ): Frag =
    val encodedPath = URLEncoder.encode(mainProject.path.toString, StandardCharsets.UTF_8.toString)
    div(
      cls := "project-details",
      // Breadcrumb navigation
      nav(
        cls := "breadcrumb",
        a(href := "/", "Projects"),
        span(" > "),
        span(projectName)
      ),
      // Project metadata header
      div(
        cls := "project-header",
        h1(projectName),
        div(
          cls := "project-metadata",
          // Tracker type badge
          span(
            cls := "tracker-type-badge",
            capitalizeTrackerType(mainProject.trackerType)
          ),
          // Team info (with optional tracker URL link)
          mainProject.trackerUrl match
            case Some(url) =>
              a(
                cls := "team-info",
                href := url,
                target := "_blank",
                mainProject.team
              )
            case None =>
              span(cls := "team-info", mainProject.team)
        ),
        // Create worktree button scoped to this project
        button(
          cls := "create-worktree-button",
          attr("hx-get") := s"/api/modal/create-worktree?project=$encodedPath",
          attr("hx-target") := "#modal-container",
          attr("hx-swap") := "innerHTML",
          "+ Create Worktree"
        )
      ),
      // Worktree cards section
      if worktreesWithData.isEmpty then
        div(
          cls := "empty-state",
          p("No worktrees for this project yet")
        )
      else
        div(
          id := "worktree-list",
          cls := "worktree-list",
          attr("hx-get") := s"/api/projects/$projectName/worktrees/changes",
          attr("hx-vals") := "js:{have: [...document.querySelectorAll('#worktree-list > [id^=\"card-\"]')].map(e => e.id.replace('card-', '')).join(',')}",
          attr("hx-trigger") := "every 30s",
          attr("hx-swap") := "none",
          worktreesWithData.map { case (wt, issueData, progress, gitStatus, prData, reviewStateResult) =>
            renderWorktreeCard(wt, issueData, progress, gitStatus, prData, reviewStateResult, now, sshHost)
          }
        ),
      // Modal container for create worktree modal (populated by HTMX)
      div(id := "modal-container")
    )

  /** Render a single worktree card.
    *
    * @param worktree Worktree registration
    * @param issueData Optional issue data with cache flag and stale flag
    * @param progress Optional workflow progress
    * @param gitStatus Optional git status
    * @param prData Optional PR data
    * @param reviewStateResult Optional review state result
    * @param now Current timestamp
    * @param sshHost SSH hostname
    * @return Scalatags Frag
    */
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
        // Skeleton card (data not yet loaded)
        WorktreeCardRenderer.renderSkeletonCard(
          worktree,
          gitStatus,
          now,
          HtmxCardConfig.dashboard
        )
      case Some((data, fromCache, isStale)) =>
        // Full card with data
        WorktreeCardRenderer.renderCard(
          worktree,
          data,
          fromCache,
          isStale,
          progress,
          gitStatus,
          prData,
          reviewStateResult,
          now,
          sshHost,
          HtmxCardConfig.dashboard
        )

  /** Capitalize first letter of tracker type for display.
    *
    * @param trackerType Tracker type string (e.g., "github", "linear")
    * @return Capitalized tracker type (e.g., "GitHub", "Linear")
    */
  /** Render a not-found page for an unknown project name.
    *
    * @param projectName The project name that was requested but not found
    * @return Scalatags Frag for the not-found page body content
    */
  def renderNotFound(projectName: String): Frag =
    div(
      cls := "project-details",
      // Breadcrumb navigation
      nav(
        cls := "breadcrumb",
        a(href := "/", "Projects"),
        span(" > "),
        span(projectName)
      ),
      div(
        cls := "empty-state",
        h2("Project Not Found"),
        p(s"No worktrees are registered for project '$projectName'."),
        p(
          a(href := "/", "Back to Projects Overview")
        )
      )
    )

  private def capitalizeTrackerType(trackerType: String): String =
    trackerType.toLowerCase match
      case "github" => "GitHub"
      case "linear" => "Linear"
      case "youtrack" => "YouTrack"
      case "gitlab" => "GitLab"
      case other => other.capitalize
