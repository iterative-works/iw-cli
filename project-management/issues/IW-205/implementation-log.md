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

## Phase 2: Remove flat worktree list from root page (2026-02-22)

**What was built:**
- Removal: `.iw/core/dashboard/DashboardService.scala` - Removed `worktreesWithData` per-worktree data fetching and `WorktreeListView.render` call from root page
- Root page now renders: header + project summary cards + modal container (no worktree cards, no HTMX polling)

**Decisions made:**
- Function signature kept unchanged (unused params `issueCache`, `progressCache`, `prCache` stay for Phase 3)
- `val now = Instant.now()` kept even though unused (Phase 3 cleanup)
- Helper methods (`fetchIssueForWorktreeCachedOnly`, etc.) kept — still used by per-card refresh paths
- `WorktreeListView` object kept in codebase — still used by `WorktreeCardRenderer`

**Patterns applied:**
- Incremental removal: Phase 2 removes usage, Phase 3 removes unused parameters
- Negative assertion testing: New tests verify absence of worktree list content

**Testing:**
- Unit tests: 3 new negative assertion tests (no worktree-list, no worktree-card, no polling)
- Unit tests: 9 existing tests updated (removed issue-ID assertions that depended on worktree cards)
- Unit tests: 4 tests removed (worktree sorting and Zed button tests — no longer root page concerns)

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20260222.md
- Findings: Added positive assertions to new tests (reviewer feedback), no critical issues

**For next phases:**
- Phase 3 should remove unused `issueCache`, `progressCache`, `prCache` parameters from `renderDashboard`
- Phase 3 should remove unused `val now = Instant.now()`
- Phase 3 should update Scaladoc to reflect simplified signature

**Files changed:**
```
M  .iw/core/dashboard/DashboardService.scala
M  .iw/core/test/DashboardServiceTest.scala
```

---
