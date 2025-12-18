// PURPOSE: Unit tests for LinearClient createIssue functionality
// PURPOSE: Tests CreatedIssue case class, parseCreateIssueResponse, and buildCreateIssueMutation

package iw.tests

import iw.core.*
import munit.FunSuite

class LinearClientCreateIssueTest extends FunSuite:

  test("parseCreateIssueResponse handles valid response"):
    val json = """{"data":{"issueCreate":{"success":true,"issue":{"id":"abc123","url":"https://linear.app/issue/abc123"}}}}"""
    val result = LinearClient.parseCreateIssueResponse(json)
    assert(result.isRight, "Expected Right but got Left")
    val created = result.getOrElse(fail("Expected CreatedIssue"))
    assertEquals(created.id, "abc123")
    assertEquals(created.url, "https://linear.app/issue/abc123")

  test("parseCreateIssueResponse handles API error"):
    val json = """{"errors":[{"message":"Invalid team"}]}"""
    val result = LinearClient.parseCreateIssueResponse(json)
    assert(result.isLeft, "Expected Left but got Right")
    val error = result.left.getOrElse("")
    assert(error.contains("Invalid team"), s"Expected error to contain 'Invalid team', got: $error")

  test("parseCreateIssueResponse handles missing url field"):
    val json = """{"data":{"issueCreate":{"success":true,"issue":{"id":"abc123"}}}}"""
    val result = LinearClient.parseCreateIssueResponse(json)
    assert(result.isLeft, "Expected Left for missing url field")
    val error = result.left.getOrElse("")
    assert(error.contains("url") || error.contains("field"), s"Expected error about missing field, got: $error")

  test("buildCreateIssueMutation creates valid GraphQL mutation"):
    val mutation = LinearClient.buildCreateIssueMutation(
      title = "Test Issue",
      description = "Test Description",
      teamId = "team-123"
    )

    // Verify it's a GraphQL mutation
    assert(mutation.contains("mutation"), "Mutation should contain 'mutation' keyword")
    assert(mutation.contains("issueCreate"), "Mutation should contain 'issueCreate'")

    // Verify input fields are present
    assert(mutation.contains("title"), "Mutation should contain 'title' field")
    assert(mutation.contains("description"), "Mutation should contain 'description' field")
    assert(mutation.contains("teamId"), "Mutation should contain 'teamId' field")

    // Verify actual values are present
    assert(mutation.contains("Test Issue"), "Mutation should contain the title value")
    assert(mutation.contains("Test Description"), "Mutation should contain the description value")
    assert(mutation.contains("team-123"), "Mutation should contain the teamId value")

    // Verify response fields are requested
    assert(mutation.contains("success"), "Mutation should request 'success' field")
    assert(mutation.contains("issue"), "Mutation should request 'issue' field")
    assert(mutation.contains("id"), "Mutation should request 'id' field")
    assert(mutation.contains("url"), "Mutation should request 'url' field")

  test("buildCreateIssueMutation escapes quotes in title"):
    val mutation = LinearClient.buildCreateIssueMutation(
      title = """Issue with "quotes" in title""",
      description = "Normal description",
      teamId = "team-123"
    )

    // The mutation should contain escaped quotes
    assert(mutation.contains("\\\""), "Mutation should escape quotes in title")

  test("createIssue returns Left for invalid token"):
    // Test with an invalid token (should fail gracefully)
    val token = ApiToken("invalid-token-12345").get
    val result = LinearClient.createIssue(
      title = "Test Issue",
      description = "Test Description",
      teamId = "team-123",
      token = token
    )

    // Should return Left with error message, not throw exception
    assert(result.isLeft, "Expected Left for invalid token")
    val error = result.left.getOrElse("")
    assert(error.nonEmpty, "Error message should not be empty")
