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
        teamPrefix = "IWCLI"
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
    assertEquals(config.teamPrefix, Some("IWCLI"))

  test("ConfigSerializer round-trip for GitHub config"):
    val original = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "",
      repository = Some("iterative-works/iw-cli"),
      projectName = "test-project",
      teamPrefix = Some("IWCLI")
    )
    val hocon = ConfigSerializer.toHocon(original)
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isRight)
    val roundTripped = result.getOrElse(fail("Expected Right"))
    assertEquals(roundTripped.trackerType, original.trackerType)
    assertEquals(roundTripped.repository, original.repository)
    assertEquals(roundTripped.teamPrefix, original.teamPrefix)
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

  // ========== Team Prefix Tests ==========

  test("ConfigSerializer serializes GitHub config with team prefix"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "",
      repository = Some("iterative-works/iw-cli"),
      projectName = "test-project",
      teamPrefix = Some("IWCLI")
    )
    val hocon = ConfigSerializer.toHocon(config)
    assert(hocon.contains("type = github"))
    assert(hocon.contains("repository = \"iterative-works/iw-cli\""))
    assert(hocon.contains("teamPrefix = \"IWCLI\""))
    assert(!hocon.contains("team ="))

  test("ConfigSerializer deserializes GitHub config with team prefix"):
    val hocon = """
      tracker {
        type = github
        repository = "iterative-works/iw-cli"
        teamPrefix = "IWCLI"
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
    assertEquals(config.teamPrefix, Some("IWCLI"))

  test("ConfigSerializer requires team prefix for GitHub tracker"):
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
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("teamPrefix required for GitHub tracker"))

  test("ConfigSerializer validates team prefix format - rejects lowercase"):
    val hocon = """
      tracker {
        type = github
        repository = "iterative-works/iw-cli"
        teamPrefix = "iwcli"
      }
      project {
        name = test-project
      }
    """
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("uppercase letters only"))

  test("ConfigSerializer validates team prefix format - rejects too short"):
    val hocon = """
      tracker {
        type = github
        repository = "iterative-works/iw-cli"
        teamPrefix = "I"
      }
      project {
        name = test-project
      }
    """
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("2-10 characters"))

  test("ConfigSerializer validates team prefix format - rejects too long"):
    val hocon = """
      tracker {
        type = github
        repository = "iterative-works/iw-cli"
        teamPrefix = "VERYLONGPREFIX"
      }
      project {
        name = test-project
      }
    """
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("2-10 characters"))

  test("ConfigSerializer validates team prefix format - rejects with numbers"):
    val hocon = """
      tracker {
        type = github
        repository = "iterative-works/iw-cli"
        teamPrefix = "IW2CLI"
      }
      project {
        name = test-project
      }
    """
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("uppercase letters only"))

  test("ConfigSerializer validates team prefix format - rejects with special chars"):
    val hocon = """
      tracker {
        type = github
        repository = "iterative-works/iw-cli"
        teamPrefix = "IW-CLI"
      }
      project {
        name = test-project
      }
    """
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("uppercase letters only"))

  test("ConfigSerializer round-trip for GitHub config with team prefix"):
    val original = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "",
      repository = Some("iterative-works/iw-cli"),
      projectName = "test-project",
      teamPrefix = Some("IWCLI")
    )
    val hocon = ConfigSerializer.toHocon(original)
    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isRight)
    val roundTripped = result.getOrElse(fail("Expected Right"))
    assertEquals(roundTripped.trackerType, original.trackerType)
    assertEquals(roundTripped.repository, original.repository)
    assertEquals(roundTripped.teamPrefix, original.teamPrefix)
    assertEquals(roundTripped.projectName, original.projectName)

  test("ConfigSerializer omits team prefix for Linear config"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.Linear,
      team = "IWLE",
      projectName = "test-project"
    )
    val hocon = ConfigSerializer.toHocon(config)
    assert(hocon.contains("type = linear"))
    assert(hocon.contains("team = IWLE"))
    assert(!hocon.contains("teamPrefix"))

  test("ConfigSerializer omits team prefix for YouTrack config"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.YouTrack,
      team = "TEST",
      projectName = "test-project"
    )
    val hocon = ConfigSerializer.toHocon(config)
    assert(hocon.contains("type = youtrack"))
    assert(hocon.contains("team = TEST"))
    assert(!hocon.contains("teamPrefix"))

  test("TeamPrefixValidator accepts valid uppercase prefix"):
    val result = TeamPrefixValidator.validate("IWCLI")
    assert(result.isRight)
    assertEquals(result, Right("IWCLI"))

  test("TeamPrefixValidator rejects lowercase prefix"):
    val result = TeamPrefixValidator.validate("iwcli")
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("uppercase letters only"))

  test("TeamPrefixValidator rejects too short prefix"):
    val result = TeamPrefixValidator.validate("I")
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("2-10 characters"))

  test("TeamPrefixValidator rejects too long prefix"):
    val result = TeamPrefixValidator.validate("VERYLONGPREFIX")
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("2-10 characters"))

  test("TeamPrefixValidator rejects prefix with numbers"):
    val result = TeamPrefixValidator.validate("IW2CLI")
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("uppercase letters only"))

  test("TeamPrefixValidator rejects prefix with special characters"):
    val result = TeamPrefixValidator.validate("IW-CLI")
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("uppercase letters only"))

  test("TeamPrefixValidator suggests prefix from repository owner/repo"):
    val suggested = TeamPrefixValidator.suggestFromRepository("iterative-works/iw-cli")
    assertEquals(suggested, "IWCLI")

  test("TeamPrefixValidator suggests prefix from repository with multiple hyphens"):
    val suggested = TeamPrefixValidator.suggestFromRepository("my-org/my-awesome-app")
    assertEquals(suggested, "MYAWESOMEA")

  test("TeamPrefixValidator suggests prefix from single-word repo"):
    val suggested = TeamPrefixValidator.suggestFromRepository("owner/project")
    assertEquals(suggested, "PROJECT")

  test("TeamPrefixValidator truncates very long suggested prefix to 10 chars"):
    val suggested = TeamPrefixValidator.suggestFromRepository("owner/very-long-repository-name")
    assert(suggested.length <= 10)
    assert(suggested == "VERYLONGRE")

  // ========== GitLab Repository Extraction Tests ==========

  test("GitRemote extracts owner/repo from GitLab HTTPS URL"):
    val remote = GitRemote("https://gitlab.com/owner/repo.git")
    assertEquals(remote.extractGitLabRepository, Right("owner/repo"))

  test("GitRemote extracts owner/repo from GitLab SSH URL"):
    val remote = GitRemote("git@gitlab.com:owner/repo.git")
    assertEquals(remote.extractGitLabRepository, Right("owner/repo"))

  test("GitRemote extracts nested group path from GitLab HTTPS URL"):
    val remote = GitRemote("https://gitlab.com/group/subgroup/project.git")
    assertEquals(remote.extractGitLabRepository, Right("group/subgroup/project"))

  test("GitRemote extracts nested group path from GitLab SSH URL"):
    val remote = GitRemote("git@gitlab.com:group/subgroup/project.git")
    assertEquals(remote.extractGitLabRepository, Right("group/subgroup/project"))

  test("GitRemote extracts repository from self-hosted GitLab HTTPS URL"):
    val remote = GitRemote("https://gitlab.company.com/owner/repo.git")
    assertEquals(remote.extractGitLabRepository, Right("owner/repo"))

  test("GitRemote extracts repository from self-hosted GitLab SSH URL"):
    val remote = GitRemote("git@gitlab.company.com:owner/repo.git")
    assertEquals(remote.extractGitLabRepository, Right("owner/repo"))

  test("GitRemote extracts nested groups from self-hosted GitLab"):
    val remote = GitRemote("https://gitlab.company.com/team/subteam/project.git")
    assertEquals(remote.extractGitLabRepository, Right("team/subteam/project"))

  test("GitRemote returns error for non-GitLab URL when extracting GitLab repo"):
    val remote = GitRemote("https://github.com/owner/repo.git")
    assert(remote.extractGitLabRepository.isLeft)

  test("GitRemote handles GitLab URL without .git suffix"):
    val remote = GitRemote("https://gitlab.com/owner/repo")
    assertEquals(remote.extractGitLabRepository, Right("owner/repo"))

  test("GitRemote handles GitLab URL with trailing slash"):
    val remote = GitRemote("https://gitlab.com/owner/repo/")
    assertEquals(remote.extractGitLabRepository, Right("owner/repo"))
