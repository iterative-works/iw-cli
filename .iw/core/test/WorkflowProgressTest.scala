// PURPOSE: Unit tests for WorkflowProgress domain model
// PURPOSE: Tests overall workflow progress calculation and current phase detection

package iw.core.domain

import munit.FunSuite
import iw.core.model.WorkflowProgress
import iw.core.model.PhaseInfo

class WorkflowProgressTest extends FunSuite:

  test("currentPhaseInfo returns current phase details"):
    val phase1 = PhaseInfo(1, "Phase 1", "/p1", 10, 10)
    val phase2 = PhaseInfo(2, "Phase 2", "/p2", 15, 8)
    val progress = WorkflowProgress(
      currentPhase = Some(2),
      totalPhases = 2,
      phases = List(phase1, phase2),
      overallCompleted = 18,
      overallTotal = 25
    )

    assertEquals(progress.currentPhaseInfo.map(_.phaseNumber), Some(2))
    assertEquals(progress.currentPhaseInfo.map(_.phaseName), Some("Phase 2"))

  test("currentPhaseInfo returns None when no current phase"):
    val progress = WorkflowProgress(
      currentPhase = None,
      totalPhases = 0,
      phases = List.empty,
      overallCompleted = 0,
      overallTotal = 0
    )

    assertEquals(progress.currentPhaseInfo, None)

  test("currentPhaseInfo returns None when phase not found"):
    val phase1 = PhaseInfo(1, "Phase 1", "/p1", 10, 10)
    val progress = WorkflowProgress(
      currentPhase = Some(5), // Non-existent phase
      totalPhases = 1,
      phases = List(phase1),
      overallCompleted = 10,
      overallTotal = 10
    )

    assertEquals(progress.currentPhaseInfo, None)

  test("overallPercentage calculates across all phases"):
    val progress = WorkflowProgress(
      currentPhase = Some(2),
      totalPhases = 3,
      phases = List.empty, // Not needed for this test
      overallCompleted = 33,
      overallTotal = 120
    )

    assertEquals(progress.overallPercentage, 27) // 33/120 = 27.5% → 27

  test("overallPercentage returns 0 when total is 0"):
    val progress = WorkflowProgress(
      currentPhase = None,
      totalPhases = 0,
      phases = List.empty,
      overallCompleted = 0,
      overallTotal = 0
    )

    assertEquals(progress.overallPercentage, 0)

  test("overallPercentage returns 100 when all tasks complete"):
    val progress = WorkflowProgress(
      currentPhase = Some(3),
      totalPhases = 3,
      phases = List.empty,
      overallCompleted = 50,
      overallTotal = 50
    )

    assertEquals(progress.overallPercentage, 100)

  test("overallPercentage rounds down"):
    val progress = WorkflowProgress(
      currentPhase = Some(1),
      totalPhases = 1,
      phases = List.empty,
      overallCompleted = 2,
      overallTotal = 3
    )

    assertEquals(progress.overallPercentage, 66) // 2/3 = 66.66% → 66

  test("workflow with multiple phases stores all phase details"):
    val phase1 = PhaseInfo(1, "Setup", "/p1", 5, 5)
    val phase2 = PhaseInfo(2, "Implementation", "/p2", 20, 10)
    val phase3 = PhaseInfo(3, "Testing", "/p3", 15, 0)
    val phases = List(phase1, phase2, phase3)

    val progress = WorkflowProgress(
      currentPhase = Some(2),
      totalPhases = 3,
      phases = phases,
      overallCompleted = 15,
      overallTotal = 40
    )

    assertEquals(progress.phases.size, 3)
    assertEquals(progress.phases(0).phaseNumber, 1)
    assertEquals(progress.phases(1).phaseNumber, 2)
    assertEquals(progress.phases(2).phaseNumber, 3)
