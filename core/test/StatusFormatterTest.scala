// PURPOSE: Unit tests for worktree status display formatting
// PURPOSE: Tests StatusFormatter.format with various field combinations and section visibility
package iw.tests

import iw.core.model.WorktreeStatus
import iw.core.output.StatusFormatter
import munit.FunSuite

class StatusFormatterTest extends FunSuite:

  test("format status with all fields populated"):
    val status = WorktreeStatus(
      issueId = "IWLE-123",
      path = "/home/user/testproject-IWLE-123",
      branchName = Some("IWLE-123"),
      gitClean = Some(true),
      issueTitle = Some("Add user login"),
      issueStatus = Some("In Progress"),
      issueUrl = Some("https://linear.app/team/IWLE-123"),
      prUrl = Some("https://github.com/org/repo/pull/42"),
      prState = Some("Open"),
      prNumber = Some(42),
      reviewDisplay = Some("Waiting for review"),
      reviewBadges = Some(List("Phase 2", "Tests passing")),
      needsAttention = true,
      currentPhase = Some(2),
      totalPhases = Some(4),
      overallProgress = Some(65)
    )

    val output = StatusFormatter.format(status)

    assert(output.contains("IWLE-123"))
    assert(output.contains("Add user login"))
    assert(output.contains("Git"))
    assert(output.contains("Branch"))
    assert(output.contains("Clean"))
    assert(output.contains("Issue"))
    assert(output.contains("In Progress"))
    assert(output.contains("Pull Request"))
    assert(output.contains("Open"))
    assert(output.contains("#42"))
    assert(output.contains("Review"))
    assert(output.contains("Waiting for review"))
    assert(output.contains("Phase 2"))
    assert(output.contains("Tests passing"))
    assert(output.contains("⚠"))
    assert(output.contains("Progress"))
    assert(output.contains("2/4"))
    assert(output.contains("65%"))

  test("format status with only required fields"):
    val status = WorktreeStatus(
      issueId = "IWLE-456",
      path = "/home/user/testproject-IWLE-456",
      branchName = None,
      gitClean = None,
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = None,
      reviewBadges = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val output = StatusFormatter.format(status)

    assert(output.contains("IWLE-456"))
    assert(!output.contains("Git"))
    assert(!output.contains("Issue"))
    assert(!output.contains("Pull Request"))
    assert(!output.contains("Review"))
    assert(!output.contains("Progress"))

  test("format status with git section"):
    val status = WorktreeStatus(
      issueId = "IWLE-789",
      path = "/home/user/testproject-IWLE-789",
      branchName = Some("IWLE-789"),
      gitClean = Some(true),
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = None,
      reviewBadges = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val output = StatusFormatter.format(status)

    assert(output.contains("Git"))
    assert(output.contains("Branch"))
    assert(output.contains("IWLE-789"))
    assert(output.contains("Clean"))

  test("format status with dirty git"):
    val status = WorktreeStatus(
      issueId = "IWLE-111",
      path = "/home/user/testproject-IWLE-111",
      branchName = Some("IWLE-111"),
      gitClean = Some(false),
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = None,
      reviewBadges = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val output = StatusFormatter.format(status)

    assert(output.contains("Git"))
    assert(output.contains("Uncommitted changes"))

  test("format status with issue section"):
    val status = WorktreeStatus(
      issueId = "IWLE-222",
      path = "/home/user/testproject-IWLE-222",
      branchName = None,
      gitClean = None,
      issueTitle = Some("Fix authentication bug"),
      issueStatus = Some("Done"),
      issueUrl = Some("https://linear.app/team/IWLE-222"),
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = None,
      reviewBadges = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val output = StatusFormatter.format(status)

    assert(output.contains("Issue"))
    assert(output.contains("Done"))
    assert(output.contains("https://linear.app/team/IWLE-222"))

  test("format status with PR section"):
    val status = WorktreeStatus(
      issueId = "IWLE-333",
      path = "/home/user/testproject-IWLE-333",
      branchName = None,
      gitClean = None,
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = Some("https://github.com/org/repo/pull/99"),
      prState = Some("Merged"),
      prNumber = Some(99),
      reviewDisplay = None,
      reviewBadges = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val output = StatusFormatter.format(status)

    assert(output.contains("Pull Request"))
    assert(output.contains("Merged"))
    assert(output.contains("#99"))
    assert(output.contains("https://github.com/org/repo/pull/99"))

  test("format status with review badges"):
    val status = WorktreeStatus(
      issueId = "IWLE-444",
      path = "/home/user/testproject-IWLE-444",
      branchName = None,
      gitClean = None,
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = Some("Approved"),
      reviewBadges = Some(List("Phase 2", "Tests passing")),
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val output = StatusFormatter.format(status)

    assert(output.contains("Review"))
    assert(output.contains("Approved"))
    assert(output.contains("Phase 2"))
    assert(output.contains("Tests passing"))

  test("format status with needsAttention"):
    val status = WorktreeStatus(
      issueId = "IWLE-555",
      path = "/home/user/testproject-IWLE-555",
      branchName = None,
      gitClean = None,
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = Some("Changes requested"),
      reviewBadges = None,
      needsAttention = true,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val output = StatusFormatter.format(status)

    assert(output.contains("⚠"))

  test("format status with progress"):
    val status = WorktreeStatus(
      issueId = "IWLE-666",
      path = "/home/user/testproject-IWLE-666",
      branchName = None,
      gitClean = None,
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = None,
      reviewBadges = None,
      needsAttention = false,
      currentPhase = Some(3),
      totalPhases = Some(5),
      overallProgress = Some(75)
    )

    val output = StatusFormatter.format(status)

    assert(output.contains("Progress"))
    assert(output.contains("3/5"))
    assert(output.contains("75%"))

  test("format status omits sections with no data"):
    val status = WorktreeStatus(
      issueId = "IWLE-777",
      path = "/home/user/testproject-IWLE-777",
      branchName = None,
      gitClean = None,
      issueTitle = Some("Simple task"),
      issueStatus = Some("Todo"),
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = None,
      reviewBadges = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val output = StatusFormatter.format(status)

    assert(output.contains("Issue"))
    assert(!output.contains("Git"))
    assert(!output.contains("Pull Request"))
    assert(!output.contains("Review"))
    assert(!output.contains("Progress"))
