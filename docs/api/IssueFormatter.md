# IssueFormatter

> Format Issue objects for human-readable CLI display.

## Import

```scala
import iw.core.output.*
import iw.core.model.Issue
```

## API

### IssueFormatter.format(issue: Issue): String

Format an Issue into a multi-line string with Unicode borders and aligned fields.

Output format:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
IWLE-123: Issue Title Here
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Status:     In Progress
Assignee:   John Doe

Description:
  Issue description text
  with multiple lines
  properly indented.
```

## Examples

```scala
// From issue.scala - displaying an issue
fetchIssue(issueId, config) match
  case Right(issue) =>
    val formatted = IssueFormatter.format(issue)
    println(formatted)
  case Left(error) =>
    Output.error(error)

// Formatting directly
val issue = Issue(
  id = "IWLE-123",
  title = "Fix login bug",
  status = "In Progress",
  assignee = Some("Developer"),
  description = Some("Users cannot log in\nwith special characters")
)
println(IssueFormatter.format(issue))
```

## Notes

- Uses Unicode box-drawing characters for visual appeal
- Description is indented with 2 spaces per line
- Assignee shows "None" when not set
- Description section only shown when present
