// PURPOSE: Tests for Git infrastructure adapter
// PURPOSE: Verifies GitAdapter can read git remote URL from actual git repos

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../Git.scala"
//> using file "../Config.scala"

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
      assertEquals(remote.get.host, "github.com")

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
