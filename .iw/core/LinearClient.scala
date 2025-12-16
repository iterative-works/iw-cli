// PURPOSE: Linear API client for token validation and issue operations
// PURPOSE: Provides validateToken to check if LINEAR_API_TOKEN is valid

package iw.core

import sttp.client4.quick.*
import sttp.model.StatusCode

object LinearClient:
  private val apiUrl = "https://api.linear.app/graphql"

  def validateToken(token: String): Boolean =
    if token.isEmpty then
      return false

    try
      // Simple GraphQL query to validate token - just fetch viewer info
      val query = """{"query":"{ viewer { id } }"}"""

      val response = quickRequest
        .post(uri"$apiUrl")
        .header("Authorization", token)
        .header("Content-Type", "application/json")
        .body(query)
        .send()

      // Valid token returns 200, invalid returns 401
      response.code == StatusCode.Ok
    catch
      case _: Exception => false

  def fetchIssue(issueId: IssueId, token: String): Either[String, Issue] =
    if token.isEmpty then
      return Left("API token is empty")

    try
      val query = buildLinearQuery(issueId)

      val response = quickRequest
        .post(uri"$apiUrl")
        .header("Authorization", token)
        .header("Content-Type", "application/json")
        .body(query)
        .send()

      response.code match
        case StatusCode.Ok =>
          parseLinearResponse(response.body)
        case StatusCode.Unauthorized =>
          Left("API token is invalid or expired")
        case _ =>
          Left(s"Linear API error: ${response.code}")
    catch
      case e: Exception => Left(s"Network error: ${e.getMessage}")

  def buildLinearQuery(issueId: IssueId): String =
    val graphql = s"""{
      "query": "query { issue(id: \\"${issueId.value}\\") { identifier title state { name } assignee { displayName } description } }"
    }"""
    graphql

  def parseLinearResponse(json: String): Either[String, Issue] =
    try
      import upickle.default.*

      val parsed = ujson.read(json)

      if !parsed.obj.contains("data") then
        return Left("Malformed response: missing 'data' field")

      val data = parsed("data")
      if data("issue").isNull then
        return Left("Issue not found")

      val issueData = data("issue")

      if !issueData.obj.contains("identifier") then
        return Left("Malformed response: missing 'identifier' field")
      if !issueData.obj.contains("title") then
        return Left("Malformed response: missing 'title' field")
      if !issueData.obj.contains("state") then
        return Left("Malformed response: missing 'state' field")

      val state = issueData("state")
      if !state.obj.contains("name") then
        return Left("Malformed response: missing 'name' field in state")

      val id = issueData("identifier").str
      val title = issueData("title").str
      val status = state("name").str

      val assignee = if issueData.obj.contains("assignee") && !issueData("assignee").isNull then
        Some(issueData("assignee")("displayName").str)
      else
        None

      val description = if issueData.obj.contains("description") && !issueData("description").isNull then
        val desc = issueData("description").str
        if desc.isEmpty then None else Some(desc)
      else
        None

      Right(Issue(id, title, status, assignee, description))
    catch
      case e: Exception => Left(s"Failed to parse Linear response: ${e.getMessage}")
