// PURPOSE: Unit tests for MainProjectService application logic
// PURPOSE: Verifies main project derivation from worktrees and config loading

package iw.tests

import iw.core.*
import iw.core.application.*
import iw.core.domain.*
import java.time.Instant
import munit.FunSuite

class MainProjectServiceTest extends FunSuite:
  test("deriveFromWorktrees with empty list returns empty list"):
    val result = MainProjectService.deriveFromWorktrees(List.empty, _ => Right(defaultConfig))
    assertEquals(result, List.empty)

  test("deriveFromWorktrees with single worktree returns one project"):
    val worktree = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "iterative-works/iw-cli",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val result = MainProjectService.deriveFromWorktrees(
      List(worktree),
      _ => Right(config)
    )

    assertEquals(result.length, 1)
    assertEquals(result.head.path, os.Path("/home/user/projects/iw-cli"))
    assertEquals(result.head.projectName, "iw-cli")
    assertEquals(result.head.trackerType, "github")
    assertEquals(result.head.team, "iterative-works/iw-cli")

  test("deriveFromWorktrees with multiple worktrees from same project returns one project"):
    val worktree1 = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val worktree2 = WorktreeRegistration(
      issueId = "IW-80",
      path = "/home/user/projects/iw-cli-IW-80",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "iterative-works/iw-cli",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val result = MainProjectService.deriveFromWorktrees(
      List(worktree1, worktree2),
      _ => Right(config)
    )

    // Should deduplicate - only one project even with two worktrees
    assertEquals(result.length, 1)
    assertEquals(result.head.projectName, "iw-cli")

  test("deriveFromWorktrees with multiple worktrees from different projects returns multiple projects"):
    val worktree1 = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val worktree2 = WorktreeRegistration(
      issueId = "IWLE-123",
      path = "/home/user/projects/kanon-IWLE-123",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val configGitHub = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "iterative-works/iw-cli",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val configLinear = ProjectConfiguration(
      trackerType = IssueTrackerType.Linear,
      team = "IWLE",
      projectName = "kanon"
    )

    def loadConfig(path: os.Path): Either[String, ProjectConfiguration] =
      if path.toString.contains("iw-cli") then
        Right(configGitHub)
      else
        Right(configLinear)

    val result = MainProjectService.deriveFromWorktrees(
      List(worktree1, worktree2),
      loadConfig
    )

    assertEquals(result.length, 2)
    assert(result.exists(_.projectName == "iw-cli"))
    assert(result.exists(_.projectName == "kanon"))

  test("deriveFromWorktrees filters out worktrees without valid issue ID suffix"):
    val validWorktree = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val invalidWorktree = WorktreeRegistration(
      issueId = "manual",
      path = "/home/user/projects/some-random-path",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "iterative-works/iw-cli",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val result = MainProjectService.deriveFromWorktrees(
      List(validWorktree, invalidWorktree),
      _ => Right(config)
    )

    // Only the valid worktree should result in a project
    assertEquals(result.length, 1)
    assertEquals(result.head.projectName, "iw-cli")

  test("deriveFromWorktrees filters out projects with missing config"):
    val worktree = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )

    val result = MainProjectService.deriveFromWorktrees(
      List(worktree),
      _ => Left("Config file not found")
    )

    // Should filter out projects where config loading fails
    assertEquals(result, List.empty)

  // Helper
  private def defaultConfig = ProjectConfiguration(
    trackerType = IssueTrackerType.GitHub,
    team = "test",
    projectName = "test",
    repository = Some("test/test"),
    teamPrefix = Some("TEST")
  )
