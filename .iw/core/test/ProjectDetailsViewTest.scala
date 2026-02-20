// PURPOSE: Unit tests for ProjectDetailsView presentation layer
// PURPOSE: Verifies rendering of project details page with filtered worktree cards

package iw.tests

import iw.core.model.{WorktreeRegistration, IssueData}
import iw.core.dashboard.domain.MainProject
import iw.core.dashboard.presentation.views.ProjectDetailsView
import java.time.Instant
import munit.FunSuite

class ProjectDetailsViewTest extends FunSuite:
  test("render includes project name in heading"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )

    val html = ProjectDetailsView.render(
      "iw-cli",
      project,
      List.empty,
      Instant.now(),
      "localhost"
    ).render

    assert(html.contains("iw-cli"), "Should contain project name in heading")
    assert(html.contains("<h1"), "Should have h1 heading")

  test("render includes breadcrumb with link to root"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )

    val html = ProjectDetailsView.render(
      "iw-cli",
      project,
      List.empty,
      Instant.now(),
      "localhost"
    ).render

    assert(html.contains("Projects"), "Should have breadcrumb with 'Projects'")
    assert(html.contains("href=\"/\""), "Should link to root /")
    assert(html.contains("breadcrumb") || html.contains("nav"), "Should have breadcrumb or nav element")

  test("render includes tracker type in metadata"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )

    val html = ProjectDetailsView.render(
      "iw-cli",
      project,
      List.empty,
      Instant.now(),
      "localhost"
    ).render

    assert(html.contains("GitHub") || html.contains("github"), "Should display tracker type")

  test("render includes team info in metadata"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )

    val html = ProjectDetailsView.render(
      "iw-cli",
      project,
      List.empty,
      Instant.now(),
      "localhost"
    ).render

    assert(html.contains("iterative-works/iw-cli"), "Should display team info")

  test("render includes tracker URL link when available"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      trackerUrl = Some("https://github.com/iterative-works/iw-cli/issues")
    )

    val html = ProjectDetailsView.render(
      "iw-cli",
      project,
      List.empty,
      Instant.now(),
      "localhost"
    ).render

    assert(html.contains("href=\"https://github.com/iterative-works/iw-cli/issues\""), "Should include tracker URL as link")
    assert(html.contains("target=\"_blank\""), "Should open in new tab")

  test("render includes worktree cards for matching worktrees"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )

    val worktree = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val issueData = IssueData(
      id = "IW-79",
      title = "Test Issue",
      status = "In Progress",
      assignee = Some("testuser"),
      description = None,
      url = "https://github.com/test/test/issues/79",
      fetchedAt = Instant.now()
    )

    val worktreesWithData = List(
      (worktree, Some((issueData, false, false)), None, None, None, None)
    )

    val html = ProjectDetailsView.render(
      "iw-cli",
      project,
      worktreesWithData,
      Instant.now(),
      "localhost"
    ).render

    assert(html.contains("IW-79"), "Should include issue ID")
    assert(html.contains("Test Issue"), "Should include issue title")
    assert(html.contains("worktree-card"), "Should have worktree card class")

  test("render shows empty state when no worktrees"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )

    val html = ProjectDetailsView.render(
      "iw-cli",
      project,
      List.empty,
      Instant.now(),
      "localhost"
    ).render

    assert(html.contains("No worktrees") || html.contains("empty"), "Should show empty state message")

  test("render cards have HTMX polling attributes"):
    val project = MainProject(
      path = os.Path("/home/user/projects/iw-cli"),
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli"
    )

    val worktree = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val issueData = IssueData(
      id = "IW-79",
      title = "Test Issue",
      status = "In Progress",
      assignee = Some("testuser"),
      description = None,
      url = "https://github.com/test/test/issues/79",
      fetchedAt = Instant.now()
    )

    val worktreesWithData = List(
      (worktree, Some((issueData, false, false)), None, None, None, None)
    )

    val html = ProjectDetailsView.render(
      "iw-cli",
      project,
      worktreesWithData,
      Instant.now(),
      "localhost"
    ).render

    assert(html.contains("hx-get=\"/worktrees/IW-79/card\""), "Should have hx-get attribute for polling")
    assert(html.contains("hx-trigger") && html.contains("30s"), "Should have hx-trigger with polling interval")

  test("renderNotFound includes project name"):
    val html = ProjectDetailsView.renderNotFound("nonexistent-project").render

    assert(html.contains("nonexistent-project"), "Should include the project name")

  test("renderNotFound includes link back to overview"):
    val html = ProjectDetailsView.renderNotFound("nonexistent-project").render

    assert(html.contains("href=\"/\""), "Should include link to root overview")

  test("renderNotFound includes breadcrumb"):
    val html = ProjectDetailsView.renderNotFound("nonexistent-project").render

    assert(html.contains("breadcrumb") || html.contains("Projects"), "Should include breadcrumb or Projects link")
