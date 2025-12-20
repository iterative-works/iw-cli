# Phase 6 Context: Show git status and PR links

**Issue:** IWLE-100
**Phase:** 6 of 7
**Story:** Story 5 - Show git status and PR links
**Estimated Effort:** 6-8 hours
**Created:** 2025-12-20

---

## Goals

This phase adds git repository status and pull request information to the dashboard. The primary objectives are:

1. **Git status detection**: Show branch name and working directory state (clean/dirty)
2. **Clean/dirty indication**: Visual indicator for uncommitted changes
3. **PR link discovery**: Detect and display GitHub/GitLab pull request URLs
4. **PR state badges**: Show PR status (open/merged/closed) with visual styling
5. **Cache-aware fetching**: Don't re-fetch PR data unnecessarily (2-minute TTL)
6. **Error handling**: Gracefully handle missing git repos, unavailable CLI tools

After this phase, developers will be able to quickly check which worktrees have uncommitted changes and access PR links directly from the dashboard.

---

## Scope

### In Scope

**Git Status Model:**
- `GitStatus` domain object (branch name, clean/dirty state, ahead/behind counts)
- Working directory state detection (uncommitted changes, staged changes)
- Branch name extraction from git commands
- Non-existent repository handling (worktree deleted)

**PR Data Model:**
- `PullRequestData` domain object (URL, state, PR number, title)
- `CachedPR` domain object (PR data + fetched timestamp, 2-minute TTL)
- PR state enum (Open, Merged, Closed)
- PR cache stored in ServerState

**Git Status Service:**
- `GitStatusService` application layer service
- Execute git commands in worktree directories
- Parse git status output for clean/dirty state
- Parse git branch output for branch name
- Handle git errors gracefully (no git repo, detached HEAD, etc.)

**PR Detection Service:**
- `PullRequestCacheService` application layer service
- Shell out to `gh pr view` / `glab mr view` for PR information
- Parse CLI output for PR URL, state, number
- Cache PR data with 2-minute TTL
- Graceful fallback if CLI tools not installed

**State Extension:**
- Add `prCache: Map[String, CachedPR]` to ServerState
- Cache PR data with timestamp for TTL validation
- TTL: 2 minutes (shorter than issue cache, PRs change more frequently)

**Dashboard Enhancements:**
- Update `WorktreeListView` to display git status: "Branch: IWLE-123-phase-02 ✓ clean"
- Add uncommitted changes warning: "Branch: IWLE-456-fix ⚠ uncommitted"
- Display PR link when available: "View PR ↗" with state badge
- PR state badges: open (blue), merged (purple), closed (gray)
- Visual styling for clean (green checkmark), dirty (yellow warning)

**CLI Tool Detection:**
- `CommandRunner` infrastructure service
- Check if `gh` or `glab` CLI tools are installed
- Execute shell commands and capture output
- Handle command failures gracefully (tool not found, not authenticated, etc.)

### Out of Scope

**Not in Phase 6 (deferred to later phases or future work):**
- Unregister endpoint and auto-pruning (Phase 7)
- Real-time git status updates (refresh on dashboard reload only)
- Git actions from dashboard (commit, push, etc.) - read-only
- Detailed git diff viewing (just clean/dirty indicator)
- Multiple PRs per worktree (only current branch PR)
- PR comments or review status (just URL and state)
- Custom git remotes (assumes origin)
- Ahead/behind indicators (deferred to future if needed)

**Technical Scope Boundaries:**
- Git status detection: working directory state only (not index vs workspace details)
- PR detection: GitHub and GitLab only (via `gh` and `glab` CLI tools)
- Clean/dirty logic: uncommitted changes OR staged changes = dirty
- Branch name: current branch only (not detached HEAD handling)
- PR cache: 2-minute TTL, no user-configurable refresh interval

---

## Dependencies

### Prerequisites from Phase 1-5

**Must exist and work correctly:**
- `CaskServer` with dashboard rendering on `GET /`
- `ServerState` domain model with worktree map, issue cache, progress cache
- `StateRepository` for JSON persistence with atomic writes
- `WorktreeListView` Scalatags template for worktree cards
- `WorktreeRegistration` with issue ID, path, tracker type, team
- `DashboardService` for dashboard HTML generation
- `CachedIssue` with TTL validation pattern (reuse for PR cache)
- Worktree path stored in WorktreeRegistration

**Available for reuse:**
- upickle JSON serialization (used by StateRepository)
- Existing error handling patterns (Either-based)
- TTL validation pattern from CachedIssue (Phase 4)
- Instant timestamps for cache expiry

**Filesystem and Git structure:**
```
worktree-path/
  .git/                  # Git repository (or .git file pointing to parent)
  project-management/    # Already in use
  src/                   # Source code
  ...
```

**Expected git commands:**
```bash
# Get branch name
git -C /path/to/worktree rev-parse --abbrev-ref HEAD

# Get working directory status
git -C /path/to/worktree status --porcelain

# Get PR info (GitHub)
cd /path/to/worktree && gh pr view --json url,state,number,title

# Get PR info (GitLab)
cd /path/to/worktree && glab mr view --output json
```

### External Dependencies

**New dependencies (CLI tools, optional):**
- `gh` (GitHub CLI) - optional, for GitHub PR detection
- `glab` (GitLab CLI) - optional, for GitLab PR detection
- `git` - required, already available in development environment

**No new library dependencies:**
- Use standard library process execution: `scala.sys.process._`
- upickle already in project.scala
- java.time.Instant for TTL comparison

**CLI Tool Detection Strategy:**
```scala
// Check if gh is available
which gh || echo "not found"

// Check if glab is available
which glab || echo "not found"
```

---

## Technical Approach

### High-Level Strategy

Phase 6 follows the **Functional Core / Imperative Shell** pattern:

