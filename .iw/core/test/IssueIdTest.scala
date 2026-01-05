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

  test("IssueId.fromBranch regression test for TEAM-NNN format"):
    val result = IssueId.fromBranch("IWLE-132-description")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWLE-132"))

  test("IssueId.team returns team for TEAM-NNN format"):
    val issueId = IssueId.parse("IWLE-132").getOrElse(fail("Failed to parse TEAM-NNN ID"))
    assertEquals(issueId.team, "IWLE")

  // ========== IssueId.forGitHub Factory Method Tests ==========

  test("IssueId.forGitHub creates valid TEAM-NNN format"):
    val result = IssueId.forGitHub("IWCLI", 51)
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWCLI-51"))

  test("IssueId.forGitHub creates valid format with short prefix"):
    val result = IssueId.forGitHub("IW", 123)
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IW-123"))

  test("IssueId.forGitHub creates valid format with long prefix"):
    val result = IssueId.forGitHub("VERYLONGPR", 99)
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("VERYLONGPR-99"))

  test("IssueId.forGitHub rejects lowercase prefix"):
    val result = IssueId.forGitHub("iwcli", 51)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("uppercase letters only"))

  test("IssueId.forGitHub rejects too short prefix"):
    val result = IssueId.forGitHub("X", 1)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("2-10 characters"))

  test("IssueId.forGitHub rejects too long prefix"):
    val result = IssueId.forGitHub("VERYLONGPREFIX", 1)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("2-10 characters"))

  test("IssueId.forGitHub rejects prefix with numbers"):
    val result = IssueId.forGitHub("IW2CLI", 51)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("uppercase letters only"))

  test("IssueId.forGitHub rejects prefix with special characters"):
    val result = IssueId.forGitHub("IW-CLI", 51)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("uppercase letters only"))

  test("IssueId.forGitHub works with single-digit issue number"):
    val result = IssueId.forGitHub("IWCLI", 1)
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWCLI-1"))

  test("IssueId.forGitHub works with large issue number"):
    val result = IssueId.forGitHub("IWCLI", 99999)
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWCLI-99999"))

  test("IssueId.forGitHub extracts correct team from created ID"):
    val result = IssueId.forGitHub("IWCLI", 51)
    assert(result.isRight)
    val issueId = result.getOrElse(fail("Expected Right"))
    assertEquals(issueId.team, "IWCLI")

  // ========== IssueId.parse with defaultTeam Tests (Phase 2) ==========

  test("IssueId.parse with team prefix composes TEAM-NNN format from numeric input"):
    val result = IssueId.parse("51", Some("IWCLI"))
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWCLI-51"))

  test("IssueId.parse with team prefix accepts explicit TEAM-NNN format"):
    val result = IssueId.parse("IWCLI-51", Some("IWCLI"))
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWCLI-51"))

  test("IssueId.parse without team prefix accepts explicit TEAM-NNN format"):
    val result = IssueId.parse("IWCLI-51", None)
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWCLI-51"))

  test("IssueId.parse explicit TEAM-NNN format wins over different defaultTeam"):
    val result = IssueId.parse("IWCLI-51", Some("OTHER"))
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWCLI-51"))

  test("IssueId.parse with team prefix rejects invalid input"):
    val result = IssueId.parse("abc", Some("IWCLI"))
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Invalid issue ID format")))

  test("IssueId.parse with team prefix trims whitespace from numeric input"):
    val result = IssueId.parse("  51  ", Some("IWCLI"))
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWCLI-51"))

  test("IssueId.parse with team prefix handles single-digit numeric input"):
    val result = IssueId.parse("1", Some("IWCLI"))
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWCLI-1"))

  test("IssueId.parse with team prefix handles large numeric input"):
    val result = IssueId.parse("99999", Some("IWCLI"))
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWCLI-99999"))

  test("IssueId.parse with invalid team prefix still composes (validation happens in forGitHub)"):
    // parse just composes the string; validation is separate
    val result = IssueId.parse("51", Some("invalid"))
    // This will compose "invalid-51" which won't match Pattern
    assert(result.isLeft)

  // ========== Phase 3: Rejection Tests for Bare Numeric ==========

  test("IssueId.parse rejects bare numeric without team prefix"):
    val result = IssueId.parse("51", None)
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Invalid") && msg.contains("TEAM-123")
    ))

  test("IssueId.parse guides user to configure team prefix for bare numeric"):
    val result = IssueId.parse("51")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("team prefix") && msg.contains("iw init")
    ))

  // ========== GitLab-Specific Parsing Tests (Phase 6 - Refactored R1) ==========
  // GitLab now uses same TEAM-NNN format as GitHub (not #123)

  test("IssueId.parse with team prefix composes TEAM-NNN for GitLab"):
    val result = IssueId.parse("123", Some("PROJ"))
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJ-123"))

  test("IssueId.parse with team prefix handles single-digit for GitLab"):
    val result = IssueId.parse("1", Some("PROJ"))
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJ-1"))

  test("IssueId.parse with team prefix handles large number for GitLab"):
    val result = IssueId.parse("99999", Some("PROJ"))
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJ-99999"))

  test("IssueId.parse with team prefix rejects negative number"):
    val result = IssueId.parse("-123", Some("PROJ"))
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Invalid")))

  test("IssueId.parse with team prefix rejects decimal number"):
    val result = IssueId.parse("12.3", Some("PROJ"))
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Invalid")))

  test("IssueId.parse with team prefix rejects alphabetic input"):
    val result = IssueId.parse("abc", Some("PROJ"))
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Invalid")))

  test("IssueId.parse with team prefix trims whitespace"):
    val result = IssueId.parse("  123  ", Some("PROJ"))
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJ-123"))

  test("IssueId.team returns correct team for GitLab TEAM-NNN format"):
    val issueId = IssueId.parse("123", Some("PROJ")).getOrElse(fail("Failed to parse"))
    assertEquals(issueId.team, "PROJ")
