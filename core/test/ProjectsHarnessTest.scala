// PURPOSE: Harness tests for Projects.run

package iw.core.test

import iw.core.commands.Projects
import iw.core.model.{ProjectRegistration, ServerState, WorktreeRegistration}
import iw.core.test.fixtures.FakeCommandEnv

import java.time.Instant

class ProjectsHarnessTest extends munit.FunSuite:

  private def reg(issueId: String, path: String): WorktreeRegistration =
    WorktreeRegistration(
      issueId = issueId,
      path = path,
      trackerType = "github",
      team = "TEST",
      registeredAt = Instant.parse("2026-01-01T12:00:00Z"),
      lastSeenAt = Instant.parse("2026-01-02T12:00:00Z")
    )

  private val sampleConfig =
    """project {
      |  name = sample
      |}
      |
      |tracker {
      |  type = github
      |  teamPrefix = "SAMPLE"
      |}
      |""".stripMargin

  test("state read failure: exit 1") {
    val env = FakeCommandEnv()
    env.state.setFailure("state.json missing")

    val result = Projects.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Failed to read state"))
  }

  test("empty state with --json: emits empty array") {
    val env = FakeCommandEnv()

    val result = Projects.run(Seq("--json"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.console.stdout.trim, "[]")
  }

  test("worktree-derived project reads config for tracker info") {
    val env = FakeCommandEnv()
    env.state.setState(
      ServerState(
        worktrees = Map(
          "TEST-1" -> reg("TEST-1", "/home/user/sample-TEST-1"),
          "TEST-2" -> reg("TEST-2", "/home/user/sample-TEST-2")
        )
      )
    )
    env.fs.put(os.Path("/home/user/sample/.iw/config.conf"), sampleConfig)

    val result = Projects.run(Seq("--json"), env)

    assertEquals(result.exitCode, 0)
    val arr = ujson.read(env.console.stdout).arr
    assertEquals(arr.size, 1)
    val p = arr(0)
    assertEquals(p("name").str, "sample")
    assertEquals(p("path").str, "/home/user/sample")
    assertEquals(p("trackerType").str, "github")
    assertEquals(p("team").str, "SAMPLE")
    assertEquals(p("worktreeCount").num.toInt, 2)
  }

  test("missing config falls back to unknown tracker/team") {
    val env = FakeCommandEnv()
    env.state.setState(
      ServerState(
        worktrees = Map("X-1" -> reg("X-1", "/home/user/mystery-X-1"))
      )
    )

    val result = Projects.run(Seq("--json"), env)

    assertEquals(result.exitCode, 0)
    val arr = ujson.read(env.console.stdout).arr
    assertEquals(arr.size, 1)
    assertEquals(arr(0)("trackerType").str, "unknown")
    assertEquals(arr(0)("team").str, "unknown")
    assertEquals(arr(0)("worktreeCount").num.toInt, 1)
  }

  test("registered projects without worktrees appear with count 0") {
    val env = FakeCommandEnv()
    env.state.setState(
      ServerState(
        worktrees = Map.empty,
        projects = Map(
          "/home/user/onlyreg" -> ProjectRegistration(
            path = "/home/user/onlyreg",
            projectName = "onlyreg",
            trackerType = "linear",
            team = "REG",
            trackerUrl = None,
            registeredAt = Instant.parse("2026-01-01T00:00:00Z")
          )
        )
      )
    )

    val result = Projects.run(Seq("--json"), env)

    assertEquals(result.exitCode, 0)
    val arr = ujson.read(env.console.stdout).arr
    assertEquals(arr.size, 1)
    assertEquals(arr(0)("name").str, "onlyreg")
    assertEquals(arr(0)("worktreeCount").num.toInt, 0)
  }

  test("registered project covered by worktree-derived is suppressed") {
    val env = FakeCommandEnv()
    env.state.setState(
      ServerState(
        worktrees = Map(
          "TEST-1" -> reg("TEST-1", "/home/user/sample-TEST-1")
        ),
        projects = Map(
          "/home/user/sample" -> ProjectRegistration(
            path = "/home/user/sample",
            projectName = "sample-from-reg",
            trackerType = "linear",
            team = "REG",
            trackerUrl = None,
            registeredAt = Instant.parse("2026-01-01T00:00:00Z")
          )
        )
      )
    )
    env.fs.put(os.Path("/home/user/sample/.iw/config.conf"), sampleConfig)

    val result = Projects.run(Seq("--json"), env)

    assertEquals(result.exitCode, 0)
    val arr = ujson.read(env.console.stdout).arr
    assertEquals(arr.size, 1)
    assertEquals(arr(0)("name").str, "sample")
    assertEquals(arr(0)("worktreeCount").num.toInt, 1)
  }

  test("text output: empty state shows 'No projects registered'") {
    val env = FakeCommandEnv()

    val result = Projects.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("No projects registered"))
  }
