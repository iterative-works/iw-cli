// PURPOSE: Unit tests for worktrees list display formatting
// PURPOSE: Tests WorktreesFormatter.format with various field combinations and cache states
package iw.tests

import iw.core.model.WorktreeSummary
import iw.core.output.WorktreesFormatter
import munit.FunSuite

class WorktreesFormatterTest extends FunSuite:

  test("format worktree with all fields"):
    val worktree = WorktreeSummary(
      issueId = "IWLE-123",
      path = "/home/user/testproject-IWLE-123",
      issueTitle = Some("Add user login"),
      issueStatus = Some("In Progress"),
      prState = Some("Open"),
      reviewDisplay = Some("Waiting for review"),
      needsAttention = true
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("IWLE-123"))
    assert(output.contains("Add user login"))
    assert(output.contains("In Progress"))
    assert(output.contains("Open"))
    assert(output.contains("⚠")) // attention indicator

  test("format worktree with no cached data"):
    val worktree = WorktreeSummary(
      issueId = "IWLE-456",
      path = "/home/user/testproject-IWLE-456",
      issueTitle = None,
      issueStatus = None,
      prState = None,
      reviewDisplay = None,
      needsAttention = false
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("IWLE-456"))
    assert(!output.contains("⚠")) // no attention indicator

  test("format worktree with needsAttention shows indicator"):
    val worktree = WorktreeSummary(
      issueId = "IWLE-789",
      path = "/home/user/testproject-IWLE-789",
      issueTitle = Some("Fix bug"),
      issueStatus = Some("Done"),
      prState = None,
      reviewDisplay = None,
      needsAttention = true
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("⚠"))

  test("format worktree without needsAttention omits indicator"):
    val worktree = WorktreeSummary(
      issueId = "IWLE-111",
      path = "/home/user/testproject-IWLE-111",
      issueTitle = Some("Simple task"),
      issueStatus = Some("Todo"),
      prState = None,
      reviewDisplay = None,
      needsAttention = false
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(!output.contains("⚠"))

  test("format multiple worktrees"):
    val worktree1 = WorktreeSummary(
      issueId = "IWLE-123",
      path = "/home/user/testproject-IWLE-123",
      issueTitle = Some("First task"),
      issueStatus = Some("In Progress"),
      prState = None,
      reviewDisplay = None,
      needsAttention = false
    )
    val worktree2 = WorktreeSummary(
      issueId = "IWLE-456",
      path = "/home/user/testproject-IWLE-456",
      issueTitle = Some("Second task"),
      issueStatus = Some("Done"),
      prState = Some("Merged"),
      reviewDisplay = None,
      needsAttention = false
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
    val longTitle = "This is a very long issue title that should be truncated when displayed"
    val worktree = WorktreeSummary(
      issueId = "IWLE-999",
      path = "/home/user/testproject-IWLE-999",
      issueTitle = Some(longTitle),
      issueStatus = Some("In Progress"),
      prState = None,
      reviewDisplay = None,
      needsAttention = false
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("...")) // truncation indicator
    assert(!output.contains(longTitle)) // full title should not appear

  test("format includes section header"):
    val worktree = WorktreeSummary(
      issueId = "IWLE-123",
      path = "/home/user/testproject-IWLE-123",
      issueTitle = Some("Task"),
      issueStatus = Some("Todo"),
      prState = None,
      reviewDisplay = None,
      needsAttention = false
    )

    val output = WorktreesFormatter.format(List(worktree))

    assert(output.contains("Worktrees"))
