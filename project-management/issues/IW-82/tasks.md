# Implementation Tasks: Development mode for dashboard testing

**Issue:** IW-82
**Created:** 2026-01-19
**Status:** 3/4 phases complete (75%) - Phase 3 skipped

## Phase Index

- [x] Phase 1: Run server with custom state file (Est: 4-6h) → `phase-01-context.md`
- [x] Phase 2: Load sample data for UI testing (Est: 6-8h) → `phase-02-context.md`
- [~] Phase 3: Run server with custom project directory - **SKIPPED** (see implementation-log.md)
- [x] Phase 4: Combined development mode flag (Est: 3-4h) → `phase-04-context.md`
- [ ] Phase 5: Validate development mode isolation (Est: 2-3h) → `phase-05-context.md`

## Progress Tracker

**Completed:** 3/4 phases (Phase 3 skipped)
**Estimated Total:** 15-21 hours (excluding skipped phase)
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phase 3 was skipped after investigation revealed the dashboard is global (serves all projects)
  and the proposed `--project` flag wouldn't have accomplished anything useful
- Remaining phases (4, 5) are independent of Phase 3:
  1. Phase 4 combines `--state-path` + `--sample-data` into `--dev`
  2. Phase 5 validates isolation (state file, not project config)
