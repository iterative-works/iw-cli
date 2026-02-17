// PURPOSE: Parser for issue create command arguments
// PURPOSE: Handles --title (required) and --description (optional) flags for issue creation

package iw.core

/** Structured issue creation request from parsed command arguments.
  *
  * @param title Issue title (non-empty, required)
  * @param description Optional issue description
  */
case class IssueCreateRequest(
  title: String,
  description: Option[String]
)

object IssueCreateParser:

  /** Parses issue create command arguments into a structured request.
    *
    * Expects --title flag with value (required).
    * Optional --description flag with value.
    *
    * @param args Raw command line arguments
    * @return Right(IssueCreateRequest) on success, Left(error message) on validation failure
    */
  def parse(args: Seq[String]): Either[String, IssueCreateRequest] =
    // Extract --title value
    val title = extractFlagValue(args, "--title")
    if title.isEmpty then
      Left("--title flag is required")
    else
      // Extract optional --description value
      val description = extractFlagValue(args, "--description")

      Right(IssueCreateRequest(title.get, description))

  /** Extracts the value of a command line flag.
    *
    * Supports multi-word values (joined with spaces).
    * Returns None if flag is not present.
    * Returns Some("") if flag is present but has no value.
    *
    * @param args Command line arguments
    * @param flag Flag name (e.g., "--title")
    * @return Some(value) if flag is present, None otherwise
    */
  private def extractFlagValue(args: Seq[String], flag: String): Option[String] =
    val flagIndex = args.indexOf(flag)
    if flagIndex >= 0 then
      // Get all args after the flag until the next flag or end
      val valueStart = flagIndex + 1
      if valueStart >= args.size then
        // Flag present but no value
        Some("")
      else
        val valueParts = args.drop(valueStart).takeWhile(!_.startsWith("--"))
        Some(valueParts.mkString(" "))
    else
      None
