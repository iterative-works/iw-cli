# Phase 3 Tasks: Remove numeric-only branch handling

**Issue:** #51
**Phase:** 3 of 3
**Status:** Complete
**Estimated Effort:** 1-2 hours

## Task Groups

### Setup
- [x] [impl] Review current IssueId.scala patterns to identify lines to remove
- [x] [impl] Review test files to identify numeric-only tests to remove

### Tests (TDD - Write Failing Tests First)
- [x] [impl] Add test: IssueId.parse rejects bare numeric without team prefix
- [x] [impl] Add test: IssueId.parse guides user to configure team prefix
- [x] [impl] Add test: IssueId.fromBranch rejects bare numeric branch (e.g., "48")
- [x] [impl] Add test: IssueId.fromBranch rejects numeric branch with description (e.g., "51-feature")
- [x] [impl] Run unit tests to confirm new tests fail as expected

### Implementation - Remove Patterns
- [x] [impl] Remove NumericPattern regex from IssueId.scala (line 12)
- [x] [impl] Remove NumericBranchPattern regex from IssueId.scala (line 16)
- [x] [impl] Run tests to verify pattern removal breaks numeric handling

### Implementation - Update parse Method
- [x] [impl] Update IssueId.parse to reject bare numeric without team prefix
- [x] [impl] Update error message to guide users to TEAM-123 format and mention 'iw init'
- [x] [impl] Ensure parse still works with numeric input + team prefix context
- [x] [impl] Run unit tests to verify parse rejection tests now pass

### Implementation - Update fromBranch Method
- [x] [impl] Update IssueId.fromBranch to reject bare numeric branches
- [x] [impl] Update error message to show TEAM-123 format requirement
- [x] [impl] Remove numeric branch case matching logic
- [x] [impl] Run unit tests to verify fromBranch rejection tests now pass

### Implementation - Simplify team Method
- [x] [impl] Remove empty string case from IssueId.team extension method
- [x] [impl] Simplify to always split on "-" and take head (all IDs now have TEAM-NNN)
- [x] [impl] Run unit tests to verify team method works correctly

### Implementation - Add doctor Check
- [x] [impl] Investigate doctor.scala command and Check infrastructure
- [x] [impl] Add legacy branch detection check to doctor (detect bare numeric branches)
- [x] [impl] Provide migration instructions in warning message
- [x] [impl] Add E2E test for doctor legacy branch detection

### Clean Up Tests
- [x] [impl] Remove test: "IssueId.parse accepts numeric GitHub ID 132" (line 132-135)
- [x] [impl] Remove test: "IssueId.parse accepts single digit numeric ID 1" (line 137-140)
- [x] [impl] Remove test: "IssueId.parse accepts multi-digit numeric ID 999" (line 142-145)
- [x] [impl] Remove test: "IssueId.parse trims whitespace from numeric ID" (line 147-150)
- [x] [impl] Remove test: "IssueId.parse does not uppercase numeric IDs" (line 152-155)
- [x] [impl] Remove test: "IssueId.fromBranch extracts numeric prefix with dash" (line 157-160)
- [x] [impl] Remove test: "IssueId.fromBranch extracts numeric prefix with underscore" (line 162-165)
- [x] [impl] Remove test: "IssueId.fromBranch extracts single digit numeric prefix" (line 167-170)
- [x] [impl] Remove test: "IssueId.team returns empty string for numeric GitHub ID" (line 177-179)
- [x] [impl] Remove test: "IssueId.parse without team prefix accepts numeric input" (line 250-253)
- [x] [impl] Remove test: "IssueId.fromBranch extracts from numeric branch with suffix" (line 65-69, IssueIdFromBranchTest.scala)
- [x] [impl] Remove test: "IssueId.fromBranch extracts from bare numeric branch" (line 86-89, IssueIdFromBranchTest.scala)
- [x] [impl] Update test comment removing NumericPattern reference (line 290-294 in IssueIdTest.scala)

### Integration - Verify Behavior
- [x] [impl] Run full unit test suite (./iw test unit) and verify all tests pass
- [x] [impl] Run full E2E test suite (./iw test e2e) and verify all tests pass
- [x] [impl] Manual test: verify "iw issue 51" without team prefix shows error with guidance
- [x] [impl] Manual test: verify "iw issue IWCLI-51" works correctly
- [x] [impl] Manual test: verify fromBranch("48") returns error with guidance
- [x] [impl] Manual test: verify fromBranch("IWCLI-48") works correctly
- [x] [impl] Manual test: verify "iw doctor" detects legacy numeric branches
- [x] [impl] Manual test: verify "iw doctor" provides migration instructions

## Success Criteria

### Code Changes
- [ ] NumericPattern removed from IssueId.scala
- [ ] NumericBranchPattern removed from IssueId.scala
- [ ] IssueId.parse rejects bare numeric without team prefix
- [ ] IssueId.fromBranch rejects bare numeric branches
- [ ] IssueId.team simplified (no empty string case)
- [ ] Error messages guide users to TEAM-NNN format
- [ ] Error messages mention team prefix configuration

### Testing
- [ ] All numeric-only tests removed
- [ ] New rejection tests added and passing
- [ ] Legacy branch detection test added and passing
- [ ] All existing TEAM-NNN tests still pass
- [ ] Unit test suite passes (munit)
- [ ] E2E test suite passes (BATS)

### Documentation
- [ ] Test comments no longer reference removed patterns
- [ ] Code comments updated if they referenced numeric patterns
- [ ] Error messages provide clear migration path

## Files to Modify

**Core Domain:**
- `.iw/core/IssueId.scala` - Remove patterns, update parse/fromBranch/team
- `.iw/core/test/IssueIdTest.scala` - Remove/update tests, add rejection tests
- `.iw/core/test/IssueIdFromBranchTest.scala` - Remove/update tests, add rejection tests

**Commands:**
- `.iw/commands/doctor.scala` (or hook file) - Add legacy branch check

**E2E Tests:**
- `.iw/test/doctor.bats` - Add legacy branch detection test

## Order of Operations

Follow this exact sequence:
1. Start with tests - Add rejection tests first (TDD)
2. Remove patterns - Delete NumericPattern and NumericBranchPattern
3. Update parse - Remove backward compatibility case
4. Update fromBranch - Remove numeric branch cases
5. Simplify team - Remove empty string case
6. Add doctor check - Detect legacy branches
7. Clean up tests - Remove numeric-only tests
8. Verify behavior - Run full test suite

## Notes

- This phase **removes code** rather than adding it
- Follow TDD: write failing tests before implementing changes
- Error messages must be clear and actionable
- Hard cutoff approach: reject immediately with helpful guidance
- All error messages should mention TEAM-123 format and 'iw init'
