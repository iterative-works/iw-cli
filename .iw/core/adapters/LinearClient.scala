// PURPOSE: Linear API client for token validation and issue operations
// PURPOSE: Provides validateToken to check if LINEAR_API_TOKEN is valid

package iw.core.adapters

import iw.core.model.{Issue, IssueId, ApiToken}

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

  def buildListRecentIssuesQuery(teamId: String, limit: Int = 5): String =
    // Filter out completed and canceled issues (only show active work)
    val graphql = s"""{
      "query": "{ team(id: \\"$teamId\\") { issues(first: $limit, orderBy: createdAt, filter: { state: { type: { nin: [\\"completed\\", \\"canceled\\"] } } }) { nodes { identifier title state { name } } } } }"
    }"""
    graphql

  def buildSearchIssuesQuery(query: String, limit: Int = 10): String =
    val graphql = s"""{
      "query": "{ issueSearch(query: \\"$query\\", first: $limit) { nodes { identifier title state { name } } } }"
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

  def parseListRecentIssuesResponse(json: String): Either[String, List[Issue]] =
    try
      import upickle.default.*

      val parsed = ujson.read(json)

      // Check for data field
      if !parsed.obj.contains("data") then
        return Left("Malformed response: missing 'data' field")

      val data = parsed("data")
      if !data.obj.contains("team") then
        return Left("Malformed response: missing 'team' field")

      val team = data("team")
      if !team.obj.contains("issues") then
        return Left("Malformed response: missing 'issues' field")

      val issues = team("issues")
      if !issues.obj.contains("nodes") then
        return Left("Malformed response: missing 'nodes' field")

      val nodes = issues("nodes").arr

      // Parse each issue node
      val issueList = nodes.map { node =>
        if !node.obj.contains("identifier") then
          throw new Exception("Missing 'identifier' field in issue node")
        if !node.obj.contains("title") then
          throw new Exception("Missing 'title' field in issue node")
        if !node.obj.contains("state") then
          throw new Exception("Missing 'state' field in issue node")

        val state = node("state")
        if !state.obj.contains("name") then
          throw new Exception("Missing 'name' field in state")

        val id = node("identifier").str
        val title = node("title").str
        val status = state("name").str

        // Recent issues don't need assignee or description
        Issue(id, title, status, None, None)
      }.toList

      Right(issueList)
    catch
      case e: Exception => Left(s"Failed to parse list recent issues response: ${e.getMessage}")

  def parseSearchIssuesResponse(json: String): Either[String, List[Issue]] =
    try
      import upickle.default.*

      val parsed = ujson.read(json)

      // Check for data field
      if !parsed.obj.contains("data") then
        return Left("Malformed response: missing 'data' field")

      val data = parsed("data")
      if !data.obj.contains("issueSearch") then
        return Left("Malformed response: missing 'issueSearch' field")

      val issueSearch = data("issueSearch")
      if !issueSearch.obj.contains("nodes") then
        return Left("Malformed response: missing 'nodes' field")

      val nodes = issueSearch("nodes").arr

      // Parse each issue node
      val issueList = nodes.map { node =>
        if !node.obj.contains("identifier") then
          throw new Exception("Missing 'identifier' field in issue node")
        if !node.obj.contains("title") then
          throw new Exception("Missing 'title' field in issue node")
        if !node.obj.contains("state") then
          throw new Exception("Missing 'state' field in issue node")

        val state = node("state")
        if !state.obj.contains("name") then
          throw new Exception("Missing 'name' field in state")

        val id = node("identifier").str
        val title = node("title").str
        val status = state("name").str

        // Search results don't need assignee or description
        Issue(id, title, status, None, None)
      }.toList

      Right(issueList)
    catch
      case e: Exception => Left(s"Failed to parse search issues response: ${e.getMessage}")

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

  /** Fetch recent issues from a Linear team via GraphQL query.
    *
    * @param teamId Linear team UUID
    * @param limit Maximum number of issues to return (default 5)
    * @param token Valid Linear API token
    * @param backend HTTP backend (defaults to real HTTP, can be stubbed for testing)
    * @return Right(List[Issue]) on success, Left(error message) on failure
    */
  def listRecentIssues(
    teamId: String,
    limit: Int = 5,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
  ): Either[String, List[Issue]] =
    try
      val query = buildListRecentIssuesQuery(teamId, limit)

      val response = basicRequest
        .post(uri"$apiUrl")
        .header("Authorization", token.value)
        .header("Content-Type", "application/json")
        .body(query)
        .send(backend)

      response.code match
        case StatusCode.Ok =>
          response.body match
            case Right(body) => parseListRecentIssuesResponse(body)
            case Left(_) => Left("Empty response body")
        case StatusCode.Unauthorized =>
          Left("API token is invalid or expired")
        case _ =>
          Left(s"Linear API error: ${response.code}")
    catch
      case e: Exception => Left(s"Network error: ${e.getMessage}")

  /** Search Linear issues by text query via GraphQL.
    *
    * @param query Search text (searches across title and description)
    * @param limit Maximum number of issues to return (default 10)
    * @param token Valid Linear API token
    * @param backend HTTP backend (defaults to real HTTP, can be stubbed for testing)
    * @return Right(List[Issue]) on success, Left(error message) on failure
    */
  def searchIssues(
    query: String,
    limit: Int = 10,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
  ): Either[String, List[Issue]] =
    try
      val graphqlQuery = buildSearchIssuesQuery(query, limit)

      val response = basicRequest
        .post(uri"$apiUrl")
        .header("Authorization", token.value)
        .header("Content-Type", "application/json")
        .body(graphqlQuery)
        .send(backend)

      response.code match
        case StatusCode.Ok =>
          response.body match
            case Right(body) => parseSearchIssuesResponse(body)
            case Left(_) => Left("Empty response body")
        case StatusCode.Unauthorized =>
          Left("API token is invalid or expired")
        case _ =>
          Left(s"Linear API error: ${response.code}")
    catch
      case e: Exception => Left(s"Network error: ${e.getMessage}")
