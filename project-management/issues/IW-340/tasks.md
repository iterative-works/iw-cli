# Implementation Tasks: Add --commit flag to review-state update command

**Issue:** IW-340
**Created:** 2026-04-13
**Status:** 0/1 phases complete (0%)

## Phase Index

- [x] Phase 1: Add --commit flag to review-state commands (Est: 2.5-5h) → `phase-01-context.md`

## Progress Tracker

**Completed:** 1/1 phases
**Estimated Total:** 2.5-5 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- All changes are in the presentation layer (command scripts) — no core changes needed
- Existing `GitAdapter.commitFileWithRetry()` provides the commit infrastructure
- Single phase covers: update.scala, write.scala, and E2E tests (TDD)
