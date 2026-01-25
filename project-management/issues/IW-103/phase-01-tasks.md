# Phase 1 Tasks: Help display

**Issue:** IW-103
**Phase:** 1 - Help display
**Story:** Story 7 - Display help when no arguments provided

## Tasks

### Setup
- [x] [setup] Create `.iw/commands/issue-create.scala` with PURPOSE header

### Tests (TDD - Write First)
- [x] [test] Create BATS E2E test file `.iw/test/issue-create.bats`
- [x] [test] Test: `iw issue create` with no args shows help and exits 1
- [x] [test] Test: `iw issue create --help` shows help and exits 0
- [x] [test] Test: `iw issue create -h` shows help and exits 0
- [x] [test] Test: Help text contains `--title` flag documentation
- [x] [test] Test: Help text contains `--description` flag documentation
- [x] [test] Test: Help text contains usage examples

### Implementation
- [x] [impl] Implement `showHelp()` function with usage text
- [x] [impl] Handle `--help` and `-h` flags in main function
- [x] [impl] Handle missing arguments case (show help, exit 1)
- [x] [impl] Add placeholder stub for `--title` argument (defer to Phase 2)

### Integration
- [x] [int] Verify all E2E tests pass
- [x] [int] Manual smoke test of help display

## Success Criteria

- [x] All BATS tests pass
- [x] `iw issue create` shows help and exits 1
- [x] `iw issue create --help` shows help and exits 0
- [x] Help text matches style of `feedback.scala`
- [x] No regressions in existing commands

**Phase Status:** Complete
