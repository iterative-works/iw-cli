# Review Packet: Phase 1 - Fix totalPhases to use Phase Index as source of truth

**Issue:** IW-100
**Phase:** 1 of 1
**Branch:** IW-100-phase-01
**Baseline:** a1eae2c

## Goals

This phase fixes the dashboard's incorrect total phase count by making the Phase Index the source of truth for `totalPhases`, while maintaining backward compatibility for issues without Phase Index sections.

**What was accomplished:**
1. Dashboard now displays correct total phase count from Phase Index
2. Issues without Phase Index continue to work (fallback to file count)

## Scenarios

Based on acceptance criteria from phase-01-context.md:

- [x] `computeProgress` uses `phaseIndex.size` when `phaseIndex.nonEmpty`
- [x] `computeProgress` falls back to `phases.size` when `phaseIndex.isEmpty`
- [x] Test: 6-phase index with 3 files returns `totalPhases = 6`
- [x] Test: Empty phase index with 3 files returns `totalPhases = 3`
- [x] All existing tests pass
- [x] No changes to other files beyond the two listed

## Entry Points

Start reviewing from:

1. **Production code change** (1 line):
   - `.iw/core/dashboard/WorkflowProgressService.scala:176` - The fix

2. **Test additions** (3 new tests):
   - `.iw/core/test/WorkflowProgressServiceTest.scala:183-230` - New tests

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Dashboard Card                          │
│                   "Phase 3/6: Login"                        │
└─────────────────────┬───────────────────────────────────────┘
                      │ displays
                      ▼
┌─────────────────────────────────────────────────────────────┐
│               WorkflowProgress (model)                      │
│  { currentPhase, totalPhases, phases[], ... }              │
└─────────────────────┬───────────────────────────────────────┘
                      │ computed by
                      ▼
┌─────────────────────────────────────────────────────────────┐
│         WorkflowProgressService.computeProgress()          │
│                                                             │
│  [BEFORE]  totalPhases = phases.size                       │
│  [AFTER]   totalPhases = if phaseIndex.nonEmpty            │
│                            then phaseIndex.size            │
│                            else phases.size                │
└─────────────────────┬───────────────────────────────────────┘
                      │ uses
          ┌───────────┴───────────┐
          ▼                       ▼
┌─────────────────┐   ┌─────────────────────────────┐
│  phases: List   │   │  phaseIndex: List           │
│  (from files)   │   │  (from tasks.md Phase Index)│
│  - phase-01.md  │   │  - [ ] Phase 1: Setup       │
│  - phase-02.md  │   │  - [ ] Phase 2: Impl        │
│  - phase-03.md  │   │  - [ ] Phase 3: Test        │
│  (3 files)      │   │  - [ ] Phase 4: Deploy      │
│                 │   │  - [ ] Phase 5: Docs        │
│                 │   │  - [ ] Phase 6: Review      │
└─────────────────┘   └─────────────────────────────┘
```

## Flow Diagram

```
fetchProgress()
      │
      ▼
  Read tasks.md
      │
      ▼
  parsePhaseIndex() ──────────────────────────────────┐
      │                                               │
      ▼                                               │
  Discover phase-NN-tasks.md files                    │
      │                                               │
      ▼                                               │
  parsePhaseFiles() → List[PhaseInfo]                 │
      │                                               │
      ▼                                               │
  computeProgress(phases, phaseIndex) ◄───────────────┘
      │
      ├── phaseIndex.nonEmpty?
      │         │
      │    ┌────┴────┐
      │    YES       NO
      │    │         │
      │    ▼         ▼
      │  phaseIndex  phases
      │  .size       .size
      │    │         │
      │    └────┬────┘
      │         ▼
      │   totalPhases
      │
      ▼
  WorkflowProgress(currentPhase, totalPhases, ...)
```

## Test Summary

**New tests added:** 3

| Test | Description | Verifies |
|------|-------------|----------|
| `computeProgress uses Phase Index size when Phase Index is non-empty (6-entry index, 3 files)` | 6 phases in index, 3 phase files → totalPhases = 6 | Phase Index is source of truth |
| `computeProgress uses Phase Index size when it matches file count (4-entry index, 4 files)` | 4 phases in index, 4 phase files → totalPhases = 4 | Consistency when counts match |
| `computeProgress falls back to file count when Phase Index is empty (empty index, 3 files)` | Empty index, 3 phase files → totalPhases = 3 | Backward compatibility |

**Existing tests:** 12 tests continue to pass (no regressions)

**Total tests:** 15 in WorkflowProgressServiceTest

## Files Changed

| File | Change Type | Lines | Description |
|------|-------------|-------|-------------|
| `.iw/core/dashboard/WorkflowProgressService.scala` | Modified | +1/-1 | Use Phase Index size when available |
| `.iw/core/test/WorkflowProgressServiceTest.scala` | Modified | +49/-0 | Add 3 tests for new behavior |

## Code Diff

**Production change (single line):**

```diff
-      totalPhases = phases.size,
+      totalPhases = if phaseIndex.nonEmpty then phaseIndex.size else phases.size,
```

This mirrors the pattern already used in `determineCurrentPhase` (line 200-215) which also uses Phase Index as source of truth with a fallback.

## Review Checklist

- [ ] Fix is minimal and focused (single conditional)
- [ ] Pattern matches existing code (see `determineCurrentPhase`)
- [ ] Backward compatibility preserved (empty phaseIndex → old behavior)
- [ ] Tests cover both behaviors (Phase Index + fallback)
- [ ] No regressions in existing tests
