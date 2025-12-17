// PURPOSE: Tests for configuration domain model and git remote parsing
package iw.tests

// PURPOSE: Verifies GitRemote URL parsing, tracker detection, and configuration validation
import iw.core.*

class ConfigTest extends munit.FunSuite:

  test("GitRemote parses GitHub HTTPS URL"):
    val remote = GitRemote("https://github.com/iterative-works/kanon.git")
    assertEquals(remote.host, Right("github.com"))

  test("GitRemote parses GitHub SSH URL"):
    val remote = GitRemote("git@github.com:iterative-works/kanon.git")
    assertEquals(remote.host, Right("github.com"))

  test("GitRemote parses GitLab E-BS HTTPS URL"):
    val remote = GitRemote("https://gitlab.e-bs.cz/iterative-works/kanon.git")
    assertEquals(remote.host, Right("gitlab.e-bs.cz"))

  test("GitRemote parses GitLab E-BS SSH URL"):
    val remote = GitRemote("git@gitlab.e-bs.cz:iterative-works/kanon.git")
    assertEquals(remote.host, Right("gitlab.e-bs.cz"))

  test("GitRemote parses generic GitLab HTTPS URL"):
    val remote = GitRemote("https://gitlab.com/user/project.git")
    assertEquals(remote.host, Right("gitlab.com"))

  test("GitRemote parses generic GitLab SSH URL"):
    val remote = GitRemote("git@gitlab.com:user/project.git")
    assertEquals(remote.host, Right("gitlab.com"))

  test("GitRemote handles URL without .git suffix"):
    val remote = GitRemote("https://github.com/iterative-works/kanon")
    assertEquals(remote.host, Right("github.com"))

  test("GitRemote handles SSH URL without .git suffix"):
    val remote = GitRemote("git@github.com:iterative-works/kanon")
    assertEquals(remote.host, Right("github.com"))

  test("TrackerDetector suggests Linear for github.com"):
    val suggestion = TrackerDetector.suggestTracker(GitRemote("https://github.com/user/repo.git"))
    assertEquals(suggestion, Some(IssueTrackerType.Linear))

  test("TrackerDetector suggests YouTrack for gitlab.e-bs.cz"):
    val suggestion = TrackerDetector.suggestTracker(GitRemote("git@gitlab.e-bs.cz:user/repo.git"))
    assertEquals(suggestion, Some(IssueTrackerType.YouTrack))

  test("TrackerDetector returns None for unknown host"):
    val suggestion = TrackerDetector.suggestTracker(GitRemote("https://bitbucket.org/user/repo.git"))
    assertEquals(suggestion, None)
