// PURPOSE: Unit tests for GitLabClient issue fetching via glab CLI
// PURPOSE: Tests command building, JSON parsing, and prerequisite validation

package iw.core.test

import iw.core.{GitLabClient, Issue}

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

    val result = GitLabClient.parseFetchIssueResponse(json, "1")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "#1")
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

    val result = GitLabClient.parseFetchIssueResponse(json, "2")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "#2")
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

  test("parseFetchIssueResponse formats issue ID with # prefix"):
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

    val result = GitLabClient.parseFetchIssueResponse(json, "999")

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "#999")

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
      issueNumber = "123",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("glab CLI is not installed"))

  test("fetchIssue validates prerequisites first - glab not authenticated"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Left("not logged in")
      else
        fail("Should not execute issue command when not authenticated")

    val result = GitLabClient.fetchIssue(
      issueNumber = "123",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("glab is not authenticated"))

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
      issueNumber = "123",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    assertEquals(capturedCommand, "glab")
    assert(capturedArgs.contains("issue"))
    assert(capturedArgs.contains("view"))
    assert(capturedArgs.contains("123"))
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
      issueNumber = "123",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right"))
    assertEquals(issue.id, "#123")
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
      issueNumber = "999999",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to fetch issue"))

  test("fetchIssue returns Left when JSON parsing fails"):
    val mockIsCommandAvailable = (cmd: String) => true
    val mockExec: (String, Array[String]) => Either[String, String] = (cmd, args) =>
      if args.contains("auth") && args.contains("status") then
        Right("Logged in")
      else
        Right("""{"invalid json""")

    val result = GitLabClient.fetchIssue(
      issueNumber = "123",
      repository = "owner/project",
      isCommandAvailable = mockIsCommandAvailable,
      execCommand = mockExec
    )

    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("Failed to parse"))

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
