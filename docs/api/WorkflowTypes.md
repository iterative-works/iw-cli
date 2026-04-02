# WorkflowTypes

> Domain models for workflow progress tracking and review state.

## Import

```scala
import iw.core.model.*
```

## API

### ReviewArtifact

```scala
case class ReviewArtifact(
  label: String,    // Human-readable label (e.g., "Analysis", "Phase Context")
  path: String      // Relative path from worktree root
)
```

### Display

```scala
case class Display(
  text: String,           // Primary status label shown in badge
  subtext: Option[String],// Optional secondary information
  displayType: String     // Category: info, success, warning, error, progress
)
```

### Badge

```scala
case class Badge(
  label: String,     // Short text shown on the badge
  badgeType: String  // Color category: info, success, warning, error, progress
)
```

### TaskList

```scala
case class TaskList(
  label: String,  // Human-readable name for the task list
  path: String    // Relative path from project root to the markdown file
)
```

### ReviewState

```scala
case class ReviewState(
  display: Option[Display],          // Workflow-controlled presentation instructions
  badges: Option[List[Badge]],       // Contextual badges
  taskLists: Option[List[TaskList]], // Task list file references
  needsAttention: Option[Boolean],   // Flag indicating workflow needs human input
  message: Option[String],           // Prominent notification for the user
  artifacts: List[ReviewArtifact],   // Artifacts available for review
  activity: Option[String],          // "working" | "waiting" — scheduling signal
  workflowType: Option[String]       // "agile" | "waterfall" | "diagnostic"
)
```

### PhaseInfo

```scala
case class PhaseInfo(
  phaseNumber: Int,
  phaseName: String,
  taskFilePath: String,
  totalTasks: Int,
  completedTasks: Int
):
  def isComplete: Boolean       // All tasks done
  def isInProgress: Boolean     // Some tasks done
  def notStarted: Boolean       // No tasks done
  def progressPercentage: Int   // 0-100
```

### WorkflowProgress

```scala
case class WorkflowProgress(
  currentPhase: Option[Int],
  totalPhases: Int,
  phases: List[PhaseInfo],
  overallCompleted: Int,
  overallTotal: Int
):
  def currentPhaseInfo: Option[PhaseInfo]
  def overallPercentage: Int    // 0-100
```

### PRState and PullRequestData

```scala
enum PRState:
  case Open, Merged, Closed

case class PullRequestData(
  url: String,
  state: PRState,
  number: Int,
  title: String
):
  def stateBadgeClass: String   // "pr-open", "pr-merged", "pr-closed"
  def stateBadgeText: String    // "Open", "Merged", "Closed"
```

## Examples

```scala
// Check phase progress
val phase = PhaseInfo(1, "Setup", "/path/tasks.md", 10, 7)
phase.progressPercentage  // 70
phase.isInProgress        // true
phase.isComplete          // false

// Overall workflow progress
val progress = WorkflowProgress(
  currentPhase = Some(2),
  totalPhases = 3,
  phases = List(phase1, phase2, phase3),
  overallCompleted = 15,
  overallTotal = 30
)
progress.overallPercentage  // 50
```
