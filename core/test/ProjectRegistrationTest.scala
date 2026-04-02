// PURPOSE: Tests for ProjectRegistration domain model
// PURPOSE: Verifies creation, validation, and field access for project registrations

package iw.tests

import iw.core.model.ProjectRegistration
import java.time.Instant

class ProjectRegistrationTest extends munit.FunSuite:
  test("ProjectRegistration direct construction with all fields"):
    val path = "/home/user/projects/iw-cli"
    val projectName = "iw-cli"
    val trackerType = "github"
    val team = "iterative-works/iw-cli"
    val trackerUrl = Some("https://github.com/iterative-works/iw-cli")
    val registeredAt = Instant.parse("2025-12-19T10:30:00Z")

    val registration = ProjectRegistration(
      path = path,
      projectName = projectName,
      trackerType = trackerType,
      team = team,
      trackerUrl = trackerUrl,
      registeredAt = registeredAt
    )

    assertEquals(registration.path, path)
    assertEquals(registration.projectName, projectName)
    assertEquals(registration.trackerType, trackerType)
    assertEquals(registration.team, team)
    assertEquals(registration.trackerUrl, trackerUrl)
    assertEquals(registration.registeredAt, registeredAt)

  test("ProjectRegistration direct construction with no trackerUrl"):
    val registration = ProjectRegistration(
      path = "/home/user/projects/kanon",
      projectName = "kanon",
      trackerType = "linear",
      team = "IWLE",
      trackerUrl = None,
      registeredAt = Instant.now()
    )

    assertEquals(registration.trackerUrl, None)

  test("ProjectRegistration.create with valid inputs returns Right"):
    val registeredAt = Instant.parse("2025-12-19T10:30:00Z")

    val result = ProjectRegistration.create(
      path = "/home/user/projects/iw-cli",
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      trackerUrl = Some("https://github.com/iterative-works/iw-cli"),
      registeredAt = registeredAt
    )

    assert(result.isRight)
    val reg = result.toOption.get
    assertEquals(reg.path, "/home/user/projects/iw-cli")
    assertEquals(reg.projectName, "iw-cli")
    assertEquals(reg.trackerType, "github")
    assertEquals(reg.team, "iterative-works/iw-cli")
    assertEquals(
      reg.trackerUrl,
      Some("https://github.com/iterative-works/iw-cli")
    )
    assertEquals(reg.registeredAt, registeredAt)

  test("ProjectRegistration.create with empty path returns Left"):
    val result = ProjectRegistration.create(
      path = "",
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      trackerUrl = None,
      registeredAt = Instant.now()
    )
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Path cannot be empty")))

  test("ProjectRegistration.create with whitespace-only path returns Left"):
    val result = ProjectRegistration.create(
      path = "   ",
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      trackerUrl = None,
      registeredAt = Instant.now()
    )
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Path cannot be empty")))

  test("ProjectRegistration.create with empty projectName returns Left"):
    val result = ProjectRegistration.create(
      path = "/home/user/projects/iw-cli",
      projectName = "",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      trackerUrl = None,
      registeredAt = Instant.now()
    )
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Project name cannot be empty")))

  test("ProjectRegistration.create with empty trackerType returns Left"):
    val result = ProjectRegistration.create(
      path = "/home/user/projects/iw-cli",
      projectName = "iw-cli",
      trackerType = "",
      team = "iterative-works/iw-cli",
      trackerUrl = None,
      registeredAt = Instant.now()
    )
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Tracker type cannot be empty")))

  test("ProjectRegistration.create with empty team returns Left"):
    val result = ProjectRegistration.create(
      path = "/home/user/projects/iw-cli",
      projectName = "iw-cli",
      trackerType = "github",
      team = "",
      trackerUrl = None,
      registeredAt = Instant.now()
    )
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Team cannot be empty")))
