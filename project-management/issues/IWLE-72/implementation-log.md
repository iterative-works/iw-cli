# Implementation Log: Create iw-cli - Project-local worktree and issue management tool

Issue: IWLE-72

This log tracks the evolution of implementation across phases.

---

## Phase 1: Bootstrap script runs tool via scala-cli (2025-12-11)

**What was built:**
- Bootstrap: `iw` - POSIX-compliant shell script with command discovery and execution
- Core: `.iw/core/Output.scala` - Output formatting utilities (info, error, success, section, keyValue)
- Command: `.iw/commands/version.scala` - Fully implemented version command with basic and verbose modes
- Stubs: 6 command stubs (init, doctor, start, open, rm, issue) ready for future phases

**Decisions made:**
- Used command discovery pattern with structured headers (PURPOSE, USAGE, ARGS, EXAMPLE) based on research in `research/approach-4-combined/`
- Commands declare their own dependencies via `//> using file` directives
- scala-cli handles compilation caching automatically (no custom JAR management needed)
- Added input validation to prevent path traversal attacks (security fix during code review)

**Patterns applied:**
- Script discovery: Bootstrap script scans `.iw/commands/` directory for available commands
- Structured headers: Machine-parseable metadata in Scala file comments enables LLM discoverability
- Self-contained commands: Each command declares its dependencies inline

**Testing:**
- Unit tests: 5 tests for Output utilities (OutputTest.scala)
- Integration tests: 0 (not applicable for shell script)
- E2E scenarios: 7 scenarios verified manually (version, help, list, describe, unknown, stubs)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20251211.md
- Major findings: Path traversal vulnerability fixed by adding command name validation

**Webapp verification:**
- Status: Skipped (CLI tool, not web feature)

**For next phases:**
- Available utilities: `Output` object with info/error/success/section/keyValue helpers
- Extension points: Add new commands by creating `.scala` files in `.iw/commands/` with structured headers
- Notes: Each command should import `iw.core.Output` via `//> using file "../core/Output.scala"`

**Files changed:**
```
A  iw
A  .iw/core/Output.scala
A  .iw/core/test/OutputTest.scala
A  .iw/core/test/DebugTest.scala
A  .iw/commands/version.scala
A  .iw/commands/init.scala
A  .iw/commands/doctor.scala
A  .iw/commands/start.scala
A  .iw/commands/open.scala
A  .iw/commands/rm.scala
A  .iw/commands/issue.scala
```

---

## Phase 2: Initialize project with issue tracker configuration (2025-12-11)

**What was built:**
- Domain: `.iw/core/Config.scala` - GitRemote value object, IssueTrackerType enum, ProjectConfiguration case class, TrackerDetector, ConfigSerializer
- Infrastructure: `.iw/core/Git.scala` - GitAdapter for reading git remote URLs and checking repo status
- Infrastructure: `.iw/core/ConfigRepository.scala` - ConfigFileRepository for HOCON file I/O
- Infrastructure: `.iw/core/Prompt.scala` - Interactive console prompts (ask, confirm)
- Command: `.iw/commands/init.scala` - Full interactive init workflow with tracker detection

**Decisions made:**
- HOCON format for configuration (standard Scala ecosystem, supports comments)
- No secrets in config file (API tokens in environment variables)
- Tracker detection based on git remote host: github.com → Linear, gitlab.e-bs.cz → YouTrack
- User can override suggested tracker if detection is wrong or host unknown
- Project name auto-detected from current directory name
- Fixed Output.scala to use System.out.println explicitly (test compatibility)

**Patterns applied:**
- Functional Core / Imperative Shell: Pure domain logic in Config.scala, I/O at edges
- Value Objects: GitRemote, ProjectConfiguration encapsulate domain concepts
- Repository Pattern: ConfigFileRepository abstracts file persistence
- Adapter Pattern: GitAdapter wraps git CLI commands

**Testing:**
- Unit tests: 19 tests (ConfigTest: 11, ConfigFileTest: 8)
- Integration tests: 10 tests (ConfigRepositoryTest: 5, GitTest: 5)
- E2E scenarios: 4 scenarios verified manually

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20251211.md
- Major findings: 0 critical, 2 warnings (scala-cli directive warning, Prompt untested), 7 suggestions

