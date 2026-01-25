// PURPOSE: Issue domain model and IssueTracker abstraction for fetching issues
// PURPOSE: Defines Issue entity and IssueTracker trait for tracker implementations

package iw.core.model

case class Issue(
  id: String,
  title: String,
  status: String,
  assignee: Option[String],
  description: Option[String]
)

trait IssueTracker:
  def fetchIssue(issueId: IssueId): Either[String, Issue]
