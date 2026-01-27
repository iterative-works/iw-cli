# Phase 1 Tasks: Fix totalPhases to use Phase Index as source of truth

**Issue:** IW-100
**Phase:** 1 of 1
**Status:** Ready for Implementation

## Setup

- [ ] [setup] Read current WorkflowProgressService.computeProgress implementation
- [ ] [setup] Read existing WorkflowProgressServiceTest to understand test patterns

## Tests (TDD - Write First)

- [ ] [test] Add test: Phase Index provides totalPhases (6-entry index, 3 files → totalPhases = 6)
- [ ] [test] Add test: Phase Index matches file count (4-entry index, 4 files → totalPhases = 4)
- [ ] [test] Add test: Empty Phase Index falls back to file count (empty index, 3 files → totalPhases = 3)
- [ ] [test] Run tests to confirm they fail (TDD red phase)

## Implementation

- [ ] [impl] Modify computeProgress to use phaseIndex.size when phaseIndex.nonEmpty
- [ ] [impl] Run tests to confirm they pass (TDD green phase)
- [ ] [impl] Run full test suite to verify no regressions

## Verification

- [ ] [verify] All new tests pass
- [ ] [verify] All existing tests pass
- [ ] [verify] No compilation warnings

## Notes

- Implementation is a single-line change with conditional
- Pattern mirrors determineCurrentPhase which already uses Phase Index
- Keep default parameter unchanged for backward compatibility
