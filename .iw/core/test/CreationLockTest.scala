// PURPOSE: Unit tests for CreationLock domain value object
// PURPOSE: Verifies construction and field validation of creation lock

package iw.core.domain

import munit.FunSuite
import java.time.Instant
import iw.core.dashboard.domain.CreationLock

class CreationLockTest extends FunSuite:

  test("CreationLock construction with valid fields succeeds"):
    val now = Instant.now()
    val lock = CreationLock(
      issueId = "IW-79",
      startedAt = now
    )

    assertEquals(lock.issueId, "IW-79")
    assertEquals(lock.startedAt, now)

  test("CreationLock fields are accessible"):
    val now = Instant.now()
    val lock = CreationLock("IWLE-123", now)

    assert(lock.issueId.nonEmpty)
    assert(lock.startedAt != null)

  test("CreationLock with different issue IDs are distinct"):
    val now = Instant.now()
    val lock1 = CreationLock("IW-1", now)
    val lock2 = CreationLock("IW-2", now)

    assertNotEquals(lock1, lock2)
    assertNotEquals(lock1.issueId, lock2.issueId)

  test("CreationLock with different timestamps are distinct"):
    val now = Instant.now()
    val later = now.plusSeconds(10)
    val lock1 = CreationLock("IW-79", now)
    val lock2 = CreationLock("IW-79", later)

    assertNotEquals(lock1, lock2)
    assertNotEquals(lock1.startedAt, lock2.startedAt)
