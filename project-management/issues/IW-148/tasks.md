# Implementation Tasks: Track main project worktrees independently from issue worktrees

**Issue:** IW-148
**Created:** 2026-03-02
**Status:** 0/5 phases complete (0%)

## Phase Index

- [ ] Phase 1: Domain — ProjectRegistration model and ServerState extension (Est: 2-3h) → `phase-01-context.md`
- [ ] Phase 2: Application — Registration service, merge logic, state service extension (Est: 3-4h) → `phase-02-context.md`
- [ ] Phase 3: Infrastructure — Server endpoint, client method, codec/persistence (Est: 3-4h) → `phase-03-context.md`
- [ ] Phase 4: Presentation — Dashboard and project details for zero-worktree projects (Est: 2-3h) → `phase-04-context.md`
- [ ] Phase 5: CLI Integration — Context-aware register, auto-register on start (Est: 1-2h) → `phase-05-context.md`

## Progress Tracker

**Completed:** 0/5 phases
**Estimated Total:** 11-16 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use ag-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Follows existing WorktreeRegistration pattern throughout — no new architectural patterns
- All CLARIFY items resolved: path as key, context-aware register, auto-prune
