// PURPOSE: Extended issue domain model with URL and cache timestamp
// PURPOSE: Wraps Issue entity with additional metadata for caching and display

package iw.core.model

import java.time.Instant
import iw.core.model.Issue
import iw.core.model.IssueData

/** Issue data with URL and fetch timestamp for caching.
  *
  * @param id Issue identifier (e.g., "IWLE-123")
  * @param title Issue title
  * @param status Issue status (e.g., "In Progress", "Done")
  * @param assignee Optional assignee name
  * @param description Optional issue description
  * @param url Direct link to issue in tracker (Linear or YouTrack)
  * @param fetchedAt Timestamp when issue data was fetched from API
  */
case class IssueData(
  id: String,
  title: String,
  status: String,
  assignee: Option[String],
  description: Option[String],
  url: String,
  fetchedAt: Instant
)

object IssueData:
  /** Create IssueData from Issue entity with URL and timestamp.
    *
    * @param issue Issue entity from tracker API
    * @param url Direct link to issue in tracker
    * @param fetchedAt Timestamp when issue was fetched
    * @return IssueData with all fields populated
    */
  def fromIssue(issue: Issue, url: String, fetchedAt: Instant): IssueData =
    IssueData(
      id = issue.id,
      title = issue.title,
      status = issue.status,
      assignee = issue.assignee,
      description = issue.description,
      url = url,
      fetchedAt = fetchedAt
    )
