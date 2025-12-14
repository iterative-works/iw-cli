# Phase 5 Context: Open existing worktree tmux session

**Issue:** IWLE-72
**Phase:** 5 of 7
**Status:** Ready for Implementation

---

## 1. Goals

This phase implements the `iw open [issue-id]` command that attaches to an existing tmux session for an issue, or creates one if the worktree exists but the session does not.

**Primary Objectives:**
- Attach to existing tmux session `{project}-{ISSUE-ID}` if running
- Create and attach to new session if worktree exists but session does not
- Infer issue ID from current branch when no parameter provided
- Handle edge cases: worktree not found, already inside tmux, nested sessions

---

## 2. Scope

### In Scope
- `iw open IWLE-123` - Open session for specific issue
- `iw open` (no parameter) - Infer issue from current branch
- Create tmux session if worktree exists but session does not
- Attach to existing tmux session
- Detect and handle nested tmux scenarios (already inside tmux)
- Error messages with actionable hints

### Out of Scope
- Creating new worktrees (use `iw start` for that)
- Issue validation against tracker API (no network required)
- Listing available worktrees/sessions

---

## 3. Dependencies from Previous Phases

**From Phase 1 (Bootstrap):**
- Bootstrap script `iw` for command discovery and execution
- `Output` utilities (info, error, success, section, keyValue)

**From Phase 2 (Init):**
- `ConfigFileRepository.read()` - read project configuration
- `ProjectConfiguration` - get project name for session naming

**From Phase 3 (Doctor):**
- `ProcessAdapter.commandExists("tmux")` - verify tmux available

**From Phase 4 (Start):**
- `IssueId.parse(raw)` - validate and normalize issue ID format
- `WorktreePath(projectName, issueId)` - calculate worktree path and session name
- `TmuxAdapter.sessionExists(name)` - check if session exists
- `TmuxAdapter.createSession(name, workDir)` - create new session
- `TmuxAdapter.attachSession(name)` - attach to existing session
- `GitWorktreeAdapter.worktreeExists(path)` - check if worktree exists

---

## 4. Technical Approach

### New Infrastructure - Branch Detection

```scala
// .iw/core/Git.scala (add to existing GitAdapter)
object GitAdapter:
  // ... existing methods ...

  /** Get the current branch name */
  def getCurrentBranch(): Either[String, String] =
    val result = ProcessAdapter.run(Seq("git", "rev-parse", "--abbrev-ref", "HEAD"))
    if result.exitCode == 0 then
      Right(result.stdout.trim)
    else
      Left(s"Failed to get current branch: ${result.stderr}")
```

### New Infrastructure - Tmux Environment Detection

```scala
// .iw/core/Tmux.scala (add to existing TmuxAdapter)
object TmuxAdapter:
  // ... existing methods ...

  /** Check if currently running inside a tmux session */
  def isInsideTmux: Boolean =
    sys.env.contains("TMUX")

  /** Get current tmux session name if inside tmux */
  def currentSessionName: Option[String] =
    if !isInsideTmux then None
    else
      val result = ProcessAdapter.run(Seq("tmux", "display-message", "-p", "#S"))
      if result.exitCode == 0 then Some(result.stdout.trim)
      else None
```

### Domain Layer - Issue ID Inference

```scala
// .iw/core/IssueId.scala (add to existing object)
object IssueId:
  // ... existing parse method ...

  /** Extract issue ID from a branch name (e.g., "IWLE-123" or "IWLE-123-some-description") */
  def fromBranch(branchName: String): Either[String, IssueId] =
    val Pattern = """^([A-Z]+-[0-9]+)""".r
    branchName.toUpperCase match
      case Pattern(issueId) => Right(IssueId(issueId))
      case _ => Left(s"Cannot extract issue ID from branch '$branchName' (expected: PROJECT-123[-description])")
```

### Command Implementation

```scala
// .iw/commands/open.scala
//> using file "../core/Output.scala"
//> using file "../core/Config.scala"
//> using file "../core/ConfigRepository.scala"
//> using file "../core/Process.scala"
//> using file "../core/IssueId.scala"
//> using file "../core/WorktreePath.scala"
//> using file "../core/Tmux.scala"
//> using file "../core/GitWorktree.scala"
//> using file "../core/Git.scala"

import iw.core.*
import java.nio.file.{Files, Paths}

// PURPOSE: Opens an existing worktree tmux session, creating session if needed.
// USAGE: iw open [issue-id]

@main def open(args: String*): Unit =
  // Resolve issue ID (from args or current branch)
  val issueIdResult = args.headOption match
    case Some(rawId) => IssueId.parse(rawId)
    case None => inferIssueFromBranch()

  issueIdResult match
    case Left(error) =>
      Output.error(error)
      sys.exit(1)
    case Right(issueId) =>
      openWorktreeSession(issueId)

def inferIssueFromBranch(): Either[String, IssueId] =
  GitAdapter.getCurrentBranch().flatMap(IssueId.fromBranch)

def openWorktreeSession(issueId: IssueId): Unit =
  // Read project config
  ConfigFileRepository.read() match
    case Left(error) =>
      Output.error(s"Cannot read configuration: $error")
      Output.info("Run './iw init' to initialize the project")
      sys.exit(1)
    case Right(config) =>
      val currentDir = Paths.get(".").toAbsolutePath.normalize
      val worktreePath = WorktreePath(config.projectName, issueId)
      val targetPath = worktreePath.resolve(currentDir)
      val sessionName = worktreePath.sessionName

      // Check worktree exists
      if !Files.exists(targetPath) then
        Output.error(s"Worktree not found: ${worktreePath.directoryName}")
        Output.info(s"Use './iw start ${issueId.value}' to create a new worktree")
        sys.exit(1)

      // Handle nested tmux scenario
      if TmuxAdapter.isInsideTmux then
        TmuxAdapter.currentSessionName match
          case Some(current) if current == sessionName =>
            Output.info(s"Already in session '$sessionName'")
            sys.exit(0)
          case Some(current) =>
            Output.error(s"Already inside tmux session '$current'")
            Output.info("Detach first with: Ctrl+B, D")
            Output.info(s"Then run: ./iw open ${issueId.value}")
            sys.exit(1)
          case None =>
            Output.error("Inside tmux but cannot determine session name")
            sys.exit(1)

      // Check if session exists, create if not
      if TmuxAdapter.sessionExists(sessionName) then
        Output.info(s"Attaching to session '$sessionName'...")
        TmuxAdapter.attachSession(sessionName) match
          case Left(error) =>
            Output.error(error)
            sys.exit(1)
          case Right(_) =>
            () // Successfully attached and detached
      else
        Output.info(s"Creating session '$sessionName' for existing worktree...")
        TmuxAdapter.createSession(sessionName, targetPath) match
          case Left(error) =>
            Output.error(s"Failed to create session: $error")
            sys.exit(1)
          case Right(_) =>
            Output.success("Session created")
            TmuxAdapter.attachSession(sessionName) match
              case Left(error) =>
                Output.error(error)
                Output.info(s"Attach manually with: tmux attach -t $sessionName")
                sys.exit(1)
              case Right(_) =>
                ()
```

