// PURPOSE: Unit tests for ForgejoClient using mocked HTTP backend
// PURPOSE: Tests HTTP operations without making real API calls using sttp SyncBackendStub

package iw.tests

import iw.core.adapters.{ForgejoClient, CreatedIssue}
import iw.core.model.{ApiToken, Issue, IssueId}
import munit.FunSuite
import sttp.client4.testing.SyncBackendStub
import sttp.model.StatusCode

class ForgejoClientTest extends FunSuite:

  private val baseUrl = "https://forgejo.example.com"
  private val repository = "owner/repo"
  private val token = ApiToken("test-token").get
  private val issueId =
    IssueId.parse("PROJ-123").getOrElse(fail("Invalid issue ID"))

  // --- URL builder tests ---

  test("buildIssueUrl constructs correct endpoint"):
    val url = ForgejoClient.buildIssueUrl(baseUrl, repository, "123")
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/issues/123"
    )

  test("buildIssueUrl strips trailing slash from baseUrl"):
    val url = ForgejoClient.buildIssueUrl(baseUrl + "/", repository, "123")
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/issues/123"
    )

  test("buildCreateIssueUrl constructs correct endpoint"):
    val url = ForgejoClient.buildCreateIssueUrl(baseUrl, repository)
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/issues"
    )

  test("buildCreateIssueUrl strips trailing slash from baseUrl"):
    val url = ForgejoClient.buildCreateIssueUrl(baseUrl + "/", repository)
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/issues"
    )

  test("buildValidateTokenUrl constructs correct endpoint"):
    val url = ForgejoClient.buildValidateTokenUrl(baseUrl)
    assertEquals(url, "https://forgejo.example.com/api/v1/user")

  test("buildValidateTokenUrl strips trailing slash from baseUrl"):
    val url = ForgejoClient.buildValidateTokenUrl(baseUrl + "/")
    assertEquals(url, "https://forgejo.example.com/api/v1/user")

  // --- ID normalization test ---

  test("buildIssueUrl with normalized ID from PROJ-123 targets issues/123"):
    val issueNumber = "PROJ-123".split("-").last
    val url = ForgejoClient.buildIssueUrl(baseUrl, repository, issueNumber)
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/issues/123"
    )

  // --- buildCreateIssueBody tests ---

  test("buildCreateIssueBody produces valid JSON with title and body fields"):
    val body = ForgejoClient.buildCreateIssueBody("My Title", "My Description")
    val parsed = ujson.read(body)
    assertEquals(parsed("title").str, "My Title")
    assertEquals(parsed("body").str, "My Description")
    assertEquals(parsed.obj.keySet, Set("title", "body"))

  test("buildCreateIssueBody handles empty description"):
    val body = ForgejoClient.buildCreateIssueBody("Title", "")
    val parsed = ujson.read(body)
    assertEquals(parsed("title").str, "Title")
    assertEquals(parsed("body").str, "")

  // --- parseFetchIssueResponse tests ---

  test("parseFetchIssueResponse parses happy-path Forgejo issue JSON"):
    val json = """{
      "number": 123,
      "title": "Fix login bug",
      "body": "The login button is broken",
      "state": "open",
      "assignee": { "login": "alice" }
    }"""
    val result = ForgejoClient.parseFetchIssueResponse(json, "PROJ-123")
    assert(result.isRight, s"Expected Right but got $result")
    val issue = result.getOrElse(fail("Expected Issue"))
    assertEquals(issue.id, "PROJ-123")
    assertEquals(issue.title, "Fix login bug")
    assertEquals(issue.status, "open")
    assertEquals(issue.assignee, Some("alice"))
    assertEquals(issue.description, Some("The login button is broken"))

  test("parseFetchIssueResponse yields None assignee when assignee is null"):
    val json = """{
      "number": 123,
      "title": "Fix login bug",
      "body": "Description",
      "state": "open",
      "assignee": null
    }"""
    val result = ForgejoClient.parseFetchIssueResponse(json, "PROJ-123")
    assert(result.isRight, s"Expected Right but got $result")
    assertEquals(result.getOrElse(fail("")).assignee, None)

  test(
    "parseFetchIssueResponse yields None assignee when assignee field is absent"
  ):
    val json = """{
      "number": 123,
      "title": "Fix login bug",
      "body": "Description",
      "state": "open"
    }"""
    val result = ForgejoClient.parseFetchIssueResponse(json, "PROJ-123")
    assert(result.isRight, s"Expected Right but got $result")
    assertEquals(result.getOrElse(fail("")).assignee, None)

  test(
    "parseFetchIssueResponse yields None description when body is empty string"
  ):
    val json = """{
      "number": 123,
      "title": "Fix login bug",
      "body": "",
      "state": "open",
      "assignee": null
    }"""
    val result = ForgejoClient.parseFetchIssueResponse(json, "PROJ-123")
    assert(result.isRight, s"Expected Right but got $result")
    assertEquals(result.getOrElse(fail("")).description, None)

  test("parseFetchIssueResponse yields None description when body is null"):
    val json = """{
      "number": 123,
      "title": "Fix login bug",
      "body": null,
      "state": "open",
      "assignee": null
    }"""
    val result = ForgejoClient.parseFetchIssueResponse(json, "PROJ-123")
    assert(result.isRight, s"Expected Right but got $result")
    assertEquals(result.getOrElse(fail("")).description, None)

  test("parseFetchIssueResponse returns Left when title field is missing"):
    val json = """{
      "number": 123,
      "state": "open"
    }"""
    val result = ForgejoClient.parseFetchIssueResponse(json, "PROJ-123")
    assert(result.isLeft, "Expected Left for missing title")
    assertEquals(
      result.left.getOrElse(""),
      "Malformed response: missing 'title' field"
    )

  test("parseFetchIssueResponse returns Left when state field is missing"):
    val json = """{
      "number": 123,
      "title": "Fix login bug"
    }"""
    val result = ForgejoClient.parseFetchIssueResponse(json, "PROJ-123")
    assert(result.isLeft, "Expected Left for missing state")
    assertEquals(
      result.left.getOrElse(""),
      "Malformed response: missing 'state' field"
    )

  test(
    "parseFetchIssueResponse yields None description when body key is absent"
  ):
    val json = """{
      "number": 123,
      "title": "Fix login bug",
      "state": "open"
    }"""
    val result = ForgejoClient.parseFetchIssueResponse(json, "PROJ-123")
    assert(result.isRight, s"Expected Right but got $result")
    assertEquals(result.getOrElse(fail("")).description, None)

  test("parseFetchIssueResponse returns Left for malformed JSON"):
    val result =
      ForgejoClient.parseFetchIssueResponse("{ invalid json }", "PROJ-123")
    assert(result.isLeft, "Expected Left for malformed JSON")
    val error = result.left.getOrElse("")
    assert(
      error.contains("parse") || error.contains("Failed"),
      s"Expected parse error, got: $error"
    )

  // --- parseCreateIssueResponse tests ---

  test("parseCreateIssueResponse parses number and html_url"):
    val json = """{
      "number": 42,
      "html_url": "https://forgejo.example.com/owner/repo/issues/42"
    }"""
    val result = ForgejoClient.parseCreateIssueResponse(json)
    assert(result.isRight, s"Expected Right but got $result")
    val created = result.getOrElse(fail("Expected CreatedIssue"))
    assertEquals(created.id, "42")
    assertEquals(
      created.url,
      "https://forgejo.example.com/owner/repo/issues/42"
    )

  test("parseCreateIssueResponse returns Left for malformed JSON"):
    val result = ForgejoClient.parseCreateIssueResponse("{ bad json }")
    assert(result.isLeft, "Expected Left for malformed JSON")

  // --- fetchIssue tests ---

  test("fetchIssue happy path returns Right(Issue) with full ID preserved"):
    val json = """{
      "number": 123,
      "title": "Test Issue",
      "body": "A description",
      "state": "open",
      "assignee": { "login": "bob" }
    }"""
    val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust(json)
    val result =
      ForgejoClient.fetchIssue(issueId, repository, baseUrl, token, backend)

    assert(result.isRight, s"Expected Right but got $result")
    val issue = result.getOrElse(fail("Expected Issue"))
    assertEquals(issue.id, "PROJ-123")
    assertEquals(issue.title, "Test Issue")
    assertEquals(issue.status, "open")
    assertEquals(issue.assignee, Some("bob"))
    assertEquals(issue.description, Some("A description"))

  test("fetchIssue returns Left on 401 Unauthorized"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondUnauthorized()
    val result =
      ForgejoClient.fetchIssue(issueId, repository, baseUrl, token, backend)

    assert(result.isLeft, "Expected Left for 401")
    val error = result.left.getOrElse("")
    assert(
      error.contains("token") || error.contains("expired"),
      s"Expected token error, got: $error"
    )

  test("fetchIssue returns Left on 404 Not Found with issue ID in message"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondNotFound()
    val result =
      ForgejoClient.fetchIssue(issueId, repository, baseUrl, token, backend)

    assert(result.isLeft, "Expected Left for 404")
    val error = result.left.getOrElse("")
    assert(
      error.contains("not found") || error.contains("PROJ-123"),
      s"Expected not-found error, got: $error"
    )

  test("fetchIssue returns Left on 500 Server Error"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondServerError()
    val result =
      ForgejoClient.fetchIssue(issueId, repository, baseUrl, token, backend)

    assert(result.isLeft, "Expected Left for 500")
    val error = result.left.getOrElse("")
    assert(
      error.contains("API error") || error.contains("500"),
      s"Expected API error, got: $error"
    )

  test("fetchIssue returns Left for malformed JSON response"):
    val backend =
      SyncBackendStub.whenAnyRequest.thenRespondAdjust("{ invalid json }")
    val result =
      ForgejoClient.fetchIssue(issueId, repository, baseUrl, token, backend)

    assert(result.isLeft, "Expected Left for malformed JSON")
    val error = result.left.getOrElse("")
    assert(
      error.contains("parse") || error.contains("Failed"),
      s"Expected parse error, got: $error"
    )

  // --- createIssue tests ---

  test("createIssue happy path with 201 Created returns Right(CreatedIssue)"):
    val json = """{
      "number": 42,
      "html_url": "https://forgejo.example.com/owner/repo/issues/42"
    }"""
    val backend =
      SyncBackendStub.whenAnyRequest.thenRespondAdjust(json, StatusCode.Created)
    val result = ForgejoClient.createIssue(
      repository,
      "New Issue",
      "Details here",
      baseUrl,
      token,
      backend
    )

    assert(result.isRight, s"Expected Right but got $result")
    val created = result.getOrElse(fail("Expected CreatedIssue"))
    assertEquals(created.id, "42")
    assertEquals(
      created.url,
      "https://forgejo.example.com/owner/repo/issues/42"
    )

  test("createIssue returns Left on 401 Unauthorized"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondUnauthorized()
    val result = ForgejoClient.createIssue(
      repository,
      "New Issue",
      "Details",
      baseUrl,
      token,
      backend
    )

    assert(result.isLeft, "Expected Left for 401")
    val error = result.left.getOrElse("")
    assert(
      error.contains("token") || error.contains("expired"),
      s"Expected token error, got: $error"
    )

  test("createIssue returns Left on 500 Server Error"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondServerError()
    val result = ForgejoClient.createIssue(
      repository,
      "New Issue",
      "Details",
      baseUrl,
      token,
      backend
    )

    assert(result.isLeft, "Expected Left for 500")
    val error = result.left.getOrElse("")
    assert(
      error.contains("API error") || error.contains("500"),
      s"Expected API error, got: $error"
    )

  // --- validateToken tests ---

  test("validateToken returns true for 200 OK"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust("{}")
    val result = ForgejoClient.validateToken(baseUrl, token, backend)
    assertEquals(result, true)

  test("validateToken returns false for 401 Unauthorized"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondUnauthorized()
    val result = ForgejoClient.validateToken(baseUrl, token, backend)
    assertEquals(result, false)

  test("validateToken returns false for 500 Server Error"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondServerError()
    val result = ForgejoClient.validateToken(baseUrl, token, backend)
    assertEquals(result, false)

  test("createIssue returns Left on 404 repository not found"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondNotFound()
    val result = ForgejoClient.createIssue(
      repository,
      "New Issue",
      "Details",
      baseUrl,
      token,
      backend
    )
    assert(result.isLeft, "Expected Left for 404")
    val error = result.left.getOrElse("")
    assert(
      error.contains("API error") || error.contains("404"),
      s"Expected API error, got: $error"
    )

  test("createIssue returns Left on 422 Unprocessable Entity"):
    val backend =
      SyncBackendStub.whenAnyRequest.thenRespondAdjust(
        "Unprocessable Entity",
        StatusCode.UnprocessableEntity
      )
    val result = ForgejoClient.createIssue(
      repository,
      "New Issue",
      "Details",
      baseUrl,
      token,
      backend
    )
    assert(result.isLeft, "Expected Left for 422")
    val error = result.left.getOrElse("")
    assert(
      error.contains("API error") || error.contains("422"),
      s"Expected API error, got: $error"
    )