**Domain Layer (Pure):**
- `GitStatus` case class (branch name, clean/dirty state)
- `PullRequestData` case class (URL, state, PR number, title)
- `CachedPR` case class (PullRequestData + fetched timestamp)
- `PRState` enum (Open, Merged, Closed)
- TTL validation: pure functions for cache expiry

**Application Layer (Pure):**
- `GitStatusService` with injected command execution:
  - `getGitStatus(path, execCommand): Either[String, GitStatus]`
  - `parseBranchName(output): Option[String]`
  - `parseWorkingTreeStatus(output): Boolean` (clean if empty)
- `PullRequestCacheService` with injected command execution:
  - `fetchPR(path, cache, now, execCommand): Either[String, Option[PullRequestData]]`
  - `parsePRData(output, tool): Either[String, PullRequestData]`
  - `isValid(cached, now): Boolean` (TTL check)

**Infrastructure Layer (Effects):**
- `CommandRunner` for shell command execution
- Execute git commands in worktree directories
- Execute `gh`/`glab` CLI commands
- StateRepository extension for PR cache serialization

**Presentation Layer:**
- `DashboardService` calls `GitStatusService` and `PullRequestCacheService` for each worktree
- `WorktreeListView` enhanced to render git status and PR links
- Git status indicators: "✓ clean" (green), "⚠ uncommitted" (yellow)
- PR link button: "View PR ↗" with state badge

### Architecture Overview

```
DashboardService (Presentation)
       ↓
   GitStatusService (Application)
       ↓
   Execute git commands via CommandRunner
       ↓
   Parse git output (branch name, status)
       ↓
   Return GitStatus

DashboardService (Presentation)
       ↓
   PullRequestCacheService (Application)
       ↓
   Check PR cache validity (2-minute TTL)
       ↓
   [Cache valid?] → Yes → Return cached PullRequestData
       ↓ No
   Execute gh/glab command via CommandRunner
       ↓
   Parse CLI output (URL, state, number)
       ↓
   Return PullRequestData + update cache
```

**Error handling philosophy:**
- No git repository → Show "No git repository" instead of branch
- Git command fails → Log warning, show "Git unavailable"
- `gh`/`glab` not installed → No PR link shown (graceful degradation)
- PR not found for branch → No PR link shown (not an error)
- CLI tool authentication required → Show "Authentication required" message

### Key Components

#### 1. GitStatus (Domain Layer)

**File:** `.iw/core/GitStatus.scala`

**Purpose:** Represents git repository state for a worktree.

**Interface:**
```scala
case class GitStatus(
  branchName: String,
  isClean: Boolean
):
  def statusIndicator: String = if isClean then "✓ clean" else "⚠ uncommitted"
  def statusCssClass: String = if isClean then "git-clean" else "git-dirty"
```

**Implementation notes:**
- Pure domain object with computed properties
- Clean = no uncommitted changes and no staged changes
- Branch name = current branch (detached HEAD shows commit SHA)

#### 2. PullRequestData (Domain Layer)

**File:** `.iw/core/PullRequestData.scala`

**Purpose:** Represents pull request information.

**Interface:**
```scala
enum PRState:
  case Open, Merged, Closed

case class PullRequestData(
  url: String,
  state: PRState,
  number: Int,
  title: String
):
  def stateBadgeClass: String = state match
    case PRState.Open => "pr-open"
    case PRState.Merged => "pr-merged"
    case PRState.Closed => "pr-closed"

  def stateBadgeText: String = state match
    case PRState.Open => "Open"
    case PRState.Merged => "Merged"
    case PRState.Closed => "Closed"
```

**Implementation notes:**
- Pure domain object with state-based styling
- URL is primary identifier (clickable link)
- State determines badge color (open=blue, merged=purple, closed=gray)

#### 3. CachedPR (Domain Layer)

**File:** `.iw/core/CachedPR.scala`

**Purpose:** Cache wrapper with TTL validation.

**Interface:**
```scala
case class CachedPR(
  pr: PullRequestData,
  fetchedAt: Instant
)

object CachedPR:
  val TTL_MINUTES = 2

  def isValid(cached: CachedPR, now: Instant): Boolean =
    java.time.Duration.between(cached.fetchedAt, now).toMinutes < TTL_MINUTES

  def age(cached: CachedPR, now: Instant): Duration =
    java.time.Duration.between(cached.fetchedAt, now)
```

**Implementation notes:**
- 2-minute TTL (shorter than issue cache, PRs change more frequently)
- Pure validation function (receives `now` parameter)
- Age calculation for "cached Xm ago" display

#### 4. GitStatusService (Application Layer)

**File:** `.iw/core/GitStatusService.scala`

**Purpose:** Pure business logic for git status detection.

**Interface:**
```scala
object GitStatusService:
  /** Get git status for worktree.
    * Executes git commands via injected execCommand function.
    * Returns GitStatus with branch name and clean/dirty state.
    */
  def getGitStatus(
    worktreePath: String,
    execCommand: (String, Array[String]) => Either[String, String]
  ): Either[String, GitStatus]

  /** Parse branch name from git output.
    * Handles: normal branch, detached HEAD, errors.
    */
  def parseBranchName(output: String): Option[String]

  /** Parse working tree status from git status --porcelain output.
    * Returns true if clean (empty output), false if dirty.
    */
  def isWorkingTreeClean(output: String): Boolean
```

**Implementation logic for `getGitStatus`:**
```scala
1. Get branch name:
   result = execCommand("git", Array("-C", worktreePath, "rev-parse", "--abbrev-ref", "HEAD"))
   branchName = parseBranchName(result.getOrElse("unknown"))

2. Get status:
   result = execCommand("git", Array("-C", worktreePath, "status", "--porcelain"))
   isClean = isWorkingTreeClean(result.getOrElse(""))

3. Return GitStatus(branchName, isClean)
```

