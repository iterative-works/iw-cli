// PURPOSE: Harness tests for Status.run

package iw.core.test

import iw.core.commands.Status
import iw.core.model.WorktreeStatus
import iw.core.test.fixtures.FakeCommandEnv

class StatusHarnessTest extends munit.FunSuite:

  private val cwd = os.root / "tmp" / "harness-repo"

  private val sampleStatus = WorktreeStatus(
    issueId = "IW-42",
    path = "/work/iw-42",
    branchName = Some("IW-42"),
    gitClean = Some(true),
    issueTitle = Some("Add status command"),
    issueStatus = Some("In Progress"),
    issueUrl = Some("https://linear.app/iw-42"),
    prUrl = None,
    prState = None,
    prNumber = None,
    reviewDisplay = Some("Implementing"),
    reviewBadges = Some(List("Review Needed")),
    needsAttention = false,
    currentPhase = Some(2),
    totalPhases = Some(5),
    overallProgress = Some(40)
  )

  test("explicit issue id: queries server with provided id") {
    val env = FakeCommandEnv(cwd = cwd)
    env.server.setWorktreeStatusResult(Right(sampleStatus))

    val result = Status.run(Seq("IW-42"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.server.statusCallList, List("IW-42"))
    assert(env.console.stdout.contains("IW-42"))
  }

  test("no args: infers issue id from current branch") {
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "IW-99")
    env.server.setWorktreeStatusResult(
      Right(sampleStatus.copy(issueId = "IW-99"))
    )

    val result = Status.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.server.statusCallList, List("IW-99"))
  }

  test("branch not an issue id: exit 1 with usage hint") {
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "main")

    val result = Status.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stdout.contains("Usage: iw status"))
    assertEquals(env.server.statusCallList, Nil)
  }

  test("server error: exit 1, error message propagated") {
    val env = FakeCommandEnv(cwd = cwd)
    env.server.setWorktreeStatusResult(Left("Server communication is disabled"))

    val result = Status.run(Seq("IW-1"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Server communication is disabled"))
  }

  test("--json: emits parseable JSON") {
    val env = FakeCommandEnv(cwd = cwd)
    env.server.setWorktreeStatusResult(Right(sampleStatus))

    val result = Status.run(Seq("IW-42", "--json"), env)

    assertEquals(result.exitCode, 0)
    val json = ujson.read(env.console.stdout)
    assertEquals(json("issueId").str, "IW-42")
  }

  test("config with teamPrefix: bare number resolves with prefix") {
    val env = FakeCommandEnv(cwd = cwd)
    env.fs.put(
      cwd / ".iw" / "config.conf",
      """tracker {
        |  type = github
        |  repository = "test/test"
        |  teamPrefix = "IW"
        |}
        |project { name = test }
        |""".stripMargin
    )
    env.server.setWorktreeStatusResult(
      Right(sampleStatus.copy(issueId = "IW-7"))
    )

    val result = Status.run(Seq("7"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.server.statusCallList, List("IW-7"))
  }
