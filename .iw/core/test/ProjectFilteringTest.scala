// PURPOSE: Unit tests for project-based worktree filtering logic
// PURPOSE: Verifies filtering worktrees by project name using path derivation

package iw.tests

import iw.core.model.WorktreeRegistration
import iw.core.dashboard.application.MainProjectService
import java.time.Instant
import munit.FunSuite

class ProjectFilteringTest extends FunSuite:
  test("filterByProjectName returns only worktrees matching project name"):
    val worktree1 = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val worktree2 = WorktreeRegistration(
      issueId = "IW-80",
      path = "/home/user/projects/iw-cli-IW-80",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val worktree3 = WorktreeRegistration(
      issueId = "IWLE-123",
      path = "/home/user/projects/kanon-IWLE-123",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val allWorktrees = List(worktree1, worktree2, worktree3)
    val result = MainProjectService.filterByProjectName(allWorktrees, "iw-cli")

    assertEquals(result.length, 2)
    assert(result.contains(worktree1), "result should contain worktree1")
    assert(result.contains(worktree2), "result should contain worktree2")
    assert(!result.contains(worktree3), "result should not contain worktree3")

  test("filterByProjectName returns empty list when no worktrees match"):
    val worktree1 = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val result = MainProjectService.filterByProjectName(List(worktree1), "nonexistent")

    assertEquals(result, List.empty)

  test("filterByProjectName handles worktrees without valid main project path"):
    val validWorktree = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val invalidWorktree = WorktreeRegistration(
      issueId = "manual",
      path = "/home/user/projects/some-random-path",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val result = MainProjectService.filterByProjectName(
      List(validWorktree, invalidWorktree),
      "iw-cli"
    )

    // Only valid worktree should match
    assertEquals(result.length, 1)
    assertEquals(result.head, validWorktree)

  test("filterByProjectName handles multiple projects with similar names"):
    val worktree1 = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val worktree2 = WorktreeRegistration(
      issueId = "IW-80",
      path = "/home/user/projects/iw-cli-tools-IW-80",
      trackerType = "github",
      team = "iterative-works/iw-cli-tools",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    // Should only match exact project name, not substring
    val result1 = MainProjectService.filterByProjectName(List(worktree1, worktree2), "iw-cli")
    assertEquals(result1.length, 1)
    assertEquals(result1.head, worktree1)

    val result2 = MainProjectService.filterByProjectName(List(worktree1, worktree2), "iw-cli-tools")
    assertEquals(result2.length, 1)
    assertEquals(result2.head, worktree2)

  test("filterByProjectName is case-sensitive on project names"):
    val worktree = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    // Case mismatch should not match
    val result = MainProjectService.filterByProjectName(List(worktree), "IW-CLI")

    assertEquals(result, List.empty)
