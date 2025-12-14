# Phase 4 Context: Create worktree for issue with tmux session

**Issue:** IWLE-72
**Phase:** 4 of 7
**Status:** Ready for Implementation

---

## 1. Goals

This phase implements the `iw start <issue-id>` command that creates an isolated worktree for a specific issue and launches a tmux session for it.

**Primary Objectives:**
- Create sibling worktree named `{project}-{ISSUE-ID}` (e.g., `kanon-IWLE-123`)
- Create git branch matching the issue ID
- Create tmux session with the same name as the worktree
- Attach the user to the tmux session with working directory in the new worktree
- Handle edge cases: existing worktree, existing directory, existing branch

---

## 2. Scope

### In Scope
- `IssueId` value object with validation (pattern: `[A-Z]+-[0-9]+`)
- `WorktreePath` value object for sibling directory naming
- `TmuxAdapter` for session creation and attachment
- `GitWorktreeAdapter` for worktree creation
- `iw start <issue-id>` command implementation
- Error handling for collisions (worktree exists, directory exists)
- Helpful hints when collisions occur ("use `./iw open` instead")

### Out of Scope
- Network operations (no issue validation against tracker API)
- Branch deletion on worktree removal (handled in Phase 6)
- Session resumption (handled in Phase 5)
- Worktree listing functionality

---

## 3. Dependencies from Previous Phases

**From Phase 1 (Bootstrap):**
- Bootstrap script `iw` for command discovery and execution
- `Output` utilities (info, error, success, section, keyValue)
- Command structure pattern with headers

**From Phase 2 (Init):**
- `ConfigFileRepository.read()` - read project configuration
- `ProjectConfiguration` - get project name for worktree naming
- `GitAdapter.isGitRepository()` - validate we're in a git repo

**From Phase 3 (Doctor):**
- `ProcessAdapter.commandExists("tmux")` - verify tmux available
- `ProcessAdapter.run(command)` - execute shell commands (can be reused)

---

## 4. Technical Approach

### Domain Layer

```scala
// .iw/core/IssueId.scala
package iw.core

case class IssueId private (value: String):
  def toBranchName: String = value

object IssueId:
  private val Pattern = """^[A-Z]+-[0-9]+$""".r

  def parse(raw: String): Either[String, IssueId] =
    raw.toUpperCase.trim match
      case Pattern() => Right(IssueId(raw.toUpperCase.trim))
      case _ => Left(s"Invalid issue ID format: $raw (expected: PROJECT-123)")

// .iw/core/WorktreePath.scala
package iw.core

import java.nio.file.{Path, Paths}

case class WorktreePath(projectName: String, issueId: IssueId):
  def directoryName: String = s"$projectName-${issueId.value}"

  /** Resolve to actual path as sibling of current directory */
  def resolve(currentDir: Path): Path =
    currentDir.getParent.resolve(directoryName)

  def sessionName: String = directoryName
```

### Infrastructure Layer

```scala
// .iw/core/Tmux.scala
package iw.core

object TmuxAdapter:
  /** Check if a tmux session with the given name exists */
  def sessionExists(name: String): Boolean =
    ProcessAdapter.run(Seq("tmux", "has-session", "-t", name)).exitCode == 0

  /** Create a new tmux session in the given directory */
  def createSession(name: String, workDir: Path): Either[String, Unit] =
    val result = ProcessAdapter.run(
      Seq("tmux", "new-session", "-d", "-s", name, "-c", workDir.toString)
    )
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to create tmux session: ${result.stderr}")

  /** Attach to an existing tmux session */
  def attachSession(name: String): Either[String, Unit] =
    val result = ProcessAdapter.run(Seq("tmux", "attach-session", "-t", name))
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to attach to session: ${result.stderr}")

// .iw/core/GitWorktree.scala
package iw.core

import java.nio.file.Path

object GitWorktreeAdapter:
  /** Check if a worktree exists for the given path */
  def worktreeExists(path: Path): Boolean =
    val result = ProcessAdapter.run(Seq("git", "worktree", "list", "--porcelain"))
    result.stdout.contains(s"worktree ${path.toAbsolutePath}")

  /** Create a new worktree with a new branch */
  def createWorktree(path: Path, branchName: String): Either[String, Unit] =
    val result = ProcessAdapter.run(
      Seq("git", "worktree", "add", "-b", branchName, path.toString)
    )
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to create worktree: ${result.stderr}")

  /** Create worktree for existing branch */
  def createWorktreeForBranch(path: Path, branchName: String): Either[String, Unit] =
    val result = ProcessAdapter.run(
      Seq("git", "worktree", "add", path.toString, branchName)
    )
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to create worktree: ${result.stderr}")

  /** Check if a branch exists */
  def branchExists(branchName: String): Boolean =
    val result = ProcessAdapter.run(
      Seq("git", "show-ref", "--verify", "--quiet", s"refs/heads/$branchName")
    )
    result.exitCode == 0
```

### Command Implementation

