// PURPOSE: GitHub CLI client for issue creation via gh CLI
// PURPOSE: Provides createIssue to create GitHub issues via gh command

package iw.core

import iw.core.infrastructure.CommandRunner

object GitHubClient:

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

    // Build base command: gh issue create --repo <repo> --title <title> --label <label> --json number,url
    val baseArgs = Array(
      "gh", "issue", "create",
      "--repo", repository,
      "--title", title,
      "--label", label
    )

    // Add --body if description is non-empty
    val withBody = if description.nonEmpty then
      baseArgs ++ Array("--body", description)
    else
      baseArgs

    // Add --json output format
    withBody ++ Array("--json", "number,url")

  /** Parse JSON response from gh issue create command.
    *
    * Expected format: {"number": 132, "url": "https://github.com/owner/repo/issues/132"}
    *
    * @param json JSON string from gh CLI
    * @return Right(CreatedIssue) on success, Left(error message) on failure
    */
  def parseCreateIssueResponse(json: String): Either[String, CreatedIssue] =
    try
      if json.isEmpty then
        return Left("Empty response from gh CLI")

      import upickle.default.*
      val parsed = ujson.read(json)

      // Check for required fields
      if !parsed.obj.contains("number") then
        return Left("Malformed response: missing 'number' field")
      if !parsed.obj.contains("url") then
        return Left("Malformed response: missing 'url' field")

      // Extract number as string (gh returns it as integer)
      val number = parsed("number").num.toInt.toString
      val url = parsed("url").str

      Right(CreatedIssue(number, url))
    catch
      case e: Exception => Left(s"Failed to parse gh response: ${e.getMessage}")

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
    // Build base command without label
    val baseArgs = Array(
      "gh", "issue", "create",
      "--repo", repository,
      "--title", title
    )

    // Add --body if description is non-empty
    val withBody = if description.nonEmpty then
      baseArgs ++ Array("--body", description)
    else
      baseArgs

    // Add --json output format
    withBody ++ Array("--json", "number,url")

  /** Create a new GitHub issue via gh CLI.
    *
    * If issue creation fails with a label-related error, retries without labels.
    *
    * @param repository GitHub repository in owner/repo format
    * @param title Issue title
    * @param description Issue description
    * @param issueType Issue type (Bug or Feature)
    * @param execCommand Function to execute shell command (injected for testability)
    * @return Right(CreatedIssue) on success, Left(error message) on failure
    */
  def createIssue(
    repository: String,
    title: String,
    description: String,
    issueType: FeedbackParser.IssueType,
    execCommand: (String, Array[String]) => Either[String, String] =
      (cmd, args) => CommandRunner.execute(cmd, args)
  ): Either[String, CreatedIssue] =
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
