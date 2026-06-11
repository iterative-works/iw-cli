// PURPOSE: Harness tests for Config.run

package iw.core.test

import iw.core.commands.Config as ConfigCmd
import iw.core.model.{IssueTrackerType, ProjectConfiguration}
import iw.core.test.fixtures.FakeCommandEnv

class ConfigHarnessTest extends munit.FunSuite:

  private def configPath(env: FakeCommandEnv): os.Path =
    env.cwd / ".iw" / "config.conf"

  private def linearCfg: ProjectConfiguration =
    ProjectConfiguration.create(
      trackerType = IssueTrackerType.Linear,
      team = "TEAM",
      projectName = "myproject"
    )

  private def githubCfg: ProjectConfiguration =
    ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitHub,
      team = "",
      projectName = "myproject",
      repository = Some("owner/myproject"),
      teamPrefix = Some("MYP")
    )

  test("no args: prints usage with exit 1") {
    val env = FakeCommandEnv()

    val result = ConfigCmd.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stdout.contains("iw config"))
    assert(env.console.stdout.contains("Usage:"))
  }

  test("get without field: exit 1 with missing-arg + usage") {
    val env = FakeCommandEnv()

    val result = ConfigCmd.run(Seq("get"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Missing required argument"))
    assert(env.console.stdout.contains("iw config"))
  }

  test("get with missing config file: exit 1") {
    val env = FakeCommandEnv()

    val result = ConfigCmd.run(Seq("get", "team"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Configuration not found"))
  }

  test("get nested field via dot-notation: prints value") {
    val env = FakeCommandEnv()
    env.config.put(configPath(env), linearCfg)

    val result = ConfigCmd.run(Seq("get", "tracker.team"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("TEAM"))
  }

  test("get backward-compat alias 'team': resolves to tracker.team") {
    val env = FakeCommandEnv()
    env.config.put(configPath(env), linearCfg)

    val result = ConfigCmd.run(Seq("get", "team"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("TEAM"))
  }

  test("get alias 'projectName': resolves to project.name") {
    val env = FakeCommandEnv()
    env.config.put(configPath(env), linearCfg)

    val result = ConfigCmd.run(Seq("get", "projectName"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("myproject"))
  }

  test("get unset optional field: exit 1 with 'not set'") {
    val env = FakeCommandEnv()
    env.config.put(configPath(env), linearCfg) // no repository

    val result = ConfigCmd.run(Seq("get", "tracker.repository"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("not set"))
  }

  test("get unknown field: exit 1 with 'Unknown configuration field'") {
    val env = FakeCommandEnv()
    env.config.put(configPath(env), linearCfg)

    val result = ConfigCmd.run(Seq("get", "tracker.totallybogus"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Unknown configuration field"))
  }

  test("get repository on github config: prints owner/repo") {
    val env = FakeCommandEnv()
    env.config.put(configPath(env), githubCfg)

    val result = ConfigCmd.run(Seq("get", "tracker.repository"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("owner/myproject"))
  }

  test("--json: prints full config as JSON") {
    val env = FakeCommandEnv()
    env.config.put(configPath(env), linearCfg)

    val result = ConfigCmd.run(Seq("--json"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Linear"))
    assert(env.console.stdout.contains("TEAM"))
    assert(env.console.stdout.contains("myproject"))
  }

  test("--json with missing config: exit 1") {
    val env = FakeCommandEnv()

    val result = ConfigCmd.run(Seq("--json"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Configuration not found"))
  }

  test("unknown option: exit 1 with 'Unknown option'") {
    val env = FakeCommandEnv()

    val result = ConfigCmd.run(Seq("--bogus"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Unknown option"))
    assert(env.console.stdout.contains("Usage:"))
  }

  test("unknown subcommand: exit 1 with 'Unknown subcommand'") {
    val env = FakeCommandEnv()

    val result = ConfigCmd.run(Seq("frobnicate"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Unknown subcommand"))
  }
