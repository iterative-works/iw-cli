# WorktreeTypes

> Value objects for worktree path calculation, registration, and priority.

## Import

```scala
import iw.core.model.*
```

## API

### WorktreePath

Calculate worktree directory names and paths:

```scala
case class WorktreePath(projectName: String, issueId: IssueId):
  def directoryName: String              // e.g., "myproject-IWLE-123"
  def resolve(currentDir: os.Path): os.Path  // Sibling path of current dir
  def sessionName: String                // Same as directoryName (for tmux)
```

### WorktreeRegistration

Registered worktree metadata for dashboard tracking:

```scala
case class WorktreeRegistration(
  issueId: String,
  path: String,
  trackerType: String,
  team: String,
  registeredAt: Instant,
  lastSeenAt: Instant
)

object WorktreeRegistration:
  def create(
    issueId: String,
    path: String,
    trackerType: String,
    team: String,
    registeredAt: Instant,
    lastSeenAt: Instant
  ): Either[String, WorktreeRegistration]
```

### WorktreePriority

Priority scoring for dashboard refresh ordering:

```scala
object WorktreePriority:
  def priorityScore(registration: WorktreeRegistration, now: Instant): Long
```

Higher score = more recent activity = higher priority.

## Examples

```scala
// From start.scala - creating worktree paths
val worktreePath = WorktreePath(config.projectName, issueId)
val targetPath = worktreePath.resolve(currentDir)
val sessionName = worktreePath.sessionName

// Directory name example
val path = WorktreePath("iw-cli", issueId)
path.directoryName  // "iw-cli-IWLE-123"
path.resolve(os.pwd)  // /home/user/projects/iw-cli-IWLE-123
```
