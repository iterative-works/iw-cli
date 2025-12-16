// PURPOSE: Integration tests for GitWorktreeAdapter worktree operations
// PURPOSE: Tests worktree creation, branch handling, and existence checks with real git

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../Process.scala"
//> using file "../GitWorktree.scala"

package iw.core.test

import iw.core.*
import munit.FunSuite

class GitWorktreeAdapterTest extends FunSuite:

  var tempDir: os.Path = null
  var repoDir: os.Path = null

  /** Run git command in repo directory using ProcessAdapter */
  def git(args: String*): ProcessResult =
    ProcessAdapter.run(Seq("git", "-C", repoDir.toString) ++ args)

  override def beforeEach(context: BeforeEach): Unit =
    // Create a temporary directory for test repos
    tempDir = os.Path(java.nio.file.Files.createTempDirectory("iw-test-git"))
    repoDir = tempDir / "test-repo"
    os.makeDir(repoDir)

    // Initialize a git repo using ProcessAdapter
    git("init")
    git("config", "user.email", "test@example.com")
    git("config", "user.name", "Test User")

    // Create initial commit
    os.write(repoDir / "README.md", "# Test Repo")
    git("add", "README.md")
    git("commit", "-m", "Initial commit")

  override def afterEach(context: AfterEach): Unit =
    // Cleanup: remove worktrees first, then delete temp directory
    if tempDir != null && os.exists(tempDir) then
      // Remove all worktrees using ProcessAdapter
      val result = git("worktree", "list", "--porcelain")
      val worktreePaths = result.stdout.split("\n")
        .filter(_.startsWith("worktree "))
        .map(_.substring(9))
        .filter(_ != repoDir.toString)

      worktreePaths.foreach { path =>
        git("worktree", "remove", path)
      }

      // Delete temp directory
      os.remove.all(tempDir)

  test("GitWorktreeAdapter.worktreeExists returns false for non-existent worktree"):
    val worktreePath = tempDir / "non-existent"
    val exists = GitWorktreeAdapter.worktreeExists(worktreePath, repoDir)
    assertEquals(exists, false)

  test("GitWorktreeAdapter.worktreeExists returns true for existing worktree"):
    val worktreePath = tempDir / "test-worktree"

    // Create worktree using adapter
    GitWorktreeAdapter.createWorktree(worktreePath, "test-branch", repoDir)

    val exists = GitWorktreeAdapter.worktreeExists(worktreePath, repoDir)
    assertEquals(exists, true)

  test("GitWorktreeAdapter.branchExists returns false for non-existent branch"):
    val exists = GitWorktreeAdapter.branchExists("non-existent-branch", repoDir)
    assertEquals(exists, false)

  test("GitWorktreeAdapter.branchExists returns true for existing branch"):
    // Create a branch using ProcessAdapter
    git("branch", "existing-branch")

    val exists = GitWorktreeAdapter.branchExists("existing-branch", repoDir)
    assertEquals(exists, true)

  test("GitWorktreeAdapter.branchExists returns true for master/main"):
    // Git init creates master or main depending on version
    val hasMaster = GitWorktreeAdapter.branchExists("master", repoDir)
    val hasMain = GitWorktreeAdapter.branchExists("main", repoDir)
    assert(hasMaster || hasMain, "Should have either master or main branch")

  test("GitWorktreeAdapter.createWorktree creates worktree with new branch"):
    val worktreePath = tempDir / "new-worktree"
    val branchName = "feature-branch"

    val result = GitWorktreeAdapter.createWorktree(worktreePath, branchName, repoDir)
    assert(result.isRight, s"Failed to create worktree: $result")

    // Verify worktree exists
    assert(os.exists(worktreePath), "Worktree directory should exist")
    assert(GitWorktreeAdapter.worktreeExists(worktreePath, repoDir), "Worktree should be registered")
    assert(GitWorktreeAdapter.branchExists(branchName, repoDir), "Branch should exist")

  test("GitWorktreeAdapter.createWorktree fails for duplicate branch"):
    // Create a branch first using ProcessAdapter
    git("branch", "duplicate-branch")

    val worktreePath = tempDir / "duplicate-worktree"
    val result = GitWorktreeAdapter.createWorktree(worktreePath, "duplicate-branch", repoDir)

    assert(result.isLeft, "Should fail to create worktree with duplicate branch name")

  test("GitWorktreeAdapter.createWorktreeForBranch creates worktree for existing branch"):
    // Create a branch first using ProcessAdapter
    val branchName = "existing-branch"
    git("branch", branchName)

    val worktreePath = tempDir / "worktree-for-existing"
    val result = GitWorktreeAdapter.createWorktreeForBranch(worktreePath, branchName, repoDir)

    assert(result.isRight, s"Failed to create worktree: $result")
    assert(os.exists(worktreePath), "Worktree directory should exist")
    assert(GitWorktreeAdapter.worktreeExists(worktreePath, repoDir), "Worktree should be registered")

  test("GitWorktreeAdapter.createWorktreeForBranch fails for non-existent branch"):
    val worktreePath = tempDir / "worktree-for-missing"
    val result = GitWorktreeAdapter.createWorktreeForBranch(worktreePath, "non-existent", repoDir)

    assert(result.isLeft, "Should fail to create worktree for non-existent branch")

  test("GitWorktreeAdapter.removeWorktree succeeds for clean worktree"):
    val worktreePath = tempDir / "remove-test"
    val branchName = "remove-branch"

    // Create worktree
    GitWorktreeAdapter.createWorktree(worktreePath, branchName, repoDir)
    assert(GitWorktreeAdapter.worktreeExists(worktreePath, repoDir), "Worktree should exist before removal")

    // Remove worktree
    val result = GitWorktreeAdapter.removeWorktree(worktreePath, repoDir, force = false)
    assert(result.isRight, s"Failed to remove worktree: $result")

    // Verify worktree is gone
    assert(!GitWorktreeAdapter.worktreeExists(worktreePath, repoDir), "Worktree should not exist after removal")
    assert(!os.exists(worktreePath), "Worktree directory should be deleted")

  test("GitWorktreeAdapter.removeWorktree with force succeeds even with uncommitted changes"):
    val worktreePath = tempDir / "remove-dirty"
    val branchName = "remove-dirty-branch"

    // Create worktree
    GitWorktreeAdapter.createWorktree(worktreePath, branchName, repoDir)

    // Add uncommitted changes
    os.write(worktreePath / "test.txt", "uncommitted")

    // Remove worktree with force
    val result = GitWorktreeAdapter.removeWorktree(worktreePath, repoDir, force = true)
    assert(result.isRight, s"Failed to remove worktree with force: $result")

    // Verify worktree is gone
    assert(!GitWorktreeAdapter.worktreeExists(worktreePath, repoDir), "Worktree should not exist after removal")

  test("GitWorktreeAdapter.removeWorktree fails for non-existent worktree"):
    val worktreePath = tempDir / "non-existent-worktree"

    val result = GitWorktreeAdapter.removeWorktree(worktreePath, repoDir, force = false)
    assert(result.isLeft, "Should fail to remove non-existent worktree")