**Implementation notes:**
- Completely pure: receives command execution function from caller
- No direct process execution in service (FCIS)
- Caller (DashboardService) provides execCommand wrapper
- Handles git errors by returning Left(error message)

#### 5. PullRequestCacheService (Application Layer)

**File:** `.iw/core/PullRequestCacheService.scala`

**Purpose:** Pure business logic for PR detection with caching.

**Interface:**
```scala
object PullRequestCacheService:
  /** Fetch PR data with cache support.
    * Checks cache validity (2-minute TTL) and re-fetches if expired.
    * Returns None if no PR found (not an error).
    */
  def fetchPR(
    worktreePath: String,
    cache: Map[String, CachedPR],
    issueId: String,
    now: Instant,
    execCommand: (String, Array[String]) => Either[String, String],
    detectTool: String => Boolean  // Check if gh/glab installed
  ): Either[String, Option[PullRequestData]]

  /** Parse PR data from gh pr view JSON output.
    * Returns PullRequestData or error if parsing fails.
    */
  def parseGitHubPR(jsonOutput: String): Either[String, PullRequestData]

  /** Parse PR data from glab mr view JSON output.
    * Returns PullRequestData or error if parsing fails.
    */
  def parseGitLabPR(jsonOutput: String): Either[String, PullRequestData]

  /** Detect which PR tool is available (gh or glab).
    * Returns Some("gh"), Some("glab"), or None.
    */
  def detectPRTool(detectTool: String => Boolean): Option[String]
```

**Implementation logic for `fetchPR`:**
```scala
1. Check cache validity:
   cache.get(issueId) match
     case Some(cached) if CachedPR.isValid(cached, now) =>
       Return Right(Some(cached.pr))
     case _ =>
       Proceed to fetch

2. Detect PR tool:
   tool = detectPRTool(detectTool)
   If None, return Right(None) // No tool available

3. Execute PR command:
   If tool == "gh":
     cmd = "gh pr view --json url,state,number,title"
   Else if tool == "glab":
     cmd = "glab mr view --output json"

   result = execCommand(cmd, worktreePath)

4. Parse PR data:
   If tool == "gh":
     prData = parseGitHubPR(result)
   Else:
     prData = parseGitLabPR(result)

5. Return prData
   If parse fails, return Right(None) // No PR found
```

**Implementation notes:**
- Completely pure: receives command execution functions from caller
- Tool detection via injected `detectTool` function
- Returns `Option[PullRequestData]` - None is not an error (no PR for branch)
- Error cases: command fails, parsing fails, tool not authenticated

#### 6. CommandRunner (Infrastructure Layer)

**File:** `.iw/core/CommandRunner.scala`

**Purpose:** Execute shell commands and capture output.

**Interface:**
```scala
object CommandRunner:
  /** Execute command and return stdout.
    * Returns Left(error) if command fails or times out.
    */
  def execute(
    command: String,
    args: Array[String],
    workingDir: Option[String] = None
  ): Either[String, String]

  /** Check if command is available in PATH.
    * Used for detecting gh/glab availability.
    */
  def isCommandAvailable(command: String): Boolean
```

**Implementation:**
```scala
import scala.sys.process._

def execute(command: String, args: Array[String], workingDir: Option[String]): Either[String, String] =
  try
    val processBuilder = Process(command +: args, workingDir.map(new java.io.File(_)))
    val output = processBuilder.!!.trim
    Right(output)
  catch
    case e: RuntimeException if e.getMessage.contains("exit code") =>
      Left(s"Command failed: ${e.getMessage}")
    case e: Exception =>
      Left(s"Command error: ${e.getMessage}")

def isCommandAvailable(command: String): Boolean =
  try
    s"which $command".! == 0
  catch
    case _: Exception => false
```

**Implementation notes:**
- Uses `scala.sys.process._` for command execution
- Captures stdout with `!!` operator
- Handles failures with Either (no exceptions)
- Working directory support for git commands

#### 7. ServerState Extension

**File:** `.iw/core/ServerState.scala` (modify existing)

**Changes:**
```scala
case class ServerState(
  worktrees: Map[String, WorktreeRegistration],
  issueCache: Map[String, CachedIssue],
  progressCache: Map[String, CachedProgress],
  prCache: Map[String, CachedPR] = Map.empty  // NEW
)
```

**JSON format (state.json):**
```json
{
  "worktrees": { /* ... */ },
  "issueCache": { /* ... */ },
  "progressCache": { /* ... */ },
  "prCache": {
    "IWLE-123": {
      "pr": {
        "url": "https://github.com/org/repo/pull/42",
        "state": "Open",
        "number": 42,
        "title": "Add server dashboard"
      },
      "fetchedAt": "2025-12-20T15:30:00Z"
    }
  }
}
```

**Implementation notes:**
- StateRepository already handles serialization via upickle
- Add ReadWriter for CachedPR, PullRequestData, PRState
- Atomic writes continue to work (no changes to StateRepository)

#### 8. DashboardService Integration

**File:** `.iw/core/DashboardService.scala` (modify existing)

