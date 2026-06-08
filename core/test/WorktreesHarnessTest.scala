// PURPOSE: Harness tests for Worktrees.run

package iw.core.test

import iw.core.commands.Worktrees
import iw.core.model.{ServerState, WorktreeRegistration}
import iw.core.test.fixtures.FakeCommandEnv

import java.time.Instant

class WorktreesHarnessTest extends munit.FunSuite:

  private def reg(issueId: String, path: String): WorktreeRegistration =
    WorktreeRegistration(
      issueId = issueId,
      path = path,
      trackerType = "github",
      team = "TEST",
      registeredAt = Instant.parse("2026-01-01T12:00:00Z"),
      lastSeenAt = Instant.parse("2026-01-02T12:00:00Z")
    )

  test("state read failure: exit 1") {
    val env = FakeCommandEnv()
    env.state.setFailure("state.json missing")

    val result = Worktrees.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Failed to read state"))
  }

  test("empty state: succeeds with empty output") {
    val env = FakeCommandEnv()
    // default FakeStateReader is empty Right(ServerState(...))

    val result = Worktrees.run(Seq("--json"), env)

    assertEquals(result.exitCode, 0)
    val parsed = ujson.read(env.console.stdout)
    assert(
      parsed.arr.isEmpty,
      s"expected empty array, got: ${env.console.stdout}"
    )
  }

  test("--all: lists every worktree regardless of project") {
    val cwd = os.root / "home" / "user" / "iw-cli"
    val env = FakeCommandEnv(cwd = cwd)
    env.state.setState(
      ServerState(
        worktrees = Map(
          "IW-1" -> reg("IW-1", "/home/user/projA/projA-IW-1"),
          "IW-2" -> reg("IW-2", "/home/user/projB/projB-IW-2")
        )
      )
    )

    val result = Worktrees.run(Seq("--all", "--json"), env)

    assertEquals(result.exitCode, 0)
    val arr = ujson.read(env.console.stdout).arr
    assertEquals(arr.size, 2)
  }

  test("--json: emits parseable JSON array sorted by issue id") {
    val env = FakeCommandEnv()
    env.state.setState(
      ServerState(
        worktrees = Map(
          "IW-9" -> reg("IW-9", "/work/iw-9"),
          "IW-1" -> reg("IW-1", "/work/iw-1")
        )
      )
    )

    val result = Worktrees.run(Seq("--all", "--json"), env)

    assertEquals(result.exitCode, 0)
    val arr = ujson.read(env.console.stdout).arr
    assertEquals(arr(0)("issueId").str, "IW-1")
    assertEquals(arr(1)("issueId").str, "IW-9")
  }
