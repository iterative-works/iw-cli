// PURPOSE: Unit tests for WorktreeUnregistrationService business logic
// PURPOSE: Verifies unregister and pruneNonExistent using pure functions

package iw.core.application

import munit.FunSuite
import iw.core.model.{ServerState, WorktreeRegistration, CachedIssue, IssueData, CachedProgress, WorkflowProgress, CachedPR, PullRequestData, PRState}
import java.time.Instant
import iw.core.dashboard.WorktreeUnregistrationService
import iw.core.model.Issue

class WorktreeUnregistrationServiceTest extends FunSuite:

  test("unregister returns Right with updated state when worktree exists"):
    val now = Instant.now()
    val worktree = WorktreeRegistration(
      issueId = "IWLE-123",
      path = "/path",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val state = ServerState(Map("IWLE-123" -> worktree))

    val result = WorktreeUnregistrationService.unregister(state, "IWLE-123")

    assert(result.isRight, "Should return Right")
    result.foreach { newState =>
      assert(!newState.worktrees.contains("IWLE-123"), "Worktree should be removed")
    }

  test("unregister returns Left with error when worktree not found"):
    val state = ServerState(Map.empty)

    val result = WorktreeUnregistrationService.unregister(state, "IWLE-999")

    assert(result.isLeft, "Should return Left for non-existent worktree")
    result.swap.foreach { error =>
      assert(error.contains("not found") || error.contains("IWLE-999"), s"Error message should mention issue: $error")
    }

  test("unregister removes worktree from state.worktrees"):
    val now = Instant.now()
    val worktree1 = WorktreeRegistration(
      issueId = "IWLE-100",
      path = "/path1",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val worktree2 = WorktreeRegistration(
      issueId = "IWLE-200",
      path = "/path2",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val state = ServerState(Map("IWLE-100" -> worktree1, "IWLE-200" -> worktree2))

    val result = WorktreeUnregistrationService.unregister(state, "IWLE-100")

    assert(result.isRight)
    result.foreach { newState =>
      assertEquals(newState.worktrees.size, 1)
      assert(!newState.worktrees.contains("IWLE-100"), "IWLE-100 should be removed")
      assert(newState.worktrees.contains("IWLE-200"), "IWLE-200 should remain")
    }

  test("unregister removes associated issue cache entry"):
    val now = Instant.now()
    val worktree = WorktreeRegistration(
      issueId = "IWLE-123",
      path = "/path",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val issueCache = Map("IWLE-123" -> CachedIssue(
      IssueData("IWLE-123", "Test Issue", "In Progress", None, None, "http://example.com", now)
    ))
    val state = ServerState(
      worktrees = Map("IWLE-123" -> worktree),
      issueCache = issueCache
    )

    val result = WorktreeUnregistrationService.unregister(state, "IWLE-123")

    assert(result.isRight)
    result.foreach { newState =>
      assert(!newState.issueCache.contains("IWLE-123"), "Issue cache should be removed")
    }

  test("unregister removes associated progress cache entry"):
    val now = Instant.now()
    val worktree = WorktreeRegistration(
      issueId = "IWLE-123",
      path = "/path",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val progressCache = Map("IWLE-123" -> CachedProgress(
      WorkflowProgress(None, 0, List.empty, 0, 0),
      Map.empty
    ))
    val state = ServerState(
      worktrees = Map("IWLE-123" -> worktree),
      progressCache = progressCache
    )

    val result = WorktreeUnregistrationService.unregister(state, "IWLE-123")

    assert(result.isRight)
    result.foreach { newState =>
      assert(!newState.progressCache.contains("IWLE-123"), "Progress cache should be removed")
    }

  test("unregister removes associated PR cache entry"):
    val now = Instant.now()
    val worktree = WorktreeRegistration(
      issueId = "IWLE-123",
      path = "/path",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val prCache = Map("IWLE-123" -> CachedPR(
      PullRequestData("http://example.com/pr/123", PRState.Open, 123, "Test PR"),
      now
    ))
    val state = ServerState(
      worktrees = Map("IWLE-123" -> worktree),
      prCache = prCache
    )

    val result = WorktreeUnregistrationService.unregister(state, "IWLE-123")

    assert(result.isRight)
    result.foreach { newState =>
      assert(!newState.prCache.contains("IWLE-123"), "PR cache should be removed")
    }

  test("pruneNonExistent removes worktrees with missing paths"):
    val now = Instant.now()
    val worktree1 = WorktreeRegistration(
      issueId = "IWLE-100",
      path = "/existing/path",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val worktree2 = WorktreeRegistration(
      issueId = "IWLE-200",
      path = "/missing/path",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val state = ServerState(Map("IWLE-100" -> worktree1, "IWLE-200" -> worktree2))

    def pathExists(path: String): Boolean = path == "/existing/path"

    val newState = WorktreeUnregistrationService.pruneNonExistent(state, pathExists)

    assertEquals(newState.worktrees.size, 1)
    assert(newState.worktrees.contains("IWLE-100"), "Existing path should remain")
    assert(!newState.worktrees.contains("IWLE-200"), "Missing path should be removed")

  test("pruneNonExistent keeps worktrees with existing paths"):
    val now = Instant.now()
    val worktree1 = WorktreeRegistration(
      issueId = "IWLE-100",
      path = "/path1",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val worktree2 = WorktreeRegistration(
      issueId = "IWLE-200",
      path = "/path2",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val state = ServerState(Map("IWLE-100" -> worktree1, "IWLE-200" -> worktree2))

    def pathExists(path: String): Boolean = true // All paths exist

    val newState = WorktreeUnregistrationService.pruneNonExistent(state, pathExists)

    assertEquals(newState.worktrees.size, 2)
    assert(newState.worktrees.contains("IWLE-100"))
    assert(newState.worktrees.contains("IWLE-200"))

  test("pruneNonExistent removes associated caches for pruned worktrees"):
    val now = Instant.now()
    val worktree1 = WorktreeRegistration(
      issueId = "IWLE-100",
      path = "/existing",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val worktree2 = WorktreeRegistration(
      issueId = "IWLE-200",
      path = "/missing",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val issueCache = Map(
      "IWLE-100" -> CachedIssue(IssueData("IWLE-100", "Issue 1", "Open", None, None, "http://example.com", now)),
      "IWLE-200" -> CachedIssue(IssueData("IWLE-200", "Issue 2", "Open", None, None, "http://example.com", now))
    )
    val progressCache = Map(
      "IWLE-100" -> CachedProgress(WorkflowProgress(None, 0, List.empty, 0, 0), Map.empty),
      "IWLE-200" -> CachedProgress(WorkflowProgress(None, 0, List.empty, 0, 0), Map.empty)
    )
    val prCache = Map(
      "IWLE-100" -> CachedPR(PullRequestData("http://example.com/pr/1", PRState.Open, 1, "PR 1"), now),
      "IWLE-200" -> CachedPR(PullRequestData("http://example.com/pr/2", PRState.Open, 2, "PR 2"), now)
    )
    val state = ServerState(
      worktrees = Map("IWLE-100" -> worktree1, "IWLE-200" -> worktree2),
      issueCache = issueCache,
      progressCache = progressCache,
      prCache = prCache
    )

    def pathExists(path: String): Boolean = path == "/existing"

    val newState = WorktreeUnregistrationService.pruneNonExistent(state, pathExists)

    // IWLE-100 should remain with all caches
    assert(newState.worktrees.contains("IWLE-100"))
    assert(newState.issueCache.contains("IWLE-100"))
    assert(newState.progressCache.contains("IWLE-100"))
    assert(newState.prCache.contains("IWLE-100"))

    // IWLE-200 should be removed from all caches
    assert(!newState.worktrees.contains("IWLE-200"))
    assert(!newState.issueCache.contains("IWLE-200"))
    assert(!newState.progressCache.contains("IWLE-200"))
    assert(!newState.prCache.contains("IWLE-200"))

  test("pruneNonExistent handles empty state gracefully"):
    val state = ServerState(Map.empty)

    def pathExists(path: String): Boolean = false

    val newState = WorktreeUnregistrationService.pruneNonExistent(state, pathExists)

    assertEquals(newState.worktrees.size, 0)
    assertEquals(newState, state)
