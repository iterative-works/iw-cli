# Phase 1 Tasks: Help display

**Issue:** IW-103
**Phase:** 1 - Help display
**Story:** Story 7 - Display help when no arguments provided

## Tasks

### Setup
- [ ] [setup] Create `.iw/commands/issue-create.scala` with PURPOSE header

### Tests (TDD - Write First)
- [ ] [test] Create BATS E2E test file `tests/e2e/issue-create.bats`
- [ ] [test] Test: `iw issue create` with no args shows help and exits 1
- [ ] [test] Test: `iw issue create --help` shows help and exits 0
- [ ] [test] Test: `iw issue create -h` shows help and exits 0
- [ ] [test] Test: Help text contains `--title` flag documentation
- [ ] [test] Test: Help text contains `--description` flag documentation
- [ ] [test] Test: Help text contains usage examples

### Implementation
- [ ] [impl] Implement `showHelp()` function with usage text
- [ ] [impl] Handle `--help` and `-h` flags in main function
- [ ] [impl] Handle missing arguments case (show help, exit 1)
- [ ] [impl] Add placeholder stub for `--title` argument (defer to Phase 2)

### Integration
- [ ] [int] Verify all E2E tests pass
- [ ] [int] Manual smoke test of help display

## Success Criteria

- [ ] All BATS tests pass
- [ ] `iw issue create` shows help and exits 1
- [ ] `iw issue create --help` shows help and exits 0
- [ ] Help text matches style of `feedback.scala`
- [ ] No regressions in existing commands
