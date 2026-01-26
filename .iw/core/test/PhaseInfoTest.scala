// PURPOSE: Unit tests for PhaseInfo domain model
// PURPOSE: Tests phase completion status and progress calculation

package iw.core.domain

import munit.FunSuite
import iw.core.model.PhaseInfo

class PhaseInfoTest extends FunSuite:

  test("isComplete returns true when all tasks done"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 10, completedTasks = 10)
    assert(phase.isComplete)

  test("isComplete returns false when tasks incomplete"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 10, completedTasks = 5)
    assert(!phase.isComplete)

  test("isInProgress returns true for partial completion"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 10, completedTasks = 5)
    assert(phase.isInProgress)
    assert(!phase.isComplete)
    assert(!phase.notStarted)

  test("isInProgress returns false when not started"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 10, completedTasks = 0)
    assert(!phase.isInProgress)

  test("isInProgress returns false when complete"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 10, completedTasks = 10)
    assert(!phase.isInProgress)

  test("notStarted returns true when zero tasks done"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 10, completedTasks = 0)
    assert(phase.notStarted)
    assert(!phase.isInProgress)
    assert(!phase.isComplete)

  test("notStarted returns false when tasks in progress"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 10, completedTasks = 5)
    assert(!phase.notStarted)

  test("progressPercentage calculates correctly"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 15, completedTasks = 8)
    assertEquals(phase.progressPercentage, 53) // 8/15 = 53.33% → 53

  test("progressPercentage rounds down"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 3, completedTasks = 2)
    assertEquals(phase.progressPercentage, 66) // 2/3 = 66.66% → 66

  test("progressPercentage returns 0 for empty phase"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 0, completedTasks = 0)
    assertEquals(phase.progressPercentage, 0)

  test("progressPercentage returns 100 for complete phase"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 20, completedTasks = 20)
    assertEquals(phase.progressPercentage, 100)

  test("empty phase (0 tasks) is not considered complete"):
    val phase = PhaseInfo(1, "Test Phase", "/path", totalTasks = 0, completedTasks = 0)
    assert(!phase.isComplete)
    assert(!phase.isInProgress)
    assert(!phase.notStarted)
