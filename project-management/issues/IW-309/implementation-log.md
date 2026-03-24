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

## Phase 2: `implement` command — workflow-aware dispatcher (2026-03-24)

**Layer:** Presentation (commands/)

**What was built:**
- `.iw/commands/implement.scala` - Workflow-aware dispatcher that reads `workflow_type` from `review-state.json`, maps it to a short code (`ag`/`wf`/`dx`) via `BatchImplement.resolveWorkflowCode()`, and spawns `claude` as an interactive foreground process with the resolved slash command. Supports `--batch` delegation to `iw batch-implement` with flag passthrough.
- `.iw/test/implement.bats` - 11 E2E tests using mock `claude` and `iw` scripts to verify command construction without running actual subprocesses.

**Dependencies on other layers:**
- `BatchImplement.resolveWorkflowCode()` - workflow type → short code mapping
- `ReviewStateAdapter.read()` - reads review-state.json
- `ProcessAdapter.runInteractive()` - subprocess execution with inherited I/O
- `ProcessAdapter.commandExists()` - claude CLI availability check
- `IssueId.parse()` / `IssueId.fromBranch()` - issue ID resolution
- `PhaseArgs.namedArg()` / `PhaseArgs.hasFlag()` - argument parsing

**Testing:**
- E2E tests: 11 tests added (implement.bats)
- Regression: analyze.bats (3 tests) still passes

**Code review:**
- Iterations: 1
- Findings: No critical issues. Applied feedback on mutable var (replaced with functional construction), removed unused parameter, cleaned up restating comments.
- Review file: review-phase-02-20260324-150754.md

**Files changed:**
```
A	.iw/commands/implement.scala
A	.iw/test/implement.bats
```

---
