// PURPOSE: Tests for Git infrastructure adapter
package iw.tests

// PURPOSE: Verifies GitAdapter can read git remote URL from actual git repos
import iw.core.*
import scala.sys.process.*

class GitTest extends munit.FunSuite:

  val gitRepo = FunFixture[os.Path](
    setup = { _ =>
      val dir = os.Path(java.nio.file.Files.createTempDirectory("iw-git-test"))
      // Initialize git repo
      Process(Seq("git", "init"), dir.toIO).!
      Process(Seq("git", "config", "user.email", "test@example.com"), dir.toIO).!
      Process(Seq("git", "config", "user.name", "Test User"), dir.toIO).!
      // Add a remote
      Process(Seq("git", "remote", "add", "origin", "https://github.com/iterative-works/kanon.git"), dir.toIO).!
      dir
    },
    teardown = { dir =>
      os.remove.all(dir)
    }
  )

  gitRepo.test("GitAdapter reads git remote URL"):
    repo =>
      val remote = GitAdapter.getRemoteUrl(repo)
      assertEquals(remote, Some(GitRemote("https://github.com/iterative-works/kanon.git")))

  gitRepo.test("GitAdapter extracts host from remote URL"):
    repo =>
      val remote = GitAdapter.getRemoteUrl(repo)
      assert(remote.isDefined)
      assertEquals(remote.get.host, Right("github.com"))

  test("GitAdapter returns None for non-git directory"):
    val dir = os.Path(java.nio.file.Files.createTempDirectory("iw-non-git-test"))
    try
      val remote = GitAdapter.getRemoteUrl(dir)
      assertEquals(remote, None)
    finally
      os.remove.all(dir)

  gitRepo.test("GitAdapter returns None for git repo without remote"):
    repo =>
      // Remove the remote
      Process(Seq("git", "remote", "remove", "origin"), repo.toIO).!
      val remote = GitAdapter.getRemoteUrl(repo)
      assertEquals(remote, None)

  test("GitAdapter checks if directory is in git repo"):
    val dir = os.Path(java.nio.file.Files.createTempDirectory("iw-git-check-test"))
    try
      // Not a git repo
      assertEquals(GitAdapter.isGitRepository(dir), false)

      // Initialize git repo
      Process(Seq("git", "init"), dir.toIO).!
      Process(Seq("git", "config", "user.email", "test@example.com"), dir.toIO).!
      Process(Seq("git", "config", "user.name", "Test User"), dir.toIO).!

      // Now it is a git repo
      assertEquals(GitAdapter.isGitRepository(dir), true)
    finally
      os.remove.all(dir)

  gitRepo.test("GitAdapter gets current branch name"):
    repo =>
      // Git init creates a default branch (usually 'main' or 'master')
      // Let's create a commit to establish the branch
      os.write(repo / "test.txt", "test")
      Process(Seq("git", "add", "test.txt"), repo.toIO).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toIO).!

      val result = GitAdapter.getCurrentBranch(repo)
      assert(result.isRight)
      // Branch name should be either 'main' or 'master'
      assert(result.exists(name => name == "main" || name == "master"))

  gitRepo.test("GitAdapter gets current branch name on custom branch"):
    repo =>
      // Create initial commit
      os.write(repo / "test.txt", "test")
      Process(Seq("git", "add", "test.txt"), repo.toIO).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toIO).!

      // Create and checkout a new branch
      Process(Seq("git", "checkout", "-b", "IWLE-123"), repo.toIO).!

      val result = GitAdapter.getCurrentBranch(repo)
      assert(result.isRight)
      assertEquals(result, Right("IWLE-123"))

  gitRepo.test("GitAdapter handles detached HEAD"):
    repo =>
      // Create initial commit
      os.write(repo / "test.txt", "test")
      Process(Seq("git", "add", "test.txt"), repo.toIO).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toIO).!

      // Get the commit hash
      val hash = Process(Seq("git", "rev-parse", "HEAD"), repo.toIO).!!.trim

      // Checkout the commit directly (detached HEAD)
      Process(Seq("git", "checkout", hash), repo.toIO).!

      val result = GitAdapter.getCurrentBranch(repo)
      assert(result.isRight)
      assertEquals(result, Right("HEAD"))

  test("GitAdapter returns error for non-git directory when getting branch"):
    val dir = os.Path(java.nio.file.Files.createTempDirectory("iw-non-git-branch-test"))
    try
      val result = GitAdapter.getCurrentBranch(dir)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("Failed to get current branch")))
    finally
      os.remove.all(dir)

  gitRepo.test("hasUncommittedChanges returns false for clean worktree"):
    repo =>
      // Create initial commit
      os.write(repo / "test.txt", "test")
      Process(Seq("git", "add", "test.txt"), repo.toIO).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toIO).!

      val result = GitAdapter.hasUncommittedChanges(repo)
      assert(result.isRight)
      assertEquals(result, Right(false))

  gitRepo.test("hasUncommittedChanges returns true for modified files"):
    repo =>
      // Create initial commit
      os.write(repo / "test.txt", "test")
      Process(Seq("git", "add", "test.txt"), repo.toIO).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toIO).!

      // Modify the file
      os.write.over(repo / "test.txt", "modified")

      val result = GitAdapter.hasUncommittedChanges(repo)
      assert(result.isRight)
      assertEquals(result, Right(true))

  gitRepo.test("hasUncommittedChanges returns true for untracked files"):
    repo =>
      // Create initial commit
      os.write(repo / "test.txt", "test")
      Process(Seq("git", "add", "test.txt"), repo.toIO).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toIO).!

      // Add untracked file
      os.write(repo / "untracked.txt", "untracked")

      val result = GitAdapter.hasUncommittedChanges(repo)
      assert(result.isRight)
      assertEquals(result, Right(true))

  test("hasUncommittedChanges returns error for non-git directory"):
    val dir = os.Path(java.nio.file.Files.createTempDirectory("iw-non-git-status-test"))
    try
      val result = GitAdapter.hasUncommittedChanges(dir)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("Failed to check")))
    finally
      os.remove.all(dir)
