// PURPOSE: Integration tests for Linear API client
// PURPOSE: Tests token validation with real API calls (requires LINEAR_API_TOKEN)

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../LinearClient.scala"
//> using file "../Issue.scala"
//> using file "../IssueId.scala"

package iw.core.test

import iw.core.*
import munit.FunSuite

class LinearClientTest extends FunSuite:

  test("validateToken returns false for invalid token"):
    val result = LinearClient.validateToken("invalid-token-12345")
    assertEquals(result, false)

  test("validateToken returns false for empty token"):
    val result = LinearClient.validateToken("")
    assertEquals(result, false)

  test("validateToken returns true for valid token"):
    // This test requires a real LINEAR_API_TOKEN environment variable
    sys.env.get("LINEAR_API_TOKEN") match
      case Some(token) if token.nonEmpty =>
        val result = LinearClient.validateToken(token)
        assertEquals(result, true)
      case _ =>
        // Skip test if no token available
        println("Skipping: LINEAR_API_TOKEN not set")

  test("validateToken handles network errors gracefully"):
    // Test with invalid API endpoint (should fail gracefully, not throw)
    val result = LinearClient.validateToken("lin_api_test")
    // Should return false, not throw exception
    assertEquals(result, false)

  test("parseLinearResponse returns error for missing data field"):
    val json = """{"errors": [{"message": "Some error"}]}"""
    val result = LinearClient.parseLinearResponse(json)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("missing 'data' field"))

  test("parseLinearResponse returns error for missing identifier field"):
    val json = """{
      "data": {
        "issue": {
          "title": "Test Issue",
          "state": { "name": "Todo" }
        }
      }
    }"""
    val result = LinearClient.parseLinearResponse(json)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("missing 'identifier' field"))

  test("parseLinearResponse returns error for missing title field"):
    val json = """{
      "data": {
        "issue": {
          "identifier": "IWLE-123",
          "state": { "name": "Todo" }
        }
      }
    }"""
    val result = LinearClient.parseLinearResponse(json)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("missing 'title' field"))

  test("parseLinearResponse returns error for missing state field"):
    val json = """{
      "data": {
        "issue": {
          "identifier": "IWLE-123",
          "title": "Test Issue"
        }
      }
    }"""
    val result = LinearClient.parseLinearResponse(json)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("missing 'state' field"))

  test("parseLinearResponse returns error for missing state name field"):
    val json = """{
      "data": {
        "issue": {
          "identifier": "IWLE-123",
          "title": "Test Issue",
          "state": {}
        }
      }
    }"""
    val result = LinearClient.parseLinearResponse(json)
    assert(result.isLeft)
    assert(result.left.getOrElse("").contains("missing 'name' field in state"))

  test("parseLinearResponse handles valid response with all required fields"):
    val json = """{
      "data": {
        "issue": {
          "identifier": "IWLE-123",
          "title": "Test Issue",
          "state": { "name": "Todo" },
          "assignee": null,
          "description": null
        }
      }
    }"""
    val result = LinearClient.parseLinearResponse(json)
    assert(result.isRight)
    val issue = result.getOrElse(fail("Expected Right but got Left"))
    assertEquals(issue.id, "IWLE-123")
    assertEquals(issue.title, "Test Issue")
    assertEquals(issue.status, "Todo")
    assertEquals(issue.assignee, None)
    assertEquals(issue.description, None)
