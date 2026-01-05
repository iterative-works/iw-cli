// PURPOSE: GitHub CLI client for issue creation via gh CLI
// PURPOSE: Provides createIssue to create GitHub issues via gh command

package iw.core

import iw.core.infrastructure.CommandRunner

object GitHubClient:

  /** Error types for gh CLI prerequisite validation. */
  enum GhPrerequisiteError:
    case GhNotInstalled
    case GhNotAuthenticated
    case GhOtherError(message: String)

  /** Validate gh CLI prerequisites before creating issues.
    *
    * Checks that:
    * 1. gh CLI is installed and available in PATH
    * 2. gh CLI is authenticated (via `gh auth status`)
    *
    * @param repository GitHub repository in owner/repo format
    * @param isCommandAvailable Function to check if command exists (injected for testability)
    * @param execCommand Function to execute shell command (injected for testability)
    * @return Right(()) if all prerequisites met, Left(error) otherwise
    */
  def validateGhPrerequisites(
    repository: String,
    isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
    execCommand: (String, Array[String]) => Either[String, String] =
      (cmd, args) => CommandRunner.execute(cmd, args)
  ): Either[GhPrerequisiteError, Unit] =
    // Check gh CLI is installed
    if !isCommandAvailable("gh") then
      return Left(GhPrerequisiteError.GhNotInstalled)

    // Check gh authentication
    execCommand("gh", Array("auth", "status")) match
      case Left(error) if isAuthenticationError(error) =>
        Left(GhPrerequisiteError.GhNotAuthenticated)
      case Left(error) =>
        Left(GhPrerequisiteError.GhOtherError(error))
      case Right(_) =>
        Right(())

  /** Check if error message indicates authentication failure.
    *
    * Detects exit code 4 from gh CLI (not authenticated).
    */
  private def isAuthenticationError(error: String): Boolean =
    val lowerError = error.toLowerCase
    lowerError.contains("exit status 4") ||
    lowerError.contains("exit value: 4") ||
    lowerError.contains("not authenticated") ||
    lowerError.contains("not logged in")

  /** Format error message for gh CLI not installed. */
  def formatGhNotInstalledError(): String =
    """gh CLI is not installed
      |
      |The GitHub tracker requires the gh CLI tool.
      |
      |Install gh CLI:
      |  https://cli.github.com/
      |
      |After installation, authenticate with:
      |  gh auth login""".stripMargin

  /** Format error message for gh CLI not authenticated. */
  def formatGhNotAuthenticatedError(): String =
    """gh is not authenticated
      |
      |You need to authenticate with GitHub before creating issues.
      |
      |Run this command to authenticate:
      |  gh auth login
      |
      |Follow the prompts to sign in with your GitHub account.""".stripMargin

  /** Build gh CLI command arguments for creating an issue.
    *
    * @param repository GitHub repository in owner/repo format
    * @param title Issue title
    * @param description Issue description (empty string if not provided)
    * @param issueType Issue type (Bug or Feature) - mapped to GitHub labels
    * @return Array of command arguments for gh CLI
    */
  def buildCreateIssueCommand(
    repository: String,
    title: String,
    description: String,
    issueType: FeedbackParser.IssueType
  ): Array[String] =
    // Map issue type to GitHub label
    val label = issueType match
      case FeedbackParser.IssueType.Bug => "bug"
      case FeedbackParser.IssueType.Feature => "feedback"

    // Build command: gh issue create --repo <repo> --title <title> --body <body> --label <label>
    // Note: --body is always required when running non-interactively
    Array(
      "gh", "issue", "create",
      "--repo", repository,
      "--title", title,
      "--body", if description.nonEmpty then description else "",
      "--label", label
    )

  /** Parse URL response from gh issue create command.
    *
    * Expected format: https://github.com/owner/repo/issues/123
    *
    * @param output URL string from gh CLI (may have trailing newline)
    * @return Right(CreatedIssue) on success, Left(error message) on failure
    */
  def parseCreateIssueResponse(output: String): Either[String, CreatedIssue] =
    val url = output.trim
    if url.isEmpty then
      return Left("Empty response from gh CLI")

    // Extract issue number from URL: https://github.com/owner/repo/issues/123
    val issuePattern = """.*/issues/(\d+)$""".r
    url match
      case issuePattern(number) =>
        Right(CreatedIssue(number, url))
      case _ =>
        Left(s"Unexpected response format: $url")

  /** Build gh CLI command arguments without labels (for fallback).
    *
    * @param repository GitHub repository in owner/repo format
    * @param title Issue title
    * @param description Issue description (empty string if not provided)
    * @return Array of command arguments for gh CLI without label
    */
  def buildCreateIssueCommandWithoutLabel(
    repository: String,
    title: String,
    description: String
  ): Array[String] =
    // Build command without label
    // Note: --body is always required when running non-interactively
    Array(
      "gh", "issue", "create",
      "--repo", repository,
      "--title", title,
      "--body", if description.nonEmpty then description else ""
    )

  /** Create a new GitHub issue via gh CLI.
    *
    * If issue creation fails with a label-related error, retries without labels.
    *
    * @param repository GitHub repository in owner/repo format
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
    validateGhPrerequisites(repository, isCommandAvailable, execCommand) match
      case Left(GhPrerequisiteError.GhNotInstalled) =>
        return Left(formatGhNotInstalledError())
      case Left(GhPrerequisiteError.GhNotAuthenticated) =>
        return Left(formatGhNotAuthenticatedError())
      case Left(GhPrerequisiteError.GhOtherError(msg)) =>
        return Left(s"gh CLI error: $msg")
      case Right(_) =>
        // Proceed with issue creation

    val args = buildCreateIssueCommand(repository, title, description, issueType)

    // Execute gh command (first element is command, rest are args)
    val command = args.head
    val commandArgs = args.tail

    execCommand(command, commandArgs) match
      case Left(error) if isLabelError(error) =>
        // Retry without labels if label-related error
        val argsWithoutLabel = buildCreateIssueCommandWithoutLabel(repository, title, description)
        val commandArgsWithoutLabel = argsWithoutLabel.tail
        execCommand(command, commandArgsWithoutLabel) match
          case Left(retryError) => Left(retryError)
          case Right(output) => parseCreateIssueResponse(output)
      case Left(error) => Left(error)
      case Right(output) => parseCreateIssueResponse(output)

  /** Check if error is related to labels (for graceful fallback). */
  private def isLabelError(error: String): Boolean =
    val lowerError = error.toLowerCase
    lowerError.contains("label") && (
      lowerError.contains("not found") ||
      lowerError.contains("does not exist") ||
      lowerError.contains("invalid")
    )

  /** Build gh CLI command arguments for fetching an issue.
    *
    * @param issueNumber GitHub issue number (e.g., "132")
    * @param repository GitHub repository in owner/repo format
    * @return Array of command arguments for gh CLI
    */
  def buildFetchIssueCommand(
    issueNumber: String,
    repository: String
  ): Array[String] =
    Array(
      "issue", "view", issueNumber,
      "--repo", repository,
      "--json", "number,title,state,assignees,body"
    )

  /** Parse JSON response from gh issue view command.
    *
    * Expected format: {"number": 132, "title": "...", "state": "OPEN", "assignees": [...], "body": "..."}
    *
    * @param jsonOutput JSON string from gh CLI
    * @param issueIdValue Full issue ID (e.g., "IWCLI-132") used in the Issue object
    * @return Right(Issue) on success, Left(error message) on failure
    */
  def parseFetchIssueResponse(
    jsonOutput: String,
    issueIdValue: String
  ): Either[String, Issue] =
    try
      import ujson.*
      val json = read(jsonOutput)

      // Use the full issue ID (e.g., "IWCLI-132")
      val id = issueIdValue

      // Extract title and state (lowercase state for consistency)
      val title = json("title").str
      val state = json("state").str.toLowerCase

      // Extract first assignee if any exist
      val assignee =
        if json("assignees").arr.isEmpty then None
        else Some(json("assignees").arr.head("login").str)

      // Handle null body (GitHub returns null for empty descriptions)
      val description =
        if json("body").isNull then None
        else Some(json("body").str)

      Right(Issue(
        id = id,
        title = title,
        status = state,
        assignee = assignee,
        description = description
      ))
    catch
      case e: Exception =>
        Left(s"Failed to parse issue response: ${e.getMessage}")

  /** Fetch a GitHub issue via gh CLI.
    *
    * @param issueIdValue Full issue ID (e.g., "IWCLI-132") - number is extracted for API call
    * @param repository GitHub repository in owner/repo format
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
    // Extract numeric issue number from full ID (e.g., "IWCLI-132" -> "132")
    val issueNumber = issueIdValue.split("-").last

    // Validate prerequisites before attempting fetch
    validateGhPrerequisites(repository, isCommandAvailable, execCommand) match
      case Left(GhPrerequisiteError.GhNotInstalled) =>
        return Left(formatGhNotInstalledError())
      case Left(GhPrerequisiteError.GhNotAuthenticated) =>
        return Left(formatGhNotAuthenticatedError())
      case Left(GhPrerequisiteError.GhOtherError(msg)) =>
        return Left(s"gh CLI error: $msg")
      case Right(_) =>
        // Proceed with issue fetch

    // Build command arguments (uses numeric ID for API)
    val args = buildFetchIssueCommand(issueNumber, repository)

    // Execute gh issue view
    execCommand("gh", args) match
      case Left(error) =>
        Left(s"Failed to fetch issue: $error")
      case Right(jsonOutput) =>
        // Parse JSON response (uses full ID for Issue object)
        parseFetchIssueResponse(jsonOutput, issueIdValue)
