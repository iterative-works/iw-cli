// PURPOSE: Unit tests for MainProjectService application logic
// PURPOSE: Verifies main project derivation from worktrees and config loading

package iw.tests

import iw.core.model.{IssueTrackerType, ProjectConfiguration, WorktreeRegistration, ProjectRegistration}
import iw.core.dashboard.application.*
import iw.core.dashboard.domain.*
import java.time.Instant
import munit.FunSuite
import iw.core.model.WorktreeRegistration
import iw.core.dashboard.application.MainProjectService

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

    val config = ProjectConfiguration.create(
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

    val config = ProjectConfiguration.create(
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

    val configGitHub = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitHub,
      team = "iterative-works/iw-cli",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val configLinear = ProjectConfiguration.create(
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

    val config = ProjectConfiguration.create(
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

  // resolveProjects tests

  test("resolveProjects with no worktrees and no registered projects returns empty list"):
    val result = MainProjectService.resolveProjects(
      worktrees = List.empty,
      registeredProjects = Map.empty,
      loadConfig = _ => Right(defaultConfig)
    )
    assertEquals(result, List.empty)

  test("resolveProjects with worktrees only returns same as deriveFromWorktrees"):
    val worktree = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitHub,
      team = "iterative-works/iw-cli",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val derived = MainProjectService.deriveFromWorktrees(List(worktree), _ => Right(config))
    val resolved = MainProjectService.resolveProjects(
      worktrees = List(worktree),
      registeredProjects = Map.empty,
      loadConfig = _ => Right(config)
    )

    assertEquals(resolved.length, derived.length)
    assertEquals(resolved.map(_.projectName), derived.map(_.projectName))

  test("resolveProjects with registered projects only (no worktrees) returns registered projects"):
    val registeredAt = Instant.now()
    val registered = ProjectRegistration(
      path = "/home/user/projects/my-project",
      projectName = "my-project",
      trackerType = "linear",
      team = "IWLE",
      trackerUrl = Some("https://linear.app/iwle"),
      registeredAt = registeredAt
    )

    val result = MainProjectService.resolveProjects(
      worktrees = List.empty,
      registeredProjects = Map("/home/user/projects/my-project" -> registered),
      loadConfig = _ => Left("no config")
    )

    assertEquals(result.length, 1)
    assertEquals(result.head.projectName, "my-project")
    assertEquals(result.head.path, os.Path("/home/user/projects/my-project"))
    assertEquals(result.head.trackerType, "linear")
    assertEquals(result.head.team, "IWLE")
    assertEquals(result.head.trackerUrl, Some("https://linear.app/iwle"))

  test("resolveProjects merges registered and derived projects deduplicating by path"):
    val worktree = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitHub,
      team = "iterative-works/iw-cli",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    // A registered project at the same path as derived worktree project
    val registered = ProjectRegistration(
      path = "/home/user/projects/iw-cli",
      projectName = "iw-cli-registered",
      trackerType = "linear",
      team = "OLD-TEAM",
      trackerUrl = None,
      registeredAt = Instant.now()
    )

    val result = MainProjectService.resolveProjects(
      worktrees = List(worktree),
      registeredProjects = Map("/home/user/projects/iw-cli" -> registered),
      loadConfig = _ => Right(config)
    )

    // Should have one project (deduplication), with derived taking precedence
    assertEquals(result.length, 1)
    // Derived project has fresher config — it should win
    assertEquals(result.head.trackerType, "github")
    assertEquals(result.head.team, "iterative-works/iw-cli")

  test("resolveProjects registered project at different path appears alongside derived project"):
    val worktree = WorktreeRegistration(
      issueId = "IW-79",
      path = "/home/user/projects/iw-cli-IW-79",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitHub,
      team = "iterative-works/iw-cli",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    // A registered project at a completely different path (no worktrees for it)
    val registered = ProjectRegistration(
      path = "/home/user/projects/other-project",
      projectName = "other-project",
      trackerType = "linear",
      team = "IWLE",
      trackerUrl = None,
      registeredAt = Instant.now()
    )

    val result = MainProjectService.resolveProjects(
      worktrees = List(worktree),
      registeredProjects = Map("/home/user/projects/other-project" -> registered),
      loadConfig = _ => Right(config)
    )

    assertEquals(result.length, 2)
    assert(result.exists(_.projectName == "iw-cli"))
    assert(result.exists(_.projectName == "other-project"))

  // Helper
  private def defaultConfig = ProjectConfiguration.create(
    trackerType = IssueTrackerType.GitHub,
    team = "test",
    projectName = "test",
    repository = Some("test/test"),
    teamPrefix = Some("TEST")
  )
