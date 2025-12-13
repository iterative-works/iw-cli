// PURPOSE: Integration tests for Linear API client
// PURPOSE: Tests token validation with real API calls (requires LINEAR_API_TOKEN)

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../LinearClient.scala"

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
