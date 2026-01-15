# Implementation Tasks: Dashboard loads too slowly with multiple worktrees

**Issue:** IW-92
**Created:** 2026-01-14
**Status:** 6/6 phases complete (100%)

## Phase Index

- [x] Phase 1: Fast initial dashboard load with cached data (Est: 6-8h) → `phase-01-context.md`
- [x] Phase 2: Aggressive caching for instant subsequent loads (Est: 4-6h) → `phase-02-context.md`
- [x] Phase 3: Background refresh of issue data (Est: 8-12h) → `phase-03-context.md`
- [x] Phase 4: Incremental card updates via HTMX (Est: 6-8h) → `phase-04-context.md`
- [x] Phase 5: Visible-items-first optimization (Est: 4-6h, stretch) → `phase-05-context.md`
- [x] Phase 6: State management refactoring (Est: 4-6h, critical fix) → `phase-06-context.md`

## Progress Tracker

**Completed:** 6/6 phases
**Estimated Total:** 32-46 hours
**Time Spent:** 0 hours

## Story-to-Phase Mapping

| Phase | Story | Description |
|-------|-------|-------------|
| 1 | Story 1 | Render dashboard instantly with cached/skeleton cards |
| 2 | Story 4 | Cache always available, instant subsequent loads |
| 3 | Story 2 | HTMX polling per card, background data refresh |
| 4 | Story 3 | Smooth per-card updates, timestamps, CSS transitions |
| 5 | Story 5 | Priority-based refresh (most recent first) - stretch goal |
| 6 | Refactor | Fix race condition, centralize state management |

## Key Decisions

- **Async strategy:** HTMX polling per card (progressive updates)
- **Cache miss:** Skeleton cards (always instant render)
- **TTL handling:** Always render cache, always refresh (30s throttle)
- **Error handling:** Silent failure with aging timestamps
- **Tab visibility:** Refresh on tab focus via `visibilitychange`

## MVP vs Full Feature

**MVP (Phases 1-3):** 18-26h - Solves the core problem
- Dashboard loads instantly
- Background refresh keeps data fresh
- Cards update progressively

**Full Feature (Phases 1-4):** 24-34h - Polished UX
- Adds smooth per-card updates
- Professional transitions
- Mobile-friendly

**Stretch (All Phases):** 28-40h - Optimized
- Priority-based refresh for many worktrees

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phases 1+2 can be deployed together for immediate UX improvement
- Phase 5 is optional - skip if time-constrained
