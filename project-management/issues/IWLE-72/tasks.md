# Implementation Tasks: Create iw-cli - Project-local worktree and issue management tool

**Issue:** IWLE-72
**Created:** 2025-12-10
**Status:** 2/7 phases complete (29%)

## Phase Index

- [x] Phase 1: Bootstrap script runs tool via scala-cli (Est: 2-4h) → `phase-01-context.md`
- [x] Phase 2: Initialize project with issue tracker configuration (Est: 4-6h) → `phase-02-context.md`
- [ ] Phase 3: Validate environment and configuration (Est: 4-6h) → `phase-03-context.md`
- [ ] Phase 4: Create worktree for issue with tmux session (Est: 6-8h) → `phase-04-context.md`
- [ ] Phase 5: Open existing worktree tmux session (Est: 4-6h) → `phase-05-context.md`
- [ ] Phase 6: Remove worktree and cleanup resources (Est: 4-6h) → `phase-06-context.md`
- [ ] Phase 7: Fetch and display issue details (Est: 8-10h) → `phase-07-context.md`

## Progress Tracker

**Completed:** 2/7 phases
**Estimated Total:** 32-46 hours
**Time Spent:** 0 hours

## Notes

- Phase context files generated just-in-time during implementation
- Use `/ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- Implementation sequence follows analysis.md recommendations:
  1. Foundation first (bootstrap, config, validation)
  2. Core workflow (create, open, remove worktrees)
  3. Issue integration last (requires both Linear + YouTrack adapters)
