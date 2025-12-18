# Implementation Log: IWLE-74

## Phase 1: Project command discovery

**Status:** Complete
**Date:** 2025-12-18

### Summary

Implemented project command discovery feature that allows `iw --list` to display both shared commands and project-specific commands from `.iw/commands/` directory.

### Changes Made

1. **Created test file:** `.iw/test/project-commands-list.bats`
   - 8 comprehensive E2E tests covering all scenarios
   - Tests for project command listing, ./ prefix, missing directory, empty directory, metadata display
   - Additional tests for hook file exclusion, missing headers, and special character handling
   - All tests passing

2. **Modified:** `iw-run`
   - Updated `list_commands()` function to detect and list project commands
   - Added logic to check for `$PROJECT_DIR/.iw/commands` directory
   - Project commands shown in separate "Project commands (use ./name):" section
   - Commands displayed with `./` prefix
   - Gracefully handles missing or empty directory (no errors, no empty section)
   - Properly skips hook files (*.hook-*.scala)

### Test Results

All 8 E2E tests passing:
- list commands shows project commands section when project commands exist ✓
- list commands shows ./prefix for project commands ✓
- list commands shows no project section when .iw/commands directory missing ✓
- list commands shows no project section when .iw/commands directory empty ✓
- project command PURPOSE metadata displayed correctly ✓
- list commands excludes hook files from project commands ✓
- list commands handles missing PURPOSE header gracefully ✓
- list commands handles special characters in metadata safely ✓

No regressions detected in existing tests.

### Code Review

**Review file:** `review-phase-01-2025-12-18-143301.md`
**Iterations:** 1 (with post-review fixes)
**Skills applied:** style, testing

**Critical issues found and fixed:**
1. Missing test for hook file exclusion - Added test
2. Missing edge case tests for malformed headers - Added tests for missing PURPOSE and special characters

**Warnings (documented, acceptable):**
- Test setup creates git repo (kept for consistency with other test files)
- Style suggestions for comments (minor, not blocking)

### TDD Approach

Followed strict TDD methodology:
1. RED: Wrote 5 tests, verified they fail appropriately (3/5 failed as expected)
2. GREEN: Implemented feature in `list_commands()`, all tests pass
3. REFACTOR: Code is clean and follows existing patterns, no refactoring needed

### Files Modified

- `iw-run` - Added project command listing support
- `.iw/test/project-commands-list.bats` - New test file with 5 tests

### Next Steps

Phase 2 will implement project command execution with `./command-name` syntax.

## Phase 2: Project command execution

**Status:** Complete
**Date:** 2025-12-18

### Summary

Implemented project command execution feature that allows users to run project-specific commands using the `./` prefix syntax. This includes routing logic, core library support, error messages, and enhanced hook discovery for shared commands.

### Changes Made

1. **Created test file:** `.iw/test/project-commands-execute.bats`
   - 10 comprehensive E2E tests covering all scenarios
   - Tests for successful execution, argument passing, core library imports
   - Tests for error messages (both namespaces)
   - Tests for namespace isolation (same name in both namespaces)
   - Tests for invalid syntax
   - Tests for hook discovery from project directory
   - All tests passing

2. **Modified:** `iw-run`
   - Updated `execute_command()` function to detect `./` prefix
   - Added routing logic: `./cmd` → `$PROJECT_DIR/.iw/commands/cmd.scala`
   - Included core library in scala-cli invocation for project commands
   - Updated error messages to indicate which namespace was searched
   - Enhanced hook discovery for shared commands to include project hooks
   - Validation for project command names (after stripping `./` prefix)

### Test Results

All 10 E2E tests passing:
- execute project command with ./ prefix successfully ✓
- project command receives CLI arguments correctly ✓
- project command can import core library (Config) ✓
- project command not found shows clear error ✓
- shared command without prefix executes normally ✓
- shared command not found shows clear error ✓
- same name in both namespaces - each invoked correctly ✓
- invalid project command syntax ./ alone shows error ✓
- invalid project command syntax with special chars shows error ✓
- shared command discovers hooks from project directory ✓

No regressions detected in existing tests (all 79 non-bootstrap tests passing).

### TDD Approach

Followed strict TDD methodology:
1. RED: Wrote 10 tests, verified they fail appropriately (5/10 failed as expected)
2. GREEN: Implemented routing logic in `execute_command()`, all tests pass
3. REFACTOR: Code is clean and follows existing patterns, no refactoring needed

### Implementation Details

