// PURPOSE: Tests for ServerState domain model
// PURPOSE: Verifies worktree collection management and issue ID-based sorting

package iw.tests

import iw.core.model.{ServerState, WorktreeRegistration, ProjectRegistration}
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
    assertEquals(state.listByIssueId, List.empty)

  test(
    "ServerState.listByIssueId returns worktrees sorted by issueId ascending"
  ):
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

    val sorted = state.listByIssueId
    assertEquals(sorted.size, 3)
    // Alphabetical by issue ID
    assertEquals(sorted(0).issueId, "IWLE-1")
    assertEquals(sorted(1).issueId, "IWLE-2")
    assertEquals(sorted(2).issueId, "IWLE-3")

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
    val sorted = state.listByIssueId
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
    val issueCache = Map(
      "IWLE-123" -> iw.core.model.CachedIssue(
        iw.core.model.IssueData(
          "IWLE-123",
          "Test Issue",
          "In Progress",
          None,
          None,
          "http://example.com",
          now
        )
      )
    )
    val progressCache = Map(
      "IWLE-123" -> iw.core.model.CachedProgress(
        iw.core.model.WorkflowProgress(None, 0, List.empty, 0, 0),
        Map.empty
      )
    )
    val prCache = Map(
      "IWLE-123" -> iw.core.model.CachedPR(
        iw.core.model.PullRequestData(
          "http://example.com/pr/123",
          iw.core.model.PRState.Open,
          123,
          "Test PR"
        ),
        now
      )
    )

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
    assert(
      newState.worktrees.contains("IWLE-100"),
      "Should preserve existing worktree"
    )
    assertEquals(
      newState,
      state.copy(
        worktrees = state.worktrees - "IWLE-999",
        issueCache = state.issueCache - "IWLE-999",
        progressCache = state.progressCache - "IWLE-999",
        prCache = state.prCache - "IWLE-999",
        reviewStateCache = state.reviewStateCache - "IWLE-999"
      )
    )

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
    val reviewStateCache = Map(
      "IWLE-123" -> iw.core.model.CachedReviewState(
        iw.core.model.ReviewState(None, None, None, None, None, List.empty),
        Map.empty
      )
    )

    val state = ServerState(
      worktrees = Map("IWLE-123" -> worktree),
      reviewStateCache = reviewStateCache
    )

    val newState = state.removeWorktree("IWLE-123")

    assert(!newState.reviewStateCache.contains("IWLE-123"))

  test(
    "ServerState.listByIssueId sorts alphabetically with different prefixes"
  ):
    val now = Instant.now()

    val worktree1 = WorktreeRegistration(
      issueId = "GH-50",
      path = "/path1",
      trackerType = "github",
      team = "GH",
      registeredAt = now,
      lastSeenAt = now
    )

    val worktree2 = WorktreeRegistration(
      issueId = "IW-100",
      path = "/path2",
      trackerType = "linear",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )

    val worktree3 = WorktreeRegistration(
      issueId = "LINEAR-25",
      path = "/path3",
      trackerType = "linear",
      team = "LINEAR",
      registeredAt = now,
      lastSeenAt = now
    )

    val state = ServerState(
      worktrees = Map(
        "GH-50" -> worktree1,
        "IW-100" -> worktree2,
        "LINEAR-25" -> worktree3
      )
    )

    val sorted = state.listByIssueId
    assertEquals(sorted.size, 3)
    // Alphabetical ordering: GH-50 < IW-100 < LINEAR-25
    assertEquals(sorted(0).issueId, "GH-50")
    assertEquals(sorted(1).issueId, "IW-100")
    assertEquals(sorted(2).issueId, "LINEAR-25")

  test("ServerState.listByIssueId uses pure alphabetical string sorting"):
    val now = Instant.now()

    val worktree1 = WorktreeRegistration(
      issueId = "IW-1",
      path = "/path1",
      trackerType = "linear",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )

    val worktree2 = WorktreeRegistration(
      issueId = "IW-10",
      path = "/path2",
      trackerType = "linear",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )

    val worktree3 = WorktreeRegistration(
      issueId = "IW-100",
      path = "/path3",
      trackerType = "linear",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )

    val worktree4 = WorktreeRegistration(
      issueId = "IW-2",
      path = "/path4",
      trackerType = "linear",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )

    val state = ServerState(
      worktrees = Map(
        "IW-1" -> worktree1,
        "IW-10" -> worktree2,
        "IW-100" -> worktree3,
        "IW-2" -> worktree4
      )
    )

    val sorted = state.listByIssueId
    assertEquals(sorted.size, 4)
    // Pure alphabetical: IW-1 < IW-10 < IW-100 < IW-2
    assertEquals(sorted(0).issueId, "IW-1")
    assertEquals(sorted(1).issueId, "IW-10")
    assertEquals(sorted(2).issueId, "IW-100")
    assertEquals(sorted(3).issueId, "IW-2")

  test("ServerState has default empty projects map"):
    val state = ServerState(worktrees = Map.empty)
    assertEquals(state.projects.size, 0)

  test("ServerState.listProjects returns projects sorted by projectName"):
    val now = Instant.now()
    val projectA = ProjectRegistration(
      path = "/path/to/zebra",
      projectName = "zebra",
      trackerType = "github",
      team = "org/zebra",
      trackerUrl = None,
      registeredAt = now
    )
    val projectB = ProjectRegistration(
      path = "/path/to/alpha",
      projectName = "alpha",
      trackerType = "linear",
      team = "TEAM",
      trackerUrl = None,
      registeredAt = now
    )
    val state = ServerState(
      worktrees = Map.empty,
      projects = Map("/path/to/zebra" -> projectA, "/path/to/alpha" -> projectB)
    )

    val listed = state.listProjects
    assertEquals(listed.size, 2)
    assertEquals(listed(0).projectName, "alpha")
    assertEquals(listed(1).projectName, "zebra")

  test(
    "ServerState.listProjects with multiple projects uses alphabetical sort"
  ):
    val now = Instant.now()
    val makeProject = (name: String, path: String) =>
      ProjectRegistration(
        path = path,
        projectName = name,
        trackerType = "github",
        team = "org",
        trackerUrl = None,
        registeredAt = now
      )
    val state = ServerState(
      worktrees = Map.empty,
      projects = Map(
        "/c" -> makeProject("cherry", "/c"),
        "/a" -> makeProject("apple", "/a"),
        "/b" -> makeProject("banana", "/b")
      )
    )

    val listed = state.listProjects
    assertEquals(listed.map(_.projectName), List("apple", "banana", "cherry"))

  test("ServerState.removeProject removes entry by path key"):
    val now = Instant.now()
    val project = ProjectRegistration(
      path = "/path/to/proj",
      projectName = "proj",
      trackerType = "github",
      team = "org/proj",
      trackerUrl = None,
      registeredAt = now
    )
    val state = ServerState(
      worktrees = Map.empty,
      projects = Map("/path/to/proj" -> project)
    )

    val newState = state.removeProject("/path/to/proj")

    assertEquals(newState.projects.size, 0)
    assert(!newState.projects.contains("/path/to/proj"))

  test("ServerState.removeProject is idempotent for non-existent key"):
    val now = Instant.now()
    val project = ProjectRegistration(
      path = "/path/to/proj",
      projectName = "proj",
      trackerType = "github",
      team = "org/proj",
      trackerUrl = None,
      registeredAt = now
    )
    val state = ServerState(
      worktrees = Map.empty,
      projects = Map("/path/to/proj" -> project)
    )

    val newState = state.removeProject("/path/to/nonexistent")

    assertEquals(newState.projects.size, 1)
    assert(newState.projects.contains("/path/to/proj"))
