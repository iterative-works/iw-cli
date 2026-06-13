// PURPOSE: Harness tests for Rm.run

package iw.core.test

import iw.core.commands.Rm
import iw.core.model.{CleanupAction, CleanupContext}
import iw.core.test.fixtures.FakeCommandEnv

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable

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

  // ========== Cleanup hook scenarios ==========

  test("no cleanup hooks: removal proceeds normally") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-1001")
    env.worktree.addWorktree(wt)
    env.hooks.setCleanupActions(Nil)

    val result = Rm.run(Seq("IWLE-1001"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.worktree.removeCallList.size, 1)
  }

  test(
    "single hook returning Nil: proceeds, hook ran before removal, context correct"
  ) {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-1002")
    env.worktree.addWorktree(wt)

    val receivedCtx = mutable.ArrayBuffer.empty[CleanupContext]
    val events = mutable.ArrayBuffer.empty[String]
    val hook = new CleanupAction:
      def cleanup(ctx: CleanupContext): List[String] =
        receivedCtx += ctx
        events += "hook"
        List("ran")

    env.hooks.setCleanupActions(List(hook))

    val result = Rm.run(Seq("IWLE-1002"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(events.toList, List("hook"), "Hook must have been invoked")
    assertEquals(env.worktree.removeCallList.size, 1)

    // Verify context fields reach the hook correctly
    assertEquals(receivedCtx.headOption.map(_.issueId), Some("IWLE-1002"))
    assertEquals(receivedCtx.headOption.map(_.worktreePath), Some(wt))
    assertEquals(receivedCtx.headOption.map(_.force), Some(false))

    // Verify ordering: Warning: ran must appear before "Removing worktree"
    val lines = env.console.stdoutLines
    val warningIdx = lines.indexWhere(_.contains("ran"))
    val removingIdx = lines.indexWhere(_.contains("Removing worktree"))
    assert(warningIdx >= 0, "Warning line must appear in stdout")
    assert(removingIdx >= 0, "'Removing worktree' line must appear in stdout")
    assert(
      warningIdx < removingIdx,
      s"Hook warning (line $warningIdx) must appear before 'Removing worktree' (line $removingIdx)"
    )
  }

  test(
    "single hook returning warnings: warnings printed before removal, proceeds"
  ) {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-1003")
    env.worktree.addWorktree(wt)

    val hook = new CleanupAction:
      def cleanup(ctx: CleanupContext): List[String] =
        List("daemon X still running")

    env.hooks.setCleanupActions(List(hook))

    val result = Rm.run(Seq("IWLE-1003"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.worktree.removeCallList.size, 1)
    assert(
      env.console.stdout.contains("daemon X still running"),
      s"Expected warning in stdout: ${env.console.stdout}"
    )
    val lines = env.console.stdoutLines
    val warningIdx = lines.indexWhere(_.contains("daemon X still running"))
    val removingIdx = lines.indexWhere(_.contains("Removing worktree"))
    assert(warningIdx >= 0, "Warning line must appear in stdout")
    assert(removingIdx >= 0, "'Removing worktree' line must appear in stdout")
    assert(
      warningIdx < removingIdx,
      s"Warning (line $warningIdx) must appear before 'Removing worktree' (line $removingIdx)"
    )
  }

  test(
    "single hook throwing: error printed, worktree.remove NOT called, exit 1"
  ) {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-1004")
    env.worktree.addWorktree(wt)

    val hook = new CleanupAction:
      def cleanup(ctx: CleanupContext): List[String] =
        sys.error("boom")

    env.hooks.setCleanupActions(List(hook))

    val result = Rm.run(Seq("IWLE-1004"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("boom"),
      s"Expected error in stderr: ${env.console.stderr}"
    )
    assertEquals(
      env.worktree.removeCallList,
      Nil,
      "Worktree must NOT be removed when a hook aborts"
    )
  }

  test("multiple hooks: declared order, warnings aggregate, removal proceeds") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-1005")
    env.worktree.addWorktree(wt)

    val order = mutable.ArrayBuffer.empty[String]
    val hookA = new CleanupAction:
      def cleanup(ctx: CleanupContext): List[String] =
        order += "A"
        List("warnA")
    val hookB = new CleanupAction:
      def cleanup(ctx: CleanupContext): List[String] =
        order += "B"
        List("warnB")

    env.hooks.setCleanupActions(List(hookA, hookB))

    val result = Rm.run(Seq("IWLE-1005"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.worktree.removeCallList.size, 1)
    assertEquals(order.toList, List("A", "B"))
    val out = env.console.stdout
    assert(out.contains("warnA"), s"Expected warnA in stdout: $out")
    assert(out.contains("warnB"), s"Expected warnB in stdout: $out")
    val lines = env.console.stdoutLines
    val idxA = lines.indexWhere(_.contains("warnA"))
    val idxB = lines.indexWhere(_.contains("warnB"))
    val idxRemoving = lines.indexWhere(_.contains("Removing worktree"))
    assert(
      idxA < idxB,
      s"warnA (line $idxA) must appear before warnB (line $idxB)"
    )
    assert(
      idxA < idxRemoving,
      s"warnA (line $idxA) must appear before 'Removing worktree' (line $idxRemoving)"
    )
    assert(
      idxB < idxRemoving,
      s"warnB (line $idxB) must appear before 'Removing worktree' (line $idxRemoving)"
    )
  }

  test("first hook throws: second hook never runs, removal skipped, exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val wt = worktreePath(env, "testproject-IWLE-1006")
    env.worktree.addWorktree(wt)

    val secondRan = AtomicBoolean(false)
    val hookA = new CleanupAction:
      def cleanup(ctx: CleanupContext): List[String] =
        sys.error("hook A failed")
    val hookB = new CleanupAction:
      def cleanup(ctx: CleanupContext): List[String] =
        secondRan.set(true)
        Nil

    env.hooks.setCleanupActions(List(hookA, hookB))

    val result = Rm.run(Seq("IWLE-1006"), env)

    assertEquals(result.exitCode, 1)
    assert(!secondRan.get(), "Second hook must NOT run when first hook throws")
    assertEquals(
      env.worktree.removeCallList,
      Nil,
      "Worktree must NOT be removed"
    )
  }
