// PURPOSE: Harness tests for Analyze.run

package iw.core.test

import iw.core.commands.Analyze
import iw.core.test.fixtures.FakeCommandEnv

class AnalyzeHarnessTest extends munit.FunSuite:

  private val iwRun = "/install/iw-run"

  test("missing issue id: exit 1 + usage") {
    val env = FakeCommandEnv()

    val result = Analyze.run(Seq.empty, env, iwRun)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Missing issue ID"))
    assert(env.console.stdout.contains("Usage: iw analyze"))
    assertEquals(env.process.interactiveCallList, Nil)
  }

  test("with issue id: delegates to iw start --prompt triage-issue <id>") {
    val env = FakeCommandEnv()

    val result = Analyze.run(Seq("IW-42"), env, iwRun)

    assertEquals(result.exitCode, 0)
    assertEquals(
      env.process.interactiveCallList,
      List(
        Seq(
          iwRun,
          "start",
          "--prompt",
          "/iterative-works:triage-issue",
          "IW-42"
        )
      )
    )
  }

  test("propagates subprocess non-zero exit") {
    val env = FakeCommandEnv()
    env.process.setInteractiveExit(7)

    val result = Analyze.run(Seq("IW-42"), env, iwRun)

    assertEquals(result.exitCode, 7)
  }
