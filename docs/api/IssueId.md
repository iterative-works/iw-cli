# IssueId

> Opaque type for validated issue identifiers in TEAM-123 format.

## Import

```scala
import iw.core.model.*
```

## API

### IssueId.parse(raw: String, defaultTeam: Option[String] = None): Either[String, IssueId]

Parse a raw string into a validated IssueId. Accepts TEAM-123 format (case-insensitive).
For GitHub/GitLab trackers with a configured team prefix, numeric input (e.g., "123") is composed with the prefix.

### IssueId.forGitHub(teamPrefix: String, number: Int): Either[String, IssueId]

Create an IssueId from a GitHub/GitLab issue number and team prefix.

### IssueId.fromBranch(branchName: String): Either[String, IssueId]

Extract IssueId from a branch name like "TEAM-123" or "TEAM-123-description".

### Extension Methods

```scala
extension (issueId: IssueId)
  def value: String        // Get the raw string value
  def toBranchName: String // Convert to git branch name
  def team: String         // Extract team prefix (e.g., "IWLE" from "IWLE-123")
```

## Examples

```scala
// From issue.scala - parsing issue ID with team prefix fallback
val teamPrefix = config.trackerType match
  case IssueTrackerType.GitHub | IssueTrackerType.GitLab =>
    config.teamPrefix
  case _ => None
IssueId.parse(args.head, teamPrefix)

// From start.scala - extracting from parsed ID
val branchName = issueId.toBranchName
val worktreePath = WorktreePath(config.projectName, issueId)

// From branch name
GitAdapter.getCurrentBranch(currentDir).flatMap(IssueId.fromBranch)
```
