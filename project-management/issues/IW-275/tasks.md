# Implementation Tasks: Add `iw batch-implement` command to replace shell script

**Issue:** IW-275
**Created:** 2026-03-19
**Status:** 0/3 phases complete (0%)

## Phase Index

- [x] Phase 1: Move MarkdownTaskParser to model (Est: 1-2h) → `phase-01-context.md`
- [ ] Phase 2: Batch implementation decision logic (Est: 3-5h) → `phase-02-context.md`
- [ ] Phase 3: batch-implement command script (Est: 5-8h) → `phase-03-context.md`

## Progress Tracker

**Completed:** 1/3 phases
**Estimated Total:** 9-15 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phase 1 is a refactoring prerequisite: MarkdownTaskParser is pure logic currently misplaced in dashboard/
- Phase 2 creates pure decision functions (phase outcomes, recovery prompts, workflow resolution)
- Phase 3 is the main command script orchestrating the phase loop using domain functions + existing adapters
