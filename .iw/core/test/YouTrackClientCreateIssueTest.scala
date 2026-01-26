// PURPOSE: Unit tests for YouTrackClient createIssue functionality
// PURPOSE: Tests buildCreateIssueUrl, buildCreateIssueBody, and parseCreateIssueResponse

package iw.tests

import iw.core.*
import iw.core.adapters.YouTrackClient
import munit.FunSuite

class YouTrackClientCreateIssueTest extends FunSuite:

  test("buildCreateIssueUrl returns correct endpoint"):
    val url = YouTrackClient.buildCreateIssueUrl("https://example.youtrack.cloud")
    assertEquals(url, "https://example.youtrack.cloud/api/issues")

  test("buildCreateIssueUrl handles trailing slash"):
    val url = YouTrackClient.buildCreateIssueUrl("https://example.youtrack.cloud/")
    assertEquals(url, "https://example.youtrack.cloud/api/issues")

  test("buildCreateIssueBody generates valid JSON with project and summary"):
    val body = YouTrackClient.buildCreateIssueBody(
      project = "PROJ",
      title = "Test Issue",
      description = "Test Description"
    )

    // Parse and verify JSON structure
    val parsed = ujson.read(body)
    assertEquals(parsed("project")("id").str, "PROJ")
    assertEquals(parsed("summary").str, "Test Issue")
    assertEquals(parsed("description").str, "Test Description")

  test("buildCreateIssueBody handles empty description"):
    val body = YouTrackClient.buildCreateIssueBody(
      project = "PROJ",
      title = "Test Issue",
      description = ""
    )

    val parsed = ujson.read(body)
    assertEquals(parsed("project")("id").str, "PROJ")
    assertEquals(parsed("summary").str, "Test Issue")
    // Empty description should still be included
    assertEquals(parsed("description").str, "")

  test("buildCreateIssueBody escapes special characters"):
    val body = YouTrackClient.buildCreateIssueBody(
      project = "PROJ",
      title = """Title with "quotes" and \backslash""",
      description = "Line 1\nLine 2"
    )

    // JSON should be valid and parseable
    val parsed = ujson.read(body)
    assertEquals(parsed("summary").str, """Title with "quotes" and \backslash""")
    assertEquals(parsed("description").str, "Line 1\nLine 2")

  test("parseCreateIssueResponse handles valid response"):
    val json = """{
      "$type": "Issue",
      "idReadable": "PROJ-123",
      "id": "2-123"
    }"""
    val baseUrl = "https://example.youtrack.cloud"

    val result = YouTrackClient.parseCreateIssueResponse(json, baseUrl)
    assert(result.isRight, s"Expected Right but got ${result.left.getOrElse("")}")
    val created = result.getOrElse(fail("Expected CreatedIssue"))
    assertEquals(created.id, "PROJ-123")
    assertEquals(created.url, "https://example.youtrack.cloud/issue/PROJ-123")

  test("parseCreateIssueResponse handles missing idReadable"):
    val json = """{
      "$type": "Issue",
      "id": "2-123"
    }"""
    val baseUrl = "https://example.youtrack.cloud"

    val result = YouTrackClient.parseCreateIssueResponse(json, baseUrl)
    assert(result.isLeft, "Expected Left for missing idReadable")
    val error = result.left.getOrElse("")
    assert(error.contains("idReadable") || error.contains("field"), s"Expected error about missing field, got: $error")

  test("parseCreateIssueResponse handles error response"):
    val json = """{
      "error": "Entity with project id INVALID not found"
    }"""
    val baseUrl = "https://example.youtrack.cloud"

    val result = YouTrackClient.parseCreateIssueResponse(json, baseUrl)
    assert(result.isLeft, "Expected Left for error response")
    val error = result.left.getOrElse("")
    assert(error.contains("INVALID") || error.contains("error"), s"Expected error message, got: $error")

  test("parseCreateIssueResponse handles malformed JSON"):
    val json = "not valid json"
    val baseUrl = "https://example.youtrack.cloud"

    val result = YouTrackClient.parseCreateIssueResponse(json, baseUrl)
    assert(result.isLeft, "Expected Left for malformed JSON")

  test("buildIssueUrl constructs correct issue URL"):
    val url = YouTrackClient.buildIssueUrl("https://example.youtrack.cloud", "PROJ-123")
    assertEquals(url, "https://example.youtrack.cloud/issue/PROJ-123")

  test("buildIssueUrl handles trailing slash in baseUrl"):
    val url = YouTrackClient.buildIssueUrl("https://example.youtrack.cloud/", "PROJ-123")
    assertEquals(url, "https://example.youtrack.cloud/issue/PROJ-123")
