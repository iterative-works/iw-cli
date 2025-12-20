// PURPOSE: Unit tests for WorkflowProgressService application service
// PURPOSE: Tests progress computation, current phase detection, and cache integration

package iw.core.application

import munit.FunSuite
import iw.core.domain.{PhaseInfo, WorkflowProgress, CachedProgress}

class WorkflowProgressServiceTest extends FunSuite:

  test("computeProgress sums task counts across phases"):
    val phases = List(
      PhaseInfo(1, "Phase 1", "/p1", 10, 10),
      PhaseInfo(2, "Phase 2", "/p2", 15, 8),
      PhaseInfo(3, "Phase 3", "/p3", 20, 0)
    )

    val progress = WorkflowProgressService.computeProgress(phases)

    assertEquals(progress.overallTotal, 45)
    assertEquals(progress.overallCompleted, 18)
    assertEquals(progress.totalPhases, 3)
    assertEquals(progress.phases, phases)

  test("computeProgress handles empty phase list"):
    val progress = WorkflowProgressService.computeProgress(List.empty)

    assertEquals(progress.overallTotal, 0)
    assertEquals(progress.overallCompleted, 0)
    assertEquals(progress.totalPhases, 0)
    assertEquals(progress.currentPhase, None)

  test("computeProgress calculates overall percentage correctly"):
    val phases = List(
      PhaseInfo(1, "Phase 1", "/p1", 100, 33)
    )
    val progress = WorkflowProgressService.computeProgress(phases)

    assertEquals(progress.overallPercentage, 33)

  test("determineCurrentPhase returns first incomplete phase"):
    val phases = List(
      PhaseInfo(1, "Phase 1", "/p1", 10, 10), // Complete
      PhaseInfo(2, "Phase 2", "/p2", 15, 8),  // In progress
      PhaseInfo(3, "Phase 3", "/p3", 20, 0)   // Not started
    )

    val current = WorkflowProgressService.determineCurrentPhase(phases)
    assertEquals(current, Some(2))

  test("determineCurrentPhase returns last phase if all complete"):
    val phases = List(
      PhaseInfo(1, "Phase 1", "/p1", 10, 10),
      PhaseInfo(2, "Phase 2", "/p2", 15, 15)
    )

    val current = WorkflowProgressService.determineCurrentPhase(phases)
    assertEquals(current, Some(2))

  test("determineCurrentPhase returns None for empty list"):
    val current = WorkflowProgressService.determineCurrentPhase(List.empty)
    assertEquals(current, None)

  test("determineCurrentPhase returns first not-started phase if no in-progress"):
    val phases = List(
      PhaseInfo(1, "Phase 1", "/p1", 10, 10), // Complete
      PhaseInfo(2, "Phase 2", "/p2", 15, 0),  // Not started
      PhaseInfo(3, "Phase 3", "/p3", 20, 0)   // Not started
    )

    val current = WorkflowProgressService.determineCurrentPhase(phases)
    assertEquals(current, Some(2))

  test("fetchProgress uses cache when mtimes match"):
    val phase1 = PhaseInfo(1, "Phase 1", "/worktree/project-management/issues/ISSUE-123/phase-01-tasks.md", 10, 5)
    val cachedProgress = WorkflowProgress(Some(1), 1, List(phase1), 5, 10)
    val filesMtime = Map("/worktree/project-management/issues/ISSUE-123/phase-01-tasks.md" -> 1000L)
    val cache = Map("ISSUE-123" -> CachedProgress(cachedProgress, filesMtime))

    var readFileCalled = false
    val readFile = (path: String) => {
      readFileCalled = true
      Right(Seq.empty)
    }

    val getMtime = (path: String) =>
      // Only return success for phase-01, fail for others
      if path.contains("phase-01-tasks.md") then Right(1000L)
      else Left("File not found")

    val result = WorkflowProgressService.fetchProgress(
      "ISSUE-123", "/worktree", cache, readFile, getMtime
    )

    assert(result.isRight)
    assertEquals(result.toOption.get.overallCompleted, 5)
    assert(!readFileCalled, "readFile should not be called when cache is valid")

  test("fetchProgress re-parses when mtime changed"):
    val phase1 = PhaseInfo(1, "Phase 1", "/old/path", 10, 5)
    val cachedProgress = WorkflowProgress(Some(1), 1, List(phase1), 5, 10)
    val filesMtime = Map("/worktree/project-management/issues/ISSUE-123/phase-01-tasks.md" -> 1000L)
    val cache = Map("ISSUE-123" -> CachedProgress(cachedProgress, filesMtime))

    val readFile = (path: String) =>
      if path.contains("phase-01-tasks.md") then
        Right(Seq(
          "# Phase 1: New Phase",
          "- [x] Task 1",
          "- [x] Task 2",
          "- [ ] Task 3"
        ))
      else
        Left("File not found")

    val getMtime = (path: String) =>
      if path.contains("phase-01-tasks.md") then Right(2000L) // Changed mtime
      else Left("File not found")

    val result = WorkflowProgressService.fetchProgress(
      "ISSUE-123", "/worktree", cache, readFile, getMtime
    )

    assert(result.isRight)
    val progress = result.toOption.get
    assertEquals(progress.overallTotal, 3)
    assertEquals(progress.overallCompleted, 2)

  test("fetchProgress handles missing directory gracefully"):
    val readFile = (path: String) => Left("Directory not found")
    val getMtime = (path: String) => Left("Directory not found")

    val result = WorkflowProgressService.fetchProgress(
      "ISSUE-123", "/nonexistent", Map.empty, readFile, getMtime
    )

    assert(result.isLeft)

  test("fetchProgress handles read errors gracefully"):
    val readFile = (path: String) => Left("Permission denied")
    val getMtime = (path: String) => Right(1000L)

    val result = WorkflowProgressService.fetchProgress(
      "ISSUE-123", "/worktree", Map.empty, readFile, getMtime
    )

    assert(result.isLeft)

  test("fetchProgress parses multiple phase files correctly"):
    val readFile = (path: String) =>
      if path.contains("phase-01") then
        Right(Seq(
          "# Phase 1: Setup",
          "- [x] Task 1",
          "- [x] Task 2"
        ))
      else if path.contains("phase-02") then
        Right(Seq(
          "# Phase 2: Implementation",
          "- [x] Task 1",
          "- [ ] Task 2",
          "- [ ] Task 3"
        ))
      else
        Left("File not found")

    val getMtime = (path: String) =>
      if path.contains("phase-01") || path.contains("phase-02") then Right(1000L)
      else Left("File not found")

    val result = WorkflowProgressService.fetchProgress(
      "ISSUE-123", "/worktree", Map.empty, readFile, getMtime
    )

    assert(result.isRight)
    val progress = result.toOption.get
    assertEquals(progress.totalPhases, 2)
    assertEquals(progress.overallTotal, 5)
    assertEquals(progress.overallCompleted, 3)
    assertEquals(progress.currentPhase, Some(2)) // Phase 2 is in progress