**Webapp verification:**
- Status: Skipped (CLI tool, not web feature)

**For next phases:**
- Available utilities:
  - `GitRemote(url)` - Parse git remote URLs and extract host
  - `TrackerDetector.suggestTracker(remote)` - Detect tracker from git host
  - `ConfigSerializer.toHocon/fromHocon` - HOCON serialization
  - `ConfigFileRepository.read/write` - Config persistence
  - `GitAdapter.getRemoteUrl/isGitRepository` - Git operations
  - `Prompt.ask/confirm` - Interactive user input
- Extension points: Add new tracker types to IssueTrackerType enum and TrackerDetector
- Notes: Configuration file location is `.iw/config.conf`, auto-created by init command

**Files changed:**
```
M  .iw/commands/init.scala
A  .iw/core/Config.scala
A  .iw/core/ConfigRepository.scala
A  .iw/core/Git.scala
A  .iw/core/Prompt.scala
A  .iw/core/test/ConfigFileTest.scala
A  .iw/core/test/ConfigRepositoryTest.scala
A  .iw/core/test/ConfigTest.scala
A  .iw/core/test/GitTest.scala
M  .iw/core/Output.scala (fixed System.out.println for test compatibility)
```

---

## Phase 3: Validate environment and configuration (2025-12-12)

**What was built:**
- Domain: `.iw/core/DoctorChecks.scala` - CheckResult enum (Success, Warning, Error, Skip) and DoctorChecksRegistry for extensible check registration
- Infrastructure: `.iw/core/Process.scala` - ProcessAdapter with commandExists for checking CLI tools in PATH
- Infrastructure: `.iw/core/LinearClient.scala` - Linear API client with token validation via GraphQL
- Command: `.iw/commands/doctor.scala` - Full doctor command with base checks (git repo, config) and formatted output
- Hook: `.iw/commands/issue.hook-doctor.scala` - Linear API token validation check
- Hook: `.iw/commands/start.hook-doctor.scala` - tmux installation check
- Bootstrap: `iw` - Updated with hook file discovery pattern (`*.hook-{command}.scala`)

**Decisions made:**
- Hook-based extensibility: Commands register their own checks via colocated `*.hook-doctor.scala` files
- Bootstrap uses `find` for safe hook discovery (handles empty results gracefully)
- Class.forName used to force hook object initialization (ensures registration code runs)
- Pure check functions (`ProjectConfiguration => CheckResult`) enable isolated testing
- Shell injection protection added via regex validation in ProcessAdapter (security fix during review)

**Patterns applied:**
- Plugin/Hook Pattern: Commands register checks dynamically without modifying doctor command
- Registry Pattern: DoctorChecksRegistry collects checks for centralized execution
- Adapter Pattern: ProcessAdapter and LinearClient wrap external system interactions
- Functional Core: Pure check functions with side effects at edges

**Testing:**
- Unit tests: 15 tests (DoctorChecksTest: 7, ProcessTest: 9 including security tests)
- Integration tests: 3 tests (LinearClientTest: token validation)
- E2E tests: 7 BATS scenarios (doctor.bats)

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20251212.md
- Major findings: 1 critical (shell injection) - FIXED, 16 warnings (accepted, non-blocking), 13 suggestions (deferred)

**Webapp verification:**
- Status: Skipped (CLI tool, not web feature)

**For next phases:**
- Available utilities:
  - `DoctorChecks.register(name)(check)` - Register new environment checks
  - `ProcessAdapter.commandExists(cmd)` - Check if CLI tool is available
  - `LinearClient.validateToken(token)` - Validate Linear API tokens
  - `CheckResult` enum for structured check results
- Extension points: Add new checks via `*.hook-doctor.scala` files in `.iw/commands/`
- Notes: Hook pattern enables future hook types (e.g., `*.hook-worktree-init.scala`)

