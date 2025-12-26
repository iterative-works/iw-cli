// PURPOSE: Issue ID value object with validation for tracker issue identifiers
// PURPOSE: Validates format PROJECT-123 and provides branch name conversion

package iw.core

opaque type IssueId = String

object IssueId:
  // Pattern for TEAM-NNN format (e.g., IWLE-132, IWCLI-51)
  private val Pattern = """^[A-Z]+-[0-9]+$""".r
  // Branch pattern for TEAM-NNN-description
  private val BranchPattern = """^([A-Z]+-[0-9]+).*""".r

  def parse(raw: String, defaultTeam: Option[String] = None): Either[String, IssueId] =
    val trimmed = raw.trim
    // Try TEAM-NNN pattern first (uppercase it for Linear/YouTrack)
    val normalized = trimmed.toUpperCase
    normalized match
      case Pattern() => Right(normalized)
      case _ =>
        // If we have team prefix context, try to compose from numeric input
        defaultTeam.flatMap { team =>
          trimmed.toIntOption.map(num => forGitHub(team, num))
        }.getOrElse {
          Left(s"Invalid issue ID format: $raw (expected: TEAM-123). For GitHub projects, configure team prefix with 'iw init'.")
        }

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
    // Try TEAM-NNN pattern (uppercase for Linear/YouTrack)
    val normalized = branchName.toUpperCase
    normalized match
      case BranchPattern(issueId) => Right(issueId)
      case _ =>
        Left(s"Cannot extract issue ID from branch '$branchName' (expected: TEAM-123 or TEAM-123-description). Configure team prefix with 'iw init' for GitHub projects.")

  extension (issueId: IssueId)
    def value: String = issueId
    def toBranchName: String = issueId
    def team: String =
      // All issue IDs now have TEAM-NNN format
      issueId.split("-").head
