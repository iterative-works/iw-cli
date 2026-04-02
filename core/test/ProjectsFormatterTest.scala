// PURPOSE: Unit tests for projects list display formatting
// PURPOSE: Tests ProjectsFormatter.format with various project configurations
package iw.tests

import iw.core.model.ProjectSummary
import iw.core.output.ProjectsFormatter
import munit.FunSuite

class ProjectsFormatterTest extends FunSuite:

  test("format single project shows all fields"):
    val project = ProjectSummary(
      name = "testproject",
      path = "/home/user/testproject",
      trackerType = "linear",
      team = "IWLE",
      worktreeCount = 1
    )

    val output = ProjectsFormatter.format(List(project))

    assert(output.contains("testproject"))
    assert(output.contains("/home/user/testproject"))
    assert(output.contains("linear"))
    assert(output.contains("IWLE"))
    assert(output.contains("1 worktree"))
    assert(!output.contains("1 worktrees")) // Should be singular

  test("format multiple projects shows all projects"):
    val project1 = ProjectSummary(
      name = "testproject",
      path = "/home/user/testproject",
      trackerType = "linear",
      team = "IWLE",
      worktreeCount = 2
    )
    val project2 = ProjectSummary(
      name = "kanon",
      path = "/home/user/kanon",
      trackerType = "github",
      team = "KANO",
      worktreeCount = 1
    )

    val output = ProjectsFormatter.format(List(project1, project2))

    assert(output.contains("testproject"))
    assert(output.contains("kanon"))
    assert(output.contains("linear"))
    assert(output.contains("github"))

  test("format project with multiple worktrees pluralizes"):
    val project = ProjectSummary(
      name = "testproject",
      path = "/home/user/testproject",
      trackerType = "linear",
      team = "IWLE",
      worktreeCount = 3
    )

    val output = ProjectsFormatter.format(List(project))

    assert(output.contains("3 worktrees"))
    assert(
      !output.contains("worktree\n")
    ) // Should not end line with singular form

  test("format project with zero worktrees"):
    val project = ProjectSummary(
      name = "testproject",
      path = "/home/user/testproject",
      trackerType = "linear",
      team = "IWLE",
      worktreeCount = 0
    )

    val output = ProjectsFormatter.format(List(project))

    assert(output.contains("0 worktrees"))

  test("format empty list shows no projects message"):
    val output = ProjectsFormatter.format(List.empty)

    assert(output.contains("No projects registered"))

  test("format includes section header"):
    val project = ProjectSummary(
      name = "testproject",
      path = "/home/user/testproject",
      trackerType = "linear",
      team = "IWLE",
      worktreeCount = 1
    )

    val output = ProjectsFormatter.format(List(project))

    assert(output.contains("Registered Projects"))
