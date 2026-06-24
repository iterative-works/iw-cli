// PURPOSE: Forgejo REST API client for issue read and create operations
// PURPOSE: Provides fetchIssue, createIssue and validateToken over injectable HTTP backend

package iw.core.adapters

import iw.core.model.{Issue, IssueId, ApiToken}

import sttp.client4.{SyncBackend, DefaultSyncBackend, basicRequest, UriContext}
import sttp.model.StatusCode

object ForgejoClient:

  private def defaultBackend: SyncBackend = DefaultSyncBackend()

  def buildIssueUrl(
      baseUrl: String,
      repository: String,
      issueNumber: String
  ): String =
    val base = baseUrl.stripSuffix("/")
    s"$base/api/v1/repos/$repository/issues/$issueNumber"

  def buildCreateIssueUrl(baseUrl: String, repository: String): String =
    val base = baseUrl.stripSuffix("/")
    s"$base/api/v1/repos/$repository/issues"

  def buildValidateTokenUrl(baseUrl: String): String =
    s"${baseUrl.stripSuffix("/")}/api/v1/user"

  def buildCreateIssueBody(title: String, description: String): String =
    import upickle.default.*
    ujson.write(ujson.Obj("title" -> title, "body" -> description))

  def parseFetchIssueResponse(
      json: String,
      issueIdValue: String
  ): Either[String, Issue] =
    try
      import ujson.*
      val parsed = read(json)
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

  def parseCreateIssueResponse(json: String): Either[String, CreatedIssue] =
    try
      val parsed = ujson.read(json)
      val number = parsed("number").num.toInt.toString
      val url = parsed("html_url").str
      Right(CreatedIssue(number, url))
    catch
      case e: Exception =>
        Left(s"Failed to parse Forgejo response: ${e.getMessage}")

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
