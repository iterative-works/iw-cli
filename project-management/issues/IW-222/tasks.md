# Implementation Tasks: Agent-usable CLI: projects, worktrees, status commands + Claude-in-tmux

**Issue:** IW-222
**Created:** 2026-02-24
**Status:** 3/4 phases complete (75%)

## Phase Index

- [x] Phase 01: Domain Layer — model/ extractions and value objects (Est: 3-5h) → `phase-01-context.md`
- [x] Phase 02: Infrastructure Layer — adapter moves, StateReader, TmuxAdapter.sendKeys (Est: 6-9h) → `phase-02-context.md`
- [x] Phase 03: Presentation Layer — new commands (projects, worktrees, status) with --json (Est: 6-9h) → `phase-03-context.md`
- [ ] Phase 04: Presentation Layer — --prompt support for start/open (Est: 2-3h) → `phase-04-context.md`

## Progress Tracker

**Completed:** 3/4 phases
**Estimated Total:** 17-26 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phases follow layer dependency order (domain → infrastructure → presentation)
- Phase 01 must complete before Phase 02 (ServerStateCodec needed by StateReader + StateRepository)
- Phase 02 must complete before Phase 03 (commands import from adapters/)
- Phase 03 and 04 are independent but sequenced to reduce risk
