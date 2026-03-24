# Implementation Log: Add `iw implement` and `iw analyze` commands

Issue: IW-309

This log tracks the evolution of implementation across phases.

---

## Phase 1: `analyze` command — triage shortcut (2026-03-24)

**Layer:** Presentation (commands/)

**What was built:**
- `.iw/commands/analyze.scala` - Thin wrapper that delegates to `iw start --prompt "/iterative-works:triage-issue"`. Locates `iw-run` via `IW_COMMANDS_DIR` env var to support both dev and release layouts.
- `.iw/test/analyze.bats` - 3 E2E tests: missing arg error, worktree+session creation, triage prompt verification.

**Dependencies on other layers:**
- `ProcessAdapter.runInteractive()` - subprocess execution with inherited I/O
- `Output.error()` / `Output.info()` - error and usage messages
- `iw start --prompt` - the underlying command being delegated to

**Testing:**
- E2E tests: 3 tests added (analyze.bats)
- Regression: start-prompt.bats (8 tests) still passes

**Code review:**
- Iterations: 1
- Findings: No critical issues. Applied feedback on control flow clarity and test coupling.

**Files changed:**
```
A	.iw/commands/analyze.scala
A	.iw/test/analyze.bats
```

---
