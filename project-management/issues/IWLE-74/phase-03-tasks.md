# Phase 3 Tasks: Describe project command with `./` prefix

**Issue:** IWLE-74
**Phase:** 3 - Describe project command with `./` prefix
**Status:** Complete

## Setup

- [x] [setup] Create BATS test file `.iw/test/project-commands-describe.bats`
- [x] [setup] Create sample project command fixture with full metadata

## Tests (write first)

- [x] [test] Test: describe project command shows full metadata (PURPOSE, USAGE, ARGS, EXAMPLES)
- [x] [test] Test: describe project command with minimal metadata shows what's available
- [x] [test] Test: describe project command not found shows clear error
- [x] [test] Test: describe shared command (no prefix) works normally
- [x] [test] Test: describe invalid project command syntax shows error

## Implementation

- [x] [impl] Detect ./ prefix in describe_command() and extract actual command name
- [x] [impl] Route project commands to $PROJECT_DIR/.iw/commands/
- [x] [impl] Update error messages to indicate namespace searched
- [x] [impl] Display command name with ./ prefix in output header

## Integration

- [x] [verify] Run all existing BATS tests to verify no regressions
- [x] [verify] Run new project-commands-describe.bats tests to verify implementation

## Completion

- [x] [cleanup] Update implementation-log.md with phase summary
