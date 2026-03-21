# Implementation Log: phase-pr leaves review-state.json uncommitted

Issue: IW-292

This log tracks the evolution of implementation across phases.

---

## Phase 1: phase-pr does not commit review-state.json update (2026-03-21)

**Root cause:** `phase-pr.scala` calls `ReviewStateAdapter.update()` which writes review-state.json to disk, but never stages or commits the change. This leaves a dirty working tree that blocks downstream workflows.

**Fix applied:**
- `.iw/core/adapters/Git.scala` — Added `stageFiles(paths: Seq[os.Path], dir: os.Path)` method for targeted git staging (avoids `stageAll` which could stage unrelated files). Returns `Right(())` for empty path list.
- `.iw/commands/phase-pr.scala` — Extracted `commitReviewState` helper using for-comprehension to stage + commit review-state.json. Applied in both batch path (commit only, on feature branch) and non-batch path (commit + push, on phase sub-branch). Failures are treated as warnings to avoid blocking PR creation.

**Regression tests added:**
- 4 unit tests for `GitAdapter.stageFiles` (stage specific file, modified tracked file, empty list, non-existent path error)
- 1 E2E BATS test verifying non-batch phase-pr leaves clean working tree with review-state.json committed

**Code review:**
- Iterations: 1 (fixed critical BATS assertion issue, refactored nested match to for-comprehension)
- Review file: review-phase-01-20260321-193943.md

**Files changed:**
```
M  .iw/commands/phase-pr.scala
M  .iw/core/adapters/Git.scala
M  .iw/core/test/GitTest.scala
M  .iw/test/phase-pr.bats
```

---
