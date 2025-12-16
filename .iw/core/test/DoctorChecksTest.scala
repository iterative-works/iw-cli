// PURPOSE: Unit tests for DoctorChecks and CheckResult enum
// PURPOSE: Tests check execution order and result formatting

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using dep com.typesafe:config:1.4.5
//> using file "../DoctorChecks.scala"
//> using file "../Config.scala"
//> using file "../Constants.scala"

package iw.core.test

import iw.core.*
import munit.FunSuite

class DoctorChecksTest extends FunSuite:

  // Test CheckResult enum values
  test("CheckResult.Success contains message"):
    val result = CheckResult.Success("Found")
    result match
      case CheckResult.Success(msg) => assertEquals(msg, "Found")
      case _ => fail("Expected Success")

  test("CheckResult.Warning with hint contains message and hint"):
    val result = CheckResult.WarningWithHint("Slow", "Consider upgrading")
    result match
      case CheckResult.WarningWithHint(msg, hintText) =>
        assertEquals(msg, "Slow")
        assertEquals(hintText, "Consider upgrading")
      case _ => fail("Expected WarningWithHint")

  test("CheckResult.Warning without hint contains only message"):
    val result = CheckResult.Warning("Slow")
    result match
      case CheckResult.Warning(msg) =>
        assertEquals(msg, "Slow")
      case _ => fail("Expected Warning")

  test("CheckResult.Error always contains message and hint"):
    val result = CheckResult.Error("Not found", "Install it")
    result match
      case CheckResult.Error(msg, hintText) =>
        assertEquals(msg, "Not found")
        assertEquals(hintText, "Install it")
      case _ => fail("Expected Error")

  test("CheckResult.Skip contains reason"):
    val result = CheckResult.Skip("Not applicable")
    result match
      case CheckResult.Skip(reason) => assertEquals(reason, "Not applicable")
      case _ => fail("Expected Skip")

  test("CheckResult hint extraction helper works correctly"):
    assertEquals(CheckResult.Success("OK").hint, None)
    assertEquals(CheckResult.Warning("Warn").hint, None)
    assertEquals(CheckResult.WarningWithHint("Warn", "Fix it").hint, Some("Fix it"))
    assertEquals(CheckResult.Error("Err", "Fix it").hint, Some("Fix it"))
    assertEquals(CheckResult.Skip("Skip").hint, None)

  // Test Check case class
  test("Check holds name and run function"):
    val check = Check("Test Check", _ => CheckResult.Success("OK"))
    assertEquals(check.name, "Test Check")

  test("DoctorChecks.runAll executes all checks in list order"):
    val checks = List(
      Check("First", _ => CheckResult.Success("First done")),
      Check("Second", _ => CheckResult.Success("Second done")),
      Check("Third", _ => CheckResult.Error("Third failed", "Check logs"))
    )
    val config = ProjectConfiguration(IssueTrackerType.Linear, "TEST", "test-project")

    val results = DoctorChecks.runAll(checks, config)

    assertEquals(results.length, 3)
    assertEquals(results(0)._1, "First")
    assertEquals(results(0)._2, CheckResult.Success("First done"))
    assertEquals(results(1)._1, "Second")
    assertEquals(results(1)._2, CheckResult.Success("Second done"))
    assertEquals(results(2)._1, "Third")
    assertEquals(results(2)._2, CheckResult.Error("Third failed", "Check logs"))

  test("DoctorChecks.runAll passes config to check functions"):
    val checks = List(
      Check("Config Check", config =>
        if config.team == "EXPECTED" then
          CheckResult.Success("Correct team")
        else
          CheckResult.Error("Wrong team", "Update config with correct team")
      )
    )

    val correctConfig = ProjectConfiguration(IssueTrackerType.Linear, "EXPECTED", "test")
    val wrongConfig = ProjectConfiguration(IssueTrackerType.Linear, "WRONG", "test")

    val correctResults = DoctorChecks.runAll(checks, correctConfig)
    assertEquals(correctResults.head._2, CheckResult.Success("Correct team"))

    val wrongResults = DoctorChecks.runAll(checks, wrongConfig)
    assertEquals(wrongResults.head._2, CheckResult.Error("Wrong team", "Update config with correct team"))

  test("DoctorChecks.runAll with empty list returns empty results"):
    val config = ProjectConfiguration(IssueTrackerType.Linear, "TEST", "test")
    val results = DoctorChecks.runAll(Nil, config)
    assertEquals(results, Nil)
