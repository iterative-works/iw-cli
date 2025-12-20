// PURPOSE: Tests for ServerState domain model
// PURPOSE: Verifies worktree collection management and activity-based sorting

package iw.tests

import iw.core.domain.{ServerState, WorktreeRegistration}
import java.time.Instant

class ServerStateTest extends munit.FunSuite:
  test("ServerState with empty worktrees map"):
    val state = ServerState(worktrees = Map.empty)
    assertEquals(state.worktrees.size, 0)
    assertEquals(state.listByActivity, List.empty)

  test("ServerState.listByActivity returns worktrees sorted by lastSeenAt descending"):
    val now = Instant.now()
    val oneHourAgo = now.minusSeconds(3600)
    val twoHoursAgo = now.minusSeconds(7200)

    val worktree1 = WorktreeRegistration(
      issueId = "IWLE-1",
      path = "/path1",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = twoHoursAgo,
      lastSeenAt = twoHoursAgo
    )

    val worktree2 = WorktreeRegistration(
      issueId = "IWLE-2",
      path = "/path2",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = oneHourAgo,
      lastSeenAt = now
    )

    val worktree3 = WorktreeRegistration(
      issueId = "IWLE-3",
      path = "/path3",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = oneHourAgo
    )

    val state = ServerState(
      worktrees = Map(
        "IWLE-1" -> worktree1,
        "IWLE-2" -> worktree2,
        "IWLE-3" -> worktree3
      )
    )

    val sorted = state.listByActivity
    assertEquals(sorted.size, 3)
    // Most recent (now) should be first
    assertEquals(sorted(0).issueId, "IWLE-2")
    // Then one hour ago
    assertEquals(sorted(1).issueId, "IWLE-3")
    // Then two hours ago
    assertEquals(sorted(2).issueId, "IWLE-1")

  test("ServerState with single worktree"):
    val worktree = WorktreeRegistration(
      issueId = "IWLE-1",
      path = "/path1",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val state = ServerState(worktrees = Map("IWLE-1" -> worktree))
    val sorted = state.listByActivity
    assertEquals(sorted.size, 1)
    assertEquals(sorted.head.issueId, "IWLE-1")
