// PURPOSE: Harness-style tests for PhaseMerge.run against an in-VM FakeCommandEnv
// PURPOSE: Covers CI polling, recovery hook invocation, merge + advance, and the previously failing
// PURPOSE: 'no recovery hook' branch from the old BATS suite

package iw.core.test

import iw.core.commands.PhaseMerge
import iw.core.model.{
  CICheckResult,
  CICheckStatus,
  ForgeType,
  GitRemote,
  RecoveryAction,
  RecoveryContext
}
import iw.core.test.fixtures.FakeCommandEnv

class PhaseMergeHarnessTest extends munit.FunSuite:

  private val cwd = os.root / "tmp" / "harness-repo"
  private val configPath = cwd / ".iw" / "config.conf"
  private val reviewStatePath =
    cwd / "project-management" / "issues" / "TEST-100" / "review-state.json"
  private val prUrl = "https://github.com/test-org/test-repo/pull/42"

  private val githubConfig =
    """project { name = testproject }
      |tracker {
      |  type = github
      |  repository = "test-org/test-repo"
      |  teamPrefix = "TEST"
      |}
      |""".stripMargin

  private val gitlabConfig =
    """project { name = testproject }
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
      ),
      prUrlOverride: String = prUrl
  ): FakeCommandEnv =
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "TEST-100-phase-01")
    env.fs.put(configPath, config)
    env.fs.put(
      reviewStatePath,
      s"""{
         |  "version": 2,
         |  "issue_id": "TEST-100",
         |  "status": "awaiting_review",
         |  "pr_url": "$prUrlOverride",
         |  "artifacts": [],
         |  "last_updated": "2026-01-01T12:00:00Z"
         |}""".stripMargin
    )
    env.git.setRemoteUrl(remote)
    env.git.addExistingBranch("TEST-100")
    env

  private val passingCheck = CICheckResult("build", CICheckStatus.Passed)
  private val pendingCheck = CICheckResult("build", CICheckStatus.Pending)
  private val failingCheck =
    CICheckResult("lint", CICheckStatus.Failed, Some("https://ci.example/1"))

  test("happy path: passing checks → merges, advances feature branch, JSON") {
    val env = envOnPhaseBranch()
    env.tracker.setCheckStatuses(Right(List(passingCheck)))

    val result = PhaseMerge.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    val jsonLine = env.console.stdoutLines.last
    val json = ujson.read(jsonLine.dropWhile(_ != '{'))
    assertEquals(json("issueId").str, "TEST-100")
    assertEquals(json("phaseNumber").str, "01")
    assertEquals(json("prUrl").str, prUrl)
    assertEquals(json("featureBranch").str, "TEST-100")
    assertEquals(env.tracker.mergeWithDeleteCallList.size, 1)
    assertEquals(env.git.currentBranchName, "TEST-100")
    assertEquals(env.git.resetBranchList, List("TEST-100"))
    val updatedState = env.fs.get(reviewStatePath).getOrElse("")
    assert(updatedState.contains("phase_merged"))
  }

  test("not on phase branch: exit 1") {
    val env = envOnPhaseBranch()
    env.git.setCurrentBranch("TEST-100")

    val result = PhaseMerge.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("phase sub-branch"))
  }

  test("config missing: exit 1 with init hint") {
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "TEST-100-phase-01")
    env.git.setRemoteUrl(Some(GitRemote("git@github.com:t/t.git")))

    val result = PhaseMerge.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("config") ||
        env.console.stderr.contains("iw init")
    )
  }

  test("review-state.json missing: exit 1") {
    val env = FakeCommandEnv(cwd = cwd, initialBranch = "TEST-100-phase-01")
    env.fs.put(configPath, githubConfig)
    env.git.setRemoteUrl(
      Some(GitRemote("git@github.com:test-org/test-repo.git"))
    )
    env.git.addExistingBranch("TEST-100")

    val result = PhaseMerge.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("review-state") ||
        env.console.stderr.contains("pr_url"),
      s"expected review-state hint, got: ${env.console.stderr}"
    )
  }

  test("review-state.json missing pr_url: exit 1") {
    val env = envOnPhaseBranch()
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

    val result = PhaseMerge.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("pr_url"))
  }

  test("pr_url repository mismatch: exit 1") {
    val env =
      envOnPhaseBranch(prUrlOverride = "https://github.com/other/proj/pull/1")

    val result = PhaseMerge.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("does not match"))
  }

  test("pending then passing: polls, sleeps poll interval, then merges") {
    val env = envOnPhaseBranch()
    env.tracker.queueCheckStatuses(
      Right(List(pendingCheck)),
      Right(List(passingCheck))
    )

    val result =
      PhaseMerge.run(Seq("--poll-interval", "5s", "--timeout", "1m"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tracker.checkStatusCallCount, 2)
    assertEquals(env.clock.sleepCalls, List(5_000L))
    assertEquals(env.tracker.mergeWithDeleteCallList.size, 1)
  }

  test("timeout exceeded: exit 1, activity=waiting") {
    val env = envOnPhaseBranch()
    env.tracker.setCheckStatuses(Right(List(pendingCheck)))
    env.clock.queueNowSequence(0L, 10_000_000L)

    val result =
      PhaseMerge.run(Seq("--timeout", "30s", "--poll-interval", "5s"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Timed out"))
    val updated = env.fs.get(reviewStatePath).getOrElse("")
    assert(updated.contains("waiting"))
  }

  test(
    "failing CI without recovery hook: exit 1, 'No recovery action hook' warning, activity=waiting"
  ) {
    val env = envOnPhaseBranch()
    env.tracker.setCheckStatuses(Right(List(failingCheck)))
    // env.hooks.setRecoveryActions(Nil) is the default

    val result = PhaseMerge.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stdout.contains("No recovery action hook") ||
        env.console.stderr.contains("No recovery action hook"),
      s"expected 'No recovery action hook' message, got stdout=${env.console.stdout} stderr=${env.console.stderr}"
    )
    val updated = env.fs.get(reviewStatePath).getOrElse("")
    assert(updated.contains("waiting"))
  }

  test("--max-retries 0 with hook installed and failure: exit 1 immediately") {
    val env = envOnPhaseBranch()
    env.tracker.setCheckStatuses(Right(List(failingCheck)))
    val invocations =
      scala.collection.mutable.ArrayBuffer.empty[RecoveryContext]
    env.hooks.setRecoveryActions(
      List(
        new RecoveryAction:
          def recover(ctx: RecoveryContext): Int =
            invocations += ctx
            0
      )
    )

    val result = PhaseMerge.run(Seq("--max-retries", "0"), env)

    assertEquals(result.exitCode, 1)
    assertEquals(invocations.size, 0, "recovery should not be invoked at max=0")
    assert(env.console.stderr.contains("still failing"))
  }

  test(
    "failing then passing after recovery: invokes recovery, retries, merges"
  ) {
    val env = envOnPhaseBranch()
    env.tracker.queueCheckStatuses(
      Right(List(failingCheck)),
      Right(List(passingCheck))
    )
    val invocations =
      scala.collection.mutable.ArrayBuffer.empty[RecoveryContext]
    env.hooks.setRecoveryActions(
      List(
        new RecoveryAction:
          def recover(ctx: RecoveryContext): Int =
            invocations += ctx
            0
      )
    )

    val result = PhaseMerge.run(Seq("--max-retries", "2"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(invocations.size, 1)
    assertEquals(env.tracker.mergeWithDeleteCallList.size, 1)
  }

  test("--timeout with invalid duration: exit 1 with parse error") {
    val env = envOnPhaseBranch()

    val result = PhaseMerge.run(Seq("--timeout", "bogus"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Invalid"))
  }

  test("--max-retries negative: exit 1 with parse error") {
    val env = envOnPhaseBranch()

    val result = PhaseMerge.run(Seq("--max-retries", "-1"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("not be negative"))
  }

  test("GitLab forge: passes gitlabHost and uses ForgeType.GitLab") {
    val env = envOnPhaseBranch(
      config = gitlabConfig,
      remote = Some(GitRemote("git@gitlab.example.com:test-org/test-repo.git")),
      prUrlOverride =
        "https://gitlab.example.com/test-org/test-repo/-/merge_requests/1"
    )
    env.tracker.setCheckStatuses(Right(List(passingCheck)))

    val result = PhaseMerge.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    val mergeCall = env.tracker.mergeWithDeleteCallList.head
    assertEquals(mergeCall.forge, ForgeType.GitLab)
    assertEquals(mergeCall.gitlabHost, Some("gitlab.example.com"))
  }
