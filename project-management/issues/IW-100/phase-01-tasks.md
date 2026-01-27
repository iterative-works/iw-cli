# Phase 1 Tasks: Fix totalPhases to use Phase Index as source of truth

**Issue:** IW-100
**Phase:** 1 of 1
**Status:** Complete

## Setup

- [x] [setup] Read current WorkflowProgressService.computeProgress implementation
- [x] [setup] Read existing WorkflowProgressServiceTest to understand test patterns

## Tests (TDD - Write First)

- [x] [test] Add test: Phase Index provides totalPhases (6-entry index, 3 files → totalPhases = 6)
- [x] [test] Add test: Phase Index matches file count (4-entry index, 4 files → totalPhases = 4)
- [x] [test] Add test: Empty Phase Index falls back to file count (empty index, 3 files → totalPhases = 3)
- [x] [test] Run tests to confirm they fail (TDD red phase)

## Implementation

- [x] [impl] Modify computeProgress to use phaseIndex.size when phaseIndex.nonEmpty
- [x] [impl] Run tests to confirm they pass (TDD green phase)
- [x] [impl] Run full test suite to verify no regressions

## Verification

- [x] [verify] All new tests pass
- [x] [verify] All existing tests pass
- [x] [verify] No compilation warnings

## Notes

- Implementation is a single-line change with conditional
- Pattern mirrors determineCurrentPhase which already uses Phase Index
- Keep default parameter unchanged for backward compatibility
