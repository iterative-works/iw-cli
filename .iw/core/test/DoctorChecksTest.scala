// PURPOSE: Unit tests for DoctorChecks and CheckResult enum
// PURPOSE: Tests check execution order and result formatting

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../DoctorChecks.scala"
//> using file "../Config.scala"

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

  test("CheckResult.Warning contains message and optional hint"):
    val withHint = CheckResult.Warning("Slow", Some("Consider upgrading"))
    withHint match
      case CheckResult.Warning(msg, hint) =>
        assertEquals(msg, "Slow")
        assertEquals(hint, Some("Consider upgrading"))
      case _ => fail("Expected Warning")

    val withoutHint = CheckResult.Warning("Slow", None)
    withoutHint match
      case CheckResult.Warning(msg, hint) =>
        assertEquals(msg, "Slow")
        assertEquals(hint, None)
      case _ => fail("Expected Warning")

  test("CheckResult.Error contains message and optional hint"):
    val withHint = CheckResult.Error("Not found", Some("Install it"))
    withHint match
      case CheckResult.Error(msg, hint) =>
        assertEquals(msg, "Not found")
        assertEquals(hint, Some("Install it"))
      case _ => fail("Expected Error")

    val withoutHint = CheckResult.Error("Not found", None)
    withoutHint match
      case CheckResult.Error(msg, hint) =>
        assertEquals(msg, "Not found")
        assertEquals(hint, None)
      case _ => fail("Expected Error")

  test("CheckResult.Skip contains reason"):
    val result = CheckResult.Skip("Not applicable")
    result match
      case CheckResult.Skip(reason) => assertEquals(reason, "Not applicable")
      case _ => fail("Expected Skip")

  // Test Check case class
  test("Check holds name and run function"):
    val check = Check("Test Check", _ => CheckResult.Success("OK"))
    assertEquals(check.name, "Test Check")

  test("DoctorChecks.runAll executes all checks in list order"):
    val checks = List(
      Check("First", _ => CheckResult.Success("First done")),
      Check("Second", _ => CheckResult.Success("Second done")),
      Check("Third", _ => CheckResult.Error("Third failed", None))
    )
    val config = ProjectConfiguration(IssueTrackerType.Linear, "TEST", "test-project")

    val results = DoctorChecks.runAll(checks, config)

    assertEquals(results.length, 3)
    assertEquals(results(0)._1, "First")
    assertEquals(results(0)._2, CheckResult.Success("First done"))
    assertEquals(results(1)._1, "Second")
    assertEquals(results(1)._2, CheckResult.Success("Second done"))
    assertEquals(results(2)._1, "Third")
    assertEquals(results(2)._2, CheckResult.Error("Third failed", None))

  test("DoctorChecks.runAll passes config to check functions"):
    val checks = List(
      Check("Config Check", config =>
        if config.team == "EXPECTED" then
          CheckResult.Success("Correct team")
        else
          CheckResult.Error("Wrong team", None)
      )
    )

    val correctConfig = ProjectConfiguration(IssueTrackerType.Linear, "EXPECTED", "test")
    val wrongConfig = ProjectConfiguration(IssueTrackerType.Linear, "WRONG", "test")

    val correctResults = DoctorChecks.runAll(checks, correctConfig)
    assertEquals(correctResults.head._2, CheckResult.Success("Correct team"))

    val wrongResults = DoctorChecks.runAll(checks, wrongConfig)
    assertEquals(wrongResults.head._2, CheckResult.Error("Wrong team", None))

  test("DoctorChecks.runAll with empty list returns empty results"):
    val config = ProjectConfiguration(IssueTrackerType.Linear, "TEST", "test")
    val results = DoctorChecks.runAll(Nil, config)
    assertEquals(results, Nil)
