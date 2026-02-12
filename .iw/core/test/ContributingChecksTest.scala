// PURPOSE: Unit tests for CONTRIBUTING.md doctor checks - validates file presence and section coverage
// PURPOSE: Tests checkFileExistsWith and checkSectionsCoveredWith functions
package iw.tests

import iw.core.model.{CheckResult, ContributingChecks, IssueTrackerType, ProjectConfiguration}
import munit.FunSuite

class ContributingChecksTest extends FunSuite:

  // Helper to create minimal config
  def testConfig(): ProjectConfiguration =
    ProjectConfiguration.create(
      trackerType = IssueTrackerType.Linear,
      team = "TEST",
      projectName = "test-project"
    )

  // Test: CONTRIBUTING.md exists → Success
  test("checkFileExistsWith succeeds when CONTRIBUTING.md exists"):
    val config = testConfig()
    // Mock fileExists to return true
    val fileExists = (_: os.Path) => true
    val result = ContributingChecks.checkFileExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Success("Found"))

  // Test: CONTRIBUTING.md missing → Warning with hint
  test("checkFileExistsWith warns when CONTRIBUTING.md missing"):
    val config = testConfig()
    // Mock fileExists to return false
    val fileExists = (_: os.Path) => false
    val result = ContributingChecks.checkFileExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Warning("Missing"))

  // Test: file exists with all key sections → Success
  test("checkSectionsCoveredWith succeeds when all topics covered"):
    val config = testConfig()
    // Mock readFile to return content with all key sections
    val readFile = (_: os.Path) => Some("""
      |# Contributing Guide
      |
      |## CI Pipeline
      |Our continuous integration checks run on every PR.
      |
      |## Git Hooks
      |Install pre-commit hooks to run checks locally.
      |
      |## Running Checks Locally
      |You can run all checks locally before pushing.
    """.stripMargin)
    val result = ContributingChecks.checkSectionsCoveredWith(config, readFile)
    assertEquals(result, CheckResult.Success("Complete"))

  // Test: file exists but missing CI section → WarningWithHint
  test("checkSectionsCoveredWith warns when CI topic missing"):
    val config = testConfig()
    // Mock readFile to return content without CI keywords
    val readFile = (_: os.Path) => Some("""
      |# Contributing Guide
      |
      |## Git Hooks
      |Install pre-commit hooks to run checks locally.
      |
      |## Running Checks Locally
      |You can run all checks locally before pushing.
    """.stripMargin)
    val result = ContributingChecks.checkSectionsCoveredWith(config, readFile)
    assertEquals(result, CheckResult.WarningWithHint("Missing: CI", "Add sections covering: CI"))

  // Test: file exists but missing hooks section → WarningWithHint
  test("checkSectionsCoveredWith warns when hooks topic missing"):
    val config = testConfig()
    // Mock readFile to return content without hooks keywords
    val readFile = (_: os.Path) => Some("""
      |# Contributing Guide
      |
      |## CI Pipeline
      |Our continuous integration checks run on every PR.
      |
      |## Running Checks Locally
      |You can run all checks locally before pushing.
    """.stripMargin)
    val result = ContributingChecks.checkSectionsCoveredWith(config, readFile)
    assertEquals(result, CheckResult.WarningWithHint("Missing: hooks", "Add sections covering: hooks"))

  // Test: file exists but missing local checks section → WarningWithHint
  test("checkSectionsCoveredWith warns when local checks topic missing"):
    val config = testConfig()
    // Mock readFile to return content without local checks keywords
    val readFile = (_: os.Path) => Some("""
      |# Contributing Guide
      |
      |## CI Pipeline
      |Our continuous integration checks run on every PR.
      |
      |## Git Hooks
      |Install pre-commit hooks.
    """.stripMargin)
    val result = ContributingChecks.checkSectionsCoveredWith(config, readFile)
    assertEquals(result, CheckResult.WarningWithHint("Missing: local checks", "Add sections covering: local checks"))

  // Test: file exists but missing multiple sections → WarningWithHint
  test("checkSectionsCoveredWith warns when multiple topics missing"):
    val config = testConfig()
    // Mock readFile to return content with only hooks
    val readFile = (_: os.Path) => Some("""
      |# Contributing Guide
      |
      |## Git Hooks
      |Install pre-commit hooks.
    """.stripMargin)
    val result = ContributingChecks.checkSectionsCoveredWith(config, readFile)
    assertEquals(result, CheckResult.WarningWithHint("Missing: CI, local checks", "Add sections covering: CI, local checks"))

  // Test: file cannot be read → Error
  test("checkSectionsCoveredWith fails when file cannot be read"):
    val config = testConfig()
    // Mock readFile to return None (file doesn't exist or can't be read)
    val readFile = (_: os.Path) => None
    val result = ContributingChecks.checkSectionsCoveredWith(config, readFile)
    assertEquals(result, CheckResult.Error("Cannot read file", "Ensure CONTRIBUTING.md exists and is readable"))

  // Test: case-insensitive keyword matching
  test("checkSectionsCoveredWith detects keywords case-insensitively"):
    val config = testConfig()
    // Test with uppercase and mixed case keywords
    val readFile = (_: os.Path) => Some("""
      |# Contributing Guide
      |
      |Our CONTINUOUS INTEGRATION runs checks.
      |Install Pre-Commit HOOKS.
      |Run checks LOCALLY before pushing.
    """.stripMargin)
    val result = ContributingChecks.checkSectionsCoveredWith(config, readFile)
    assertEquals(result, CheckResult.Success("Complete"))

  // Test: alternative keywords for CI
  test("checkSectionsCoveredWith recognizes alternative CI keywords"):
    val config = testConfig()
    // Test with "github actions" keyword
    val readFile1 = (_: os.Path) => Some("""
      |GitHub Actions workflow runs on PR.
      |Install hooks.
      |Run locally.
    """.stripMargin)
    assertEquals(ContributingChecks.checkSectionsCoveredWith(config, readFile1), CheckResult.Success("Complete"))

    // Test with "pipeline" keyword
    val readFile2 = (_: os.Path) => Some("""
      |Our pipeline validates code.
      |Install hooks.
      |Run locally.
    """.stripMargin)
    assertEquals(ContributingChecks.checkSectionsCoveredWith(config, readFile2), CheckResult.Success("Complete"))

  // Test: alternative keywords for hooks
  test("checkSectionsCoveredWith recognizes alternative hook keywords"):
    val config = testConfig()
    // Test with "pre-push" keyword
    val readFile = (_: os.Path) => Some("""
      |CI runs checks.
      |Set up pre-push validation.
      |Run locally.
    """.stripMargin)
    val result = ContributingChecks.checkSectionsCoveredWith(config, readFile)
    assertEquals(result, CheckResult.Success("Complete"))

  // Test: alternative keywords for local checks
  test("checkSectionsCoveredWith recognizes alternative local check keywords"):
    val config = testConfig()
    // Test with "running checks" keyword
    val readFile = (_: os.Path) => Some("""
      |CI runs checks.
      |Install hooks.
      |Running checks before commit is recommended.
    """.stripMargin)
    val result = ContributingChecks.checkSectionsCoveredWith(config, readFile)
    assertEquals(result, CheckResult.Success("Complete"))
