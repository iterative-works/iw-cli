// PURPOSE: Unit tests for Create Worktree button on project details page
// PURPOSE: Verifies button rendering, HTMX attributes, and modal container presence

package iw.tests

import iw.core.dashboard.domain.MainProject
import iw.core.dashboard.presentation.views.ProjectDetailsView
import java.time.Instant
import munit.FunSuite

class ProjectDetailsCreateButtonTest extends FunSuite:
  private val project = MainProject(
    path = os.Path("/home/user/projects/iw-cli"),
    projectName = "iw-cli",
    trackerType = "github",
    team = "iterative-works/iw-cli"
  )

  private def renderHtml(): String =
    ProjectDetailsView.render(
      "iw-cli",
      project,
      List.empty,
      Instant.now(),
      "localhost"
    ).render

  test("render includes create worktree button"):
    val html = renderHtml()
    assert(html.contains("create-worktree-button"), "Should have create-worktree-button class")

  test("render create button has correct hx-get URL with encoded project path"):
    val html = renderHtml()
    // URL-encoded /home/user/projects/iw-cli
    assert(
      html.contains("/api/modal/create-worktree?project="),
      "Should have hx-get pointing to create-worktree modal endpoint"
    )
    assert(
      html.contains("%2Fhome%2Fuser%2Fprojects%2Fiw-cli"),
      "Should contain URL-encoded project path"
    )

  test("render create button targets modal container"):
    val html = renderHtml()
    assert(
      html.contains("hx-target=\"#modal-container\""),
      "Should target #modal-container"
    )

  test("render create button uses innerHTML swap"):
    val html = renderHtml()
    assert(
      html.contains("hx-swap=\"innerHTML\""),
      "Should use innerHTML swap strategy"
    )

  test("render includes modal container div"):
    val html = renderHtml()
    assert(
      html.contains("id=\"modal-container\""),
      "Should have modal-container div"
    )
