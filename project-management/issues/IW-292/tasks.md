# Investigation Tasks: phase-pr leaves review-state.json uncommitted

**Issue:** IW-292
**Created:** 2026-03-21
**Severity:** High
**Status:** 1/3 phases complete (33%)

## Phase Index

- [x] Phase 1: phase-pr does not commit review-state.json update (Est: 2-4h) → `phase-01-context.md`
- [ ] Phase 2: phase-merge does not commit review-state.json updates (Est: 1-2h) → `phase-02-context.md`
- [ ] Phase 3: phase-advance does not commit review-state.json update (Est: 1-2h) → `phase-03-context.md`

## Progress Tracker

**Completed:** 1/3 phases
**Estimated Total:** 4-8 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during investigation
- Use dx-fix to start next phase automatically
- Estimates are rough and will be refined during investigation
- Each phase follows: reproduce → investigate → fix → verify
- Phase 1 includes shared work: adding `stageFiles` to `GitAdapter`, reused by Phases 2 and 3
- Non-batch phase-pr path requires a follow-up push after committing (resolved decision)
- Intermediate review-state updates in phase-merge (ci_pending, ci_fixing) are transient on the phase sub-branch; only the final "phase_merged" update needs committing
