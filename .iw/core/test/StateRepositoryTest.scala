// PURPOSE: Integration tests for StateRepository JSON persistence
// PURPOSE: Verifies atomic writes, error handling, and state file management

package iw.tests

import iw.core.model.{ServerState, WorktreeRegistration, IssueData, CachedIssue, PhaseInfo, WorkflowProgress, CachedProgress, PullRequestData, PRState, CachedPR}
import iw.core.dashboard.StateRepository
import java.time.Instant
import java.nio.file.{Files, Path, Paths}

class StateRepositoryTest extends munit.FunSuite:
  val tempDir = FunFixture[Path](
    setup = { _ =>
      val dir = Files.createTempDirectory("iw-test-state")
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

  tempDir.test("StateRepository.read with non-existent file returns empty state"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val result = repo.read()
      assert(result.isRight)
      result.foreach { state =>
        assertEquals(state.worktrees.size, 0)
      }

  tempDir.test("StateRepository.write then read returns same state"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val worktree = WorktreeRegistration(
        issueId = "IWLE-123",
        path = "/test/path",
        trackerType = "linear",
        team = "IWLE",
        registeredAt = Instant.parse("2025-12-19T10:00:00Z"),
        lastSeenAt = Instant.parse("2025-12-19T14:00:00Z")
      )

      val originalState = ServerState(Map("IWLE-123" -> worktree))

      val writeResult = repo.write(originalState)
      assert(writeResult.isRight, s"Write failed: $writeResult")

      val readResult = repo.read()
      assert(readResult.isRight, s"Read failed: $readResult")

      readResult.foreach { state =>
        assertEquals(state.worktrees.size, 1)
        assert(state.worktrees.contains("IWLE-123"))
        val readWorktree = state.worktrees("IWLE-123")
        assertEquals(readWorktree.issueId, worktree.issueId)
        assertEquals(readWorktree.path, worktree.path)
        assertEquals(readWorktree.trackerType, worktree.trackerType)
        assertEquals(readWorktree.team, worktree.team)
        assertEquals(readWorktree.registeredAt, worktree.registeredAt)
        assertEquals(readWorktree.lastSeenAt, worktree.lastSeenAt)
      }

  tempDir.test("StateRepository.write uses atomic writes (tmp file + rename)"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val state = ServerState(Map.empty)
      val writeResult = repo.write(state)
      assert(writeResult.isRight)

      // Verify no .tmp file left behind
      val tmpPath = Paths.get(statePath.toString + ".tmp")
      assert(!Files.exists(tmpPath), "Temporary file should be deleted after atomic write")

      // Verify actual file exists
      assert(Files.exists(statePath))

  tempDir.test("StateRepository.read handles malformed JSON gracefully"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      Files.writeString(statePath, "{invalid json")

      val repo = StateRepository(statePath.toString)
      val result = repo.read()

      assert(result.isLeft, "Should return Left for malformed JSON")
      result.left.foreach { error =>
        assert(error.contains("JSON") || error.contains("parse"))
      }

  tempDir.test("StateRepository serializes ServerState with issueCache"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val issueData = IssueData(
        id = "IWLE-123",
        title = "Test Issue",
        status = "In Progress",
        assignee = Some("Jane Doe"),
        description = Some("Description"),
        url = "https://linear.app/issue/IWLE-123",
        fetchedAt = Instant.parse("2025-12-20T10:00:00Z")
      )
      val cached = CachedIssue(issueData, ttlMinutes = 5)

      val worktree = WorktreeRegistration(
        issueId = "IWLE-123",
        path = "/test/path",
        trackerType = "linear",
        team = "IWLE",
        registeredAt = Instant.parse("2025-12-19T10:00:00Z"),
        lastSeenAt = Instant.parse("2025-12-19T14:00:00Z")
      )

      val state = ServerState(
        worktrees = Map("IWLE-123" -> worktree),
        issueCache = Map("IWLE-123" -> cached)
      )

      val writeResult = repo.write(state)
      assert(writeResult.isRight, s"Write failed: $writeResult")

      // Verify file exists
      assert(Files.exists(statePath))

  tempDir.test("StateRepository deserializes ServerState with issueCache correctly"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val issueData = IssueData(
        id = "IWLE-456",
        title = "Another Issue",
        status = "Todo",
        assignee = None,
        description = None,
        url = "https://linear.app/issue/IWLE-456",
        fetchedAt = Instant.parse("2025-12-20T11:00:00Z")
      )
      val cached = CachedIssue(issueData, ttlMinutes = 10)

      val worktree = WorktreeRegistration(
        issueId = "IWLE-456",
        path = "/test/path2",
        trackerType = "linear",
        team = "IWLE",
        registeredAt = Instant.parse("2025-12-19T10:00:00Z"),
        lastSeenAt = Instant.parse("2025-12-19T14:00:00Z")
      )

      val state = ServerState(
        worktrees = Map("IWLE-456" -> worktree),
        issueCache = Map("IWLE-456" -> cached)
      )

      repo.write(state)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.issueCache.size, 1)
        assert(loadedState.issueCache.contains("IWLE-456"))

        val loadedCached = loadedState.issueCache("IWLE-456")
        assertEquals(loadedCached.data.id, "IWLE-456")
        assertEquals(loadedCached.data.title, "Another Issue")
        assertEquals(loadedCached.data.status, "Todo")
        assertEquals(loadedCached.data.assignee, None)
        assertEquals(loadedCached.data.url, "https://linear.app/issue/IWLE-456")
        assertEquals(loadedCached.ttlMinutes, 10)
      }

  tempDir.test("StateRepository handles empty issueCache"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map.empty
      )

      val writeResult = repo.write(state)
      assert(writeResult.isRight)

      val readResult = repo.read()
      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.issueCache.size, 0)
      }

  tempDir.test("StateRepository preserves Instant timestamps in issueCache"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val specificTime = Instant.parse("2025-12-20T12:34:56.789Z")
      val issueData = IssueData(
        id = "TEST-1",
        title = "Test",
        status = "In Progress",
        assignee = None,
        description = None,
        url = "https://example.com/TEST-1",
        fetchedAt = specificTime
      )
      val cached = CachedIssue(issueData)

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map("TEST-1" -> cached)
      )

      repo.write(state)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        val loadedCached = loadedState.issueCache("TEST-1")
        assertEquals(loadedCached.data.fetchedAt, specificTime)
      }

  tempDir.test("StateRepository handles multiple cached issues"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val issue1 = IssueData("IWLE-1", "Issue 1", "Open", None, None, "https://example.com/1", Instant.now())
      val issue2 = IssueData("IWLE-2", "Issue 2", "Done", Some("John"), None, "https://example.com/2", Instant.now())
      val issue3 = IssueData("PROJ-3", "Issue 3", "In Progress", None, Some("Desc"), "https://example.com/3", Instant.now())

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map(
          "IWLE-1" -> CachedIssue(issue1),
          "IWLE-2" -> CachedIssue(issue2),
          "PROJ-3" -> CachedIssue(issue3)
        )
      )

      repo.write(state)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.issueCache.size, 3)
        assert(loadedState.issueCache.contains("IWLE-1"))
        assert(loadedState.issueCache.contains("IWLE-2"))
        assert(loadedState.issueCache.contains("PROJ-3"))
      }

  tempDir.test("StateRepository serializes ServerState with progressCache"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val phase1 = PhaseInfo(1, "Phase 1", "/path/phase-01-tasks.md", 10, 5)
      val progress = WorkflowProgress(
        currentPhase = Some(1),
        totalPhases = 1,
        phases = List(phase1),
        overallCompleted = 5,
        overallTotal = 10
      )
      val cached = CachedProgress(progress, Map("/path/phase-01-tasks.md" -> 1000L))

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map.empty,
        progressCache = Map("IWLE-123" -> cached)
      )

      val writeResult = repo.write(state)
      assert(writeResult.isRight, s"Write failed: $writeResult")

      // Verify file exists
      assert(Files.exists(statePath))

  tempDir.test("StateRepository deserializes ServerState with progressCache correctly"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val phase1 = PhaseInfo(1, "Setup", "/worktree/phase-01.md", 5, 5)
      val phase2 = PhaseInfo(2, "Implementation", "/worktree/phase-02.md", 10, 3)
      val progress = WorkflowProgress(
        currentPhase = Some(2),
        totalPhases = 2,
        phases = List(phase1, phase2),
        overallCompleted = 8,
        overallTotal = 15
      )
      val filesMtime = Map(
        "/worktree/phase-01.md" -> 1000L,
        "/worktree/phase-02.md" -> 2000L
      )
      val cached = CachedProgress(progress, filesMtime)

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map.empty,
        progressCache = Map("IWLE-456" -> cached)
      )

      repo.write(state)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.progressCache.size, 1)
        assert(loadedState.progressCache.contains("IWLE-456"))

        val loadedCached = loadedState.progressCache("IWLE-456")
        assertEquals(loadedCached.progress.totalPhases, 2)
        assertEquals(loadedCached.progress.overallCompleted, 8)
        assertEquals(loadedCached.progress.overallTotal, 15)
        assertEquals(loadedCached.progress.currentPhase, Some(2))
        assertEquals(loadedCached.filesMtime.size, 2)
        assertEquals(loadedCached.filesMtime("/worktree/phase-01.md"), 1000L)
        assertEquals(loadedCached.filesMtime("/worktree/phase-02.md"), 2000L)
      }

  tempDir.test("StateRepository handles empty progressCache"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map.empty,
        progressCache = Map.empty
      )

      val writeResult = repo.write(state)
      assert(writeResult.isRight)

      val readResult = repo.read()
      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.progressCache.size, 0)
      }

  tempDir.test("Old state.json without progressCache loads successfully"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")

      // Simulate old state.json format (no progressCache field)
      val oldJsonContent = """{
        "worktrees": {},
        "issueCache": {}
      }"""
      Files.writeString(statePath, oldJsonContent)

      val repo = StateRepository(statePath.toString)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.worktrees.size, 0)
        assertEquals(loadedState.issueCache.size, 0)
        assertEquals(loadedState.progressCache.size, 0)
      }

  tempDir.test("StateRepository serializes ServerState with prCache field"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val prData = PullRequestData(
        url = "https://github.com/org/repo/pull/123",
        state = PRState.Open,
        number = 123,
        title = "Test PR"
      )
      val cachedPR = CachedPR(prData, Instant.parse("2025-12-20T10:00:00Z"))

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map.empty,
        progressCache = Map.empty,
        prCache = Map("IWLE-123" -> cachedPR)
      )

      val writeResult = repo.write(state)
      assert(writeResult.isRight, s"Write failed: $writeResult")

      // Verify file exists
      assert(Files.exists(statePath))

  tempDir.test("StateRepository deserializes ServerState with prCache correctly"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val prData = PullRequestData(
        url = "https://github.com/org/repo/pull/456",
        state = PRState.Merged,
        number = 456,
        title = "Feature: Add awesome feature"
      )
      val cachedPR = CachedPR(prData, Instant.parse("2025-12-20T11:30:00Z"))

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map.empty,
        progressCache = Map.empty,
        prCache = Map("IWLE-456" -> cachedPR)
      )

      repo.write(state)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.prCache.size, 1)
        assert(loadedState.prCache.contains("IWLE-456"))

        val loadedCached = loadedState.prCache("IWLE-456")
        assertEquals(loadedCached.pr.url, "https://github.com/org/repo/pull/456")
        assertEquals(loadedCached.pr.state, PRState.Merged)
        assertEquals(loadedCached.pr.number, 456)
        assertEquals(loadedCached.pr.title, "Feature: Add awesome feature")
        assertEquals(loadedCached.fetchedAt, Instant.parse("2025-12-20T11:30:00Z"))
      }

  tempDir.test("StateRepository prCache persists PullRequestData url, state, number"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val prData1 = PullRequestData(
        url = "https://github.com/org/repo/pull/100",
        state = PRState.Open,
        number = 100,
        title = "PR 100"
      )
      val prData2 = PullRequestData(
        url = "https://github.com/org/repo/pull/200",
        state = PRState.Closed,
        number = 200,
        title = "PR 200"
      )

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map.empty,
        progressCache = Map.empty,
        prCache = Map(
          "ISSUE-1" -> CachedPR(prData1, Instant.now()),
          "ISSUE-2" -> CachedPR(prData2, Instant.now())
        )
      )

      repo.write(state)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.prCache.size, 2)

        val pr1 = loadedState.prCache("ISSUE-1").pr
        assertEquals(pr1.url, "https://github.com/org/repo/pull/100")
        assertEquals(pr1.state, PRState.Open)
        assertEquals(pr1.number, 100)

        val pr2 = loadedState.prCache("ISSUE-2").pr
        assertEquals(pr2.url, "https://github.com/org/repo/pull/200")
        assertEquals(pr2.state, PRState.Closed)
        assertEquals(pr2.number, 200)
      }

  tempDir.test("StateRepository prCache persists fetchedAt timestamp"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      val specificTime = Instant.parse("2025-12-20T14:22:33.456Z")
      val prData = PullRequestData(
        url = "https://github.com/org/repo/pull/999",
        state = PRState.Open,
        number = 999,
        title = "Test timestamp"
      )
      val cachedPR = CachedPR(prData, specificTime)

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map.empty,
        progressCache = Map.empty,
        prCache = Map("TEST-1" -> cachedPR)
      )

      repo.write(state)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        val loadedCached = loadedState.prCache("TEST-1")
        assertEquals(loadedCached.fetchedAt, specificTime)
      }

  tempDir.test("Old state.json without prCache loads successfully"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")

      // Simulate old state.json format (no prCache field)
      val oldJsonContent = """{
        "worktrees": {},
        "issueCache": {},
        "progressCache": {}
      }"""
      Files.writeString(statePath, oldJsonContent)

      val repo = StateRepository(statePath.toString)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.worktrees.size, 0)
        assertEquals(loadedState.issueCache.size, 0)
        assertEquals(loadedState.progressCache.size, 0)
        assertEquals(loadedState.prCache.size, 0)
      }

  tempDir.test("StateRepository serializes ReviewState"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      import iw.core.model.{ReviewState, ReviewArtifact}
      val reviewState = ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
        message = Some("Ready for review"),
        artifacts = List(
          ReviewArtifact("Analysis", "project-management/issues/46/analysis.md"),
          ReviewArtifact("Context", "project-management/issues/46/phase-08-context.md")
        )
      )

      val state = ServerState(
        worktrees = Map.empty,
        issueCache = Map.empty,
        progressCache = Map.empty,
        prCache = Map.empty,
        reviewStateCache = Map.empty
      )

      val writeResult = repo.write(state)
      assert(writeResult.isRight, s"Write failed: $writeResult")

      // Verify file exists
      assert(Files.exists(statePath))

  tempDir.test("StateRepository serializes CachedReviewState"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      import iw.core.model.{ReviewState, ReviewArtifact, CachedReviewState}
      val reviewState = ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
        message = None,
        artifacts = List(ReviewArtifact("Test", "test.md"))
      )
      val cached = CachedReviewState(
        state = reviewState,
        filesMtime = Map("/path/to/review-state.json" -> 1000L)
      )

      val state = ServerState(
        worktrees = Map.empty,
        reviewStateCache = Map("IWLE-123" -> cached)
      )

      val writeResult = repo.write(state)
      assert(writeResult.isRight, s"Write failed: $writeResult")

      assert(Files.exists(statePath))

  tempDir.test("StateRepository deserializes reviewStateCache"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      import iw.core.model.{ReviewState, ReviewArtifact, CachedReviewState}
      val reviewState = ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
        message = Some("Phase 3 review"),
        artifacts = List(
          ReviewArtifact("Analysis", "analysis.md"),
          ReviewArtifact("Tasks", "tasks.md")
        )
      )
      val cached = CachedReviewState(
        state = reviewState,
        filesMtime = Map("/path/review-state.json" -> 2000L)
      )

      val state = ServerState(
        worktrees = Map.empty,
        reviewStateCache = Map("ISSUE-456" -> cached)
      )

      repo.write(state)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.reviewStateCache.size, 1)
        assert(loadedState.reviewStateCache.contains("ISSUE-456"))

        val loadedCached = loadedState.reviewStateCache("ISSUE-456")
        assertEquals(loadedCached.state.message, Some("Phase 3 review"))
        assertEquals(loadedCached.state.artifacts.size, 2)
        assertEquals(loadedCached.state.artifacts.head.label, "Analysis")
        assertEquals(loadedCached.filesMtime.size, 1)
      }

  tempDir.test("StateRepository handles missing reviewStateCache gracefully"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")

      // Simulate old state.json format (no reviewStateCache field)
      val oldJsonContent = """{
        "worktrees": {},
        "issueCache": {},
        "progressCache": {},
        "prCache": {}
      }"""
      Files.writeString(statePath, oldJsonContent)

      val repo = StateRepository(statePath.toString)
      val readResult = repo.read()

      assert(readResult.isRight)
      readResult.foreach { loadedState =>
        assertEquals(loadedState.reviewStateCache.size, 0)
      }

  tempDir.test("StateRepository concurrent writes don't corrupt state file"):
    tempDir =>
      val statePath = tempDir.resolve("state.json")
      val repo = StateRepository(statePath.toString)

      import java.util.concurrent.{Executors, TimeUnit, CountDownLatch}
      import scala.util.Random

      // Create thread pool for concurrent writes
      val threadCount = 10
      val executor = Executors.newFixedThreadPool(threadCount)
      val latch = new CountDownLatch(threadCount)

      try
        // Each thread writes a different worktree
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
                val state = ServerState(Map(s"IWLE-$i" -> worktree))

                // Add small random delay to increase chance of collision
                Thread.sleep(Random.nextInt(10))

                repo.write(state)
              finally
                latch.countDown()
          })
        }

        // Wait for all writes to complete
        latch.await(5, TimeUnit.SECONDS)
      finally
        executor.shutdown()

      // Verify state file is valid JSON (not corrupted)
      val readResult = repo.read()
      assert(readResult.isRight, "State file should be valid JSON after concurrent writes")

      // Verify no temp files left behind
      val tempFiles = Files.list(tempDir)
        .filter(p => p.getFileName.toString.contains(".tmp"))
        .toArray
      assertEquals(tempFiles.length, 0, "No temp files should remain after writes")
