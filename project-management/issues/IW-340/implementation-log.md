# Implementation Log: Add --commit flag to review-state update command

Issue: IW-340

This log tracks the evolution of implementation across phases.

---

## Phase 1: Add --commit flag to review-state commands (2026-04-13)

**Layer:** Presentation (Command Scripts)

**What was built:**
- `commands/review-state/update.scala` - Added `--commit` boolean flag that stages and commits review-state.json after writing, using `GitAdapter.commitFileWithRetry()`
- `commands/review-state/write.scala` - Same `--commit` flag added to both `handleFlags` and `handleStdin` paths, with shared `commitIfRequested` helper to avoid duplication
- `test/review-state.bats` - 10 new E2E tests covering both commands with `--commit` flag

**Dependencies on other layers:**
- Infrastructure: `GitAdapter.commitFileWithRetry()` (pre-existing, no changes needed)

**Testing:**
- E2E tests: 10 tests added (update: 5, write: 5)
- All 61 review-state tests pass

**Code review:**
- Iterations: 2
- Findings addressed: extracted `commitIfRequested` helper, fixed `Output.error` → `Output.warning` for non-fatal failures, added missing `--from-stdin --commit` test, simplified fragile assertion

**Files changed:**
```
M	commands/review-state/update.scala
M	commands/review-state/write.scala
M	test/review-state.bats
```

---