**Files changed:**
```
M  iw (hook discovery pattern)
M  .iw/commands/doctor.scala (full implementation)
A  .iw/commands/issue.hook-doctor.scala (Linear token check)
A  .iw/commands/start.hook-doctor.scala (tmux check)
A  .iw/core/DoctorChecks.scala (registry and CheckResult)
A  .iw/core/LinearClient.scala (API client)
A  .iw/core/Process.scala (command checking)
A  .iw/core/test/DoctorChecksTest.scala
A  .iw/core/test/LinearClientTest.scala
A  .iw/core/test/ProcessTest.scala
A  .iw/test/doctor.bats (E2E tests)
```

---

## Phase 4: Create worktree for issue with tmux session (2025-12-13)

**What was built:**
- Domain: `.iw/core/IssueId.scala` - Issue ID value object with validation (PROJECT-123 format, case normalization)
- Domain: `.iw/core/WorktreePath.scala` - Worktree path calculation (sibling directory naming, session naming)
- Infrastructure: `.iw/core/Tmux.scala` - TmuxAdapter for session management (create, attach, exists, kill)
- Infrastructure: `.iw/core/GitWorktree.scala` - GitWorktreeAdapter for worktree operations (create, exists, branch checks)
- Infrastructure: `.iw/core/Process.scala` - Extended with ProcessResult case class and run() method
- Command: `.iw/commands/start.scala` - Full start command implementation orchestrating worktree creation and tmux session

**Decisions made:**
- Worktrees created as sibling directories named `{project}-{ISSUE-ID}` (e.g., `kanon-IWLE-123`)
- Branch name matches issue ID exactly (e.g., `IWLE-123`)
- Tmux session name matches directory name for consistency
- Issue IDs normalized to uppercase (e.g., `iwle-123` → `IWLE-123`)
- Existing branch reuse: If branch exists, use `git worktree add` without `-b` flag
- Cleanup on failure: If tmux session creation fails after worktree creation, remove the worktree
- Collision detection: Check for existing directory, worktree, and tmux session before creating

**Patterns applied:**
- Value Objects: IssueId and WorktreePath encapsulate domain validation and path logic
- Adapter Pattern: TmuxAdapter and GitWorktreeAdapter wrap CLI interactions
- Functional Core / Imperative Shell: Pure domain objects, effects only in adapters
- Either for error handling: Operations return `Either[String, Unit]` for explicit error propagation

**Testing:**
- Unit tests: 19 tests (IssueIdTest: 11, WorktreePathTest: 8)
- Integration tests: 16 tests (TmuxAdapterTest: 8, GitWorktreeAdapterTest: 8)
- E2E tests: 14 tests (start.bats)
- Total: 49 tests

**Code review:**
- Iterations: 1
- Review file: review-phase-04-20251213.md
- Major findings: 1 critical (C1: integration tests bypassed adapters) - FIXED
- Warnings: 7 (naming consistency, documentation, test logic) - noted for future
- Suggestions: 8 (opaque types, typed errors, property tests) - deferred

**Webapp verification:**
- Status: Skipped (CLI tool, not web feature)

**For next phases:**
- Available utilities:
  - `IssueId.parse(raw)` - Validate and normalize issue IDs
  - `WorktreePath(project, issueId)` - Calculate worktree paths and session names
  - `TmuxAdapter.sessionExists/createSession/attachSession/killSession` - Tmux operations
  - `GitWorktreeAdapter.worktreeExists/branchExists/createWorktree/createWorktreeForBranch` - Git worktree operations
  - `ProcessAdapter.run(command)` - Execute shell commands with result capture
- Extension points: Add new adapters following the same pattern (return Either, use ProcessAdapter)
- Notes: The open command (Phase 5) will reuse these adapters for attaching to existing worktrees

**Files changed:**
```
A  .iw/core/IssueId.scala
A  .iw/core/WorktreePath.scala
A  .iw/core/Tmux.scala
A  .iw/core/GitWorktree.scala
M  .iw/core/Process.scala (added ProcessResult and run method)
M  .iw/commands/start.scala (full implementation)
A  .iw/core/test/IssueIdTest.scala
A  .iw/core/test/WorktreePathTest.scala
A  .iw/core/test/TmuxAdapterTest.scala
A  .iw/core/test/GitWorktreeAdapterTest.scala
A  .iw/test/start.bats
```

