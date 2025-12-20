// PURPOSE: Unit tests for WorktreeRegistrationService business logic
// PURPOSE: Verifies registration, updates, and validation using pure functions

package iw.core.service

import munit.FunSuite
import iw.core.domain.{ServerState, WorktreeRegistration}
import java.time.Instant

class WorktreeRegistrationServiceTest extends FunSuite:

  test("register creates new WorktreeRegistration with current timestamp"):
    val state = ServerState(Map.empty)
    val before = Instant.now()

    val result = WorktreeRegistrationService.register(
      "IWLE-123",
      "/path/to/worktree",
      "Linear",
      "IWLE",
      state
    )

    val after = Instant.now()

    assert(result.isRight)
    result.foreach { newState =>
      val registration = newState.worktrees.get("IWLE-123")
      assert(registration.isDefined)
      registration.foreach { reg =>
        assertEquals(reg.issueId, "IWLE-123")
        assertEquals(reg.path, "/path/to/worktree")
        assertEquals(reg.trackerType, "Linear")
        assertEquals(reg.team, "IWLE")
        assert(!reg.registeredAt.isBefore(before))
        assert(!reg.registeredAt.isAfter(after))
        assert(!reg.lastSeenAt.isBefore(before))
        assert(!reg.lastSeenAt.isAfter(after))
      }
    }

  test("register adds WorktreeRegistration to ServerState.worktrees map"):
    val state = ServerState(Map.empty)

    val result = WorktreeRegistrationService.register(
      "IWLE-456",
      "/another/path",
      "YouTrack",
      "IWLE",
      state
    )

    assert(result.isRight)
    result.foreach { newState =>
      assert(newState.worktrees.contains("IWLE-456"))
      assertEquals(newState.worktrees.size, 1)
    }

  test("register returns Right with new ServerState on success"):
    val state = ServerState(Map.empty)

    val result = WorktreeRegistrationService.register(
      "IWLE-789",
      "/path",
      "Linear",
      "IWLE",
      state
    )

    assert(result.isRight)
    result.foreach { newState =>
      assertNotEquals(newState, state)
      assert(newState.worktrees.nonEmpty)
    }

  test("register updates existing worktree's lastSeenAt timestamp"):
    val now = Instant.now()
    val earlier = now.minusSeconds(3600)
    val existingReg = WorktreeRegistration(
      "IWLE-123",
      "/path",
      "Linear",
      "IWLE",
      earlier,
      earlier
    )
    val state = ServerState(Map("IWLE-123" -> existingReg))

    Thread.sleep(10) // Ensure time difference

    val result = WorktreeRegistrationService.register(
      "IWLE-123",
      "/path",
      "Linear",
      "IWLE",
      state
    )

    assert(result.isRight)
    result.foreach { newState =>
      val newReg = newState.worktrees.get("IWLE-123")
      assert(newReg.isDefined)
      newReg.foreach { reg =>
        assert(reg.lastSeenAt.isAfter(earlier))
      }
    }

  test("register preserves registeredAt timestamp on update"):
    val now = Instant.now()
    val earlier = now.minusSeconds(3600)
    val existingReg = WorktreeRegistration(
      "IWLE-123",
      "/path",
      "Linear",
      "IWLE",
      earlier,
      earlier
    )
    val state = ServerState(Map("IWLE-123" -> existingReg))

    val result = WorktreeRegistrationService.register(
      "IWLE-123",
      "/path",
      "Linear",
      "IWLE",
      state
    )

    assert(result.isRight)
    result.foreach { newState =>
      val newReg = newState.worktrees.get("IWLE-123")
      assert(newReg.isDefined)
      newReg.foreach { reg =>
        assertEquals(reg.registeredAt, earlier)
      }
    }

  test("register updates path/trackerType/team if changed"):
    val now = Instant.now()
    val earlier = now.minusSeconds(3600)
    val existingReg = WorktreeRegistration(
      "IWLE-123",
      "/old/path",
      "Linear",
      "IWLE",
      earlier,
      earlier
    )
    val state = ServerState(Map("IWLE-123" -> existingReg))

    val result = WorktreeRegistrationService.register(
      "IWLE-123",
      "/new/path",
      "YouTrack",
      "NEWTEAM",
      state
    )

    assert(result.isRight)
    result.foreach { newState =>
      val newReg = newState.worktrees.get("IWLE-123")
      assert(newReg.isDefined)
      newReg.foreach { reg =>
        assertEquals(reg.path, "/new/path")
        assertEquals(reg.trackerType, "YouTrack")
        assertEquals(reg.team, "NEWTEAM")
      }
    }

  test("register returns Left for invalid issue ID format"):
    val state = ServerState(Map.empty)

    val result = WorktreeRegistrationService.register(
      "",
      "/path",
      "Linear",
      "IWLE",
      state
    )

    assert(result.isLeft)

  test("register returns Left for empty path"):
    val state = ServerState(Map.empty)

    val result = WorktreeRegistrationService.register(
      "IWLE-123",
      "",
      "Linear",
      "IWLE",
      state
    )

    assert(result.isLeft)

  test("register returns Left for invalid tracker type"):
    val state = ServerState(Map.empty)

    val result = WorktreeRegistrationService.register(
      "IWLE-123",
      "/path",
      "",
      "IWLE",
      state
    )

    assert(result.isLeft)

  test("updateLastSeen updates timestamp for existing worktree"):
    val now = Instant.now()
    val earlier = now.minusSeconds(3600)
    val existingReg = WorktreeRegistration(
      "IWLE-123",
      "/path",
      "Linear",
      "IWLE",
      earlier,
      earlier
    )
    val state = ServerState(Map("IWLE-123" -> existingReg))

    Thread.sleep(10) // Ensure time difference

    val result = WorktreeRegistrationService.updateLastSeen("IWLE-123", state)

    assert(result.isRight)
    result.foreach { newState =>
      val newReg = newState.worktrees.get("IWLE-123")
      assert(newReg.isDefined)
      newReg.foreach { reg =>
        assert(reg.lastSeenAt.isAfter(earlier))
      }
    }

  test("updateLastSeen returns Left for non-existent worktree"):
    val state = ServerState(Map.empty)

    val result = WorktreeRegistrationService.updateLastSeen("IWLE-999", state)

    assert(result.isLeft)
    result.swap.foreach { error =>
      assert(error.contains("not found") || error.contains("not registered"))
    }

  test("updateLastSeen preserves all other fields unchanged"):
    val now = Instant.now()
    val earlier = now.minusSeconds(3600)
    val existingReg = WorktreeRegistration(
      "IWLE-123",
      "/original/path",
      "Linear",
      "IWLE",
      earlier,
      earlier
    )
    val state = ServerState(Map("IWLE-123" -> existingReg))

    val result = WorktreeRegistrationService.updateLastSeen("IWLE-123", state)

    assert(result.isRight)
    result.foreach { newState =>
      val newReg = newState.worktrees.get("IWLE-123")
      assert(newReg.isDefined)
      newReg.foreach { reg =>
        assertEquals(reg.issueId, "IWLE-123")
        assertEquals(reg.path, "/original/path")
        assertEquals(reg.trackerType, "Linear")
        assertEquals(reg.team, "IWLE")
        assertEquals(reg.registeredAt, earlier)
      }
    }
