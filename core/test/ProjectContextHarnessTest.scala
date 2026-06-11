// PURPOSE: Harness tests for ProjectContext.run against an in-VM FakeCommandEnv

package iw.core.test

import iw.core.commands.ProjectContext
import iw.core.model.GitRemote
import iw.core.test.fixtures.FakeCommandEnv

class ProjectContextHarnessTest extends munit.FunSuite:

  private val cwd = os.root / "tmp" / "harness-repo"
  private val configPath = cwd / ".iw" / "config.conf"

  test("GitHub config: outputs project + repository + GitHub forge section") {
    val env = FakeCommandEnv(cwd = cwd)
    env.fs.put(
      configPath,
      """tracker {
        |  type = github
        |  repository = "iterative-works/iw-cli"
        |  teamPrefix = "IW"
        |}
        |project { name = iw-cli }
        |""".stripMargin
    )
    env.git.setRemoteUrl(
      Some(GitRemote("git@github.com:iterative-works/iw-cli.git"))
    )

    val result = ProjectContext.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    val out = env.console.stdout
    assert(out.contains("**Project:** iw-cli"))
    assert(out.contains("**Repository:** iterative-works/iw-cli"))
    assert(out.contains("**Tracker:** GitHub"))
    assert(out.contains("Forge: GitHub"))
  }

  test("GitLab config: outputs GitLab forge section") {
    val env = FakeCommandEnv(cwd = cwd)
    env.fs.put(
      configPath,
      """tracker {
        |  type = gitlab
        |  repository = "myorg/myproj"
        |  teamPrefix = "PROJ"
        |}
        |project { name = myproj }
        |""".stripMargin
    )
    env.git.setRemoteUrl(
      Some(GitRemote("git@gitlab.example.com:myorg/myproj.git"))
    )

    val result = ProjectContext.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Forge: GitLab"))
  }

  test("missing config: exit 1 with init hint") {
    val env = FakeCommandEnv(cwd = cwd)

    val result = ProjectContext.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Configuration file not found"))
  }
