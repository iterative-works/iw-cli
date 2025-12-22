// PURPOSE: Tests for configuration domain model and git remote parsing
// PURPOSE: Verifies GitRemote URL parsing, tracker detection, and configuration serialization
package iw.tests

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

  test("TrackerDetector suggests GitHub for github.com"):
    val suggestion = TrackerDetector.suggestTracker(GitRemote("https://github.com/user/repo.git"))
    assertEquals(suggestion, Some(IssueTrackerType.GitHub))

  test("TrackerDetector suggests YouTrack for gitlab.e-bs.cz"):
    val suggestion = TrackerDetector.suggestTracker(GitRemote("git@gitlab.e-bs.cz:user/repo.git"))
    assertEquals(suggestion, Some(IssueTrackerType.YouTrack))

  test("TrackerDetector returns None for unknown host"):
    val suggestion = TrackerDetector.suggestTracker(GitRemote("https://bitbucket.org/user/repo.git"))
    assertEquals(suggestion, None)

  // GitHub repository extraction tests
  test("GitRemote extracts owner/repo from HTTPS URL with .git suffix"):
    val remote = GitRemote("https://github.com/iterative-works/iw-cli.git")
    assertEquals(remote.repositoryOwnerAndName, Right("iterative-works/iw-cli"))

  test("GitRemote extracts owner/repo from HTTPS URL without .git suffix"):
    val remote = GitRemote("https://github.com/iterative-works/iw-cli")
    assertEquals(remote.repositoryOwnerAndName, Right("iterative-works/iw-cli"))

  test("GitRemote extracts owner/repo from SSH URL with .git suffix"):
    val remote = GitRemote("git@github.com:iterative-works/iw-cli.git")
    assertEquals(remote.repositoryOwnerAndName, Right("iterative-works/iw-cli"))

  test("GitRemote extracts owner/repo from SSH URL without .git suffix"):
    val remote = GitRemote("git@github.com:iterative-works/iw-cli")
    assertEquals(remote.repositoryOwnerAndName, Right("iterative-works/iw-cli"))

  test("GitRemote returns error for non-GitHub HTTPS URL"):
    val remote = GitRemote("https://gitlab.com/user/project.git")
    assertEquals(remote.repositoryOwnerAndName, Left("Not a GitHub URL"))

  test("GitRemote returns error for non-GitHub SSH URL"):
    val remote = GitRemote("git@gitlab.com:user/project.git")
    assertEquals(remote.repositoryOwnerAndName, Left("Not a GitHub URL"))

  test("GitRemote returns error for invalid repository format"):
    val remote = GitRemote("https://github.com/single-component")
    assertEquals(remote.repositoryOwnerAndName, Left("Invalid repository format: expected owner/repo"))

  test("GitRemote returns error for repository with empty owner"):
    val remote = GitRemote("https://github.com//repo.git")
    assertEquals(remote.repositoryOwnerAndName, Left("Invalid repository format: expected owner/repo"))

  test("GitRemote returns error for repository with empty repo"):
    val remote = GitRemote("https://github.com/owner/.git")
    assertEquals(remote.repositoryOwnerAndName, Left("Invalid repository format: expected owner/repo"))

  test("GitRemote returns error for repository with too many slashes"):
    val remote = GitRemote("https://github.com/owner/repo/extra.git")
    assertEquals(remote.repositoryOwnerAndName, Left("Invalid repository format: expected owner/repo"))

  // GitHub config serialization tests
  test("ConfigSerializer serializes GitHub config to HOCON"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "",
      repository = Some("iterative-works/iw-cli"),
      projectName = "test-project"
    )
    val hocon = ConfigSerializer.toHocon(config)
    assert(hocon.contains("type = github"))
    assert(hocon.contains("repository = \"iterative-works/iw-cli\""))
    assert(!hocon.contains("team ="))

  test("ConfigSerializer deserializes HOCON with GitHub tracker"):
    val hocon = """
      tracker {
        type = github
        repository = "iterative-works/iw-cli"
      }
      project {
        name = test-project
      }
    """
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isRight)
    val config = result.getOrElse(fail("Expected Right"))
    assertEquals(config.trackerType, IssueTrackerType.GitHub)
    assertEquals(config.repository, Some("iterative-works/iw-cli"))

  test("ConfigSerializer round-trip for GitHub config"):
    val original = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "",
      repository = Some("iterative-works/iw-cli"),
      projectName = "test-project"
    )
    val hocon = ConfigSerializer.toHocon(original)
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isRight)
    val roundTripped = result.getOrElse(fail("Expected Right"))
    assertEquals(roundTripped.trackerType, original.trackerType)
    assertEquals(roundTripped.repository, original.repository)
    assertEquals(roundTripped.projectName, original.projectName)

  test("ConfigSerializer fails when GitHub config missing repository"):
    val hocon = """
      tracker {
        type = github
      }
      project {
        name = test-project
      }
    """
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("repository required for GitHub tracker"))

  test("ConfigSerializer fails when GitHub repository has invalid format"):
    val hocon = """
      tracker {
        type = github
        repository = "invalid"
      }
      project {
        name = test-project
      }
    """
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("repository must be in owner/repo format"))

  test("ConfigSerializer still handles Linear config correctly"):
    val hocon = """
      tracker {
        type = linear
        team = IWLE
      }
      project {
        name = test-project
      }
    """
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isRight)
    val config = result.getOrElse(fail("Expected Right"))
    assertEquals(config.trackerType, IssueTrackerType.Linear)
    assertEquals(config.team, "IWLE")

  test("ConfigSerializer still handles YouTrack config correctly"):
    val hocon = """
      tracker {
        type = youtrack
        team = TEST
      }
      project {
        name = test-project
      }
    """
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isRight)
    val config = result.getOrElse(fail("Expected Right"))
    assertEquals(config.trackerType, IssueTrackerType.YouTrack)
    assertEquals(config.team, "TEST")

  // TrackerDetector tests for GitHub
  test("TrackerDetector suggests GitHub for github.com HTTPS remote"):
    val suggestion = TrackerDetector.suggestTracker(GitRemote("https://github.com/user/repo.git"))
    assertEquals(suggestion, Some(IssueTrackerType.GitHub))

  test("TrackerDetector suggests GitHub for github.com SSH remote"):
    val suggestion = TrackerDetector.suggestTracker(GitRemote("git@github.com:user/repo.git"))
    assertEquals(suggestion, Some(IssueTrackerType.GitHub))

  // Edge case tests for URL parsing
  test("GitRemote extracts owner/repo from HTTPS URL with trailing slash"):
    val remote = GitRemote("https://github.com/iterative-works/iw-cli/")
    assertEquals(remote.repositoryOwnerAndName, Right("iterative-works/iw-cli"))

  test("GitRemote extracts owner/repo from HTTPS URL with .git and trailing slash"):
    val remote = GitRemote("https://github.com/iterative-works/iw-cli.git/")
    assertEquals(remote.repositoryOwnerAndName, Right("iterative-works/iw-cli"))

  test("GitRemote extracts owner/repo from HTTPS URL with username prefix"):
    val remote = GitRemote("https://username@github.com/iterative-works/iw-cli.git")
    assertEquals(remote.repositoryOwnerAndName, Right("iterative-works/iw-cli"))

  test("GitRemote extracts owner/repo from HTTPS URL with username and no .git"):
    val remote = GitRemote("https://username@github.com/iterative-works/iw-cli")
    assertEquals(remote.repositoryOwnerAndName, Right("iterative-works/iw-cli"))

  test("GitRemote handles SSH URL with trailing slash"):
    val remote = GitRemote("git@github.com:iterative-works/iw-cli/")
    assertEquals(remote.repositoryOwnerAndName, Right("iterative-works/iw-cli"))

  test("GitRemote handles SSH URL with .git and trailing slash"):
    val remote = GitRemote("git@github.com:iterative-works/iw-cli.git/")
    assertEquals(remote.repositoryOwnerAndName, Right("iterative-works/iw-cli"))

  test("GitRemote returns error for malformed SSH URL without colon"):
    val remote = GitRemote("git@github.com/iterative-works/iw-cli.git")
    assert(remote.repositoryOwnerAndName.isLeft)
