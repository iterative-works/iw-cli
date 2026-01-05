// PURPOSE: Unit tests for IssueId.fromBranch branch name extraction
// PURPOSE: Tests extraction from various branch naming patterns
package iw.tests

import iw.core.*
import munit.FunSuite

class IssueIdFromBranchTest extends FunSuite:

  test("IssueId.fromBranch extracts from exact match IWLE-123"):
    val result = IssueId.fromBranch("IWLE-123")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWLE-123"))

  test("IssueId.fromBranch extracts from branch with suffix IWLE-123-description"):
    val result = IssueId.fromBranch("IWLE-123-add-feature")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWLE-123"))

  test("IssueId.fromBranch extracts from branch with multiple suffixes IWLE-123-fix-bug-in-parser"):
    val result = IssueId.fromBranch("IWLE-123-fix-bug-in-parser")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWLE-123"))

  test("IssueId.fromBranch normalizes lowercase to uppercase iwle-123"):
    val result = IssueId.fromBranch("iwle-123")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWLE-123"))

  test("IssueId.fromBranch normalizes lowercase with suffix iwle-123-description"):
    val result = IssueId.fromBranch("iwle-123-some-feature")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IWLE-123"))

  test("IssueId.fromBranch extracts from different project ABC-456"):
    val result = IssueId.fromBranch("ABC-456")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("ABC-456"))

  test("IssueId.fromBranch extracts from different project with suffix ABC-456-test"):
    val result = IssueId.fromBranch("ABC-456-implement-feature")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("ABC-456"))

  test("IssueId.fromBranch rejects branch without issue ID (main)"):
    val result = IssueId.fromBranch("main")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Cannot extract issue ID from branch 'main'")))

  test("IssueId.fromBranch rejects branch without issue ID (master)"):
    val result = IssueId.fromBranch("master")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Cannot extract issue ID from branch 'master'")))

  test("IssueId.fromBranch rejects branch without issue ID (develop)"):
    val result = IssueId.fromBranch("develop")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Cannot extract issue ID from branch 'develop'")))

  test("IssueId.fromBranch rejects branch with invalid format (feature-branch)"):
    val result = IssueId.fromBranch("feature-branch")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Cannot extract issue ID from branch 'feature-branch'")))

  test("IssueId.fromBranch rejects empty string"):
    val result = IssueId.fromBranch("")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Cannot extract issue ID from branch ''")))

  test("IssueId.fromBranch handles long project names PROJECT-789"):
    val result = IssueId.fromBranch("PROJECT-789")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJECT-789"))

  test("IssueId.fromBranch handles long project names with suffix PROJECT-789-description"):
    val result = IssueId.fromBranch("PROJECT-789-implement-new-api")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJECT-789"))

  // ========== Phase 3: Rejection Tests for Bare Numeric ==========

  test("IssueId.fromBranch rejects bare numeric branch"):
    val result = IssueId.fromBranch("48")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Cannot extract") && msg.contains("TEAM-123")
    ))

  test("IssueId.fromBranch rejects numeric branch with description"):
    val result = IssueId.fromBranch("51-add-feature")
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Cannot extract") && msg.contains("TEAM-123")
    ))

  // ========== GitLab-Specific Branch Extraction Tests (Phase 6 - Refactored R1) ==========
  // GitLab now uses same TEAM-NNN format as GitHub (not #123)

  test("IssueId.fromBranch extracts from TEAM-NNN branch with description for GitLab"):
    val result = IssueId.fromBranch("PROJ-123-add-feature")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJ-123"))

  test("IssueId.fromBranch extracts from TEAM-NNN exact match for GitLab"):
    val result = IssueId.fromBranch("PROJ-123")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJ-123"))

  test("IssueId.fromBranch extracts single-digit from TEAM-N branch for GitLab"):
    val result = IssueId.fromBranch("PROJ-1")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJ-1"))

  test("IssueId.fromBranch normalizes lowercase TEAM-NNN for GitLab"):
    val result = IssueId.fromBranch("proj-123-feature")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("PROJ-123"))

  test("IssueId.fromBranch rejects bare numeric branch for GitLab"):
    val result = IssueId.fromBranch("123-feature")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Cannot extract")))

  test("IssueId.fromBranch rejects non-TEAM-NNN branch for GitLab"):
    val result = IssueId.fromBranch("feature-branch")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Cannot extract")))
