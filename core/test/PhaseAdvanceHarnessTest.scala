// PURPOSE: Harness-style tests for PhaseAdvance.run against an in-VM FakeCommandEnv
// PURPOSE: Asserts stdout/stderr and final fake state for the scenarios the BATS suite covers

package iw.core.test

import iw.core.adapters.ProcessResult
import iw.core.commands.PhaseAdvance
import iw.core.model.GitRemote
import iw.core.test.fixtures.FakeCommandEnv

class PhaseAdvanceHarnessTest extends munit.FunSuite:

  private val cwd = os.root / "tmp" / "harness-repo"
  private val configPath = cwd / ".iw" / "config.conf"

  private val githubConfig =
    """project {
      |  name = testproject
      |}
      |tracker {
      |  type = github
      |  repository = "test-org/test-repo"
      |  teamPrefix = "TEST"
      |}
      |""".stripMargin

  private val gitlabConfig =
    """project {
      |  name = testproject
      |}
      |tracker {
      |  type = gitlab
      |  repository = "test-org/test-repo"
      |  teamPrefix = "TEST"
      |}
      |""".stripMargin

  private def envOnPhaseBranch(
      initialBranch: String = "TEST-100-phase-01",
      config: String = githubConfig,
      remote: Option[GitRemote] = Some(
        GitRemote("git@github.com:test-org/test-repo.git")
      )
  ): FakeCommandEnv =
    val env = FakeCommandEnv(cwd = cwd, initialBranch = initialBranch)
    env.fs.put(configPath, config)
    env.git.setRemoteUrl(remote)
    env.git.addExistingBranch("TEST-100")
    env

  test("happy path: PR merged, checkout feature branch, fetch+reset, JSON") {
    val env = envOnPhaseBranch()
    env.process.scriptResponse(
      Seq("gh", "pr", "list"),
      ProcessResult(
        0,
        """[{"url":"https://github.com/test/repo/pull/42"}]""",
        ""
      )
    )

    val result = PhaseAdvance.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    val json = ujson.read(env.console.stdout)
    assertEquals(json("issueId").str, "TEST-100")
    assertEquals(json("phaseNumber").str, "01")
    assertEquals(json("branch").str, "TEST-100")
    assertEquals(json("previousBranch").str, "TEST-100-phase-01")
    assertEquals(env.git.currentBranchName, "TEST-100")
    assertEquals(env.git.resetBranchList, List("TEST-100"))
  }

  test("from feature branch without --phase-number: exit 1") {
    val env = envOnPhaseBranch(initialBranch = "TEST-100")

    val result = PhaseAdvance.run(Seq("--issue-id", "TEST-100"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("phase-number") ||
        env.console.stderr.contains("phase number"),
      s"expected phase-number hint, got: ${env.console.stderr}"
    )
  }

  test("from feature branch with --phase-number 2: targets phase-02 branch") {
    val env = envOnPhaseBranch(initialBranch = "TEST-100")
    env.process.scriptResponse(
      Seq("gh", "pr", "list"),
      ProcessResult(0, """[{"url":"https://example.com/pr"}]""", "")
    )

    val result =
      PhaseAdvance.run(
        Seq("--phase-number", "2", "--issue-id", "TEST-100"),
        env
      )

    assertEquals(result.exitCode, 0)
    val ghCalls = env.process.invocationList.filter(_.headOption.contains("gh"))
    assert(
      ghCalls.exists(cmd => cmd.contains("TEST-100-phase-02")),
      s"expected gh call to query TEST-100-phase-02, got: $ghCalls"
    )
  }

  test("config missing: exit 1 with init hint") {
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "TEST-100-phase-01")

    val result = PhaseAdvance.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("config") ||
        env.console.stderr.contains("iw init"),
      s"expected config/init hint, got: ${env.console.stderr}"
    )
  }

  test("cli tool not installed: exit 1 with install URL") {
    val env = envOnPhaseBranch()
    env.process.markCommandMissing("gh")

    val result = PhaseAdvance.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("gh") &&
        env.console.stderr.contains("not installed"),
      s"expected 'gh' + 'not installed' in stderr, got: ${env.console.stderr}"
    )
  }

  test("PR still open: error mentions 'still open'") {
    val env = envOnPhaseBranch()
    env.process.scriptResponse(
      Seq(
        "gh",
        "pr",
        "list",
        "--head",
        "TEST-100-phase-01",
        "--state",
        "merged"
      ),
      ProcessResult(0, "[]", "")
    )
    env.process.scriptResponse(
      Seq("gh", "pr", "list", "--head", "TEST-100-phase-01", "--state", "open"),
      ProcessResult(0, """[{"url":"https://example.com/pr/9"}]""", "")
    )

    val result = PhaseAdvance.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("still open"),
      s"expected 'still open' in stderr, got: ${env.console.stderr}"
    )
  }

  test("PR not merged and no open PR: error mentions 'No merged PR'") {
    val env = envOnPhaseBranch()
    env.process.scriptResponse(
      Seq("gh", "pr", "list"),
      ProcessResult(0, "[]", "")
    )

    val result = PhaseAdvance.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("No merged PR"),
      s"expected 'No merged PR' in stderr, got: ${env.console.stderr}"
    )
  }

  test("review-state.json present: updates state and commits") {
    val env = envOnPhaseBranch()
    env.process.scriptResponse(
      Seq("gh", "pr", "list"),
      ProcessResult(0, """[{"url":"https://example.com/pr"}]""", "")
    )
    val reviewStatePath =
      cwd / "project-management" / "issues" / "TEST-100" / "review-state.json"
    env.fs.put(
      reviewStatePath,
      """{
        |  "version": 2,
        |  "issue_id": "TEST-100",
        |  "status": "awaiting_review",
        |  "artifacts": [],
        |  "last_updated": "2026-01-01T12:00:00Z"
        |}""".stripMargin
    )

    val result = PhaseAdvance.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    val updated = env.fs.get(reviewStatePath).getOrElse("")
    assert(
      updated.contains("phase_merged"),
      s"expected phase_merged in review-state, got: $updated"
    )
    assertEquals(
      env.git.committedMessages,
      List("chore(TEST-100): update review-state for phase 01")
    )
  }

  test("review-state.json absent: no commit, just print JSON") {
    val env = envOnPhaseBranch()
    env.process.scriptResponse(
      Seq("gh", "pr", "list"),
      ProcessResult(0, """[{"url":"https://example.com/pr"}]""", "")
    )

    val result = PhaseAdvance.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.git.committedMessages, Nil)
  }

  test("gitlab forge: queries glab instead of gh") {
    val env = envOnPhaseBranch(
      config = gitlabConfig,
      remote = Some(GitRemote("git@gitlab.com:test-org/test-repo.git"))
    )
    env.process.scriptResponse(
      Seq("glab", "mr", "list"),
      ProcessResult(
        0,
        "https://gitlab.com/test-org/test-repo/-/merge_requests/1",
        ""
      )
    )

    val result = PhaseAdvance.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    val glabCalls =
      env.process.invocationList.filter(_.headOption.contains("glab"))
    assert(
      glabCalls.nonEmpty,
      s"expected glab to be called, got: ${env.process.invocationList}"
    )
  }