**Changes:**
```scala
object DashboardService:
  def renderDashboard(
    worktrees: List[WorktreeRegistration],
    issueCache: Map[String, CachedIssue],
    progressCache: Map[String, CachedProgress],
    prCache: Map[String, CachedPR],
    config: Config
  ): Tag =
    val now = Instant.now()

    val worktreesWithData = worktrees.map { wt =>
      val issueData = fetchIssueForWorktree(wt, issueCache, now, config)
      val progress = fetchProgressForWorktree(wt, progressCache)
      val gitStatus = fetchGitStatusForWorktree(wt)
      val prData = fetchPRForWorktree(wt, prCache, now)
      (wt, issueData, progress, gitStatus, prData)
    }

    WorktreeListView.render(worktreesWithData, now)

  private def fetchGitStatusForWorktree(
    wt: WorktreeRegistration
  ): Option[GitStatus] =
    val execCommand = (cmd: String, args: Array[String]) =>
      CommandRunner.execute(cmd, args, None)

    GitStatusService.getGitStatus(wt.path, execCommand).toOption

  private def fetchPRForWorktree(
    wt: WorktreeRegistration,
    cache: Map[String, CachedPR],
    now: Instant
  ): Option[PullRequestData] =
    val execCommand = (cmd: String, args: Array[String]) =>
      CommandRunner.execute(cmd, args, Some(wt.path))

    val detectTool = (toolName: String) =>
      CommandRunner.isCommandAvailable(toolName)

    PullRequestCacheService.fetchPR(
      wt.path,
      cache,
      wt.issueId,
      now,
      execCommand,
      detectTool
    ).toOption.flatten
```

**Implementation notes:**
- Wraps command execution in Either for error handling
- Passes command execution functions to services
- Handles errors by showing no git status / no PR link (graceful fallback)

#### 9. WorktreeListView Enhancement

**File:** `.iw/core/WorktreeListView.scala` (modify existing)

**Changes:**
```scala
def renderWorktreeCard(
  wt: WorktreeRegistration,
  issueData: Option[(IssueData, Boolean)],
  progress: Option[WorkflowProgress],
  gitStatus: Option[GitStatus],
  prData: Option[PullRequestData],
  now: Instant
): Tag =
  div(cls := "worktree-card")(
    h3(issueData.map(_._1.title).getOrElse("Issue data unavailable")),
    p(cls := "issue-id")(
      a(href := issueData.map(_._1.url).getOrElse("#"))(wt.issueId)
    ),

    // Git status (if available)
    gitStatus.map { gs =>
      div(cls := "git-status")(
        span(cls := "git-branch")(s"Branch: ${gs.branchName}"),
        span(cls := s"git-indicator ${gs.statusCssClass}")(gs.statusIndicator)
      )
    },

    // PR link (if available)
    prData.map { pr =>
      div(cls := "pr-link")(
        a(
          href := pr.url,
          target := "_blank",
          cls := "pr-button"
        )(
          "View PR ↗",
          span(cls := s"pr-badge ${pr.stateBadgeClass}")(pr.stateBadgeText)
        )
      )
    },

    // Phase info (from Phase 5)
    /* ... */,

    // Issue details (from Phase 4)
    /* ... */
  )
```

**Styling (inline CSS):**
```scala
style(raw("""
  .git-status {
    margin: 8px 0;
    font-size: 0.9em;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .git-branch {
    font-weight: 500;
    color: #495057;
  }

  .git-indicator {
    font-size: 0.85em;
    padding: 2px 6px;
    border-radius: 3px;
    font-weight: 600;
  }

  .git-clean {
    color: #37b24d;
    background: #d3f9d8;
  }

  .git-dirty {
    color: #f59f00;
    background: #fff3bf;
  }

  .pr-link {
    margin: 8px 0;
  }

  .pr-button {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    background: #f1f3f5;
    border: 1px solid #dee2e6;
    border-radius: 4px;
    text-decoration: none;
    color: #212529;
    font-size: 0.9em;
    font-weight: 500;
    transition: background 0.2s;
  }

  .pr-button:hover {
    background: #e9ecef;
  }

  .pr-badge {
    padding: 2px 8px;
    border-radius: 3px;
    font-size: 0.85em;
    font-weight: 600;
  }

  .pr-open {
    color: #1c7ed6;
    background: #d0ebff;
  }

  .pr-merged {
    color: #7048e8;
    background: #e5dbff;
  }

  .pr-closed {
    color: #868e96;
    background: #e9ecef;
  }
"""))
```

---

## Files to Modify/Create

### New Files

**Domain Layer:**
- `.iw/core/GitStatus.scala` - Git repository state model
- `.iw/core/PullRequestData.scala` - PR information model with state enum
- `.iw/core/CachedPR.scala` - Cache wrapper with TTL validation

**Application Layer:**
- `.iw/core/GitStatusService.scala` - Pure business logic for git status detection
- `.iw/core/PullRequestCacheService.scala` - Pure business logic for PR fetching with cache

**Infrastructure Layer:**
- `.iw/core/CommandRunner.scala` - Shell command execution utilities

**Tests:**
- `.iw/core/test/GitStatusTest.scala` - Unit tests for GitStatus model
- `.iw/core/test/PullRequestDataTest.scala` - Unit tests for PullRequestData model
- `.iw/core/test/CachedPRTest.scala` - Unit tests for cache validation logic
- `.iw/core/test/GitStatusServiceTest.scala` - Unit tests for git status service
- `.iw/core/test/PullRequestCacheServiceTest.scala` - Unit tests for PR cache service
- `.iw/core/test/CommandRunnerTest.scala` - Unit tests for command execution

### Modified Files

**Domain Layer:**
- `.iw/core/ServerState.scala`:
  - Add `prCache: Map[String, CachedPR]` field
  - Add upickle ReadWriter instances for new types

**Application Layer:**
- `.iw/core/DashboardService.scala`:
  - Add git status fetching logic for each worktree
  - Add PR data fetching logic for each worktree
  - Pass git status and PR data to WorktreeListView
  - Handle command execution errors gracefully

**Presentation Layer:**
- `.iw/core/WorktreeListView.scala`:
  - Update `renderWorktreeCard` to display git status
  - Add PR link button with state badge
  - Add CSS for git status indicators and PR button

**Infrastructure Layer:**
- `.iw/core/StateRepository.scala`:
  - Add upickle serializers for CachedPR, PullRequestData, PRState, GitStatus

---

## Testing Strategy

