// PURPOSE: Unit tests for RepoUrlBuilder pure function
// PURPOSE: Verifies correct repo URL construction for all supported tracker types

package iw.tests

import iw.core.model.{RepoUrlBuilder, ProjectConfiguration, IssueTrackerType}
import munit.FunSuite

class RepoUrlBuilderTest extends FunSuite:

  test("buildRepoUrl returns None when repository is not set"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitHub,
      projectName = "my-project"
    )
    assertEquals(RepoUrlBuilder.buildRepoUrl(config), None)

  test("buildRepoUrl returns GitHub repo URL from repository"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitHub,
      projectName = "my-project",
      repository = Some("my-org/my-repo")
    )
    assertEquals(
      RepoUrlBuilder.buildRepoUrl(config),
      Some("https://github.com/my-org/my-repo")
    )

  test(
    "buildRepoUrl returns GitLab repo URL using gitlab.com when trackerBaseUrl is unset"
  ):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitLab,
      projectName = "my-project",
      repository = Some("my-group/my-project")
    )
    assertEquals(
      RepoUrlBuilder.buildRepoUrl(config),
      Some("https://gitlab.com/my-group/my-project")
    )

  test("buildRepoUrl returns GitLab repo URL using self-hosted trackerBaseUrl"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitLab,
      projectName = "my-project",
      repository = Some("my-group/my-project"),
      trackerBaseUrl = Some("https://gitlab.example.com")
    )
    assertEquals(
      RepoUrlBuilder.buildRepoUrl(config),
      Some("https://gitlab.example.com/my-group/my-project")
    )

  test("buildRepoUrl strips trailing slash from self-hosted GitLab base URL"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitLab,
      projectName = "my-project",
      repository = Some("my-group/my-project"),
      trackerBaseUrl = Some("https://gitlab.example.com/")
    )
    assertEquals(
      RepoUrlBuilder.buildRepoUrl(config),
      Some("https://gitlab.example.com/my-group/my-project")
    )

  test("buildRepoUrl handles GitLab nested groups"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitLab,
      projectName = "my-project",
      repository = Some("group/subgroup/project")
    )
    assertEquals(
      RepoUrlBuilder.buildRepoUrl(config),
      Some("https://gitlab.com/group/subgroup/project")
    )

  test(
    "buildRepoUrl returns GitHub-style repo URL for Linear-tracked project with repository set"
  ):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.Linear,
      team = "my-team",
      projectName = "my-project",
      repository = Some("my-org/my-repo")
    )
    assertEquals(
      RepoUrlBuilder.buildRepoUrl(config),
      Some("https://github.com/my-org/my-repo")
    )

  test(
    "buildRepoUrl returns None for Linear-tracked project without repository"
  ):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.Linear,
      team = "my-team",
      projectName = "my-project"
    )
    assertEquals(RepoUrlBuilder.buildRepoUrl(config), None)

  test(
    "buildRepoUrl returns GitHub-style repo URL for YouTrack-tracked project with repository set"
  ):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.YouTrack,
      team = "MYTEAM",
      projectName = "my-project",
      repository = Some("my-org/my-repo"),
      trackerBaseUrl = Some("https://youtrack.example.com")
    )
    assertEquals(
      RepoUrlBuilder.buildRepoUrl(config),
      Some("https://github.com/my-org/my-repo")
    )

  test(
    "buildRepoUrl returns None for YouTrack-tracked project without repository"
  ):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.YouTrack,
      team = "MYTEAM",
      projectName = "my-project",
      trackerBaseUrl = Some("https://youtrack.example.com")
    )
    assertEquals(RepoUrlBuilder.buildRepoUrl(config), None)

  test("buildRepoUrl returns None for GitLab when repository is not set"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitLab,
      projectName = "my-project",
      trackerBaseUrl = Some("https://gitlab.example.com")
    )
    assertEquals(RepoUrlBuilder.buildRepoUrl(config), None)

  test("buildRepoUrl returns None when trackerBaseUrl uses an unsafe scheme"):
    // Defence in depth: even though Config parsing rejects non-http(s) baseUrl,
    // RepoUrlBuilder also filters here so the rule travels with the function.
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitLab,
      projectName = "my-project",
      repository = Some("my-group/my-project"),
      trackerBaseUrl = Some("javascript:alert(1)//")
    )
    assertEquals(RepoUrlBuilder.buildRepoUrl(config), None)
