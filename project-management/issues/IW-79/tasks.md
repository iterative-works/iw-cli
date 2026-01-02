# Implementation Tasks: Spawn worktrees from dashboard

**Issue:** IW-79
**Created:** 2026-01-02
**Status:** 0/7 phases complete (0%)

## Phase Index

- [ ] Phase 1: Display main repository with available issues (Est: 6-8h) → `phase-01-context.md`
- [ ] Phase 2: Spawn worktree via dashboard button click (Est: 8-12h) → `phase-02-context.md`
- [ ] Phase 3: Handle worktree creation errors gracefully (Est: 3-4h) → `phase-03-context.md`
- [ ] Phase 4: Open existing worktree from dashboard (Est: 3-4h) → `phase-04-context.md`
- [ ] Phase 5: Filter and search available issues (Est: 4-6h) → `phase-05-context.md`
- [ ] Phase 6: Auto-refresh issue list when worktrees change (Est: 6-8h) → `phase-06-context.md`
- [ ] Phase 7: Handle concurrent worktree creation attempts (Est: 4-5h) → `phase-07-context.md`

## Progress Tracker

**Completed:** 0/7 phases
**Estimated Total:** 34-47 hours
**Time Spent:** 0 hours

## Technical Decisions

Key decisions that affect implementation:

1. **Tmux:** Create session but don't attach (server runs `tmux new-session -d`)
2. **Issue scope:** Fetch recent 50 open issues by default
3. **Architecture:** Hybrid with HTMX (ScalaTags + HTMX attributes)
4. **Async:** Synchronous with 30s timeout
5. **Auth:** Use environment variables (existing approach)

## Phase Dependencies

```
Phase 1 ──┬──► Phase 2 ──► Phase 3
          │         │
          │         └──► Phase 7
          │
          └──► Phase 4
          │
          └──► Phase 5
          │
          └──► Phase 6
```

- Phases 3, 7 depend on Phase 2
- Phases 4, 5, 6 can run after Phase 1 (independent)

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- HTMX simplifies Stories 5 (auto-refresh) and 7 (concurrent protection)