---

## Phase 5: Open existing worktree tmux session (2025-12-14)

**What was built:**
- Command: `.iw/commands/open.scala` - Open command for attaching to existing worktree sessions
- Infrastructure: `.iw/core/Git.scala` - Extended with `getCurrentBranch()` method for branch inference
- Domain: `.iw/core/IssueId.scala` - Extended with `fromBranch()` method for extracting issue ID from branch name

**Decisions made:**
- Issue ID can be provided explicitly or inferred from current branch name
- If session exists: attach; if only worktree exists: create session then attach
- Nested tmux handling: detect if already inside tmux and provide helpful error message
- Lowercase issue IDs normalized to uppercase

**Patterns applied:**
- Reused adapters from Phase 4 (TmuxAdapter, GitWorktreeAdapter)
- Same error handling pattern with Either

**Testing:**
- Unit tests: 7 tests (IssueIdFromBranchTest)
- E2E tests: 8 tests (open.bats)
- Total: 15 new tests

**Code review:**
- Iterations: 1
- Major findings: No critical issues

**Files changed:**
```
M  .iw/commands/open.scala (full implementation)
M  .iw/core/Git.scala (added getCurrentBranch)
M  .iw/core/IssueId.scala (added fromBranch)
A  .iw/core/test/IssueIdFromBranchTest.scala
A  .iw/test/open.bats
```

---

## Phase 6: Remove worktree and cleanup resources (2025-12-15)

**What was built:**
- Domain: `.iw/core/DeletionSafety.scala` - Value object for deletion safety checks (uncommitted changes, active session)
- Infrastructure: `.iw/core/Git.scala` - Extended with `hasUncommittedChanges()` method
- Infrastructure: `.iw/core/Tmux.scala` - Extended with `isCurrentSession()` method
- Infrastructure: `.iw/core/GitWorktree.scala` - Extended with `removeWorktree()` method
- Infrastructure: `.iw/core/Output.scala` - Extended with `warning()` method for yellow warning text
- Command: `.iw/commands/rm.scala` - Full rm command with safety checks and cleanup workflow

**Decisions made:**
- Safety first: Check for active session (hard block) and uncommitted changes (soft block with confirmation)
- Branch preservation: Explicitly do NOT delete git branch when removing worktree (branch lifecycle tied to PR/MR)
- Force flag: `--force` bypasses uncommitted changes confirmation but NOT active session check
- Graceful degradation: Session kill failure logs warning but continues with worktree removal
- Cleanup order: Kill session first, then remove worktree directory

**Patterns applied:**
- Value Object: DeletionSafety encapsulates safety conditions (though not used in final implementation)
- Adapter Pattern: Extensions to GitAdapter, TmuxAdapter, GitWorktreeAdapter
- Either for error handling: All adapter operations return Either[String, Unit]
- Functional Core / Imperative Shell: Safety logic pure, effects at edges

**Testing:**
- Unit tests: 4 tests (DeletionSafetyTest: safety check logic)
- Integration tests: 11 tests (GitTest: 4 for hasUncommittedChanges, TmuxAdapterTest: 3 for isCurrentSession, GitWorktreeAdapterTest: 3 for removeWorktree, OutputTest: 1 for warning)
- E2E tests: 10 tests (rm.bats: success paths, error paths, branch preservation)
- Total: 25 new tests

**Code review:**
- Iterations: 1
- Review file: review-phase-06-20251215.md
- Major findings: 0 critical, 7 warnings (unused DeletionSafety abstraction, missing prompt tests, code organization), 7 suggestions
- Notes: DeletionSafety class defined and tested but not integrated into rm.scala workflow

**Webapp verification:**
- Status: Skipped (CLI tool, not web feature)

