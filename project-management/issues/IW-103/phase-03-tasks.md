# Phase 3 Tasks: Prerequisite validation

**Issue:** IW-103
**Phase:** 3 - Prerequisite validation
**Story:** Story 5 - Handle missing prerequisites gracefully

## Tasks

### Setup
- [ ] [impl] [ ] [reviewed] Review feedback.bats prerequisite tests as pattern reference

### Tests (TDD - Write First)
- [ ] [impl] [ ] [reviewed] Create E2E test: `issue create` fails with helpful message when gh CLI not installed
- [ ] [impl] [ ] [reviewed] Create E2E test: `issue create` fails with auth instructions when gh not authenticated
- [ ] [impl] [ ] [reviewed] Verify error message contains installation URL (https://cli.github.com/)
- [ ] [impl] [ ] [reviewed] Verify error message contains authentication command (gh auth login)

### Verification
- [ ] [impl] [ ] [reviewed] Run all issue-create.bats tests to verify no regressions
- [ ] [impl] [ ] [reviewed] Verify feedback.bats prerequisite tests still pass (shared patterns)

## Success Criteria

- [ ] All BATS E2E tests pass (including new prerequisite tests)
- [ ] Error messages contain actionable instructions
- [ ] Test patterns consistent with feedback.bats
- [ ] No regressions in existing commands

**Phase Status:** Not Started
