// PURPOSE: Unit tests for BatchImplement model object
// PURPOSE: Tests pure decision functions for batch implementation orchestration

package iw.core.application

import munit.FunSuite
import iw.core.model.BatchImplement
import iw.core.model.PhaseOutcome
import iw.core.model.PhaseOutcome.*
import iw.core.model.PhaseIndexEntry

class BatchImplementTest extends FunSuite:

  // decideOutcome tests

  test("decideOutcome awaiting_review returns MergePR"):
    assertEquals(BatchImplement.decideOutcome("awaiting_review"), MergePR)

  test("decideOutcome phase_merged returns MarkDone"):
    assertEquals(BatchImplement.decideOutcome("phase_merged"), MarkDone)

  test("decideOutcome all_complete returns MarkDone"):
    assertEquals(BatchImplement.decideOutcome("all_complete"), MarkDone)

  test("decideOutcome context_ready returns Recover"):
    assertEquals(BatchImplement.decideOutcome("context_ready"), Recover)

  test("decideOutcome tasks_ready returns Recover"):
    assertEquals(BatchImplement.decideOutcome("tasks_ready"), Recover)

  test("decideOutcome implementing returns Recover"):
    assertEquals(BatchImplement.decideOutcome("implementing"), Recover)

  test("decideOutcome refactoring_complete returns Recover"):
    assertEquals(BatchImplement.decideOutcome("refactoring_complete"), Recover)

  test("decideOutcome review_failed returns Recover"):
    assertEquals(BatchImplement.decideOutcome("review_failed"), Recover)

  test("decideOutcome bogus_status returns Fail with descriptive reason"):
    BatchImplement.decideOutcome("bogus_status") match
      case Fail(reason) => assert(reason.nonEmpty, "Expected non-empty reason")
      case other => fail(s"Expected Fail but got $other")

  // isTerminal tests

  test("isTerminal all_complete returns true"):
    assert(BatchImplement.isTerminal("all_complete"))

  test("isTerminal phase_merged returns true"):
    assert(BatchImplement.isTerminal("phase_merged"))

  test("isTerminal implementing returns false"):
    assert(!BatchImplement.isTerminal("implementing"))

  test("isTerminal awaiting_review returns false"):
    assert(!BatchImplement.isTerminal("awaiting_review"))

  test("isTerminal review_failed returns false"):
    assert(!BatchImplement.isTerminal("review_failed"))

  test("isTerminal unknown returns false"):
    assert(!BatchImplement.isTerminal("unknown"))

  // nextPhase tests

  test("nextPhase empty list returns None"):
    assertEquals(BatchImplement.nextPhase(Nil), None)

  test("nextPhase all phases complete returns None"):
    val phases = List(
      PhaseIndexEntry(1, isComplete = true, "First"),
      PhaseIndexEntry(2, isComplete = true, "Second")
    )
    assertEquals(BatchImplement.nextPhase(phases), None)

  test("nextPhase first phase incomplete returns Some(1)"):
    val phases = List(
      PhaseIndexEntry(1, isComplete = false, "First"),
      PhaseIndexEntry(2, isComplete = true, "Second")
    )
    assertEquals(BatchImplement.nextPhase(phases), Some(1))

  test("nextPhase phase 1 done and phase 2 not done returns Some(2)"):
    val phases = List(
      PhaseIndexEntry(1, isComplete = true, "First"),
      PhaseIndexEntry(2, isComplete = false, "Second"),
      PhaseIndexEntry(3, isComplete = false, "Third")
    )
    assertEquals(BatchImplement.nextPhase(phases), Some(2))

  test("nextPhase only last phase incomplete returns Some(N)"):
    val phases = List(
      PhaseIndexEntry(1, isComplete = true, "First"),
      PhaseIndexEntry(2, isComplete = true, "Second"),
      PhaseIndexEntry(3, isComplete = false, "Third")
    )
    assertEquals(BatchImplement.nextPhase(phases), Some(3))

  test("nextPhase single incomplete phase returns its number"):
    val phases = List(PhaseIndexEntry(5, isComplete = false, "Only"))
    assertEquals(BatchImplement.nextPhase(phases), Some(5))

  // resolveWorkflowCode tests

  test("resolveWorkflowCode agile returns Right(ag)"):
    assertEquals(BatchImplement.resolveWorkflowCode(Some("agile")), Right("ag"))

  test("resolveWorkflowCode waterfall returns Right(wf)"):
    assertEquals(BatchImplement.resolveWorkflowCode(Some("waterfall")), Right("wf"))

  test("resolveWorkflowCode diagnostic returns Right(dx)"):
    assertEquals(BatchImplement.resolveWorkflowCode(Some("diagnostic")), Right("dx"))

  test("resolveWorkflowCode None returns Left"):
    BatchImplement.resolveWorkflowCode(None) match
      case Left(_) => () // expected
      case Right(v) => fail(s"Expected Left but got Right($v)")

  test("resolveWorkflowCode unknown returns Left"):
    BatchImplement.resolveWorkflowCode(Some("unknown")) match
      case Left(_) => () // expected
      case Right(v) => fail(s"Expected Left but got Right($v)")

  // markPhaseComplete tests

  val sampleTasks =
    """|# Tasks
       |
       |- [ ] Phase 1: Setup environment
       |- [ ] Phase 2: Implement core logic
       |- [x] Phase 3: Write tests
       |""".stripMargin

  test("markPhaseComplete unchecked phase returns content with that phase checked"):
    val result = BatchImplement.markPhaseComplete(sampleTasks, 1)
    result match
      case Right(content) =>
        assert(content.contains("- [x] Phase 1:"), s"Expected checked Phase 1 in: $content")
      case Left(err) => fail(s"Expected Right but got Left($err)")

  test("markPhaseComplete already-checked phase returns Left"):
    val result = BatchImplement.markPhaseComplete(sampleTasks, 3)
    result match
      case Left(_) => () // expected
      case Right(content) => fail(s"Expected Left but got Right")

  test("markPhaseComplete non-existent phase number returns Left"):
    val result = BatchImplement.markPhaseComplete(sampleTasks, 99)
    result match
      case Left(_) => () // expected
      case Right(_) => fail("Expected Left but got Right")

  test("markPhaseComplete with multiple phases only checks the target phase"):
    val result = BatchImplement.markPhaseComplete(sampleTasks, 2)
    result match
      case Right(content) =>
        assert(content.contains("- [x] Phase 2:"), s"Expected Phase 2 checked in: $content")
        assert(content.contains("- [ ] Phase 1:"), s"Expected Phase 1 still unchecked in: $content")
      case Left(err) => fail(s"Expected Right but got Left($err)")

  test("markPhaseComplete preserves all other content unchanged"):
    val result = BatchImplement.markPhaseComplete(sampleTasks, 1)
    result match
      case Right(content) =>
        assert(content.contains("- [ ] Phase 2:"), "Phase 2 should remain unchecked")
        assert(content.contains("- [x] Phase 3:"), "Phase 3 should remain checked")
        assert(content.contains("# Tasks"), "Header should be preserved")
      case Left(err) => fail(s"Expected Right but got Left($err)")

  // Zero-padded phase number tests

  val zeroPaddedTasks =
    """|# Tasks
       |
       |- [ ] Phase 01: Setup environment
       |- [ ] Phase 02: Implement core logic
       |- [x] Phase 03: Write tests
       |""".stripMargin

  test("markPhaseComplete handles zero-padded phase numbers"):
    val result = BatchImplement.markPhaseComplete(zeroPaddedTasks, 1)
    result match
      case Right(content) =>
        assert(content.contains("- [x] Phase 01:"), s"Expected checked Phase 01 in: $content")
      case Left(err) => fail(s"Expected Right but got Left($err)")

  test("markPhaseComplete with zero-padded numbers only checks the target phase"):
    val result = BatchImplement.markPhaseComplete(zeroPaddedTasks, 2)
    result match
      case Right(content) =>
        assert(content.contains("- [x] Phase 02:"), s"Expected Phase 02 checked in: $content")
        assert(content.contains("- [ ] Phase 01:"), s"Expected Phase 01 still unchecked in: $content")
      case Left(err) => fail(s"Expected Right but got Left($err)")

  test("markPhaseComplete recognizes already-checked zero-padded phase"):
    val result = BatchImplement.markPhaseComplete(zeroPaddedTasks, 3)
    result match
      case Left(msg) => assert(msg.contains("already marked complete"))
      case Right(_) => fail("Expected Left but got Right")

  test("markPhaseComplete with zero-padded non-existent phase returns Left"):
    val result = BatchImplement.markPhaseComplete(zeroPaddedTasks, 99)
    result match
      case Left(_) => () // expected
      case Right(_) => fail("Expected Left but got Right")
