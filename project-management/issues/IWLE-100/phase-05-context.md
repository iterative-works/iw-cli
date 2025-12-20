# Phase 5 Context: Display phase and task progress

**Issue:** IWLE-100
**Phase:** 5 of 7
**Story:** Story 4 - Display phase and task progress
**Estimated Effort:** 8-12 hours
**Created:** 2025-12-20

---

## Goals

This phase adds workflow progress tracking to the dashboard by parsing markdown task files from the agile iterative workflow. The primary objectives are:

1. **Phase detection**: Determine current phase number and name from task file structure
2. **Task parsing**: Extract checkbox tasks from markdown files (`- [ ]`, `- [x]`)
3. **Progress calculation**: Count completed vs total tasks for percentage display
4. **Dashboard enhancement**: Add phase info and progress bar to worktree cards
5. **Error handling**: Gracefully handle missing or malformed task files
6. **Cache-aware parsing**: Don't re-parse files unless changed (use mtime)

After this phase, developers will see which implementation phase each worktree is on and how far along they are in completing tasks.

---

## Scope

### In Scope

**Workflow Progress Model:**
- `WorkflowProgress` domain object (current phase, total phases, phase name, completed tasks, total tasks)
- `PhaseInfo` domain object (phase number, phase name, task file path)
- Progress percentage calculation: `(completed / total) * 100`

**Task File Parsing:**
- `MarkdownTaskParser` application layer service
- Parse checkbox syntax: `- [ ]` (incomplete) and `- [x]` (complete)
- Ignore other bullet styles (`*`, `+`, numbered lists)
- Handle multi-line tasks correctly
- Extract phase name from file header or filename

**Phase Detection:**
- Read `project-management/issues/{ISSUE-ID}/` directory
- Detect phase files: `phase-01-tasks.md`, `phase-02-tasks.md`, etc.
- Determine current phase from highest-numbered phase file with incomplete tasks
- Extract total phase count from `tasks.md` phase index

**State Extension:**
- Add `progressCache: Map[String, CachedProgress]` to ServerState
- Cache parsed progress with file modification timestamp
- Re-parse only if file mtime changed (avoid unnecessary I/O)
- TTL: No time-based expiry (only mtime-based invalidation)

**Dashboard Enhancements:**
- Update `WorktreeListView` to display phase info: "Phase 2/4: Validation errors"
- Add progress bar component (HTML + CSS)
- Show task count: "8/15 tasks"
- Display progress percentage in progress bar
- Handle edge cases: no tasks file, empty tasks, all phases complete

**Progress Fetching Service:**
- `WorkflowProgressService` application layer service
- `fetchProgress(worktreePath, cache, issueId): Either[String, WorkflowProgress]`
- Read task files from filesystem
- Parse markdown with MarkdownTaskParser
- Compute progress percentage
- Return cached progress if file unchanged (mtime check)

### Out of Scope

**Not in Phase 5 (deferred to later phases):**
- Git status detection (Phase 6)
- PR link detection (Phase 6)
- Unregister endpoint and auto-pruning (Phase 7)
- Real-time file watching (progress updates on page refresh only)
- Task file editing from dashboard (read-only)
- Nested task support (only top-level checkboxes)
- Custom task file formats (standard agile workflow format only)

**Technical Scope Boundaries:**
- Parse only standard checkbox format: `- [ ]` and `- [x]`
- Phase files must be named `phase-NN-tasks.md` (two-digit zero-padded)
- Main task index at `project-management/issues/{ISSUE-ID}/tasks.md`
- No regex complexity (simple line-by-line parsing)
- Cache invalidation: mtime only, no file content hashing

---

## Dependencies

### Prerequisites from Phase 1, 2, 3, 4

**Must exist and work correctly:**
- `CaskServer` with dashboard rendering on `GET /`
- `ServerState` domain model with worktree map and issue cache
- `StateRepository` for JSON persistence with atomic writes
- `WorktreeListView` Scalatags template for worktree cards
- `WorktreeRegistration` with issue ID, path, tracker type, team
- `DashboardService` for dashboard HTML generation
- `IssueData` and issue cache from Phase 4
- Worktree path stored in WorktreeRegistration

**Available for reuse:**
- upickle JSON serialization (used by StateRepository)
- Existing error handling patterns (Either-based)
- File I/O utilities from Scala standard library
- Instant timestamps for mtime comparison

**Filesystem structure:**
```
worktree-path/
  project-management/
    issues/
      {ISSUE-ID}/
        tasks.md                    # Main task index with phase list
        phase-01-tasks.md          # Phase 1 task file
        phase-02-tasks.md          # Phase 2 task file (current)
        phase-03-tasks.md          # Phase 3 task file
        ...
```

### External Dependencies

**No new dependencies required:**
- Standard library File I/O: `scala.io.Source`, `java.nio.file.*`
- upickle already in project.scala
- java.time.Instant for mtime comparison

**File Format (from agile workflow):**

Example `tasks.md`:
```markdown
# Implementation Tasks: Add server dashboard for worktree monitoring

**Issue:** IWLE-100
**Status:** 2/7 phases complete (28%)

## Phase Index

- [x] Phase 1: View basic dashboard (Est: 8-12h)
- [x] Phase 2: Auto-registration (Est: 6-8h)
- [ ] Phase 3: Server lifecycle (Est: 6-8h)
- [ ] Phase 4: Issue details (Est: 6-8h)
...
```

Example `phase-02-tasks.md`:
```markdown
# Phase 2 Tasks: Automatic worktree registration

**Issue:** IWLE-100
**Phase:** 2 of 7
**Estimated Effort:** 6-8 hours

## Setup

- [x] Create test directory
- [x] Verify munit available

## Part 1: Domain Logic

- [x] Write test for register method
- [x] Implement WorktreeRegistrationService
- [ ] Add validation tests
...
```

