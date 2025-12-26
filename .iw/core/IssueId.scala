// PURPOSE: Issue ID value object with validation for tracker issue identifiers
// PURPOSE: Validates format PROJECT-123 and provides branch name conversion

package iw.core

opaque type IssueId = String

object IssueId:
  // Pattern for Linear/YouTrack format: TEAM-NNN (e.g., IWLE-132)
  private val Pattern = """^[A-Z]+-[0-9]+$""".r
  // Pattern for numeric GitHub format: NNN (e.g., 132)
  private val NumericPattern = """^[0-9]+$""".r
  // Branch pattern for Linear/YouTrack: TEAM-NNN-description
  private val BranchPattern = """^([A-Z]+-[0-9]+).*""".r
  // Branch pattern for numeric GitHub: NNN-description or NNN_description
  private val NumericBranchPattern = """^([0-9]+)[-_].*""".r

  def parse(raw: String): Either[String, IssueId] =
    val trimmed = raw.trim
    // Try TEAM-NNN pattern first (uppercase it for Linear/YouTrack)
    val normalized = trimmed.toUpperCase
    normalized match
      case Pattern() => Right(normalized)
      case _ =>
        // Try numeric pattern (GitHub) - don't uppercase
        trimmed match
          case NumericPattern() => Right(trimmed)
          case _ => Left(s"Invalid issue ID format: $raw (expected: PROJECT-123 or 123)")

  def forGitHub(teamPrefix: String, number: Int): Either[String, IssueId] =
    // Validate team prefix first
    TeamPrefixValidator.validate(teamPrefix) match
      case Left(err) => Left(err)
      case Right(validPrefix) =>
        // Compose TEAM-NNN format
        val composed = s"$validPrefix-$number"
        // Validate through existing parse method to ensure consistency
        parse(composed)

  def fromBranch(branchName: String): Either[String, IssueId] =
    // Try TEAM-NNN pattern first (uppercase for Linear/YouTrack)
    val normalized = branchName.toUpperCase
    normalized match
      case BranchPattern(issueId) => Right(issueId)
      case _ =>
        // Try numeric pattern with suffix (GitHub) - don't uppercase
        branchName match
          case NumericBranchPattern(issueId) => Right(issueId)
          case NumericPattern() => Right(branchName) // Bare numeric branch (e.g., "48")
          case _ => Left(s"Cannot extract issue ID from branch '$branchName' (expected: PROJECT-123[-description] or 123[-description])")

  extension (issueId: IssueId)
    def value: String = issueId
    def toBranchName: String = issueId
    def team: String =
      // GitHub numeric IDs don't have a team part
      if issueId.contains("-") then
        issueId.split("-").head
      else
        "" // Numeric GitHub ID has no team