```scala
// .iw/commands/start.scala
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

// PURPOSE: Creates an isolated worktree for a specific issue with a tmux session.
// USAGE: iw start <issue-id>

@main def start(args: String*): Unit =
  if args.isEmpty then
    Output.error("Missing issue ID")
    Output.info("Usage: iw start <issue-id>")
    sys.exit(1)

  val rawIssueId = args.head

  // Validate issue ID format
  IssueId.parse(rawIssueId) match
    case Left(error) =>
      Output.error(error)
      sys.exit(1)
    case Right(issueId) =>
      createWorktreeForIssue(issueId)

def createWorktreeForIssue(issueId: IssueId): Unit =
  // Read project config to get project name
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
      val branchName = issueId.toBranchName

      // Check for collisions
      if Files.exists(targetPath) then
        Output.error(s"Directory ${worktreePath.directoryName} already exists")
        if GitWorktreeAdapter.worktreeExists(targetPath) then
          Output.info(s"Use './iw open ${issueId.value}' to open existing worktree")
        sys.exit(1)

      if TmuxAdapter.sessionExists(sessionName) then
        Output.error(s"Tmux session '$sessionName' already exists")
        Output.info(s"Use './iw open ${issueId.value}' to attach to existing session")
        sys.exit(1)

      // Create worktree (with new branch or existing)
      Output.info(s"Creating worktree ${worktreePath.directoryName}...")

      val worktreeResult =
        if GitWorktreeAdapter.branchExists(branchName) then
          Output.info(s"Using existing branch '$branchName'")
          GitWorktreeAdapter.createWorktreeForBranch(targetPath, branchName)
        else
          Output.info(s"Creating new branch '$branchName'")
          GitWorktreeAdapter.createWorktree(targetPath, branchName)

      worktreeResult match
        case Left(error) =>
          Output.error(error)
          sys.exit(1)
        case Right(_) =>
          Output.success(s"Worktree created at ${targetPath}")

      // Create tmux session
      Output.info(s"Creating tmux session '$sessionName'...")
      TmuxAdapter.createSession(sessionName, targetPath) match
        case Left(error) =>
          Output.error(error)
          // Cleanup: remove worktree on tmux failure
          Output.info("Cleaning up worktree...")
          ProcessAdapter.run(Seq("git", "worktree", "remove", targetPath.toString))
          sys.exit(1)
        case Right(_) =>
          Output.success(s"Tmux session created")

      // Attach to session
      Output.info(s"Attaching to session...")
      TmuxAdapter.attachSession(sessionName) match
        case Left(error) =>
          Output.error(error)
          Output.info(s"Session created. Attach manually with: tmux attach -t $sessionName")
          sys.exit(1)
        case Right(_) =>
          () // Successfully attached and detached
```

---

## 5. Files to Modify/Create

**New files:**
- `.iw/core/IssueId.scala` - Issue ID value object with validation
- `.iw/core/WorktreePath.scala` - Worktree path calculation
- `.iw/core/Tmux.scala` - TmuxAdapter for session management
- `.iw/core/GitWorktree.scala` - GitWorktreeAdapter for worktree operations
- `.iw/core/test/IssueIdTest.scala` - Unit tests for IssueId
- `.iw/core/test/WorktreePathTest.scala` - Unit tests for WorktreePath
- `.iw/core/test/TmuxAdapterTest.scala` - Integration tests for tmux
- `.iw/core/test/GitWorktreeAdapterTest.scala` - Integration tests for git worktree
- `.iw/test/start.bats` - E2E tests for start command

**Modified files:**
- `.iw/commands/start.scala` - Full implementation replacing stub

---

## 6. Testing Strategy

### Unit Tests
- `IssueId.parse` with valid/invalid formats
- `WorktreePath.directoryName` and `resolve` calculations
- `WorktreePath.sessionName` matches directory name

### Integration Tests
- `TmuxAdapter.sessionExists` with real tmux
- `TmuxAdapter.createSession` and verify session exists
- `GitWorktreeAdapter.branchExists` with real git repo
- `GitWorktreeAdapter.createWorktree` in temporary repo

### E2E Scenario Tests (BATS)

1. **Successfully create worktree for issue**
   - Setup: Clean git repo, no existing worktree
   - Run: `./iw start IWLE-123`
   - Expect: Worktree created, tmux session created, user attached

2. **Worktree already exists**
   - Setup: Worktree for IWLE-123 already exists
   - Run: `./iw start IWLE-123`
   - Expect: Error message, hint to use `./iw open`

3. **Directory collision (not a worktree)**
   - Setup: Directory `project-IWLE-123` exists but is not a worktree
   - Run: `./iw start IWLE-123`
   - Expect: Error message about existing directory

4. **Invalid issue ID format**
   - Run: `./iw start invalid-123`
   - Expect: Error about invalid format

5. **Missing configuration**
   - Setup: No `.iw/config.conf`
   - Run: `./iw start IWLE-123`
   - Expect: Error, hint to run `./iw init`

6. **Branch already exists**
   - Setup: Branch `IWLE-123` exists from previous work
   - Run: `./iw start IWLE-123`
   - Expect: Worktree created using existing branch

---

## 7. Acceptance Criteria

- [ ] `IssueId` value object validates format `PROJECT-123`
- [ ] `WorktreePath` calculates sibling directory correctly
- [ ] Worktree created as sibling of current directory
- [ ] Branch created matching issue ID (or uses existing branch)
- [ ] Tmux session created with same name as worktree directory
- [ ] User attached to tmux session after creation
- [ ] Working directory in session is the new worktree
- [ ] Error on directory collision with helpful message
- [ ] Error on worktree collision with hint to use `open`
- [ ] Error on missing configuration with hint to run `init`
- [ ] Cleanup worktree if tmux session creation fails
- [ ] Exit code 0 on success, 1 on failure

---

## 8. Notes

- No network required - `iw start` works offline from issue ID alone
- Branch is created from current HEAD (typically main/master)
- tmux must be installed (checked by `iw doctor`)
- Session attachment will fail if already inside tmux - user should detach first
- Consider `exec` for final tmux attach to replace current shell
