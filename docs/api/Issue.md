# Issue

> Domain model for issue data fetched from trackers.

## Import

```scala
import iw.core.model.*
```

## API

### Issue

```scala
case class Issue(
  id: String,                    // Issue identifier (e.g., "IWLE-123", "132")
  title: String,                 // Issue title
  status: String,                // Status (e.g., "open", "In Progress", "Done")
  assignee: Option[String],      // Assignee display name
  description: Option[String]    // Issue description/body
)
```

### IssueTracker

Trait for fetching issues from different trackers:

```scala
trait IssueTracker:
  def fetchIssue(issueId: IssueId): Either[String, Issue]
```

## Examples

```scala
// From issue.scala - fetching and displaying an issue
fetchIssue(issueId, config) match
  case Right(issue) =>
    val formatted = IssueFormatter.format(issue)
    println(formatted)
  case Left(error) =>
    Output.error(error)

// Accessing issue fields
val header = s"${issue.id}: ${issue.title}"
val assigneeValue = issue.assignee.getOrElse("None")
```
