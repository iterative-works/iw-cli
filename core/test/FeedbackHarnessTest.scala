// PURPOSE: Harness tests for Feedback.run

package iw.core.test

import iw.core.adapters.CreatedIssue
import iw.core.commands.Feedback
import iw.core.model.{Constants, FeedbackParser}
import iw.core.test.fixtures.FakeCommandEnv

class FeedbackHarnessTest extends munit.FunSuite:

  test("missing title: exit 1 with error and usage hint") {
    val env = FakeCommandEnv()

    val result = Feedback.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Title is required"))
    assert(env.console.stdout.contains("iw feedback --help"))
    assertEquals(env.tracker.feedbackIssueCallList, Nil)
  }

  test("--help: exit 0 and prints usage banner") {
    val env = FakeCommandEnv()

    val result = Feedback.run(Seq("--help"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Submit feedback to the iw-cli team"))
    assert(env.console.stdout.contains("Usage:"))
    assertEquals(env.tracker.feedbackIssueCallList, Nil)
  }

  test("-h alias: same banner as --help") {
    val env = FakeCommandEnv()

    val result = Feedback.run(Seq("-h"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Submit feedback to the iw-cli team"))
  }

  test("invalid --type: exit 1, no issue created") {
    val env = FakeCommandEnv()

    val result = Feedback.run(Seq("Bug title", "--type", "weird"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Type must be 'bug' or 'feature'"))
    assertEquals(env.tracker.feedbackIssueCallList, Nil)
  }

  test("happy path with title only: defaults to Feature, hits iw-cli repo") {
    val env = FakeCommandEnv()
    env.tracker.setCreateFeedbackIssueResult(
      Right(CreatedIssue("42", "https://example.com/iw-cli/issues/42"))
    )

    val result = Feedback.run(Seq("Quick", "bug", "report"), env)

    assertEquals(result.exitCode, 0)
    val calls = env.tracker.feedbackIssueCallList
    assertEquals(calls.size, 1)
    assertEquals(calls.head.repository, Constants.Feedback.Repository)
    assertEquals(calls.head.title, "Quick bug report")
    assertEquals(calls.head.description, "")
    assertEquals(calls.head.issueType, FeedbackParser.IssueType.Feature)
    assert(env.console.stdout.contains("Issue: #42"))
    assert(env.console.stdout.contains("https://example.com/iw-cli/issues/42"))
  }

  test("explicit bug type with description") {
    val env = FakeCommandEnv()

    val result = Feedback.run(
      Seq(
        "Crash on start",
        "--type",
        "bug",
        "--description",
        "Stack trace inside"
      ),
      env
    )

    assertEquals(result.exitCode, 0)
    val call = env.tracker.feedbackIssueCallList.head
    assertEquals(call.title, "Crash on start")
    assertEquals(call.description, "Stack trace inside")
    assertEquals(call.issueType, FeedbackParser.IssueType.Bug)
  }

  test("tracker error: exit 1 with formatted error") {
    val env = FakeCommandEnv()
    env.tracker.setCreateFeedbackIssueResult(Left("gh not authenticated"))

    val result = Feedback.run(Seq("Some title"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Failed to create issue"))
    assert(env.console.stderr.contains("gh not authenticated"))
  }
