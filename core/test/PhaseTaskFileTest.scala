// PURPOSE: Unit tests for PhaseTaskFile markdown parsing and rewriting
// PURPOSE: Tests markComplete and markReviewed pure string transformations

package iw.tests

import munit.FunSuite
import iw.core.model.PhaseTaskFile

class PhaseTaskFileTest extends FunSuite:

  // markComplete tests

  test(
    "markComplete updates '**Phase Status:** Not Started' to '**Phase Status:** Complete'"
  ):
    val content =
      "# My Phase\n\nSome content.\n\n**Phase Status:** Not Started\n"
    val result = PhaseTaskFile.markComplete(content)
    assert(result.contains("**Phase Status:** Complete"), s"Got: $result")
    assert(!result.contains("Not Started"), s"Got: $result")

  test("markComplete is idempotent on already-complete content"):
    val content = "# My Phase\n\n**Phase Status:** Complete\n"
    val result = PhaseTaskFile.markComplete(content)
    assertEquals(result, content)

  test("markComplete appends status line when none exists"):
    val content = "# My Phase\n\nSome content without a status line.\n"
    val result = PhaseTaskFile.markComplete(content)
    assert(result.contains("**Phase Status:** Complete"), s"Got: $result")

  test(
    "markComplete handles '**Phase Status:** Ready for Implementation' (arbitrary existing value)"
  ):
    val content = "# Tasks\n\n**Phase Status:** Ready for Implementation\n"
    val result = PhaseTaskFile.markComplete(content)
    assert(result.contains("**Phase Status:** Complete"), s"Got: $result")
    assert(!result.contains("Ready for Implementation"), s"Got: $result")

  test("markComplete leaves '## Phase Status:' heading format unchanged"):
    val content = "## Phase Status: Not Started\n\nSome content.\n"
    val result = PhaseTaskFile.markComplete(content)
    // The heading-format line should be preserved as-is
    assert(result.contains("## Phase Status: Not Started"), s"Got: $result")
    // And a new bold-format line should be appended
    assert(result.contains("**Phase Status:** Complete"), s"Got: $result")

  test("markComplete preserves all other lines in the file"):
    val content =
      "# Title\n\nParagraph one.\n\n- task item\n\n**Phase Status:** Not Started\n"
    val result = PhaseTaskFile.markComplete(content)
    assert(result.contains("# Title"), s"Missing title in: $result")
    assert(result.contains("Paragraph one."), s"Missing paragraph in: $result")
    assert(result.contains("- task item"), s"Missing task in: $result")

  // markReviewed tests

  test(
    "markReviewed marks '- [x] [impl] [ ] [reviewed]' as '- [x] [impl] [x] [reviewed]'"
  ):
    val content = "- [x] [impl] [ ] [reviewed] Implement feature\n"
    val result = PhaseTaskFile.markReviewed(content)
    assertEquals(result, "- [x] [impl] [x] [reviewed] Implement feature\n")

  test(
    "markReviewed marks '- [x] [test] [ ] [reviewed]' as '- [x] [test] [x] [reviewed]'"
  ):
    val content = "- [x] [test] [ ] [reviewed] Write tests\n"
    val result = PhaseTaskFile.markReviewed(content)
    assertEquals(result, "- [x] [test] [x] [reviewed] Write tests\n")

  test(
    "markReviewed does not touch '- [ ] [impl] [ ] [reviewed]' (primary checkbox unchecked)"
  ):
    val content = "- [ ] [impl] [ ] [reviewed] Not done yet\n"
    val result = PhaseTaskFile.markReviewed(content)
    assertEquals(result, content)

  test(
    "markReviewed does not touch '- [x] [impl]' lines without [reviewed] marker"
  ):
    val content = "- [x] [impl] Done task without review tracking\n"
    val result = PhaseTaskFile.markReviewed(content)
    assertEquals(result, content)

  test(
    "markReviewed is idempotent on already-reviewed lines '- [x] [impl] [x] [reviewed]'"
  ):
    val content = "- [x] [impl] [x] [reviewed] Already reviewed\n"
    val result = PhaseTaskFile.markReviewed(content)
    assertEquals(result, content)

  test("markReviewed preserves non-checkbox lines exactly"):
    val content =
      "# Heading\n\nSome paragraph text.\n\n**Phase Status:** Complete\n"
    val result = PhaseTaskFile.markReviewed(content)
    assertEquals(result, content)

  test(
    "markReviewed handles mixed content (some lines with [reviewed], some without)"
  ):
    val content =
      "- [x] [impl] [ ] [reviewed] Task with review\n" +
        "- [x] [impl] Task without review tracking\n" +
        "- [ ] [impl] [ ] [reviewed] Not done yet\n" +
        "- [x] [test] [ ] [reviewed] Test task\n"
    val result = PhaseTaskFile.markReviewed(content)
    assertEquals(
      result,
      "- [x] [impl] [x] [reviewed] Task with review\n" +
        "- [x] [impl] Task without review tracking\n" +
        "- [ ] [impl] [ ] [reviewed] Not done yet\n" +
        "- [x] [test] [x] [reviewed] Test task\n"
    )

  // findUncheckedImplTasks tests

  test(
    "findUncheckedImplTasks returns empty list when all impl tasks are checked"
  ):
    val content =
      "- [x] [impl] [x] [reviewed] Task one\n" +
        "- [x] [impl] Task two\n" +
        "- [x] [test] [x] [reviewed] Test task\n"
    assertEquals(PhaseTaskFile.findUncheckedImplTasks(content), Nil)

  test(
    "findUncheckedImplTasks finds unchecked impl tasks with reviewed marker"
  ):
    val content =
      "- [x] [impl] [x] [reviewed] Done task\n" +
        "- [ ] [impl] [ ] [reviewed] Not done task\n"
    assertEquals(
      PhaseTaskFile.findUncheckedImplTasks(content),
      List("- [ ] [impl] [ ] [reviewed] Not done task")
    )

  test(
    "findUncheckedImplTasks finds unchecked impl tasks without reviewed marker"
  ):
    val content =
      "- [x] [impl] Done task\n" +
        "- [ ] [impl] Not done task\n"
    assertEquals(
      PhaseTaskFile.findUncheckedImplTasks(content),
      List("- [ ] [impl] Not done task")
    )

  test("findUncheckedImplTasks ignores unchecked non-impl tasks"):
    val content =
      "- [ ] [test] Unchecked test\n" +
        "- [ ] [setup] Unchecked setup\n" +
        "- [ ] [int] Unchecked integration\n"
    assertEquals(PhaseTaskFile.findUncheckedImplTasks(content), Nil)

  test("findUncheckedImplTasks finds multiple unchecked impl tasks"):
    val content =
      "- [ ] [impl] [ ] [reviewed] First unchecked\n" +
        "- [x] [impl] [x] [reviewed] Done\n" +
        "- [ ] [impl] [ ] [reviewed] Second unchecked\n"
    assertEquals(
      PhaseTaskFile.findUncheckedImplTasks(content),
      List(
        "- [ ] [impl] [ ] [reviewed] First unchecked",
        "- [ ] [impl] [ ] [reviewed] Second unchecked"
      )
    )

  test(
    "findUncheckedImplTasks returns empty list for content with no task lines"
  ):
    val content =
      "# Phase 1\n\nSome description.\n\n**Phase Status:** Not Started\n"
    assertEquals(PhaseTaskFile.findUncheckedImplTasks(content), Nil)

  test("findUncheckedImplTasks returns empty list for empty content"):
    assertEquals(PhaseTaskFile.findUncheckedImplTasks(""), Nil)
