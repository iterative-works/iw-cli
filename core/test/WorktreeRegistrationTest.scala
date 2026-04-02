// PURPOSE: Tests for WorktreeRegistration domain model
// PURPOSE: Verifies creation, validation, and immutability of worktree registrations

package iw.tests

import iw.core.model.WorktreeRegistration
import java.time.Instant
import iw.core.model.Issue

class WorktreeRegistrationTest extends munit.FunSuite:
  test("WorktreeRegistration creation with all fields"):
    val issueId = "IWLE-123"
    val path = "/home/user/projects/repo/worktrees/IWLE-123"
    val trackerType = "linear"
    val team = "IWLE"
    val registeredAt = Instant.parse("2025-12-19T10:30:00Z")
    val lastSeenAt = Instant.parse("2025-12-19T14:22:00Z")

    val registration = WorktreeRegistration(
      issueId = issueId,
      path = path,
      trackerType = trackerType,
      team = team,
      registeredAt = registeredAt,
      lastSeenAt = lastSeenAt
    )

    assertEquals(registration.issueId, issueId)
    assertEquals(registration.path, path)
    assertEquals(registration.trackerType, trackerType)
    assertEquals(registration.team, team)
    assertEquals(registration.registeredAt, registeredAt)
    assertEquals(registration.lastSeenAt, lastSeenAt)

  test("WorktreeRegistration rejects empty issue ID"):
    val result = WorktreeRegistration.create(
      issueId = "",
      path = "/some/path",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Issue ID cannot be empty")))
