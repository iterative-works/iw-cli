// PURPOSE: Unit tests for Git hooks doctor checks - validates hook directory, files, and installation
// PURPOSE: Tests checkHooksDirExistsWith, checkHookFilesExistWith, and checkHooksInstalledWith functions
package iw.tests

import iw.core.model.{CheckResult, GitHooksChecks, IssueTrackerType, ProjectConfiguration}
import munit.FunSuite

class GitHooksChecksTest extends FunSuite:

  // Helper to create minimal config
  def testConfig(): ProjectConfiguration =
    ProjectConfiguration.create(
      trackerType = IssueTrackerType.Linear,
      team = "TEST",
      projectName = "test-project"
    )

  // Test: .git-hooks/ directory exists → Success
  test("checkHooksDirExistsWith succeeds when .git-hooks/ exists"):
    val config = testConfig()
    // Mock dirExists to return true
    val dirExists = (_: os.Path) => true
    val result = GitHooksChecks.checkHooksDirExistsWith(config, dirExists)
    assertEquals(result, CheckResult.Success("Found"))

  // Test: .git-hooks/ directory missing → Error with hint
  test("checkHooksDirExistsWith fails when .git-hooks/ missing"):
    val config = testConfig()
    // Mock dirExists to return false
    val dirExists = (_: os.Path) => false
    val result = GitHooksChecks.checkHooksDirExistsWith(config, dirExists)
    assertEquals(result, CheckResult.Error("Missing", "Create .git-hooks/ directory in project root"))

  // Test: pre-commit and pre-push exist → Success
  test("checkHookFilesExistWith succeeds when both hooks exist"):
    val config = testConfig()
    // Mock fileExists to return true for all files
    val fileExists = (_: os.Path) => true
    val result = GitHooksChecks.checkHookFilesExistWith(config, fileExists)
    assertEquals(result, CheckResult.Success("Found"))

  // Test: hook files missing → Error listing which ones
  test("checkHookFilesExistWith fails when pre-commit missing"):
    val config = testConfig()
    // Mock fileExists to return false for pre-commit, true for pre-push
    val fileExists = (path: os.Path) =>
      !path.toString.contains("pre-commit")
    val result = GitHooksChecks.checkHookFilesExistWith(config, fileExists)
    assertEquals(result, CheckResult.Error("Missing: pre-commit", "Create missing hook files in .git-hooks/"))

  test("checkHookFilesExistWith fails when pre-push missing"):
    val config = testConfig()
    // Mock fileExists to return true for pre-commit, false for pre-push
    val fileExists = (path: os.Path) =>
      !path.toString.contains("pre-push")
    val result = GitHooksChecks.checkHookFilesExistWith(config, fileExists)
    assertEquals(result, CheckResult.Error("Missing: pre-push", "Create missing hook files in .git-hooks/"))

  test("checkHookFilesExistWith fails when both hooks missing"):
    val config = testConfig()
    // Mock fileExists to return false for all files
    val fileExists = (_: os.Path) => false
    val result = GitHooksChecks.checkHookFilesExistWith(config, fileExists)
    assertEquals(result, CheckResult.Error("Missing: pre-commit, pre-push", "Create missing hook files in .git-hooks/"))

  // Test: hooks installed via core.hooksPath → Success
  test("checkHooksInstalledWith succeeds when core.hooksPath set"):
    val config = testConfig()
    // Mock getHooksPath to return .git-hooks
    val getHooksPath = () => Some(".git-hooks")
    // Other mocks won't be called
    val getGitDir = () => Some(os.pwd / ".git")
    val readSymlink = (_: os.Path) => None
    val result = GitHooksChecks.checkHooksInstalledWith(config, getGitDir, getHooksPath, readSymlink)
    assertEquals(result, CheckResult.Success("Installed"))

  // Test: hooks installed (symlinked correctly) → Success
  test("checkHooksInstalledWith succeeds when hooks symlinked"):
    val config = testConfig()
    // Mock getHooksPath to return None
    val getHooksPath = () => None
    // Mock getGitDir to return a path
    val getGitDir = () => Some(os.pwd / ".git")
    // Mock readSymlink to return .git-hooks/ paths
    val readSymlink = (path: os.Path) =>
      if path.toString.contains("pre-commit") then
        Some(os.pwd / ".git-hooks" / "pre-commit")
      else if path.toString.contains("pre-push") then
        Some(os.pwd / ".git-hooks" / "pre-push")
      else
        None
    val result = GitHooksChecks.checkHooksInstalledWith(config, getGitDir, getHooksPath, readSymlink)
    assertEquals(result, CheckResult.Success("Installed"))

  // Test: hooks not installed → WarningWithHint
  test("checkHooksInstalledWith warns when hooks not symlinked"):
    val config = testConfig()
    // Mock getHooksPath to return None
    val getHooksPath = () => None
    // Mock getGitDir to return a path
    val getGitDir = () => Some(os.pwd / ".git")
    // Mock readSymlink to return None (not a symlink)
    val readSymlink = (_: os.Path) => None
    val result = GitHooksChecks.checkHooksInstalledWith(config, getGitDir, getHooksPath, readSymlink)
    assertEquals(result, CheckResult.WarningWithHint("Not installed", "Run: git config core.hooksPath .git-hooks"))

  test("checkHooksInstalledWith warns when only pre-commit installed"):
    val config = testConfig()
    // Mock getHooksPath to return None
    val getHooksPath = () => None
    // Mock getGitDir to return a path
    val getGitDir = () => Some(os.pwd / ".git")
    // Mock readSymlink to return path only for pre-commit
    val readSymlink = (path: os.Path) =>
      if path.toString.contains("pre-commit") then
        Some(os.pwd / ".git-hooks" / "pre-commit")
      else
        None
    val result = GitHooksChecks.checkHooksInstalledWith(config, getGitDir, getHooksPath, readSymlink)
    assertEquals(result, CheckResult.WarningWithHint("Not installed", "Run: git config core.hooksPath .git-hooks"))

  // Test: Git dir unavailable → Skip
  test("checkHooksInstalledWith skips when git dir unavailable"):
    val config = testConfig()
    // Mock getHooksPath to return None
    val getHooksPath = () => None
    // Mock getGitDir to return None
    val getGitDir = () => None
    // readSymlink won't be called
    val readSymlink = (_: os.Path) => None
    val result = GitHooksChecks.checkHooksInstalledWith(config, getGitDir, getHooksPath, readSymlink)
    assertEquals(result, CheckResult.Skip("Cannot determine git directory"))
