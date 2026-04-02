// PURPOSE: Unit tests for ServerStatus domain model
// PURPOSE: Validates status fields and JSON serialization

package iw.core.test

import iw.core.model.ServerStatus
import java.time.Instant

class ServerStatusTest extends munit.FunSuite:

  test("Create ServerStatus with all fields"):
    val startedAt = Instant.parse("2025-12-20T10:00:00Z")
    val status = ServerStatus(
      running = true,
      port = 9876,
      worktreeCount = 5,
      startedAt = Some(startedAt),
      pid = Some(12345L)
    )

    assert(status.running)
    assertEquals(status.port, 9876)
    assertEquals(status.worktreeCount, 5)
    assertEquals(status.startedAt, Some(startedAt))
    assertEquals(status.pid, Some(12345L))

  test("Create ServerStatus for stopped server"):
    val status = ServerStatus(
      running = false,
      port = 0,
      worktreeCount = 0,
      startedAt = None,
      pid = None
    )

    assert(!status.running)
    assertEquals(status.port, 0)
    assertEquals(status.worktreeCount, 0)
    assertEquals(status.startedAt, None)
    assertEquals(status.pid, None)

  test("ServerStatus JSON serialization"):
    val startedAt = Instant.parse("2025-12-20T10:00:00Z")
    val status = ServerStatus(
      running = true,
      port = 8080,
      worktreeCount = 3,
      startedAt = Some(startedAt),
      pid = Some(54321L)
    )

    val json = upickle.default.write(status)
    val deserialized = upickle.default.read[ServerStatus](json)

    assertEquals(deserialized.running, status.running)
    assertEquals(deserialized.port, status.port)
    assertEquals(deserialized.worktreeCount, status.worktreeCount)
    assertEquals(deserialized.startedAt, status.startedAt)
    assertEquals(deserialized.pid, status.pid)
