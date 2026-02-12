// PURPOSE: Unit tests for DoctorChecks and CheckResult enum
// PURPOSE: Tests check execution order and result formatting
package iw.tests

import iw.core.model.{Check, CheckResult, DoctorChecks, IssueTrackerType, ProjectConfiguration}
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
    val config = ProjectConfiguration.create(IssueTrackerType.Linear, "TEST", "test-project")

    val results = DoctorChecks.runAll(checks, config)

    assertEquals(results.length, 3)
    assertEquals(results(0)._1, "First")
    assertEquals(results(0)._2, CheckResult.Success("First done"))
    assertEquals(results(0)._3, "Environment")
    assertEquals(results(1)._1, "Second")
    assertEquals(results(1)._2, CheckResult.Success("Second done"))
    assertEquals(results(1)._3, "Environment")
    assertEquals(results(2)._1, "Third")
    assertEquals(results(2)._2, CheckResult.Error("Third failed", "Check logs"))
    assertEquals(results(2)._3, "Environment")

  test("DoctorChecks.runAll passes config to check functions"):
    val checks = List(
      Check("Config Check", config =>
        if config.team == "EXPECTED" then
          CheckResult.Success("Correct team")
        else
          CheckResult.Error("Wrong team", "Update config with correct team")
      )
    )

    val correctConfig = ProjectConfiguration.create(IssueTrackerType.Linear, "EXPECTED", "test")
    val wrongConfig = ProjectConfiguration.create(IssueTrackerType.Linear, "WRONG", "test")

    val correctResults = DoctorChecks.runAll(checks, correctConfig)
    assertEquals(correctResults.head._1, "Config Check")
    assertEquals(correctResults.head._2, CheckResult.Success("Correct team"))
    assertEquals(correctResults.head._3, "Environment")

    val wrongResults = DoctorChecks.runAll(checks, wrongConfig)
    assertEquals(wrongResults.head._1, "Config Check")
    assertEquals(wrongResults.head._2, CheckResult.Error("Wrong team", "Update config with correct team"))
    assertEquals(wrongResults.head._3, "Environment")

  test("DoctorChecks.runAll with empty list returns empty results"):
    val config = ProjectConfiguration.create(IssueTrackerType.Linear, "TEST", "test")
    val results = DoctorChecks.runAll(Nil, config)
    assertEquals(results, Nil)

  // Test category field default value
  test("Check defaults to Environment category when not specified"):
    val check = Check("Test Check", _ => CheckResult.Success("OK"))
    assertEquals(check.category, "Environment")

  test("Check accepts explicit category value"):
    val check = Check("Test Check", _ => CheckResult.Success("OK"), "Quality")
    assertEquals(check.category, "Quality")

  // Test filterByCategory
  test("filterByCategory returns all checks when filter is None"):
    val checks = List(
      Check("Env Check 1", _ => CheckResult.Success("OK"), "Environment"),
      Check("Quality Check 1", _ => CheckResult.Success("OK"), "Quality"),
      Check("Env Check 2", _ => CheckResult.Success("OK"), "Environment")
    )
    val filtered = DoctorChecks.filterByCategory(checks, None)
    assertEquals(filtered.length, 3)

  test("filterByCategory returns only Environment checks when filter is Some(Environment)"):
    val checks = List(
      Check("Env Check 1", _ => CheckResult.Success("OK"), "Environment"),
      Check("Quality Check 1", _ => CheckResult.Success("OK"), "Quality"),
      Check("Env Check 2", _ => CheckResult.Success("OK"), "Environment")
    )
    val filtered = DoctorChecks.filterByCategory(checks, Some("Environment"))
    assertEquals(filtered.length, 2)
    assertEquals(filtered(0).name, "Env Check 1")
    assertEquals(filtered(1).name, "Env Check 2")

  test("filterByCategory returns only Quality checks when filter is Some(Quality)"):
    val checks = List(
      Check("Env Check 1", _ => CheckResult.Success("OK"), "Environment"),
      Check("Quality Check 1", _ => CheckResult.Success("OK"), "Quality"),
      Check("Quality Check 2", _ => CheckResult.Success("OK"), "Quality")
    )
    val filtered = DoctorChecks.filterByCategory(checks, Some("Quality"))
    assertEquals(filtered.length, 2)
    assertEquals(filtered(0).name, "Quality Check 1")
    assertEquals(filtered(1).name, "Quality Check 2")

  // Test runAll preserves category in results
  test("DoctorChecks.runAll includes category in results"):
    val checks = List(
      Check("Env Check", _ => CheckResult.Success("OK"), "Environment"),
      Check("Quality Check", _ => CheckResult.Warning("Warn"), "Quality")
    )
    val config = ProjectConfiguration.create(IssueTrackerType.Linear, "TEST", "test")
    val results = DoctorChecks.runAll(checks, config)

    assertEquals(results.length, 2)
    assertEquals(results(0)._1, "Env Check")
    assertEquals(results(0)._2, CheckResult.Success("OK"))
    assertEquals(results(0)._3, "Environment")
    assertEquals(results(1)._1, "Quality Check")
    assertEquals(results(1)._2, CheckResult.Warning("Warn"))
    assertEquals(results(1)._3, "Quality")
