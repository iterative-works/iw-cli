// PURPOSE: Harness tests for ReviewState.run covering validate/write/update subcommands

package iw.core.test

import iw.core.commands.ReviewState
import iw.core.test.fixtures.FakeCommandEnv

class ReviewStateHarnessTest extends munit.FunSuite:

  private val cwd = os.root / "tmp" / "harness-repo"

  private val validJson =
    """{
      |  "version": 2,
      |  "issue_id": "TEST-100",
      |  "status": "implementing",
      |  "artifacts": [],
      |  "last_updated": "2026-01-01T12:00:00Z"
      |}""".stripMargin

  test("validate: valid file on disk → exit 0") {
    val env = FakeCommandEnv(cwd = cwd)
    val path = cwd / "review-state.json"
    env.fs.put(path, validJson)

    val result = ReviewState.run(Seq("validate", path.toString), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Review state is valid"))
  }

  test("validate: non-existent file → exit 1") {
    val env = FakeCommandEnv(cwd = cwd)

    val result =
      ReviewState.run(Seq("validate", "/nonexistent/review-state.json"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("File not found"))
  }

  test("validate --stdin: reads payload from stdin fake") {
    val env = FakeCommandEnv(cwd = cwd)
    env.stdin.setInput(validJson)

    val result = ReviewState.run(Seq("validate", "--stdin"), env)

    assertEquals(result.exitCode, 0)
  }

  test("write --display-text --output: file round-trips through validator") {
    val env =
      FakeCommandEnv(
        cwd = cwd,
        initialBranch = "TEST-100",
        initialHeadSha = "deadbeefcafebabe1234567890"
      )
    val outputPath = cwd / "out.json"

    val result = ReviewState.run(
      Seq(
        "write",
        "--display-text",
        "Implementing",
        "--display-type",
        "progress",
        "--output",
        outputPath.toString
      ),
      env
    )

    assertEquals(result.exitCode, 0)
    val written = env.fs.get(outputPath).getOrElse("")
    val json = ujson.read(written)
    assertEquals(json("issue_id").str, "TEST-100")
    assertEquals(json("display")("text").str, "Implementing")
  }

  test("update: edits existing file") {
    val env =
      FakeCommandEnv(
        cwd = cwd,
        initialBranch = "TEST-100",
        initialHeadSha = "deadbeefcafe1234567890"
      )
    val statePath =
      cwd / "project-management" / "issues" / "TEST-100" / "review-state.json"
    env.fs.put(statePath, validJson)

    val result = ReviewState.run(
      Seq("update", "--display-text", "Done", "--display-type", "info"),
      env
    )

    assertEquals(result.exitCode, 0)
    val updated = env.fs.get(statePath).getOrElse("")
    assert(
      updated.contains("Done"),
      s"expected 'Done' in updated, got: $updated"
    )
  }

  test(
    "update --commit: invokes commitFileWithRetry with status-aware message"
  ) {
    val env =
      FakeCommandEnv(
        cwd = cwd,
        initialBranch = "TEST-100",
        initialHeadSha = "deadbeefcafe1234567890"
      )
    val statePath =
      cwd / "project-management" / "issues" / "TEST-100" / "review-state.json"
    env.fs.put(statePath, validJson)

    val result = ReviewState.run(
      Seq("update", "--status", "phase_merged", "--commit"),
      env
    )

    assertEquals(result.exitCode, 0)
    assertEquals(
      env.git.committedMessages,
      List("chore(TEST-100): update review-state to phase_merged")
    )
  }

  test("dispatcher --help: lists subcommands") {
    val env = FakeCommandEnv(cwd = cwd)

    val result = ReviewState.run(Seq("--help"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("validate"))
    assert(env.console.stdout.contains("write"))
    assert(env.console.stdout.contains("update"))
  }

  test("unknown subcommand: exit 1") {
    val env = FakeCommandEnv(cwd = cwd)

    val result = ReviewState.run(Seq("nonsense"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Unknown subcommand"))
  }