### Unit Tests

**GitStatusTest:**
```scala
test("statusIndicator returns clean checkmark when clean") {
  val status = GitStatus("main", isClean = true)
  assertEquals(status.statusIndicator, "✓ clean")
}

test("statusIndicator returns warning when dirty") {
  val status = GitStatus("feature-branch", isClean = false)
  assertEquals(status.statusIndicator, "⚠ uncommitted")
}

test("statusCssClass returns correct class") {
  val clean = GitStatus("main", isClean = true)
  assertEquals(clean.statusCssClass, "git-clean")

  val dirty = GitStatus("main", isClean = false)
  assertEquals(dirty.statusCssClass, "git-dirty")
}
```

**PullRequestDataTest:**
```scala
test("stateBadgeClass returns correct class for each state") {
  val open = PullRequestData("url", PRState.Open, 42, "Title")
  assertEquals(open.stateBadgeClass, "pr-open")

  val merged = PullRequestData("url", PRState.Merged, 42, "Title")
  assertEquals(merged.stateBadgeClass, "pr-merged")

  val closed = PullRequestData("url", PRState.Closed, 42, "Title")
  assertEquals(closed.stateBadgeClass, "pr-closed")
}

test("stateBadgeText returns correct text") {
  val open = PullRequestData("url", PRState.Open, 42, "Title")
  assertEquals(open.stateBadgeText, "Open")
}
```

**CachedPRTest:**
```scala
test("isValid returns true when within TTL") {
  val now = Instant.now()
  val cached = CachedPR(mockPR, now.minusSeconds(60)) // 1 minute ago
  assert(CachedPR.isValid(cached, now))
}

test("isValid returns false when TTL expired") {
  val now = Instant.now()
  val cached = CachedPR(mockPR, now.minusSeconds(180)) // 3 minutes ago (>2 min TTL)
  assert(!CachedPR.isValid(cached, now))
}

test("age calculates duration correctly") {
  val now = Instant.now()
  val cached = CachedPR(mockPR, now.minusSeconds(90)) // 90 seconds ago
  assertEquals(CachedPR.age(cached, now).toSeconds, 90L)
}
```

**GitStatusServiceTest:**
```scala
test("getGitStatus returns status when git commands succeed") {
  val execCommand = (cmd: String, args: Array[String]) =>
    if args.contains("rev-parse") then Right("main")
    else Right("") // Empty status = clean

  val result = GitStatusService.getGitStatus("/path", execCommand)
  assert(result.isRight)
  assertEquals(result.map(_.branchName), Right("main"))
  assertEquals(result.map(_.isClean), Right(true))
}

test("getGitStatus handles dirty working tree") {
  val execCommand = (cmd: String, args: Array[String]) =>
    if args.contains("rev-parse") then Right("feature")
    else Right(" M file.txt\n") // Modified file

  val result = GitStatusService.getGitStatus("/path", execCommand)
  assert(result.isRight)
  assertEquals(result.map(_.isClean), Right(false))
}

test("parseBranchName extracts branch from output") {
  assertEquals(GitStatusService.parseBranchName("main\n"), Some("main"))
  assertEquals(GitStatusService.parseBranchName("feature-branch"), Some("feature-branch"))
}

test("isWorkingTreeClean returns true for empty output") {
  assert(GitStatusService.isWorkingTreeClean(""))
  assert(GitStatusService.isWorkingTreeClean("\n"))
}

test("isWorkingTreeClean returns false for changes") {
  assert(!GitStatusService.isWorkingTreeClean(" M file.txt"))
  assert(!GitStatusService.isWorkingTreeClean("?? new-file.txt"))
}
```

**PullRequestCacheServiceTest:**
```scala
test("fetchPR uses cache when valid") {
  val now = Instant.now()
  val cachedPR = CachedPR(mockPRData, now.minusSeconds(60))
  val cache = Map("ISSUE-123" -> cachedPR)

  val execCommandCalled = scala.collection.mutable.ArrayBuffer[String]()
  val execCommand = (cmd: String, args: Array[String]) => {
    execCommandCalled += cmd
    Right("{}")
  }
  val detectTool = (tool: String) => true

  val result = PullRequestCacheService.fetchPR(
    "/path", cache, "ISSUE-123", now, execCommand, detectTool
  )

  assert(result.isRight)
  assert(execCommandCalled.isEmpty) // No command execution
  assertEquals(result.toOption.flatten, Some(mockPRData))
}

test("fetchPR re-fetches when cache expired") {
  val now = Instant.now()
  val cachedPR = CachedPR(mockPRData, now.minusSeconds(180)) // Expired
  val cache = Map("ISSUE-123" -> cachedPR)

  val execCommand = (cmd: String, args: Array[String]) =>
    Right("""{"url": "https://...", "state": "OPEN", "number": 42, "title": "Test"}""")
  val detectTool = (tool: String) => tool == "gh"

  val result = PullRequestCacheService.fetchPR(
    "/path", cache, "ISSUE-123", now, execCommand, detectTool
  )

  assert(result.isRight)
  assert(result.toOption.flatten.isDefined) // Fresh fetch
}

test("fetchPR returns None when no PR tool available") {
  val cache = Map.empty[String, CachedPR]
  val execCommand = (cmd: String, args: Array[String]) => Right("")
  val detectTool = (tool: String) => false // No tools

  val result = PullRequestCacheService.fetchPR(
    "/path", cache, "ISSUE-123", Instant.now(), execCommand, detectTool
  )

  assert(result.isRight)
  assertEquals(result.toOption.flatten, None)
}

test("parseGitHubPR extracts PR data from JSON") {
  val json = """{"url": "https://github.com/org/repo/pull/42", "state": "OPEN", "number": 42, "title": "Add feature"}"""
  val result = PullRequestCacheService.parseGitHubPR(json)

  assert(result.isRight)
  assertEquals(result.map(_.url), Right("https://github.com/org/repo/pull/42"))
  assertEquals(result.map(_.state), Right(PRState.Open))
  assertEquals(result.map(_.number), Right(42))
}

test("detectPRTool returns gh when available") {
  val detectTool = (tool: String) => tool == "gh"
  assertEquals(PullRequestCacheService.detectPRTool(detectTool), Some("gh"))
}

test("detectPRTool returns glab when only glab available") {
  val detectTool = (tool: String) => tool == "glab"
  assertEquals(PullRequestCacheService.detectPRTool(detectTool), Some("glab"))
}

test("detectPRTool returns None when no tools available") {
  val detectTool = (tool: String) => false
  assertEquals(PullRequestCacheService.detectPRTool(detectTool), None)
}
```

