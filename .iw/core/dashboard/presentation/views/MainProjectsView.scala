// PURPOSE: Presentation layer for rendering main projects section with create buttons
// PURPOSE: Generates HTML for main project cards showing tracker info and per-project worktree creation

package iw.core.dashboard.presentation.views

import iw.core.dashboard.domain.MainProject
import scalatags.Text.all.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object MainProjectsView:
  /** Render main projects section with create buttons.
    *
    * @param summaries List of project summaries with worktree counts
    * @return HTML fragment
    */
  def render(summaries: List[ProjectSummary]): Frag =
    if summaries.isEmpty then
      div(
        cls := "empty-state main-projects-empty",
        h3("No main projects found"),
        p("Run './iw start <issue-id>' from a project directory to register it")
      )
    else
      div(
        cls := "main-projects-section",
        h2("Main Projects"),
        div(
          cls := "main-projects-list",
          summaries.map(renderProjectCard)
        )
      )

  private def renderProjectCard(summary: ProjectSummary): Frag =
    val project = summary.project
    // URL-encode the project path for use in query parameters
    val encodedPath = URLEncoder.encode(project.path.toString, StandardCharsets.UTF_8.toString)

    // Determine worktree count text (singular vs plural)
    val worktreeText = if summary.worktreeCount == 1 then "1 worktree" else s"${summary.worktreeCount} worktrees"

    div(
      cls := "main-project-card",
      // Project name linking to project details page
      a(
        href := s"/projects/${project.projectName}",
        h3(project.projectName)
      ),
      // Tracker info
      div(
        cls := "project-info",
        span(cls := "tracker-type", formatTrackerType(project.trackerType)),
        project.trackerUrl match
          case Some(url) =>
            a(cls := "team-info", href := url, target := "_blank", project.team)
          case None =>
            span(cls := "team-info", project.team)
      ),
      // Worktree count
      div(
        cls := "worktree-count",
        worktreeText
      ),
      // Attention indicator (only shown if > 0)
      if summary.attentionCount > 0 then
        div(
          cls := "attention-count",
          s"${summary.attentionCount} needs attention"
        )
      else
        frag(),
      // Create worktree button
      button(
        cls := "create-worktree-button",
        attr("hx-get") := s"/api/modal/create-worktree?project=$encodedPath",
        attr("hx-target") := "#modal-container",
        attr("hx-swap") := "innerHTML",
        "+ Create"
      )
    )

  /** Format tracker type for display.
    *
    * @param trackerType Tracker type string (lowercase)
    * @return Formatted tracker type (capitalized)
    */
  private def formatTrackerType(trackerType: String): String =
    trackerType.capitalize match
      case "Github" => "GitHub"
      case "Youtrack" => "YouTrack"
      case other => other
