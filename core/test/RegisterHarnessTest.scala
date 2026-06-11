// PURPOSE: Harness tests for Register.run

package iw.core.test

import iw.core.commands.Register
import iw.core.test.fixtures.FakeCommandEnv

class RegisterHarnessTest extends munit.FunSuite:

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

  private val githubConfig =
    """project {
      |  name = testproject
      |}
      |
      |tracker {
      |  type = github
      |  repository = "owner/repo"
      |  teamPrefix = "TR"
      |}
      |""".stripMargin

  private def seedConfig(
      env: FakeCommandEnv,
      content: String = linearConfig
  ): Unit =
    env.fs.put(env.cwd / ".iw" / "config.conf", content)

  test("config missing: exit 1 with init hint") {
    val env = FakeCommandEnv(initialBranch = "main")

    val result = Register.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Cannot read configuration"))
    assert(env.console.stdout.contains("./iw init"))
  }

  test("non-issue branch: registers project, exit 0") {
    val env = FakeCommandEnv(initialBranch = "main")
    seedConfig(env)

    val result = Register.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.server.registerProjectCallList.size, 1)
    val call = env.server.registerProjectCallList.head
    assertEquals(call.projectName, "testproject")
    assertEquals(call.team, "TEST")
    assert(env.console.stdout.contains("Registered project"))
  }

  test(
    "non-issue branch with github config: uses repository as team identifier"
  ) {
    val env = FakeCommandEnv(initialBranch = "feature-xyz")
    seedConfig(env, githubConfig)

    val result = Register.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    val call = env.server.registerProjectCallList.head
    assertEquals(call.team, "owner/repo")
  }

  test("issue branch: registers worktree AND parent project") {
    val cwd = os.root / "home" / "user" / "testproject-IWLE-123"
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "IWLE-123")
    seedConfig(env)
    // also seed parent project config
    env.fs.put(
      os.root / "home" / "user" / "testproject" / ".iw" / "config.conf",
      linearConfig
    )

    val result = Register.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.server.registerWorktreeCallList.size, 1)
    val wt = env.server.registerWorktreeCallList.head
    assertEquals(wt.issueId, "IWLE-123")
    assertEquals(wt.team, "IWLE")
    assert(env.console.stdout.contains("Registered worktree"))
    assertEquals(env.server.registerProjectCallList.size, 1)
    assertEquals(
      env.server.registerProjectCallList.head.path,
      "/home/user/testproject"
    )
  }

  test("issue branch with no parent config: warns but exits 0") {
    val cwd = os.root / "home" / "user" / "testproject-IWLE-999"
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "IWLE-999")
    seedConfig(env)
    // no parent config seeded

    val result = Register.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.server.registerWorktreeCallList.size, 1)
    assertEquals(env.server.registerProjectCallList, Nil)
    assert(env.console.stdout.contains("Could not read parent project config"))
  }

  test("issue branch + worktree register fails: exit 1") {
    val cwd = os.root / "home" / "user" / "testproject-IWLE-1"
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "IWLE-1")
    seedConfig(env)
    env.server.setRegisterWorktreeResult(Left("server down"))

    val result = Register.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Failed to register worktree"))
  }

  test("non-issue branch + project register fails: exit 1") {
    val env = FakeCommandEnv(initialBranch = "main")
    seedConfig(env)
    env.server.setRegisterProjectResult(Left("server down"))

    val result = Register.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Failed to register project"))
  }
