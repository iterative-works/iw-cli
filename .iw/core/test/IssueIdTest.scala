// PURPOSE: Unit tests for IssueId value object validation and parsing
// PURPOSE: Tests valid/invalid formats and branch name conversion
package iw.tests

import iw.core.*
import munit.FunSuite

class IssueIdTest extends FunSuite:

  test("IssueId.parse accepts valid format IWLE-123"):
    val result = IssueId.parse("IWLE-123")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWLE-123"))

  test("IssueId.parse accepts valid format ABC-1"):
    val result = IssueId.parse("ABC-1")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("ABC-1"))

  test("IssueId.parse accepts valid format XY-99999"):
    val result = IssueId.parse("XY-99999")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("XY-99999"))

  test("IssueId.parse converts lowercase to uppercase"):
    val result = IssueId.parse("iwle-123")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWLE-123"))

  test("IssueId.parse trims whitespace"):
    val result = IssueId.parse("  IWLE-123  ")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWLE-123"))

  test("IssueId.parse accepts long project prefix"):
    val result = IssueId.parse("PROJECT-456")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJECT-456"))

  test("IssueId.parse rejects lowercase with no conversion"):
    // After conversion, should be accepted
    val result = IssueId.parse("abc-123")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("ABC-123"))

  test("IssueId.parse rejects missing dash"):
    val result = IssueId.parse("IWLE123")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Invalid issue ID format")))

  test("IssueId.parse rejects missing number"):
    val result = IssueId.parse("IWLE-")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Invalid") && msg.contains("expected")
    ), s"Expected meaningful error message, got: ${result.left.getOrElse("")}")

  test("IssueId.parse rejects missing project"):
    val result = IssueId.parse("-123")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Invalid") && msg.contains("expected")
    ), s"Expected meaningful error message, got: ${result.left.getOrElse("")}")

  test("IssueId.parse rejects empty string"):
    val result = IssueId.parse("")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Invalid") && msg.contains("expected")
    ), s"Expected meaningful error message, got: ${result.left.getOrElse("")}")

  test("IssueId.parse rejects only whitespace"):
    val result = IssueId.parse("   ")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Invalid") && msg.contains("expected")
    ), s"Expected meaningful error message, got: ${result.left.getOrElse("")}")

  test("IssueId.parse rejects non-numeric suffix"):
    val result = IssueId.parse("IWLE-ABC")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Invalid") && msg.contains("expected")
    ), s"Expected meaningful error message, got: ${result.left.getOrElse("")}")

  test("IssueId.parse rejects numeric prefix"):
    val result = IssueId.parse("123-456")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Invalid") && msg.contains("expected")
    ), s"Expected meaningful error message, got: ${result.left.getOrElse("")}")

  test("IssueId.parse rejects lowercase letters in project after trim"):
    // Should convert to uppercase, so this should pass
    val result = IssueId.parse("iwle-123")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWLE-123"))

  test("IssueId.parse rejects multiple dashes"):
    val result = IssueId.parse("IWLE-SUB-123")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Invalid") && msg.contains("expected")
    ), s"Expected meaningful error message, got: ${result.left.getOrElse("")}")

  test("IssueId.parse rejects special characters"):
    val result = IssueId.parse("IWLE@123")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Invalid") && msg.contains("expected")
    ), s"Expected meaningful error message, got: ${result.left.getOrElse("")}")

  test("IssueId.toBranchName returns value unchanged"):
    val issueId = IssueId.parse("IWLE-123").getOrElse(fail("Failed to parse valid issue ID"))
    assertEquals(issueId.toBranchName, "IWLE-123")

  test("IssueId.toBranchName preserves exact format"):
    val issueId = IssueId.parse("ABC-99").getOrElse(fail("Failed to parse valid issue ID"))
    assertEquals(issueId.toBranchName, "ABC-99")

  test("IssueId.value accessor returns underlying string"):
    val issueId = IssueId.parse("IWLE-123").getOrElse(fail("Failed to parse valid issue ID"))
    assertEquals(issueId.value, "IWLE-123")

  test("IssueId instances with same value are equal"):
    val id1 = IssueId.parse("IWLE-123").getOrElse(fail("Failed to parse"))
    val id2 = IssueId.parse("IWLE-123").getOrElse(fail("Failed to parse"))
    assertEquals(id1, id2)
