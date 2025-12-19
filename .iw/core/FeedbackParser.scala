// PURPOSE: Parser for feedback command arguments
// PURPOSE: Handles title, --description, and --type flags for issue creation

package iw.core

object FeedbackParser:

  enum IssueType:
    case Bug, Feature

  object IssueType:
    def fromString(s: String): Either[String, IssueType] =
      s.toLowerCase match
        case "bug" => Right(IssueType.Bug)
        case "feature" => Right(IssueType.Feature)
        case _ => Left(s"Type must be 'bug' or 'feature', got: $s")

  case class FeedbackRequest(
    title: String,
    description: String,
    issueType: IssueType
  )

  def parseFeedbackArgs(args: Seq[String]): Either[String, FeedbackRequest] =
    // Parse title - all args before first flag
    val titleParts = args.takeWhile(!_.startsWith("--"))
    if titleParts.isEmpty then
      return Left("Title is required")

    val title = titleParts.mkString(" ")

    // Parse flags
    val flagArgs = args.dropWhile(!_.startsWith("--"))
    val description = extractFlagValue(flagArgs, "--description").getOrElse("")
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
