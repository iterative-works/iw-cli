// PURPOSE: Integration tests for StateRepository JSON persistence
// PURPOSE: Verifies atomic writes, error handling, and state file management

package iw.tests

import iw.core.domain.{ServerState, WorktreeRegistration}
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
