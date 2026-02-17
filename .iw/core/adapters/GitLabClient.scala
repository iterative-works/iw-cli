// PURPOSE: GitLab CLI client for issue management via glab CLI
// PURPOSE: Provides fetchIssue and createIssue for GitLab issue operations

package iw.core.adapters

import iw.core.model.{Issue, IssueId, ApiToken}
import iw.core.dashboard.FeedbackParser

object GitLabClient:

  /** Error types for glab CLI prerequisite validation. */
  enum GlabPrerequisiteError:
    case GlabNotInstalled
    case GlabNotAuthenticated
    case GlabError(message: String)

  /** Validate glab CLI prerequisites before fetching issues.
    *
    * Checks that:
    * 1. glab CLI is installed and available in PATH
    * 2. glab CLI is authenticated (via `glab auth status`)
    *
    * @param repository GitLab repository in owner/project format
    * @param isCommandAvailable Function to check if command exists (injected for testability)
    * @param execCommand Function to execute shell command (injected for testability)
    * @return Right(()) if all prerequisites met, Left(error) otherwise
    */
  def validateGlabPrerequisites(
    repository: String,
    isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
    execCommand: (String, Array[String]) => Either[String, String] =
      (cmd, args) => CommandRunner.execute(cmd, args)
  ): Either[GlabPrerequisiteError, Unit] =
    // Check glab CLI is installed
    if !isCommandAvailable("glab") then
      Left(GlabPrerequisiteError.GlabNotInstalled)
    else
      // Check glab authentication
      execCommand("glab", Array("auth", "status")) match
      case Left(error) =>
        Left(GlabPrerequisiteError.GlabNotAuthenticated)
      case Right(_) =>
        Right(())

  /** Format error message for glab CLI not installed. */
  def formatGlabNotInstalledError(): String =
    """glab CLI is not installed

The GitLab tracker requires the glab CLI tool.

Install glab CLI:
  https://gitlab.com/gitlab-org/cli

After installation, authenticate with:
  glab auth login""".stripMargin

  /** Format error message for glab CLI not authenticated. */
  def formatGlabNotAuthenticatedError(): String =
    """glab is not authenticated

You need to authenticate with GitLab before fetching issues.

Run this command to authenticate:
  glab auth login

Follow the prompts to sign in with your GitLab account.""".stripMargin

  /** Format error message for issue not found.
    *
    * @param issueId GitLab issue number
    * @param repository GitLab repository in owner/project format
    * @return User-friendly error message
    */
  def formatIssueNotFoundError(issueId: String, repository: String): String =
    s"Issue $issueId not found in repository $repository."

  /** Format error message for network errors.
    *
    * @param details Error details from glab CLI
    * @return User-friendly error message with remediation
    */
  def formatNetworkError(details: String): String =
    s"""Network error while connecting to GitLab.

Details: $details

Check your network connection and try again.""".stripMargin

  /** Check if error message indicates authentication failure.
    *
    * @param error Error message from glab CLI
    * @return true if error is authentication-related
    */
  def isAuthenticationError(error: String): Boolean =
    error.contains("401") ||
    error.toLowerCase.contains("unauthorized") ||
    error.toLowerCase.contains("authentication")

  /** Check if error message indicates resource not found.
    *
    * @param error Error message from glab CLI
    * @return true if error indicates not found
    */
  def isNotFoundError(error: String): Boolean =
    error.contains("404") ||
    error.toLowerCase.contains("not found")

  /** Check if error message indicates network connectivity issue.
    *
    * @param error Error message from glab CLI
    * @return true if error is network-related
    */
  def isNetworkError(error: String): Boolean =
    error.toLowerCase.contains("network") ||
    error.toLowerCase.contains("connection") ||
    error.toLowerCase.contains("timeout") ||
    error.toLowerCase.contains("could not resolve")

  /** Build glab CLI command arguments for fetching an issue.
    *
    * @param issueNumber GitLab issue number (e.g., "123")
    * @param repository GitLab repository in owner/project format (can include nested groups)
    * @return Array of command arguments for glab CLI
    */
  def buildFetchIssueCommand(
    issueNumber: String,
    repository: String
  ): Array[String] =
    Array(
      "issue", "view", issueNumber,
      "--repo", repository,
      "--output", "json"
    )

  /** Parse JSON response from glab issue view command.
    *
    * Expected format: {"iid": 1, "title": "...", "state": "opened", "assignees": [...], "description": "..."}
    *
    * @param jsonOutput JSON string from glab CLI
    * @param issueIdValue Full issue ID (e.g., "PROJ-123") used in the Issue object
    * @return Right(Issue) on success, Left(error message) on failure
    */
  def parseFetchIssueResponse(
    jsonOutput: String,
    issueIdValue: String
  ): Either[String, Issue] =
    try
      import ujson.*
      val json = read(jsonOutput)

      // Use the full issue ID (e.g., "PROJ-123")
      val id = issueIdValue

      // Extract title and state
      val title = json("title").str
      val state = json("state").str

      // Map GitLab state to normalized state
      // GitLab uses "opened"/"closed" (not "OPEN"/"CLOSED")
      val normalizedState = state match
        case "opened" => "open"
        case "closed" => "closed"
        case other => other

      // Extract first assignee if any exist
      val assignee =
        if json("assignees").arr.isEmpty then None
        else Some(json("assignees").arr.head("username").str)

      // Handle null or empty description
      val description =
        if json("description").isNull then None
        else
          val desc = json("description").str
          if desc.isEmpty then None else Some(desc)

      Right(Issue(
        id = id,
        title = title,
        status = normalizedState,
        assignee = assignee,
        description = description
      ))
    catch
      case e: Exception =>
        Left(s"Failed to parse issue response: ${e.getMessage}")

  /** Fetch a GitLab issue via glab CLI.
    *
    * @param issueIdValue Full issue ID (e.g., "PROJ-123") - number is extracted for API call
    * @param repository GitLab repository in owner/project format
    * @param isCommandAvailable Function to check if command exists (injected for testability)
    * @param execCommand Function to execute shell command (injected for testability)
    * @return Right(Issue) on success, Left(error message) on failure
    */
  def fetchIssue(
    issueIdValue: String,
    repository: String,
    isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
    execCommand: (String, Array[String]) => Either[String, String] =
      (cmd, args) => CommandRunner.execute(cmd, args)
  ): Either[String, Issue] =
    // Extract numeric issue number from full ID (e.g., "PROJ-123" -> "123")
    val issueNumber = issueIdValue.split("-").last

    // Validate prerequisites before attempting fetch
    validateGlabPrerequisites(repository, isCommandAvailable, execCommand) match
      case Left(GlabPrerequisiteError.GlabNotInstalled) =>
        Left(formatGlabNotInstalledError())
      case Left(GlabPrerequisiteError.GlabNotAuthenticated) =>
        Left(formatGlabNotAuthenticatedError())
      case Left(GlabPrerequisiteError.GlabError(msg)) =>
        Left(s"glab CLI error: $msg")
      case Right(_) =>
        // Build command arguments (uses numeric ID for API)
        val args = buildFetchIssueCommand(issueNumber, repository)

        // Execute glab issue view
        execCommand("glab", args) match
          case Left(error) =>
            Left(s"Failed to fetch issue: $error")
          case Right(jsonOutput) =>
            // Parse JSON response (uses full ID for Issue object)
            parseFetchIssueResponse(jsonOutput, issueIdValue)

  /** Build glab CLI command arguments for creating an issue.
    *
    * @param repository GitLab repository in owner/project format
    * @param title Issue title
    * @param description Issue description (empty string if not provided)
    * @param issueType Issue type (Bug or Feature) - mapped to GitLab labels
    * @return Array of command arguments for glab CLI
    */
  def buildCreateIssueCommand(
    repository: String,
    title: String,
    description: String,
    issueType: FeedbackParser.IssueType
  ): Array[String] =
    // Map issue type to GitLab label
    val label = issueType match
      case FeedbackParser.IssueType.Bug => "bug"
      case FeedbackParser.IssueType.Feature => "feature"

    // Build command: glab issue create --repo <repo> --title <title> --description <desc> --label <label>
    // Note: glab uses --description (not --body like gh)
    Array(
      "issue", "create",
      "--repo", repository,
      "--title", title,
      "--description", description,
      "--label", label
    )

  /** Build glab CLI command arguments without labels (for fallback).
    *
    * @param repository GitLab repository in owner/project format
    * @param title Issue title
    * @param description Issue description (empty string if not provided)
    * @return Array of command arguments for glab CLI without label
    */
  def buildCreateIssueCommandWithoutLabel(
    repository: String,
    title: String,
    description: String
  ): Array[String] =
    // Build command without label
    Array(
      "issue", "create",
      "--repo", repository,
      "--title", title,
      "--description", description
    )

  /** Parse URL response from glab issue create command.
    *
    * Expected format: https://gitlab.com/owner/project/-/issues/123
    * or self-hosted: https://gitlab.company.com/owner/project/-/issues/123
    *
    * @param output URL string from glab CLI (may have trailing newline)
    * @return Right(CreatedIssue) on success, Left(error message) on failure
    */
  def parseCreateIssueResponse(output: String): Either[String, CreatedIssue] =
    val url = output.trim
    if url.isEmpty then
      Left("Empty response from glab CLI")
    else
      // Extract issue number from GitLab URL pattern: .*/-/issues/(\d+)$
      val issuePattern = """.*/-/issues/(\d+)$""".r
      url match
      case issuePattern(number) =>
        Right(CreatedIssue(number, url))
      case _ =>
        Left(s"Unexpected response format: $url")

  /** Check if error is related to labels (for graceful fallback).
    *
    * @param error Error message from glab CLI
    * @return true if error indicates label-related issue
    */
  def isLabelError(error: String): Boolean =
    val lowerError = error.toLowerCase
    lowerError.contains("label") && (
      lowerError.contains("not found") ||
      lowerError.contains("does not exist") ||
      lowerError.contains("invalid")
    )

  /** Create a new GitLab issue via glab CLI.
    *
    * If issue creation fails with a label-related error, retries without labels.
    *
    * @param repository GitLab repository in owner/project format
    * @param title Issue title
    * @param description Issue description
    * @param issueType Issue type (Bug or Feature)
    * @param isCommandAvailable Function to check if command exists (injected for testability)
    * @param execCommand Function to execute shell command (injected for testability)
    * @return Right(CreatedIssue) on success, Left(error message) on failure
    */
  def createIssue(
    repository: String,
    title: String,
    description: String,
    issueType: FeedbackParser.IssueType,
    isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
    execCommand: (String, Array[String]) => Either[String, String] =
      (cmd, args) => CommandRunner.execute(cmd, args)
  ): Either[String, CreatedIssue] =
    // Validate prerequisites before attempting creation
    validateGlabPrerequisites(repository, isCommandAvailable, execCommand) match
      case Left(GlabPrerequisiteError.GlabNotInstalled) =>
        Left(formatGlabNotInstalledError())
      case Left(GlabPrerequisiteError.GlabNotAuthenticated) =>
        Left(formatGlabNotAuthenticatedError())
      case Left(GlabPrerequisiteError.GlabError(msg)) =>
        Left(s"glab CLI error: $msg")
      case Right(_) =>
        val args = buildCreateIssueCommand(repository, title, description, issueType)

        // Execute glab command
        execCommand("glab", args) match
          case Left(error) if isLabelError(error) =>
            // Retry without labels if label-related error
            val argsWithoutLabel = buildCreateIssueCommandWithoutLabel(repository, title, description)
            execCommand("glab", argsWithoutLabel) match
              case Left(retryError) => Left(retryError)
              case Right(output) => parseCreateIssueResponse(output)
          case Left(error) => Left(error)
          case Right(output) => parseCreateIssueResponse(output)

  /** Create a new GitLab issue via glab CLI without labels.
    *
    * Simplified version for issue creation without label support.
    *
    * @param repository GitLab repository path
    * @param title Issue title
    * @param description Issue description
    * @return Right(CreatedIssue) on success, Left(error message) on failure
    */
  def createIssue(
    repository: String,
    title: String,
    description: String
  ): Either[String, CreatedIssue] =
    // Validate prerequisites before attempting creation
    validateGlabPrerequisites(repository) match
      case Left(GlabPrerequisiteError.GlabNotInstalled) =>
        Left(formatGlabNotInstalledError())
      case Left(GlabPrerequisiteError.GlabNotAuthenticated) =>
        Left(formatGlabNotAuthenticatedError())
      case Left(GlabPrerequisiteError.GlabError(msg)) =>
        Left(s"glab CLI error: $msg")
      case Right(_) =>
        val args = buildCreateIssueCommandWithoutLabel(repository, title, description)

        CommandRunner.execute("glab", args) match
          case Left(error) => Left(error)
          case Right(output) => parseCreateIssueResponse(output)
