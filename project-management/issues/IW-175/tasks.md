# Implementation Tasks: Dashboard cards jump around during refresh causing misclicks

**Issue:** IW-175
**Created:** 2026-01-28
**Status:** 2/3 phases complete (67%)

## Phase Index

- [x] Phase 1: Stable card positions during auto-refresh (Est: 4-6h) → `phase-01-context.md`
- [x] Phase 2: New worktrees appear at predictable location (Est: 2-3h) → `phase-02-context.md`
- [ ] Phase 3: Removed worktrees shift remaining cards predictably (Est: 1-2h) → `phase-03-context.md`

## Progress Tracker

**Completed:** 2/3 phases
**Estimated Total:** 7-11 hours
**Time Spent:** 0 hours

## Phase Summary

| Phase | Story | Key Changes | Dependencies |
|-------|-------|-------------|--------------|
| 1 | Stable card positions | Replace `WorktreePriority` sort with Issue ID sort in `DashboardService` | None |
| 2 | Predictable insertion | Verify HTMX OOB inserts new cards at correct sorted position | Phase 1 |
| 3 | Predictable removal | Verify auto-pruning maintains stable sort order | Phase 1 |

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- Story 4 (user-selectable sort) deferred to future issue
