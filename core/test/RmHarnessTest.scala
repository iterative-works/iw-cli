// PURPOSE: Harness tests for Rm.run

package iw.core.test

import iw.core.commands.Rm
import iw.core.test.fixtures.FakeCommandEnv

class RmHarnessTest extends munit.FunSuite:

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

  private def worktreePath(env: FakeCommandEnv, dirName: String): os.Path =
    env.cwd / os.up / dirName

  test("config missing: exit 1 with init hint") {
    val env = FakeCommandEnv()

    val result = Rm.run(Seq("IWLE-123"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Cannot read configuration"))
  }

  test("missing issue id: exit 1 with usage") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Rm.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Missing issue ID"))
    assert(env.console.stdout.contains("Usage:"))
  }

  test("invalid issue id format: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Rm.run(Seq("123-invalid"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Invalid issue ID format"))
  }

  test("worktree missing: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Rm.run(Seq("IWLE-123"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Worktree not found"))
  }

  test("inside target session: refuse removal") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-200")
    env.worktree.addWorktree(wt)
    env.tmux.setInsideTmux(true)
    env.tmux.setCurrentSessionName(Some("testproject-IWLE-200"))

    val result = Rm.run(Seq("IWLE-200"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Cannot remove worktree"))
    assertEquals(env.worktree.removeCallList, Nil)
  }

  test("clean worktree without session: removes and unregisters") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-300")
    env.worktree.addWorktree(wt)

    val result = Rm.run(Seq("IWLE-300"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.worktree.removeCallList.size, 1)
    assertEquals(env.worktree.removeCallList.head.path, wt)
    assertEquals(env.worktree.removeCallList.head.force, false)
    assertEquals(env.tmux.killCallList, Nil)
    assertEquals(env.server.unregisterCallList, List("IWLE-300"))
  }

  test("worktree with existing session: kills session before removal") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-400")
    env.worktree.addWorktree(wt)
    env.tmux.addSession("testproject-IWLE-400")

    val result = Rm.run(Seq("IWLE-400"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tmux.killCallList, List("testproject-IWLE-400"))
    assert(env.console.stdout.contains("Killing tmux session"))
  }

  test("uncommitted changes + decline confirm: cancel without removing") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-500")
    env.worktree.addWorktree(wt)
    env.git.setUncommittedChanges(Right(true))
    env.prompt.queueAnswers(false)

    val result = Rm.run(Seq("IWLE-500"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Removal cancelled"))
    assertEquals(env.worktree.removeCallList, Nil)
    assertEquals(env.prompt.callList.size, 1)
  }

  test("uncommitted changes + confirm: force-removes") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-600")
    env.worktree.addWorktree(wt)
    env.git.setUncommittedChanges(Right(true))
    env.prompt.queueAnswers(true)

    val result = Rm.run(Seq("IWLE-600"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.worktree.removeCallList.head.force, true)
  }

  test("--force bypasses prompt entirely") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-700")
    env.worktree.addWorktree(wt)
    env.git.setUncommittedChanges(Right(true))

    val result = Rm.run(Seq("--force", "IWLE-700"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.prompt.callList, Nil)
    assertEquals(env.worktree.removeCallList.head.force, true)
  }

  test("hasUncommittedChanges error: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-800")
    env.worktree.addWorktree(wt)
    env.git.setUncommittedChanges(Left("git diff failed"))

    val result = Rm.run(Seq("IWLE-800"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Failed to check for uncommitted"))
  }

  test("worktree remove failure: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-900")
    env.worktree.addWorktree(wt)
    env.worktree.setRemoveResult(Left("worktree busy"))

    val result = Rm.run(Seq("IWLE-900"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Failed to remove worktree"))
  }

  test("kill session failure: warns but continues with worktree removal") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-110")
    env.worktree.addWorktree(wt)
    env.tmux.addSession("testproject-IWLE-110")
    env.tmux.setKillResult(Left("tmux unreachable"))

    val result = Rm.run(Seq("IWLE-110"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Failed to kill session"))
    assertEquals(env.worktree.removeCallList.size, 1)
  }
