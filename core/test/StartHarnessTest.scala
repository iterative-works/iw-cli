// PURPOSE: Harness tests for Start.run

package iw.core.test

import iw.core.adapters.SessionHookResult
import iw.core.commands.Start
import iw.core.test.fixtures.FakeCommandEnv

class StartHarnessTest extends munit.FunSuite:

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

  private def targetPathFor(env: FakeCommandEnv, dir: String): os.Path =
    env.cwd / os.up / dir

  test("missing issue id: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Start.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Missing issue ID"))
  }

  test("config missing: exit 1") {
    val env = FakeCommandEnv()

    val result = Start.run(Seq("IWLE-100"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Cannot read configuration"))
  }

  test("--prompt without value: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Start.run(Seq("--prompt"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("--prompt requires a text argument"))
  }

  test("invalid issue id: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Start.run(Seq("garbled"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Invalid issue ID format"))
  }

  test("target directory already present: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val target = targetPathFor(env, "testproject-IWLE-200")
    env.fs.put(target, "")
    env.worktree.addWorktree(target)

    val result = Start.run(Seq("IWLE-200"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("already exists"))
    assert(env.console.stdout.contains("./iw open IWLE-200"))
  }

  test("tmux session already present: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)
    env.tmux.addSession("testproject-IWLE-300")

    val result = Start.run(Seq("IWLE-300"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("already exists"))
    assert(env.console.stdout.contains("./iw open IWLE-300"))
  }

  test("happy path with new branch: creates worktree, registers, attaches") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Start.run(Seq("IWLE-400"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.worktree.createCallList.size, 1)
    val createCall = env.worktree.createCallList.head
    assertEquals(createCall.branchName, "IWLE-400")
    assertEquals(createCall.forExistingBranch, false)
    assertEquals(env.tmux.createCallList.size, 1)
    assertEquals(env.tmux.attachCallList, List("testproject-IWLE-400"))
    assertEquals(env.server.registerWorktreeCallList.size, 1)
    assertEquals(env.server.registerProjectCallList.size, 1)
  }

  test("happy path with existing branch: uses createForBranch") {
    val env = FakeCommandEnv()
    seedConfig(env)
    env.worktree.addBranch("IWLE-500")

    val result = Start.run(Seq("IWLE-500"), env)

    assertEquals(result.exitCode, 0)
    val createCall = env.worktree.createCallList.head
    assertEquals(createCall.forExistingBranch, true)
    assert(env.console.stdout.contains("Using existing branch"))
  }

  test("worktree creation failure: exit 1 without further work") {
    val env = FakeCommandEnv()
    seedConfig(env)
    env.worktree.setCreateResult(Left("branch in use"))

    val result = Start.run(Seq("IWLE-600"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("branch in use"))
    assertEquals(env.tmux.createCallList, Nil)
    assertEquals(env.server.registerWorktreeCallList, Nil)
  }

  test("tmux create failure: cleans up worktree, exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)
    env.tmux.setCreateResult(Left("no display"))

    val result = Start.run(Seq("IWLE-700"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("no display"))
    assert(env.console.stdout.contains("Cleaning up worktree"))
    assertEquals(env.worktree.removeCallList.size, 1)
  }

  test("dashboard register failure: warns but still attaches") {
    val env = FakeCommandEnv()
    seedConfig(env)
    env.server.setRegisterWorktreeResult(Left("server down"))

    val result = Start.run(Seq("IWLE-800"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Failed to register worktree"))
    assertEquals(env.tmux.attachCallList.size, 1)
  }

  test("inside tmux: switches instead of attaching") {
    val env = FakeCommandEnv()
    seedConfig(env)
    env.tmux.setInsideTmux(true)

    val result = Start.run(Seq("IWLE-900"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tmux.switchCallList, List("testproject-IWLE-900"))
    assertEquals(env.tmux.attachCallList, Nil)
  }

  test("hook ActionHandled: do not attach") {
    val env = FakeCommandEnv()
    seedConfig(env)
    env.hooks.setSessionHookResult(SessionHookResult.ActionHandled)

    val result = Start.run(Seq("IWLE-110"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tmux.attachCallList, Nil)
    assert(env.console.stdout.contains("hook command sent"))
  }

  test("--prompt without hook action: warn, exit 0, do not attach") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Start.run(Seq("--prompt", "do x", "IWLE-120"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("--prompt ignored"))
    assertEquals(env.tmux.attachCallList, Nil)
  }
