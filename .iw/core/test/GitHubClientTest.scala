// PURPOSE: Unit tests for GitHubClient issue creation via gh CLI
// PURPOSE: Tests command building, URL parsing, and error handling

package iw.core.test

import iw.core.adapters.GitHubClient
import iw.core.dashboard.FeedbackParser
import iw.core.CreatedIssue
import iw.core.model.Issue

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
    assertEquals(args(7), "--body")
    assertEquals(args(8), "")  // Empty body required for non-interactive
    assertEquals(args(9), "--label")
    assertEquals(args(10), "feedback")
    // No --json flag - gh issue create outputs URL directly

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

  test("parseCreateIssueResponse parses valid URL"):
    val url = "https://github.com/owner/repo/issues/132"
    val result = GitHubClient.parseCreateIssueResponse(url)

    assertEquals(result, Right(CreatedIssue("132", "https://github.com/owner/repo/issues/132")))

  test("parseCreateIssueResponse handles URL with trailing newline"):
    val url = "https://github.com/owner/repo/issues/456\n"
    val result = GitHubClient.parseCreateIssueResponse(url)

    assertEquals(result, Right(CreatedIssue("456", "https://github.com/owner/repo/issues/456")))

  test("parseCreateIssueResponse returns error for non-URL output"):
    val badOutput = "some unexpected output"
    val result = GitHubClient.parseCreateIssueResponse(badOutput)

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Unexpected response format"))

  test("parseCreateIssueResponse returns error for empty output"):
    val result = GitHubClient.parseCreateIssueResponse("")

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
    // Verify no --label flag and no --json flag
    assert(!args.contains("--label"))
    assert(!args.contains("--json"))

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
          // Second issue create call succeeds without label - returns URL
          Right("https://github.com/owner/repo/issues/42")
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
    assertEquals(result.left.getOrElse(null), GitHubClient.GhPrerequisiteError.GhNotInstalled)

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
    assertEquals(result.left.getOrElse(null), GitHubClient.GhPrerequisiteError.GhNotAuthenticated)

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

  // ========== fetchIssue Command Building Tests ==========

  test("buildFetchIssueCommand generates correct gh CLI arguments"):
    val args = GitHubClient.buildFetchIssueCommand(
      issueNumber = "132",
      repository = "owner/repo"
    )

    assertEquals(args(0), "issue")
    assertEquals(args(1), "view")
    assertEquals(args(2), "132")
    assertEquals(args(3), "--repo")
    assertEquals(args(4), "owner/repo")
    assertEquals(args(5), "--json")
    assertEquals(args(6), "number,title,state,assignees,body")

  test("buildFetchIssueCommand with different issue number"):
    val args = GitHubClient.buildFetchIssueCommand(
      issueNumber = "1",
      repository = "owner/repo"
    )

    assertEquals(args(2), "1")

  test("buildFetchIssueCommand with different repository"):
    val args = GitHubClient.buildFetchIssueCommand(
      issueNumber = "999",
      repository = "iterative-works/iw-cli"
    )

    val repoIndex = args.indexOf("--repo")
    assertEquals(args(repoIndex + 1), "iterative-works/iw-cli")

  // ========== fetchIssue JSON Parsing Tests ==========

  test("parseFetchIssueResponse parses complete valid JSON"):
    val json = """{"number": 132, "title": "Add feature", "state": "OPEN", "assignees": [{"login": "user1"}], "body": "Description here"}"""
    val result = GitHubClient.parseFetchIssueResponse(json, "IWCLI-132")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "132")
    assertEquals(issue.title, "Add feature")
    assertEquals(issue.status, "open")
    assertEquals(issue.assignee, Some("user1"))
    assertEquals(issue.description, Some("Description here"))

  test("parseFetchIssueResponse handles empty assignees array"):
    val json = """{"number": 132, "title": "Add feature", "state": "OPEN", "assignees": [], "body": "Description"}"""
    val result = GitHubClient.parseFetchIssueResponse(json, "132")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.assignee, None)

  test("parseFetchIssueResponse handles null body"):
    val json = """{"number": 132, "title": "Add feature", "state": "OPEN", "assignees": [], "body": null}"""
    val result = GitHubClient.parseFetchIssueResponse(json, "132")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.description, None)

  test("parseFetchIssueResponse uses first assignee when multiple exist"):
    val json = """{"number": 132, "title": "Add feature", "state": "OPEN", "assignees": [{"login": "user1"}, {"login": "user2"}], "body": "Description"}"""
    val result = GitHubClient.parseFetchIssueResponse(json, "132")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.assignee, Some("user1"))

  test("parseFetchIssueResponse maps OPEN state to lowercase"):
    val json = """{"number": 132, "title": "Add feature", "state": "OPEN", "assignees": [], "body": null}"""
    val result = GitHubClient.parseFetchIssueResponse(json, "132")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.status, "open")

  test("parseFetchIssueResponse maps CLOSED state to lowercase"):
    val json = """{"number": 132, "title": "Add feature", "state": "CLOSED", "assignees": [], "body": null}"""
    val result = GitHubClient.parseFetchIssueResponse(json, "132")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.status, "closed")

  test("parseFetchIssueResponse uses bare issue number as ID"):
    val json = """{"number": 999, "title": "Test", "state": "OPEN", "assignees": [], "body": null}"""
    val result = GitHubClient.parseFetchIssueResponse(json, "TEAM-999")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "999")

  test("parseFetchIssueResponse returns error for malformed JSON"):
    val json = """{"invalid json"""
    val result = GitHubClient.parseFetchIssueResponse(json, "132")

    assert(result.isLeft)

  test("parseFetchIssueResponse returns error for missing title field"):
    val json = """{"number": 132, "state": "OPEN", "assignees": [], "body": null}"""
    val result = GitHubClient.parseFetchIssueResponse(json, "132")

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to parse"))

  test("parseFetchIssueResponse returns error for missing state field"):
    val json = """{"number": 132, "title": "Test", "assignees": [], "body": null}"""
    val result = GitHubClient.parseFetchIssueResponse(json, "132")

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to parse"))

  // ========== fetchIssue Integration Tests ==========

  test("fetchIssue validates prerequisites first - gh not installed"):
    val mockIsCommandAvailable = (cmd: String) => false
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      fail("Should not execute command when gh not installed")

    val result = GitHubClient.fetchIssue(
      issueIdValue = "TEAM-132",
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("gh CLI is not installed"), s"Error should mention gh not installed: $error")

  test("fetchIssue validates prerequisites first - gh not authenticated"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Left("Command failed: gh auth status: exit status 4")
      else
        fail("Should not execute issue command when not authenticated")

    val result = GitHubClient.fetchIssue(
      issueIdValue = "TEAM-132",
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("gh is not authenticated"), s"Error should mention gh not authenticated: $error")

  test("fetchIssue executes command with correct arguments"):
    var capturedCommand = ""
    var capturedArgs = Array.empty[String]
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else
        capturedCommand = cmd
        capturedArgs = args
        Right("""{"number": 132, "title": "Test", "state": "OPEN", "assignees": [], "body": null}""")

    val result = GitHubClient.fetchIssue(
      issueIdValue = "TEAM-132",
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    assertEquals(capturedCommand, "gh")
    assert(capturedArgs.contains("issue"))
    assert(capturedArgs.contains("view"))
    assert(capturedArgs.contains("132")) // API uses extracted numeric ID
    assert(capturedArgs.contains("--repo"))
    assert(capturedArgs.contains("owner/repo"))

  test("fetchIssue parses successful response into Issue"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else
        Right("""{"number": 132, "title": "Add feature", "state": "OPEN", "assignees": [{"login": "user1"}], "body": "Description"}""")

    val result = GitHubClient.fetchIssue(
      issueIdValue = "TEAM-132",
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "132")
    assertEquals(issue.title, "Add feature")
    assertEquals(issue.status, "open")
    assertEquals(issue.assignee, Some("user1"))
    assertEquals(issue.description, Some("Description"))

  test("fetchIssue returns Left when command execution fails"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else
        Left("issue not found")

    val result = GitHubClient.fetchIssue(
      issueIdValue = "TEAM-999999",
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to fetch issue"), "Error should mention failed to fetch")

  test("fetchIssue returns Left when JSON parsing fails"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else
        Right("""{"invalid json""")

    val result = GitHubClient.fetchIssue(
      issueIdValue = "TEAM-132",
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to parse"), "Error should mention failed to parse")

  // ========== listRecentIssues Command Building Tests ==========

  test("buildListRecentIssuesCommand with default limit"):
    val args = GitHubClient.buildListRecentIssuesCommand(
      repository = "owner/repo"
    )

    assertEquals(args(0), "issue")
    assertEquals(args(1), "list")
    assertEquals(args(2), "--repo")
    assertEquals(args(3), "owner/repo")
    assertEquals(args(4), "--state")
    assertEquals(args(5), "open")
    assertEquals(args(6), "--limit")
    assertEquals(args(7), "5")
    assertEquals(args(8), "--json")
    assertEquals(args(9), "number,title,state,updatedAt")

  test("buildListRecentIssuesCommand with custom limit"):
    val args = GitHubClient.buildListRecentIssuesCommand(
      repository = "iterative-works/iw-cli",
      limit = 10
    )

    assertEquals(args(2), "--repo")
    assertEquals(args(3), "iterative-works/iw-cli")
    assertEquals(args(6), "--limit")
    assertEquals(args(7), "10")

  // ========== listRecentIssues JSON Parsing Tests ==========

  test("parseListRecentIssuesResponse parses valid JSON array with multiple issues"):
    val json = """[
      {"number": 132, "title": "Add feature", "state": "OPEN", "updatedAt": "2024-01-15T10:30:00Z"},
      {"number": 131, "title": "Fix bug", "state": "OPEN", "updatedAt": "2024-01-14T09:15:00Z"},
      {"number": 130, "title": "Update docs", "state": "OPEN", "updatedAt": "2024-01-13T14:20:00Z"}
    ]"""
    val result = GitHubClient.parseListRecentIssuesResponse(json)

    assert(result.isRight)
    val issues = result.getOrElse(fail("Expected Right"))
    assertEquals(issues.length, 3)
    assertEquals(issues(0).id, "132")
    assertEquals(issues(0).title, "Add feature")
    assertEquals(issues(0).status, "open")
    assertEquals(issues(1).id, "131")
    assertEquals(issues(1).title, "Fix bug")
    assertEquals(issues(2).id, "130")
    assertEquals(issues(2).title, "Update docs")

  test("parseListRecentIssuesResponse parses empty array"):
    val json = """[]"""
    val result = GitHubClient.parseListRecentIssuesResponse(json)

    assert(result.isRight)
    val issues = result.getOrElse(fail("Expected Right"))
    assertEquals(issues.length, 0)

  test("parseListRecentIssuesResponse handles malformed JSON"):
    val json = """[{"invalid": "json"""
    val result = GitHubClient.parseListRecentIssuesResponse(json)

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to parse"))

  test("parseListRecentIssuesResponse handles missing required fields"):
    val json = """[{"number": 132, "title": "Test"}]"""
    val result = GitHubClient.parseListRecentIssuesResponse(json)

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to parse"))

  // ========== listRecentIssues Integration Tests ==========

  test("listRecentIssues success case with mocked command"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else if args.contains("issue") && args.contains("list") then
        Right("""[
          {"number": 132, "title": "Add feature", "state": "OPEN", "updatedAt": "2024-01-15T10:30:00Z"},
          {"number": 131, "title": "Fix bug", "state": "OPEN", "updatedAt": "2024-01-14T09:15:00Z"}
        ]""")
      else
        Left("Unexpected command")

    val result = GitHubClient.listRecentIssues(
      repository = "owner/repo",
      limit = 5,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    val issues = result.getOrElse(fail("Expected Right"))
    assertEquals(issues.length, 2)
    assertEquals(issues(0).id, "132")
    assertEquals(issues(0).title, "Add feature")

  test("listRecentIssues fails when gh CLI not available"):
    val mockIsCommandAvailable = (cmd: String) => false
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      fail("Should not execute command when gh not installed")

    val result = GitHubClient.listRecentIssues(
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("gh CLI is not installed"))

  test("listRecentIssues fails when gh CLI not authenticated"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Left("Command failed: gh auth status: exit status 4")
      else
        fail("Should not execute issue command when not authenticated")

    val result = GitHubClient.listRecentIssues(
      repository = "owner/repo",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("gh is not authenticated"))

  // ========== searchIssues Command Building Tests ==========

  test("buildSearchIssuesCommand with default limit"):
    val args = GitHubClient.buildSearchIssuesCommand(
      repository = "owner/repo",
      query = "fix bug"
    )

    assertEquals(args(0), "issue")
    assertEquals(args(1), "list")
    assertEquals(args(2), "--repo")
    assertEquals(args(3), "owner/repo")
    assertEquals(args(4), "--search")
    assertEquals(args(5), "fix bug")
    assertEquals(args(6), "--state")
    assertEquals(args(7), "open")
    assertEquals(args(8), "--limit")
    assertEquals(args(9), "10")
    assertEquals(args(10), "--json")
    assertEquals(args(11), "number,title,state,updatedAt")

  test("buildSearchIssuesCommand with custom limit"):
    val args = GitHubClient.buildSearchIssuesCommand(
      repository = "iterative-works/iw-cli",
      query = "performance",
      limit = 20
    )

    assertEquals(args(2), "--repo")
    assertEquals(args(3), "iterative-works/iw-cli")
    assertEquals(args(4), "--search")
    assertEquals(args(5), "performance")
    assertEquals(args(8), "--limit")
    assertEquals(args(9), "20")

  test("buildSearchIssuesCommand query parameter"):
    val args = GitHubClient.buildSearchIssuesCommand(
      repository = "owner/repo",
      query = "authentication error in login"
    )

    val searchIndex = args.indexOf("--search")
    assertEquals(args(searchIndex + 1), "authentication error in login")

  // ========== searchIssues Parsing Tests ==========

  test("parseSearchIssuesResponse reuses parseListRecentIssuesResponse"):
    val json = """[
      {"number": 132, "title": "Add feature", "state": "OPEN", "updatedAt": "2024-01-15T10:30:00Z"},
      {"number": 131, "title": "Fix bug", "state": "OPEN", "updatedAt": "2024-01-14T09:15:00Z"}
    ]"""
    val result = GitHubClient.parseSearchIssuesResponse(json)

    assert(result.isRight)
    val issues = result.getOrElse(fail("Expected Right"))
    assertEquals(issues.length, 2)
    assertEquals(issues(0).id, "132")
    assertEquals(issues(0).title, "Add feature")
    assertEquals(issues(1).id, "131")
    assertEquals(issues(1).title, "Fix bug")

  // ========== searchIssues Integration Tests ==========

  test("searchIssues success case with mocked command"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else if args.contains("issue") && args.contains("list") && args.contains("--search") then
        Right("""[
          {"number": 132, "title": "Fix authentication bug", "state": "OPEN", "updatedAt": "2024-01-15T10:30:00Z"},
          {"number": 131, "title": "Add auth feature", "state": "OPEN", "updatedAt": "2024-01-14T09:15:00Z"}
        ]""")
      else
        Left("Unexpected command")

    val result = GitHubClient.searchIssues(
      repository = "owner/repo",
      query = "auth",
      limit = 10,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    val issues = result.getOrElse(fail("Expected Right"))
    assertEquals(issues.length, 2)
    assertEquals(issues(0).id, "132")
    assertEquals(issues(0).title, "Fix authentication bug")

  test("searchIssues when gh CLI not available"):
    val mockIsCommandAvailable = (cmd: String) => false
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      fail("Should not execute command when gh not installed")

    val result = GitHubClient.searchIssues(
      repository = "owner/repo",
      query = "test",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("gh CLI is not installed"))

  test("searchIssues empty results"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else if args.contains("issue") && args.contains("list") && args.contains("--search") then
        Right("[]")
      else
        Left("Unexpected command")

    val result = GitHubClient.searchIssues(
      repository = "owner/repo",
      query = "nonexistent",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    val issues = result.getOrElse(fail("Expected Right"))
    assertEquals(issues.length, 0)
