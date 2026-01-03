# Implementation Tasks: Spawn worktrees from dashboard

**Issue:** IW-79
**Created:** 2026-01-02
**Status:** 2/4 phases complete (50%)

## Phase Index

- [x] Phase 1: Modal UI + Issue Search (Est: 4-5h) → `phase-01-context.md`
- [x] Phase 2: Worktree Creation from Modal (Est: 4-5h) → `phase-02-context.md`
- [ ] Phase 3: Error Handling (Est: 2-3h) → `phase-03-context.md`
- [ ] Phase 4: Concurrent Creation Protection (Est: 2-3h) → `phase-04-context.md`

## Progress Tracker

**Completed:** 2/4 phases
**Estimated Total:** 12-16 hours
**Time Spent:** 0 hours

## Technical Decisions

Key decisions that affect implementation:

1. **Tmux:** Create session but don't attach (server runs `tmux new-session -d`)
2. **Issue search:** On-demand modal search (not upfront listing)
3. **Architecture:** Hybrid with HTMX (ScalaTags + HTMX attributes)
4. **Async:** Synchronous with 30s timeout
5. **Auth:** Use environment variables (existing approach)

## Phase Dependencies

```
Phase 1 ──► Phase 2 ──► Phase 3
                  │
                  └──► Phase 4
```

- Phase 2 depends on Phase 1 (need modal before creation)
- Phases 3, 4 depend on Phase 2 (extend creation functionality)
- Phases 3, 4 can run in parallel after Phase 2

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- HTMX simplifies UI interactions significantly
