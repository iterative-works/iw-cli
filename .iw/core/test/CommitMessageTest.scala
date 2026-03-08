// PURPOSE: Unit tests for CommitMessage pure commit message construction
// PURPOSE: Tests title-only and title-with-items formatting edge cases

package iw.tests

import munit.FunSuite
import iw.core.model.CommitMessage

class CommitMessageTest extends FunSuite:

  test("title only produces single-line message"):
    val result = CommitMessage.build("feat: add phase support")
    assertEquals(result, "feat: add phase support")

  test("title with items produces title, blank line, and bulleted list"):
    val result = CommitMessage.build("feat: add phase support", List("item one", "item two"))
    assertEquals(result, "feat: add phase support\n\n- item one\n- item two")

  test("single item produces one bullet line"):
    val result = CommitMessage.build("fix: something", List("only item"))
    assertEquals(result, "fix: something\n\n- only item")

  test("empty items list produces title-only message with no trailing blank line"):
    val result = CommitMessage.build("fix: something", Nil)
    assertEquals(result, "fix: something")

  test("items with leading and trailing whitespace are trimmed"):
    val result = CommitMessage.build("title", List("  padded  ", "\ttabbed\t"))
    assertEquals(result, "title\n\n- padded\n- tabbed")

  test("title with trailing newline is trimmed"):
    val result = CommitMessage.build("title with newline\n")
    assertEquals(result, "title with newline")
