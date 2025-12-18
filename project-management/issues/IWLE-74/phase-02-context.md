# Phase 2 Context: Execute project command with `./` prefix

**Issue:** IWLE-74
**Phase:** 2 - Execute project command with `./` prefix
**Status:** Ready for implementation

## Goals

Enable users to execute project-specific commands using the explicit `./` prefix syntax. This delivers the core value of the feature - actually running custom project commands.

## Scope

### In Scope
- Detect `./` prefix in command name in `execute_command()` function
- Route `./cmd` to `$PROJECT_DIR/.iw/commands/cmd.scala`
- Route `cmd` (no prefix) to `$COMMANDS_DIR/cmd.scala` (unchanged behavior)
- Include core library in scala-cli invocation for project commands
- Discover hooks from BOTH directories for shared commands
- Clear error messages indicating which namespace was searched
- BATS E2E tests for all scenarios

### Out of Scope
- Command description with `./` prefix (Phase 3)
- Hooks for project commands (deferred - YAGNI per analysis)
- Bootstrap changes (shared commands only per analysis)

## Dependencies

### From Previous Phases
- Phase 1 complete: `list_commands()` already shows project commands with `./` prefix
- Users can discover project commands via `iw --list`

### External Dependencies
- Existing `iw-run` script with working `execute_command()` function
- Existing BATS test infrastructure
- scala-cli available

## Technical Approach

### Key Changes to `iw-run`

1. **Modify command name validation in `execute_command()`:**
   - Allow `./` prefix (strip it to get actual command name)
   - Continue rejecting other special characters
   - Pattern: `^\.\/[a-zA-Z0-9_-]+$` for project commands

2. **Detect namespace in `execute_command()`:**
   ```bash
   if [[ "$cmd_name" == ./* ]]; then
       # Project command - strip ./ prefix
       actual_name="${cmd_name:2}"
       cmd_file="$PROJECT_DIR/.iw/commands/${actual_name}.scala"
       is_project_cmd=true
   else
       # Shared command
       cmd_file="$COMMANDS_DIR/${cmd_name}.scala"
       is_project_cmd=false
   fi
   ```

3. **Execute project commands:**
   - Look ONLY in `$PROJECT_DIR/.iw/commands/`
   - Include core library: `$CORE_DIR/*.scala`
   - NO hook discovery for project commands (per analysis decision)
   - Pass all CLI arguments through

4. **Execute shared commands (enhanced):**
   - Look ONLY in `$COMMANDS_DIR/` (unchanged)
   - Hook discovery from BOTH directories:
     - `$COMMANDS_DIR/*.hook-<cmd>.scala` (shared hooks)
     - `$PROJECT_DIR/.iw/commands/*.hook-<cmd>.scala` (project hooks for shared commands)
   - This allows projects to extend shared commands with custom hooks

5. **Error messages by namespace:**
   - Project command not found: "Project command 'deploy' not found in .iw/commands/"
   - Shared command not found: "Command 'missing' not found"
   - Both suggest: "Run 'iw --list' to see available commands"

### Edge Cases
- `./` alone (no command name): Error "Invalid project command name"
- `.//cmd` (double slash): Error "Invalid project command name"
- `./cmd-with-dash`: Should work (dashes allowed)
- `./CMD` (uppercase): Should work
- Project command imports core library: Must work (primary use case)

### Files to Modify
- `iw-run` (shell script)

### Test Files to Create
- `tests/e2e/project-commands-execute.bats`

## Testing Strategy

### E2E Tests (BATS)

**Scenario 1: Execute project command successfully**
- Create project command that echoes a message
- Run `iw-run ./test-cmd`
- Assert command executed and message printed

**Scenario 2: Project command receives arguments**
- Create project command that echoes its arguments
- Run `iw-run ./test-cmd arg1 arg2`
- Assert arguments passed correctly

**Scenario 3: Project command accesses core library**
- Create project command that imports and uses Config or similar
- Run `iw-run ./test-cmd`
- Assert command runs without import errors

**Scenario 4: Project command not found shows clear error**
- Run `iw-run ./nonexistent`
- Assert error mentions "Project command 'nonexistent' not found"
- Assert suggestion to run --list

**Scenario 5: Shared command without prefix works normally**
- Run `iw-run version` (known shared command)
- Assert it executes (doesn't look in project commands)

**Scenario 6: Shared command not found shows clear error**
- Run `iw-run nonexistent`
- Assert error mentions "Command 'nonexistent' not found"
- Assert suggestion to run --list

**Scenario 7: Same name in both namespaces - no conflict**
- Create shared command and project command with same name
- Run `iw-run same-name` → shared executes
- Run `iw-run ./same-name` → project executes

**Scenario 8: Invalid project command syntax**
- Run `iw-run ./` → error
- Run `iw-run ./invalid$name` → error

**Scenario 9: Hook discovery for shared commands includes project hooks**
- Create hook file in project `.iw/commands/*.hook-doctor.scala`
- Run shared `doctor` command
- Assert project hook is discovered and executed

### Test Data
- Create minimal project commands for tests (simple echo scripts)
- Use temporary directories for isolation
- Clean up in teardown

## Acceptance Criteria

1. ✓ `iw ./deploy` executes project command from `.iw/commands/deploy.scala`
2. ✓ `iw start` executes shared command (never looks at project commands)
3. ✓ Project command can import and use core library classes
4. ✓ Project command receives all CLI arguments correctly
5. ✓ Clear error messages indicate namespace searched
6. ✓ `iw ./missing` shows "Project command 'missing' not found"
7. ✓ `iw missing` shows "Command 'missing' not found"
8. ✓ Same-named commands in both namespaces work independently
9. ✓ Shared command hook discovery includes project hooks
10. ✓ All BATS tests pass
11. ✓ Existing tests continue to pass (regression)

## Custom Instructions

None - proceed with standard TDD approach.

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `iw-run` | Modify | Update `execute_command()` to handle `./` prefix routing |
| `tests/e2e/project-commands-execute.bats` | Create | E2E tests for project command execution |

## Estimated Effort

3-4 hours including tests
