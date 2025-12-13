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
