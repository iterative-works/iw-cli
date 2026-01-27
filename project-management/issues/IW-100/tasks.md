# Implementation Tasks: Dashboard shows incorrect total phase count

**Issue:** IW-100
**Created:** 2026-01-26
**Status:** 0/1 phases complete (0%)

## Phase Index

- [ ] Phase 1: Fix totalPhases to use Phase Index as source of truth (Est: 3-4h) → `phase-01-context.md`

## Progress Tracker

**Completed:** 0/1 phases
**Estimated Total:** 3-4 hours
**Time Spent:** 0 hours

## Notes

- Both stories (Phase Index source of truth + graceful fallback) combined into single phase
- Stories are tightly coupled and should be implemented together for backward compatibility
- Phase context file generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start implementation automatically

### Story Mapping

| Phase | Stories | Focus |
|-------|---------|-------|
| 1 | Story 1 + Story 2 | Use phaseIndex.size for totalPhases with fallback to phases.size |

### Key Implementation Points

1. Modify `WorkflowProgressService.computeProgress` to use Phase Index count
2. Add fallback: `if phaseIndex.nonEmpty then phaseIndex.size else phases.size`
3. Add unit tests for both behaviors
4. All existing tests must continue passing
