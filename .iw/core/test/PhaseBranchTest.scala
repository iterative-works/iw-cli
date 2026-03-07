// PURPOSE: Unit tests for PhaseNumber and PhaseBranch value objects
// PURPOSE: Tests phase number parsing, normalization, and branch name derivation

package iw.core.domain

import munit.FunSuite
import iw.core.model.{PhaseNumber, PhaseBranch}

class PhaseBranchTest extends FunSuite:

  // PhaseNumber.parse - valid cases

  test("PhaseNumber.parse('1') returns Right with value '01'"):
    val result = PhaseNumber.parse("1")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("01"))

  test("PhaseNumber.parse('03') returns Right with value '03' (already zero-padded)"):
    val result = PhaseNumber.parse("03")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("03"))

  test("PhaseNumber.parse('12') returns Right with value '12'"):
    val result = PhaseNumber.parse("12")
    assert(result.isRight)
    assertEquals(result.map(_.value), Right("12"))

  // PhaseNumber.parse - invalid cases

  test("PhaseNumber.parse('0') returns Left (phases are 1-based)"):
    val result = PhaseNumber.parse("0")
    assert(result.isLeft)

  test("PhaseNumber.parse('100') returns Left (exceeds two-digit format)"):
    val result = PhaseNumber.parse("100")
    assert(result.isLeft)

  test("PhaseNumber.parse('-1') returns Left"):
    val result = PhaseNumber.parse("-1")
    assert(result.isLeft)

  test("PhaseNumber.parse('abc') returns Left"):
    val result = PhaseNumber.parse("abc")
    assert(result.isLeft)

  test("PhaseNumber.parse('') returns Left"):
    val result = PhaseNumber.parse("")
    assert(result.isLeft)

  // PhaseNumber.toInt

  test("PhaseNumber.toInt returns integer value for '03' -> 3"):
    val phaseNum = PhaseNumber.parse("3").getOrElse(fail("Expected Right"))
    assertEquals(phaseNum.toInt, 3)

  test("PhaseNumber.toInt returns integer value for '12' -> 12"):
    val phaseNum = PhaseNumber.parse("12").getOrElse(fail("Expected Right"))
    assertEquals(phaseNum.toInt, 12)

  // PhaseBranch.branchName

  test("PhaseBranch branchName returns 'IW-238-phase-01' for phase 1"):
    val phaseNum = PhaseNumber.parse("1").getOrElse(fail("Expected Right"))
    val branch = PhaseBranch("IW-238", phaseNum)
    assertEquals(branch.branchName, "IW-238-phase-01")

  test("PhaseBranch with multi-digit phase number returns correct branch name"):
    val phaseNum = PhaseNumber.parse("12").getOrElse(fail("Expected Right"))
    val branch = PhaseBranch("IW-238", phaseNum)
    assertEquals(branch.branchName, "IW-238-phase-12")

  test("PhaseBranch with feature branch containing '-phase-' produces concatenated name"):
    val phaseNum = PhaseNumber.parse("2").getOrElse(fail("Expected Right"))
    val branch = PhaseBranch("IW-238-phase-01", phaseNum)
    assertEquals(branch.branchName, "IW-238-phase-01-phase-02")
