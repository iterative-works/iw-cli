// PURPOSE: Harness-style tests for PhasePr.run against an in-VM FakeCommandEnv
// PURPOSE: Asserts stdout/stderr and final fake state for the scenarios the BATS suite covers

package iw.core.test

import iw.core.commands.PhasePr
import iw.core.model.{ForgeConfig, ForgeType, GitRemote}
import iw.core.test.fixtures.FakeCommandEnv

class PhasePrHarnessTest extends munit.FunSuite:

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
      config: String = githubConfig,
      remote: Option[GitRemote] = Some(
        GitRemote("git@github.com:test-org/test-repo.git")
      )
  ): FakeCommandEnv =
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "TEST-100-phase-01")
    env.fs.put(configPath, config)
    env.git.setRemoteUrl(remote)
    env.git.addExistingBranch("TEST-100")
    env

  test("happy path: pushes phase branch, creates PR, prints JSON") {
    val env = envOnPhaseBranch()
    env.tracker.setCreatePrResult(Right("https://github.com/x/y/pull/42"))

    val result = PhasePr.run(Seq("--title", "Phase 1 implementation"), env)

    assertEquals(result.exitCode, 0)
    val json = ujson.read(env.console.stdout)
    assertEquals(json("prUrl").str, "https://github.com/x/y/pull/42")
    assertEquals(json("headBranch").str, "TEST-100-phase-01")
    assertEquals(json("baseBranch").str, "TEST-100")
    assertEquals(json("merged").bool, false)

    assert(env.git.wasPushed("TEST-100-phase-01"))
    val calls = env.tracker.prCallList
    assertEquals(calls.size, 1)
    assertEquals(calls.head.forge, ForgeType.GitHub)
    assertEquals(calls.head.repository, "test-org/test-repo")
    assertEquals(calls.head.title, "Phase 1 implementation")
  }

  test("missing --title: exit 1 + usage") {
    val env = envOnPhaseBranch()

    val result = PhasePr.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("--title"),
      s"expected --title in stderr, got: ${env.console.stderr}"
    )
  }

  test("not on phase branch: exit 1") {
    val env = envOnPhaseBranch()
    env.git.setCurrentBranch("TEST-100")

    val result = PhasePr.run(Seq("--title", "Test"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("phase sub-branch") ||
        env.console.stderr.contains("phase-start"),
      s"expected phase-branch hint, got: ${env.console.stderr}"
    )
  }

  test("config missing: exit 1 with init hint") {
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "TEST-100-phase-01")
    env.git.setRemoteUrl(Some(GitRemote("git@github.com:test/test.git")))

    val result = PhasePr.run(Seq("--title", "Test"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("config") ||
        env.console.stderr.contains("iw init"),
      s"expected config hint, got: ${env.console.stderr}"
    )
  }

  test("default body mentions phase and issue id when no --body") {
    val env = envOnPhaseBranch()

    PhasePr.run(Seq("--title", "Title"), env)

    val body = env.tracker.prCallList.head.body
    assert(
      body.contains("Phase 01"),
      s"expected 'Phase 01' in body, got: $body"
    )
    assert(
      body.contains("TEST-100"),
      s"expected 'TEST-100' in body, got: $body"
    )
  }

  test("--body override: uses provided body verbatim") {
    val env = envOnPhaseBranch()

    PhasePr.run(Seq("--title", "T", "--body", "Custom body"), env)

    assertEquals(env.tracker.prCallList.head.body, "Custom body")
  }

  test(
    "review-state.json present (non-batch): updates state to awaiting_review and commits"
  ) {
    val env = envOnPhaseBranch()
    val reviewStatePath =
      cwd / "project-management" / "issues" / "TEST-100" / "review-state.json"
    env.fs.put(
      reviewStatePath,
      """{
        |  "version": 2,
        |  "issue_id": "TEST-100",
        |  "status": "implementing",
        |  "artifacts": [],
        |  "last_updated": "2026-01-01T12:00:00Z"
        |}""".stripMargin
    )

    PhasePr.run(Seq("--title", "Test"), env)

    val updated = env.fs.get(reviewStatePath).getOrElse("")
    assert(
      updated.contains("awaiting_review"),
      s"expected awaiting_review in review-state, got: $updated"
    )
    assertEquals(env.git.committedMessages.size, 1)
  }

  test("--batch mode: merges PR via tracker and reports merged=true") {
    val env = envOnPhaseBranch()
    env.tracker.setCreatePrResult(Right("https://example.com/pr/1"))

    val result = PhasePr.run(Seq("--title", "T", "--batch"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(ujson.read(env.console.stdout)("merged").bool, true)
    assertEquals(env.tracker.mergeCallList.size, 1)
    assertEquals(
      env.tracker.mergeCallList.head.prUrl,
      "https://example.com/pr/1"
    )
    assertEquals(env.git.resetBranchList, List("TEST-100"))
  }

  test("--batch mode merge failure: exit 1, PR URL stated") {
    val env = envOnPhaseBranch()
    env.tracker.setCreatePrResult(Right("https://example.com/pr/9"))
    env.tracker.setMergeResult(Left("conflicts"))

    val result = PhasePr.run(Seq("--title", "T", "--batch"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("https://example.com/pr/9"),
      s"expected PR URL in stderr, got: ${env.console.stderr}"
    )
  }

  test(
    "PR create failure: exit 1 with 'manually' hint and the failure reported"
  ) {
    val env = envOnPhaseBranch()
    env.tracker.setCreatePrResult(Left("gh: not authenticated"))

    val result = PhasePr.run(Seq("--title", "T"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("manually"),
      s"expected 'manually' in stderr, got: ${env.console.stderr}"
    )
    assert(
      env.console.stderr.contains("not authenticated"),
      s"expected error detail in stderr, got: ${env.console.stderr}"
    )
  }

  test("gitlab forge: routes through GitLab path, passes gitlabHost") {
    val env = envOnPhaseBranch(
      config = gitlabConfig,
      remote = Some(GitRemote("git@gitlab.example.com:test/test.git"))
    )
    env.tracker.setCreatePrResult(
      Right("https://gitlab.com/x/y/-/merge_requests/1")
    )

    val result = PhasePr.run(Seq("--title", "Test"), env)

    assertEquals(result.exitCode, 0)
    val call = env.tracker.prCallList.head
    assertEquals(call.forge, ForgeType.GitLab)
    assertEquals(call.gitlabHost, Some("gitlab.example.com"))
  }

  // =========================================================================
  // Forgejo forge tests
  // =========================================================================

  private val forgejoConfig =
    """project {
      |  name = testproject
      |}
      |tracker {
      |  type = forgejo
      |  repository = "test-org/test-repo"
      |  teamPrefix = "TEST"
      |  baseUrl = "https://codeberg.org"
      |}
      |""".stripMargin

  private def forgejoEnv(
      withToken: Boolean = true
  ): FakeCommandEnv =
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "TEST-100-phase-01")
    env.fs.put(configPath, forgejoConfig)
    env.git.setRemoteUrl(None)
    env.git.addExistingBranch("TEST-100")
    if withToken then
      env.envVars.setEnv("FORGEJO_API_TOKEN", "test-forgejo-token")
    env

  test(
    "forgejo forge: routes through Forgejo path with resolved baseUrl and token"
  ) {
    val env = forgejoEnv()
    env.tracker.setCreatePrResult(
      Right("https://codeberg.org/test-org/test-repo/pulls/5")
    )

    val result = PhasePr.run(Seq("--title", "Forgejo PR"), env)

    assertEquals(result.exitCode, 0)
    val calls = env.tracker.prCallList
    assertEquals(calls.size, 1)
    val call = calls.head
    assertEquals(call.forge, ForgeType.Forgejo)
    assertEquals(call.repository, "test-org/test-repo")
    assertEquals(
      call.forgeConfig.baseUrl,
      Some("https://codeberg.org")
    )
    assert(
      call.forgeConfig.token.isDefined,
      "Expected token to be resolved from env"
    )
    val json = ujson.read(env.console.stdout)
    assertEquals(
      json("prUrl").str,
      "https://codeberg.org/test-org/test-repo/pulls/5"
    )
  }

  test(
    "forgejo forge: missing FORGEJO_API_TOKEN exits with error before any forge call"
  ) {
    val env = forgejoEnv(withToken = false)

    val result = PhasePr.run(Seq("--title", "Forgejo PR"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("FORGEJO_API_TOKEN"),
      s"Expected FORGEJO_API_TOKEN in stderr, got: ${env.console.stderr}"
    )
    assertEquals(
      env.tracker.prCallList.size,
      0,
      "No forge call should be made when token is missing"
    )
  }

  test(
    "forgejo forge: missing baseUrl exits with error before any forge call"
  ) {
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "TEST-100-phase-01")
    val configWithoutBaseUrl =
      """project { name = testproject }
        |tracker {
        |  type = forgejo
        |  repository = "test-org/test-repo"
        |  teamPrefix = "TEST"
        |}
        |""".stripMargin
    env.fs.put(configPath, configWithoutBaseUrl)
    env.git.setRemoteUrl(None)
    env.git.addExistingBranch("TEST-100")
    env.envVars.setEnv("FORGEJO_API_TOKEN", "test-token")

    val result = PhasePr.run(Seq("--title", "Forgejo PR"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("baseUrl") ||
        env.console.stderr.contains("base URL"),
      s"Expected baseUrl error in stderr, got: ${env.console.stderr}"
    )
    assertEquals(
      env.tracker.prCallList.size,
      0,
      "No forge call should be made when baseUrl is missing"
    )
  }
