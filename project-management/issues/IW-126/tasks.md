# Implementation Tasks: Add llms.txt files for core module documentation

**Issue:** IW-126
**Created:** 2026-01-25
**Status:** 2/3 phases complete (67%)

## Phase Index

- [x] Phase 1: Establish public API boundary (Est: 3-4h) → `phase-01-context.md`
      Story 1: Refactor core modules to separate public API from internal implementation

- [x] Phase 2: Create llms.txt documentation (Est: 6-9h) → `phase-02-context.md`
      Stories 2-3: Create index file and per-module API documentation with examples

- [ ] Phase 3: Integrate with skill (Est: 1-2h) → `phase-03-context.md`
      Story 4: Update iw-command-creation skill to reference llms.txt

## Progress Tracker

**Completed:** 2/3 phases
**Estimated Total:** 10-15 hours
**Time Spent:** 0 hours

## Phase Dependencies

```
Phase 1 ──► Phase 2 ──► Phase 3
   │           │
   │           └── Creates llms.txt that skill will reference
   └── Defines what modules are public (determines documentation scope)
```

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phase 2 is the largest - may be split if needed during implementation
