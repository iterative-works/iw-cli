// PURPOSE: Forgejo REST API client for issue read and create operations
// PURPOSE: Fetches, creates, and validates issues and tokens against a Forgejo instance

package iw.core.adapters

import iw.core.model.{Issue, IssueId, ApiToken}

import sttp.client4.{SyncBackend, DefaultSyncBackend, basicRequest, UriContext}
import sttp.model.StatusCode

object ForgejoClient:

  private def defaultBackend: SyncBackend = DefaultSyncBackend()

  /** Build the REST API URL for fetching a single issue.
    *
    * @param baseUrl
    *   Base URL of the Forgejo instance (trailing slash stripped)
    * @param repository
    *   Repository in owner/repo format
    * @param issueNumber
    *   Numeric issue number as a string
    * @return
    *   Full API endpoint URL
    */
  def buildIssueUrl(
      baseUrl: String,
      repository: String,
      issueNumber: String
  ): String =
    val base = baseUrl.stripSuffix("/")
    s"$base/api/v1/repos/$repository/issues/$issueNumber"

  /** Build the REST API URL for creating an issue.
    *
    * @param baseUrl
    *   Base URL of the Forgejo instance (trailing slash stripped)
    * @param repository
    *   Repository in owner/repo format
    * @return
    *   Full API endpoint URL
    */
  def buildCreateIssueUrl(baseUrl: String, repository: String): String =
    val base = baseUrl.stripSuffix("/")
    s"$base/api/v1/repos/$repository/issues"

  /** Build the REST API URL for validating an API token.
    *
    * @param baseUrl
    *   Base URL of the Forgejo instance (trailing slash stripped)
    * @return
    *   Full API endpoint URL for the authenticated user resource
    */
  def buildValidateTokenUrl(baseUrl: String): String =
    s"${baseUrl.stripSuffix("/")}/api/v1/user"

  /** Build the JSON request body for issue creation.
    *
    * @param title
    *   Issue title
    * @param description
    *   Issue body/description
    * @return
    *   JSON string with title and body fields
    */
  def buildCreateIssueBody(title: String, description: String): String =
    import upickle.default.*
    ujson.write(ujson.Obj("title" -> title, "body" -> description))

  /** Parse the JSON response from the Forgejo issues API into an Issue.
    *
    * @param json
    *   Raw JSON string from Forgejo issue endpoint
    * @param issueIdValue
    *   Full issue ID (e.g. "PROJ-123") preserved in the returned Issue
    * @return
    *   Right(Issue) on success, Left(error message) on missing fields or parse
    *   failure
    */
  def parseFetchIssueResponse(
      json: String,
      issueIdValue: String
  ): Either[String, Issue] =
    try
      import ujson.*
      val parsed = read(json)

      if !parsed.obj.contains("title") then
        Left("Malformed response: missing 'title' field")
      else if !parsed.obj.contains("state") then
        Left("Malformed response: missing 'state' field")
      else
        val title = parsed("title").str
        val status = parsed("state").str

        val assignee =
          if parsed.obj.contains("assignee") && !parsed("assignee").isNull
          then Some(parsed("assignee")("login").str)
          else None

        val description =
          if parsed.obj.contains("body") && !parsed("body").isNull then
            val b = parsed("body").str
            if b.isEmpty then None else Some(b)
          else None

        Right(Issue(issueIdValue, title, status, assignee, description))
    catch
      case e: Exception =>
        Left(s"Failed to parse Forgejo response: ${e.getMessage}")

  /** Parse the JSON response from the Forgejo issue creation endpoint.
    *
    * @param json
    *   Raw JSON string from Forgejo create-issue endpoint
    * @return
    *   Right(CreatedIssue) on success, Left(error message) on parse failure
    */
  def parseCreateIssueResponse(json: String): Either[String, CreatedIssue] =
    try
      val parsed = ujson.read(json)
      val number = parsed("number").num.toInt.toString
      val url = parsed("html_url").str
      Right(CreatedIssue(number, url))
    catch
      case e: Exception =>
        Left(s"Failed to parse Forgejo response: ${e.getMessage}")

  /** Fetch a Forgejo issue by ID.
    *
    * @param issueId
    *   Issue identifier; the numeric part is extracted for the API call
    * @param repository
    *   Repository in owner/repo format
    * @param baseUrl
    *   Base URL of the Forgejo instance
    * @param token
    *   API token for authentication
    * @param backend
    *   HTTP backend (defaults to real HTTP, can be stubbed for testing)
    * @return
    *   Right(Issue) on success, Left(error message) on failure
    */
  def fetchIssue(
      issueId: IssueId,
      repository: String,
      baseUrl: String,
      token: ApiToken,
      backend: SyncBackend = defaultBackend
  ): Either[String, Issue] =
    try
      val issueNumber = issueId.value.split("-").last
      val response = basicRequest
        .get(uri"${buildIssueUrl(baseUrl, repository, issueNumber)}")
        .header("Authorization", s"token ${token.value}")
        .header("Accept", "application/json")
        .send(backend)

      response.code match
        case StatusCode.Ok =>
          response.body match
            case Right(body) => parseFetchIssueResponse(body, issueId.value)
            case Left(_)     => Left("Empty response body")
        case StatusCode.Unauthorized => Left("API token is invalid or expired")
        case StatusCode.NotFound => Left(s"Issue ${issueId.value} not found")
        case _                   => Left(s"Forgejo API error: ${response.code}")
    catch case e: Exception => Left(s"Network error: ${e.getMessage}")

  /** Create a new issue in a Forgejo repository.
    *
    * @param repository
    *   Repository in owner/repo format
    * @param title
    *   Issue title
    * @param description
    *   Issue body/description
    * @param baseUrl
    *   Base URL of the Forgejo instance
    * @param token
    *   API token for authentication
    * @param backend
    *   HTTP backend (defaults to real HTTP, can be stubbed for testing)
    * @return
    *   Right(CreatedIssue) on success, Left(error message) on failure
    */
  def createIssue(
      repository: String,
      title: String,
      description: String,
      baseUrl: String,
      token: ApiToken,
      backend: SyncBackend = defaultBackend
  ): Either[String, CreatedIssue] =
    try
      val body = buildCreateIssueBody(title, description)
      val response = basicRequest
        .post(uri"${buildCreateIssueUrl(baseUrl, repository)}")
        .header("Authorization", s"token ${token.value}")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .body(body)
        .send(backend)

      response.code match
        case StatusCode.Created | StatusCode.Ok =>
          response.body match
            case Right(body) => parseCreateIssueResponse(body)
            case Left(_)     => Left("Empty response body")
        case StatusCode.Unauthorized => Left("API token is invalid or expired")
        case _ => Left(s"Forgejo API error: ${response.code}")
    catch case e: Exception => Left(s"Network error: ${e.getMessage}")

  /** Validate a Forgejo API token by calling the authenticated-user endpoint.
    *
    * @param baseUrl
    *   Base URL of the Forgejo instance
    * @param token
    *   API token to validate
    * @param backend
    *   HTTP backend (defaults to real HTTP, can be stubbed for testing)
    * @return
    *   true if the token is valid (200 OK), false otherwise
    */
  def validateToken(
      baseUrl: String,
      token: ApiToken,
      backend: SyncBackend = defaultBackend
  ): Boolean =
    try
      val response = basicRequest
        .get(uri"${buildValidateTokenUrl(baseUrl)}")
        .header("Authorization", s"token ${token.value}")
        .send(backend)
      response.code == StatusCode.Ok
    catch case _: Exception => false
