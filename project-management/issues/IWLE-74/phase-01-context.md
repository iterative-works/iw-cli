# Phase 1 Context: Project command discovery

**Issue:** IWLE-74
**Phase:** 1 - Project command discovery
**Status:** Ready for implementation

## Goals

Enable users to discover both shared and project-specific commands when running `iw --list`. This establishes the foundation for project command functionality.

## Scope

### In Scope
- Modify `list_commands()` function in `iw-run` script
- Display shared commands in main "Commands:" section
- Display project commands in separate "Project commands (use ./name):" section
- Show `./` prefix for project commands in listing
- Gracefully handle missing `.iw/commands/` directory (no error, no section)
- BATS E2E tests for all scenarios

### Out of Scope
- Command execution (Phase 2)
- Command description (Phase 3)
- Hook discovery for project commands
- Bootstrap changes

## Dependencies

### From Previous Phases
None - this is the first phase.

### External Dependencies
- Existing `iw-run` script with working `list_commands()` function
- Existing BATS test infrastructure
- Project directory with potential `.iw/commands/` directory

## Technical Approach

### Key Changes to `iw-run`

1. **Modify `list_commands()` function:**
   - Keep existing logic for shared commands from `$COMMANDS_DIR`
   - Add detection of `$PROJECT_DIR/.iw/commands` directory
   - If project commands directory exists and has .scala files:
     - Add "Project commands (use ./name):" section
     - List each project command with `./` prefix
     - Parse same metadata (PURPOSE, USAGE) as shared commands

2. **Output format:**
   ```
   Commands:
     version    Show version information
     init       Initialize project
     start      Start working on an issue

   Project commands (use ./name):
     ./deploy   Deploy application to environment
     ./migrate  Run database migrations
   ```

3. **Edge cases:**
   - No `.iw/commands/` directory: Skip project section entirely (no error)
   - Empty `.iw/commands/` directory: Skip project section
   - Files without proper headers: Show command name without description

### File to Modify
- `iw-run` (shell script)

### Test Files to Create
- `tests/e2e/project-commands-list.bats`

## Testing Strategy

### E2E Tests (BATS)

**Scenario 1: List commands shows both namespaces separately**
- Create temp project with `.iw/commands/` containing a sample command
- Run `iw-run --list`
- Assert shared commands appear in "Commands:" section
- Assert project commands appear in "Project commands (use ./name):" section
- Assert `./` prefix shown for project commands

**Scenario 2: List commands when no project commands exist**
- Run `iw-run --list` without `.iw/commands/` directory
- Assert shared commands listed normally
- Assert NO "Project commands" section appears

**Scenario 3: List commands with empty project commands directory**
- Create `.iw/commands/` directory but leave it empty
- Run `iw-run --list`
- Assert shared commands listed normally
- Assert NO "Project commands" section appears

**Scenario 4: Project command metadata displayed correctly**
- Create project command with PURPOSE and USAGE comments
- Run `iw-run --list`
- Assert PURPOSE description shown for project command

### Test Data
- Create minimal sample project command script for tests
- Use temporary directories for isolation
- Clean up in teardown

## Acceptance Criteria

1. ✓ `iw --list` shows shared commands in main "Commands:" section
2. ✓ `iw --list` shows project commands in separate "Project commands (use ./name):" section (if any exist)
3. ✓ Project commands shown with `./` prefix
4. ✓ Each command displays correct PURPOSE metadata
5. ✓ No errors or empty section if project commands directory doesn't exist
6. ✓ No errors or empty section if project commands directory is empty
7. ✓ All BATS tests pass
8. ✓ Existing tests continue to pass (regression)

## Custom Instructions

None - proceed with standard TDD approach.

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `iw-run` | Modify | Update `list_commands()` to handle both namespaces |
| `tests/e2e/project-commands-list.bats` | Create | E2E tests for project command listing |

## Estimated Effort

2-3 hours including tests
