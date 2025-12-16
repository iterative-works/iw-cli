// PURPOSE: Tests for Git infrastructure adapter
package iw.tests

// PURPOSE: Verifies GitAdapter can read git remote URL from actual git repos
import iw.core.*
import scala.sys.process.*

class GitTest extends munit.FunSuite, Fixtures:

  gitRepo.test("GitAdapter reads git remote URL"):
    repo =>
      val remote = GitAdapter.getRemoteUrl(repo)
      assertEquals(remote, Some(GitRemote("https://github.com/iterative-works/test-repo.git")))

  gitRepo.test("GitAdapter extracts host from remote URL"):
    repo =>
      val remote = GitAdapter.getRemoteUrl(repo)
      assert(remote.isDefined)
      assertEquals(remote.get.host, Right("github.com"))

  tempDir.test("GitAdapter returns None for non-git directory"):
    dir =>
      val remote = GitAdapter.getRemoteUrl(dir)
      assertEquals(remote, None)

  gitRepo.test("GitAdapter returns None for git repo without remote"):
    repo =>
      // Remove the remote
      Process(Seq("git", "remote", "remove", "origin"), repo.toIO).!
      val remote = GitAdapter.getRemoteUrl(repo)
      assertEquals(remote, None)

  tempDir.test("GitAdapter checks if directory is in git repo"):
    dir =>
      // Not a git repo
      assertEquals(GitAdapter.isGitRepository(dir), false)

      // Initialize git repo
      Process(Seq("git", "init"), dir.toIO).!
      Process(Seq("git", "config", "user.email", "test@example.com"), dir.toIO).!
      Process(Seq("git", "config", "user.name", "Test User"), dir.toIO).!

      // Now it is a git repo
      assertEquals(GitAdapter.isGitRepository(dir), true)

  gitRepo.test("GitAdapter gets current branch name"):
    repo =>
      // Fixture has initial commit, branch is established
      val result = GitAdapter.getCurrentBranch(repo)
      assert(result.isRight)
      // Branch name should be either 'main' or 'master'
      assert(result.exists(name => name == "main" || name == "master"))

  gitRepo.test("GitAdapter gets current branch name on custom branch"):
    repo =>
      // Fixture has initial commit, create and checkout a new branch
      Process(Seq("git", "checkout", "-b", "IWLE-123"), repo.toIO).!

      val result = GitAdapter.getCurrentBranch(repo)
      assert(result.isRight)
      assertEquals(result, Right("IWLE-123"))

  gitRepo.test("GitAdapter handles detached HEAD"):
    repo =>
      // Fixture has initial commit, get its hash and checkout detached
      val hash = Process(Seq("git", "rev-parse", "HEAD"), repo.toIO).!!.trim
      Process(Seq("git", "checkout", hash), repo.toIO).!

      val result = GitAdapter.getCurrentBranch(repo)
      assert(result.isRight)
      assertEquals(result, Right("HEAD"))

  tempDir.test("GitAdapter returns error for non-git directory when getting branch"):
    dir =>
      val result = GitAdapter.getCurrentBranch(dir)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("Failed to get current branch")))

  gitRepo.test("hasUncommittedChanges returns false for clean worktree"):
    repo =>
      // Fixture already has initial commit, worktree is clean
      val result = GitAdapter.hasUncommittedChanges(repo)
      assert(result.isRight)
      assertEquals(result, Right(false))

  gitRepo.test("hasUncommittedChanges returns true for modified files"):
    repo =>
      // Fixture has README.md committed, modify it
      os.write.over(repo / "README.md", "modified content")

      val result = GitAdapter.hasUncommittedChanges(repo)
      assert(result.isRight)
      assertEquals(result, Right(true))

  gitRepo.test("hasUncommittedChanges returns true for untracked files"):
    repo =>
      // Fixture is clean, add untracked file
      os.write(repo / "untracked.txt", "untracked content")

      val result = GitAdapter.hasUncommittedChanges(repo)
      assert(result.isRight)
      assertEquals(result, Right(true))

  tempDir.test("hasUncommittedChanges returns error for non-git directory"):
    dir =>
      val result = GitAdapter.hasUncommittedChanges(dir)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("Failed to check")))
