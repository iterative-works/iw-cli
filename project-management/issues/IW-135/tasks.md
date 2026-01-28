# Implementation Tasks: Add iw config command for querying project configuration

**Issue:** IW-135
**Created:** 2026-01-28
**Status:** 1/3 phases complete (33%)

## Phase Index

- [x] Phase 1: Query specific configuration value by field name (Est: 3-4h) → `phase-01-context.md`
- [ ] Phase 2: Export full configuration as JSON (Est: 2-3h) → `phase-02-context.md`
- [ ] Phase 3: Validate command usage and provide help (Est: 1-2h) → `phase-03-context.md`

## Progress Tracker

**Completed:** 1/3 phases
**Estimated Total:** 6-9 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- All phases share single command file: `.iw/commands/config.scala`
- Phase 1 establishes upickle `ReadWriter` derivation used by Phase 2
- Phase 3 can be implemented in parallel with Phase 1 or 2 if desired
