// PURPOSE: Tests for DeletionSafety value object
package iw.tests

// PURPOSE: Verifies safety checks for worktree deletion operations
import iw.core.*

class DeletionSafetyTest extends munit.FunSuite:

  test("DeletionSafety.isSafe returns true when no issues"):
    val safety = DeletionSafety(
      hasUncommittedChanges = false,
      isActiveSession = false
    )
    assertEquals(DeletionSafety.isSafe(safety), true)

  test("DeletionSafety.isSafe returns false with uncommitted changes"):
    val safety = DeletionSafety(
      hasUncommittedChanges = true,
      isActiveSession = false
    )
    assertEquals(DeletionSafety.isSafe(safety), false)

  test("DeletionSafety.isSafe returns false when in active session"):
    val safety = DeletionSafety(
      hasUncommittedChanges = false,
      isActiveSession = true
    )
    assertEquals(DeletionSafety.isSafe(safety), false)

  test("DeletionSafety.isSafe returns false when both conditions"):
    val safety = DeletionSafety(
      hasUncommittedChanges = true,
      isActiveSession = true
    )
    assertEquals(DeletionSafety.isSafe(safety), false)
