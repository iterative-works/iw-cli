# Phase 2 Tasks: Execute project command with `./` prefix

**Issue:** IWLE-74
**Phase:** 2 - Execute project command with `./` prefix
**Status:** Complete

## Setup

- [x] [setup] Create BATS test file `.iw/test/project-commands-execute.bats`
- [x] [setup] Create sample project command fixture that echoes arguments
- [x] [setup] Create sample project command fixture that imports core library

## Tests (write first)

- [x] [test] Test: execute project command with ./ prefix successfully
- [x] [test] Test: project command receives CLI arguments correctly
- [x] [test] Test: project command can import core library (Config)
- [x] [test] Test: project command not found shows clear error
- [x] [test] Test: shared command without prefix executes normally
- [x] [test] Test: shared command not found shows clear error
- [x] [test] Test: same name in both namespaces - each invoked correctly
- [x] [test] Test: invalid project command syntax (e.g., ./) shows error
- [x] [test] Test: shared command discovers hooks from project directory

## Implementation

- [x] [impl] Detect ./ prefix in execute_command() and extract actual command name
- [x] [impl] Route project commands to $PROJECT_DIR/.iw/commands/
- [x] [impl] Include core library ($CORE_DIR/*.scala) when executing project commands
- [x] [impl] Update error messages to indicate namespace searched
- [x] [impl] Enhance hook discovery for shared commands to include project hooks

## Integration

- [x] [verify] Run all existing BATS tests to verify no regressions
- [x] [verify] Run new project-commands-execute.bats tests to verify implementation

## Completion

- [x] [cleanup] Update implementation-log.md with phase summary
