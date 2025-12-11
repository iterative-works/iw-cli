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
