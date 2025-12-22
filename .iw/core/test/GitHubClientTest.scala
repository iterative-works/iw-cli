// PURPOSE: Unit tests for GitHubClient issue creation via gh CLI
// PURPOSE: Tests command building, JSON parsing, and error handling

package iw.core.test

import iw.core.{GitHubClient, CreatedIssue, FeedbackParser}

class GitHubClientTest extends munit.FunSuite:

  test("buildCreateIssueCommand with title only"):
    val args = GitHubClient.buildCreateIssueCommand(
      repository = "owner/repo",
      title = "Bug in start command",
      description = "",
      issueType = FeedbackParser.IssueType.Feature
    )

    assertEquals(args(0), "gh")
    assertEquals(args(1), "issue")
    assertEquals(args(2), "create")
    assertEquals(args(3), "--repo")
    assertEquals(args(4), "owner/repo")
    assertEquals(args(5), "--title")
    assertEquals(args(6), "Bug in start command")
    assertEquals(args(7), "--label")
    assertEquals(args(8), "feedback")
    assertEquals(args(9), "--json")
    assertEquals(args(10), "number,url")

  test("buildCreateIssueCommand with description"):
    val args = GitHubClient.buildCreateIssueCommand(
      repository = "owner/repo",
      title = "Feature request",
      description = "Would be nice to have X",
      issueType = FeedbackParser.IssueType.Feature
    )

    assert(args.contains("--body"))
    val bodyIndex = args.indexOf("--body")
    assertEquals(args(bodyIndex + 1), "Would be nice to have X")

  test("buildCreateIssueCommand with bug label"):
    val args = GitHubClient.buildCreateIssueCommand(
      repository = "owner/repo",
      title = "Bug report",
      description = "Something broke",
      issueType = FeedbackParser.IssueType.Bug
    )

    assert(args.contains("--label"))
    val labelIndex = args.indexOf("--label")
    assertEquals(args(labelIndex + 1), "bug")

  test("buildCreateIssueCommand with feedback label for feature type"):
    val args = GitHubClient.buildCreateIssueCommand(
      repository = "owner/repo",
      title = "Feature request",
      description = "Add new feature",
      issueType = FeedbackParser.IssueType.Feature
    )

    assert(args.contains("--label"))
    val labelIndex = args.indexOf("--label")
    assertEquals(args(labelIndex + 1), "feedback")

  test("buildCreateIssueCommand uses correct repository format"):
    val args = GitHubClient.buildCreateIssueCommand(
      repository = "iterative-works/iw-cli",
      title = "Test",
      description = "",
      issueType = FeedbackParser.IssueType.Feature
    )

    val repoIndex = args.indexOf("--repo")
    assertEquals(args(repoIndex + 1), "iterative-works/iw-cli")

  test("parseCreateIssueResponse parses valid JSON with number and URL"):
    val json = """{"number": 132, "url": "https://github.com/owner/repo/issues/132"}"""
    val result = GitHubClient.parseCreateIssueResponse(json)

    assertEquals(result, Right(CreatedIssue("132", "https://github.com/owner/repo/issues/132")))

  test("parseCreateIssueResponse returns error for missing number field"):
    val json = """{"url": "https://github.com/owner/repo/issues/132"}"""
    val result = GitHubClient.parseCreateIssueResponse(json)

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("number"))

  test("parseCreateIssueResponse returns error for missing url field"):
    val json = """{"number": 132}"""
    val result = GitHubClient.parseCreateIssueResponse(json)

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("url"))

  test("parseCreateIssueResponse returns error for malformed JSON"):
    val json = """{"invalid json"""
    val result = GitHubClient.parseCreateIssueResponse(json)

    assert(result.isLeft)

  test("parseCreateIssueResponse returns error for empty response"):
    val json = ""
    val result = GitHubClient.parseCreateIssueResponse(json)

    assert(result.isLeft)

  test("buildCreateIssueCommandWithoutLabel generates command without label"):
    val args = GitHubClient.buildCreateIssueCommandWithoutLabel(
      repository = "owner/repo",
      title = "Bug in start command",
      description = "Description here"
    )

    assertEquals(args(0), "gh")
    assertEquals(args(1), "issue")
    assertEquals(args(2), "create")
    assertEquals(args(3), "--repo")
    assertEquals(args(4), "owner/repo")
    assertEquals(args(5), "--title")
    assertEquals(args(6), "Bug in start command")
    assertEquals(args(7), "--body")
    assertEquals(args(8), "Description here")
    assertEquals(args(9), "--json")
    assertEquals(args(10), "number,url")
    // Verify no --label flag
    assert(!args.contains("--label"))

  test("createIssue retries without label on label error"):
    var callCount = 0
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      callCount += 1
      if callCount == 1 then
        // First call fails with label error
        Left("label 'bug' not found in repository")
      else
        // Second call succeeds without label
        Right("""{"number": 42, "url": "https://github.com/owner/repo/issues/42"}""")

    val result = GitHubClient.createIssue(
      repository = "owner/repo",
      title = "Test",
      description = "Test description",
      issueType = FeedbackParser.IssueType.Bug,
      execCommand = mockExec
    )

    assertEquals(callCount, 2)
    assertEquals(result, Right(CreatedIssue("42", "https://github.com/owner/repo/issues/42")))

  test("createIssue does not retry on non-label error"):
    var callCount = 0
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      callCount += 1
      Left("network error: connection refused")

    val result = GitHubClient.createIssue(
      repository = "owner/repo",
      title = "Test",
      description = "",
      issueType = FeedbackParser.IssueType.Bug,
      execCommand = mockExec
    )

    assertEquals(callCount, 1)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("network error"))

  test("createIssue returns error when retry also fails"):
    var callCount = 0
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      callCount += 1
      if callCount == 1 then
        Left("label 'bug' does not exist")
      else
        Left("permission denied")

    val result = GitHubClient.createIssue(
      repository = "owner/repo",
      title = "Test",
      description = "",
      issueType = FeedbackParser.IssueType.Bug,
      execCommand = mockExec
    )

    assertEquals(callCount, 2)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("permission denied"))
