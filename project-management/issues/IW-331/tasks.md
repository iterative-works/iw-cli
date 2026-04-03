# Implementation Tasks: Add action hook points to start, doctor, and phase-merge

**Issue:** IW-331
**Created:** 2026-04-03
**Status:** 0/4 phases complete (0%)

## Phase Index

- [ ] Phase 1: Domain model — hook traits and context types (Est: 0.5-1h) → `phase-01-context.md`
- [ ] Phase 2: HookDiscovery adapter — generic reflection-based hook collection (Est: 0.5-1h) → `phase-02-context.md`
- [ ] Phase 3: Command modifications — start, open, doctor, phase-merge (Est: 1-1.5h) → `phase-03-context.md`
- [ ] Phase 4: Regression and E2E tests (Est: 0.5-1h) → `phase-04-context.md`

## Progress Tracker

**Completed:** 0/4 phases
**Estimated Total:** 2.5-4.5 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phase 1 must complete before Phases 2-3 (traits needed by adapters and commands)
- Phase 2 must complete before Phase 3 (commands use HookDiscovery)
- Phase 3 includes presentation layer changes (warning messages are part of command logic)
- Phase 4 verifies existing doctor hooks still work and commands degrade gracefully
