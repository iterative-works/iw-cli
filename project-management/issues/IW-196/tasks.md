# Implementation Tasks: Extend iw doctor to check project quality gates

**Issue:** IW-196
**Created:** 2026-02-09
**Status:** 8/8 phases complete (100%)

## Phase Index

- [x] Phase 0: Move Check types from dashboard/ to model/ (Est: 2-3h) → `phase-00-context.md`
- [x] Phase 1: Scalafmt configuration check (Est: 4-6h) → `phase-01-context.md`
- [x] Phase 2: Scalafix configuration check (Est: 4-6h) → `phase-02-context.md`
- [x] Phase 3: Git hooks check (Est: 6-8h) → `phase-03-context.md`
- [x] Phase 4: Contributor documentation check (Est: 4-6h) → `phase-04-context.md`
- [x] Phase 5: CI workflow check (Est: 3-4h) → `phase-05-context.md`
- [x] Phase 6: Check grouping and filtering (Est: 6-8h) → `phase-06-context.md`
- [x] Phase 7: Fix remediation via Claude Code (Est: 8-12h) → `phase-07-context.md`

## Progress Tracker

**Completed:** 8/8 phases
**Estimated Total:** 37-53 hours
**Time Spent:** 0 hours

## Notes

- Phase 0 is a pre-requisite refactoring: move `Check`, `CheckResult`, `DoctorChecks` from `core/dashboard/` to `core/model/` to fix architectural violation
- Phase 1 establishes the quality gate check pattern used by all subsequent phases
- Phases 1-5 are independent of each other (after Phase 0), but recommended in listed order
- Phase 6 depends on at least one check phase (1-5)
- Phase 7 depends on Phases 1-5
- Phase context files generated just-in-time during implementation
- Use ag-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
