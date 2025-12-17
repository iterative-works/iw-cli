// PURPOSE: Unit tests for WorktreePath value object for directory naming
// PURPOSE: Tests directory name calculation, path resolution, and session naming
package iw.tests

import iw.core.*
import munit.FunSuite

class WorktreePathTest extends FunSuite:

  test("WorktreePath.directoryName combines project name and issue ID"):
    val issueId = IssueId.parse("IWLE-123").getOrElse(fail("Failed to parse valid issue ID"))
    val worktreePath = WorktreePath("kanon", issueId)
    assertEquals(worktreePath.directoryName, "kanon-IWLE-123")

  test("WorktreePath.directoryName uses exact issue ID format"):
    val issueId = IssueId.parse("ABC-99").getOrElse(fail("Failed to parse valid issue ID"))
    val worktreePath = WorktreePath("myproject", issueId)
    assertEquals(worktreePath.directoryName, "myproject-ABC-99")

  test("WorktreePath.directoryName handles long project names"):
    val issueId = IssueId.parse("PROJ-456").getOrElse(fail("Failed to parse valid issue ID"))
    val worktreePath = WorktreePath("very-long-project-name", issueId)
    assertEquals(worktreePath.directoryName, "very-long-project-name-PROJ-456")

  test("WorktreePath.resolve creates sibling path"):
    val issueId = IssueId.parse("IWLE-123").getOrElse(fail("Failed to parse valid issue ID"))
    val worktreePath = WorktreePath("kanon", issueId)
    val currentDir = os.Path("/home/user/projects/kanon")
    val resolved = worktreePath.resolve(currentDir)
    assertEquals(resolved.toString, "/home/user/projects/kanon-IWLE-123")

  test("WorktreePath.resolve handles different current paths"):
    val issueId = IssueId.parse("ABC-1").getOrElse(fail("Failed to parse valid issue ID"))
    val worktreePath = WorktreePath("myproject", issueId)
    val currentDir = os.Path("/opt/code/myproject")
    val resolved = worktreePath.resolve(currentDir)
    assertEquals(resolved.toString, "/opt/code/myproject-ABC-1")

  test("WorktreePath.resolve handles absolute paths correctly"):
    val issueId = IssueId.parse("TEST-999").getOrElse(fail("Failed to parse valid issue ID"))
    val worktreePath = WorktreePath("test", issueId)
    val currentDir = os.Path("/a/b/c/test")
    val resolved = worktreePath.resolve(currentDir)
    assertEquals(resolved.toString, "/a/b/c/test-TEST-999")

  test("WorktreePath.sessionName matches directory name"):
    val issueId = IssueId.parse("IWLE-123").getOrElse(fail("Failed to parse valid issue ID"))
    val worktreePath = WorktreePath("kanon", issueId)
    assertEquals(worktreePath.sessionName, worktreePath.directoryName)

  test("WorktreePath.sessionName is consistent"):
    val issueId = IssueId.parse("ABC-99").getOrElse(fail("Failed to parse valid issue ID"))
    val worktreePath = WorktreePath("myproject", issueId)
    assertEquals(worktreePath.sessionName, "myproject-ABC-99")
