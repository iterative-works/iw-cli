// PURPOSE: Unit tests for Linear issue fetching and response parsing
// PURPOSE: Tests LinearClient.fetchIssue with various response scenarios
package iw.tests

import iw.core.*
import munit.FunSuite

class LinearIssueTrackerTest extends FunSuite:

  test("parseLinearResponse extracts all fields from valid response"):
    val json = """{
      "data": {
        "issue": {
          "identifier": "IWLE-123",
          "title": "Add user login",
          "state": { "name": "In Progress" },
          "assignee": { "displayName": "Michal Příhoda" },
          "description": "Users need to log in"
        }
      }
    }"""

    val result = LinearClient.parseLinearResponse(json)
    assert(result.isRight)

    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.id, "IWLE-123")
    assertEquals(issue.title, "Add user login")
    assertEquals(issue.status, "In Progress")
    assertEquals(issue.assignee, Some("Michal Příhoda"))
    assertEquals(issue.description, Some("Users need to log in"))

  test("parseLinearResponse handles missing assignee"):
    val json = """{
      "data": {
        "issue": {
          "identifier": "IWLE-456",
          "title": "Unassigned task",
          "state": { "name": "Todo" },
          "assignee": null,
          "description": "Description here"
        }
      }
    }"""

    val result = LinearClient.parseLinearResponse(json)
    assert(result.isRight)

    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.assignee, None)

  test("parseLinearResponse handles missing description"):
    val json = """{
      "data": {
        "issue": {
          "identifier": "IWLE-789",
          "title": "No description task",
          "state": { "name": "Done" },
          "assignee": { "displayName": "Jane Doe" },
          "description": null
        }
      }
    }"""

    val result = LinearClient.parseLinearResponse(json)
    assert(result.isRight)

    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.description, None)

  test("parseLinearResponse handles empty description"):
    val json = """{
      "data": {
        "issue": {
          "identifier": "IWLE-999",
          "title": "Empty desc",
          "state": { "name": "Todo" },
          "assignee": { "displayName": "John" },
          "description": ""
        }
      }
    }"""

    val result = LinearClient.parseLinearResponse(json)
    assert(result.isRight)

    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.description, None)

  test("parseLinearResponse returns error for issue not found"):
    val json = """{
      "data": {
        "issue": null
      }
    }"""

    val result = LinearClient.parseLinearResponse(json)
    assert(result.isLeft)
    assert(result.left.exists(_.contains("not found")))

  test("parseLinearResponse returns error for malformed JSON"):
    val json = """{ invalid json }"""

    val result = LinearClient.parseLinearResponse(json)
    assert(result.isLeft)
    assert(result.left.exists(msg =>
      msg.contains("Failed to parse") || msg.contains("parse")
    ), s"Expected meaningful error message, got: ${result.left.getOrElse("")}")

  test("buildLinearQuery creates valid GraphQL query"):
    val issueId = IssueId.parse("IWLE-123").getOrElse(fail("Failed to parse issue ID"))
    val query = LinearClient.buildLinearQuery(issueId)

    assert(query.contains("query"))
    assert(query.contains("issue"))
    assert(query.contains("IWLE-123"))
    assert(query.contains("identifier"))
    assert(query.contains("title"))
    assert(query.contains("state"))
    assert(query.contains("assignee"))
    assert(query.contains("description"))
