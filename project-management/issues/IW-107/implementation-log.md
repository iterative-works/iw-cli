# Implementation Log: claude-sync template path resolution fix

Issue: IW-107

This log tracks the evolution of implementation across phases.

---

## Phase 1: Template path resolution fix (2026-01-12)

**What was built:**
- Constant: `.iw/core/Constants.scala` - Added `IwCommandsDir = "IW_COMMANDS_DIR"` to EnvVars object
- Feature: `.iw/commands/claude-sync.scala` - Modified template path resolution to use IW_COMMANDS_DIR env var with fallback to os.pwd
- Tests: `.iw/test/claude-sync.bats` - E2E tests for path resolution scenarios

**Decisions made:**
- Use `IW_COMMANDS_DIR` environment variable (already set by iw-run bootstrap) to locate installation directory
- Path calculation: `IW_COMMANDS_DIR` points to `.iw/commands`, so navigate up one level to `.iw` then to `scripts/`
- Fallback to `os.pwd / ".iw"` for development workflow (running from iw-cli repo directly)

**Patterns applied:**
- Option.map().getOrElse() for clean fallback logic with environment variable
- Used existing Constants.EnvVars pattern for consistency

**Testing:**
- E2E tests: 3 tests added
  - Template found via IW_COMMANDS_DIR
  - Fallback to os.pwd works
  - Graceful failure when template missing

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260112-110706.md
- Major findings: No critical issues. One suggestion to extract path resolution to testable function (deferred as low priority for simple logic).

**For next phases:**
- Available utilities: Constants.EnvVars.IwCommandsDir for installation directory lookup
- Extension points: Same pattern can be used for other templates/resources
- Notes: Error messaging improvements planned for Phase 2

**Files changed:**
```
M .iw/commands/claude-sync.scala
M .iw/core/Constants.scala
A .iw/test/claude-sync.bats
```

---
