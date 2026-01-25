// PURPOSE: Unit tests for GitLabClient issue fetching via glab CLI
// PURPOSE: Tests command building, JSON parsing, and prerequisite validation

package iw.core.test

import iw.core.adapters.GitLabClient
import iw.core.dashboard.FeedbackParser
import iw.core.model.Issue
import iw.core.CreatedIssue

class GitLabClientTest extends munit.FunSuite:

  // ========== buildFetchIssueCommand Tests ==========

  test("buildFetchIssueCommand generates correct glab CLI arguments for simple repository"):
    val args = GitLabClient.buildFetchIssueCommand(
      issueNumber = "123",
      repository = "owner/project"
    )

    assertEquals(args(0), "issue")
    assertEquals(args(1), "view")
    assertEquals(args(2), "123")
    assertEquals(args(3), "--repo")
    assertEquals(args(4), "owner/project")
    assertEquals(args(5), "--output")
    assertEquals(args(6), "json")

  test("buildFetchIssueCommand handles nested groups repository"):
    val args = GitLabClient.buildFetchIssueCommand(
      issueNumber = "456",
      repository = "company/team/project"
    )

    val repoIndex = args.indexOf("--repo")
    assertEquals(args(repoIndex + 1), "company/team/project")

  test("buildFetchIssueCommand with different issue number"):
    val args = GitLabClient.buildFetchIssueCommand(
      issueNumber = "1",
      repository = "owner/project"
    )

    assertEquals(args(2), "1")

  // ========== parseFetchIssueResponse Tests ==========

  test("parseFetchIssueResponse parses valid opened issue JSON"):
    val json = """{
      "iid": 1,
      "state": "opened",
      "title": "Issue title here",
      "description": "Issue body/description here",
      "author": {
        "username": "pkatolicky",
        "name": "Petr Katolický"
      },
      "assignees": [],
      "labels": [],
      "web_url": "https://gitlab.e-bs.cz/CMI/mdr/medeca-modul-ucet-zadatele/-/issues/1"
    }"""

    val result = GitLabClient.parseFetchIssueResponse(json, "PROJ-1")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "PROJ-1")
    assertEquals(issue.title, "Issue title here")
    assertEquals(issue.status, "open")
    assertEquals(issue.assignee, None)
    assertEquals(issue.description, Some("Issue body/description here"))

  test("parseFetchIssueResponse parses closed issue JSON"):
    val json = """{
      "iid": 2,
      "state": "closed",
      "title": "Closed issue",
      "description": "This is closed",
      "author": {"username": "user1"},
      "assignees": [{"username": "assignee1", "name": "Assignee Name"}],
      "labels": ["bug"],
      "web_url": "https://gitlab.e-bs.cz/owner/project/-/issues/2"
    }"""

    val result = GitLabClient.parseFetchIssueResponse(json, "PROJ-2")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "PROJ-2")
    assertEquals(issue.title, "Closed issue")
    assertEquals(issue.status, "closed")
    assertEquals(issue.assignee, Some("assignee1"))
    assertEquals(issue.description, Some("This is closed"))

  test("parseFetchIssueResponse handles missing optional fields"):
    val json = """{
      "iid": 3,
      "state": "opened",
      "title": "Minimal issue",
      "description": null,
      "author": {"username": "user1"},
      "assignees": [],
      "labels": [],
      "web_url": "https://gitlab.e-bs.cz/owner/project/-/issues/3"
    }"""

    val result = GitLabClient.parseFetchIssueResponse(json, "3")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.description, None)
    assertEquals(issue.assignee, None)

  test("parseFetchIssueResponse handles empty description"):
    val json = """{
      "iid": 4,
      "state": "opened",
      "title": "Issue with empty description",
      "description": "",
      "author": {"username": "user1"},
      "assignees": [],
      "labels": [],
      "web_url": "https://gitlab.e-bs.cz/owner/project/-/issues/4"
    }"""

    val result = GitLabClient.parseFetchIssueResponse(json, "4")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.description, None)

  test("parseFetchIssueResponse uses first assignee when multiple exist"):
    val json = """{
      "iid": 5,
      "state": "opened",
      "title": "Multiple assignees",
      "description": "Test",
      "author": {"username": "user1"},
      "assignees": [
        {"username": "assignee1", "name": "First"},
        {"username": "assignee2", "name": "Second"}
      ],
      "labels": [],
      "web_url": "https://gitlab.e-bs.cz/owner/project/-/issues/5"
    }"""

    val result = GitLabClient.parseFetchIssueResponse(json, "5")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.assignee, Some("assignee1"))

  test("parseFetchIssueResponse preserves full issue ID value"):
    val json = """{
      "iid": 999,
      "state": "opened",
      "title": "Test",
      "description": null,
      "author": {"username": "user1"},
      "assignees": [],
      "labels": [],
      "web_url": "https://gitlab.e-bs.cz/owner/project/-/issues/999"
    }"""

    val result = GitLabClient.parseFetchIssueResponse(json, "TEAM-999")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "TEAM-999")

  test("parseFetchIssueResponse returns error for malformed JSON"):
    val json = """{"invalid json"""

    val result = GitLabClient.parseFetchIssueResponse(json, "1")

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to parse"))

  test("parseFetchIssueResponse returns error for missing title field"):
    val json = """{
      "iid": 1,
      "state": "opened",
      "description": "Test",
      "author": {"username": "user1"},
      "assignees": [],
      "labels": [],
      "web_url": "https://gitlab.e-bs.cz/owner/project/-/issues/1"
    }"""

    val result = GitLabClient.parseFetchIssueResponse(json, "1")

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to parse"))

  test("parseFetchIssueResponse returns error for missing state field"):
    val json = """{
      "iid": 1,
      "title": "Test",
      "description": "Test",
      "author": {"username": "user1"},
      "assignees": [],
      "labels": [],
      "web_url": "https://gitlab.e-bs.cz/owner/project/-/issues/1"
    }"""

    val result = GitLabClient.parseFetchIssueResponse(json, "1")

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to parse"))

  // ========== validateGlabPrerequisites Tests ==========

  test("validateGlabPrerequisites returns GlabNotInstalled when glab not found"):
    val mockIsCommandAvailable = (cmd: String) => false
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      Right("shouldn't get here")

    val result = GitLabClient.validateGlabPrerequisites(
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    assertEquals(result.left.getOrElse(null), GitLabClient.GlabPrerequisiteError.GlabNotInstalled)

  test("validateGlabPrerequisites returns GlabNotAuthenticated when auth status fails"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Left("not logged in")
      else
        Right("success")

    val result = GitLabClient.validateGlabPrerequisites(
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    assertEquals(result.left.getOrElse(null), GitLabClient.GlabPrerequisiteError.GlabNotAuthenticated)

  test("validateGlabPrerequisites returns Right(()) when glab is authenticated"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in to gitlab.e-bs.cz")
      else
        Right("success")

    val result = GitLabClient.validateGlabPrerequisites(
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)

  // ========== fetchIssue Integration Tests ==========

  test("fetchIssue validates prerequisites first - glab not installed"):
    val mockIsCommandAvailable = (cmd: String) => false
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      fail("Should not execute command when glab not installed")

    val result = GitLabClient.fetchIssue(
      issueIdValue = "PROJ-123",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("glab CLI is not installed"), s"Error should mention glab not installed: $error")

  test("fetchIssue validates prerequisites first - glab not authenticated"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Left("not logged in")
      else
        fail("Should not execute issue command when not authenticated")

    val result = GitLabClient.fetchIssue(
      issueIdValue = "PROJ-123",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("glab is not authenticated"), s"Error should mention glab not authenticated: $error")

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
        Right("""{
          "iid": 123,
          "state": "opened",
          "title": "Test",
          "description": null,
          "author": {"username": "user1"},
          "assignees": [],
          "labels": [],
          "web_url": "https://gitlab.e-bs.cz/owner/project/-/issues/123"
        }""")

    val result = GitLabClient.fetchIssue(
      issueIdValue = "PROJ-123",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    assertEquals(capturedCommand, "glab")
    assert(capturedArgs.contains("issue"))
    assert(capturedArgs.contains("view"))
    assert(capturedArgs.contains("123")) // API uses extracted numeric ID
    assert(capturedArgs.contains("--repo"))
    assert(capturedArgs.contains("owner/project"))

  test("fetchIssue parses successful response into Issue"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else
        Right("""{
          "iid": 123,
          "state": "opened",
          "title": "Add feature",
          "description": "Description here",
          "author": {"username": "user1"},
          "assignees": [{"username": "assignee1"}],
          "labels": ["enhancement"],
          "web_url": "https://gitlab.e-bs.cz/owner/project/-/issues/123"
        }""")

    val result = GitLabClient.fetchIssue(
      issueIdValue = "PROJ-123",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "PROJ-123") // Full issue ID format
    assertEquals(issue.title, "Add feature")
    assertEquals(issue.status, "open")
    assertEquals(issue.assignee, Some("assignee1"))
    assertEquals(issue.description, Some("Description here"))

  test("fetchIssue returns Left when command execution fails"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else
        Left("ERROR: 404 Not Found")

    val result = GitLabClient.fetchIssue(
      issueIdValue = "PROJ-999999",
      repository = "owner/project",
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

    val result = GitLabClient.fetchIssue(
      issueIdValue = "PROJ-123",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to parse"), "Error should mention failed to parse")

  // ========== Error Formatting Tests ==========

  test("formatGlabNotInstalledError returns installation instructions"):
    val error = GitLabClient.formatGlabNotInstalledError()

    assert(error.contains("glab CLI is not installed"))
    assert(error.contains("https://gitlab.com/gitlab-org/cli"))
    assert(error.contains("glab auth login"))

  test("formatGlabNotAuthenticatedError returns auth login instructions"):
    val error = GitLabClient.formatGlabNotAuthenticatedError()

    assert(error.contains("glab is not authenticated"))
    assert(error.contains("glab auth login"))
    assert(error.toLowerCase.contains("authenticate"))

  test("formatIssueNotFoundError includes issue ID and repository"):
    val error = GitLabClient.formatIssueNotFoundError("123", "owner/project")

    assert(error.contains("123"))
    assert(error.contains("owner/project"))
    assert(error.toLowerCase.contains("not found"))

  test("formatNetworkError includes details and suggestions"):
    val error = GitLabClient.formatNetworkError("Connection timeout")

    assert(error.contains("Connection timeout"))
    assert(error.toLowerCase.contains("network"))
    assert(error.toLowerCase.contains("connection"))

  // ========== Error Detection Tests ==========

  test("isAuthenticationError detects 401 status code"):
    assert(GitLabClient.isAuthenticationError("HTTP Error 401"))
    assert(GitLabClient.isAuthenticationError("Error: 401 Unauthorized"))

  test("isAuthenticationError detects unauthorized string"):
    assert(GitLabClient.isAuthenticationError("unauthorized access"))
    assert(GitLabClient.isAuthenticationError("UNAUTHORIZED"))

  test("isAuthenticationError detects authentication string"):
    assert(GitLabClient.isAuthenticationError("authentication failed"))
    assert(GitLabClient.isAuthenticationError("Authentication required"))

  test("isNotFoundError detects 404 status code"):
    assert(GitLabClient.isNotFoundError("HTTP Error 404"))
    assert(GitLabClient.isNotFoundError("Error: 404 Not Found"))

  test("isNotFoundError detects not found string"):
    assert(GitLabClient.isNotFoundError("issue not found"))
    assert(GitLabClient.isNotFoundError("NOT FOUND"))

  test("isNetworkError detects network string"):
    assert(GitLabClient.isNetworkError("network error occurred"))
    assert(GitLabClient.isNetworkError("NETWORK failure"))

  test("isNetworkError detects connection string"):
    assert(GitLabClient.isNetworkError("connection refused"))
    assert(GitLabClient.isNetworkError("Connection timeout"))

  test("isNetworkError detects timeout string"):
    assert(GitLabClient.isNetworkError("request timeout"))
    assert(GitLabClient.isNetworkError("TIMEOUT"))

  test("isNetworkError detects could not resolve"):
    assert(GitLabClient.isNetworkError("could not resolve host"))
    assert(GitLabClient.isNetworkError("Could not resolve"))

  // ========== Negative Tests for Error Detection ==========

  test("isAuthenticationError returns false for non-auth errors"):
    assert(!GitLabClient.isAuthenticationError("network error"))
    assert(!GitLabClient.isAuthenticationError("404 not found"))
    assert(!GitLabClient.isAuthenticationError("internal server error"))

  test("isNotFoundError returns false for non-404 errors"):
    assert(!GitLabClient.isNotFoundError("401 unauthorized"))
    assert(!GitLabClient.isNotFoundError("network error"))
    assert(!GitLabClient.isNotFoundError("internal server error"))

  test("isNetworkError returns false for non-network errors"):
    assert(!GitLabClient.isNetworkError("401 unauthorized"))
    assert(!GitLabClient.isNetworkError("404 not found"))
    assert(!GitLabClient.isNetworkError("internal server error"))

  // ========== buildCreateIssueCommand Tests ==========

  test("buildCreateIssueCommand generates correct glab CLI arguments for Bug type"):
    val args = GitLabClient.buildCreateIssueCommand(
      repository = "my-org/my-project",
      title = "Login broken",
      description = "Details",
      issueType = FeedbackParser.IssueType.Bug
    )

    assertEquals(args(0), "issue")
    assertEquals(args(1), "create")
    assertEquals(args(2), "--repo")
    assertEquals(args(3), "my-org/my-project")
    assertEquals(args(4), "--title")
    assertEquals(args(5), "Login broken")
    assertEquals(args(6), "--description")
    assertEquals(args(7), "Details")
    assertEquals(args(8), "--label")
    assertEquals(args(9), "bug")

  test("buildCreateIssueCommand generates correct glab CLI arguments for Feature type"):
    val args = GitLabClient.buildCreateIssueCommand(
      repository = "owner/repo",
      title = "Add dark mode",
      description = "Please add dark mode",
      issueType = FeedbackParser.IssueType.Feature
    )

    assertEquals(args(0), "issue")
    assertEquals(args(1), "create")
    assertEquals(args(2), "--repo")
    assertEquals(args(3), "owner/repo")
    assertEquals(args(4), "--title")
    assertEquals(args(5), "Add dark mode")
    assertEquals(args(6), "--description")
    assertEquals(args(7), "Please add dark mode")
    assertEquals(args(8), "--label")
    assertEquals(args(9), "feature")

  test("buildCreateIssueCommand handles empty description"):
    val args = GitLabClient.buildCreateIssueCommand(
      repository = "owner/repo",
      title = "Test issue",
      description = "",
      issueType = FeedbackParser.IssueType.Bug
    )

    val descIndex = args.indexOf("--description")
    assertEquals(args(descIndex + 1), "")

  test("buildCreateIssueCommandWithoutLabel generates command without label flag"):
    val args = GitLabClient.buildCreateIssueCommandWithoutLabel(
      repository = "my-org/project",
      title = "Issue",
      description = "Details"
    )

    assertEquals(args(0), "issue")
    assertEquals(args(1), "create")
    assertEquals(args(2), "--repo")
    assertEquals(args(3), "my-org/project")
    assertEquals(args(4), "--title")
    assertEquals(args(5), "Issue")
    assertEquals(args(6), "--description")
    assertEquals(args(7), "Details")
    assert(!args.contains("--label"))

  // ========== parseCreateIssueResponse Tests ==========

  test("parseCreateIssueResponse parses gitlab.com URL"):
    val output = "https://gitlab.com/owner/project/-/issues/123\n"
    val result = GitLabClient.parseCreateIssueResponse(output)

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "123")
    assertEquals(issue.url, "https://gitlab.com/owner/project/-/issues/123")

  test("parseCreateIssueResponse parses self-hosted GitLab URL"):
    val output = "https://gitlab.company.com/team/app/-/issues/456"
    val result = GitLabClient.parseCreateIssueResponse(output)

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "456")
    assertEquals(issue.url, "https://gitlab.company.com/team/app/-/issues/456")

  test("parseCreateIssueResponse handles URL with trailing newline"):
    val output = "https://gitlab.e-bs.cz/owner/project/-/issues/789\n"
    val result = GitLabClient.parseCreateIssueResponse(output)

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "789")
    assertEquals(issue.url.trim, "https://gitlab.e-bs.cz/owner/project/-/issues/789")

  test("parseCreateIssueResponse returns error for empty response"):
    val output = ""
    val result = GitLabClient.parseCreateIssueResponse(output)

    assert(result.isLeft)
    assertEquals(result.left.getOrElse(""), "Empty response from glab CLI")

  test("parseCreateIssueResponse returns error for invalid URL format"):
    val output = "https://gitlab.com/not-an-issue-url"
    val result = GitLabClient.parseCreateIssueResponse(output)

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Unexpected response format"))

  test("parseCreateIssueResponse handles URL with whitespace"):
    val output = "  https://gitlab.com/owner/project/-/issues/999  \n"
    val result = GitLabClient.parseCreateIssueResponse(output)

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "999")

  // ========== isLabelError Tests ==========

  test("isLabelError detects label not found error"):
    val error = "Error: label 'bug' not found in project"
    assert(GitLabClient.isLabelError(error))

  test("isLabelError detects does not exist error"):
    val error = "label does not exist"
    assert(GitLabClient.isLabelError(error))

  test("isLabelError detects invalid label error"):
    val error = "Error: invalid label specified"
    assert(GitLabClient.isLabelError(error))

  test("isLabelError returns false for network timeout"):
    val error = "Network timeout"
    assert(!GitLabClient.isLabelError(error))

  test("isLabelError returns false for auth error"):
    val error = "401 Unauthorized"
    assert(!GitLabClient.isLabelError(error))

  test("isLabelError returns false for generic error"):
    val error = "Something went wrong"
    assert(!GitLabClient.isLabelError(error))

  // ========== createIssue Integration Tests ==========

  test("createIssue validates prerequisites - glab not installed"):
    val mockIsCommandAvailable = (cmd: String) => false
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      fail("Should not execute command when glab not installed")

    val result = GitLabClient.createIssue(
      repository = "owner/project",
      title = "Test issue",
      description = "Details",
      issueType = FeedbackParser.IssueType.Bug,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("glab CLI is not installed"))

  test("createIssue validates prerequisites - glab not authenticated"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Left("not logged in")
      else
        fail("Should not execute issue command when not authenticated")

    val result = GitLabClient.createIssue(
      repository = "owner/project",
      title = "Test issue",
      description = "Details",
      issueType = FeedbackParser.IssueType.Bug,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("glab is not authenticated"))

  test("createIssue success path with label"):
    var commandCalls = 0
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in to gitlab.com")
      else if args.contains("issue") && args.contains("create") then
        commandCalls += 1
        Right("https://gitlab.com/owner/project/-/issues/123\n")
      else
        Left(s"Unexpected command: $cmd ${args.mkString(" ")}")

    val result = GitLabClient.createIssue(
      repository = "owner/project",
      title = "Bug report",
      description = "Something is broken",
      issueType = FeedbackParser.IssueType.Bug,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "123")
    assertEquals(issue.url, "https://gitlab.com/owner/project/-/issues/123")
    assertEquals(commandCalls, 1) // Only one attempt

  test("createIssue retries without label on label error"):
    var commandCalls = 0
    var lastArgs: Array[String] = Array()
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else if args.contains("issue") && args.contains("create") then
        commandCalls += 1
        lastArgs = args
        if args.contains("--label") then
          // First attempt with label fails
          Left("Error: label 'bug' not found in project")
        else
          // Second attempt without label succeeds
          Right("https://gitlab.com/owner/project/-/issues/456\n")
      else
        Left(s"Unexpected command: $cmd ${args.mkString(" ")}")

    val result = GitLabClient.createIssue(
      repository = "owner/project",
      title = "Feature request",
      description = "Add something",
      issueType = FeedbackParser.IssueType.Feature,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "456")
    assertEquals(commandCalls, 2) // Two attempts: with label, then without
    assert(!lastArgs.contains("--label")) // Final attempt had no label

  test("createIssue does not retry on non-label error"):
    var commandCalls = 0
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else if args.contains("issue") && args.contains("create") then
        commandCalls += 1
        Left("Network timeout")
      else
        Left(s"Unexpected command: $cmd ${args.mkString(" ")}")

    val result = GitLabClient.createIssue(
      repository = "owner/project",
      title = "Test",
      description = "Details",
      issueType = FeedbackParser.IssueType.Bug,
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    assertEquals(commandCalls, 1) // Only one attempt, no retry
    assert(result.left.getOrElse("").contains("Network timeout"))
