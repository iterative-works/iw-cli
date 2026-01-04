// PURPOSE: GitLab CLI client for issue fetching via glab CLI
// PURPOSE: Provides fetchIssue to retrieve GitLab issues via glab command

package iw.core

import iw.core.infrastructure.CommandRunner

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
      return Left(GlabPrerequisiteError.GlabNotInstalled)

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
    * @param issueNumber Issue number (used for error messages and ID formatting)
    * @return Right(Issue) on success, Left(error message) on failure
    */
  def parseFetchIssueResponse(
    jsonOutput: String,
    issueNumber: String
  ): Either[String, Issue] =
    try
      import ujson.*
      val json = read(jsonOutput)

      // Format issue ID with # prefix (e.g., "#123")
      val id = s"#$issueNumber"

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
    * @param issueNumber GitLab issue number (e.g., "123")
    * @param repository GitLab repository in owner/project format
    * @param isCommandAvailable Function to check if command exists (injected for testability)
    * @param execCommand Function to execute shell command (injected for testability)
    * @return Right(Issue) on success, Left(error message) on failure
    */
  def fetchIssue(
    issueNumber: String,
    repository: String,
    isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
    execCommand: (String, Array[String]) => Either[String, String] =
      (cmd, args) => CommandRunner.execute(cmd, args)
  ): Either[String, Issue] =
    // Validate prerequisites before attempting fetch
    validateGlabPrerequisites(repository, isCommandAvailable, execCommand) match
      case Left(GlabPrerequisiteError.GlabNotInstalled) =>
        return Left(formatGlabNotInstalledError())
      case Left(GlabPrerequisiteError.GlabNotAuthenticated) =>
        return Left(formatGlabNotAuthenticatedError())
      case Left(GlabPrerequisiteError.GlabError(msg)) =>
        return Left(s"glab CLI error: $msg")
      case Right(_) =>
        // Proceed with issue fetch

    // Build command arguments
    val args = buildFetchIssueCommand(issueNumber, repository)

    // Execute glab issue view
    execCommand("glab", args) match
      case Left(error) =>
        Left(s"Failed to fetch issue: $error")
      case Right(jsonOutput) =>
        // Parse JSON response
        parseFetchIssueResponse(jsonOutput, issueNumber)
