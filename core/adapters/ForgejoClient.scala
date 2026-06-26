// PURPOSE: Forgejo REST API client for issue, pull request, merge, and CI status operations
// PURPOSE: All operations return Either[String, A]; HTTP backend is injectable for unit testing

package iw.core.adapters

import iw.core.model.{
  CICheckResult,
  CICheckStatus,
  ForgejoUrl,
  ForgePullRequest,
  Issue,
  IssueId,
  ApiToken
}

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

  /** Build the REST API URL for creating a pull request.
    *
    * @param baseUrl
    *   Base URL of the Forgejo instance (trailing slash stripped)
    * @param repository
    *   Repository in owner/repo format
    * @return
    *   Full API endpoint URL
    */
  def buildCreatePullRequestUrl(baseUrl: String, repository: String): String =
    s"${baseUrl.stripSuffix("/")}/api/v1/repos/$repository/pulls"

  /** Build the REST API URL for merging a pull request.
    *
    * @param baseUrl
    *   Base URL of the Forgejo instance (trailing slash stripped)
    * @param repository
    *   Repository in owner/repo format
    * @param index
    *   PR index (number) within the repository
    * @return
    *   Full API endpoint URL
    */
  def buildMergePullRequestUrl(
      baseUrl: String,
      repository: String,
      index: Int
  ): String =
    s"${baseUrl.stripSuffix("/")}/api/v1/repos/$repository/pulls/$index/merge"

  /** Build the REST API URL for fetching combined commit status.
    *
    * @param baseUrl
    *   Base URL of the Forgejo instance (trailing slash stripped)
    * @param repository
    *   Repository in owner/repo format
    * @param sha
    *   Commit SHA to fetch status for
    * @return
    *   Full API endpoint URL
    */
  def buildCommitStatusUrl(
      baseUrl: String,
      repository: String,
      sha: String
  ): String =
    s"${baseUrl.stripSuffix("/")}/api/v1/repos/$repository/commits/$sha/status"

  /** Build the JSON request body for pull request creation.
    *
    * @param headBranch
    *   Branch to merge from
    * @param baseBranch
    *   Branch to merge into
    * @param title
    *   Pull request title
    * @param body
    *   Pull request description body
    * @return
    *   JSON string with head, base, title, body fields
    */
  def buildCreatePullRequestBody(
      headBranch: String,
      baseBranch: String,
      title: String,
      body: String
  ): String =
    ujson.write(
      ujson.Obj(
        "head" -> headBranch,
        "base" -> baseBranch,
        "title" -> title,
        "body" -> body
      )
    )

  /** JSON request body for squash-merging a pull request with branch deletion.
    *
    * Constant: Do=squash and delete_branch_after_merge=true.
    */
  val mergePullRequestBody: String =
    ujson.write(
      ujson.Obj(
        "Do" -> "squash",
        "delete_branch_after_merge" -> true
      )
    )

  /** Extract the numeric pull request index from a Forgejo PR HTML URL.
    *
    * Delegates to [[iw.core.model.ForgejoUrl.extractPullRequestIndex]].
    *
    * @param prUrl
    *   Full PR HTML URL (e.g., "https://codeberg.org/owner/repo/pulls/42")
    * @return
    *   Right(index) on success, Left(error message) if not parseable
    */
  def extractPullRequestIndex(prUrl: String): Either[String, Int] =
    ForgejoUrl.extractPullRequestIndex(prUrl)

  /** Extract the repository (owner/repo) from a Forgejo PR HTML URL.
    *
    * Delegates to [[iw.core.model.ForgejoUrl.extractRepositoryFromPrUrl]].
    *
    * @param prUrl
    *   Full PR HTML URL (e.g., "https://codeberg.org/owner/repo/pulls/42")
    * @return
    *   Right("owner/repo") on success, Left(error message) if not parseable
    */
  def extractRepositoryFromPrUrl(prUrl: String): Either[String, String] =
    ForgejoUrl.extractRepositoryFromPrUrl(prUrl)

  /** Parse the JSON response from the Forgejo PR creation endpoint.
    *
    * @param json
    *   Raw JSON string from Forgejo create-pull-request endpoint
    * @return
    *   Right(ForgePullRequest) on success, Left(error message) on parse failure
    */
  def parseCreatePullRequestResponse(
      json: String
  ): Either[String, ForgePullRequest] =
    try
      val parsed = ujson.read(json)
      val number = parsed("number").num.toInt
      val htmlUrl = parsed("html_url").str
      val headSha = parsed("head")("sha").str
      Right(ForgePullRequest(number, htmlUrl, headSha))
    catch
      case e: Exception =>
        Left(s"Failed to parse Forgejo PR response: ${e.getMessage}")

  /** Parse the JSON response from the Forgejo combined commit status endpoint.
    *
    * Maps each status in the `statuses` array: success → Passed, failure/error
    * → Failed, pending → Pending, other → Unknown. Empty statuses array returns
    * Right(Nil).
    *
    * @param json
    *   Raw JSON string from Forgejo commits/{sha}/status endpoint
    * @return
    *   Right(List[CICheckResult]) on success, Left(error message) on failure
    */
  def parseCommitStatusResponse(
      json: String
  ): Either[String, List[CICheckResult]] =
    try
      val parsed = ujson.read(json)
      val statuses = parsed("statuses").arr.toList
      val results = statuses.map { s =>
        val name = s("context").str
        val state = s("state").str
        val urlOpt =
          if s.obj.contains("target_url") && !s("target_url").isNull then
            val v = s("target_url").str
            if v.isEmpty then None else Some(v)
          else None
        val status = state match
          case "success"           => CICheckStatus.Passed
          case "failure" | "error" => CICheckStatus.Failed
          case "pending"           => CICheckStatus.Pending
          case _                   => CICheckStatus.Unknown
        CICheckResult(name, status, urlOpt)
      }
      Right(results)
    catch
      case e: Exception =>
        Left(s"Failed to parse Forgejo commit status response: ${e.getMessage}")

  /** Create a pull request in a Forgejo repository.
    *
    * @param repository
    *   Repository in owner/repo format
    * @param headBranch
    *   Branch to merge from
    * @param baseBranch
    *   Branch to merge into
    * @param title
    *   Pull request title
    * @param body
    *   Pull request description body
    * @param baseUrl
    *   Base URL of the Forgejo instance
    * @param token
    *   API token for authentication
    * @param backend
    *   HTTP backend (defaults to real HTTP, can be stubbed for testing)
    * @return
    *   Right(ForgePullRequest) on success, Left(error message) on failure
    */
  def createPullRequest(
      repository: String,
      headBranch: String,
      baseBranch: String,
      title: String,
      body: String,
      baseUrl: String,
      token: ApiToken,
      backend: SyncBackend = defaultBackend
  ): Either[String, ForgePullRequest] =
    try
      val requestBody = buildCreatePullRequestBody(
        headBranch,
        baseBranch,
        title,
        body
      )
      val response = basicRequest
        .post(uri"${buildCreatePullRequestUrl(baseUrl, repository)}")
        .header("Authorization", s"token ${token.value}")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .body(requestBody)
        .send(backend)

      response.code match
        case StatusCode.Created | StatusCode.Ok =>
          response.body match
            case Right(body) => parseCreatePullRequestResponse(body)
            case Left(_)     => Left("Empty response body")
        case StatusCode.Unauthorized => Left("API token is invalid or expired")
        case StatusCode.NotFound     =>
          Left(s"Repository $repository not found")
        case _ => Left(s"Forgejo API error: ${response.code}")
    catch case e: Exception => Left(s"Network error: ${e.getMessage}")

  /** Squash-merge a pull request and delete its branch.
    *
    * @param repository
    *   Repository in owner/repo format
    * @param index
    *   PR index (number) within the repository
    * @param baseUrl
    *   Base URL of the Forgejo instance
    * @param token
    *   API token for authentication
    * @param backend
    *   HTTP backend (defaults to real HTTP, can be stubbed for testing)
    * @return
    *   Right(()) on success, Left(error message) on failure
    */
  def mergePullRequest(
      repository: String,
      index: Int,
      baseUrl: String,
      token: ApiToken,
      backend: SyncBackend = defaultBackend
  ): Either[String, Unit] =
    try
      val response = basicRequest
        .post(uri"${buildMergePullRequestUrl(baseUrl, repository, index)}")
        .header("Authorization", s"token ${token.value}")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .body(mergePullRequestBody)
        .send(backend)

      response.code match
        case StatusCode.Ok | StatusCode.NoContent => Right(())
        case StatusCode.Unauthorized => Left("API token is invalid or expired")
        case StatusCode.NotFound     =>
          Left(s"Pull request $index not found in $repository")
        case _ => Left(s"Forgejo API error: ${response.code}")
    catch case e: Exception => Left(s"Network error: ${e.getMessage}")

  /** Fetch combined CI commit status for a given SHA.
    *
    * Maps each status entry to a CICheckResult. An empty statuses array returns
    * Right(Nil), which the PhaseMerge model treats as NoChecksFound.
    *
    * @param repository
    *   Repository in owner/repo format
    * @param sha
    *   Commit SHA to fetch status for
    * @param baseUrl
    *   Base URL of the Forgejo instance
    * @param token
    *   API token for authentication
    * @param backend
    *   HTTP backend (defaults to real HTTP, can be stubbed for testing)
    * @return
    *   Right(List[CICheckResult]) on success, Left(error message) on failure
    */
  def fetchCheckStatuses(
      repository: String,
      sha: String,
      baseUrl: String,
      token: ApiToken,
      backend: SyncBackend = defaultBackend
  ): Either[String, List[CICheckResult]] =
    try
      val response = basicRequest
        .get(uri"${buildCommitStatusUrl(baseUrl, repository, sha)}")
        .header("Authorization", s"token ${token.value}")
        .header("Accept", "application/json")
        .send(backend)

      response.code match
        case StatusCode.Ok =>
          response.body match
            case Right(body) => parseCommitStatusResponse(body)
            case Left(_)     => Left("Empty response body")
        case StatusCode.Unauthorized => Left("API token is invalid or expired")
        case StatusCode.NotFound     =>
          Left(s"Commit SHA or repository not found")
        case _ => Left(s"Forgejo API error: ${response.code}")
    catch case e: Exception => Left(s"Network error: ${e.getMessage}")

  /** Fetch the head commit SHA for a pull request by its index.
    *
    * Used to obtain the SHA for CI status polling; avoids threading the SHA
    * through the shared TrackerOps signature by doing a dedicated lookup.
    *
    * @param repository
    *   Repository in owner/repo format
    * @param prNumber
    *   PR index within the repository
    * @param baseUrl
    *   Base URL of the Forgejo instance
    * @param token
    *   API token for authentication
    * @param backend
    *   HTTP backend (defaults to real HTTP, can be stubbed for testing)
    * @return
    *   Right(sha) on success, Left(error message) on failure
    */
  def fetchPrHeadSha(
      repository: String,
      prNumber: Int,
      baseUrl: String,
      token: ApiToken,
      backend: SyncBackend = defaultBackend
  ): Either[String, String] =
    try
      val url =
        s"${baseUrl.stripSuffix("/")}/api/v1/repos/$repository/pulls/$prNumber"
      val response = basicRequest
        .get(uri"$url")
        .header("Authorization", s"token ${token.value}")
        .header("Accept", "application/json")
        .send(backend)

      response.code match
        case StatusCode.Ok =>
          response.body match
            case Right(body) =>
              try
                val parsed = ujson.read(body)
                Right(parsed("head")("sha").str)
              catch
                case e: Exception =>
                  Left(s"Failed to parse PR head SHA: ${e.getMessage}")
            case Left(_) => Left("Empty response body")
        case StatusCode.Unauthorized => Left("API token is invalid or expired")
        case StatusCode.NotFound     =>
          Left(s"Pull request $prNumber not found in $repository")
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
