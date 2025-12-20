// PURPOSE: Unit tests for MarkdownTaskParser application service
// PURPOSE: Tests checkbox counting and phase name extraction from markdown

package iw.core.application

import munit.FunSuite

class MarkdownTaskParserTest extends FunSuite:

  test("parseTasks counts incomplete checkbox"):
    val lines = Seq("- [ ] Task 1", "- [ ] Task 2")
    val count = MarkdownTaskParser.parseTasks(lines)
    assertEquals(count.total, 2)
    assertEquals(count.completed, 0)

  test("parseTasks counts completed checkbox"):
    val lines = Seq("- [x] Task 1", "- [x] Task 2")
    val count = MarkdownTaskParser.parseTasks(lines)
    assertEquals(count.total, 2)
    assertEquals(count.completed, 2)

  test("parseTasks counts completed checkbox case-insensitive"):
    val lines = Seq("- [X] Task 1", "- [x] Task 2")
    val count = MarkdownTaskParser.parseTasks(lines)
    assertEquals(count.total, 2)
    assertEquals(count.completed, 2)

  test("parseTasks counts mixed checkboxes correctly"):
    val lines = Seq(
      "- [x] Task 1",
      "- [ ] Task 2",
      "- [x] Task 3",
      "- [ ] Task 4"
    )
    val count = MarkdownTaskParser.parseTasks(lines)
    assertEquals(count.total, 4)
    assertEquals(count.completed, 2)

  test("parseTasks ignores non-checkbox bullets"):
    val lines = Seq(
      "- [x] Checkbox task",
      "- Regular bullet",
      "* Asterisk bullet",
      "+ Plus bullet",
      "1. Numbered list"
    )
    val count = MarkdownTaskParser.parseTasks(lines)
    assertEquals(count.total, 1)
    assertEquals(count.completed, 1)

  test("parseTasks handles indented checkboxes"):
    val lines = Seq(
      "- [x] Task 1",
      "  - [ ] Subtask 1.1",
      "    - [x] Subtask 1.1.1"
    )
    val count = MarkdownTaskParser.parseTasks(lines)
    assertEquals(count.total, 3)
    assertEquals(count.completed, 2)

  test("parseTasks handles empty input"):
    val count = MarkdownTaskParser.parseTasks(Seq.empty)
    assertEquals(count.total, 0)
    assertEquals(count.completed, 0)

  test("parseTasks ignores malformed checkboxes"):
    val lines = Seq(
      "- [x] Valid task",
      "- [y] Invalid marker",
      "- [] Missing marker",
      "-[x] Missing space",
      "[ ] No dash"
    )
    val count = MarkdownTaskParser.parseTasks(lines)
    assertEquals(count.total, 1)
    assertEquals(count.completed, 1)

  test("extractPhaseName parses 'Phase N Tasks: Name' format"):
    val lines = Seq(
      "# Phase 2 Tasks: Automatic worktree registration",
      "",
      "Content here"
    )
    val name = MarkdownTaskParser.extractPhaseName(lines)
    assertEquals(name, Some("Automatic worktree registration"))

  test("extractPhaseName parses 'Phase N: Name' format"):
    val lines = Seq("# Phase 3: Server lifecycle management")
    val name = MarkdownTaskParser.extractPhaseName(lines)
    assertEquals(name, Some("Server lifecycle management"))

  test("extractPhaseName returns None for missing header"):
    val lines = Seq("No header here", "Just content")
    val name = MarkdownTaskParser.extractPhaseName(lines)
    assertEquals(name, None)

  test("extractPhaseName handles multiple headers (picks first)"):
    val lines = Seq(
      "# Phase 1: First Phase",
      "## Some section",
      "# Phase 2: Second Phase"
    )
    val name = MarkdownTaskParser.extractPhaseName(lines)
    assertEquals(name, Some("First Phase"))

  test("extractPhaseName trims whitespace"):
    val lines = Seq("# Phase 4:   Extra whitespace   ")
    val name = MarkdownTaskParser.extractPhaseName(lines)
    assertEquals(name, Some("Extra whitespace"))

  test("extractPhaseName handles header without colon"):
    val lines = Seq("# Phase 5 Implementation")
    val name = MarkdownTaskParser.extractPhaseName(lines)
    assertEquals(name, None) // No colon means no match

  test("parseTasks handles checkboxes with extra whitespace"):
    val lines = Seq(
      "-   [x]   Task with spaces",
      "-  [ ]  Another task"
    )
    val count = MarkdownTaskParser.parseTasks(lines)
    assertEquals(count.total, 2)
    assertEquals(count.completed, 1)

  test("parseTasks handles checkboxes at various indentation levels"):
    val lines = Seq(
      "- [x] Level 0",
      " - [x] Level 1",
      "  - [ ] Level 2",
      "   - [x] Level 3",
      "    - [ ] Level 4"
    )
    val count = MarkdownTaskParser.parseTasks(lines)
    assertEquals(count.total, 5)
    assertEquals(count.completed, 3)

  // Phase Index parsing tests

  test("parsePhaseIndex extracts completed phases"):
    val lines = Seq(
      "## Phase Index",
      "",
      "- [x] Phase 1: View basic dashboard (Est: 8-12h)",
      "- [x] Phase 2: Automatic registration (Est: 6-8h)",
      "- [ ] Phase 3: Server lifecycle (Est: 6-8h)"
    )
    val index = MarkdownTaskParser.parsePhaseIndex(lines)
    assertEquals(index.size, 3)
    assertEquals(index(0), PhaseIndexEntry(1, isComplete = true, "View basic dashboard"))
    assertEquals(index(1), PhaseIndexEntry(2, isComplete = true, "Automatic registration"))
    assertEquals(index(2), PhaseIndexEntry(3, isComplete = false, "Server lifecycle"))

  test("parsePhaseIndex handles all phases complete"):
    val lines = Seq(
      "- [x] Phase 1: First",
      "- [x] Phase 2: Second"
    )
    val index = MarkdownTaskParser.parsePhaseIndex(lines)
    assertEquals(index.size, 2)
    assert(index.forall(_.isComplete))

  test("parsePhaseIndex handles no phases complete"):
    val lines = Seq(
      "- [ ] Phase 1: First",
      "- [ ] Phase 2: Second"
    )
    val index = MarkdownTaskParser.parsePhaseIndex(lines)
    assertEquals(index.size, 2)
    assert(index.forall(!_.isComplete))

  test("parsePhaseIndex ignores non-phase lines"):
    val lines = Seq(
      "# Implementation Tasks",
      "",
      "**Issue:** IWLE-100",
      "",
      "- [x] Phase 1: First phase",
      "- Some other bullet",
      "- [ ] Phase 2: Second phase"
    )
    val index = MarkdownTaskParser.parsePhaseIndex(lines)
    assertEquals(index.size, 2)

  test("parsePhaseIndex handles empty input"):
    val index = MarkdownTaskParser.parsePhaseIndex(Seq.empty)
    assertEquals(index.size, 0)

  test("parsePhaseIndex extracts phase number correctly"):
    val lines = Seq(
      "- [x] Phase 10: Tenth phase",
      "- [ ] Phase 11: Eleventh phase"
    )
    val index = MarkdownTaskParser.parsePhaseIndex(lines)
    assertEquals(index(0).phaseNumber, 10)
    assertEquals(index(1).phaseNumber, 11)

  test("parsePhaseIndex handles arrow notation"):
    val lines = Seq(
      "- [x] Phase 1: View dashboard (Est: 8-12h) → `phase-01-context.md`",
      "- [ ] Phase 2: Registration (Est: 6-8h) → `phase-02-context.md`"
    )
    val index = MarkdownTaskParser.parsePhaseIndex(lines)
    assertEquals(index(0).name, "View dashboard")
    assertEquals(index(1).name, "Registration")
