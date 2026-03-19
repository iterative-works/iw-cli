# WorktreeSummary

> Worktree metadata for `iw worktrees` output, combining cached issue, PR, workflow, and progress data.

## Import

```scala
import iw.core.model.*
```

## API

```scala
case class WorktreeSummary(
  // Identity
  issueId: String,                    // Issue identifier (e.g., "IWLE-123")
  path: String,                       // Absolute path to worktree directory

  // Issue metadata (from issueCache)
  issueTitle: Option[String],         // Issue title, if available
  issueStatus: Option[String],        // Issue status (e.g., "In Progress"), if available

  // URLs
  issueUrl: Option[String],           // Direct link to issue in tracker
  prUrl: Option[String],              // Direct link to pull request

  // PR state (from prCache)
  prState: Option[String],            // "Open", "Merged", "Closed"

  // Workflow state (from reviewStateCache)
  activity: Option[String],           // "working" | "waiting" — scheduling signal
  workflowType: Option[String],       // "agile" | "waterfall" | "diagnostic"
  workflowDisplay: Option[String],    // Workflow state display text
  needsAttention: Boolean,            // True if review state needs human input

  // Progress (from progressCache)
  currentPhase: Option[Int],          // Current phase number (1-based)
  totalPhases: Option[Int],           // Total number of phases
  completedTasks: Option[Int],        // Completed task count across all phases
  totalTasks: Option[Int],            // Total task count across all phases

  // Timestamps (from WorktreeRegistration)
  registeredAt: Option[String],       // ISO 8601 timestamp when worktree was registered
  lastActivityAt: Option[String]      // ISO 8601 timestamp of last seen activity
) derives ReadWriter
```

## Data Sources

`WorktreeSummary` is a derived view — not persisted. Built fresh from `ServerState` caches each time `iw worktrees` runs:

| Field group | Source |
|-------------|--------|
| Identity | `WorktreeRegistration` |
| Issue metadata | `issueCache` → `IssueData` |
| URLs | `issueCache` → `IssueData.url`, `prCache` → `PullRequestData.url` |
| PR state | `prCache` → `PullRequestData` |
| Workflow state | `reviewStateCache` → `ReviewState` |
| Progress | `progressCache` → `WorkflowProgress` |
| Timestamps | `WorktreeRegistration.registeredAt`, `.lastActivityAt` |

## Examples

```scala
// Minimal worktree (only required fields)
val wt = WorktreeSummary(
  issueId = "IW-42",
  path = "/home/user/project-IW-42",
  issueTitle = None, issueStatus = None,
  issueUrl = None, prUrl = None, prState = None,
  activity = None, workflowType = None,
  workflowDisplay = None, needsAttention = false,
  currentPhase = None, totalPhases = None,
  completedTasks = None, totalTasks = None,
  registeredAt = None, lastActivityAt = None
)

// Fully populated worktree
val wt = WorktreeSummary(
  issueId = "IW-42",
  path = "/home/user/project-IW-42",
  issueTitle = Some("Add caching layer"),
  issueStatus = Some("In Progress"),
  issueUrl = Some("https://github.com/org/repo/issues/42"),
  prUrl = Some("https://github.com/org/repo/pull/99"),
  prState = Some("Open"),
  activity = Some("working"),
  workflowType = Some("waterfall"),
  workflowDisplay = Some("Phase 2: Implementing"),
  needsAttention = false,
  currentPhase = Some(2),
  totalPhases = Some(4),
  completedTasks = Some(8),
  totalTasks = Some(20),
  registeredAt = Some("2026-03-15T10:00:00Z"),
  lastActivityAt = Some("2026-03-18T14:30:00Z")
)
```
