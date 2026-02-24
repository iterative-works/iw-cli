# Implementation Log: Agent-usable CLI: projects, worktrees, status commands + Claude-in-tmux

Issue: IW-222

This log tracks the evolution of implementation across phases.

---

## Phase 1: Domain Layer — model/ extractions and value objects (2026-02-24)

**Layer:** Domain

**What was built:**
- `.iw/core/model/ProjectPath.scala` — Pure function for deriving main project paths from worktree paths
- `.iw/core/model/ServerStateCodec.scala` — JSON serialization codecs for all server state domain models (16 ReadWriter instances + StateJson wire format)
- `.iw/core/model/ServerLifecycleService.scala` — Server lifecycle pure business logic (moved from dashboard)
- `.iw/core/model/FeedbackParser.scala` — Feedback command argument parser (moved from dashboard)
- `.iw/core/model/ProjectSummary.scala` — Value object for `iw projects` CLI output
- `.iw/core/model/WorktreeSummary.scala` — Value object for `iw worktrees` CLI output
- `.iw/core/model/WorktreeStatus.scala` — Value object for `iw status` CLI output

**Dependencies on other layers:**
- None — this is the domain layer, all code is pure

**Decisions made:**
- Used re-export pattern (`export iw.core.model.X`) in dashboard files for backward compatibility during Phase 1; callers will be updated in Phase 2
- `MainProject.deriveMainProjectPath` delegates to `ProjectPath` rather than being deleted, preserving existing callers
- Value objects use `derives ReadWriter` for automatic JSON codec generation
- `ServerStateCodec` centralizes all codec instances in one object for discoverability

**Testing:**
- Unit tests: 20 new tests (8 ProjectPathTest + 12 ServerStateCodecTest)
- All existing tests pass: MainProjectTest (12), StateRepositoryTest (23), ServerLifecycleServiceTest (17), FeedbackParserTest (18)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260224.md
- Findings: 0 critical, 5 warnings (pre-existing/intentional), 14 suggestions

**Files changed:**
```
A  .iw/core/model/ProjectPath.scala
A  .iw/core/model/ServerStateCodec.scala
A  .iw/core/model/ServerLifecycleService.scala
A  .iw/core/model/FeedbackParser.scala
A  .iw/core/model/ProjectSummary.scala
A  .iw/core/model/WorktreeSummary.scala
A  .iw/core/model/WorktreeStatus.scala
A  .iw/core/test/ProjectPathTest.scala
A  .iw/core/test/ServerStateCodecTest.scala
M  .iw/core/dashboard/FeedbackParser.scala (re-export)
M  .iw/core/dashboard/ServerLifecycleService.scala (re-export)
M  .iw/core/dashboard/StateRepository.scala (imports from ServerStateCodec)
M  .iw/core/dashboard/domain/MainProject.scala (delegates to ProjectPath)
```

---
