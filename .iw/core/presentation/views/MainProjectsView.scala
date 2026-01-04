// PURPOSE: Presentation layer for rendering main projects section with create buttons
// PURPOSE: Generates HTML for main project cards showing tracker info and per-project worktree creation

package iw.core.presentation.views

import iw.core.domain.MainProject
import scalatags.Text.all.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object MainProjectsView:
  /** Render main projects section with create buttons.
    *
    * @param projects List of main projects derived from worktrees
    * @return HTML fragment
    */
  def render(projects: List[MainProject]): Frag =
    if projects.isEmpty then
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
          projects.map(renderProjectCard)
        )
      )

  private def renderProjectCard(project: MainProject): Frag =
    // URL-encode the project path for use in query parameters
    val encodedPath = URLEncoder.encode(project.path.toString, StandardCharsets.UTF_8.toString)

    div(
      cls := "main-project-card",
      // Project name
      h3(project.projectName),
      // Tracker info
      div(
        cls := "project-info",
        span(cls := "tracker-type", formatTrackerType(project.trackerType)),
        span(cls := "team-info", project.team)
      ),
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
