# Investigation Tasks: review-state update should support --help flag

**Issue:** IW-202
**Created:** 2026-03-14
**Severity:** Medium
**Status:** 2/4 phases complete (50%)

## Phase Index

- [x] Phase 1: `update --help` mutates state instead of showing help (Est: 1-2h) → `phase-01-context.md`
- [x] Phase 2: `write --help` fails with misleading error (Est: 1-2h) → `phase-02-context.md`
- [ ] Phase 3: `validate --help` fails with misleading error (Est: 0.5-1h) → `phase-03-context.md`
- [ ] Phase 4: `review-state --help` reports "Unknown subcommand" (Est: 0.5-1h) → `phase-04-context.md`

## Progress Tracker

**Completed:** 2/4 phases
**Estimated Total:** 3-6 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during investigation
- Use dx-fix to start next phase automatically
- Estimates are rough and will be refined during investigation
- Each phase follows: reproduce → investigate → fix → verify
- Help text format: simple, matching header comments in each file
- All 4 defects scoped to IW-202 per decision
