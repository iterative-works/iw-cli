// PURPOSE: Unit tests for MainProjectsView presentation layer
// PURPOSE: Verifies rendering of main projects section with create buttons

package iw.tests

import iw.core.dashboard.domain.MainProject
import iw.core.dashboard.presentation.views.{MainProjectsView, ProjectSummary}
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
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

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

    val summary1 = ProjectSummary(project1, worktreeCount = 0, attentionCount = 0)
    val summary2 = ProjectSummary(project2, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary1, summary2)).render

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
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

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
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

    assert(html.contains("GitHub"))
    assert(html.contains("owner/repo"))

  test("displays tracker type correctly for Linear"):
    val project = MainProject(
      path = os.Path("/home/user/projects/test"),
      projectName = "test",
      trackerType = "linear",
      team = "TEAM"
    )
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

    assert(html.contains("Linear"))
    assert(html.contains("TEAM"))

  test("displays tracker type correctly for YouTrack"):
    val project = MainProject(
      path = os.Path("/home/user/projects/test"),
      projectName = "test",
      trackerType = "youtrack",
      team = "PROJECT"
    )
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

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
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

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
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

    assert(html.contains("<span class=\"team-info\">owner/repo</span>"))
    assert(!html.contains("<a class=\"team-info\""))

  test("project name links to project details page"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

    assert(html.contains("href=\"/projects/iw-cli\""), "Should link to /projects/iw-cli")

  test("project name link wraps the project heading"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

    assert(html.contains("<a ") && html.contains("iw-cli</h3>"), "Link should wrap the h3 heading")

  test("create button still present alongside project link"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

    assert(html.contains("create-worktree-button"), "Create button should still be present")
    assert(html.contains("/api/modal/create-worktree"), "Create button should have modal URL")

  test("render with summary showing worktree count text"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )
    val summary = ProjectSummary(project, worktreeCount = 3, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

    assert(html.contains("3 worktrees"))

  test("render with zero worktrees shows 0 worktrees"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )
    val summary = ProjectSummary(project, worktreeCount = 0, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

    assert(html.contains("0 worktrees"))

  test("render with attention count > 0 shows attention indicator"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )
    val summary = ProjectSummary(project, worktreeCount = 3, attentionCount = 1)

    val html = MainProjectsView.render(List(summary)).render

    assert(html.contains("1 needs attention"))

  test("render with attention count == 0 does not show attention indicator"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )
    val summary = ProjectSummary(project, worktreeCount = 3, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

    assert(!html.contains("needs attention"))

  test("render with single worktree shows singular text"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )
    val summary = ProjectSummary(project, worktreeCount = 1, attentionCount = 0)

    val html = MainProjectsView.render(List(summary)).render

    assert(html.contains("1 worktree"))
    assert(!html.contains("1 worktrees"))
