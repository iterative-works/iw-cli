// PURPOSE: Parser for feedback command arguments
// PURPOSE: Handles title, --description, and --type flags for issue creation

package iw.core.dashboard

import iw.core.model.Constants

object FeedbackParser:

  /** Maximum allowed length for issue title */
  val MaxTitleLength: Int = 500

  /** Maximum allowed length for issue description */
  val MaxDescriptionLength: Int = 10000

  /** Type of feedback issue being submitted. */
  enum IssueType:
    case Bug, Feature

  object IssueType:
    /** Parses a string into an IssueType enum value.
      *
      * Case-insensitive matching: "bug" or "BUG" → Bug, "feature" or "Feature" → Feature.
      *
      * @param s Input string to parse
      * @return Right(IssueType) for valid input, Left(error message) for invalid input
      */
    def fromString(s: String): Either[String, IssueType] =
      s.toLowerCase match
        case "bug" => Right(IssueType.Bug)
        case "feature" => Right(IssueType.Feature)
        case _ => Left(s"Type must be 'bug' or 'feature', got: $s")

  /** Structured feedback request from parsed command arguments.
    *
    * @param title Issue title (non-empty, max 500 chars)
    * @param description Issue description (empty string if not provided, max 10000 chars)
    * @param issueType Type of issue (Bug or Feature)
    */
  case class FeedbackRequest(
    title: String,
    description: String,
    issueType: IssueType
  )

  /** Returns the Linear label ID for a given issue type.
    *
    * @param issueType The issue type (Bug or Feature)
    * @return Linear label UUID string
    */
  def getLabelIdForIssueType(issueType: IssueType): String =
    issueType match
      case IssueType.Bug => Constants.IwCliLabels.Bug
      case IssueType.Feature => Constants.IwCliLabels.Feature

  /** Parses feedback command arguments into a structured request.
    *
    * Title is extracted from all arguments before the first flag (--).
    * Multi-word titles are joined with spaces.
    *
    * @param args Raw command line arguments
    * @return Right(FeedbackRequest) on success, Left(error message) on validation failure
    */
  def parseFeedbackArgs(args: Seq[String]): Either[String, FeedbackRequest] =
    // Parse title - all args before first flag
    val titleParts = args.takeWhile(!_.startsWith("--"))
    if titleParts.isEmpty then
      return Left("Title is required")

    val title = titleParts.mkString(" ")

    // Validate title length
    if title.length > MaxTitleLength then
      return Left(s"Title must be $MaxTitleLength characters or less (got ${title.length})")

    // Parse flags
    val flagArgs = args.dropWhile(!_.startsWith("--"))
    val description = extractFlagValue(flagArgs, "--description").getOrElse("")

    // Validate description length
    if description.length > MaxDescriptionLength then
      return Left(s"Description must be $MaxDescriptionLength characters or less (got ${description.length})")

    val issueTypeStr = extractFlagValue(flagArgs, "--type").getOrElse("feature")

    // Validate issue type
    IssueType.fromString(issueTypeStr) match
      case Left(error) => Left(error)
      case Right(issueType) => Right(FeedbackRequest(title, description, issueType))

  private def extractFlagValue(args: Seq[String], flag: String): Option[String] =
    val flagIndex = args.indexOf(flag)
    if flagIndex >= 0 && flagIndex + 1 < args.size then
      // Get all args after the flag until the next flag or end
      val valueStart = flagIndex + 1
      val valueParts = args.drop(valueStart).takeWhile(!_.startsWith("--"))
      Some(valueParts.mkString(" "))
    else
      None
