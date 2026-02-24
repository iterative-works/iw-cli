# Code Review Results

**Review Context:** Phase 1: Project cards show worktree count and summary status for issue IW-205 (Iteration 1/3)
**Files Reviewed:** 6
**Skills Applied:** code-review-architecture, code-review-style, code-review-testing, code-review-composition
**Timestamp:** 2026-02-21
**Git Context:** git diff 1f5013c

---

## Architecture Review

### Critical Issues
None.

### Warnings

#### View model computation in presentation layer
**Location:** `.iw/core/dashboard/presentation/views/ProjectSummary.scala`
**Note:** `computeSummaries` contains business logic (grouping worktrees, counting attention). The analysis explicitly placed this in the presentation layer as a view model. Consider moving to application layer in Phase 3 simplification.

#### Mixed concerns in DashboardService
**Location:** `.iw/core/dashboard/DashboardService.scala:50-55`
**Note:** DashboardService performs both orchestration and rendering. Acceptable for Phase 1; Phase 3 will simplify this.

### Suggestions
- Extract "needs attention" check to a domain predicate for reusability.
- Add tests for orphaned worktrees and projects with zero worktrees.

---

## Style Review

### Critical Issues
None.

### Warnings

#### Duplicate wildcard import
**Location:** `.iw/core/test/MainProjectsViewTest.scala:8`
**Problem:** `import iw.core.dashboard.domain.*` duplicates the specific import on line 6.
**Resolution:** Remove redundant wildcard import.

### Suggestions
- Consider singular/plural handling for attention count text ("1 needs attention" vs "2 need attention").

---

## Testing Review

### Critical Issues

#### Missing edge case: orphaned worktrees
**Location:** `ProjectSummaryTest.scala`
**Problem:** No test for worktrees that don't match any project (deriveMainProjectPath returns None).
**Resolution:** Add test verifying orphaned worktrees don't affect counts.

#### Missing singular worktree text test
**Location:** `MainProjectsViewTest.scala`
**Problem:** Tests verify "3 worktrees" and "0 worktrees" but not "1 worktree" (singular branch untested).
**Resolution:** Add test for worktreeCount=1.

#### Weak integration test
**Location:** `DashboardServiceTest.scala:647-669`
**Problem:** Test only checks for CSS class existence, not actual data flow.
**Note:** Environmental constraint (no config files) limits what can be tested here. Unit tests cover the components well.

### Warnings
- Missing test for project with no matching worktrees (should show 0 count).
- No test for CSS class names (`worktree-count`, `attention-count`).

### Suggestions
- Consider extracting common test constants in MainProjectsViewTest.

---

## Composition Review

### Critical Issues
None.

### Warnings
None.

### Suggestions
- Consider extracting grouping logic if reused elsewhere.
- Consider named predicate for attention check if complexity grows.

---

## Summary

- **Critical Issues:** 3 (all test coverage gaps)
- **Warnings:** 3
- **Suggestions:** ~8

**Verdict:** Fix critical test gaps and the duplicate import, then proceed.
