// PURPOSE: Unit tests for MainProject domain model
// PURPOSE: Verifies construction, field access, and path derivation for main projects

package iw.tests

import iw.core.dashboard.domain.MainProject
import munit.FunSuite

class MainProjectTest extends FunSuite:
  test("MainProject construction with all required fields"):
    val path = os.Path("/home/user/projects/iw-cli")
    val projectName = "iw-cli"
    val trackerType = "github"
    val team = "iterative-works/iw-cli"

    val mainProject = MainProject(
      path = path,
      projectName = projectName,
      trackerType = trackerType,
      team = team
    )

    assertEquals(mainProject.path, path)
    assertEquals(mainProject.projectName, projectName)
    assertEquals(mainProject.trackerType, trackerType)
    assertEquals(mainProject.team, team)

  test("MainProject equality for same values"):
    val path = os.Path("/home/user/projects/kanon")
    val project1 = MainProject(path, "kanon", "linear", "IWLE")
    val project2 = MainProject(path, "kanon", "linear", "IWLE")

    assertEquals(project1, project2)

  test("MainProject inequality for different paths"):
    val path1 = os.Path("/home/user/projects/iw-cli")
    val path2 = os.Path("/home/other/projects/iw-cli")
    val project1 = MainProject(path1, "iw-cli", "github", "iterative-works/iw-cli")
    val project2 = MainProject(path2, "iw-cli", "github", "iterative-works/iw-cli")

    assertNotEquals(project1, project2)

  test("MainProject handles different project names"):
    val path1 = os.Path("/home/user/projects/iw-cli")
    val path2 = os.Path("/home/user/projects/kanon")
    val project1 = MainProject(path1, "iw-cli", "github", "iterative-works/iw-cli")
    val project2 = MainProject(path2, "kanon", "linear", "IWLE")

    assertNotEquals(project1, project2)

  test("deriveMainProjectPath strips standard issue ID suffix"):
    val worktreePath = "/home/user/projects/iw-cli-IW-79"
    val result = MainProject.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/home/user/projects/iw-cli"))

  test("deriveMainProjectPath handles LINEAR issue format"):
    val worktreePath = "/home/user/projects/kanon-IWLE-123"
    val result = MainProject.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/home/user/projects/kanon"))

  test("deriveMainProjectPath handles GitHub issue format"):
    val worktreePath = "/opt/code/myproject-123"
    val result = MainProject.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/opt/code/myproject"))

  test("deriveMainProjectPath handles multi-digit issue numbers"):
    val worktreePath = "/home/projects/foo-ABC-9999"
    val result = MainProject.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/home/projects/foo"))

  test("deriveMainProjectPath handles project names with hyphens"):
    val worktreePath = "/home/user/projects/my-long-project-name-IW-79"
    val result = MainProject.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/home/user/projects/my-long-project-name"))

  test("deriveMainProjectPath returns None for path without issue ID"):
    val worktreePath = "/home/user/projects/just-a-directory"
    val result = MainProject.deriveMainProjectPath(worktreePath)
    assertEquals(result, None)

  test("deriveMainProjectPath returns None for path with only project name"):
    val worktreePath = "/home/user/projects/iw-cli"
    val result = MainProject.deriveMainProjectPath(worktreePath)
    assertEquals(result, None)

  test("deriveMainProjectPath handles single letter team prefix"):
    val worktreePath = "/home/user/projects/foo-A-123"
    val result = MainProject.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/home/user/projects/foo"))
