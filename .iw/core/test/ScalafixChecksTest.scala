// PURPOSE: Unit tests for Scalafix doctor checks - validates configuration presence and DisableSyntax rule
// PURPOSE: Tests checkConfigExistsWith and checkDisableSyntaxRulesWith functions
package iw.tests

import iw.core.model.{CheckResult, ScalafixChecks, IssueTrackerType, ProjectConfiguration}
import munit.FunSuite

class ScalafixChecksTest extends FunSuite:

  // Helper to create minimal config
  def testConfig(): ProjectConfiguration =
    ProjectConfiguration.create(
      trackerType = IssueTrackerType.Linear,
      team = "TEST",
      projectName = "test-project"
    )

  // Test: config file exists → Success
  test("checkConfigExistsWith succeeds when .scalafix.conf exists"):
    val config = testConfig()
    // Mock fileExists to return true
    val fileExists = (_: os.Path) => true
    val result = ScalafixChecks.checkConfigExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Success("Found"))

  // Test: config file missing → Error with hint
  test("checkConfigExistsWith fails when .scalafix.conf missing"):
    val config = testConfig()
    // Mock fileExists to return false
    val fileExists = (_: os.Path) => false
    val result = ScalafixChecks.checkConfigExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Error("Missing", "Create .scalafix.conf in project root"))

  // Test: config has DisableSyntax with all required rules → Success
  test("checkDisableSyntaxRulesWith succeeds when all rules present"):
    val config = testConfig()
    // Mock readFile to return config with all required rules
    val readFile = (_: os.Path) => Some("""
      |rules = [
      |  DisableSyntax
      |]
      |DisableSyntax.noNulls = true
      |DisableSyntax.noVars = true
      |DisableSyntax.noThrows = true
      |DisableSyntax.noReturns = true
    """.stripMargin)
    val result = ScalafixChecks.checkDisableSyntaxRulesWith(config, readFile)
    assertEquals(result, CheckResult.Success("Configured"))

  // Test: config has DisableSyntax but missing some rules → WarningWithHint
  test("checkDisableSyntaxRulesWith warns when some rules missing"):
    val config = testConfig()
    // Mock readFile to return config with DisableSyntax but missing noVars and noThrows
    val readFile = (_: os.Path) => Some("""
      |rules = [
      |  DisableSyntax
      |]
      |DisableSyntax.noNulls = true
      |DisableSyntax.noReturns = true
    """.stripMargin)
    val result = ScalafixChecks.checkDisableSyntaxRulesWith(config, readFile)
    assertEquals(result, CheckResult.WarningWithHint("Missing rules: noThrows, noVars", "Add missing rules to DisableSyntax in .scalafix.conf"))

  // Test: config missing DisableSyntax entirely → WarningWithHint
  test("checkDisableSyntaxRulesWith warns when DisableSyntax not configured"):
    val config = testConfig()
    // Mock readFile to return config without DisableSyntax
    val readFile = (_: os.Path) => Some("""
      |rules = [
      |  OrganizeImports
      |]
    """.stripMargin)
    val result = ScalafixChecks.checkDisableSyntaxRulesWith(config, readFile)
    assertEquals(result, CheckResult.WarningWithHint("DisableSyntax not configured", "Add DisableSyntax rule to .scalafix.conf"))

  // Test: config file unreadable → Error
  test("checkDisableSyntaxRulesWith fails when file cannot be read"):
    val config = testConfig()
    // Mock readFile to return None (file doesn't exist or can't be read)
    val readFile = (_: os.Path) => None
    val result = ScalafixChecks.checkDisableSyntaxRulesWith(config, readFile)
    assertEquals(result, CheckResult.Error("Cannot read file", "Ensure .scalafix.conf exists and is readable"))

  // Test: DisableSyntax with alternate HOCON format (nested)
  test("checkDisableSyntaxRulesWith detects nested HOCON format"):
    val config = testConfig()
    // Mock readFile to return config with nested format
    val readFile = (_: os.Path) => Some("""
      |DisableSyntax {
      |  noNulls = true
      |  noVars = true
      |  noThrows = true
      |  noReturns = true
      |}
    """.stripMargin)
    val result = ScalafixChecks.checkDisableSyntaxRulesWith(config, readFile)
    assertEquals(result, CheckResult.Success("Configured"))

  // Test: Case sensitivity - all rule names should be detected
  test("checkDisableSyntaxRulesWith is case-sensitive for rule names"):
    val config = testConfig()
    // Mock readFile with correct case
    val readFile = (_: os.Path) => Some("""
      |DisableSyntax.noNulls = true
      |DisableSyntax.noVars = true
      |DisableSyntax.noThrows = true
      |DisableSyntax.noReturns = true
    """.stripMargin)
    val result = ScalafixChecks.checkDisableSyntaxRulesWith(config, readFile)
    assertEquals(result, CheckResult.Success("Configured"))

  // Test: Multiple missing rules reported in sorted order
  test("checkDisableSyntaxRulesWith lists missing rules"):
    val config = testConfig()
    // Only noNulls is present, all others missing
    val readFile = (_: os.Path) => Some("""
      |DisableSyntax.noNulls = true
    """.stripMargin)
    val result = ScalafixChecks.checkDisableSyntaxRulesWith(config, readFile)
    assertEquals(result, CheckResult.WarningWithHint("Missing rules: noReturns, noThrows, noVars", "Add missing rules to DisableSyntax in .scalafix.conf"))
