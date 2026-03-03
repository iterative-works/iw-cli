// PURPOSE: Unit tests for ProjectRegistrationService business logic
// PURPOSE: Verifies registration, updates, and deregistration using pure functions

package iw.core.application

import munit.FunSuite
import iw.core.model.{ServerState, ProjectRegistration}
import java.time.Instant
import iw.core.dashboard.ProjectRegistrationService

class ProjectRegistrationServiceTest extends FunSuite:

  test("register creates new ProjectRegistration with provided fields and wasCreated=true"):
    val state = ServerState(Map.empty)
    val timestamp = Instant.now()

    val result = ProjectRegistrationService.register(
      path = "/home/user/projects/my-project",
      projectName = "my-project",
      trackerType = "Linear",
      team = "IWLE",
      trackerUrl = Some("https://linear.app/iwle"),
      timestamp = timestamp,
      state = state
    )

    assert(result.isRight)
    result.foreach { case (newState, wasCreated) =>
      assert(wasCreated)
      val reg = newState.projects.get("/home/user/projects/my-project")
      assert(reg.isDefined)
      reg.foreach { r =>
        assertEquals(r.path, "/home/user/projects/my-project")
        assertEquals(r.projectName, "my-project")
        assertEquals(r.trackerType, "Linear")
        assertEquals(r.team, "IWLE")
        assertEquals(r.trackerUrl, Some("https://linear.app/iwle"))
        assertEquals(r.registeredAt, timestamp)
      }
    }

  test("register adds ProjectRegistration to ServerState.projects map"):
    val state = ServerState(Map.empty)
    val timestamp = Instant.now()

    val result = ProjectRegistrationService.register(
      path = "/home/user/projects/another-project",
      projectName = "another-project",
      trackerType = "GitHub",
      team = "org/repo",
      trackerUrl = None,
      timestamp = timestamp,
      state = state
    )

    assert(result.isRight)
    result.foreach { case (newState, _) =>
      assert(newState.projects.contains("/home/user/projects/another-project"))
      assertEquals(newState.projects.size, 1)
    }

  test("register existing project updates fields and returns wasCreated=false"):
    val earlier = Instant.now().minusSeconds(3600)
    val later = Instant.now()
    val existing = ProjectRegistration(
      path = "/home/user/projects/my-project",
      projectName = "my-project",
      trackerType = "Linear",
      team = "IWLE",
      trackerUrl = None,
      registeredAt = earlier
    )
    val state = ServerState(Map.empty, projects = Map("/home/user/projects/my-project" -> existing))

    val result = ProjectRegistrationService.register(
      path = "/home/user/projects/my-project",
      projectName = "my-project-renamed",
      trackerType = "GitHub",
      team = "new-team",
      trackerUrl = Some("https://github.com/new-team/repo"),
      timestamp = later,
      state = state
    )

    assert(result.isRight)
    result.foreach { case (newState, wasCreated) =>
      assert(!wasCreated)
      val reg = newState.projects.get("/home/user/projects/my-project")
      assert(reg.isDefined)
      reg.foreach { r =>
        assertEquals(r.projectName, "my-project-renamed")
        assertEquals(r.trackerType, "GitHub")
        assertEquals(r.team, "new-team")
        assertEquals(r.trackerUrl, Some("https://github.com/new-team/repo"))
      }
    }

  test("register existing project preserves registeredAt timestamp"):
    val earlier = Instant.now().minusSeconds(3600)
    val later = Instant.now()
    val existing = ProjectRegistration(
      path = "/home/user/projects/my-project",
      projectName = "my-project",
      trackerType = "Linear",
      team = "IWLE",
      trackerUrl = None,
      registeredAt = earlier
    )
    val state = ServerState(Map.empty, projects = Map("/home/user/projects/my-project" -> existing))

    val result = ProjectRegistrationService.register(
      path = "/home/user/projects/my-project",
      projectName = "my-project",
      trackerType = "Linear",
      team = "IWLE",
      trackerUrl = None,
      timestamp = later,
      state = state
    )

    assert(result.isRight)
    result.foreach { case (newState, _) =>
      val reg = newState.projects.get("/home/user/projects/my-project")
      assert(reg.isDefined)
      reg.foreach { r =>
        assertEquals(r.registeredAt, earlier)
      }
    }

  test("register returns Left for empty path"):
    val state = ServerState(Map.empty)
    val timestamp = Instant.now()

    val result = ProjectRegistrationService.register(
      path = "",
      projectName = "my-project",
      trackerType = "Linear",
      team = "IWLE",
      trackerUrl = None,
      timestamp = timestamp,
      state = state
    )

    assert(result.isLeft)

  test("register returns Left for empty projectName"):
    val state = ServerState(Map.empty)
    val timestamp = Instant.now()

    val result = ProjectRegistrationService.register(
      path = "/home/user/projects/my-project",
      projectName = "",
      trackerType = "Linear",
      team = "IWLE",
      trackerUrl = None,
      timestamp = timestamp,
      state = state
    )

    assert(result.isLeft)

  test("deregister removes project from state"):
    val existing = ProjectRegistration(
      path = "/home/user/projects/my-project",
      projectName = "my-project",
      trackerType = "Linear",
      team = "IWLE",
      trackerUrl = None,
      registeredAt = Instant.now()
    )
    val state = ServerState(Map.empty, projects = Map("/home/user/projects/my-project" -> existing))

    val newState = ProjectRegistrationService.deregister("/home/user/projects/my-project", state)

    assert(!newState.projects.contains("/home/user/projects/my-project"))
    assertEquals(newState.projects.size, 0)

  test("deregister is idempotent for non-existent path"):
    val state = ServerState(Map.empty)

    val newState = ProjectRegistrationService.deregister("/home/user/projects/non-existent", state)

    assertEquals(newState.projects.size, 0)
    assertEquals(newState, state)
