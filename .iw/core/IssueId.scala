// PURPOSE: Issue ID value object with validation for tracker issue identifiers
// PURPOSE: Validates format PROJECT-123 and provides branch name conversion

package iw.core

case class IssueId private (value: String):
  def toBranchName: String = value

object IssueId:
  private val Pattern = """^[A-Z]+-[0-9]+$""".r
  private val BranchPattern = """^([A-Z]+-[0-9]+).*""".r

  def parse(raw: String): Either[String, IssueId] =
    val normalized = raw.toUpperCase.trim
    normalized match
      case Pattern() => Right(IssueId(normalized))
      case _ => Left(s"Invalid issue ID format: $raw (expected: PROJECT-123)")

  def fromBranch(branchName: String): Either[String, IssueId] =
    val normalized = branchName.toUpperCase
    normalized match
      case BranchPattern(issueId) => Right(IssueId(issueId))
      case _ => Left(s"Cannot extract issue ID from branch '$branchName' (expected: PROJECT-123[-description])")
