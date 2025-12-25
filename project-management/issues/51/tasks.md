# Implementation Tasks: Improve branch naming convention for GitHub issues

**Issue:** #51
**Created:** 2025-12-26
**Status:** 0/3 phases complete (0%)

## Phase Index

- [ ] Phase 1: Configure team prefix for GitHub projects (Est: 2-3h) → `phase-01-context.md`
- [ ] Phase 2: Parse and display GitHub issues with team prefix (Est: 2-3h) → `phase-02-context.md`
- [ ] Phase 3: Remove numeric-only branch handling (Est: 1-2h) → `phase-03-context.md`

## Progress Tracker

**Completed:** 0/3 phases
**Estimated Total:** 5-8 hours
**Time Spent:** 0 hours

## Resolved Decisions

From CLARIFY markers in analysis:

1. **Team prefix validation:** Uppercase letters only, 2-10 characters
2. **Migration approach:** Hard cutoff - reject bare numeric branches with clear error messages

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phases 2 and 3 both modify `IssueId.scala` - could potentially be combined
