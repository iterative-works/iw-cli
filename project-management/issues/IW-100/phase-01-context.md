# Phase 1 Context: Fix totalPhases to use Phase Index as source of truth

**Issue:** IW-100
**Phase:** 1 of 1
**Stories:** Story 1 (Display correct total) + Story 2 (Graceful fallback)

## Goals

This phase fixes the dashboard's incorrect total phase count by making the Phase Index the source of truth for `totalPhases`, while maintaining backward compatibility for issues without Phase Index sections.

**What we accomplish:**
1. Dashboard displays correct total phase count from Phase Index
2. Issues without Phase Index continue to work (fallback to file count)

## Scope

### In Scope
- Modify `WorkflowProgressService.computeProgress` to use `phaseIndex.size` when available
- Add fallback logic: `if phaseIndex.nonEmpty then phaseIndex.size else phases.size`
- Add unit tests for both behaviors (Phase Index source of truth + fallback)

### Out of Scope
- Changes to Phase Index parsing (already works correctly)
- Changes to dashboard rendering (already uses `totalPhases`)
- Changes to current phase detection (already uses Phase Index)

## Dependencies

### From Previous Phases
None - this is the only phase.

### Prerequisites
- `MarkdownTaskParser.parsePhaseIndex` already exists and works
- `computeProgress` already receives `phaseIndex` parameter
- `determineCurrentPhase` already uses Phase Index (good reference pattern)

## Technical Approach

### The Fix (Single Line Change + Conditional)

Current code in `WorkflowProgressService.computeProgress` (line 176):
```scala
totalPhases = phases.size
```

New code:
```scala
totalPhases = if phaseIndex.nonEmpty then phaseIndex.size else phases.size
```

This is the same pattern used in `determineCurrentPhase` - use Phase Index when available, fall back otherwise.

### Why This Works
- `phaseIndex` is already parsed and passed to `computeProgress`
- The fix preserves backward compatibility automatically
- No structural changes needed - just a conditional

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/dashboard/WorkflowProgressService.scala` | Modify `computeProgress` to use `phaseIndex.size` when non-empty |
| `.iw/core/test/WorkflowProgressServiceTest.scala` | Add tests for Phase Index totalPhases + fallback |

## Testing Strategy

### Unit Tests to Add

1. **Phase Index provides totalPhases:**
   - Input: `phaseIndex` with 6 entries, `phases` with 3 files
   - Expected: `totalPhases = 6`

2. **Phase Index matches file count:**
   - Input: `phaseIndex` with 4 entries, `phases` with 4 files
   - Expected: `totalPhases = 4`

3. **Empty Phase Index falls back:**
   - Input: `phaseIndex` empty, `phases` with 3 files
   - Expected: `totalPhases = 3`

4. **No Phase Index parameter (default):**
   - Input: Call `computeProgress(phases)` without second param
   - Expected: `totalPhases = phases.size`

### Existing Tests Must Pass
All existing `WorkflowProgressServiceTest` tests should continue passing unchanged.

## Acceptance Criteria

- [ ] `computeProgress` uses `phaseIndex.size` when `phaseIndex.nonEmpty`
- [ ] `computeProgress` falls back to `phases.size` when `phaseIndex.isEmpty`
- [ ] Test: 6-phase index with 3 files returns `totalPhases = 6`
- [ ] Test: Empty phase index with 3 files returns `totalPhases = 3`
- [ ] All existing tests pass
- [ ] No changes to other files beyond the two listed

## Implementation Notes

- The fix mirrors how `determineCurrentPhase` handles Phase Index (lines 201-215)
- Keep the default parameter `phaseIndex: List[PhaseIndexEntry] = List.empty` unchanged
- This ensures existing callers continue to work
