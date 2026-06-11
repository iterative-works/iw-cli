// PURPOSE: Harness-style tests for PhaseCommit.run against an in-VM FakeCommandEnv
// PURPOSE: Asserts stdout/stderr and final fake state for the scenarios the BATS suite covers

package iw.core.test

import iw.core.commands.PhaseCommit
import iw.core.model.StagingCheck
import iw.core.test.fixtures.FakeCommandEnv

class PhaseCommitHarnessTest extends munit.FunSuite:

  private val cwd = os.root / "tmp" / "harness-repo"

  private def envOnPhaseBranch(
      initialBranch: String = "TEST-100-phase-01"
  ): FakeCommandEnv =
    val env = FakeCommandEnv(cwd = cwd, initialBranch = initialBranch)
    env.git.setStagingCheck(StagingCheck(List("src/main.scala"), Nil, Nil))
    env.git.setDiffFiles(List("src/main.scala"))
    env

  test("happy path: outputs valid JSON with all required fields") {
    val env = envOnPhaseBranch()

    val result = PhaseCommit.run(Seq("--title", "Test commit"), env)

    assertEquals(result.exitCode, 0)
    val json = ujson.read(env.console.stdout)
    assertEquals(json("issueId").str, "TEST-100")
    assertEquals(json("phaseNumber").str, "01")
    assertEquals(json("message").str, "Test commit")
    assertEquals(json("filesCommitted").num.toInt, 1)
    assert(json("commitSha").str.nonEmpty)
  }

  test("--items produces a multi-line commit message with bullets") {
    val env = envOnPhaseBranch()

    val result =
      PhaseCommit.run(
        Seq("--title", "Title", "--items", "item1,item2"),
        env
      )

    assertEquals(result.exitCode, 0)
    val message = ujson.read(env.console.stdout)("message").str
    assert(message.contains("- item1"), s"expected '- item1' in: $message")
    assert(message.contains("- item2"), s"expected '- item2' in: $message")
    assertEquals(env.git.committedMessages, List(message))
  }

  test("missing --title: usage message + exit 1") {
    val env = envOnPhaseBranch()

    val result = PhaseCommit.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("--title"),
      s"expected '--title' in stderr, got: ${env.console.stderr}"
    )
  }

  test("not on a phase sub-branch: exit 1 with explanatory message") {
    val env = envOnPhaseBranch(initialBranch = "TEST-100")

    val result = PhaseCommit.run(Seq("--title", "Test"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("phase sub-branch") ||
        env.console.stderr.contains("phase-start"),
      s"expected phase-branch hint in stderr, got: ${env.console.stderr}"
    )
  }

  test("no staged changes: exit 1 + error mentions No staged changes") {
    val env = envOnPhaseBranch()
    env.git.setStagingCheck(StagingCheck(Nil, Nil, Nil))

    val result = PhaseCommit.run(Seq("--title", "Test"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("No staged changes"),
      s"expected 'No staged changes' in stderr, got: ${env.console.stderr}"
    )
  }

  test("unstaged modifications: exit 1 + names dirty file") {
    val env = envOnPhaseBranch()
    env.git.setStagingCheck(
      StagingCheck(List("a.scala"), List("README.md"), Nil)
    )

    val result = PhaseCommit.run(Seq("--title", "Test"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("Unstaged modifications") &&
        env.console.stderr.contains("README.md"),
      s"expected unstaged hint with README.md, got: ${env.console.stderr}"
    )
  }

  test("untracked files: exit 1 + names untracked file") {
    val env = envOnPhaseBranch()
    env.git.setStagingCheck(
      StagingCheck(List("a.scala"), Nil, List("unrelated.txt"))
    )

    val result = PhaseCommit.run(Seq("--title", "Test"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("Untracked files") &&
        env.console.stderr.contains("unrelated.txt"),
      s"expected untracked hint with unrelated.txt, got: ${env.console.stderr}"
    )
  }

  test("unchecked impl tasks in task file: exit 1 before commit") {
    val env = envOnPhaseBranch()
    val taskPath =
      cwd / "project-management" / "issues" / "TEST-100" / "phase-01-tasks.md"
    env.fs.put(
      taskPath,
      """# Phase 1 Tasks
        |
        |- [x] [impl] Task one
        |- [ ] [impl] Task two
        |
        |**Phase Status:** Not Started
        |""".stripMargin
    )

    val result = PhaseCommit.run(Seq("--title", "Test"), env)

    assertEquals(result.exitCode, 1)
    assert(
      env.console.stderr.contains("unchecked tasks") ||
        env.console.stderr.contains("not marked as implemented"),
      s"expected unchecked-tasks message in stderr, got: ${env.console.stderr}"
    )
    assertEquals(
      env.git.committedMessages,
      Nil,
      "no commits should be made when unchecked tasks are present"
    )
  }

  test(
    "task file present and clean: marks Phase Status Complete, stages, commits"
  ) {
    val env = envOnPhaseBranch()
    val taskPath =
      cwd / "project-management" / "issues" / "TEST-100" / "phase-01-tasks.md"
    env.fs.put(
      taskPath,
      """# Phase 1 Tasks
        |
        |- [x] [impl] Task one
        |- [x] [test] Task two
        |
        |**Phase Status:** Not Started
        |""".stripMargin
    )

    val result = PhaseCommit.run(Seq("--title", "Test"), env)

    assertEquals(result.exitCode, 0)
    val updated = env.fs.get(taskPath).getOrElse("")
    assert(
      updated.contains("**Phase Status:** Complete"),
      s"expected Phase Status Complete, got: $updated"
    )
    assert(
      env.git.stagedPathList.contains(taskPath),
      s"task file should be staged, got: ${env.git.stagedPathList}"
    )
    assertEquals(env.git.committedMessages.size, 1)
  }

  test("--issue-id override: JSON uses provided issue id") {
    val env = envOnPhaseBranch()

    val result = PhaseCommit.run(
      Seq("--title", "Test", "--issue-id", "OTHER-999"),
      env
    )

    assertEquals(result.exitCode, 0)
    assertEquals(
      ujson.read(env.console.stdout)("issueId").str,
      "OTHER-999"
    )
  }

  test("--phase-number override: JSON uses provided phase number") {
    val env = envOnPhaseBranch()

    val result = PhaseCommit.run(
      Seq("--title", "Test", "--phase-number", "5"),
      env
    )

    assertEquals(result.exitCode, 0)
    assertEquals(
      ujson.read(env.console.stdout)("phaseNumber").str,
      "05"
    )
  }
