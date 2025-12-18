# Phase 3 Context: Describe project command with `./` prefix

**Issue:** IWLE-74
**Phase:** 3 - Describe project command with `./` prefix
**Status:** Ready for implementation

## Goals

Enable users to get detailed help for project-specific commands using the `./` prefix syntax with `--describe`. This completes the feature by providing discoverability for project commands.

## Scope

### In Scope
- Modify `describe_command()` function in `iw-run` script
- Detect `./` prefix and route to project commands directory
- Show PURPOSE, USAGE, ARGS, and EXAMPLES metadata for project commands
- Clear error messages for project command not found
- BATS E2E tests for all scenarios

### Out of Scope
- Changes to execution logic (Phase 2 complete)
- Changes to list logic (Phase 1 complete)
- New metadata fields

## Dependencies

### From Previous Phases
- Phase 1 complete: `list_commands()` shows project commands with `./` prefix
- Phase 2 complete: `execute_command()` routes `./cmd` correctly

### External Dependencies
- Existing `iw-run` script with working `describe_command()` function
- Existing `parse_command_header()` function for metadata extraction
- Existing BATS test infrastructure

## Technical Approach

### Key Changes to `iw-run`

1. **Modify `describe_command()` function:**
   - Detect `./` prefix in command name
   - If prefix: look in `$PROJECT_DIR/.iw/commands/`
   - If no prefix: look in `$COMMANDS_DIR/` (unchanged)
   - Use same `parse_command_header()` for both namespaces

2. **Implementation pattern (same as execute_command):**
   ```bash
   describe_command() {
       local cmd_name="$1"
       local cmd_file=""
       local display_name=""

       if [[ "$cmd_name" == ./* ]]; then
           # Project command - strip ./ prefix
           local actual_name="${cmd_name:2}"
           # Validate
           if [[ -z "$actual_name" ]] || [[ ! "$actual_name" =~ ^[a-zA-Z0-9_-]+$ ]]; then
               echo "Error: Invalid project command name '$cmd_name'" >&2
               exit 1
           fi
           cmd_file="$PROJECT_DIR/.iw/commands/${actual_name}.scala"
           display_name="./$actual_name"

           if [ ! -f "$cmd_file" ]; then
               echo "Error: Project command '$actual_name' not found in .iw/commands/" >&2
               exit 1
           fi
       else
           # Shared command (existing logic)
           ...
       fi

       # Display metadata (same for both)
       echo "=== Command: $display_name ==="
       ...
   }
   ```

3. **Error messages by namespace:**
   - Project command not found: "Project command 'deploy' not found in .iw/commands/"
   - Shared command not found: "Command 'missing' not found"

### Edge Cases
- `./` alone (no command name): Error "Invalid project command name"
- `./cmd-with-dash`: Should work
- Project command with all metadata (PURPOSE, USAGE, ARGS, EXAMPLES): Display all
- Project command with minimal metadata: Display what's available

### Files to Modify
- `iw-run` (shell script)

### Test Files to Create
- `.iw/test/project-commands-describe.bats`

## Testing Strategy

### E2E Tests (BATS)

**Scenario 1: Describe project command with full metadata**
- Create project command with PURPOSE, USAGE, ARGS, EXAMPLES
- Run `iw-run --describe ./test-cmd`
- Assert all metadata sections shown

**Scenario 2: Describe project command with minimal metadata**
- Create project command with only PURPOSE
- Run `iw-run --describe ./test-cmd`
- Assert PURPOSE shown, other sections omitted gracefully

**Scenario 3: Describe project command not found**
- Run `iw-run --describe ./nonexistent`
- Assert error mentions "Project command 'nonexistent' not found"
- Assert mentions ".iw/commands/"

**Scenario 4: Describe shared command (no prefix) works normally**
- Run `iw-run --describe version`
- Assert shared command metadata shown

**Scenario 5: Invalid project command syntax**
- Run `iw-run --describe ./`
- Assert error "Invalid project command name"

### Test Data
- Create project commands with varying metadata levels
- Use temporary directories for isolation
- Clean up in teardown

## Acceptance Criteria

1. ✓ `iw --describe ./deploy` shows project command metadata
2. ✓ `iw --describe start` shows shared command metadata (unchanged)
3. ✓ All metadata fields (PURPOSE, USAGE, ARGS, EXAMPLES) displayed correctly
4. ✓ Clear error messages indicate namespace searched
5. ✓ Invalid syntax handled gracefully
6. ✓ All BATS tests pass
7. ✓ Existing tests continue to pass (regression)

## Custom Instructions

None - proceed with standard TDD approach.

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `iw-run` | Modify | Update `describe_command()` to handle `./` prefix routing |
| `.iw/test/project-commands-describe.bats` | Create | E2E tests for project command description |

## Estimated Effort

1 hour including tests (straightforward - same pattern as Phase 2)
