// PURPOSE: Tests for ServerState domain model
// PURPOSE: Verifies worktree collection management and activity-based sorting

package iw.tests

import iw.core.model.{ServerState, WorktreeRegistration}
import java.time.Instant
import iw.core.model.CachedIssue
import iw.core.model.CachedPR
import iw.core.model.CachedProgress
import iw.core.model.CachedReviewState
import iw.core.model.ReviewState
import iw.core.model.Issue
import iw.core.model.IssueData
import iw.core.model.WorkflowProgress
import iw.core.model.PullRequestData
import iw.core.model.PRState

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

  test("ServerState.removeWorktree removes entry from worktrees map"):
    val worktree = WorktreeRegistration(
      issueId = "IWLE-123",
      path = "/path",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )
    val state = ServerState(worktrees = Map("IWLE-123" -> worktree))

    val newState = state.removeWorktree("IWLE-123")

    assertEquals(newState.worktrees.size, 0)
    assert(!newState.worktrees.contains("IWLE-123"))

  test("ServerState.removeWorktree removes entry from all cache maps"):
    val now = Instant.now()
    val worktree = WorktreeRegistration(
      issueId = "IWLE-123",
      path = "/path",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val issueCache = Map("IWLE-123" -> iw.core.model.CachedIssue(
      iw.core.model.IssueData("IWLE-123", "Test Issue", "In Progress", None, None, "http://example.com", now)
    ))
    val progressCache = Map("IWLE-123" -> iw.core.model.CachedProgress(
      iw.core.model.WorkflowProgress(None, 0, List.empty, 0, 0),
      Map.empty
    ))
    val prCache = Map("IWLE-123" -> iw.core.model.CachedPR(
      iw.core.model.PullRequestData("http://example.com/pr/123", iw.core.model.PRState.Open, 123, "Test PR"),
      now
    ))

    val state = ServerState(
      worktrees = Map("IWLE-123" -> worktree),
      issueCache = issueCache,
      progressCache = progressCache,
      prCache = prCache
    )

    val newState = state.removeWorktree("IWLE-123")

    assert(!newState.worktrees.contains("IWLE-123"))
    assert(!newState.issueCache.contains("IWLE-123"))
    assert(!newState.progressCache.contains("IWLE-123"))
    assert(!newState.prCache.contains("IWLE-123"))

  test("ServerState.removeWorktree is idempotent for non-existent issueId"):
    val worktree1 = WorktreeRegistration(
      issueId = "IWLE-100",
      path = "/path1",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )
    val state = ServerState(worktrees = Map("IWLE-100" -> worktree1))

    val newState = state.removeWorktree("IWLE-999")

    assertEquals(newState.worktrees.size, 1)
    assert(newState.worktrees.contains("IWLE-100"), "Should preserve existing worktree")
    assertEquals(newState, state.copy(
      worktrees = state.worktrees - "IWLE-999",
      issueCache = state.issueCache - "IWLE-999",
      progressCache = state.progressCache - "IWLE-999",
      prCache = state.prCache - "IWLE-999",
      reviewStateCache = state.reviewStateCache - "IWLE-999"
    ))

  test("ServerState includes reviewStateCache field"):
    val state = ServerState(
      worktrees = Map.empty,
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty
    )

    assertEquals(state.reviewStateCache.size, 0)

  test("ServerState.removeWorktree clears review state cache entry"):
    val now = Instant.now()
    val worktree = WorktreeRegistration(
      issueId = "IWLE-123",
      path = "/path",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val reviewStateCache = Map("IWLE-123" -> iw.core.model.CachedReviewState(
      iw.core.model.ReviewState(None, None, None, List.empty),
      Map.empty
    ))

    val state = ServerState(
      worktrees = Map("IWLE-123" -> worktree),
      reviewStateCache = reviewStateCache
    )

    val newState = state.removeWorktree("IWLE-123")

    assert(!newState.reviewStateCache.contains("IWLE-123"))
