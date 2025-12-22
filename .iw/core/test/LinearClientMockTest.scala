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