**CommandRunnerTest:**
```scala
test("execute returns stdout when command succeeds") {
  val result = CommandRunner.execute("echo", Array("test"))
  assertEquals(result, Right("test"))
}

test("execute returns error when command fails") {
  val result = CommandRunner.execute("false", Array())
  assert(result.isLeft)
}

test("isCommandAvailable returns true for existing command") {
  assert(CommandRunner.isCommandAvailable("echo"))
}

test("isCommandAvailable returns false for non-existent command") {
  assert(!CommandRunner.isCommandAvailable("nonexistent-command-xyz"))
}
```

### Integration Tests

**StateRepositoryTest (extend existing):**
```scala
test("serialize and deserialize ServerState with PR cache") {
  val prData = PullRequestData("https://...", PRState.Open, 42, "Test PR")
  val cached = CachedPR(prData, Instant.now())

  val state = ServerState(
    worktrees = Map(/* ... */),
    issueCache = Map(/* ... */),
    progressCache = Map(/* ... */),
    prCache = Map("IWLE-123" -> cached)
  )

  val repo = StateRepository(tempFile)
  repo.write(state)

  val loaded = repo.read()
  assert(loaded.isRight)
  assertEquals(loaded.map(_.prCache.size).getOrElse(0), 1)
  assertEquals(
    loaded.flatMap(_.prCache.get("IWLE-123").map(_.pr.number)),
    Right(42)
  )
}
```

### Manual Testing Scenarios

**Scenario 1: Clean working directory indicated**
1. Register worktree IWLE-123 on branch "IWLE-123-phase-02"
2. Ensure working directory is clean (no uncommitted changes)
3. Load dashboard
4. Verify: Card shows "Branch: IWLE-123-phase-02 ✓ clean"
5. Verify: Clean indicator styled in green

**Scenario 2: Uncommitted changes warning**
1. Register worktree IWLE-456
2. Edit a file without committing
3. Load dashboard
4. Verify: Card shows "Branch: IWLE-456-fix ⚠ uncommitted"
5. Verify: Warning indicator styled in yellow

**Scenario 3: PR links displayed when available**
1. Register worktree IWLE-123 with open PR
2. Ensure `gh` CLI is installed and authenticated
3. Load dashboard
4. Verify: "View PR ↗" link displayed
5. Verify: Clicking link opens GitHub PR in new tab
6. Verify: State badge shows "Open" in blue

**Scenario 4: PR cache validity**
1. Load dashboard (populates PR cache)
2. Verify state.json contains PR cache with timestamp
3. Wait 1 minute, reload dashboard
4. Verify: Cached PR data used (no gh command)
5. Wait 3 minutes, reload dashboard
6. Verify: PR re-fetched (cache expired)

**Scenario 5: Missing git repository handled**
1. Register worktree with non-git directory
2. Load dashboard
3. Verify: No git status shown (no error)
4. Verify: Dashboard renders other worktrees correctly

**Scenario 6: No PR CLI tools available**
1. Ensure `gh` and `glab` are not in PATH
2. Load dashboard
3. Verify: No PR link shown
4. Verify: No error messages
5. Verify: Dashboard renders correctly

---

## Acceptance Criteria

Phase 6 is complete when:

### Functional Requirements

- [ ] Git branch name displayed for each worktree
- [ ] Clean vs dirty status clearly indicated (✓ clean / ⚠ uncommitted)
- [ ] Clean status styled with green background
- [ ] Dirty status styled with yellow background
- [ ] PR links appear when PRs exist for current branch
- [ ] PR state (open/merged/closed) shown visually with badges
- [ ] Open PRs show blue badge, merged show purple, closed show gray
- [ ] PR link opens in new browser tab
- [ ] PR data cached with 2-minute TTL
- [ ] Cache invalidated after 2 minutes, PR re-fetched
- [ ] GitHub PRs detected via `gh pr view` command
- [ ] GitLab PRs detected via `glab mr view` command
- [ ] Non-existent git repos show no git status (no error)
- [ ] Missing `gh`/`glab` tools handled gracefully (no PR link shown)

### Non-Functional Requirements

- [ ] All unit tests passing (GitStatus, PullRequestData, CachedPR, services)
- [ ] All integration tests passing (StateRepository with PR cache)
- [ ] Manual scenarios verified (clean/dirty, PR links, cache TTL)
- [ ] Git status detection completes within 100ms per worktree
- [ ] PR detection completes within 2 seconds per worktree (network call)
- [ ] Command execution errors don't crash dashboard
- [ ] Code follows FCIS pattern (pure domain/application, effects in infrastructure)
- [ ] No new compilation warnings
- [ ] Git commits follow TDD: test → implementation → refactor

### Quality Checks

- [ ] Code review self-check: Are command execution functions injected?
- [ ] Code review self-check: Does PR cache TTL work correctly?
- [ ] Code review self-check: Are edge cases handled (no git, no tools, no PR)?
- [ ] Documentation: Update implementation-log.md with Phase 6 summary
- [ ] Documentation: Comment PR JSON parsing logic and tool detection

