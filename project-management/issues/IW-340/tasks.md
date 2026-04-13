# Implementation Tasks: Add --commit flag to review-state update command

**Issue:** IW-340
**Created:** 2026-04-13
**Status:** 0/3 phases complete (0%)

## Phase Index

- [ ] Phase 1: update.scala --commit flag (Est: 1-2h) → `phase-01-context.md`
- [ ] Phase 2: write.scala --commit flag (Est: 0.5-1h) → `phase-02-context.md`
- [ ] Phase 3: E2E tests (Est: 1-2h) → `phase-03-context.md`

## Progress Tracker

**Completed:** 0/3 phases
**Estimated Total:** 2.5-5 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- All changes are in the presentation layer (command scripts) — no core changes needed
- Existing `GitAdapter.commitFileWithRetry()` provides the commit infrastructure
