# Implementation Tasks: Add deterministic phase lifecycle commands

**Issue:** IW-238
**Created:** 2026-03-07
**Status:** 1/3 phases complete (33%)

## Phase Index

- [x] Phase 1: Domain Layer (Est: 4-6h) → `phase-01-context.md`
- [ ] Phase 2: Infrastructure Layer (Est: 6-9h) → `phase-02-context.md`
- [ ] Phase 3: Presentation Layer (Est: 6-8h) → `phase-03-context.md`

## Progress Tracker

**Completed:** 1/3 phases
**Estimated Total:** 16-23 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phases follow layer dependency order (domain → infrastructure → presentation)
- Phase 1 (Domain): PhaseBranch, CommitMessage, PhaseTaskFile, PhaseOutput — all pure logic
- Phase 2 (Infrastructure): GitAdapter extensions, GitHubClient PR ops, GitLabClient MR ops, FileUrlBuilder, ReviewStateAdapter
- Phase 3 (Presentation): phase-start.scala, phase-commit.scala, phase-pr.scala command scripts
