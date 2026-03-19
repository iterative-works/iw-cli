# Implementation Log: Add `iw batch-implement` command

Issue: IW-275

This log tracks the evolution of implementation across phases.

---

## Phase 1: Move MarkdownTaskParser to model (2026-03-19)

**Layer:** Domain (refactoring)

**What was built:**
- Moved `MarkdownTaskParser.scala` (with `TaskCount`, `PhaseIndexEntry`) from `iw.core.dashboard` to `iw.core.model`
- Updated all imports in `WorkflowProgressService`, test files

**Dependencies on other layers:**
- None (prerequisite refactoring for Phase 2)

**Testing:**
- Unit tests: 0 new (existing tests updated imports, all pass)
- Integration tests: 0 new
- E2E tests: all pass

**Code review:**
- Iterations: 1
- Findings: No critical issues. Fixed unused import, merged split imports, updated PURPOSE comment.

**Files changed:**
```
M  .iw/core/dashboard/WorkflowProgressService.scala
R  .iw/core/dashboard/MarkdownTaskParser.scala → .iw/core/model/MarkdownTaskParser.scala
M  .iw/core/test/MarkdownTaskParserTest.scala
M  .iw/core/test/WorkflowProgressServiceTest.scala
```

---

## Phase 2: Batch implementation decision logic (2026-03-19)

**Layer:** Domain (pure model)

**What was built:**
- `PhaseOutcome` enum — ADT representing possible outcomes after phase execution (MergePR, MarkDone, Recover, Fail)
- `BatchImplement` object — 5 pure decision functions for the batch-implement loop:
  - `decideOutcome`: maps review-state status string to PhaseOutcome
  - `isTerminal`: derived from decideOutcome, checks if status is final
  - `nextPhase`: finds first unchecked phase from PhaseIndexEntry list
  - `resolveWorkflowCode`: maps workflow type ("agile"/"waterfall") to short code ("ag"/"wf")
  - `markPhaseComplete`: regex-based checkbox replacement in tasks.md content

**Dependencies on other layers:**
- Uses `PhaseIndexEntry` from Phase 1's relocated `MarkdownTaskParser`
- No I/O, no imports from adapters/output/dashboard

**Testing:**
- Unit tests: 30 new tests covering all functions and edge cases
- Integration tests: 0 new (pure functions, no integration needed)
- E2E tests: all pass (no downstream impact)

**Code review:**
- Iterations: 1
- Findings: Fixed 2 warnings — removed early `return` statements (refactored to expression-oriented style), derived `isTerminal` from `decideOutcome` to eliminate knowledge duplication. Updated Scaladoc.
- Review file: review-phase-02-20260319-184835.md

**Files changed:**
```
A  .iw/core/model/BatchImplement.scala
A  .iw/core/test/BatchImplementTest.scala
```

---

## Phase 3: batch-implement command script (2026-03-19)

**Layer:** Presentation (command script)

**What was built:**
- `batch-implement.scala` command — orchestrates unattended phase-by-phase implementation via claude CLI
  - Argument parsing: issue ID (positional/auto-detect from branch), workflow code (positional/auto-detect from review-state.json), model, max-turns, max-retries, max-budget-usd
  - Pre-flight checks: tasks.md, claude CLI, forge CLI (gh/glab), clean working tree
  - Phase loop: finds next unchecked phase, invokes `claude -p` via ProcessAdapter.runStreaming
  - Outcome handling via BatchImplement.decideOutcome: MergePR (merge via forge CLI, advance branch), MarkDone (mark tasks.md), Recover (resume session with targeted prompt), Fail (exit)
  - Recovery loop with configurable max retries
  - Completion flow: invokes claude for final PR/release notes after all phases done
  - Session logging to batch-implement.log
  - Commits any uncommitted changes claude leaves behind

**Dependencies on other layers:**
- Phase 2: `BatchImplement` pure decision functions (decideOutcome, nextPhase, resolveWorkflowCode, markPhaseComplete)
- Phase 1: `MarkdownTaskParser.parsePhaseIndex` for reading tasks.md
- Existing adapters: ProcessAdapter, GitAdapter, ReviewStateAdapter, ConfigFileRepository, ForgeType

**Testing:**
- Unit tests: 0 new (decision logic tested in Phase 2)
- E2E tests: 9 BATS tests (4 pre-flight validation, 1 forge CLI, 4 argument parsing/auto-detection)

**Code review:**
- Iterations: 1
- Findings: Fixed PR URL validation (security), removed untrusted status from recovery prompt (prompt injection), added gh stub to tests, improved test assertions, extracted timeout constant, renamed parameter.

**Files changed:**
```
A  .iw/commands/batch-implement.scala
A  .iw/test/batch-implement.bats
```

---
