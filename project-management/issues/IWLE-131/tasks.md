# Implementation Tasks: E2E tests create real issues in Linear - need mock or sandbox

**Issue:** IWLE-131
**Created:** 2025-12-21
**Status:** 2/3 phases complete (67%)

## Phase Index

- [x] Phase 1: E2E tests skip real API calls by default (Est: 2-3h) → `phase-01-context.md`
- [x] Phase 2: Explicit live API opt-in mechanism (Est: 1-2h) → `phase-02-context.md`
- [ ] Phase 3: Mock-based unit tests with sttp backend injection (Est: 3-4h) → `phase-03-context.md`

## Progress Tracker

**Completed:** 2/3 phases
**Estimated Total:** 6-9 hours
**Time Spent:** 0 hours

## Phase Summary

| Phase | Description | Key Changes |
|-------|-------------|-------------|
| 1 | Skip real API by default | Add ENABLE_LIVE_API_TESTS gate to feedback.bats |
| 2 | Live API opt-in | Warning messages, documentation updates |
| 3 | Mock-based tests | Refactor LinearClient for backend injection, add unit tests |

## Dependencies

- Phase 2 depends on Phase 1 (builds on skip mechanism)
- Phase 3 is independent (can run in parallel with 1-2)

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- No production code changes in Phases 1-2 (test files only)
- Phase 3 requires minor refactoring of LinearClient to accept backend parameter
