// PURPOSE: Unit tests for CreationLockRegistry thread-safe lock management
// PURPOSE: Verifies lock acquisition, release, and cleanup operations

package iw.core.infrastructure

import munit.FunSuite
import java.time.Duration

class CreationLockRegistryTest extends FunSuite:

  // Reset registry before each test to ensure isolation
  override def beforeEach(context: BeforeEach): Unit =
    CreationLockRegistry.clear()

  test("tryAcquire returns true when issue is not locked"):
    val acquired = CreationLockRegistry.tryAcquire("IW-79")
    assert(acquired, "Should successfully acquire lock for unlocked issue")

  test("tryAcquire returns false when issue is already locked"):
    val firstAcquire = CreationLockRegistry.tryAcquire("IW-79")
    val secondAcquire = CreationLockRegistry.tryAcquire("IW-79")

    assert(firstAcquire, "First acquire should succeed")
    assert(!secondAcquire, "Second acquire should fail when already locked")

  test("release allows subsequent acquire"):
    CreationLockRegistry.tryAcquire("IW-79")
    CreationLockRegistry.release("IW-79")
    val acquired = CreationLockRegistry.tryAcquire("IW-79")

    assert(acquired, "Should allow acquire after release")

  test("isLocked returns true when issue is locked"):
    CreationLockRegistry.tryAcquire("IW-79")
    assert(CreationLockRegistry.isLocked("IW-79"), "Should report as locked")

  test("isLocked returns false when issue is not locked"):
    assert(!CreationLockRegistry.isLocked("IW-79"), "Should report as not locked")

  test("isLocked returns false after release"):
    CreationLockRegistry.tryAcquire("IW-79")
    CreationLockRegistry.release("IW-79")
    assert(!CreationLockRegistry.isLocked("IW-79"), "Should report as not locked after release")

  test("cleanupExpired removes old locks"):
    // Acquire lock (this will be recent)
    CreationLockRegistry.tryAcquire("IW-79")

    // Clean up locks older than 0 seconds (should remove all)
    Thread.sleep(10) // Small delay to ensure time has passed
    CreationLockRegistry.cleanupExpired(Duration.ofMillis(1))

    // Should be able to acquire again after cleanup
    val acquired = CreationLockRegistry.tryAcquire("IW-79")
    assert(acquired, "Should allow acquire after expired lock cleanup")

  test("cleanupExpired does not remove recent locks"):
    CreationLockRegistry.tryAcquire("IW-79")

    // Clean up locks older than 1 hour (should not remove recent lock)
    CreationLockRegistry.cleanupExpired(Duration.ofHours(1))

    // Should still be locked
    assert(CreationLockRegistry.isLocked("IW-79"), "Recent lock should not be removed")

  test("different issues can be locked independently"):
    val acquired1 = CreationLockRegistry.tryAcquire("IW-79")
    val acquired2 = CreationLockRegistry.tryAcquire("IW-80")

    assert(acquired1, "Should acquire lock for IW-79")
    assert(acquired2, "Should acquire lock for IW-80")

  test("release does not affect other locked issues"):
    CreationLockRegistry.tryAcquire("IW-79")
    CreationLockRegistry.tryAcquire("IW-80")
    CreationLockRegistry.release("IW-79")

    assert(!CreationLockRegistry.isLocked("IW-79"), "IW-79 should be released")
    assert(CreationLockRegistry.isLocked("IW-80"), "IW-80 should still be locked")

  test("release on non-existent lock does not throw"):
    // Should not throw exception
    CreationLockRegistry.release("NON-EXISTENT")
    assert(true, "Release should be safe even if lock doesn't exist")
