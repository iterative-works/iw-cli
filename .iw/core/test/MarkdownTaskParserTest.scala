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