**Namespace Routing:**
- Commands with `./` prefix → project namespace (`$PROJECT_DIR/.iw/commands/`)
- Commands without prefix → shared namespace (`$COMMANDS_DIR/`)
- Clear error messages indicate which namespace was searched

**Core Library Support:**
- Project commands automatically include `$CORE_DIR/*.scala` in scala-cli invocation
- Enables project commands to import and use core library classes
- Example: `import iw.core.{ConfigFileRepository, ProjectConfiguration}`

**Hook Discovery Enhancement:**
- Shared commands now discover hooks from BOTH directories:
  - `$COMMANDS_DIR/*.hook-<cmd>.scala` (shared hooks)
  - `$PROJECT_DIR/.iw/commands/*.hook-<cmd>.scala` (project hooks)
- Allows projects to extend shared commands with custom hooks
- Project commands do NOT support hooks (per YAGNI analysis)

**Error Handling:**
- Project command not found: "Project command 'X' not found in .iw/commands/"
- Shared command not found: "Command 'X' not found"
- Both suggest: "Run 'iw --list' to see available commands"
- Invalid syntax: "Invalid project command name"

### Files Modified

- `iw-run` - Added project command execution support with namespace routing
- `.iw/test/project-commands-execute.bats` - New test file with 10 tests

### Next Steps

Phase 3 will implement project command description with `iw --describe ./command-name` syntax.

## Phase 3: Project command description with ./prefix

**Status:** Complete
**Date:** 2025-12-18

### Summary

Implemented project command description feature that allows users to get detailed help for project-specific commands using `iw --describe ./command-name` syntax. This completes the feature by providing discoverability for project commands.

### Changes Made

1. **Created test file:** `.iw/test/project-commands-describe.bats`
   - 7 comprehensive E2E tests covering all scenarios
   - Tests for full metadata display (PURPOSE, USAGE, ARGS, EXAMPLES)
   - Tests for minimal metadata display
   - Tests for error messages (both namespaces)
   - Tests for invalid syntax
   - Tests for commands with dashes in name
   - All tests passing

2. **Modified:** `iw-run`
   - Updated `describe_command()` function to detect `./` prefix
   - Added routing logic: `./cmd` → `$PROJECT_DIR/.iw/commands/cmd.scala`
   - Uses same `parse_command_header()` function for both namespaces
   - Display name includes `./` prefix for project commands
   - Updated error messages to indicate which namespace was searched

### Test Results

All 7 E2E tests passing:
- describe project command shows full metadata (PURPOSE, USAGE, ARGS, EXAMPLES) ✓
- describe project command with minimal metadata shows what's available ✓
- describe project command not found shows clear error ✓
- describe shared command (no prefix) works normally ✓
- describe invalid project command syntax ./ alone shows error ✓
- describe invalid project command syntax with special chars shows error ✓
- describe project command with dashes in name works ✓

No regressions detected in existing tests (all 86 non-bootstrap tests passing).

### TDD Approach

Followed strict TDD methodology:
1. RED: Wrote 7 tests, verified they fail appropriately (4/7 failed as expected)
2. GREEN: Implemented routing logic in `describe_command()`, all tests pass
3. REFACTOR: Code is clean and follows existing patterns, no refactoring needed

### Implementation Details

**Namespace Routing:**
- Commands with `./` prefix → project namespace (`$PROJECT_DIR/.iw/commands/`)
- Commands without prefix → shared namespace (`$COMMANDS_DIR/`)
- Display name includes `./` prefix for project commands: `=== Command: ./deploy ===`
- Clear error messages indicate which namespace was searched

**Metadata Display:**
- Uses same `parse_command_header()` function for both namespaces
- Displays PURPOSE (required)
- Displays USAGE (required)
- Displays ARGS (optional, shown if present)
- Displays EXAMPLES (optional, shown if present)
- Gracefully handles minimal metadata (only PURPOSE/USAGE)

**Error Handling:**
- Project command not found: "Project command 'X' not found in .iw/commands/"
- Shared command not found: "Command 'X' not found"
- Invalid syntax: "Invalid project command name"
- Same validation as execute_command (alphanumeric, dash, underscore only)

### Files Modified

- `iw-run` - Added project command description support with namespace routing
- `.iw/test/project-commands-describe.bats` - New test file with 7 tests

### Feature Complete

All three phases of IWLE-74 are now complete:
- Phase 1: List project commands with `iw --list`
- Phase 2: Execute project commands with `./command-name`
- Phase 3: Describe project commands with `iw --describe ./command-name`

The project commands feature is fully implemented and ready for use.
