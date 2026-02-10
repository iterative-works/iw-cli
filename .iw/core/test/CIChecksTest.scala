// PURPOSE: Unit tests for CI workflow doctor checks - validates CI workflow file presence
// PURPOSE: Tests checkWorkflowExistsWith function with different tracker types
package iw.tests

import iw.core.model.{CheckResult, CIChecks, IssueTrackerType, ProjectConfiguration}
import munit.FunSuite

class CIChecksTest extends FunSuite:

  // Helper to create minimal config
  def testConfig(trackerType: IssueTrackerType): ProjectConfiguration =
    ProjectConfiguration.create(
      trackerType = trackerType,
      team = "TEST",
      projectName = "test-project"
    )

  // Test: GitHub tracker + .github/workflows/ci.yml exists → Success
  test("checkWorkflowExistsWith succeeds for GitHub when ci.yml exists"):
    val config = testConfig(IssueTrackerType.GitHub)
    val fileExists = (path: os.Path) =>
      path.toString.endsWith(".github/workflows/ci.yml")
    val result = CIChecks.checkWorkflowExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Success("Found (.github/workflows/ci.yml)"))

  // Test: GitHub tracker + file missing → Error with hint
  test("checkWorkflowExistsWith fails for GitHub when ci.yml missing"):
    val config = testConfig(IssueTrackerType.GitHub)
    val fileExists = (_: os.Path) => false
    val result = CIChecks.checkWorkflowExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Error("Missing", "Create .github/workflows/ci.yml"))

  // Test: GitLab tracker + .gitlab-ci.yml exists → Success
  test("checkWorkflowExistsWith succeeds for GitLab when .gitlab-ci.yml exists"):
    val config = testConfig(IssueTrackerType.GitLab)
    val fileExists = (path: os.Path) =>
      path.toString.endsWith(".gitlab-ci.yml")
    val result = CIChecks.checkWorkflowExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Success("Found (.gitlab-ci.yml)"))

  // Test: GitLab tracker + file missing → Error with hint
  test("checkWorkflowExistsWith fails for GitLab when .gitlab-ci.yml missing"):
    val config = testConfig(IssueTrackerType.GitLab)
    val fileExists = (_: os.Path) => false
    val result = CIChecks.checkWorkflowExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Error("Missing", "Create .gitlab-ci.yml"))

  // Test: Linear tracker + GitHub Actions file exists → Success
  test("checkWorkflowExistsWith succeeds for Linear when GitHub Actions exists"):
    val config = testConfig(IssueTrackerType.Linear)
    val fileExists = (path: os.Path) =>
      path.toString.endsWith(".github/workflows/ci.yml")
    val result = CIChecks.checkWorkflowExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Success("Found (.github/workflows/ci.yml)"))

  // Test: Linear tracker + GitLab CI file exists → Success
  test("checkWorkflowExistsWith succeeds for Linear when GitLab CI exists"):
    val config = testConfig(IssueTrackerType.Linear)
    val fileExists = (path: os.Path) =>
      path.toString.endsWith(".gitlab-ci.yml")
    val result = CIChecks.checkWorkflowExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Success("Found (.gitlab-ci.yml)"))

  // Test: Linear tracker + no CI file → Warning
  test("checkWorkflowExistsWith warns for Linear when no CI file found"):
    val config = testConfig(IssueTrackerType.Linear)
    val fileExists = (_: os.Path) => false
    val result = CIChecks.checkWorkflowExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Warning("No CI workflow found"))

  // Test: YouTrack tracker + GitHub Actions file exists → Success
  test("checkWorkflowExistsWith succeeds for YouTrack when GitHub Actions exists"):
    val config = testConfig(IssueTrackerType.YouTrack)
    val fileExists = (path: os.Path) =>
      path.toString.endsWith(".github/workflows/ci.yml")
    val result = CIChecks.checkWorkflowExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Success("Found (.github/workflows/ci.yml)"))

  // Test: YouTrack tracker + GitLab CI file exists → Success
  test("checkWorkflowExistsWith succeeds for YouTrack when GitLab CI exists"):
    val config = testConfig(IssueTrackerType.YouTrack)
    val fileExists = (path: os.Path) =>
      path.toString.endsWith(".gitlab-ci.yml")
    val result = CIChecks.checkWorkflowExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Success("Found (.gitlab-ci.yml)"))

  // Test: YouTrack tracker + no CI file → Warning
  test("checkWorkflowExistsWith warns for YouTrack when no CI file found"):
    val config = testConfig(IssueTrackerType.YouTrack)
    val fileExists = (_: os.Path) => false
    val result = CIChecks.checkWorkflowExistsWith(config, fileExists)
    assertEquals(result, CheckResult.Warning("No CI workflow found"))
