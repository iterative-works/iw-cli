// PURPOSE: Harness-style tests for PhaseStart.run against an in-VM FakeCommandEnv
// PURPOSE: Asserts stdout/stderr and final fake state for the scenarios the BATS suite covers

package iw.core.test

import iw.core.commands.PhaseStart
import iw.core.test.fixtures.{
  FakeCommandEnv,
  FakeConsole,
  FakeFileSystem,
  FakeGit
}

class PhaseStartHarnessTest extends munit.FunSuite:

  private val cwd = os.root / "tmp" / "harness-repo"

  private def envWith(
      initialBranch: String = "TEST-100",
      initialHeadSha: String = "abc1234"
  ): FakeCommandEnv =
    FakeCommandEnv(
      cwd = cwd,
      initialBranch = initialBranch,
      initialHeadSha = initialHeadSha
    )

  test("happy path: produces JSON output with all fields") {
    val env = envWith()

    val result = PhaseStart.run(Seq("1"), env)

    assertEquals(result.exitCode, 0)
    val expected =
      """{
        |  "issueId": "TEST-100",
        |  "phaseNumber": "01",
        |  "branch": "TEST-100-phase-01",
        |  "baselineSha": "abc1234"
        |}""".stripMargin
    assertEquals(env.console.stdout, expected)
    assertEquals(env.console.stderr, "")
  }

  test("happy path: switches current branch to the phase sub-branch") {
    val env = envWith()

    PhaseStart.run(Seq("1"), env)

    assertEquals(env.git.currentBranchName, "TEST-100-phase-01")
  }

  test(
    "happy path: pushes feature branch to origin before creating sub-branch"
  ) {
    val env = envWith()

    PhaseStart.run(Seq("1"), env)

    assert(env.git.wasPushed("TEST-100"), "feature branch should be pushed")
  }

  test("missing phase number argument: usage message + exit 1") {
    val env = envWith()

    val result = PhaseStart.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    val expected =
      """Error: Missing phase-number argument
        |Error: Usage: iw phase-start <phase-number> [--issue-id ID]""".stripMargin
    assertEquals(env.console.stderr, expected)
    assertEquals(env.console.stdout, "")
  }

  test("invalid phase number 'abc': error mentions Invalid phase number") {
    val env = envWith()

    val result = PhaseStart.run(Seq("abc"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("Invalid phase number"),
      s"expected 'Invalid phase number' in stderr, got: ${env.console.stderr}"
    )
  }

  test("already on a phase sub-branch: error + exit 1") {
    val env = envWith(initialBranch = "TEST-100-phase-01")

    val result = PhaseStart.run(Seq("2"), env)

    assertEquals(result.exitCode, 1)
    val expected =
      "Error: Already on a phase sub-branch 'TEST-100-phase-01'. Use 'iw phase-commit' to commit your work."
    assertEquals(env.console.stderr, expected)
  }

  test("create-branch conflict (branch already exists): error + exit 1") {
    val env = envWith()
    env.git.addExistingBranch("TEST-100-phase-01")

    val result = PhaseStart.run(Seq("1"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("Failed to create branch") &&
        env.console.stderr.contains("TEST-100-phase-01"),
      s"expected 'Failed to create branch' for TEST-100-phase-01, got: ${env.console.stderr}"
    )
    assertEquals(
      env.git.currentBranchName,
      "TEST-100",
      "current branch must stay on feature branch when sub-branch creation fails"
    )
  }

  test("--issue-id override: JSON output uses the provided issue id") {
    val env = envWith()

    val result =
      PhaseStart.run(Seq("2", "--issue-id", "TEST-999"), env)

    assertEquals(result.exitCode, 0)
    val expected =
      """{
        |  "issueId": "TEST-999",
        |  "phaseNumber": "02",
        |  "branch": "TEST-100-phase-02",
        |  "baselineSha": "abc1234"
        |}""".stripMargin
    assertEquals(env.console.stdout, expected)
  }

  test(
    "review-state.json present: command updates state and commits the file"
  ) {
    val env = envWith()
    val reviewStatePath =
      cwd / "project-management" / "issues" / "TEST-100" / "review-state.json"
    env.fs.put(
      reviewStatePath,
      """{
        |  "version": 2,
        |  "issue_id": "TEST-100",
        |  "status": "tasks_ready",
        |  "artifacts": [],
        |  "last_updated": "2026-01-01T12:00:00Z"
        |}""".stripMargin
    )

    val result = PhaseStart.run(Seq("1"), env)

    assertEquals(result.exitCode, 0)
    val updated = env.fs.get(reviewStatePath).getOrElse("")
    assert(
      updated.contains("\"implementing\""),
      s"review-state status should be 'implementing' after run, got: $updated"
    )
    assertEquals(
      env.git.committedMessages,
      List("chore(TEST-100): update review-state for phase 01")
    )
  }

  test(
    "review-state.json absent: command skips review-state update + commit"
  ) {
    val env = envWith()

    val result = PhaseStart.run(Seq("1"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(
      env.git.committedMessages,
      Nil,
      "no commits should be made when review-state.json is absent"
    )
  }

  test("push failure prevents sub-branch creation") {
    val env = envWith()
    env.git.pushResult = Left("Failed to push branch 'TEST-100': no remote")

    val result = PhaseStart.run(Seq("1"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("Failed to push branch"),
      s"expected 'Failed to push branch' in stderr, got: ${env.console.stderr}"
    )
    assertEquals(
      env.git.currentBranchName,
      "TEST-100",
      "current branch must stay on feature branch when push fails"
    )
  }
