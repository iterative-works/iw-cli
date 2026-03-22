// PURPOSE: Tests for Git infrastructure adapter
package iw.tests

// PURPOSE: Verifies GitAdapter can read git remote URL from actual git repos
import iw.core.adapters.{GitAdapter, ProcessAdapter}
import iw.core.model.GitRemote
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

  gitRepo.test("GitAdapter gets HEAD SHA"):
    repo =>
      val result = GitAdapter.getHeadSha(repo)
      assert(result.isRight, s"Expected Right but got: $result")
      // SHA should be a short hex string (7+ chars)
      val sha = result.toOption.get
      assert(sha.nonEmpty, "SHA should not be empty")
      assert(sha.forall(c => "0123456789abcdef".contains(c)), s"SHA should be hex: $sha")

  tempDir.test("GitAdapter returns error for getHeadSha on non-git directory"):
    dir =>
      val result = GitAdapter.getHeadSha(dir)
      assert(result.isLeft, s"Expected Left but got: $result")
      assert(result.left.exists(_.contains("Failed to get HEAD SHA")), s"Expected error message, got: $result")

  tempDir.test("hasUncommittedChanges returns error for non-git directory"):
    dir =>
      val result = GitAdapter.hasUncommittedChanges(dir)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("Failed to check")))

  // ========== createAndCheckoutBranch Tests ==========

  gitRepo.test("createAndCheckoutBranch creates a new branch and checks it out"):
    repo =>
      val result = GitAdapter.createAndCheckoutBranch("feature-phase-01", repo)
      assert(result.isRight, s"Expected Right but got: $result")
      val branch = GitAdapter.getCurrentBranch(repo)
      assertEquals(branch, Right("feature-phase-01"))

  gitRepo.test("createAndCheckoutBranch on an already-existing branch name returns Left"):
    repo =>
      // Create the branch first
      Process(Seq("git", "checkout", "-b", "existing-branch"), repo.toIO).!
      Process(Seq("git", "checkout", "-"), repo.toIO).!
      // Now try to create it again
      val result = GitAdapter.createAndCheckoutBranch("existing-branch", repo)
      assert(result.isLeft, s"Expected Left but got: $result")

  // ========== checkoutBranch Tests ==========

  gitRepo.test("checkoutBranch switches to an existing branch"):
    repo =>
      // Create a branch to switch to
      Process(Seq("git", "checkout", "-b", "target-branch"), repo.toIO).!
      // Go back to the original branch
      Process(Seq("git", "checkout", "-"), repo.toIO).!
      // Now switch back using checkoutBranch
      val result = GitAdapter.checkoutBranch("target-branch", repo)
      assert(result.isRight, s"Expected Right but got: $result")
      assertEquals(GitAdapter.getCurrentBranch(repo), Right("target-branch"))

  gitRepo.test("checkoutBranch on a non-existent branch returns Left"):
    repo =>
      val result = GitAdapter.checkoutBranch("non-existent-branch", repo)
      assert(result.isLeft, s"Expected Left but got: $result")

  // ========== stageFiles Tests ==========

  gitRepo.test("stageFiles stages only the specified file, leaving other changes unstaged"):
    repo =>
      os.write(repo / "target.txt", "target content")
      os.write(repo / "other.txt", "other content")
      val result = GitAdapter.stageFiles(Seq(repo / "target.txt"), repo)
      assert(result.isRight, s"Expected Right but got: $result")
      val staged = ProcessAdapter.run(Seq("git", "-C", repo.toString, "diff", "--cached", "--name-only"))
      assert(staged.stdout.contains("target.txt"), "target.txt should be staged")
      assert(!staged.stdout.contains("other.txt"), "other.txt should NOT be staged")

  gitRepo.test("stageFiles stages a modified tracked file"):
    repo =>
      os.write.over(repo / "README.md", "modified content")
      val result = GitAdapter.stageFiles(Seq(repo / "README.md"), repo)
      assert(result.isRight, s"Expected Right but got: $result")
      val staged = ProcessAdapter.run(Seq("git", "-C", repo.toString, "diff", "--cached", "--name-only"))
      assert(staged.stdout.contains("README.md"), "README.md should be staged")

  gitRepo.test("stageFiles with empty path list succeeds with no error"):
    repo =>
      val result = GitAdapter.stageFiles(Seq.empty, repo)
      assert(result.isRight, s"Expected Right but got: $result")

  gitRepo.test("stageFiles with a non-existent path returns Left"):
    repo =>
      val result = GitAdapter.stageFiles(Seq(repo / "does-not-exist.txt"), repo)
      assert(result.isLeft, s"Expected Left but got: $result")

  // ========== stageAll Tests ==========

  gitRepo.test("stageAll on a repo with unstaged changes stages all files"):
    repo =>
      // Create an untracked file
      os.write(repo / "new-file.txt", "new content")
      // Modify the existing file
      os.write.over(repo / "README.md", "modified")
      val result = GitAdapter.stageAll(repo)
      assert(result.isRight, s"Expected Right but got: $result")
      // After stageAll, hasUncommittedChanges should return true (staged but not committed)
      val statusResult = ProcessAdapter.run(Seq("git", "-C", repo.toString, "diff", "--cached", "--name-only"))
      assert(statusResult.stdout.contains("new-file.txt"), "new-file.txt should be staged")

  gitRepo.test("stageAll on a clean worktree succeeds with no error"):
    repo =>
      val result = GitAdapter.stageAll(repo)
      assert(result.isRight, s"Expected Right but got: $result")

  // ========== commit Tests ==========

  gitRepo.test("commit with a staged change returns Right containing a 40-character SHA string"):
    repo =>
      os.write(repo / "change.txt", "some change")
      GitAdapter.stageAll(repo)
      val result = GitAdapter.commit("Test commit", repo)
      assert(result.isRight, s"Expected Right but got: $result")
      val sha = result.toOption.get
      assertEquals(sha.length, 40)
      assert(sha.forall(c => "0123456789abcdef".contains(c)), s"SHA should be hex: $sha")

  gitRepo.test("commit with no staged changes returns Left"):
    repo =>
      val result = GitAdapter.commit("Nothing to commit", repo)
      assert(result.isLeft, s"Expected Left but got: $result")

  // ========== push Tests ==========

  gitRepo.test("push with setUpstream = true pushes to bare repo remote"):
    repo =>
      // Create a bare repo as remote
      val bareDir = os.Path(java.nio.file.Files.createTempDirectory("iw-test-bare"))
      try
        Process(Seq("git", "init", "--bare"), bareDir.toIO).!
        // Update origin to point to bare repo
        Process(Seq("git", "remote", "set-url", "origin", bareDir.toString), repo.toIO).!
        // The actual branch might be 'master' or 'main' in the test repo
        val branch = GitAdapter.getCurrentBranch(repo).getOrElse("main")
        val pushResult = GitAdapter.push(branch, repo, setUpstream = true)
        assert(pushResult.isRight, s"Expected Right but got: $pushResult")
      finally
        os.remove.all(bareDir)

  gitRepo.test("push on a branch with no remote configured returns Left"):
    repo =>
      // Remove origin
      Process(Seq("git", "remote", "remove", "origin"), repo.toIO).!
      val result = GitAdapter.push("main", repo)
      assert(result.isLeft, s"Expected Left but got: $result")

  // ========== diffNameOnly Tests ==========

  gitRepo.test("diffNameOnly lists files changed since a baseline commit SHA"):
    repo =>
      val baselineSha = GitAdapter.getFullHeadSha(repo).toOption.get
      // Add a new commit
      os.write(repo / "new.txt", "new file")
      GitAdapter.stageAll(repo)
      GitAdapter.commit("Add new file", repo)
      val result = GitAdapter.diffNameOnly(baselineSha, repo)
      assert(result.isRight, s"Expected Right but got: $result")
      assert(result.toOption.get.contains("new.txt"), s"Expected new.txt in diff: $result")

  gitRepo.test("diffNameOnly with an invalid/non-existent baseline SHA returns Left"):
    repo =>
      val result = GitAdapter.diffNameOnly("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", repo)
      assert(result.isLeft, s"Expected Left but got: $result")

  gitRepo.test("diffNameOnly with no changes since baseline returns Right(Nil)"):
    repo =>
      val sha = GitAdapter.getFullHeadSha(repo).toOption.get
      val result = GitAdapter.diffNameOnly(sha, repo)
      assert(result.isRight, s"Expected Right but got: $result")
      assertEquals(result, Right(Nil))

  // ========== pull Tests ==========

  gitRepo.test("pull on a branch with nothing to pull succeeds"):
    repo =>
      // Set origin to a bare repo clone
      val bareDir = os.Path(java.nio.file.Files.createTempDirectory("iw-test-bare-pull"))
      try
        Process(Seq("git", "init", "--bare"), bareDir.toIO).!
        Process(Seq("git", "remote", "set-url", "origin", bareDir.toString), repo.toIO).!
        // Push first so there is a tracking branch
        val branch = GitAdapter.getCurrentBranch(repo).getOrElse("main")
        Process(Seq("git", "push", "-u", "origin", branch), repo.toIO).!
        // Now pull - should succeed with nothing to pull
        val result = GitAdapter.pull(repo)
        assert(result.isRight, s"Expected Right but got: $result")
      finally
        os.remove.all(bareDir)

  // ========== getFullHeadSha Tests ==========

  gitRepo.test("getFullHeadSha returns a 40-character hex string"):
    repo =>
      val result = GitAdapter.getFullHeadSha(repo)
      assert(result.isRight, s"Expected Right but got: $result")
      val sha = result.toOption.get
      assertEquals(sha.length, 40)
      assert(sha.forall(c => "0123456789abcdef".contains(c)), s"SHA should be hex: $sha")

  gitRepo.test("getFullHeadSha result differs from getHeadSha abbreviated output"):
    repo =>
      val fullSha = GitAdapter.getFullHeadSha(repo).toOption.get
      val shortSha = GitAdapter.getHeadSha(repo).toOption.get
      // Full SHA is 40 chars, short is typically 7
      assertEquals(fullSha.length, 40)
      assert(shortSha.length < 40, s"Short SHA should be abbreviated: $shortSha")
      assert(fullSha.startsWith(shortSha), s"Full SHA should start with short SHA")

  // ========== fetchAndReset Tests ==========

  gitRepo.test("fetchAndReset updates local branch to match remote after new commits"):
    repo =>
      // Create a bare repo as origin
      val bareDir = os.Path(java.nio.file.Files.createTempDirectory("iw-test-bare-fr"))
      try
        Process(Seq("git", "init", "--bare"), bareDir.toIO).!
        Process(Seq("git", "remote", "set-url", "origin", bareDir.toString), repo.toIO).!
        val branch = GitAdapter.getCurrentBranch(repo).getOrElse("main")
        // Push initial commit to bare repo
        Process(Seq("git", "push", "-u", "origin", branch), repo.toIO).!

        // Clone the bare repo to simulate a second contributor adding a commit
        val cloneDir = os.Path(java.nio.file.Files.createTempDirectory("iw-test-clone-fr"))
        try
          Process(Seq("git", "clone", bareDir.toString, cloneDir.toString)).!
          Process(Seq("git", "config", "user.email", "test@example.com"), cloneDir.toIO).!
          Process(Seq("git", "config", "user.name", "Test User"), cloneDir.toIO).!
          os.write(cloneDir / "remote-change.txt", "remote change")
          Process(Seq("git", "add", "-A"), cloneDir.toIO).!
          Process(Seq("git", "commit", "-m", "Remote commit"), cloneDir.toIO).!
          Process(Seq("git", "push", "origin", branch), cloneDir.toIO).!

          // Now the bare repo has a commit that our local repo doesn't have yet
          val beforeSha = GitAdapter.getFullHeadSha(repo).toOption.get
          val remoteSha = Process(Seq("git", "rev-parse", s"origin/$branch"), cloneDir.toIO).!!.trim
          val result = GitAdapter.fetchAndReset(branch, repo)
          assert(result.isRight, s"fetchAndReset should succeed: $result")
          val afterSha = GitAdapter.getFullHeadSha(repo).toOption.get
          assert(beforeSha != afterSha, "HEAD SHA should change after fetchAndReset")
          assert(os.exists(repo / "remote-change.txt"), "Remote file should exist after reset")
          assertEquals(afterSha, remoteSha, "Local HEAD should match remote SHA after fetchAndReset")
        finally
          os.remove.all(cloneDir)
      finally
        os.remove.all(bareDir)

  // ========== isFileDirty Tests ==========

  gitRepo.test("isFileDirty returns false for a clean tracked file"):
    repo =>
      assert(!GitAdapter.isFileDirty(repo / "README.md", repo))

  gitRepo.test("isFileDirty returns true for a modified tracked file"):
    repo =>
      os.write.over(repo / "README.md", "modified")
      assert(GitAdapter.isFileDirty(repo / "README.md", repo))

  gitRepo.test("isFileDirty returns true for an untracked file"):
    repo =>
      os.write(repo / "new.txt", "new content")
      assert(GitAdapter.isFileDirty(repo / "new.txt", repo))

  // ========== commitFileWithRetry Tests ==========

  gitRepo.test("commitFileWithRetry commits a modified file and returns SHA"):
    repo =>
      os.write.over(repo / "README.md", "modified for retry test")
      val result = GitAdapter.commitFileWithRetry(repo / "README.md", "Test commit with retry", repo)
      assert(result.isRight, s"Expected Right but got: $result")
      val sha = result.toOption.get
      assertEquals(sha.length, 40)
      // File should be clean after commit
      assert(!GitAdapter.isFileDirty(repo / "README.md", repo))

  gitRepo.test("commitFileWithRetry succeeds silently when file is already clean"):
    repo =>
      // README.md is already committed and clean — stage+commit fails but file is clean,
      // so commitFileWithRetry treats this as success (no action needed)
      val result = GitAdapter.commitFileWithRetry(repo / "README.md", "Nothing to commit", repo)
      assert(result.isRight, s"Expected Right but got: $result")
      assertEquals(result.toOption.get, "unknown-sha")

  gitRepo.test("fetchAndReset returns Left when no remote configured"):
    repo =>
      Process(Seq("git", "remote", "remove", "origin"), repo.toIO).!
      val result = GitAdapter.fetchAndReset("main", repo)
      assert(result.isLeft, s"Expected Left but got: $result")
