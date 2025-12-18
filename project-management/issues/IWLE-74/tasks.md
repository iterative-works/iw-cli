# Implementation Tasks: Support project-specific commands alongside shared commands

**Issue:** IWLE-74
**Created:** 2025-12-18
**Status:** 0/3 phases complete (0%)

## Phase Index

- [ ] Phase 1: Project command discovery (Est: 2-3h) → `phase-01-context.md`
- [ ] Phase 2: Execute project command with `./` prefix (Est: 3-4h) → `phase-02-context.md`
- [ ] Phase 3: Describe project command with `./` prefix (Est: 1h) → `phase-03-context.md`

## Progress Tracker

**Completed:** 0/3 phases
**Estimated Total:** 6-8 hours
**Time Spent:** 0 hours

## Phase Summary

| Phase | Story | Key Deliverable |
|-------|-------|-----------------|
| 1 | Discovery | `iw --list` shows both namespaces |
| 2 | Execution | `iw ./cmd` runs project commands, hooks from both dirs for global |
| 3 | Describe | `iw --describe ./cmd` shows project command help |

## Design Decisions (from analysis)

- **Namespacing:** Explicit `./` prefix for project commands, no implicit override
- **Hook discovery:** Project hooks extend global commands; no hooks for project commands
- **Bootstrap:** Shared commands only (unchanged)

## Notes

- Phase context files generated just-in-time during implementation
- Use `/ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- All changes are to `iw-run` shell script
- BATS tests required for each phase
