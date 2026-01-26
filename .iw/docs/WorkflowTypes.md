# WorkflowTypes

> Domain models for agile workflow progress tracking.

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

### ReviewState

```scala
case class ReviewState(
  status: Option[String],          // e.g., "awaiting_review", "in_review"
  phase: Option[Int],              // Phase number associated with review
  message: Option[String],         // Status message
  artifacts: List[ReviewArtifact]  // Available artifacts for review
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
