// PURPOSE: Integration tests for StateRepository JSON persistence
// PURPOSE: Verifies atomic writes, error handling, and state file management

package iw.tests

import iw.core.domain.{ServerState, WorktreeRegistration, IssueData, CachedIssue, PhaseInfo, WorkflowProgress, CachedProgress}
import iw.core.infrastructure.StateRepository
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
        status = "Done",
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
        assertEquals(loadedCached.data.status, "Done")
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
        status = "Open",
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
