// PURPOSE: Unit tests for TrackerUrlBuilder pure function
// PURPOSE: Verifies correct tracker URL construction for all supported tracker types

package iw.tests

import iw.core.model.{TrackerUrlBuilder, ProjectConfiguration, IssueTrackerType}
import munit.FunSuite

class TrackerUrlBuilderTest extends FunSuite:

  test("buildTrackerUrl returns GitHub issues URL from repository"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitHub,
      projectName = "my-project",
      repository = Some("my-org/my-repo"),
      teamPrefix = Some("MR")
    )
    val result = TrackerUrlBuilder.buildTrackerUrl(config)
    assertEquals(result, Some("https://github.com/my-org/my-repo/issues"))

  test("buildTrackerUrl returns None for GitHub when repository is missing"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitHub,
      projectName = "my-project",
      teamPrefix = Some("MP")
    )
    val result = TrackerUrlBuilder.buildTrackerUrl(config)
    assertEquals(result, None)

  test("buildTrackerUrl returns Linear workspace URL from team"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.Linear,
      team = "my-team",
      projectName = "my-project"
    )
    val result = TrackerUrlBuilder.buildTrackerUrl(config)
    assertEquals(result, Some("https://linear.app/my-team"))

  test("buildTrackerUrl lowercases team name for Linear URL"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.Linear,
      team = "MY-TEAM",
      projectName = "my-project"
    )
    val result = TrackerUrlBuilder.buildTrackerUrl(config)
    assertEquals(result, Some("https://linear.app/my-team"))

  test("buildTrackerUrl returns YouTrack issues URL with base URL and team"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.YouTrack,
      team = "MYTEAM",
      projectName = "my-project",
      youtrackBaseUrl = Some("https://mycompany.youtrack.cloud")
    )
    val result = TrackerUrlBuilder.buildTrackerUrl(config)
    assertEquals(result, Some("https://mycompany.youtrack.cloud/issues/MYTEAM"))

  test("buildTrackerUrl strips trailing slash from YouTrack base URL"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.YouTrack,
      team = "MYTEAM",
      projectName = "my-project",
      youtrackBaseUrl = Some("https://mycompany.youtrack.cloud/")
    )
    val result = TrackerUrlBuilder.buildTrackerUrl(config)
    assertEquals(result, Some("https://mycompany.youtrack.cloud/issues/MYTEAM"))

  test("buildTrackerUrl returns None for YouTrack when base URL is missing"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.YouTrack,
      team = "MYTEAM",
      projectName = "my-project"
    )
    val result = TrackerUrlBuilder.buildTrackerUrl(config)
    assertEquals(result, None)

  test(
    "buildTrackerUrl returns GitLab issues URL with default gitlab.com host"
  ):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitLab,
      projectName = "my-project",
      repository = Some("my-group/my-project"),
      teamPrefix = Some("MP")
    )
    val result = TrackerUrlBuilder.buildTrackerUrl(config)
    assertEquals(
      result,
      Some("https://gitlab.com/my-group/my-project/-/issues")
    )

  test("buildTrackerUrl returns None for GitLab when repository is missing"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitLab,
      projectName = "my-project",
      teamPrefix = Some("MP")
    )
    val result = TrackerUrlBuilder.buildTrackerUrl(config)
    assertEquals(result, None)
