# Phase 2 Tasks: Explicit live API opt-in mechanism

**Issue:** IWLE-131
**Phase:** 2 of 3

## Implementation Tasks

### Warning Message

- [x] [impl] Add setup_file() function to feedback.bats with warning message for live API tests
- [x] [test] Verify warning message appears when ENABLE_LIVE_API_TESTS=1 is set
- [x] [test] Verify no warning when ENABLE_LIVE_API_TESTS is not set

### Documentation Updates

- [x] [impl] Update README.md with comprehensive Testing section
- [x] [test] Verify README commands are accurate and work correctly

### Verification

- [x] [test] Run full E2E test suite to confirm no regressions
- [x] [test] Verify skip messages remain clear and actionable

## Notes

- setup_file() runs once per file, before any tests
- Warning goes to stderr to not interfere with test output parsing
- README section should explain all test modes concisely
