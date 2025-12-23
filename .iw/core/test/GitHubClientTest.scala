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
    var authCallCount = 0
    var issueCreateCallCount = 0
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        authCallCount += 1
        // Auth check succeeds
        Right("Logged in")
      else if args.contains("issue") && args.contains("create") then
        issueCreateCallCount += 1
        if issueCreateCallCount == 1 then
          // First issue create call fails with label error
          Left("label 'bug' not found in repository")
        else
          // Second issue create call succeeds without label
          Right("""{"number": 42, "url": "https://github.com/owner/repo/issues/42"}""")
      else
        Right("unexpected call")

    val result = GitHubClient.createIssue(
      repository = "owner/repo",
      title = "Test",
      description = "Test description",
      issueType = FeedbackParser.IssueType.Bug,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assertEquals(authCallCount, 1)
    assertEquals(issueCreateCallCount, 2)
    assertEquals(result, Right(CreatedIssue("42", "https://github.com/owner/repo/issues/42")))

  test("createIssue does not retry on non-label error"):
    var authCallCount = 0
    var issueCreateCallCount = 0
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        authCallCount += 1
        // Auth check succeeds
        Right("Logged in")
      else if args.contains("issue") && args.contains("create") then
        issueCreateCallCount += 1
        // Issue create fails with non-label error
        Left("network error: connection refused")
      else
        Right("unexpected call")

    val result = GitHubClient.createIssue(
      repository = "owner/repo",
      title = "Test",
      description = "",
      issueType = FeedbackParser.IssueType.Bug,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assertEquals(authCallCount, 1)
    assertEquals(issueCreateCallCount, 1) // No retry on non-label error
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("network error"))

  test("createIssue returns error when retry also fails"):
    var authCallCount = 0
    var issueCreateCallCount = 0
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        authCallCount += 1
        // Auth check succeeds
        Right("Logged in")
      else if args.contains("issue") && args.contains("create") then
        issueCreateCallCount += 1
        if issueCreateCallCount == 1 then
          // First issue create fails with label error
          Left("label 'bug' does not exist")
        else
          // Retry also fails
          Left("permission denied")
      else
        Right("unexpected call")

    val result = GitHubClient.createIssue(
      repository = "owner/repo",
      title = "Test",
      description = "",
      issueType = FeedbackParser.IssueType.Bug,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assertEquals(authCallCount, 1)
    assertEquals(issueCreateCallCount, 2)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("permission denied"))

  // ========== Prerequisite Validation Tests ==========

  test("validateGhPrerequisites returns GhNotInstalled when gh not found"):
    val mockIsCommandAvailable = (cmd: String) => false
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      Right("Logged in")

    val result = GitHubClient.validateGhPrerequisites(
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    assertEquals(result.left.getOrElse(null), GitHubClient.GhNotInstalled)

  test("validateGhPrerequisites returns GhNotAuthenticated when auth status fails with exit code 4"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Left("Command failed: gh auth status: exit status 4")
      else
        Right("success")

    val result = GitHubClient.validateGhPrerequisites(
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    assertEquals(result.left.getOrElse(null), GitHubClient.GhNotAuthenticated)

  test("validateGhPrerequisites returns Right(()) when gh is authenticated"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in to github.com")
      else
        Right("success")

    val result = GitHubClient.validateGhPrerequisites(
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)

  test("createIssue fails with installation message when gh not installed"):
    val mockIsCommandAvailable = (cmd: String) => false
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      Right("shouldn't get here")

    val result = GitHubClient.createIssue(
      repository = "owner/repo",
      title = "Test",
      description = "",
      issueType = FeedbackParser.IssueType.Bug,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("gh CLI is not installed"))
    assert(error.contains("https://cli.github.com/"))

  test("createIssue fails with auth message when gh not authenticated"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Left("Command failed: gh auth status: exit status 4")
      else
        Right("success")

    val result = GitHubClient.createIssue(
      repository = "owner/repo",
      title = "Test",
      description = "",
      issueType = FeedbackParser.IssueType.Bug,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("gh is not authenticated"))
    assert(error.contains("gh auth login"))

  // ========== Error Message Formatting Tests ==========

  test("formatGhNotInstalledError contains installation URL"):
    val error = GitHubClient.formatGhNotInstalledError()

    assert(error.contains("gh CLI is not installed"))
    assert(error.contains("https://cli.github.com/"))
    assert(error.contains("gh auth login"))

  test("formatGhNotAuthenticatedError contains auth instruction"):
    val error = GitHubClient.formatGhNotAuthenticatedError()

    assert(error.contains("gh is not authenticated"))
    assert(error.contains("gh auth login"))
