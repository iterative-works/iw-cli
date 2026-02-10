// PURPOSE: Unit tests for Scalafmt doctor checks - validates configuration presence and version
// PURPOSE: Tests checkConfigExistsWith and checkVersionExistsWith functions
package iw.tests

import iw.core.model.{CheckResult, ScalafmtChecks, IssueTrackerType, ProjectConfiguration}
import munit.FunSuite

class ScalafmtChecksTest extends FunSuite:

  // Helper to create minimal config
  def testConfig(): ProjectConfiguration =
    ProjectConfiguration.create(
      trackerType = IssueTrackerType.Linear,
      team = "TEST",
      projectName = "test-project"
    )

  // Test: config file exists → Success
  test("checkConfigExistsWith succeeds when .scalafmt.conf exists"):
    val config = testConfig()
    // Mock fileExists to return true
    val fileExists = (_: os.Path) => true
    val result = ScalafmtChecks.checkConfigExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Success("Found"))

  // Test: config file missing → Error with hint
  test("checkConfigExistsWith fails when .scalafmt.conf missing"):
    val config = testConfig()
    // Mock fileExists to return false
    val fileExists = (_: os.Path) => false
    val result = ScalafmtChecks.checkConfigExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Error("Missing", "Create .scalafmt.conf in project root"))

  // Test: config exists with version → Success
  test("checkVersionExistsWith succeeds when version key present"):
    val config = testConfig()
    // Mock readFile to return config with version
    val readFile = (_: os.Path) => Some("""
      |version = "3.8.1"
      |maxColumn = 120
    """.stripMargin)
    val result = ScalafmtChecks.checkVersionExistsWith(config, readFile)
    assertEquals(result, CheckResult.Success("Configured"))

  // Test: config exists but no version → WarningWithHint
  test("checkVersionExistsWith warns when version key missing"):
    val config = testConfig()
    // Mock readFile to return config without version
    val readFile = (_: os.Path) => Some("""
      |maxColumn = 120
      |align.preset = more
    """.stripMargin)
    val result = ScalafmtChecks.checkVersionExistsWith(config, readFile)
    assertEquals(result, CheckResult.WarningWithHint("Version not specified", "Add 'version = \"3.x.x\"' to .scalafmt.conf"))

  // Test: config file cannot be read → Error
  test("checkVersionExistsWith fails when file cannot be read"):
    val config = testConfig()
    // Mock readFile to return None (file doesn't exist or can't be read)
    val readFile = (_: os.Path) => None
    val result = ScalafmtChecks.checkVersionExistsWith(config, readFile)
    assertEquals(result, CheckResult.Error("Cannot read file", "Ensure .scalafmt.conf exists and is readable"))

  // Test: version with quotes and spaces
  test("checkVersionExistsWith detects version with various formats"):
    val config = testConfig()
    // Test version = "..."
    val readFile1 = (_: os.Path) => Some("version = \"3.8.1\"")
    assertEquals(ScalafmtChecks.checkVersionExistsWith(config, readFile1), CheckResult.Success("Configured"))

    // Test version="..." (no spaces)
    val readFile2 = (_: os.Path) => Some("version=\"3.8.1\"")
    assertEquals(ScalafmtChecks.checkVersionExistsWith(config, readFile2), CheckResult.Success("Configured"))

    // Test version = '...' (single quotes)
    val readFile3 = (_: os.Path) => Some("version = '3.8.1'")
    assertEquals(ScalafmtChecks.checkVersionExistsWith(config, readFile3), CheckResult.Success("Configured"))
