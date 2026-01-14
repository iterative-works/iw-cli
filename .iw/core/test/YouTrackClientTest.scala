// PURPOSE: Unit tests for YouTrack listRecentIssues functionality
// PURPOSE: Tests URL building and response parsing for recent issues endpoint
package iw.tests

import iw.core.*
import munit.FunSuite

class YouTrackClientTest extends FunSuite:

  test("buildListRecentIssuesUrl returns correct URL with default limit (5)"):
    val baseUrl = "https://youtrack.example.com"
    val url = YouTrackClient.buildListRecentIssuesUrl(baseUrl, 5)

    assert(url.contains("https://youtrack.example.com/api/issues"))
    assert(url.contains("fields=idReadable,summary,customFields(name,value(name))"))
    assert(url.contains("$top=5"))
    assert(url.contains("$orderBy=created%20desc"))

  test("buildListRecentIssuesUrl encodes spaces in orderBy parameter"):
    val baseUrl = "https://youtrack.example.com"
    val url = YouTrackClient.buildListRecentIssuesUrl(baseUrl, 10)

    // Verify space is encoded as %20 in "created desc"
    assert(url.contains("$orderBy=created%20desc"))
    assert(!url.contains("created desc"))

  test("parseListRecentIssuesResponse parses valid JSON array with multiple issues"):
    val json = """[
      {
        "idReadable": "PROJ-1",
        "summary": "First issue",
        "customFields": [
          {"name": "State", "value": {"name": "Open"}}
        ]
      },
      {
        "idReadable": "PROJ-2",
        "summary": "Second issue",
        "customFields": [
          {"name": "State", "value": {"name": "In Progress"}}
        ]
      }
    ]"""

    val result = YouTrackClient.parseListRecentIssuesResponse(json)
    assert(result.isRight)

    val issues = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issues.length, 2)

    assertEquals(issues(0).id, "PROJ-1")
    assertEquals(issues(0).title, "First issue")
    assertEquals(issues(0).status, "Open")

    assertEquals(issues(1).id, "PROJ-2")
    assertEquals(issues(1).title, "Second issue")
    assertEquals(issues(1).status, "In Progress")

  test("parseListRecentIssuesResponse returns empty list for empty JSON array"):
    val json = """[]"""

    val result = YouTrackClient.parseListRecentIssuesResponse(json)
    assert(result.isRight)

    val issues = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issues.length, 0)

  test("parseListRecentIssuesResponse extracts State from customFields"):
    val json = """[
      {
        "idReadable": "PROJ-100",
        "summary": "Test issue",
        "customFields": [
          {"name": "Priority", "value": {"name": "High"}},
          {"name": "State", "value": {"name": "Done"}},
          {"name": "Type", "value": {"name": "Bug"}}
        ]
      }
    ]"""

    val result = YouTrackClient.parseListRecentIssuesResponse(json)
    assert(result.isRight)

    val issues = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issues(0).status, "Done")

  test("parseListRecentIssuesResponse returns Unknown for missing State"):
    val json = """[
      {
        "idReadable": "PROJ-200",
        "summary": "No state issue",
        "customFields": []
      }
    ]"""

    val result = YouTrackClient.parseListRecentIssuesResponse(json)
    assert(result.isRight)

    val issues = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issues(0).status, "Unknown")

  test("parseListRecentIssuesResponse returns error for malformed JSON"):
    val json = """{ invalid json }"""

    val result = YouTrackClient.parseListRecentIssuesResponse(json)
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Failed to parse") || msg.contains("parse")
    ), s"Expected meaningful error message, got: ${result.left.getOrElse("")}")
