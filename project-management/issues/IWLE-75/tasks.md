# Implementation Tasks: iw start fails when running inside tmux

**Issue:** IWLE-75
**Created:** 2025-12-18
**Status:** 1/2 phases complete (50%)

## Phase Index

- [x] Phase 1: Core switch functionality for start.scala (Est: 3-4h) → `phase-01-context.md`
- [ ] Phase 2: Apply switch pattern to open.scala (Est: 2-3h) → `phase-02-context.md`

## Phase Summary

**Phase 1 - Core switch functionality for start.scala:**
- Add `TmuxAdapter.switchSession(name: String)` method
- Update start.scala to detect tmux and use switch instead of attach
- Error handling with fallback manual command message
- Unit tests for switchSession and conditional logic

**Phase 2 - Apply switch pattern to open.scala:**
- Replace current "please detach" error with automatic switch
- Apply same error handling pattern from Phase 1
- Unit tests for open.scala tmux behavior

## Progress Tracker

**Completed:** 1/2 phases
**Estimated Total:** 5-7 hours
**Time Spent:** 0 hours

## Resolved Decisions

- **Orphaned sessions:** Leave session running on switch failure, show manual command
- **Scope:** Update both start.scala AND open.scala for consistent UX
- **Testing:** Mock TMUX env var (simpler, faster test setup)

## Notes

- Phase context files generated just-in-time during implementation
- Use `/ag-implement` to start next phase automatically
- Phase 1 delivers immediate value (fixes the reported bug)
- Phase 2 extends fix for UX consistency across commands
- TmuxAdapter.isInsideTmux already exists - just need switchSession method