---

## Implementation Sequence

**Recommended order (TDD):**

### Step 1: Domain Models (1h)

1. Write `GitStatusTest.scala` with status indicator tests
2. Implement `GitStatus.scala` case class with computed properties
3. Write `PullRequestDataTest.scala` with state badge tests
4. Implement `PullRequestData.scala` with PRState enum
5. Write `CachedPRTest.scala` with TTL validation tests
6. Implement `CachedPR.scala` with validation logic
7. Verify all unit tests pass
8. Commit: "feat(IWLE-100): Add GitStatus and PullRequestData domain models"

### Step 2: Command Execution Infrastructure (1h)

9. Write `CommandRunnerTest.scala` with execution tests
10. Implement `CommandRunner.scala` with process execution
11. Test command availability detection
12. Test error handling (command not found, failed execution)
13. Verify all CommandRunner tests pass
14. Commit: "feat(IWLE-100): Add CommandRunner for shell command execution"

### Step 3: Git Status Service (1.5h)

15. Write `GitStatusServiceTest.scala` with git command scenarios
16. Implement `GitStatusService.getGitStatus()` with command injection
17. Implement `parseBranchName()` parser
18. Implement `isWorkingTreeClean()` parser
19. Test edge cases: no git repo, detached HEAD, errors
20. Verify all git status tests pass
21. Commit: "feat(IWLE-100): Add GitStatusService for repository status detection"

### Step 4: PR Cache Service (2h)

22. Write `PullRequestCacheServiceTest.scala` with cache scenarios
23. Implement `PullRequestCacheService.fetchPR()` with cache check
24. Implement `parseGitHubPR()` JSON parser
25. Implement `parseGitLabPR()` JSON parser
26. Implement `detectPRTool()` tool detection
27. Test cache validity: valid cache, expired cache, no cache
28. Test tool detection: gh only, glab only, neither, both
29. Verify all PR cache tests pass
30. Commit: "feat(IWLE-100): Add PullRequestCacheService with GitHub/GitLab support"

### Step 5: State Repository Extension (0.5h)

31. Extend `ServerState` with prCache field
32. Add upickle ReadWriter instances in StateRepository
33. Write integration test for serialization/deserialization
34. Verify state.json correctly stores and loads PR cache
35. Commit: "feat(IWLE-100): Extend ServerState with PR cache"

### Step 6: Dashboard Integration (1.5h)

36. Modify `DashboardService.renderDashboard()` to fetch git status
37. Add PR data fetching logic
38. Build command execution wrappers (execCommand, detectTool)
39. Pass git status and PR data to WorktreeListView
40. Write integration test for DashboardService with git/PR data
41. Verify tests pass
42. Commit: "feat(IWLE-100): Integrate git status and PR data in dashboard"

43. Modify `WorktreeListView.renderWorktreeCard()` to display git status
44. Add PR link button with state badge
45. Add inline CSS for git indicators and PR button
46. Manual test: Load dashboard, verify git status and PR links appear
47. Commit: "feat(IWLE-100): Enhance worktree cards with git status and PR links"

### Step 7: Error Handling & Edge Cases (0.5h)

48. Test missing git repository (directory without .git)
49. Verify graceful fallback (no git status shown)
50. Test `gh`/`glab` not installed
51. Verify no PR link shown (no error)
52. Test PR not found for branch (404 from gh pr view)
53. Verify handled as "no PR" (not error)
54. Fix any issues found
55. Commit fixes if needed

### Step 8: Manual E2E Verification (1h)

56. Run all manual test scenarios (see Testing Strategy)
57. Verify cache TTL (wait 2 minutes, check re-fetch)
58. Verify PR state badges display correctly (open/merged/closed)
59. Test with real GitHub/GitLab repositories
60. Test with worktrees that have no PR
61. Fix any issues found
62. Commit fixes if needed

### Step 9: Documentation (0.5h)

63. Update `implementation-log.md` with Phase 6 summary
64. Document git status detection and PR cache TTL
65. Add comments for PR JSON parsing logic
66. Commit: "docs(IWLE-100): Document Phase 6 implementation"

**Total estimated time: 6-8 hours**

---

## Risk Assessment

### Risk: PR CLI tools not installed or authenticated

**Likelihood:** Medium
**Impact:** Low (no PR links shown, but dashboard works)

**Mitigation:**
- Graceful degradation: show worktree without PR link if tool unavailable
- Document installation instructions for `gh` and `glab` in README
- Check tool availability before attempting PR fetch
- Clear error message if authentication required: "GitHub CLI not authenticated"

### Risk: Git command execution slow or timeout

**Likelihood:** Low
**Impact:** Low (dashboard load delay)

**Mitigation:**
- Git commands are fast for local repos (~10-50ms)
- Use `git -C` to avoid changing working directory
- Set command timeout (e.g., 5 seconds) to prevent hang
- If timeout occurs, show "Git unavailable" instead of blocking

### Risk: PR JSON parsing breaks on API changes

**Likelihood:** Low
**Impact:** Medium (no PR links for affected tool)

**Mitigation:**
- Use stable JSON output from `gh`/`glab` (documented API)
- Handle parsing errors gracefully (return None instead of crash)
- Test with real GitHub/GitLab repos during manual verification
- Log warning if parsing fails (for debugging)

### Risk: Cache TTL too short causes excessive API calls

**Likelihood:** Low
**Impact:** Low (rate limiting by GitHub/GitLab)

**Mitigation:**
- 2-minute TTL balances freshness with API limits
- PR state doesn't change frequently (minutes to hours)
- Dashboard refresh interval is 30 seconds (HTMX), so max ~15 fetches per TTL period
- GitHub/GitLab rate limits are high enough (5000 req/hour for authenticated users)

