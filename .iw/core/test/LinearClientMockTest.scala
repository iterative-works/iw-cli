// PURPOSE: Unit tests for LinearClient using mocked HTTP backend
// PURPOSE: Tests HTTP operations without making real API calls using sttp SyncBackendStub

package iw.tests

import iw.core.*
import munit.FunSuite
import sttp.client4.testing.SyncBackendStub
import sttp.model.StatusCode

class LinearClientMockTest extends FunSuite:

  // --- validateToken tests ---

  test("validateToken returns true for 200 OK response"):
    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondAdjust("""{"data":{"viewer":{"id":"test-user-id"}}}""")

    val token = ApiToken("test-token").get
    val result = LinearClient.validateToken(token, testBackend)
    assertEquals(result, true)

  test("validateToken returns false for 401 Unauthorized"):
    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondUnauthorized()

    val token = ApiToken("invalid-token").get
    val result = LinearClient.validateToken(token, testBackend)
    assertEquals(result, false)

  // --- fetchIssue tests ---

  test("fetchIssue returns Right(Issue) for valid response"):
    val jsonResponse = """{
      "data": {
        "issue": {
          "identifier": "IWLE-123",
          "title": "Test Issue",
          "state": { "name": "In Progress" },
          "assignee": { "displayName": "John Doe" },
          "description": "Test description"
        }
      }
    }"""

    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondAdjust(jsonResponse)

    val issueId = IssueId.parse("IWLE-123").getOrElse(fail("Invalid issue ID"))
    val token = ApiToken("test-token").get
    val result = LinearClient.fetchIssue(issueId, token, testBackend)

    assert(result.isRight, s"Expected Right but got $result")
    val issue = result.getOrElse(fail("Expected Issue"))
    assertEquals(issue.id, "IWLE-123")
    assertEquals(issue.title, "Test Issue")
    assertEquals(issue.status, "In Progress")
    assertEquals(issue.assignee, Some("John Doe"))
    assertEquals(issue.description, Some("Test description"))

  test("fetchIssue returns Left for 401 Unauthorized"):
    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondUnauthorized()

    val issueId = IssueId.parse("IWLE-123").getOrElse(fail("Invalid issue ID"))
    val token = ApiToken("invalid-token").get
    val result = LinearClient.fetchIssue(issueId, token, testBackend)

    assert(result.isLeft, "Expected Left for 401")
    val error = result.left.getOrElse("")
    assert(error.contains("token") || error.contains("expired"), s"Expected token error, got: $error")

  test("fetchIssue returns Left for issue not found"):
    val jsonResponse = """{"data": {"issue": null}}"""

    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondAdjust(jsonResponse)

    val issueId = IssueId.parse("IWLE-999").getOrElse(fail("Invalid issue ID"))
    val token = ApiToken("test-token").get
    val result = LinearClient.fetchIssue(issueId, token, testBackend)

    assert(result.isLeft, "Expected Left for not found")
    val error = result.left.getOrElse("")
    assert(error.contains("not found"), s"Expected not found error, got: $error")

  // --- createIssue tests ---

  test("createIssue returns Right(CreatedIssue) for valid response"):
    val jsonResponse = """{
      "data": {
        "issueCreate": {
          "success": true,
          "issue": {
            "id": "abc123",
            "url": "https://linear.app/test/issue/IWLE-123"
          }
        }
      }
    }"""

    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondAdjust(jsonResponse)

    val token = ApiToken("test-token").get
    val result = LinearClient.createIssue(
      title = "Test Issue",
      description = "Test Description",
      teamId = "team-123",
      token = token,
      backend = testBackend
    )

    assert(result.isRight, s"Expected Right but got $result")
    val created = result.getOrElse(fail("Expected CreatedIssue"))
    assertEquals(created.id, "abc123")
    assertEquals(created.url, "https://linear.app/test/issue/IWLE-123")

  test("createIssue returns Left for 401 Unauthorized"):
    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondUnauthorized()

    val token = ApiToken("invalid-token").get
    val result = LinearClient.createIssue(
      title = "Test Issue",
      description = "Test Description",
      teamId = "team-123",
      token = token,
      backend = testBackend
    )

    assert(result.isLeft, "Expected Left for 401")
    val error = result.left.getOrElse("")
    assert(error.contains("token") || error.contains("expired"), s"Expected token error, got: $error")

  test("createIssue returns Left for 500 Server Error"):
    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondServerError()

    val token = ApiToken("test-token").get
    val result = LinearClient.createIssue(
      title = "Test Issue",
      description = "Test Description",
      teamId = "team-123",
      token = token,
      backend = testBackend
    )

    assert(result.isLeft, "Expected Left for 500")
    val error = result.left.getOrElse("")
    assert(error.contains("API error") || error.contains("500"), s"Expected API error, got: $error")

  test("createIssue returns Left for GraphQL error response"):
    val jsonResponse = """{"errors":[{"message":"Invalid team ID"}]}"""

    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondAdjust(jsonResponse)

    val token = ApiToken("test-token").get
    val result = LinearClient.createIssue(
      title = "Test Issue",
      description = "Test Description",
      teamId = "invalid-team",
      token = token,
      backend = testBackend
    )

    assert(result.isLeft, "Expected Left for GraphQL error")
    val error = result.left.getOrElse("")
    assert(error.contains("Invalid team"), s"Expected team error, got: $error")

  // --- buildListRecentIssuesQuery tests ---

  test("buildListRecentIssuesQuery with default limit (5)"):
    val query = LinearClient.buildListRecentIssuesQuery("team-123")

    // Should be valid JSON with GraphQL query
    assert(query.contains("\"query\""))
    assert(query.contains("team(id: \\\"team-123\\\")"))
    assert(query.contains("issues(first: 5, orderBy: createdAt, filter:"))
    assert(query.contains("nin"))  // Filter excludes completed/canceled
    assert(query.contains("nodes"))
    assert(query.contains("identifier"))
    assert(query.contains("title"))
    assert(query.contains("state"))

  test("buildListRecentIssuesQuery with custom limit"):
    val query = LinearClient.buildListRecentIssuesQuery("team-456", 10)

    assert(query.contains("team(id: \\\"team-456\\\")"))
    assert(query.contains("issues(first: 10, orderBy: createdAt, filter:"))

  // --- parseListRecentIssuesResponse tests ---

  test("parseListRecentIssuesResponse with valid response"):
    val jsonResponse = """{
      "data": {
        "team": {
          "issues": {
            "nodes": [
              {
                "identifier": "IW-123",
                "title": "First issue",
                "state": { "name": "In Progress" }
              },
              {
                "identifier": "IW-122",
                "title": "Second issue",
                "state": { "name": "Todo" }
              },
              {
                "identifier": "IW-121",
                "title": "Third issue",
                "state": { "name": "Done" }
              }
            ]
          }
        }
      }
    }"""

    val result = LinearClient.parseListRecentIssuesResponse(jsonResponse)

    assert(result.isRight, s"Expected Right but got $result")
    val issues = result.getOrElse(fail("Expected List[Issue]"))
    assertEquals(issues.length, 3)
    assertEquals(issues(0).id, "IW-123")
    assertEquals(issues(0).title, "First issue")
    assertEquals(issues(0).status, "In Progress")
    assertEquals(issues(1).id, "IW-122")
    assertEquals(issues(1).title, "Second issue")
    assertEquals(issues(2).id, "IW-121")

  test("parseListRecentIssuesResponse with empty issues array"):
    val jsonResponse = """{
      "data": {
        "team": {
          "issues": {
            "nodes": []
          }
        }
      }
    }"""

    val result = LinearClient.parseListRecentIssuesResponse(jsonResponse)

    assert(result.isRight, s"Expected Right but got $result")
    val issues = result.getOrElse(fail("Expected List[Issue]"))
    assertEquals(issues.length, 0)

  test("parseListRecentIssuesResponse with missing fields"):
    val jsonResponse = """{
      "data": {
        "team": {
          "issues": {
            "nodes": [
              {
                "identifier": "IW-123",
                "title": "Test issue"
              }
            ]
          }
        }
      }
    }"""

    val result = LinearClient.parseListRecentIssuesResponse(jsonResponse)

    assert(result.isLeft, "Expected Left for missing state field")
    val error = result.left.getOrElse("")
    assert(error.contains("Failed to parse") || error.contains("state"), s"Expected parse error, got: $error")

  // --- listRecentIssues tests ---

  test("listRecentIssues success case (mocked backend)"):
    val jsonResponse = """{
      "data": {
        "team": {
          "issues": {
            "nodes": [
              {
                "identifier": "IW-123",
                "title": "Recent issue",
                "state": { "name": "In Progress" }
              },
              {
                "identifier": "IW-122",
                "title": "Another issue",
                "state": { "name": "Todo" }
              }
            ]
          }
        }
      }
    }"""

    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondAdjust(jsonResponse)

    val token = ApiToken("test-token").get
    val result = LinearClient.listRecentIssues("team-123", 5, token, testBackend)

    assert(result.isRight, s"Expected Right but got $result")
    val issues = result.getOrElse(fail("Expected List[Issue]"))
    assertEquals(issues.length, 2)
    assertEquals(issues(0).id, "IW-123")
    assertEquals(issues(0).title, "Recent issue")

  test("listRecentIssues unauthorized (401) response"):
    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondUnauthorized()

    val token = ApiToken("invalid-token").get
    val result = LinearClient.listRecentIssues("team-123", 5, token, testBackend)

    assert(result.isLeft, "Expected Left for 401")
    val error = result.left.getOrElse("")
    assert(error.contains("token") || error.contains("expired"), s"Expected token error, got: $error")

  test("listRecentIssues network error"):
    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondServerError()

    val token = ApiToken("test-token").get
    val result = LinearClient.listRecentIssues("team-123", 5, token, testBackend)

    assert(result.isLeft, "Expected Left for network error")
    val error = result.left.getOrElse("")
    assert(error.contains("API error") || error.contains("500"), s"Expected API error, got: $error")

  // --- buildSearchIssuesQuery tests ---

  test("buildSearchIssuesQuery with query and default limit (10)"):
    val query = LinearClient.buildSearchIssuesQuery("dashboard")

    // Should be valid JSON with GraphQL query
    assert(query.contains("\"query\""))
    assert(query.contains("issueSearch(query: \\\"dashboard\\\", first: 10)"))
    assert(query.contains("nodes"))
    assert(query.contains("identifier"))
    assert(query.contains("title"))
    assert(query.contains("state"))

  test("buildSearchIssuesQuery with custom limit"):
    val query = LinearClient.buildSearchIssuesQuery("refactor", 5)

    assert(query.contains("issueSearch(query: \\\"refactor\\\", first: 5)"))

  // --- parseSearchIssuesResponse tests ---

  test("parseSearchIssuesResponse with valid response"):
    val jsonResponse = """{
      "data": {
        "issueSearch": {
          "nodes": [
            {
              "identifier": "IW-123",
              "title": "Dashboard refactoring",
              "state": { "name": "In Progress" }
            },
            {
              "identifier": "IW-88",
              "title": "Dashboard search feature",
              "state": { "name": "Todo" }
            },
            {
              "identifier": "IW-55",
              "title": "Update dashboard UI",
              "state": { "name": "Done" }
            }
          ]
        }
      }
    }"""

    val result = LinearClient.parseSearchIssuesResponse(jsonResponse)

    assert(result.isRight, s"Expected Right but got $result")
    val issues = result.getOrElse(fail("Expected List[Issue]"))
    assertEquals(issues.length, 3)
    assertEquals(issues(0).id, "IW-123")
    assertEquals(issues(0).title, "Dashboard refactoring")
    assertEquals(issues(0).status, "In Progress")
    assertEquals(issues(1).id, "IW-88")
    assertEquals(issues(1).title, "Dashboard search feature")
    assertEquals(issues(2).id, "IW-55")

  test("parseSearchIssuesResponse with empty issues array"):
    val jsonResponse = """{
      "data": {
        "issueSearch": {
          "nodes": []
        }
      }
    }"""

    val result = LinearClient.parseSearchIssuesResponse(jsonResponse)

    assert(result.isRight, s"Expected Right but got $result")
    val issues = result.getOrElse(fail("Expected List[Issue]"))
    assertEquals(issues.length, 0)

  test("parseSearchIssuesResponse with missing fields"):
    val jsonResponse = """{
      "data": {
        "issueSearch": {
          "nodes": [
            {
              "identifier": "IW-123",
              "title": "Test issue"
            }
          ]
        }
      }
    }"""

    val result = LinearClient.parseSearchIssuesResponse(jsonResponse)

    assert(result.isLeft, "Expected Left for missing state field")
    val error = result.left.getOrElse("")
    assert(error.contains("Failed to parse") || error.contains("state"), s"Expected parse error, got: $error")

  // --- searchIssues tests ---

  test("searchIssues success case (mocked backend)"):
    val jsonResponse = """{
      "data": {
        "issueSearch": {
          "nodes": [
            {
              "identifier": "IW-123",
              "title": "Dashboard refactoring",
              "state": { "name": "In Progress" }
            },
            {
              "identifier": "IW-88",
              "title": "Dashboard search",
              "state": { "name": "Todo" }
            }
          ]
        }
      }
    }"""

    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondAdjust(jsonResponse)

    val token = ApiToken("test-token").get
    val result = LinearClient.searchIssues("dashboard", 10, token, testBackend)

    assert(result.isRight, s"Expected Right but got $result")
    val issues = result.getOrElse(fail("Expected List[Issue]"))
    assertEquals(issues.length, 2)
    assertEquals(issues(0).id, "IW-123")
    assertEquals(issues(0).title, "Dashboard refactoring")

  test("searchIssues unauthorized (401) response"):
    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondUnauthorized()

    val token = ApiToken("invalid-token").get
    val result = LinearClient.searchIssues("dashboard", 10, token, testBackend)

    assert(result.isLeft, "Expected Left for 401")
    val error = result.left.getOrElse("")
    assert(error.contains("token") || error.contains("expired"), s"Expected token error, got: $error")

  test("searchIssues network error"):
    val testBackend = SyncBackendStub
      .whenAnyRequest
      .thenRespondServerError()

    val token = ApiToken("test-token").get
    val result = LinearClient.searchIssues("dashboard", 10, token, testBackend)

    assert(result.isLeft, "Expected Left for network error")
    val error = result.left.getOrElse("")
    assert(error.contains("API error") || error.contains("500"), s"Expected API error, got: $error")
