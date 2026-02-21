# Implementation Log: Dashboard - Restructure root page as projects overview

Issue: IW-205

This log tracks the evolution of implementation across phases.

---

## Phase 1: Project cards show worktree count and summary status (2026-02-21)

**What was built:**
- View model: `.iw/core/dashboard/presentation/views/ProjectSummary.scala` - Case class and pure `computeSummaries` function pairing MainProject with worktree count and attention count
- View update: `.iw/core/dashboard/presentation/views/MainProjectsView.scala` - Updated render signature to accept `List[ProjectSummary]`, added worktree count badge and attention indicator to cards
- Service update: `.iw/core/dashboard/DashboardService.scala` - Computes project summaries and passes them to MainProjectsView

**Decisions made:**
- Placed ProjectSummary in presentation/views package as a view model (per analysis recommendation)
- "Needs attention" uses only `ReviewState.needsAttention == Some(true)` — no heuristic signals
- Singular/plural handling for worktree count text ("1 worktree" vs "N worktrees")
- Attention text uses simple format ("N needs attention") without singular/plural distinction

**Patterns applied:**
- View Model pattern: ProjectSummary bundles domain data with computed display values
- Pure function: computeSummaries has no side effects, takes all inputs explicitly
- FCIS: Computation is pure, rendering is separate

**Testing:**
- Unit tests: 8 tests for ProjectSummary (empty, single, multiple, attention states, orphaned worktrees, zero-match projects)
- Unit tests: 17 tests for MainProjectsView (existing updated + 5 new for counts and attention)
- Integration tests: 1 test for DashboardService summary rendering

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260221.md
- Findings: 3 test coverage gaps (fixed), duplicate import (fixed), architecture layer suggestions (noted for Phase 3)

**For next phases:**
- `ProjectSummary.computeSummaries` is reusable if other views need project statistics
- Phase 2 can remove WorktreeListView.render call from DashboardService
- Phase 3 can simplify DashboardService signature and potentially move computeSummaries to application layer

**Files changed:**
```
A  .iw/core/dashboard/presentation/views/ProjectSummary.scala
M  .iw/core/dashboard/presentation/views/MainProjectsView.scala
M  .iw/core/dashboard/DashboardService.scala
A  .iw/core/test/ProjectSummaryTest.scala
M  .iw/core/test/MainProjectsViewTest.scala
M  .iw/core/test/DashboardServiceTest.scala
```

---
