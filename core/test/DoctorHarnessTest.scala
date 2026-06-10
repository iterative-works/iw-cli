// PURPOSE: Harness tests for Doctor.run

package iw.core.test

import iw.core.commands.Doctor
import iw.core.model.{
  Check,
  CheckResult,
  DoctorFixContext,
  FixAction,
  ProjectConfiguration
}
import iw.core.test.fixtures.FakeCommandEnv

class DoctorHarnessTest extends munit.FunSuite:

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

  test("missing config: exit 1 with 'Missing or invalid'") {
    val env = FakeCommandEnv()

    val result = Doctor.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stdout.contains("Configuration"))
    assert(env.console.stdout.contains("Missing or invalid"))
  }

  test("valid config + git repo: exit 0 with all checks passed") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Doctor.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("All checks passed"))
    assert(env.console.stdout.contains("Git repository"))
    assert(env.console.stdout.contains("Configuration"))
  }

  test("git not a repository: exit 1, hints at git init") {
    val env = FakeCommandEnv()
    seedConfig(env)
    env.git.setIsRepository(false)

    val result = Doctor.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stdout.contains("Not found"))
    assert(env.console.stdout.contains("git init"))
  }

  test("discovered plugin checks are included") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val pluginCheck = Check(
      "Custom plugin check",
      _ => CheckResult.Success("ok"),
      category = "Quality"
    )
    env.hooks.setDiscoveredChecks(List(pluginCheck))

    val result = Doctor.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Custom plugin check"))
  }

  test("--quality filters out Environment checks") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val pluginCheck = Check(
      "Quality gate",
      _ => CheckResult.Success("ok"),
      category = "Quality"
    )
    env.hooks.setDiscoveredChecks(List(pluginCheck))

    val result = Doctor.run(Seq("--quality"), env)

    assertEquals(result.exitCode, 0)
    assert(!env.console.stdout.contains("Git repository"))
    assert(env.console.stdout.contains("Quality gate"))
  }

  test("--env filters out Quality checks") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val pluginCheck = Check(
      "Quality gate",
      _ => CheckResult.Success("ok"),
      category = "Quality"
    )
    env.hooks.setDiscoveredChecks(List(pluginCheck))

    val result = Doctor.run(Seq("--env"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Git repository"))
    assert(!env.console.stdout.contains("Quality gate"))
  }

  test("--fix with all checks passing: 'Nothing to fix'") {
    val env = FakeCommandEnv()
    seedConfig(env)

    val result = Doctor.run(Seq("--fix"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Nothing to fix"))
  }

  test("--fix with errors but no FixAction installed: warning + exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val failingCheck = Check(
      "Failing quality check",
      _ => CheckResult.Error("Broken", "Fix me"),
      category = "Quality"
    )
    env.hooks.setDiscoveredChecks(List(failingCheck))

    val result = Doctor.run(Seq("--fix"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stdout.contains("No fix provider installed"))
  }

  test("--fix dispatches to first discovered FixAction") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val failingCheck = Check(
      "Failing quality check",
      _ => CheckResult.Error("Broken", "Fix me"),
      category = "Quality"
    )
    var fixCalledWith: Option[DoctorFixContext] = None
    val fixAction = new FixAction:
      def fix(ctx: DoctorFixContext): Int =
        fixCalledWith = Some(ctx)
        0
    env.hooks.setDiscoveredChecks(List(failingCheck))
    env.hooks.setDiscoveredFixActions(List(fixAction))

    val result = Doctor.run(Seq("--fix"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(
      fixCalledWith.map(_.failedChecks),
      Some(List("Failing quality check"))
    )
  }

  test("--fix forwards non-zero exit code from FixAction") {
    val env = FakeCommandEnv()
    seedConfig(env)
    val failingCheck = Check(
      "Failing quality check",
      _ => CheckResult.Error("Broken", "Fix me"),
      category = "Quality"
    )
    val fixAction = new FixAction:
      def fix(ctx: DoctorFixContext): Int = 42
    env.hooks.setDiscoveredChecks(List(failingCheck))
    env.hooks.setDiscoveredFixActions(List(fixAction))

    val result = Doctor.run(Seq("--fix"), env)

    assertEquals(result.exitCode, 42)
  }
