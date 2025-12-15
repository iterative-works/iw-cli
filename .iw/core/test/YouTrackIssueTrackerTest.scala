// PURPOSE: Unit tests for YouTrack issue fetching and response parsing
// PURPOSE: Tests YouTrackClient with various response scenarios including customFields

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../YouTrackClient.scala"
//> using file "../Issue.scala"
//> using file "../IssueId.scala"

package iw.core.test

import iw.core.*
import munit.FunSuite

class YouTrackIssueTrackerTest extends FunSuite:

  test("parseYouTrackResponse extracts all fields from valid response"):
    val json = """{
      "idReadable": "IWSD-123",
      "summary": "Fix login bug",
      "description": "Users cannot log in",
      "customFields": [
        {
          "name": "State",
          "value": { "name": "In Progress" }
        },
        {
          "name": "Assignee",
          "value": { "fullName": "John Smith" }
        }
      ]
    }"""

    val result = YouTrackClient.parseYouTrackResponse(json)
    assert(result.isRight)

    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.id, "IWSD-123")
    assertEquals(issue.title, "Fix login bug")
    assertEquals(issue.status, "In Progress")
    assertEquals(issue.assignee, Some("John Smith"))
    assertEquals(issue.description, Some("Users cannot log in"))

  test("parseYouTrackResponse handles missing assignee"):
    val json = """{
      "idReadable": "IWSD-456",
      "summary": "Unassigned task",
      "description": "Description here",
      "customFields": [
        {
          "name": "State",
          "value": { "name": "Todo" }
        }
      ]
    }"""

    val result = YouTrackClient.parseYouTrackResponse(json)
    assert(result.isRight)

    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.assignee, None)

  test("parseYouTrackResponse handles missing description"):
    val json = """{
      "idReadable": "IWSD-789",
      "summary": "No description task",
      "customFields": [
        {
          "name": "State",
          "value": { "name": "Done" }
        },
        {
          "name": "Assignee",
          "value": { "fullName": "Jane Doe" }
        }
      ]
    }"""

    val result = YouTrackClient.parseYouTrackResponse(json)
    assert(result.isRight)

    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.description, None)

  test("parseYouTrackResponse handles null description"):
    val json = """{
      "idReadable": "IWSD-999",
      "summary": "Null desc",
      "description": null,
      "customFields": [
        {
          "name": "State",
          "value": { "name": "Todo" }
        }
      ]
    }"""

    val result = YouTrackClient.parseYouTrackResponse(json)
    assert(result.isRight)

    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.description, None)

  test("parseYouTrackResponse handles empty description"):
    val json = """{
      "idReadable": "IWSD-888",
      "summary": "Empty desc",
      "description": "",
      "customFields": [
        {
          "name": "State",
          "value": { "name": "Todo" }
        }
      ]
    }"""

    val result = YouTrackClient.parseYouTrackResponse(json)
    assert(result.isRight)

    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.description, None)

  test("parseYouTrackResponse returns error for malformed JSON"):
    val json = """{ invalid json }"""

    val result = YouTrackClient.parseYouTrackResponse(json)
    assert(result.isLeft)

  test("parseYouTrackResponse handles missing State field"):
    val json = """{
      "idReadable": "IWSD-111",
      "summary": "No state",
      "description": "test",
      "customFields": []
    }"""

    val result = YouTrackClient.parseYouTrackResponse(json)
    assert(result.isRight)

    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.status, "Unknown")

  test("buildYouTrackUrl creates correct REST URL"):
    val baseUrl = "https://youtrack.e-bs.cz"
    val issueId = IssueId.parse("IWSD-123").getOrElse(fail("Failed to parse issue ID"))
    val url = YouTrackClient.buildYouTrackUrl(baseUrl, issueId)

    assert(url.contains("https://youtrack.e-bs.cz/api/issues/IWSD-123"))
    assert(url.contains("fields="))
    assert(url.contains("idReadable"))
    assert(url.contains("summary"))
    assert(url.contains("customFields"))
    assert(url.contains("description"))
