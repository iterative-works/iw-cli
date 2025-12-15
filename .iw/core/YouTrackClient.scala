// PURPOSE: YouTrack REST API client for issue operations
// PURPOSE: Provides fetchIssue to retrieve issue details from YouTrack

//> using scala 3.3.1
//> using dep com.softwaremill.sttp.client4::core:4.0.0-M18
//> using dep com.lihaoyi::upickle:4.0.2

package iw.core

import sttp.client4.quick.*
import sttp.model.StatusCode

object YouTrackClient:
  def fetchIssue(issueId: IssueId, baseUrl: String, token: String): Either[String, Issue] =
    if token.isEmpty then
      return Left("API token is empty")

    try
      val url = buildYouTrackUrl(baseUrl, issueId)

      val response = quickRequest
        .get(uri"$url")
        .header("Authorization", s"Bearer $token")
        .header("Accept", "application/json")
        .send()

      response.code match
        case StatusCode.Ok =>
          parseYouTrackResponse(response.body)
        case StatusCode.Unauthorized =>
          Left("API token is invalid or expired")
        case StatusCode.NotFound =>
          Left(s"Issue ${issueId.value} not found")
        case _ =>
          Left(s"YouTrack API error: ${response.code}")
    catch
      case e: Exception => Left(s"Network error: ${e.getMessage}")

  def buildYouTrackUrl(baseUrl: String, issueId: IssueId): String =
    val fields = "idReadable,summary,customFields(name,value(name,fullName)),description"
    s"$baseUrl/api/issues/${issueId.value}?fields=$fields"

  def parseYouTrackResponse(json: String): Either[String, Issue] =
    try
      import upickle.default.*

      val parsed = ujson.read(json)

      val id = parsed("idReadable").str
      val title = parsed("summary").str

      val description = if parsed.obj.contains("description") && !parsed("description").isNull then
        val desc = parsed("description").str
        if desc.isEmpty then None else Some(desc)
      else
        None

      // Extract State from customFields
      val customFields = parsed("customFields").arr
      val stateField = customFields.find(field => field("name").str == "State")
      val status = stateField match
        case Some(field) if !field("value").isNull =>
          field("value")("name").str
        case _ => "Unknown"

      // Extract Assignee from customFields
      val assigneeField = customFields.find(field => field("name").str == "Assignee")
      val assignee = assigneeField match
        case Some(field) if !field("value").isNull =>
          Some(field("value")("fullName").str)
        case _ => None

      Right(Issue(id, title, status, assignee, description))
    catch
      case e: Exception => Left(s"Failed to parse YouTrack response: ${e.getMessage}")
