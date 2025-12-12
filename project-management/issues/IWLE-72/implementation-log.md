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
