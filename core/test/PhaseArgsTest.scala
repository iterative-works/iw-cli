// PURPOSE: Unit tests for PhaseArgs pure argument parsing helpers
// PURPOSE: Verifies named arg extraction, flag detection, and issue ID/phase number resolution

package iw.tests

import munit.FunSuite
import iw.core.model.{PhaseArgs, IssueId, PhaseNumber}

class PhaseArgsTest extends FunSuite:

  // namedArg tests

  test("namedArg returns the value following the named flag"):
    val args = List("--title", "My Title", "--other", "value")
    assertEquals(PhaseArgs.namedArg(args, "--title"), Some("My Title"))

  test("namedArg returns None when the flag is absent"):
    val args = List("--other", "value")
    assertEquals(PhaseArgs.namedArg(args, "--title"), None)

  test("namedArg returns None for an empty arg list"):
    assertEquals(PhaseArgs.namedArg(Nil, "--title"), None)

  test("namedArg picks the first occurrence when flag appears twice"):
    val args = List("--title", "first", "--title", "second")
    assertEquals(PhaseArgs.namedArg(args, "--title"), Some("first"))

  // hasFlag tests

  test("hasFlag returns true when the flag is present"):
    val args = List("--batch", "--title", "Test")
    assertEquals(PhaseArgs.hasFlag(args, "--batch"), true)

  test("hasFlag returns false when the flag is absent"):
    val args = List("--title", "Test")
    assertEquals(PhaseArgs.hasFlag(args, "--batch"), false)

  test("hasFlag returns false for an empty arg list"):
    assertEquals(PhaseArgs.hasFlag(Nil, "--batch"), false)

  // resolveIssueId tests

  test("resolveIssueId uses provided issueIdArg when present"):
    val result = PhaseArgs.resolveIssueId(Some("IW-999"), "IW-238")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IW-999"))

  test("resolveIssueId falls back to feature branch when no arg given"):
    val result = PhaseArgs.resolveIssueId(None, "IW-238")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IW-238"))

  test("resolveIssueId returns Left for invalid issueIdArg"):
    val result = PhaseArgs.resolveIssueId(Some("invalid"), "IW-238")
    assert(result.isLeft)

  test("resolveIssueId returns Left when branch has no extractable issue ID"):
    val result = PhaseArgs.resolveIssueId(None, "no-issue-branch")
    assert(result.isLeft)
    assert(
      result.left.exists(_.contains("no-issue-branch")),
      s"Error should mention branch name, got: $result"
    )

  test("resolveIssueId extracts issue ID from branch with description suffix"):
    val result = PhaseArgs.resolveIssueId(None, "IW-238-some-description")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("IW-238"))

  // resolvePhaseNumber tests

  test("resolvePhaseNumber uses provided phaseNumberArg when present"):
    val result = PhaseArgs.resolvePhaseNumber(Some("3"), "02")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("03"))

  test("resolvePhaseNumber falls back to fromBranch string when no arg given"):
    val result = PhaseArgs.resolvePhaseNumber(None, "02")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("02"))

  test("resolvePhaseNumber returns Left for invalid phaseNumberArg"):
    val result = PhaseArgs.resolvePhaseNumber(Some("abc"), "02")
    assert(result.isLeft)
    assert(
      result.left.exists(_.contains("--phase-number")),
      s"Error should mention --phase-number, got: $result"
    )
    assert(
      result.left.exists(_.contains("abc")),
      s"Error should mention the invalid value, got: $result"
    )

  test("resolvePhaseNumber returns Left when fromBranch string is invalid"):
    val result = PhaseArgs.resolvePhaseNumber(None, "not-a-number")
    assert(result.isLeft)
    assert(
      result.left.exists(_.contains("branch")),
      s"Error should mention branch, got: $result"
    )