---

## Technical Approach

### High-Level Strategy

Phase 5 follows the **Functional Core / Imperative Shell** pattern:

**Domain Layer (Pure):**
- `PhaseInfo` case class (phase number, phase name, task file path, total tasks, completed tasks)
- `WorkflowProgress` case class (current phase, total phases, phase list, completion percentage)
- `CachedProgress` case class (WorkflowProgress + file mtime)
- Progress calculation: pure functions for percentage, completion status

**Application Layer (Pure):**
- `MarkdownTaskParser` with pure functions:
  - `parseTasks(lines: Seq[String]): TaskCount` - count checkboxes
  - `extractPhaseName(lines: Seq[String]): Option[String]` - parse header
- `WorkflowProgressService` with pure functions:
  - `computeProgress(phases: List[PhaseInfo]): WorkflowProgress`
  - `determineCurrentPhase(phases: List[PhaseInfo]): Option[Int]`
  - `isCacheValid(cached: CachedProgress, currentMtime: Long): Boolean`

**Infrastructure Layer (Effects):**
- File I/O to read markdown files
- Filesystem operations: list directory, check file existence, get mtime
- StateRepository extension for progress cache serialization

**Presentation Layer:**
- `DashboardService` calls `WorkflowProgressService` for each worktree
- `WorktreeListView` enhanced to render phase info and progress bar
- Progress bar HTML/CSS component

### Architecture Overview

```
DashboardService (Presentation)
       ↓
   WorkflowProgressService (Application)
       ↓
   Check progress cache validity (mtime)
       ↓
   [Cache valid?] → Yes → Return cached WorkflowProgress
       ↓ No
   Read task files from filesystem
       ↓
   MarkdownTaskParser.parseTasks()
       ↓
   Compute current phase and progress
       ↓
   Return WorkflowProgress + update cache
```

