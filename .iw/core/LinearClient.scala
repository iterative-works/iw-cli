// PURPOSE: Linear API client for token validation and issue operations
// PURPOSE: Provides validateToken to check if LINEAR_API_TOKEN is valid

package iw.core

import sttp.client4.quick.*
import sttp.model.StatusCode

case class CreatedIssue(id: String, url: String)

object LinearClient:
  private val apiUrl = "https://api.linear.app/graphql"

  def validateToken(token: ApiToken): Boolean =
    try
      // Simple GraphQL query to validate token - just fetch viewer info
      val query = """{"query":"{ viewer { id } }"}"""

      val response = quickRequest
        .post(uri"$apiUrl")
        .header("Authorization", token.value)
        .header("Content-Type", "application/json")
        .body(query)
        .send()

      // Valid token returns 200, invalid returns 401
      response.code == StatusCode.Ok
    catch
      case _: Exception => false

  def fetchIssue(issueId: IssueId, token: ApiToken): Either[String, Issue] =
    try
      val query = buildLinearQuery(issueId)

      val response = quickRequest
        .post(uri"$apiUrl")
        .header("Authorization", token.value)
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

  def buildCreateIssueMutation(title: String, description: String, teamId: String): String =
    // Linear API mutation reference: https://developers.linear.app/docs/graphql/mutations#issuecreate
    // Escape quotes in user input
    val escapedTitle = title.replace("\\", "\\\\").replace("\"", "\\\"")
    val escapedDescription = description.replace("\\", "\\\\").replace("\"", "\\\"")

    val graphql = s"""{
      "query": "mutation { issueCreate(input: { title: \\"$escapedTitle\\", description: \\"$escapedDescription\\", teamId: \\"$teamId\\" }) { success issue { id url } } }"
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

  def parseCreateIssueResponse(json: String): Either[String, CreatedIssue] =
    try
      import upickle.default.*

      val parsed = ujson.read(json)

      // GraphQL response structure:
      // Success: {"data": {"issueCreate": {"success": true, "issue": {"id": "...", "url": "..."}}}}
      // Error:   {"errors": [{"message": "..."}]}

      // Check for GraphQL errors
      if parsed.obj.contains("errors") then
        val errors = parsed("errors").arr
        if errors.nonEmpty then
          val errorMessage = errors.head("message").str
          return Left(s"Linear API error: $errorMessage")

      // Check for data field
      if !parsed.obj.contains("data") then
        return Left("Malformed response: missing 'data' field")

      val data = parsed("data")
      if !data.obj.contains("issueCreate") then
        return Left("Malformed response: missing 'issueCreate' field")

      val issueCreate = data("issueCreate")
      if !issueCreate.obj.contains("issue") then
        return Left("Malformed response: missing 'issue' field")

      val issue = issueCreate("issue")
      if !issue.obj.contains("id") then
        return Left("Malformed response: missing 'id' field")
      if !issue.obj.contains("url") then
        return Left("Malformed response: missing 'url' field")

      val id = issue("id").str
      val url = issue("url").str

      Right(CreatedIssue(id, url))
    catch
      case e: Exception => Left(s"Failed to parse create issue response: ${e.getMessage}")

  /** Create a new Linear issue via GraphQL mutation.
    *
    * @param title Issue title
    * @param description Issue description
    * @param teamId Linear team UUID
    * @param token Valid Linear API token
    * @return Right(CreatedIssue) on success, Left(error message) on failure
    */
  def createIssue(title: String, description: String, teamId: String, token: ApiToken): Either[String, CreatedIssue] =
    try
      val mutation = buildCreateIssueMutation(title, description, teamId)

      val response = quickRequest
        .post(uri"$apiUrl")
        .header("Authorization", token.value)
        .header("Content-Type", "application/json")
        .body(mutation)
        .send()

      response.code match
        case StatusCode.Ok =>
          parseCreateIssueResponse(response.body)
        case StatusCode.Unauthorized =>
          Left("API token is invalid or expired")
        case _ =>
          Left(s"Linear API error: ${response.code}")
    catch
      case e: Exception => Left(s"Network error: ${e.getMessage}")
