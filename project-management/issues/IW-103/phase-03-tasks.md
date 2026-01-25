# Phase 3 Tasks: Prerequisite validation

**Issue:** IW-103
**Phase:** 3 - Prerequisite validation
**Story:** Story 5 - Handle missing prerequisites gracefully

## Tasks

### Setup
- [x] [impl] [x] [reviewed] Review feedback.bats prerequisite tests as pattern reference

### Tests (TDD - Write First)
- [x] [impl] [x] [reviewed] Create E2E test: `issue create` fails with helpful message when gh CLI not installed
- [x] [impl] [x] [reviewed] Create E2E test: `issue create` fails with auth instructions when gh not authenticated
- [x] [impl] [x] [reviewed] Verify error message contains installation URL (https://cli.github.com/)
- [x] [impl] [x] [reviewed] Verify error message contains authentication command (gh auth login)

### Verification
- [x] [impl] [x] [reviewed] Run all issue-create.bats tests to verify no regressions
- [x] [impl] [x] [reviewed] Verify feedback.bats prerequisite tests still pass (shared patterns)

## Success Criteria

- [ ] All BATS E2E tests pass (including new prerequisite tests)
- [ ] Error messages contain actionable instructions
- [ ] Test patterns consistent with feedback.bats
- [ ] No regressions in existing commands

**Phase Status:** Complete
