// PURPOSE: Unit tests for MainProjectsView presentation layer
// PURPOSE: Verifies rendering of main projects section with create buttons

package iw.tests

import iw.core.dashboard.domain.MainProject
import iw.core.dashboard.presentation.views.MainProjectsView
import iw.core.dashboard.domain.*
import iw.core.dashboard.presentation.views.MainProjectsView
import munit.FunSuite

class MainProjectsViewTest extends FunSuite:
  test("render with empty projects list shows empty state"):
    val html = MainProjectsView.render(List.empty).render

    assert(html.contains("No main projects found"))
    assert(html.contains("./iw start"))

  test("render with single project shows project card"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )

    val html = MainProjectsView.render(List(project)).render

    assert(html.contains("iw-cli"))
    assert(html.contains("GitHub"))
    assert(html.contains("iterative-works/iw-cli"))
    assert(html.contains("Create"))

  test("render with multiple projects shows all project cards"):
    val project1 = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )

    val project2 = MainProject(
      path = os.Path("/home/user/projects/kanon"),
      projectName = "kanon",
      trackerType = "linear",
      team = "IWLE"
    )

    val html = MainProjectsView.render(List(project1, project2)).render

    assert(html.contains("iw-cli"))
    assert(html.contains("kanon"))
    assert(html.contains("GitHub"))
    assert(html.contains("Linear"))

  test("create button includes correct project path parameter"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )

    val html = MainProjectsView.render(List(project)).render

    // Should include project path in modal URL (URL-encoded)
    assert(html.contains("/api/modal/create-worktree"))
    assert(html.contains("project=%2Fhome%2Fuser%2Fprojects%2Fiw-cli"))

  test("displays tracker type correctly for GitHub"):
    val project = MainProject(
      path = os.Path("/home/user/projects/test"),
      projectName = "test",
      trackerType = "github",
      team = "owner/repo"
    )

    val html = MainProjectsView.render(List(project)).render

    assert(html.contains("GitHub"))
    assert(html.contains("owner/repo"))

  test("displays tracker type correctly for Linear"):
    val project = MainProject(
      path = os.Path("/home/user/projects/test"),
      projectName = "test",
      trackerType = "linear",
      team = "TEAM"
    )

    val html = MainProjectsView.render(List(project)).render

    assert(html.contains("Linear"))
    assert(html.contains("TEAM"))

  test("displays tracker type correctly for YouTrack"):
    val project = MainProject(
      path = os.Path("/home/user/projects/test"),
      projectName = "test",
      trackerType = "youtrack",
      team = "PROJECT"
    )

    val html = MainProjectsView.render(List(project)).render

    assert(html.contains("YouTrack"))
    assert(html.contains("PROJECT"))

  test("team renders as link when trackerUrl is provided"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      trackerUrl = Some("https://github.com/iterative-works/iw-cli/issues")
    )

    val html = MainProjectsView.render(List(project)).render

    assert(html.contains("<a"), "Should render as link")
    assert(html.contains("href=\"https://github.com/iterative-works/iw-cli/issues\""))
    assert(html.contains("target=\"_blank\""), "Should open in new tab")
    assert(html.contains("iterative-works/iw-cli"))

  test("team renders as span when trackerUrl is None"):
    val project = MainProject(
      path = os.Path("/home/user/projects/test"),
      projectName = "test",
      trackerType = "github",
      team = "owner/repo",
      trackerUrl = None
    )

    val html = MainProjectsView.render(List(project)).render

    assert(html.contains("<span class=\"team-info\">owner/repo</span>"))
    assert(!html.contains("<a class=\"team-info\""))
