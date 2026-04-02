// PURPOSE: Unit tests for ProjectPath pure function
// PURPOSE: Verifies path derivation logic for extracting main project paths from worktree paths

package iw.tests

import iw.core.model.ProjectPath
import munit.FunSuite

class ProjectPathTest extends FunSuite:
  test("deriveMainProjectPath strips standard issue ID suffix"):
    val worktreePath = "/home/user/projects/iw-cli-IW-79"
    val result = ProjectPath.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/home/user/projects/iw-cli"))

  test("deriveMainProjectPath handles LINEAR issue format"):
    val worktreePath = "/home/user/projects/kanon-IWLE-123"
    val result = ProjectPath.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/home/user/projects/kanon"))

  test("deriveMainProjectPath handles GitHub issue format"):
    val worktreePath = "/opt/code/myproject-123"
    val result = ProjectPath.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/opt/code/myproject"))

  test("deriveMainProjectPath handles multi-digit issue numbers"):
    val worktreePath = "/home/projects/foo-ABC-9999"
    val result = ProjectPath.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/home/projects/foo"))

  test("deriveMainProjectPath handles project names with hyphens"):
    val worktreePath = "/home/user/projects/my-long-project-name-IW-79"
    val result = ProjectPath.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/home/user/projects/my-long-project-name"))

  test("deriveMainProjectPath returns None for path without issue ID"):
    val worktreePath = "/home/user/projects/just-a-directory"
    val result = ProjectPath.deriveMainProjectPath(worktreePath)
    assertEquals(result, None)

  test("deriveMainProjectPath returns None for path with only project name"):
    val worktreePath = "/home/user/projects/iw-cli"
    val result = ProjectPath.deriveMainProjectPath(worktreePath)
    assertEquals(result, None)

  test("deriveMainProjectPath handles single letter team prefix"):
    val worktreePath = "/home/user/projects/foo-A-123"
    val result = ProjectPath.deriveMainProjectPath(worktreePath)
    assertEquals(result, Some("/home/user/projects/foo"))
