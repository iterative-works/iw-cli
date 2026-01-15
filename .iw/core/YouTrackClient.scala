// PURPOSE: YouTrack REST API client for issue operations
// PURPOSE: Provides fetchIssue to retrieve issue details from YouTrack

package iw.core

import sttp.client4.quick.*
import sttp.model.StatusCode

object YouTrackClient:
  def fetchIssue(issueId: IssueId, baseUrl: String, token: ApiToken): Either[String, Issue] =
    try
      val url = buildYouTrackUrl(baseUrl, issueId)

      val response = quickRequest
        .get(uri"$url")
        .header("Authorization", s"Bearer ${token.value}")
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

      if !parsed.obj.contains("idReadable") then
        return Left("Malformed response: missing 'idReadable' field")
      if !parsed.obj.contains("summary") then
        return Left("Malformed response: missing 'summary' field")
      if !parsed.obj.contains("customFields") then
        return Left("Malformed response: missing 'customFields' field")

      val id = parsed("idReadable").str
      val title = parsed("summary").str

      val description = if parsed.obj.contains("description") && !parsed("description").isNull then
        val desc = parsed("description").str
        if desc.isEmpty then None else Some(desc)
      else
        None

      // Extract State from customFields
      val customFields = parsed("customFields").arr
      val stateField = customFields.find(field =>
        field.obj.contains("name") && field("name").str == "State"
      )
      val status = stateField match
        case Some(field) if field.obj.contains("value") && !field("value").isNull =>
          field("value")("name").str
        case _ => "Unknown"

      // Extract Assignee from customFields
      val assigneeField = customFields.find(field =>
        field.obj.contains("name") && field("name").str == "Assignee"
      )
      val assignee = assigneeField match
        case Some(field) if field.obj.contains("value") && !field("value").isNull =>
          Some(field("value")("fullName").str)
        case _ => None

      Right(Issue(id, title, status, assignee, description))
    catch
      case e: Exception => Left(s"Failed to parse YouTrack response: ${e.getMessage}")

  def buildListRecentIssuesUrl(baseUrl: String, limit: Int): String =
    val fields = "idReadable,summary,customFields(name,value(name))"
    // Filter to unresolved issues only (excludes Done, Closed, etc.)
    s"$baseUrl/api/issues?fields=$fields&query=%23Unresolved&$$top=$limit&$$orderBy=created%20desc"

  def buildSearchIssuesUrl(baseUrl: String, query: String, limit: Int): String =
    val fields = "idReadable,summary,customFields(name,value(name))"
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
    s"$baseUrl/api/issues?fields=$fields&query=$encodedQuery&$$top=$limit"

  def parseListRecentIssuesResponse(json: String): Either[String, List[Issue]] =
    try
      import upickle.default.*

      val parsed = ujson.read(json)

      if !parsed.isInstanceOf[ujson.Arr] then
        Left("Expected JSON array")
      else
        // Parse each issue, collecting errors
        val results = parsed.arr.map(parseIssueFromJson).toList
        // Use foldRight to sequence Either values, returning first error
        results.foldRight[Either[String, List[Issue]]](Right(Nil)) { (item, acc) =>
          for
            issues <- acc
            issue <- item
          yield issue :: issues
        }
    catch
      case e: Exception => Left(s"Failed to parse YouTrack response: ${e.getMessage}")

  private def parseIssueFromJson(issueJson: ujson.Value): Either[String, Issue] =
    if !issueJson.obj.contains("idReadable") then
      Left("Malformed response: missing 'idReadable' field")
    else if !issueJson.obj.contains("summary") then
      Left("Malformed response: missing 'summary' field")
    else if !issueJson.obj.contains("customFields") then
      Left("Malformed response: missing 'customFields' field")
    else
      val id = issueJson("idReadable").str
      val title = issueJson("summary").str

      // Extract State from customFields
      val customFields = issueJson("customFields").arr
      val stateField = customFields.find(field =>
        field.obj.contains("name") && field("name").str == "State"
      )
      val status = stateField match
        case Some(field) if field.obj.contains("value") && !field("value").isNull =>
          field("value")("name").str
        case _ => "Unknown"

      Right(Issue(id, title, status, None, None))

  def listRecentIssues(baseUrl: String, limit: Int = 5, token: ApiToken): Either[String, List[Issue]] =
    try
      val url = buildListRecentIssuesUrl(baseUrl, limit)

      val response = quickRequest
        .get(uri"$url")
        .header("Authorization", s"Bearer ${token.value}")
        .header("Accept", "application/json")
        .send()

      response.code match
        case StatusCode.Ok =>
          parseListRecentIssuesResponse(response.body)
        case StatusCode.Unauthorized =>
          Left("API token is invalid or expired")
        case _ =>
          Left(s"YouTrack API error: ${response.code}")
    catch
      case e: Exception => Left(s"Network error: ${e.getMessage}")

  def searchIssues(baseUrl: String, query: String, limit: Int = 10, token: ApiToken): Either[String, List[Issue]] =
    try
      val url = buildSearchIssuesUrl(baseUrl, query, limit)

      val response = quickRequest
        .get(uri"$url")
        .header("Authorization", s"Bearer ${token.value}")
        .header("Accept", "application/json")
        .send()

      response.code match
        case StatusCode.Ok =>
          parseListRecentIssuesResponse(response.body)
        case StatusCode.Unauthorized =>
          Left("API token is invalid or expired")
        case _ =>
          Left(s"YouTrack API error: ${response.code}")
    catch
      case e: Exception => Left(s"Network error: ${e.getMessage}")
