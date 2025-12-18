# Phase 1 Tasks: Project command discovery

**Issue:** IWLE-74
**Phase:** 1 - Project command discovery
**Status:** Complete

## Setup

- [x] [setup] Create BATS test file `.iw/test/project-commands-list.bats`
- [x] [setup] Create sample project command fixture for testing

## Tests (write first)

- [x] [test] Test: list commands shows project commands section when project commands exist
- [x] [test] Test: list commands shows ./prefix for project commands
- [x] [test] Test: list commands shows no project section when .iw/commands directory missing
- [x] [test] Test: list commands shows no project section when .iw/commands directory empty
- [x] [test] Test: project command PURPOSE metadata displayed correctly

## Implementation

- [x] [impl] Add project commands listing to list_commands() function in iw-run
- [x] [impl] Display project commands with ./ prefix
- [x] [impl] Handle missing .iw/commands directory gracefully
- [x] [impl] Handle empty .iw/commands directory gracefully

## Integration

- [x] [verify] Run all existing BATS tests to verify no regressions
- [x] [verify] Run new project-commands-list.bats tests to verify implementation

## Completion

- [x] [cleanup] Update implementation-log.md with phase summary
