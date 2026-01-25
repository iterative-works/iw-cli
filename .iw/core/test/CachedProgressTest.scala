// PURPOSE: Unit tests for CachedProgress domain model
// PURPOSE: Tests cache validation logic with file modification timestamps

package iw.core.domain

import munit.FunSuite
import iw.core.model.CachedProgress
import iw.core.model.WorkflowProgress
import iw.core.model.PhaseInfo

class CachedProgressTest extends FunSuite:

  val mockProgress = WorkflowProgress(
    currentPhase = Some(1),
    totalPhases = 1,
    phases = List(PhaseInfo(1, "Test", "/path", 10, 5)),
    overallCompleted = 5,
    overallTotal = 10
  )

  test("isValid returns true when all mtimes match"):
    val filesMtime = Map(
      "/path/phase-01.md" -> 1000L,
      "/path/phase-02.md" -> 2000L
    )
    val cached = CachedProgress(mockProgress, filesMtime)
    val currentMtimes = Map(
      "/path/phase-01.md" -> 1000L,
      "/path/phase-02.md" -> 2000L
    )

    assert(CachedProgress.isValid(cached, currentMtimes))

  test("isValid returns false when any mtime changed"):
    val filesMtime = Map("/path/phase-01.md" -> 1000L)
    val cached = CachedProgress(mockProgress, filesMtime)
    val currentMtimes = Map("/path/phase-01.md" -> 2000L) // Changed

    assert(!CachedProgress.isValid(cached, currentMtimes))

  test("isValid returns false when new file added"):
    val filesMtime = Map("/path/phase-01.md" -> 1000L)
    val cached = CachedProgress(mockProgress, filesMtime)
    val currentMtimes = Map(
      "/path/phase-01.md" -> 1000L,
      "/path/phase-02.md" -> 2000L  // New file
    )

    assert(!CachedProgress.isValid(cached, currentMtimes))

  test("isValid returns false when file removed"):
    val filesMtime = Map(
      "/path/phase-01.md" -> 1000L,
      "/path/phase-02.md" -> 2000L
    )
    val cached = CachedProgress(mockProgress, filesMtime)
    val currentMtimes = Map("/path/phase-01.md" -> 1000L) // phase-02 removed

    assert(!CachedProgress.isValid(cached, currentMtimes))

  test("isValid returns true when cache is empty and current is empty"):
    val cached = CachedProgress(mockProgress, Map.empty)
    val currentMtimes = Map.empty[String, Long]

    assert(CachedProgress.isValid(cached, currentMtimes))

  test("isValid returns false when multiple files have different mtimes"):
    val filesMtime = Map(
      "/path/phase-01.md" -> 1000L,
      "/path/phase-02.md" -> 2000L,
      "/path/phase-03.md" -> 3000L
    )
    val cached = CachedProgress(mockProgress, filesMtime)
    val currentMtimes = Map(
      "/path/phase-01.md" -> 1000L,  // Same
      "/path/phase-02.md" -> 2500L,  // Changed
      "/path/phase-03.md" -> 3000L   // Same
    )

    assert(!CachedProgress.isValid(cached, currentMtimes))

  test("isValid handles case when current mtime is older"):
    val filesMtime = Map("/path/phase-01.md" -> 2000L)
    val cached = CachedProgress(mockProgress, filesMtime)
    val currentMtimes = Map("/path/phase-01.md" -> 1000L) // Older mtime

    assert(!CachedProgress.isValid(cached, currentMtimes))
