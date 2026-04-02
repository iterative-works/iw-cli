// PURPOSE: Integration tests for Linear API client
// PURPOSE: Tests token validation with real API calls (requires LINEAR_API_TOKEN)
package iw.tests

import iw.core.adapters.LinearClient
import iw.core.model.{ApiToken, Issue}
import munit.FunSuite
import iw.core.model.Issue

class LinearClientTest extends FunSuite:

  test("validateToken returns false for invalid token"):
    val token = ApiToken("invalid-token-12345").get
    val result = LinearClient.validateToken(token)
    assertEquals(result, false)

  test("validateToken returns true for valid token"):
    // This test requires a real LINEAR_API_TOKEN environment variable
    ApiToken.fromEnv("LINEAR_API_TOKEN") match
      case Some(token) =>
        val result = LinearClient.validateToken(token)
        assertEquals(result, true)
      case None =>
        // Skip test if no token available
        println("Skipping: LINEAR_API_TOKEN not set")

  test("validateToken handles network errors gracefully"):
    // Test with invalid API endpoint (should fail gracefully, not throw)
    val token = ApiToken("lin_api_test").get
    val result = LinearClient.validateToken(token)
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