---

## 5. Files to Modify/Create

**Modified files:**
- `.iw/core/Git.scala` - Add `getCurrentBranch()` method to GitAdapter
- `.iw/core/Tmux.scala` - Add `isInsideTmux` and `currentSessionName` methods
- `.iw/core/IssueId.scala` - Add `fromBranch()` extraction method
- `.iw/commands/open.scala` - Full implementation replacing stub

**New files:**
- `.iw/core/test/IssueIdFromBranchTest.scala` - Unit tests for branch parsing
- `.iw/core/test/GitCurrentBranchTest.scala` - Integration tests for branch detection
- `.iw/core/test/TmuxEnvironmentTest.scala` - Tests for tmux environment detection
- `.iw/test/open.bats` - E2E tests for open command

---

## 6. Testing Strategy

### Unit Tests
- `IssueId.fromBranch` with various branch formats:
  - `IWLE-123` → success
  - `IWLE-123-some-description` → success (extracts IWLE-123)
  - `iwle-123-lower` → success (case normalized)
  - `feature/IWLE-123` → success (extracts from prefix)
  - `main` → error
  - `some-random-branch` → error

### Integration Tests
- `GitAdapter.getCurrentBranch` with real git repo
- `TmuxAdapter.isInsideTmux` (test with/without TMUX env var)
- `TmuxAdapter.currentSessionName` (requires tmux running)

### E2E Scenario Tests (BATS)

1. **Open session with explicit issue ID**
   - Setup: Worktree and session exist for IWLE-123
   - Run: `./iw open IWLE-123`
   - Expect: Attach to session (exit 0)

2. **Open session, infer issue from branch**
   - Setup: In worktree with branch IWLE-123, session exists
   - Run: `./iw open` (no args)
   - Expect: Infer IWLE-123 from branch, attach to session

3. **Create session for existing worktree**
   - Setup: Worktree exists but session does not
   - Run: `./iw open IWLE-123`
   - Expect: Create session, attach

4. **Worktree does not exist**
   - Run: `./iw open IWLE-999`
   - Expect: Error, hint to use `./iw start`

5. **Missing configuration**
   - Setup: No `.iw/config.conf`
   - Run: `./iw open IWLE-123`
   - Expect: Error, hint to run `./iw init`

6. **Invalid issue ID format**
   - Run: `./iw open invalid-123`
   - Expect: Error about invalid format

7. **Cannot infer issue from branch (non-issue branch)**
   - Setup: On branch `main`
   - Run: `./iw open` (no args)
   - Expect: Error about cannot extract issue ID

8. **Nested tmux scenario (different session)**
   - Setup: Inside tmux session `other-session`, IWLE-123 worktree exists
   - Run: `./iw open IWLE-123`
   - Expect: Error, hint to detach first

9. **Already in target session**
   - Setup: Inside tmux session `project-IWLE-123`
   - Run: `./iw open IWLE-123`
   - Expect: Success message "Already in session", exit 0

---

## 7. Acceptance Criteria

- [ ] `iw open IWLE-123` attaches to existing session
- [ ] `iw open IWLE-123` creates session if worktree exists but session does not
- [ ] `iw open` (no args) infers issue ID from current branch
- [ ] Error on worktree not found with hint to use `start`
- [ ] Error on invalid issue ID format
- [ ] Error on cannot infer issue from non-issue branch
- [ ] Graceful handling of nested tmux (error with hint to detach)
- [ ] If already in target session, exit successfully with message
- [ ] Error on missing configuration with hint to run `init`
- [ ] Exit code 0 on success, 1 on failure

---

## 8. Notes

- Reuses most infrastructure from Phase 4 (IssueId, WorktreePath, TmuxAdapter, GitWorktreeAdapter)
- No network required - works entirely with local git and tmux state
- Branch inference uses simple prefix matching (first PROJECT-NNN pattern)
- Nested tmux detection uses TMUX environment variable (standard tmux behavior)
- Session switch from inside tmux requires detach-attach workflow (tmux limitation)
