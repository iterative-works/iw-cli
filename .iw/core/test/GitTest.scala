// PURPOSE: Tests for Git infrastructure adapter
// PURPOSE: Verifies GitAdapter can read git remote URL from actual git repos

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../Git.scala"
//> using file "../Config.scala"
//> using file "../Process.scala"

import iw.core.*
import java.nio.file.Files
import java.nio.file.Path
import scala.sys.process.*

class GitTest extends munit.FunSuite:

  val gitRepo = FunFixture[Path](
    setup = { _ =>
      val dir = Files.createTempDirectory("iw-git-test")
      // Initialize git repo
      Process(Seq("git", "init"), dir.toFile).!
      Process(Seq("git", "config", "user.email", "test@example.com"), dir.toFile).!
      Process(Seq("git", "config", "user.name", "Test User"), dir.toFile).!
      // Add a remote
      Process(Seq("git", "remote", "add", "origin", "https://github.com/iterative-works/kanon.git"), dir.toFile).!
      dir
    },
    teardown = { dir =>
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
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
    val dir = Files.createTempDirectory("iw-non-git-test")
    try
      val remote = GitAdapter.getRemoteUrl(dir)
      assertEquals(remote, None)
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)

  gitRepo.test("GitAdapter returns None for git repo without remote"):
    repo =>
      // Remove the remote
      Process(Seq("git", "remote", "remove", "origin"), repo.toFile).!
      val remote = GitAdapter.getRemoteUrl(repo)
      assertEquals(remote, None)

  test("GitAdapter checks if directory is in git repo"):
    val dir = Files.createTempDirectory("iw-git-check-test")
    try
      // Not a git repo
      assertEquals(GitAdapter.isGitRepository(dir), false)

      // Initialize git repo
      Process(Seq("git", "init"), dir.toFile).!
      Process(Seq("git", "config", "user.email", "test@example.com"), dir.toFile).!
      Process(Seq("git", "config", "user.name", "Test User"), dir.toFile).!

      // Now it is a git repo
      assertEquals(GitAdapter.isGitRepository(dir), true)
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)

  gitRepo.test("GitAdapter gets current branch name"):
    repo =>
      // Git init creates a default branch (usually 'main' or 'master')
      // Let's create a commit to establish the branch
      Files.writeString(repo.resolve("test.txt"), "test")
      Process(Seq("git", "add", "test.txt"), repo.toFile).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toFile).!

      val result = GitAdapter.getCurrentBranch(repo)
      assert(result.isRight)
      // Branch name should be either 'main' or 'master'
      assert(result.exists(name => name == "main" || name == "master"))

  gitRepo.test("GitAdapter gets current branch name on custom branch"):
    repo =>
      // Create initial commit
      Files.writeString(repo.resolve("test.txt"), "test")
      Process(Seq("git", "add", "test.txt"), repo.toFile).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toFile).!

      // Create and checkout a new branch
      Process(Seq("git", "checkout", "-b", "IWLE-123"), repo.toFile).!

      val result = GitAdapter.getCurrentBranch(repo)
      assert(result.isRight)
      assertEquals(result, Right("IWLE-123"))

  gitRepo.test("GitAdapter handles detached HEAD"):
    repo =>
      // Create initial commit
      Files.writeString(repo.resolve("test.txt"), "test")
      Process(Seq("git", "add", "test.txt"), repo.toFile).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toFile).!

      // Get the commit hash
      val hash = Process(Seq("git", "rev-parse", "HEAD"), repo.toFile).!!.trim

      // Checkout the commit directly (detached HEAD)
      Process(Seq("git", "checkout", hash), repo.toFile).!

      val result = GitAdapter.getCurrentBranch(repo)
      assert(result.isRight)
      assertEquals(result, Right("HEAD"))

  test("GitAdapter returns error for non-git directory when getting branch"):
    val dir = Files.createTempDirectory("iw-non-git-branch-test")
    try
      val result = GitAdapter.getCurrentBranch(dir)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("Failed to get current branch")))
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)

  gitRepo.test("hasUncommittedChanges returns false for clean worktree"):
    repo =>
      // Create initial commit
      Files.writeString(repo.resolve("test.txt"), "test")
      Process(Seq("git", "add", "test.txt"), repo.toFile).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toFile).!

      val result = GitAdapter.hasUncommittedChanges(repo)
      assert(result.isRight)
      assertEquals(result, Right(false))

  gitRepo.test("hasUncommittedChanges returns true for modified files"):
    repo =>
      // Create initial commit
      Files.writeString(repo.resolve("test.txt"), "test")
      Process(Seq("git", "add", "test.txt"), repo.toFile).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toFile).!

      // Modify the file
      Files.writeString(repo.resolve("test.txt"), "modified")

      val result = GitAdapter.hasUncommittedChanges(repo)
      assert(result.isRight)
      assertEquals(result, Right(true))

  gitRepo.test("hasUncommittedChanges returns true for untracked files"):
    repo =>
      // Create initial commit
      Files.writeString(repo.resolve("test.txt"), "test")
      Process(Seq("git", "add", "test.txt"), repo.toFile).!
      Process(Seq("git", "commit", "-m", "Initial commit"), repo.toFile).!

      // Add untracked file
      Files.writeString(repo.resolve("untracked.txt"), "untracked")

      val result = GitAdapter.hasUncommittedChanges(repo)
      assert(result.isRight)
      assertEquals(result, Right(true))

  test("hasUncommittedChanges returns error for non-git directory"):
    val dir = Files.createTempDirectory("iw-non-git-status-test")
    try
      val result = GitAdapter.hasUncommittedChanges(dir)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("Failed to check")))
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
