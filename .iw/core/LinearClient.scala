// PURPOSE: Linear API client for token validation and issue operations
// PURPOSE: Provides validateToken to check if LINEAR_API_TOKEN is valid

package iw.core

import sttp.client4.{SyncBackend, DefaultSyncBackend, basicRequest, UriContext}
import sttp.model.StatusCode

case class CreatedIssue(id: String, url: String)

object LinearClient:
  private val apiUrl = "https://api.linear.app/graphql"

  // Default backend for production use
  private def defaultBackend: SyncBackend = DefaultSyncBackend()

  /** Escapes special characters for safe JSON string embedding in GraphQL queries.
    * Handles backslashes, quotes, newlines, carriage returns, and tabs.
    */
  private def escapeForJson(s: String): String =
    s.replace("\\", "\\\\")
     .replace("\"", "\\\"")
     .replace("\n", "\\n")
     .replace("\r", "\\r")
     .replace("\t", "\\t")

  def validateToken(token: ApiToken, backend: SyncBackend = defaultBackend): Boolean =
    try
      // Simple GraphQL query to validate token - just fetch viewer info
      val query = """{"query":"{ viewer { id } }"}"""

      val response = basicRequest
        .post(uri"$apiUrl")
        .header("Authorization", token.value)
        .header("Content-Type", "application/json")
        .body(query)
        .send(backend)

      // Valid token returns 200, invalid returns 401
      response.code == StatusCode.Ok
    catch
      case _: Exception => false

  def fetchIssue(issueId: IssueId, token: ApiToken, backend: SyncBackend = defaultBackend): Either[String, Issue] =
    try
      val query = buildLinearQuery(issueId)

      val response = basicRequest
        .post(uri"$apiUrl")
        .header("Authorization", token.value)
        .header("Content-Type", "application/json")
        .body(query)
        .send(backend)

      response.code match
        case StatusCode.Ok =>
          response.body match
            case Right(body) => parseLinearResponse(body)
            case Left(_) => Left("Empty response body")
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

  def buildCreateIssueMutation(title: String, description: String, teamId: String, labelIds: Seq[String] = Seq.empty): String =
    // Linear API mutation reference: https://developers.linear.app/docs/graphql/mutations#issuecreate
    // Escape special characters in user input for JSON string embedding
    val escapedTitle = escapeForJson(title)
    val escapedDescription = escapeForJson(description)

    // Build labelIds array if provided
    val labelIdsField = if labelIds.nonEmpty then
      val ids = labelIds.map(id => s"""\\"$id\\"""").mkString(", ")
      s""", labelIds: [$ids]"""
    else ""

    val graphql = s"""{
      "query": "mutation { issueCreate(input: { title: \\"$escapedTitle\\", description: \\"$escapedDescription\\", teamId: \\"$teamId\\"$labelIdsField }) { success issue { id url } } }"
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
    * @param labelIds Optional list of Linear label UUIDs to apply
    * @param backend HTTP backend (defaults to real HTTP, can be stubbed for testing)
    * @return Right(CreatedIssue) on success, Left(error message) on failure
    */
  def createIssue(
    title: String,
    description: String,
    teamId: String,
    token: ApiToken,
    labelIds: Seq[String] = Seq.empty,
    backend: SyncBackend = defaultBackend
  ): Either[String, CreatedIssue] =
    try
      val mutation = buildCreateIssueMutation(title, description, teamId, labelIds)

      val response = basicRequest
        .post(uri"$apiUrl")
        .header("Authorization", token.value)
        .header("Content-Type", "application/json")
        .body(mutation)
        .send(backend)

      response.code match
        case StatusCode.Ok =>
          response.body match
            case Right(body) => parseCreateIssueResponse(body)
            case Left(_) => Left("Empty response body")
        case StatusCode.Unauthorized =>
          Left("API token is invalid or expired")
        case _ =>
          Left(s"Linear API error: ${response.code}")
    catch
      case e: Exception => Left(s"Network error: ${e.getMessage}")
