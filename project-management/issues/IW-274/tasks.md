# Implementation Tasks: Add activity and workflow_type to review-state schema and redesign WorktreeSummary

**Issue:** IW-274
**Created:** 2026-03-17
**Status:** 6/6 phases complete (100%)

## Phase Index

- [x] Phase 1: Schema and Validator (Est: 1-2.5h) → `phase-01-context.md`
- [x] Phase 2: Domain Model — ReviewState fields and codec (Est: 1-2h) → `phase-02-context.md`
- [x] Phase 3: Builder and Updater — new field support (Est: 0.5-1.5h) → `phase-03-context.md`
- [x] Phase 4: CLI Commands — write/update flags (Est: 1-2h) → `phase-04-context.md`
- [x] Phase 5: WorktreeSummary redesign, worktrees command, and formatter (Est: 2-4h) → `phase-05-context.md`
- [x] Phase 6: Documentation — llms.txt and schema skill (Est: 0.25-0.75h) → `phase-06-context.md`

## Progress Tracker

**Completed:** 6/6 phases
**Estimated Total:** 5.75-12.75 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phase 1 combines schema and validator since they are tightly coupled
- Phase 5 combines WorktreeSummary redesign, worktrees command, and formatter as one vertical slice
- Skill updates (workflow transition points) are out of scope for this issue
