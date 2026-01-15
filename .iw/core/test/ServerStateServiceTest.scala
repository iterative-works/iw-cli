// PURPOSE: Unit tests for ServerStateService centralized state management
// PURPOSE: Verifies thread-safe operations, per-entry updates, and persistence

package iw.tests

import iw.core.application.ServerStateService
import iw.core.domain.{ServerState, WorktreeRegistration, IssueData, CachedIssue, PhaseInfo, WorkflowProgress, CachedProgress, PullRequestData, PRState, CachedPR, ReviewState, ReviewArtifact, CachedReviewState}
import iw.core.infrastructure.StateRepository
import java.time.Instant
import java.nio.file.{Files, Path}
import java.util.concurrent.{Executors, TimeUnit, CountDownLatch}

class ServerStateServiceTest extends munit.FunSuite:
  val tempDir = FunFixture[Path](
    setup = { _ =>
      val dir = Files.createTempDirectory("iw-test-state-service")
      dir
    },
    teardown = { dir =>
      // Clean up temp directory and files
      if Files.exists(dir) then
        Files.walk(dir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(Files.delete)
    }
  )

  tempDir.test("ServerStateService.initialize loads state from repository"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      // Pre-populate state file
      val worktree = WorktreeRegistration(
        issueId = "IWLE-123",
        path = "/test/path",
        trackerType = "linear",
        team = "IWLE",
        registeredAt = Instant.parse("2025-12-19T10:00:00Z"),
        lastSeenAt = Instant.parse("2025-12-19T14:00:00Z")
      )
      repo.write(ServerState(Map("IWLE-123" -> worktree)))

      // Initialize service
      val service = new ServerStateService(repo)
      val result = service.initialize()

      assert(result.isRight)
      val state = service.getState
      assertEquals(state.worktrees.size, 1)
      assert(state.worktrees.contains("IWLE-123"))

  tempDir.test("ServerStateService.initialize creates empty state if file doesn't exist"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      val result = service.initialize()

      assert(result.isRight)
      val state = service.getState
      assertEquals(state.worktrees.size, 0)

  tempDir.test("ServerStateService.getState returns current state"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      service.initialize()

      val state = service.getState
      assertEquals(state.worktrees.size, 0)

  tempDir.test("ServerStateService.updateWorktree adds new worktree"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      service.initialize()

      val worktree = WorktreeRegistration(
        issueId = "IWLE-456",
        path = "/test/path2",
        trackerType = "linear",
        team = "IWLE",
        registeredAt = Instant.now(),
        lastSeenAt = Instant.now()
      )

      service.updateWorktree("IWLE-456")(_ => Some(worktree))

      val state = service.getState
      assertEquals(state.worktrees.size, 1)
      assert(state.worktrees.contains("IWLE-456"))

  tempDir.test("ServerStateService.updateWorktree removes worktree when function returns None"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      service.initialize()

      val worktree = WorktreeRegistration(
        issueId = "IWLE-789",
        path = "/test/path3",
        trackerType = "linear",
        team = "IWLE",
        registeredAt = Instant.now(),
        lastSeenAt = Instant.now()
      )

      service.updateWorktree("IWLE-789")(_ => Some(worktree))
      assertEquals(service.getState.worktrees.size, 1)

      service.updateWorktree("IWLE-789")(_ => None)
      assertEquals(service.getState.worktrees.size, 0)

  tempDir.test("ServerStateService.updateWorktree persists changes"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      service.initialize()

      val worktree = WorktreeRegistration(
        issueId = "IWLE-999",
        path = "/test/path4",
        trackerType = "linear",
        team = "IWLE",
        registeredAt = Instant.now(),
        lastSeenAt = Instant.now()
      )

      service.updateWorktree("IWLE-999")(_ => Some(worktree))

      // Read from disk to verify persistence
      val diskState = repo.read()
      assert(diskState.isRight)
      diskState.foreach { state =>
        assertEquals(state.worktrees.size, 1)
        assert(state.worktrees.contains("IWLE-999"))
      }

  tempDir.test("ServerStateService.updateIssueCache adds cache entry"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      service.initialize()

      val issueData = IssueData(
        id = "IWLE-100",
        title = "Test Issue",
        status = "In Progress",
        assignee = Some("Jane Doe"),
        description = None,
        url = "https://linear.app/issue/IWLE-100",
        fetchedAt = Instant.now()
      )
      val cached = CachedIssue(issueData)

      service.updateIssueCache("IWLE-100")(_ => Some(cached))

      val state = service.getState
      assertEquals(state.issueCache.size, 1)
      assert(state.issueCache.contains("IWLE-100"))

  tempDir.test("ServerStateService concurrent updates don't lose data"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      service.initialize()

      val threadCount = 20
      val executor = Executors.newFixedThreadPool(threadCount)
      val latch = new CountDownLatch(threadCount)

      try
        // Each thread adds a different worktree
        (0 until threadCount).foreach { i =>
          executor.submit(new Runnable {
            def run(): Unit =
              try
                val worktree = WorktreeRegistration(
                  issueId = s"IWLE-$i",
                  path = s"/test/path-$i",
                  trackerType = "linear",
                  team = "IWLE",
                  registeredAt = Instant.now(),
                  lastSeenAt = Instant.now()
                )
                service.updateWorktree(s"IWLE-$i")(_ => Some(worktree))
              finally
                latch.countDown()
          })
        }

        latch.await(10, TimeUnit.SECONDS)
      finally
        executor.shutdown()

      // Verify all worktrees were added
      val state = service.getState
      assertEquals(state.worktrees.size, threadCount, "All concurrent updates should be preserved")

      // Verify persistence
      val diskState = repo.read()
      assert(diskState.isRight)
      diskState.foreach { state =>
        assertEquals(state.worktrees.size, threadCount, "All concurrent updates should be persisted")
      }

  tempDir.test("ServerStateService.pruneWorktrees removes invalid worktrees"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      service.initialize()

      // Add three worktrees
      service.updateWorktree("IWLE-1")(_ => Some(WorktreeRegistration(
        "IWLE-1", "/path/valid1", "linear", "IWLE", Instant.now(), Instant.now()
      )))
      service.updateWorktree("IWLE-2")(_ => Some(WorktreeRegistration(
        "IWLE-2", "/path/invalid", "linear", "IWLE", Instant.now(), Instant.now()
      )))
      service.updateWorktree("IWLE-3")(_ => Some(WorktreeRegistration(
        "IWLE-3", "/path/valid2", "linear", "IWLE", Instant.now(), Instant.now()
      )))

      // Prune worktrees with "invalid" in path
      val pruned = service.pruneWorktrees(wt => !wt.path.contains("invalid"))

      assertEquals(pruned.size, 1)
      assert(pruned.contains("IWLE-2"))
      assertEquals(service.getState.worktrees.size, 2)

  tempDir.test("ServerStateService.updateProgressCache adds progress"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      service.initialize()

      val phase = PhaseInfo(1, "Setup", "/path/phase-01.md", 10, 5)
      val progress = WorkflowProgress(
        currentPhase = Some(1),
        totalPhases = 1,
        phases = List(phase),
        overallCompleted = 5,
        overallTotal = 10
      )
      val cached = CachedProgress(progress, Map("/path/phase-01.md" -> 1000L))

      service.updateProgressCache("IWLE-200")(_ => Some(cached))

      val state = service.getState
      assertEquals(state.progressCache.size, 1)
      assert(state.progressCache.contains("IWLE-200"))

  tempDir.test("ServerStateService.updatePRCache adds PR data"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      service.initialize()

      val prData = PullRequestData(
        url = "https://github.com/org/repo/pull/123",
        state = PRState.Open,
        number = 123,
        title = "Test PR"
      )
      val cached = CachedPR(prData, Instant.now())

      service.updatePRCache("IWLE-300")(_ => Some(cached))

      val state = service.getState
      assertEquals(state.prCache.size, 1)
      assert(state.prCache.contains("IWLE-300"))

  tempDir.test("ServerStateService.updateReviewStateCache adds review state"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val service = new ServerStateService(repo)
      service.initialize()

      val reviewState = ReviewState(
        status = Some("awaiting_review"),
        phase = Some(1),
        message = Some("Ready for review"),
        artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
      )
      val cached = CachedReviewState(reviewState, Map("/path/review-state.json" -> 2000L))

      service.updateReviewStateCache("IWLE-400")(_ => Some(cached))

      val state = service.getState
      assertEquals(state.reviewStateCache.size, 1)
      assert(state.reviewStateCache.contains("IWLE-400"))
