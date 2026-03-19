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
