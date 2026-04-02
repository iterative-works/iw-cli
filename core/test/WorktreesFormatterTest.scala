// PURPOSE: Unit tests for worktrees list display formatting
// PURPOSE: Tests WorktreesFormatter.format with various field combinations and cache states
package iw.tests

import iw.core.model.WorktreeSummary
import iw.core.output.WorktreesFormatter
import munit.FunSuite

class WorktreesFormatterTest extends FunSuite:

  private def minimalWorktree(issueId: String): WorktreeSummary =
    WorktreeSummary(
      issueId = issueId,
      path = s"/home/user/testproject-$issueId",
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      activity = None,
      workflowType = None,
      workflowDisplay = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      completedTasks = None,
      totalTasks = None,
      registeredAt = None,
      lastActivityAt = None
    )

  test("format worktree with all fields"):
    val worktree = minimalWorktree("IWLE-123").copy(
      issueTitle = Some("Add user login"),
      issueStatus = Some("In Progress"),
      prState = Some("Open"),
      workflowDisplay = Some("Waiting for review"),
      needsAttention = true
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("IWLE-123"))
    assert(output.contains("Add user login"))
    assert(output.contains("In Progress"))
    assert(output.contains("Open"))
    assert(output.contains("⚠")) // attention indicator

  test("format worktree with no cached data"):
    val worktree = minimalWorktree("IWLE-456")

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("IWLE-456"))
    assert(!output.contains("⚠")) // no attention indicator

  test("format worktree with needsAttention shows indicator"):
    val worktree = minimalWorktree("IWLE-789").copy(
      issueTitle = Some("Fix bug"),
      issueStatus = Some("Done"),
      needsAttention = true
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("⚠"))

  test("format worktree without needsAttention omits indicator"):
    val worktree = minimalWorktree("IWLE-111").copy(
      issueTitle = Some("Simple task"),
      issueStatus = Some("Todo")
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(!output.contains("⚠"))

  test("format multiple worktrees"):
    val worktree1 = minimalWorktree("IWLE-123").copy(
      issueTitle = Some("First task"),
      issueStatus = Some("In Progress")
    )
    val worktree2 = minimalWorktree("IWLE-456").copy(
      issueTitle = Some("Second task"),
      issueStatus = Some("Done"),
      prState = Some("Merged")
    )

    val output = WorktreesFormatter.format(List(worktree1, worktree2))

    assert(output.contains("IWLE-123"))
    assert(output.contains("IWLE-456"))
    assert(output.contains("First task"))
    assert(output.contains("Second task"))

  test("format empty list shows no worktrees message"):
    val output = WorktreesFormatter.format(List.empty)

    assert(output.contains("No worktrees found"))

  test("format truncates long issue titles"):
    val longTitle =
      "This is a very long issue title that should be truncated when displayed"
    val worktree = minimalWorktree("IWLE-999").copy(
      issueTitle = Some(longTitle),
      issueStatus = Some("In Progress")
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("...")) // truncation indicator
    assert(!output.contains(longTitle)) // full title should not appear

  test("format includes section header"):
    val worktree = minimalWorktree("IWLE-123").copy(
      issueTitle = Some("Task"),
      issueStatus = Some("Todo")
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("Worktrees"))

  test("format worktree with activity working shows indicator"):
    val worktree = minimalWorktree("IWLE-200").copy(
      activity = Some("working")
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("▶"))

  test("format worktree with activity waiting shows indicator"):
    val worktree = minimalWorktree("IWLE-201").copy(
      activity = Some("waiting")
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("⏸"))

  test("format worktree with workflow type agile shows abbreviation"):
    val worktree = minimalWorktree("IWLE-210").copy(
      workflowType = Some("agile")
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("AG"))

  test("format worktree with workflow type waterfall shows abbreviation"):
    val worktree = minimalWorktree("IWLE-211").copy(
      workflowType = Some("waterfall")
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("WF"))

  test("format worktree with workflow type diagnostic shows abbreviation"):
    val worktree = minimalWorktree("IWLE-212").copy(
      workflowType = Some("diagnostic")
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("DX"))

  test("format worktree with phase progress shows progress"):
    val worktree = minimalWorktree("IWLE-220").copy(
      currentPhase = Some(2),
      totalPhases = Some(4)
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("Phase 2/4"))

  test("format worktree with task progress shows progress"):
    val worktree = minimalWorktree("IWLE-221").copy(
      completedTasks = Some(5),
      totalTasks = Some(12)
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("5/12 tasks"))

  test("format worktree with all new fields combines them"):
    val worktree = minimalWorktree("IWLE-230").copy(
      activity = Some("working"),
      workflowType = Some("waterfall"),
      currentPhase = Some(3),
      totalPhases = Some(5),
      completedTasks = Some(8),
      totalTasks = Some(20)
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("▶"))
    assert(output.contains("WF"))
    assert(output.contains("Phase 3/5"))
    assert(output.contains("8/20 tasks"))

  test("format worktree with no new fields shows original format"):
    val worktree = minimalWorktree("IWLE-240").copy(
      issueTitle = Some("Basic task"),
      issueStatus = Some("In Progress")
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("IWLE-240"))
    assert(output.contains("Basic task"))
    assert(output.contains("In Progress"))
    assert(!output.contains("▶"))
    assert(!output.contains("⏸"))
    assert(!output.contains("Phase"))
    assert(!output.contains("tasks"))
