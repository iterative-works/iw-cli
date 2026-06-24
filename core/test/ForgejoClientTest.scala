// PURPOSE: Unit tests for ForgejoClient using mocked HTTP backend
// PURPOSE: Tests HTTP operations without making real API calls using sttp SyncBackendStub

package iw.tests

import iw.core.adapters.{ForgejoClient, CreatedIssue}
import iw.core.model.{
  ApiToken,
  CICheckResult,
  CICheckStatus,
  ForgePullRequest,
  Issue,
  IssueId
}
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

  // =========================================================================
  // Pull Request URL/body builders
  // =========================================================================

  test("buildCreatePullRequestUrl constructs correct endpoint"):
    val url = ForgejoClient.buildCreatePullRequestUrl(baseUrl, repository)
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/pulls"
    )

  test("buildCreatePullRequestUrl strips trailing slash from baseUrl"):
    val url =
      ForgejoClient.buildCreatePullRequestUrl(baseUrl + "/", repository)
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/pulls"
    )

  test("buildMergePullRequestUrl constructs correct endpoint with index"):
    val url = ForgejoClient.buildMergePullRequestUrl(baseUrl, repository, 42)
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/pulls/42/merge"
    )

  test("buildMergePullRequestUrl strips trailing slash from baseUrl"):
    val url =
      ForgejoClient.buildMergePullRequestUrl(baseUrl + "/", repository, 7)
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/pulls/7/merge"
    )

  test("buildCommitStatusUrl constructs correct endpoint"):
    val url =
      ForgejoClient.buildCommitStatusUrl(baseUrl, repository, "abc123def")
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/commits/abc123def/status"
    )

  test("buildCommitStatusUrl strips trailing slash from baseUrl"):
    val url =
      ForgejoClient.buildCommitStatusUrl(baseUrl + "/", repository, "abc123")
    assertEquals(
      url,
      "https://forgejo.example.com/api/v1/repos/owner/repo/commits/abc123/status"
    )

  // =========================================================================
  // Pull Request body builders
  // =========================================================================

  test(
    "buildCreatePullRequestBody produces JSON with exact keys: head, base, title, body"
  ):
    val json = ForgejoClient.buildCreatePullRequestBody(
      "feature-branch",
      "main",
      "My PR Title",
      "PR body text"
    )
    val parsed = ujson.read(json)
    assertEquals(parsed("head").str, "feature-branch")
    assertEquals(parsed("base").str, "main")
    assertEquals(parsed("title").str, "My PR Title")
    assertEquals(parsed("body").str, "PR body text")
    assertEquals(parsed.obj.keySet, Set("head", "base", "title", "body"))

  test(
    "mergePullRequestBody produces JSON with Do=squash and delete_branch_after_merge=true"
  ):
    val json = ForgejoClient.mergePullRequestBody
    val parsed = ujson.read(json)
    assertEquals(parsed("Do").str, "squash")
    assertEquals(parsed("delete_branch_after_merge").bool, true)
    assertEquals(parsed.obj.keySet, Set("Do", "delete_branch_after_merge"))

  // =========================================================================
  // extractPullRequestIndex
  // =========================================================================

  test("extractPullRequestIndex parses trailing index from pulls URL"):
    val result = ForgejoClient.extractPullRequestIndex(
      "https://codeberg.org/owner/repo/pulls/42"
    )
    assertEquals(result, Right(42))

  test("extractPullRequestIndex handles multi-digit index"):
    val result = ForgejoClient.extractPullRequestIndex(
      "https://git.example.com/org/project/pulls/1234"
    )
    assertEquals(result, Right(1234))

  test("extractPullRequestIndex returns Left for URL with no /pulls/ segment"):
    val result = ForgejoClient.extractPullRequestIndex(
      "https://github.com/org/repo/pull/42"
    )
    assert(result.isLeft, s"Expected Left for github PR URL, got $result")

  test("extractPullRequestIndex returns Left for malformed URL"):
    val result = ForgejoClient.extractPullRequestIndex("not-a-url")
    assert(result.isLeft, s"Expected Left for malformed URL, got $result")

  test(
    "extractPullRequestIndex returns Left for /pulls/ with no trailing index"
  ):
    val result = ForgejoClient.extractPullRequestIndex(
      "https://codeberg.org/owner/repo/pulls/"
    )
    assert(result.isLeft, s"Expected Left for URL without index, got $result")

  // =========================================================================
  // extractRepositoryFromPrUrl
  // =========================================================================

  test("extractRepositoryFromPrUrl returns owner/repo for simple path"):
    val result = ForgejoClient.extractRepositoryFromPrUrl(
      "https://codeberg.org/owner/repo/pulls/42"
    )
    assertEquals(result, Right("owner/repo"))

  test(
    "extractRepositoryFromPrUrl returns multi-segment path for org/sub/repo"
  ):
    val result = ForgejoClient.extractRepositoryFromPrUrl(
      "https://git.example.com/org/sub/repo/pulls/7"
    )
    assertEquals(result, Right("org/sub/repo"))

  test("extractRepositoryFromPrUrl returns Left for GitHub /pull/ URL"):
    val result = ForgejoClient.extractRepositoryFromPrUrl(
      "https://github.com/owner/repo/pull/42"
    )
    assert(
      result.isLeft,
      s"Expected Left for non-Forgejo /pull/ URL, got $result"
    )

  test(
    "extractRepositoryFromPrUrl returns Left for URL with trailing slash after index"
  ):
    val result = ForgejoClient.extractRepositoryFromPrUrl(
      "https://codeberg.org/owner/repo/pulls/42/"
    )
    assert(
      result.isLeft,
      s"Expected Left for URL with trailing slash, got $result"
    )

  // =========================================================================
  // parseCreatePullRequestResponse
  // =========================================================================

  test(
    "parseCreatePullRequestResponse returns Right(PullRequest) from happy-path JSON"
  ):
    val json = """{
      "number": 99,
      "html_url": "https://codeberg.org/owner/repo/pulls/99",
      "head": { "sha": "abc123def456" }
    }"""
    val result = ForgejoClient.parseCreatePullRequestResponse(json)
    assert(result.isRight, s"Expected Right but got $result")
    val pr = result.getOrElse(fail("Expected PullRequest"))
    assertEquals(pr.number, 99)
    assertEquals(pr.htmlUrl, "https://codeberg.org/owner/repo/pulls/99")
    assertEquals(pr.headSha, "abc123def456")

  test("parseCreatePullRequestResponse returns Left for malformed JSON"):
    val result =
      ForgejoClient.parseCreatePullRequestResponse("{ not valid json }")
    assert(result.isLeft, "Expected Left for malformed JSON")

  test(
    "parseCreatePullRequestResponse returns Left when head.sha is missing"
  ):
    val json = """{
      "number": 10,
      "html_url": "https://codeberg.org/owner/repo/pulls/10"
    }"""
    val result = ForgejoClient.parseCreatePullRequestResponse(json)
    assert(
      result.isLeft,
      s"Expected Left when head.sha missing, got $result"
    )

  // =========================================================================
  // createPullRequest (wired via SyncBackendStub)
  // =========================================================================

  test("createPullRequest happy path 201 returns Right(PullRequest)"):
    val json = """{
      "number": 42,
      "html_url": "https://codeberg.org/owner/repo/pulls/42",
      "head": { "sha": "deadbeef" }
    }"""
    val backend =
      SyncBackendStub.whenAnyRequest.thenRespondAdjust(json, StatusCode.Created)
    val result = ForgejoClient.createPullRequest(
      repository,
      "feature-branch",
      "main",
      "Test PR",
      "body",
      baseUrl,
      token,
      backend
    )
    assert(result.isRight, s"Expected Right but got $result")
    val pr = result.getOrElse(fail("Expected PullRequest"))
    assertEquals(pr.number, 42)
    assertEquals(pr.htmlUrl, "https://codeberg.org/owner/repo/pulls/42")
    assertEquals(pr.headSha, "deadbeef")

  test("createPullRequest happy path 200 OK also returns Right(PullRequest)"):
    val json = """{
      "number": 7,
      "html_url": "https://codeberg.org/owner/repo/pulls/7",
      "head": { "sha": "cafebabe" }
    }"""
    val backend =
      SyncBackendStub.whenAnyRequest.thenRespondAdjust(json, StatusCode.Ok)
    val result = ForgejoClient.createPullRequest(
      repository,
      "feature-branch",
      "main",
      "Test PR",
      "body",
      baseUrl,
      token,
      backend
    )
    assert(result.isRight, s"Expected Right for 200 OK but got $result")
    assertEquals(
      result.getOrElse(fail("Expected PullRequest")).headSha,
      "cafebabe"
    )

  test("createPullRequest returns Left on 401"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondUnauthorized()
    val result = ForgejoClient.createPullRequest(
      repository,
      "feature",
      "main",
      "T",
      "b",
      baseUrl,
      token,
      backend
    )
    assert(result.isLeft, "Expected Left for 401")
    assert(
      result.left.getOrElse("").contains("token") || result.left
        .getOrElse("")
        .contains("expired"),
      s"Expected token error, got: ${result.left.getOrElse("")}"
    )

  test("createPullRequest returns Left on 404"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondNotFound()
    val result = ForgejoClient.createPullRequest(
      repository,
      "feature",
      "main",
      "T",
      "b",
      baseUrl,
      token,
      backend
    )
    assert(result.isLeft, "Expected Left for 404")
    val error = result.left.getOrElse("")
    assert(
      error.contains("not found") || error.contains("404"),
      s"Expected not-found error, got: $error"
    )

  test("createPullRequest returns Left on 422"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      "Unprocessable Entity",
      StatusCode.UnprocessableEntity
    )
    val result = ForgejoClient.createPullRequest(
      repository,
      "feature",
      "main",
      "T",
      "b",
      baseUrl,
      token,
      backend
    )
    assert(result.isLeft, "Expected Left for 422")
    assert(
      result.left.getOrElse("").contains("422"),
      s"Expected 422 error, got: ${result.left.getOrElse("")}"
    )

  test("createPullRequest returns Left on network exception"):
    val backend =
      SyncBackendStub.whenAnyRequest.thenThrow(
        new RuntimeException("connection refused")
      )
    val result = ForgejoClient.createPullRequest(
      repository,
      "feature",
      "main",
      "T",
      "b",
      baseUrl,
      token,
      backend
    )
    assert(result.isLeft, "Expected Left on network error")
    assert(
      result.left.getOrElse("").contains("Network error"),
      s"Expected Network error, got: ${result.left.getOrElse("")}"
    )

  // =========================================================================
  // mergePullRequest (wired via SyncBackendStub)
  // =========================================================================

  test("mergePullRequest happy path 200 returns Right(())"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust("{}")
    val result =
      ForgejoClient.mergePullRequest(repository, 42, baseUrl, token, backend)
    assertEquals(result, Right(()))

  test("mergePullRequest happy path 204 NoContent also returns Right(())"):
    val backend =
      SyncBackendStub.whenAnyRequest.thenRespondAdjust("", StatusCode.NoContent)
    val result =
      ForgejoClient.mergePullRequest(repository, 42, baseUrl, token, backend)
    assertEquals(result, Right(()))

  test("mergePullRequest returns Left on network exception"):
    val backend =
      SyncBackendStub.whenAnyRequest.thenThrow(
        new RuntimeException("connection reset")
      )
    val result =
      ForgejoClient.mergePullRequest(repository, 1, baseUrl, token, backend)
    assert(result.isLeft, "Expected Left on network error")
    assert(
      result.left.getOrElse("").contains("Network error"),
      s"Expected Network error, got: ${result.left.getOrElse("")}"
    )

  test("mergePullRequest returns Left on 401"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondUnauthorized()
    val result =
      ForgejoClient.mergePullRequest(repository, 1, baseUrl, token, backend)
    assert(result.isLeft, "Expected Left for 401")
    assert(
      result.left.getOrElse("").contains("token") || result.left
        .getOrElse("")
        .contains("expired"),
      s"Expected token error, got: ${result.left.getOrElse("")}"
    )

  test("mergePullRequest returns Left on 404"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondNotFound()
    val result =
      ForgejoClient.mergePullRequest(repository, 1, baseUrl, token, backend)
    assert(result.isLeft, "Expected Left for 404")
    val error = result.left.getOrElse("")
    assert(
      error.contains("not found") || error.contains("404"),
      s"Expected not-found error, got: $error"
    )

  test("mergePullRequest returns Left on 500"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondServerError()
    val result =
      ForgejoClient.mergePullRequest(repository, 1, baseUrl, token, backend)
    assert(result.isLeft, "Expected Left for 500")
    val error = result.left.getOrElse("")
    assert(
      error.contains("API error") || error.contains("500"),
      s"Expected API error, got: $error"
    )

  // =========================================================================
  // parseCommitStatusResponse / fetchCheckStatuses
  // =========================================================================

  test(
    "parseCommitStatusResponse maps success state to Passed"
  ):
    val json = """{
      "state": "success",
      "statuses": [
        { "context": "build", "state": "success", "target_url": "https://ci.example.com/1" }
      ]
    }"""
    val result = ForgejoClient.parseCommitStatusResponse(json)
    assert(result.isRight, s"Expected Right but got $result")
    val checks = result.getOrElse(fail("Expected checks"))
    assertEquals(checks.size, 1)
    assertEquals(checks.head.name, "build")
    assertEquals(checks.head.status, CICheckStatus.Passed)
    assertEquals(checks.head.url, Some("https://ci.example.com/1"))

  test(
    "parseCommitStatusResponse maps failure state to Failed"
  ):
    val json = """{
      "state": "failure",
      "statuses": [
        { "context": "lint", "state": "failure", "target_url": "https://ci.example.com/2" }
      ]
    }"""
    val result = ForgejoClient.parseCommitStatusResponse(json)
    assert(result.isRight, s"Expected Right but got $result")
    val checks = result.getOrElse(fail("Expected checks"))
    assertEquals(checks.head.status, CICheckStatus.Failed)

  test(
    "parseCommitStatusResponse maps error state to Failed"
  ):
    val json = """{
      "state": "error",
      "statuses": [
        { "context": "test", "state": "error", "target_url": null }
      ]
    }"""
    val result = ForgejoClient.parseCommitStatusResponse(json)
    assert(result.isRight, s"Expected Right but got $result")
    val checks = result.getOrElse(fail("Expected checks"))
    assertEquals(checks.head.status, CICheckStatus.Failed)
    assertEquals(checks.head.url, None)

  test(
    "parseCommitStatusResponse maps pending state to Pending"
  ):
    val json = """{
      "state": "pending",
      "statuses": [
        { "context": "deploy", "state": "pending", "target_url": "" }
      ]
    }"""
    val result = ForgejoClient.parseCommitStatusResponse(json)
    assert(result.isRight, s"Expected Right but got $result")
    val checks = result.getOrElse(fail("Expected checks"))
    assertEquals(checks.head.status, CICheckStatus.Pending)
    assertEquals(checks.head.url, None)

  test(
    "parseCommitStatusResponse maps unknown state to Unknown"
  ):
    val json = """{
      "state": "warning",
      "statuses": [
        { "context": "scan", "state": "warning", "target_url": null }
      ]
    }"""
    val result = ForgejoClient.parseCommitStatusResponse(json)
    assert(result.isRight, s"Expected Right but got $result")
    val checks = result.getOrElse(fail("Expected checks"))
    assertEquals(checks.head.status, CICheckStatus.Unknown)

  test(
    "parseCommitStatusResponse returns Right(Nil) for empty statuses array"
  ):
    val json = """{ "state": "success", "statuses": [] }"""
    val result = ForgejoClient.parseCommitStatusResponse(json)
    assertEquals(result, Right(Nil))

  test(
    "parseCommitStatusResponse handles multiple statuses"
  ):
    val json = """{
      "state": "failure",
      "statuses": [
        { "context": "build", "state": "success", "target_url": "https://ci.example/1" },
        { "context": "test", "state": "failure", "target_url": "https://ci.example/2" },
        { "context": "lint", "state": "pending", "target_url": null }
      ]
    }"""
    val result = ForgejoClient.parseCommitStatusResponse(json)
    assert(result.isRight, s"Expected Right but got $result")
    val checks = result.getOrElse(fail("Expected checks"))
    assertEquals(checks.size, 3)
    assertEquals(checks(0).status, CICheckStatus.Passed)
    assertEquals(checks(1).status, CICheckStatus.Failed)
    assertEquals(checks(2).status, CICheckStatus.Pending)

  test("parseCommitStatusResponse returns Left for malformed JSON"):
    val result = ForgejoClient.parseCommitStatusResponse("{ bad json }")
    assert(result.isLeft, "Expected Left for malformed JSON")

  test("fetchCheckStatuses happy path returns Right(List[CICheckResult])"):
    val json = """{
      "state": "success",
      "statuses": [
        { "context": "ci/build", "state": "success", "target_url": "https://ci.example/1" }
      ]
    }"""
    val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust(json)
    val result = ForgejoClient.fetchCheckStatuses(
      repository,
      "abc123",
      baseUrl,
      token,
      backend
    )
    assert(result.isRight, s"Expected Right but got $result")
    val checks = result.getOrElse(fail("Expected checks"))
    assertEquals(checks.size, 1)
    assertEquals(checks.head.name, "ci/build")
    assertEquals(checks.head.status, CICheckStatus.Passed)

  test("fetchCheckStatuses returns Left on 401"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondUnauthorized()
    val result = ForgejoClient.fetchCheckStatuses(
      repository,
      "abc123",
      baseUrl,
      token,
      backend
    )
    assert(result.isLeft, "Expected Left for 401")
    assert(
      result.left.getOrElse("").contains("token") || result.left
        .getOrElse("")
        .contains("expired"),
      s"Expected token error, got: ${result.left.getOrElse("")}"
    )

  test("fetchCheckStatuses returns Left on 404"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondNotFound()
    val result = ForgejoClient.fetchCheckStatuses(
      repository,
      "abc123",
      baseUrl,
      token,
      backend
    )
    assert(result.isLeft, "Expected Left for 404")
    val error = result.left.getOrElse("")
    assert(
      error.contains("not found") || error.contains("404"),
      s"Expected not-found error, got: $error"
    )

  test("fetchCheckStatuses returns Left on network exception"):
    val backend =
      SyncBackendStub.whenAnyRequest.thenThrow(
        new RuntimeException("connection refused")
      )
    val result = ForgejoClient.fetchCheckStatuses(
      repository,
      "abc123",
      baseUrl,
      token,
      backend
    )
    assert(result.isLeft, "Expected Left on network error")
    assert(
      result.left.getOrElse("").contains("Network error"),
      s"Expected Network error, got: ${result.left.getOrElse("")}"
    )

  // =========================================================================
  // fetchPrHeadSha (wired via SyncBackendStub)
  // =========================================================================

  test("fetchPrHeadSha happy path 200 returns Right(sha)"):
    val json = """{
      "number": 42,
      "head": { "sha": "abc123def456" }
    }"""
    val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust(json)
    val result =
      ForgejoClient.fetchPrHeadSha(repository, 42, baseUrl, token, backend)
    assertEquals(result, Right("abc123def456"))

  test("fetchPrHeadSha returns Left on 401"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondUnauthorized()
    val result =
      ForgejoClient.fetchPrHeadSha(repository, 1, baseUrl, token, backend)
    assert(result.isLeft, "Expected Left for 401")
    assert(
      result.left.getOrElse("").contains("token") || result.left
        .getOrElse("")
        .contains("expired"),
      s"Expected token error, got: ${result.left.getOrElse("")}"
    )

  test("fetchPrHeadSha returns Left on 404"):
    val backend = SyncBackendStub.whenAnyRequest.thenRespondNotFound()
    val result =
      ForgejoClient.fetchPrHeadSha(repository, 99, baseUrl, token, backend)
    assert(result.isLeft, "Expected Left for 404")
    val error = result.left.getOrElse("")
    assert(
      error.contains("not found") || error.contains("404"),
      s"Expected not-found error, got: $error"
    )

  test("fetchPrHeadSha returns Left on network exception"):
    val backend =
      SyncBackendStub.whenAnyRequest.thenThrow(
        new RuntimeException("connection refused")
      )
    val result =
      ForgejoClient.fetchPrHeadSha(repository, 1, baseUrl, token, backend)
    assert(result.isLeft, "Expected Left on network error")
    assert(
      result.left.getOrElse("").contains("Network error"),
      s"Expected Network error, got: ${result.left.getOrElse("")}"
    )