**For next phases:**
- Available utilities:
  - `GitAdapter.hasUncommittedChanges(path)` - Check for uncommitted changes in directory
  - `TmuxAdapter.isCurrentSession(name)` - Check if currently in specified tmux session
  - `GitWorktreeAdapter.removeWorktree(path, workDir, force)` - Remove git worktree
  - `Output.warning(msg)` - Print yellow warning message
  - `DeletionSafety` - Value object for safety checks (available but currently unused)
- Extension points: Prompt confirmation pattern reusable for other destructive operations
- Notes: rm command completes the worktree lifecycle (start → open → rm)

**Files changed:**
```
M  .iw/commands/rm.scala (full implementation)
A  .iw/core/DeletionSafety.scala (value object)
M  .iw/core/Git.scala (added hasUncommittedChanges)
M  .iw/core/GitWorktree.scala (added removeWorktree)
M  .iw/core/Output.scala (added warning)
M  .iw/core/Tmux.scala (added isCurrentSession)
A  .iw/core/test/DeletionSafetyTest.scala
M  .iw/core/test/GitTest.scala (added uncommitted changes tests)
M  .iw/core/test/GitWorktreeAdapterTest.scala (added removal tests)
M  .iw/core/test/OutputTest.scala (added warning test)
M  .iw/core/test/TmuxAdapterTest.scala (added current session tests)
A  .iw/test/rm.bats (E2E tests)
```

---

## Phase 7: Fetch and display issue details (2025-12-15)

**What was built:**
- Domain: `.iw/core/Issue.scala` - Issue entity (id, title, status, assignee, description) and IssueTracker trait
- Infrastructure: `.iw/core/LinearClient.scala` - Extended with `fetchIssue()` method using GraphQL API
- Infrastructure: `.iw/core/YouTrackClient.scala` - YouTrack REST API client with customFields parsing
- Infrastructure: `.iw/core/IssueFormatter.scala` - Output formatter with Unicode borders
- Command: `.iw/commands/issue.scala` - Full issue command with tracker selection and branch inference

**Decisions made:**
- Linear uses GraphQL API with issue identifier lookup (no "Bearer" prefix for token)
- YouTrack uses REST API with custom fields for State and Assignee
- Issue ID can be provided explicitly or inferred from current branch
- Added upickle dependency for JSON parsing (both trackers return JSON)
- Output format uses Unicode borders (━) for visual separation
- Missing assignee shows "None", missing description omits the section

**Patterns applied:**
- Adapter Pattern: LinearClient and YouTrackClient implement similar fetchIssue() interface
- Value Objects: Issue entity, reused IssueId from Phase 4
- Functional Core / Imperative Shell: Pure domain model, HTTP at edges
- Either for error handling: All operations return Either[String, Issue]

**Testing:**
- Unit tests: 24 tests (IssueTest: 4, IssueFormatterTest: 5, LinearIssueTrackerTest: 7, YouTrackIssueTrackerTest: 8)
- E2E tests: 6 tests (issue.bats)
- Total: 30 new tests

**Code review:**
- Iterations: 1
- Review file: review-packet-phase-07.md
- Major findings: No critical issues

**Webapp verification:**
- Status: Skipped (CLI tool, not web feature)

**For next phases:**
- Available utilities:
  - `LinearClient.fetchIssue(issueId, token)` - Fetch issue from Linear GraphQL API
  - `YouTrackClient.fetchIssue(issueId, baseUrl, token)` - Fetch issue from YouTrack REST API
  - `IssueFormatter.format(issue)` - Format issue for console output
- Extension points: Add more fields to Issue entity, add more trackers implementing IssueTracker trait
- Notes: This completes the iw-cli tool - all 7 phases implemented

**Files changed:**
```
A  .iw/core/Issue.scala
A  .iw/core/IssueFormatter.scala
A  .iw/core/YouTrackClient.scala
M  .iw/core/LinearClient.scala (added fetchIssue)
M  .iw/commands/issue.scala (full implementation)
A  .iw/core/test/IssueTest.scala
A  .iw/core/test/IssueFormatterTest.scala
A  .iw/core/test/LinearIssueTrackerTest.scala
A  .iw/core/test/YouTrackIssueTrackerTest.scala
A  .iw/test/issue.bats
```

---
