// PURPOSE: Harness tests for Open.run

package iw.core.test

import iw.core.adapters.SessionHookResult
import iw.core.commands.Open
import iw.core.test.fixtures.FakeCommandEnv

class OpenHarnessTest extends munit.FunSuite:

  private val linearConfig =
    """project {
      |  name = testproject
      |}
      |
      |tracker {
      |  type = linear
      |  team = TEST
      |}
      |""".stripMargin

  private def seedConfig(env: FakeCommandEnv): Unit =
    env.fs.put(env.cwd / ".iw" / "config.conf", linearConfig)

  private def expectedWorktreePath(env: FakeCommandEnv, dir: String): os.Path =
    env.cwd / os.up / dir

  test("config missing: exit 1 with init hint") {
    val env = FakeCommandEnv()
    // no config file seeded

    val result = Open.run(Seq("IWLE-123"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Cannot read configuration"))
    assert(env.console.stdout.contains("./iw init"))
  }

  test("--prompt without value: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Open.run(Seq("--prompt"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("--prompt requires a text argument"))
  }

  test("invalid issue id format: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Open.run(Seq("123-invalid"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Invalid issue ID format"))
  }

  test("worktree missing: exit 1 with start hint") {
    val env = FakeCommandEnv()
    seedConfig(env)
    // worktree dir NOT seeded into FakeFileSystem

    val result = Open.run(Seq("IWLE-123"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Worktree not found"))
    assert(env.console.stdout.contains("./iw start IWLE-123"))
  }

  test("creates session and attaches when none exists and no hooks") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val targetPath = expectedWorktreePath(env, "testproject-IWLE-123")
    env.fs.put(targetPath, "")

    val result = Open.run(Seq("IWLE-123"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tmux.createCallList.size, 1)
    assertEquals(env.tmux.createCallList.head.name, "testproject-IWLE-123")
    assertEquals(env.tmux.attachCallList, List("testproject-IWLE-123"))
    assert(env.console.stdout.contains("Creating session"))
    assert(env.console.stdout.contains("Attaching to session"))
  }

  test("inside tmux: switch instead of attach") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val targetPath = expectedWorktreePath(env, "testproject-IWLE-456")
    env.fs.put(targetPath, "")
    env.tmux.setInsideTmux(true)
    env.tmux.setCurrentSessionName(Some("some-other"))
    env.tmux.addSession("testproject-IWLE-456")

    val result = Open.run(Seq("IWLE-456"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tmux.switchCallList, List("testproject-IWLE-456"))
    assertEquals(env.tmux.attachCallList, Nil)
  }

  test("already in target session: exit 0 with 'Already in session'") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val targetPath = expectedWorktreePath(env, "testproject-IWLE-789")
    env.fs.put(targetPath, "")
    env.tmux.setInsideTmux(true)
    env.tmux.setCurrentSessionName(Some("testproject-IWLE-789"))
    env.tmux.addSession("testproject-IWLE-789")

    val result = Open.run(Seq("IWLE-789"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Already in session"))
    assertEquals(env.tmux.switchCallList, Nil)
    assertEquals(env.tmux.attachCallList, Nil)
  }

  test("hook ActionHandled: do not attach") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val targetPath = expectedWorktreePath(env, "testproject-IWLE-321")
    env.fs.put(targetPath, "")
    env.tmux.addSession("testproject-IWLE-321")
    env.hooks.setSessionHookResult(SessionHookResult.ActionHandled)

    val result = Open.run(Seq("IWLE-321"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tmux.attachCallList, Nil)
    assert(env.console.stdout.contains("hook command sent"))
  }

  test("--prompt with no action hook: warn, do not attach") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val targetPath = expectedWorktreePath(env, "testproject-IWLE-555")
    env.fs.put(targetPath, "")
    env.tmux.addSession("testproject-IWLE-555")
    // default hook result is NoHooks

    val result = Open.run(Seq("--prompt", "hello", "IWLE-555"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("--prompt ignored"))
    assertEquals(env.tmux.attachCallList, Nil)
  }

  test("infers issue ID from current branch when none given") {
    val env = FakeCommandEnv(initialBranch = "IWLE-777-fix-thing")
    seedConfig(env)
    val targetPath = expectedWorktreePath(env, "testproject-IWLE-777")
    env.fs.put(targetPath, "")
    env.tmux.addSession("testproject-IWLE-777")

    val result = Open.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tmux.attachCallList, List("testproject-IWLE-777"))
  }

  test("non-issue branch without args: exit 1") {
    val env = FakeCommandEnv(initialBranch = "main")
    seedConfig(env)

    val result = Open.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Cannot extract issue ID"))
  }

  test("updates dashboard lastSeen with issue, path, tracker, team") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val targetPath = expectedWorktreePath(env, "testproject-IWLE-100")
    env.fs.put(targetPath, "")
    env.tmux.addSession("testproject-IWLE-100")

    val result = Open.run(Seq("IWLE-100"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.server.updateLastSeenCallList.size, 1)
    val call = env.server.updateLastSeenCallList.head
    assertEquals(call.issueId, "IWLE-100")
    assertEquals(call.team, "IWLE")
    assertEquals(call.trackerType, "Linear")
  }