### Risk: Mixed GitHub and GitLab repos in same dashboard

**Likelihood:** Medium
**Impact:** Low (minor: both tools must be installed)

**Mitigation:**
- Tool detection per worktree (not global)
- If both `gh` and `glab` available, prefer `gh` (check in order)
- Document that mixed repos work if both CLI tools installed
- Future: detect remote URL to choose correct tool (github.com → gh, gitlab.com → glab)

---

## Open Questions

None. All technical decisions resolved:

**Resolved during planning:**
- Git status: Use `git status --porcelain` for clean/dirty (standard format)
- PR detection: Shell out to `gh`/`glab` CLI tools (reuses user authentication)
- Cache TTL: 2 minutes for PR data (balance freshness vs API overhead)
- Clean/dirty logic: Empty `git status --porcelain` output = clean
- Tool priority: If both `gh` and `glab` available, prefer `gh` (GitHub more common)

---

## Notes and Decisions

### Design Decisions

**1. PR cache TTL: 2 minutes vs 5 minutes**
- Decision: 2-minute TTL for PR cache
- Rationale: PRs change more frequently than issues (merges, state updates)
- Shorter TTL improves accuracy without excessive API calls

**2. Tool detection: Remote URL vs CLI availability**
- Decision: Detect `gh`/`glab` availability in PATH, try gh first
- Rationale: Simpler, works for most cases, no git remote parsing needed
- Alternative considered: Parse remote URL to determine GitHub/GitLab (deferred to future)

**3. Git status: Clean/dirty vs detailed state**
- Decision: Simple clean/dirty indicator (no staged vs unstaged details)
- Rationale: Matches story requirements, avoids UI clutter
- Detailed diff viewing deferred to future if needed

**4. PR state mapping: JSON to enum**
- Decision: Map GitHub/GitLab states to simple Open/Merged/Closed enum
- Rationale: Consistent across platforms, clear visual distinction
- GitHub states: OPEN → Open, MERGED → Merged, CLOSED → Closed
- GitLab states: opened → Open, merged → Merged, closed → Closed

**5. Command execution: Sync vs async**
- Decision: Synchronous command execution (blocking)
- Rationale: Simple, predictable, acceptable latency (<100ms for git, <2s for PR)
- Async execution deferred to future if dashboard load time becomes issue

### Technical Notes

**Git command assumptions:**
- Git repository exists (worktree created via `git worktree add`)
- `.git` file or directory present in worktree path
- Git commands work with `-C` flag to specify working directory

**PR CLI tool output formats:**
```bash
# GitHub (gh pr view --json url,state,number,title)
{"url": "https://github.com/org/repo/pull/42", "state": "OPEN", "number": 42, "title": "Add feature"}

# GitLab (glab mr view --output json)
{"url": "https://gitlab.com/org/repo/-/merge_requests/42", "state": "opened", "iid": 42, "title": "Add feature"}
```

**Error handling patterns:**
- No git repo → Show worktree without git status section
- Git command fails → Log warning, show "Git unavailable"
- No PR tool → Show worktree without PR link section
- PR not found → Show worktree without PR link (not an error)
- PR fetch fails → Use stale cache if available, otherwise no PR link

**Performance optimization (future):**
- Phase 6: Sequential git status checks (simple, predictable)
- Future: Parallel fetching with Futures if >10 worktrees
- Future: Background PR refresh to avoid blocking dashboard render

---

## Links to Related Documents

- **Analysis:** `project-management/issues/IWLE-100/analysis.md` (Story 5, lines 209-258)
- **Phase 1 Context:** `project-management/issues/IWLE-100/phase-01-context.md`
- **Phase 2 Context:** `project-management/issues/IWLE-100/phase-02-context.md`
- **Phase 3 Context:** `project-management/issues/IWLE-100/phase-03-context.md`
- **Phase 4 Context:** `project-management/issues/IWLE-100/phase-04-context.md`
- **Phase 5 Context:** `project-management/issues/IWLE-100/phase-05-context.md`
- **Implementation Log:** `project-management/issues/IWLE-100/implementation-log.md`
- **Task Index:** `project-management/issues/IWLE-100/tasks.md`

---

## Gherkin Scenarios (from Analysis)

```gherkin
Feature: Dashboard displays git and PR information
  As a developer managing multiple worktrees
  I want to see git branch status and PR links
  So that I can quickly check what needs review

Scenario: Clean working directory indicated
  Given worktree IWLE-123 is on branch "IWLE-123-phase-02"
  And the working directory is clean (no uncommitted changes)
  When I view the dashboard
  Then the card shows "Branch: IWLE-123-phase-02 ✓ clean"
  And the clean indicator is styled in green

Scenario: Uncommitted changes warning
  Given worktree IWLE-456 has uncommitted changes
  When I view the dashboard
  Then the card shows "Branch: IWLE-456-fix ⚠ uncommitted"
  And the warning indicator is styled in yellow

Scenario: PR links displayed when available
  Given worktree IWLE-123 has an open PR for phase 2
  And the PR URL is "https://github.com/org/repo/pull/42"
  When I view the dashboard
  Then I see a "View PR ↗" link on the card
  And clicking it opens the GitHub PR in a new tab
  And the link shows PR state "open" badge
```

**Test Automation:**
- Phase 6: Manual testing of scenarios (verify with real git repos and PRs)
- Unit tests for parsing logic (automated)
- Integration tests for command execution (automated with mocked commands)
- Future: Mock git/gh/glab output for automated E2E tests

---

**Phase Status:** Ready for Implementation

**Next Steps:**
1. Begin implementation following Step 1 (Domain Models)
2. Run tests continuously during development (TDD)
3. Manual testing after dashboard integration complete
4. Update implementation-log.md after completion
5. Mark Phase 6 complete in tasks.md
