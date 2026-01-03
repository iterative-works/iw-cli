// PURPOSE: Unit tests for WorktreeCreationResult domain value object
// PURPOSE: Verifies construction and field validation of creation result

package iw.core.domain

import munit.FunSuite

class WorktreeCreationResultTest extends FunSuite:

  test("WorktreeCreationResult construction with valid fields succeeds"):
    val result = WorktreeCreationResult(
      issueId = "IW-79",
      worktreePath = "/home/user/projects/iw-cli-IW-79",
      tmuxSessionName = "iw-cli-IW-79",
      tmuxAttachCommand = "tmux attach -t iw-cli-IW-79"
    )

    assertEquals(result.issueId, "IW-79")
    assertEquals(result.worktreePath, "/home/user/projects/iw-cli-IW-79")
    assertEquals(result.tmuxSessionName, "iw-cli-IW-79")
    assertEquals(result.tmuxAttachCommand, "tmux attach -t iw-cli-IW-79")

  test("WorktreeCreationResult fields are accessible"):
    val result = WorktreeCreationResult(
      "IWLE-123",
      "/path/to/worktree",
      "session-name",
      "tmux attach -t session-name"
    )

    assert(result.issueId.nonEmpty)
    assert(result.worktreePath.nonEmpty)
    assert(result.tmuxSessionName.nonEmpty)
    assert(result.tmuxAttachCommand.nonEmpty)

  test("WorktreeCreationResult with different issue IDs are distinct"):
    val result1 = WorktreeCreationResult(
      "IW-1",
      "/path1",
      "session1",
      "tmux attach -t session1"
    )
    val result2 = WorktreeCreationResult(
      "IW-2",
      "/path2",
      "session2",
      "tmux attach -t session2"
    )

    assertNotEquals(result1, result2)
    assertNotEquals(result1.issueId, result2.issueId)