**Error handling philosophy:**
- Missing task files → No progress shown (graceful fallback)
- Malformed markdown → Best-effort parsing (count what's valid)
- Non-existent worktree path → Return Left(error), card shows "Path not found"
- Empty task files → 0/0 tasks, no progress bar

### Key Components

#### 1. PhaseInfo (Domain Layer)

**File:** `.iw/core/PhaseInfo.scala`

**Purpose:** Represents a single phase with task counts.

**Interface:**
```scala
case class PhaseInfo(
  phaseNumber: Int,
  phaseName: String,
  taskFilePath: String,
  totalTasks: Int,
  completedTasks: Int
):
  def isComplete: Boolean = totalTasks > 0 && completedTasks == totalTasks
  def isInProgress: Boolean = totalTasks > 0 && completedTasks > 0 && completedTasks < totalTasks
  def notStarted: Boolean = totalTasks > 0 && completedTasks == 0
  def progressPercentage: Int = if totalTasks == 0 then 0 else (completedTasks * 100) / totalTasks
```

**Implementation notes:**
- Pure domain object with computed properties
- Progress percentage rounded down to integer
- Empty task file → totalTasks = 0, no progress

#### 2. WorkflowProgress (Domain Layer)

**File:** `.iw/core/WorkflowProgress.scala`

**Purpose:** Complete workflow progress across all phases.

**Interface:**
```scala
case class WorkflowProgress(
  currentPhase: Option[Int],       // Phase number currently in progress (1-based)
  totalPhases: Int,                 // Total number of phases
  phases: List[PhaseInfo],          // All phase details
  overallCompleted: Int,            // Total completed tasks across all phases
  overallTotal: Int                 // Total tasks across all phases
):
  def currentPhaseInfo: Option[PhaseInfo] =
    currentPhase.flatMap(num => phases.find(_.phaseNumber == num))
  
  def overallPercentage: Int =
    if overallTotal == 0 then 0 else (overallCompleted * 100) / overallTotal
```

**Implementation notes:**
- Current phase = first phase with incomplete tasks (or last phase if all complete)
- Overall progress = sum of all phase task counts
- Empty workflow → totalPhases = 0, no current phase

#### 3. CachedProgress (Domain Layer)

**File:** `.iw/core/CachedProgress.scala`

**Purpose:** Cache wrapper with file modification timestamp.

**Interface:**
```scala
case class CachedProgress(
  progress: WorkflowProgress,
  filesMtime: Map[String, Long]  // Map of file path -> mtime (epoch millis)
)

object CachedProgress:
  def isValid(cached: CachedProgress, currentMtimes: Map[String, Long]): Boolean =
    currentMtimes.forall { case (path, mtime) =>
      cached.filesMtime.get(path).contains(mtime)
    }
```

**Implementation notes:**
- Cache valid if ALL task file mtimes match
- If any file changed, re-parse entire workflow
- No TTL (only mtime-based invalidation)

#### 4. MarkdownTaskParser (Application Layer)

**File:** `.iw/core/MarkdownTaskParser.scala`

**Purpose:** Pure functions for parsing markdown task files.

**Interface:**
```scala
case class TaskCount(total: Int, completed: Int)

object MarkdownTaskParser:
  /** Parse markdown lines and count checkbox tasks.
    * Recognizes: "- [ ]" (incomplete) and "- [x]" (complete)
    * Ignores: other bullet styles, nested lists, non-checkbox bullets
    */
  def parseTasks(lines: Seq[String]): TaskCount =
    val checkboxPattern = "^\\s*-\\s*\\[([ xX])\\]".r
    val tasks = lines.flatMap { line =>
      checkboxPattern.findFirstMatchIn(line).map { m =>
        val isComplete = m.group(1).toLowerCase == "x"
        (1, if isComplete then 1 else 0)
      }
    }
    val (total, completed) = tasks.foldLeft((0, 0)) { case ((t, c), (taskTotal, taskCompleted)) =>
      (t + taskTotal, c + taskCompleted)
    }
    TaskCount(total, completed)
  
  /** Extract phase name from markdown header.
    * Looks for: "# Phase N: Phase Name" or "# Phase N Tasks: Phase Name"
    * Returns phase name without prefix.
    */
  def extractPhaseName(lines: Seq[String]): Option[String] =
    val phaseHeaderPattern = "^#\\s+Phase\\s+\\d+.*:\\s*(.+)$".r
    lines.collectFirst {
      case phaseHeaderPattern(name) => name.trim
    }
```

**Implementation notes:**
- Case-insensitive checkbox detection (`[x]` or `[X]`)
- Ignores indentation (allows nested tasks if workflow adds them later)
- Fallback: If no phase name in header, derive from filename (e.g., "phase-02-tasks.md" → "Phase 2")

#### 5. WorkflowProgressService (Application Layer)

**File:** `.iw/core/WorkflowProgressService.scala`

**Purpose:** Pure business logic for workflow progress computation.

**Interface:**
```scala
object WorkflowProgressService:
  /** Fetch workflow progress with cache support.
    * Checks file mtimes and re-parses if any file changed.
    * Returns WorkflowProgress for dashboard display.
    */
  def fetchProgress(
    issueId: String,
    worktreePath: String,
    cache: Map[String, CachedProgress],
    readFile: String => Either[String, Seq[String]],  // File I/O function
    getMtime: String => Either[String, Long]           // Mtime function
  ): Either[String, WorkflowProgress]
  
  /** Compute workflow progress from phase info list.
    * Determines current phase (first with incomplete tasks).
    * Sums overall task counts.
    */
  def computeProgress(phases: List[PhaseInfo]): WorkflowProgress
  
  /** Determine current phase number from phase list.
    * Returns first phase with incomplete tasks, or last phase if all complete, or None if no phases.
    */
  def determineCurrentPhase(phases: List[PhaseInfo]): Option[Int]
```

**Implementation logic for `fetchProgress`:**
```scala
1. Build task file paths: {worktreePath}/project-management/issues/{issueId}/phase-NN-tasks.md
2. Detect phase files in directory (list files, filter phase-*.md)
3. Get current mtimes for all phase files
4. Check cache validity:
   If cache exists && CachedProgress.isValid(cached, currentMtimes):
     Return cached.progress
5. Else:
   For each phase file:
     lines = readFile(phaseFilePath)
     taskCount = MarkdownTaskParser.parseTasks(lines)
     phaseName = MarkdownTaskParser.extractPhaseName(lines).getOrElse(deriveName)
     Create PhaseInfo(phaseNumber, phaseName, path, taskCount.total, taskCount.completed)
   
   progress = computeProgress(phaseInfoList)
   Return progress
```

**Implementation notes:**
- Completely pure: receives file I/O functions from caller
- No direct filesystem access in service (FCIS)
- Caller (DashboardService) provides readFile and getMtime wrappers
- Phase number extracted from filename: `phase-02-tasks.md` → 2

#### 6. ServerState Extension

**File:** `.iw/core/ServerState.scala` (modify existing)

**Changes:**
```scala
case class ServerState(
  worktrees: Map[String, WorktreeRegistration],
  issueCache: Map[String, CachedIssue],
  progressCache: Map[String, CachedProgress] = Map.empty  // NEW
)
```

**JSON format (state.json):**
```json
{
  "worktrees": { /* ... */ },
  "issueCache": { /* ... */ },
  "progressCache": {
    "IWLE-123": {
      "progress": {
        "currentPhase": 2,
        "totalPhases": 7,
        "phases": [
          {
            "phaseNumber": 1,
            "phaseName": "View basic dashboard",
            "taskFilePath": "/path/to/worktree/project-management/issues/IWLE-123/phase-01-tasks.md",
            "totalTasks": 25,
            "completedTasks": 25
          },
          {
            "phaseNumber": 2,
            "phaseName": "Auto-registration",
            "taskFilePath": "...",
            "totalTasks": 15,
            "completedTasks": 8
          }
        ],
        "overallCompleted": 33,
        "overallTotal": 120
      },
      "filesMtime": {
        "/path/to/phase-01-tasks.md": 1703001234567,
        "/path/to/phase-02-tasks.md": 1703001234890
      }
    }
  }
}
```

**Implementation notes:**
- StateRepository already handles serialization via upickle
- Add ReadWriter for CachedProgress, WorkflowProgress, PhaseInfo
- Atomic writes continue to work (no changes to StateRepository)

#### 7. DashboardService Integration

**File:** `.iw/core/DashboardService.scala` (modify existing)

**Changes:**
```scala
object DashboardService:
  def renderDashboard(
    worktrees: List[WorktreeRegistration],
    issueCache: Map[String, CachedIssue],
    progressCache: Map[String, CachedProgress],
    config: Config
  ): Tag =
    val now = Instant.now()
    
    val worktreesWithData = worktrees.map { wt =>
      val issueData = fetchIssueForWorktree(wt, issueCache, now, config)
      val progress = fetchProgressForWorktree(wt, progressCache)
      (wt, issueData, progress)
    }
    
    WorktreeListView.render(worktreesWithData, now)
  
  private def fetchProgressForWorktree(
    wt: WorktreeRegistration,
    cache: Map[String, CachedProgress]
  ): Option[WorkflowProgress] =
    val readFile = (path: String) => Try {
      scala.io.Source.fromFile(path).getLines().toSeq
    }.toEither.left.map(_.getMessage)
    
    val getMtime = (path: String) => Try {
      java.nio.file.Files.getLastModifiedTime(
        java.nio.file.Paths.get(path)
      ).toMillis
    }.toEither.left.map(_.getMessage)
    
    WorkflowProgressService.fetchProgress(
      wt.issueId,
      wt.path,
      cache,
      readFile,
      getMtime
    ).toOption
```

**Implementation notes:**
- Wraps filesystem I/O in Try/Either for error handling
- Passes file I/O functions to WorkflowProgressService
- Handles errors by showing no progress (graceful fallback)

#### 8. WorktreeListView Enhancement

**File:** `.iw/core/WorktreeListView.scala` (modify existing)

**Changes:**
```scala
def renderWorktreeCard(
  wt: WorktreeRegistration,
  issueData: Option[(IssueData, Boolean)],
  progress: Option[WorkflowProgress],
  now: Instant
): Tag =
  div(cls := "worktree-card")(
    h3(issueData.map(_._1.title).getOrElse("Issue data unavailable")),
    p(cls := "issue-id")(
      a(href := issueData.map(_._1.url).getOrElse("#"))(wt.issueId)
    ),
    
    // Phase info (if available)
    progress.flatMap(_.currentPhaseInfo).map { phaseInfo =>
      div(cls := "phase-info")(
        span(cls := "phase-label")(
          s"Phase ${phaseInfo.phaseNumber}/${progress.get.totalPhases}: ${phaseInfo.phaseName}"
        ),
        div(cls := "progress-container")(
          div(
            cls := "progress-bar",
            attr("style") := s"width: ${phaseInfo.progressPercentage}%"
          ),
          span(cls := "progress-text")(
            s"${phaseInfo.completedTasks}/${phaseInfo.totalTasks} tasks"
          )
        )
      )
    },
    
    // Issue details (existing from Phase 4)
    /* ... */
  )
```

**Styling (inline CSS):**
```scala
style(raw("""
  .phase-info {
    margin: 8px 0;
    font-size: 0.9em;
  }
  
  .phase-label {
    font-weight: 600;
    color: #495057;
    display: block;
    margin-bottom: 4px;
  }
  
  .progress-container {
    position: relative;
    background: #e9ecef;
    border-radius: 4px;
    height: 20px;
    overflow: hidden;
  }
  
  .progress-bar {
    background: linear-gradient(90deg, #51cf66, #37b24d);
    height: 100%;
    transition: width 0.3s ease;
  }
  
  .progress-text {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    text-align: center;
    line-height: 20px;
    font-size: 0.85em;
    color: #212529;
    font-weight: 600;
  }
"""))
```

---

## Files to Modify/Create

### New Files

**Domain Layer:**
- `.iw/core/PhaseInfo.scala` - Phase metadata with task counts
- `.iw/core/WorkflowProgress.scala` - Complete workflow progress model
- `.iw/core/CachedProgress.scala` - Cache wrapper with mtime validation

**Application Layer:**
- `.iw/core/MarkdownTaskParser.scala` - Pure functions for markdown parsing
- `.iw/core/WorkflowProgressService.scala` - Pure business logic for progress computation

**Tests:**
- `.iw/core/test/PhaseInfoTest.scala` - Unit tests for PhaseInfo model
- `.iw/core/test/WorkflowProgressTest.scala` - Unit tests for WorkflowProgress model
- `.iw/core/test/CachedProgressTest.scala` - Unit tests for cache validation logic
- `.iw/core/test/MarkdownTaskParserTest.scala` - Unit tests for parsing logic
- `.iw/core/test/WorkflowProgressServiceTest.scala` - Unit tests for progress service

### Modified Files

**Domain Layer:**
- `.iw/core/ServerState.scala`:
  - Add `progressCache: Map[String, CachedProgress]` field
  - Add upickle ReadWriter instances for new types

**Application Layer:**
- `.iw/core/DashboardService.scala`:
  - Add progress fetching logic for each worktree
  - Pass progress data to WorktreeListView
  - Handle file I/O errors gracefully

**Presentation Layer:**
- `.iw/core/WorktreeListView.scala`:
  - Update `renderWorktreeCard` to display phase info
  - Add progress bar component
  - Add CSS for progress bar and phase label

**Infrastructure Layer:**
- `.iw/core/StateRepository.scala`:
  - Add upickle serializers for CachedProgress, WorkflowProgress, PhaseInfo

---

## Testing Strategy

### Unit Tests

**PhaseInfoTest:**
```scala
test("isComplete returns true when all tasks done") {
  val phase = PhaseInfo(1, "Test", "/path", totalTasks = 10, completedTasks = 10)
  assert(phase.isComplete)
}

test("isInProgress returns true for partial completion") {
  val phase = PhaseInfo(1, "Test", "/path", totalTasks = 10, completedTasks = 5)
  assert(phase.isInProgress)
  assert(!phase.isComplete)
  assert(!phase.notStarted)
}

test("progressPercentage calculates correctly") {
  val phase = PhaseInfo(1, "Test", "/path", totalTasks = 15, completedTasks = 8)
  assertEquals(phase.progressPercentage, 53) // 8/15 = 53.33% → 53
}

test("progressPercentage returns 0 for empty phase") {
  val phase = PhaseInfo(1, "Test", "/path", totalTasks = 0, completedTasks = 0)
  assertEquals(phase.progressPercentage, 0)
}
```

**WorkflowProgressTest:**
```scala
test("currentPhaseInfo returns current phase details") {
  val phase1 = PhaseInfo(1, "Phase 1", "/p1", 10, 10)
  val phase2 = PhaseInfo(2, "Phase 2", "/p2", 15, 8)
  val progress = WorkflowProgress(
    currentPhase = Some(2),
    totalPhases = 2,
    phases = List(phase1, phase2),
    overallCompleted = 18,
    overallTotal = 25
  )
  
  assertEquals(progress.currentPhaseInfo.map(_.phaseNumber), Some(2))
  assertEquals(progress.currentPhaseInfo.map(_.phaseName), Some("Phase 2"))
}

test("overallPercentage calculates across all phases") {
  val progress = WorkflowProgress(
    currentPhase = Some(2),
    totalPhases = 3,
    phases = List(/* ... */),
    overallCompleted = 33,
    overallTotal = 120
  )
  
  assertEquals(progress.overallPercentage, 27) // 33/120 = 27.5% → 27
}
```

**CachedProgressTest:**
```scala
test("isValid returns true when all mtimes match") {
  val filesMtime = Map(
    "/path/phase-01.md" -> 1000L,
    "/path/phase-02.md" -> 2000L
  )
  val cached = CachedProgress(mockProgress, filesMtime)
  val currentMtimes = Map(
    "/path/phase-01.md" -> 1000L,
    "/path/phase-02.md" -> 2000L
  )
  
  assert(CachedProgress.isValid(cached, currentMtimes))
}

test("isValid returns false when any mtime changed") {
  val filesMtime = Map("/path/phase-01.md" -> 1000L)
  val cached = CachedProgress(mockProgress, filesMtime)
  val currentMtimes = Map("/path/phase-01.md" -> 2000L) // Changed
  
  assert(!CachedProgress.isValid(cached, currentMtimes))
}

test("isValid returns false when new file added") {
  val filesMtime = Map("/path/phase-01.md" -> 1000L)
  val cached = CachedProgress(mockProgress, filesMtime)
  val currentMtimes = Map(
    "/path/phase-01.md" -> 1000L,
    "/path/phase-02.md" -> 2000L  // New file
  )
  
  assert(!CachedProgress.isValid(cached, currentMtimes))
}
```

**MarkdownTaskParserTest:**
```scala
test("parseTasks counts incomplete checkbox") {
  val lines = Seq("- [ ] Task 1", "- [ ] Task 2")
  val count = MarkdownTaskParser.parseTasks(lines)
  assertEquals(count.total, 2)
  assertEquals(count.completed, 0)
}

test("parseTasks counts completed checkbox") {
  val lines = Seq("- [x] Task 1", "- [X] Task 2") // Case insensitive
  val count = MarkdownTaskParser.parseTasks(lines)
  assertEquals(count.total, 2)
  assertEquals(count.completed, 2)
}

test("parseTasks counts mixed checkboxes") {
  val lines = Seq(
    "- [x] Task 1",
    "- [ ] Task 2",
    "- [x] Task 3",
    "- [ ] Task 4"
  )
  val count = MarkdownTaskParser.parseTasks(lines)
  assertEquals(count.total, 4)
  assertEquals(count.completed, 2)
}

test("parseTasks ignores non-checkbox bullets") {
  val lines = Seq(
    "- [x] Checkbox task",
    "- Regular bullet",
    "* Asterisk bullet",
    "+ Plus bullet",
    "1. Numbered list"
  )
  val count = MarkdownTaskParser.parseTasks(lines)
  assertEquals(count.total, 1)
  assertEquals(count.completed, 1)
}

test("parseTasks handles indented checkboxes") {
  val lines = Seq(
    "- [x] Task 1",
    "  - [ ] Subtask 1.1",
    "    - [x] Subtask 1.1.1"
  )
  val count = MarkdownTaskParser.parseTasks(lines)
  assertEquals(count.total, 3)
  assertEquals(count.completed, 2)
}

test("extractPhaseName parses header") {
  val lines = Seq(
    "# Phase 2 Tasks: Automatic worktree registration",
    "",
    "Content here"
  )
  val name = MarkdownTaskParser.extractPhaseName(lines)
  assertEquals(name, Some("Automatic worktree registration"))
}

test("extractPhaseName handles alternate format") {
  val lines = Seq("# Phase 3: Server lifecycle management")
  val name = MarkdownTaskParser.extractPhaseName(lines)
  assertEquals(name, Some("Server lifecycle management"))
}

test("extractPhaseName returns None for missing header") {
  val lines = Seq("No header here", "Just content")
  val name = MarkdownTaskParser.extractPhaseName(lines)
  assertEquals(name, None)
}
```

**WorkflowProgressServiceTest:**
```scala
test("computeProgress sums task counts correctly") {
  val phases = List(
    PhaseInfo(1, "Phase 1", "/p1", 10, 10),
    PhaseInfo(2, "Phase 2", "/p2", 15, 8),
    PhaseInfo(3, "Phase 3", "/p3", 20, 0)
  )
  
  val progress = WorkflowProgressService.computeProgress(phases)
  
  assertEquals(progress.overallTotal, 45)
  assertEquals(progress.overallCompleted, 18)
  assertEquals(progress.totalPhases, 3)
}

test("determineCurrentPhase returns first incomplete phase") {
  val phases = List(
    PhaseInfo(1, "Phase 1", "/p1", 10, 10), // Complete
    PhaseInfo(2, "Phase 2", "/p2", 15, 8),  // In progress
    PhaseInfo(3, "Phase 3", "/p3", 20, 0)   // Not started
  )
  
  val current = WorkflowProgressService.determineCurrentPhase(phases)
  assertEquals(current, Some(2))
}

test("determineCurrentPhase returns last phase if all complete") {
  val phases = List(
    PhaseInfo(1, "Phase 1", "/p1", 10, 10),
    PhaseInfo(2, "Phase 2", "/p2", 15, 15)
  )
  
  val current = WorkflowProgressService.determineCurrentPhase(phases)
  assertEquals(current, Some(2))
}

test("determineCurrentPhase returns None for empty list") {
  val current = WorkflowProgressService.determineCurrentPhase(List.empty)
  assertEquals(current, None)
}

test("fetchProgress uses cache when mtimes match") {
  val cachedProgress = mockWorkflowProgress
  val filesMtime = Map("/path/phase-01.md" -> 1000L)
  val cache = Map("ISSUE-123" -> CachedProgress(cachedProgress, filesMtime))
  
  val readFileCalled = scala.collection.mutable.ArrayBuffer[String]()
  val readFile = (path: String) => {
    readFileCalled += path
    Right(Seq.empty)
  }
  val getMtime = (path: String) => Right(1000L) // Same mtime
  
  val result = WorkflowProgressService.fetchProgress(
    "ISSUE-123", "/worktree", cache, readFile, getMtime
  )
  
  assert(result.isRight)
  assert(readFileCalled.isEmpty) // No file reads
}

test("fetchProgress re-parses when mtime changed") {
  val cachedProgress = mockWorkflowProgress
  val filesMtime = Map("/worktree/project-management/issues/ISSUE-123/phase-01-tasks.md" -> 1000L)
  val cache = Map("ISSUE-123" -> CachedProgress(cachedProgress, filesMtime))
  
  val readFile = (path: String) => Right(Seq("- [x] Task 1"))
  val getMtime = (path: String) => Right(2000L) // Changed mtime
  
  val result = WorkflowProgressService.fetchProgress(
    "ISSUE-123", "/worktree", cache, readFile, getMtime
  )
  
  assert(result.isRight)
  // Verify fresh parse happened (implementation-specific assertion)
}
```

### Integration Tests

**StateRepositoryTest (extend existing):**
```scala
test("serialize and deserialize ServerState with progress cache") {
  val phaseInfo = PhaseInfo(1, "Phase 1", "/path", 10, 5)
  val progress = WorkflowProgress(
    currentPhase = Some(1),
    totalPhases = 1,
    phases = List(phaseInfo),
    overallCompleted = 5,
    overallTotal = 10
  )
  val cached = CachedProgress(progress, Map("/path/phase-01.md" -> 1000L))
  
  val state = ServerState(
    worktrees = Map(/* ... */),
    issueCache = Map(/* ... */),
    progressCache = Map("IWLE-123" -> cached)
  )
  
  val repo = StateRepository(tempFile)
  repo.write(state)
  
  val loaded = repo.read()
  assert(loaded.isRight)
  assertEquals(loaded.map(_.progressCache.size).getOrElse(0), 1)
  assertEquals(
    loaded.flatMap(_.progressCache.get("IWLE-123").map(_.progress.totalPhases)),
    Right(1)
  )
}
```

### Manual Testing Scenarios

**Scenario 1: Show current phase and task completion**
1. Register worktree IWLE-123 in Phase 2 of 4 phases
2. Edit phase-02-tasks.md to have 8 completed tasks out of 15 total
3. Load dashboard
4. Verify: Card shows "Phase 2/4: Validation errors" (or actual phase name)
5. Verify: Progress bar at 53% (8/15)
6. Verify: "8/15 tasks" label displayed

**Scenario 2: Progress derived from task files**
1. Register worktree IWLE-123
2. Ensure task files exist: tasks.md, phase-01-tasks.md, phase-02-tasks.md
3. Load dashboard
4. Verify: Server parses markdown files
5. Verify: Checkboxes counted correctly
6. Verify: Progress percentage displayed

**Scenario 3: Cache invalidation on file change**
1. Load dashboard (populates progress cache)
2. Verify state.json contains progress cache with mtime
3. Edit phase-02-tasks.md (add new task checkbox)
4. Reload dashboard
5. Verify: Progress re-parsed (task count updated)
6. Verify: Cache mtime updated in state.json

**Scenario 4: Missing task files handled gracefully**
1. Register worktree with no task files
2. Load dashboard
3. Verify: No phase info displayed (no error)
4. Verify: Dashboard renders other worktrees correctly

**Scenario 5: Empty task file (0/0 tasks)**
1. Create empty phase-01-tasks.md file
2. Load dashboard
3. Verify: No progress bar shown (or 0/0 tasks with 0% bar)
4. Verify: Dashboard doesn't crash

---

## Acceptance Criteria

Phase 5 is complete when:

### Functional Requirements

- [ ] Dashboard displays current phase number and name (e.g., "Phase 2/4: Validation errors")
- [ ] Progress bar shows completion percentage (e.g., 53% for 8/15 tasks)
- [ ] Task count label displayed (e.g., "8/15 tasks")
- [ ] Progress derived from checkbox parsing in markdown files
- [ ] Standard checkbox format recognized: `- [ ]` and `- [x]` (case-insensitive)
- [ ] Other bullet styles ignored (`*`, `+`, numbered lists)
- [ ] Phase name extracted from file header or derived from filename
- [ ] Current phase determined as first phase with incomplete tasks
- [ ] Progress cache stored in state.json with file mtimes
- [ ] Cache invalidated when any task file modified (mtime check)
- [ ] Missing task files show no progress (graceful fallback, no error)
- [ ] Empty task files show 0/0 tasks or no progress bar

### Non-Functional Requirements

- [ ] All unit tests passing (PhaseInfo, WorkflowProgress, CachedProgress, MarkdownTaskParser, WorkflowProgressService)
- [ ] All integration tests passing (StateRepository with progress cache)
- [ ] Manual scenarios verified (task counting, cache invalidation, missing files)
- [ ] Progress parsing completes within 100ms for typical workflow (3-7 phase files)
- [ ] File I/O errors don't crash dashboard
- [ ] Code follows FCIS pattern (pure domain/application, effects in infrastructure)
- [ ] No new compilation warnings
- [ ] Git commits follow TDD: test → implementation → refactor

### Quality Checks

- [ ] Code review self-check: Are file I/O functions injected (not called in pure functions)?
- [ ] Code review self-check: Does cache invalidation work correctly (mtime comparison)?
- [ ] Code review self-check: Are edge cases handled (no files, empty files, malformed markdown)?
- [ ] Documentation: Update implementation-log.md with Phase 5 summary
- [ ] Documentation: Comment complex parts (markdown parsing regex, current phase detection)

---

## Implementation Sequence

**Recommended order (TDD):**

### Step 1: Domain Models (1-2h)

1. Write `PhaseInfoTest.scala` with completion status and percentage tests
2. Implement `PhaseInfo.scala` case class with computed properties
3. Write `WorkflowProgressTest.scala` with overall progress tests
4. Implement `WorkflowProgress.scala` case class
5. Write `CachedProgressTest.scala` with mtime validation tests
6. Implement `CachedProgress.scala` with validation logic
7. Verify all unit tests pass
8. Commit: "feat(IWLE-100): Add WorkflowProgress domain models"

### Step 2: Markdown Parsing (2-3h)

9. Write `MarkdownTaskParserTest.scala` with checkbox counting tests
10. Implement `MarkdownTaskParser.parseTasks()` with regex matching
11. Test edge cases: indented tasks, case-insensitive, non-checkbox bullets
12. Write tests for `extractPhaseName()` with various header formats
13. Implement `extractPhaseName()` with regex extraction
14. Verify all parser tests pass
15. Commit: "feat(IWLE-100): Add MarkdownTaskParser for checkbox counting"

### Step 3: Progress Service Logic (2-3h)

16. Write `WorkflowProgressServiceTest.scala` with cache scenarios
17. Implement `WorkflowProgressService.computeProgress()` pure function
18. Implement `determineCurrentPhase()` logic
19. Test cache validation: valid cache, changed mtime, new file added
20. Implement `fetchProgress()` with file I/O injection
21. Test all progress computation paths
22. Verify unit tests pass
23. Commit: "feat(IWLE-100): Add WorkflowProgressService for progress computation"

### Step 4: State Repository Extension (0.5-1h)

24. Extend `ServerState` with progressCache field
25. Add upickle ReadWriter instances in StateRepository
26. Write integration test for serialization/deserialization
27. Verify state.json correctly stores and loads progress cache
28. Commit: "feat(IWLE-100): Extend ServerState with progress cache"

### Step 5: Dashboard Integration (2-3h)

29. Modify `DashboardService.renderDashboard()` to fetch progress data
30. Build file I/O wrapper functions (readFile, getMtime)
31. Pass progress data to WorktreeListView
32. Write integration test for DashboardService with progress data
33. Verify tests pass
34. Commit: "feat(IWLE-100): Integrate progress fetching in dashboard"

35. Modify `WorktreeListView.renderWorktreeCard()` to display phase info
36. Add progress bar HTML component
37. Add task count label
38. Add inline CSS for progress bar and phase label
39. Manual test: Load dashboard, verify progress bar appears
40. Commit: "feat(IWLE-100): Enhance worktree cards with progress bar"

### Step 6: File System Integration (1h)

41. Implement phase file detection (list directory, filter phase-*.md)
42. Implement phase number extraction from filename (phase-02-tasks.md → 2)
43. Test with real task files in test worktree
44. Verify phase files parsed correctly
45. Commit: "feat(IWLE-100): Add phase file detection and parsing"

### Step 7: Error Handling & Edge Cases (1h)

46. Test missing task files (directory doesn't exist)
47. Verify graceful fallback (no progress shown)
48. Test empty task files (0 checkboxes)
49. Test malformed markdown (best-effort parsing)
50. Fix any issues found
51. Commit fixes if needed

### Step 8: Manual E2E Verification (0.5-1h)

52. Run all manual test scenarios (see Testing Strategy)
53. Verify cache invalidation (edit task file, reload dashboard)
54. Verify progress bar styling looks correct
55. Test with real agile workflow task files
56. Fix any issues found
57. Commit fixes if needed

### Step 9: Documentation (0.5h)

58. Update `implementation-log.md` with Phase 5 summary
59. Document markdown parsing format and cache invalidation
60. Add comments for complex logic (regex patterns, current phase detection)
61. Commit: "docs(IWLE-100): Document Phase 5 implementation"

**Total estimated time: 8-12 hours**

---

## Risk Assessment

### Risk: Markdown parsing breaks on unexpected format

**Likelihood:** Medium
**Impact:** Medium (no progress shown for that worktree)

**Mitigation:**
- Use simple regex patterns for well-defined checkbox format
- Best-effort parsing: count what's valid, ignore malformed lines
- Test with actual agile workflow task files (real-world format)
- Fallback: If parse fails, show no progress (doesn't crash dashboard)

### Risk: File I/O performance with many phase files

**Likelihood:** Low
**Impact:** Low (minor slowdown)

**Mitigation:**
- Typical workflow: 3-7 phase files per worktree, each 5-20KB
- mtime-based cache avoids re-parsing unchanged files
- Sequential file reads fast enough for local filesystem (~10ms per file)
- If >10 phases become common, add parallel file reading (Future optimization)

### Risk: Cache invalidation false positives (mtime changes without content change)

**Likelihood:** Low
**Impact:** Low (unnecessary re-parse, no functional issue)

**Mitigation:**
- mtime granularity typically 1 second (good enough for manual edits)
- Re-parsing is fast (~50ms per file), acceptable overhead
- Alternative (file content hashing) adds complexity without clear benefit
- Accept rare false positives for simpler implementation

### Risk: Phase file naming convention not followed

**Likelihood:** Low
**Impact:** Medium (phase not detected)

**Mitigation:**
- Document expected format: `phase-NN-tasks.md` (two-digit zero-padded)
- Match agile workflow generator output format
- Log warning if non-standard filename found (for debugging)
- Future: Add validation command to check task file structure

### Risk: Large task files (>10,000 lines) cause slow parsing

**Likelihood:** Very Low
**Impact:** Low (minor slowdown)

**Mitigation:**
- Typical task file: 200-500 lines (1-2KB)
- Regex-based line parsing is fast (~1ms per line)
- Even 10,000 lines = ~10ms parse time (acceptable)
- If extreme cases arise, add line limit or streaming parser

---

## Open Questions

None. All technical decisions resolved:

**Resolved during planning:**
- Checkbox format: `- [ ]` and `- [x]` only (standard agile workflow format)
- Phase file naming: `phase-NN-tasks.md` (two-digit zero-padded)
- Cache invalidation: mtime-based (no TTL, no content hashing)
- Current phase detection: First phase with incomplete tasks (or last if all complete)

---

## Notes and Decisions

### Design Decisions

**1. mtime-based cache invalidation vs content hashing**
- Decision: Use file modification timestamp (mtime) for cache invalidation
- Rationale: Simpler, faster, good enough for manual file edits
- Alternative considered: SHA-256 content hash (rejected: overkill, adds I/O overhead)

**2. Checkbox format: strict vs lenient parsing**
- Decision: Strict parsing of `- [ ]` and `- [x]` only
- Rationale: Matches agile workflow generator output, clear expectations
- Alternative considered: Support `*`, `+` bullets (rejected: ambiguous, not needed)

**3. Phase name extraction: header vs filename**
- Decision: Try extracting from header first, fallback to filename
- Rationale: Header more descriptive, filename always available as backup
- Example: `# Phase 2 Tasks: Auto-registration` → "Auto-registration"
- Fallback: `phase-02-tasks.md` → "Phase 2"

**4. Progress bar display: current phase vs overall**
- Decision: Show current phase progress (not overall)
- Rationale: More actionable (focus on what's in progress now)
- Overall progress available in WorkflowProgress model if needed later

**5. Cache storage: separate file vs embedded in state.json**
- Decision: Embed progress cache in state.json
- Rationale: Consistent with issue cache (Phase 4), atomic writes, simpler
- Alternative considered: Separate progress-cache.json (rejected: no benefit, adds complexity)

### Technical Notes

**Phase file structure assumptions:**
- Files in: `{worktree}/project-management/issues/{ISSUE-ID}/`
- Naming: `phase-01-tasks.md`, `phase-02-tasks.md`, etc.
- Main index: `tasks.md` (for total phase count, not strictly required)

**Markdown parsing edge cases:**
- Multi-line tasks: Only first line matters (checkbox must be on first line)
- Indented tasks: Supported (allows nested task structure if workflow evolves)
- Case-insensitive: `[x]` and `[X]` both recognized as complete

**Performance optimization (future):**
- Phase 5: Sequential file reads (simple, predictable)
- Future: Parallel parsing with Futures if >10 phase files per worktree
- Future: Incremental parsing (parse only changed files, not all phases)

---

## Links to Related Documents

- **Analysis:** `project-management/issues/IWLE-100/analysis.md` (Story 4, lines 160-206)
- **Phase 1 Context:** `project-management/issues/IWLE-100/phase-01-context.md`
- **Phase 2 Context:** `project-management/issues/IWLE-100/phase-02-context.md`
- **Phase 3 Context:** `project-management/issues/IWLE-100/phase-03-context.md`
- **Phase 4 Context:** `project-management/issues/IWLE-100/phase-04-context.md`
- **Implementation Log:** `project-management/issues/IWLE-100/implementation-log.md`
- **Task Index:** `project-management/issues/IWLE-100/tasks.md`

---

## Gherkin Scenarios (from Analysis)

```gherkin
Feature: Dashboard shows agile workflow progress
  As a developer using agile iterative workflow
  I want to see which phase I'm on and task progress
  So that I can track implementation status at a glance

Scenario: Show current phase and task completion
  Given worktree IWLE-123 is in Phase 2 of 4 phases
  And the current phase has 8 completed tasks out of 15 total
  And the phase is "Validation errors"
  When I view the dashboard
  Then the card shows "Phase 2/4: Validation errors"
  And I see a progress bar at 53% (8/15)
  And I see "8/15 tasks" label

Scenario: Progress derived from task files
  Given worktree IWLE-123 exists
  And file "project-management/issues/IWLE-123/tasks.md" exists
  And file "project-management/issues/IWLE-123/phase-02-tasks.md" has task checkboxes
  When the dashboard loads
  Then the server parses the markdown files
  And counts checked vs total tasks
  And displays the progress percentage
```

**Test Automation:**
- Phase 5: Manual testing of scenarios (verify with real task files)
- Unit tests for parsing logic (automated)
- Future: Mock filesystem for automated E2E tests

---

**Phase Status:** Ready for Implementation

**Next Steps:**
1. Begin implementation following Step 1 (Domain Models)
2. Run tests continuously during development (TDD)
3. Manual testing after dashboard integration complete
4. Update implementation-log.md after completion
5. Mark Phase 5 complete in tasks.md
