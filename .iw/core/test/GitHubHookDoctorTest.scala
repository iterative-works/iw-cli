// PURPOSE: Unit tests for GitHub doctor hook - validates gh CLI checks
// PURPOSE: Tests checkGhInstalled and checkGhAuthenticated functions
package iw.tests

import iw.core.*
import munit.FunSuite

class GitHubHookDoctorTest extends FunSuite:

  // Helper to create GitHub config
  def githubConfig(): ProjectConfiguration =
    ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "owner",
      projectName = "repo",
      repository = Some("owner/repo")
    )

  // Helper to create Linear config
  def linearConfig(): ProjectConfiguration =
    ProjectConfiguration(
      trackerType = IssueTrackerType.Linear,
      team = "TEST",
      projectName = "test-project"
    )

  // Test: checkGhInstalled skips for non-GitHub tracker
  test("checkGhInstalled skips for non-GitHub tracker"):
    val config = linearConfig()
    val result = GitHubHookDoctor.checkGhInstalled(config)
    assertEquals(result, CheckResult.Skip("Not using GitHub"))

  // Test: checkGhInstalled succeeds when gh CLI available
  test("checkGhInstalled succeeds when gh CLI available"):
    val config = githubConfig()
    // Mock isCommandAvailable to return true
    val isCommandAvailable = (_: String) => true
    val result = GitHubHookDoctor.checkGhInstalledWith(config, isCommandAvailable)
    assertEquals(result, CheckResult.Success("Installed"))

  // Test: checkGhInstalled fails when gh CLI not available
  test("checkGhInstalled fails when gh CLI not available"):
    val config = githubConfig()
    // Mock isCommandAvailable to return false
    val isCommandAvailable = (_: String) => false
    val result = GitHubHookDoctor.checkGhInstalledWith(config, isCommandAvailable)
    assertEquals(result, CheckResult.Error("Not found", "Install: https://cli.github.com/"))

  // Test: checkGhAuthenticated skips for non-GitHub tracker
  test("checkGhAuthenticated skips for non-GitHub tracker"):
    val config = linearConfig()
    val result = GitHubHookDoctor.checkGhAuthenticated(config)
    assertEquals(result, CheckResult.Skip("Not using GitHub"))

  // Test: checkGhAuthenticated skips when gh not installed
  test("checkGhAuthenticated skips when gh not installed"):
    val config = githubConfig()
    // Mock isCommandAvailable to return false
    val isCommandAvailable = (_: String) => false
    val execCommand = (_: String, _: Array[String]) => Right("")
    val result = GitHubHookDoctor.checkGhAuthenticatedWith(config, isCommandAvailable, execCommand)
    assertEquals(result, CheckResult.Skip("gh not installed"))

  // Test: checkGhAuthenticated succeeds when authenticated
  test("checkGhAuthenticated succeeds when authenticated"):
    val config = githubConfig()
    // Mock isCommandAvailable to return true
    val isCommandAvailable = (_: String) => true
    // Mock execCommand to succeed (authenticated)
    val execCommand = (_: String, _: Array[String]) => Right("Logged in to github.com")
    val result = GitHubHookDoctor.checkGhAuthenticatedWith(config, isCommandAvailable, execCommand)
    assertEquals(result, CheckResult.Success("Authenticated"))

  // Test: checkGhAuthenticated fails when not authenticated
  test("checkGhAuthenticated fails when not authenticated"):
    val config = githubConfig()
    // Mock isCommandAvailable to return true
    val isCommandAvailable = (_: String) => true
    // Mock execCommand to fail with exit code 4 (not authenticated)
    val execCommand = (_: String, _: Array[String]) =>
      Left("Command failed: gh auth status: exit status 4")
    val result = GitHubHookDoctor.checkGhAuthenticatedWith(config, isCommandAvailable, execCommand)
    assertEquals(result, CheckResult.Error("Not authenticated", "Run: gh auth login"))
