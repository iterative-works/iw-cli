# Phase 3: Plugin Command Execution

## Goals

Extend `iw-run`'s `execute_command()` function to handle `<plugin>/<command>` syntax (e.g., `iw kanon/implement`). After this phase, users can discover, describe, AND execute plugin commands. This is the execution counterpart to Phase 2's discovery and listing.

## Scope

### In Scope
- Parse `<plugin>/<command>` syntax in `execute_command()` (contains `/` but does not start with `./`)
- Use `discover_plugins()` (from Phase 2) to resolve plugin name to plugin directory
- Include plugin `lib/*.scala` files on the scala-cli classpath alongside `$IW_CORE_DIR` files
- Plugin `lib/` is isolated: each plugin's lib is only compiled with its own commands (no cross-plugin lib files)
- Plugin commands execute from `$PROJECT_DIR` (same working directory as shared/project commands)
- Error handling: unknown plugin name, unknown command within plugin, invalid syntax (multiple slashes, empty parts)
- Pass arguments through to the plugin command
- BATS E2E tests for plugin command execution

### Out of Scope
- Hook discovery from plugin `hooks/` directories -- Phase 4
- Project-level hook discovery for plugin commands -- Phase 4
- `// REQUIRES:` version checking -- Phase 4
- Any changes to `list_commands()` or `describe_command()` -- done in Phase 2

## Dependencies

- **Phase 1 (complete):** Constants in `Constants.scala`
- **Phase 2 (complete):** `discover_plugins()` function, plugin name validation patterns in `describe_command()`

## Approach

### Step 1: Add plugin command branch to `execute_command()`

Insert a new branch in `execute_command()` (lines 401-502 of `iw-run`) between the `./` project command detection (line 410) and the shared command fallback (line 439). The branch follows the same pattern as `describe_command()` (lines 234-311).

Detection logic:
```bash
# After the ./ project command check, before the shared command else:
if [[ "$cmd_name" != ./* ]] && [[ "$cmd_name" == */* ]]; then
    # Plugin command
fi
```

Implementation steps within the branch:
1. Validate syntax: only one slash allowed, both parts non-empty and alphanumeric+dash+underscore
2. Split on `/` to get `plugin_name` and `plugin_cmd_name`
3. Call `discover_plugins()` to find the plugin directory
4. Resolve command file: `$plugin_dir/commands/${plugin_cmd_name}.scala`
5. Find core files: `find "$CORE_DIR" -name "*.scala" -not -path "*/test/*" -not -path "*/.scala-build/*"`
6. Find plugin lib files: `find "$plugin_dir/lib" -name "*.scala" -not -path "*/.scala-build/*"` (if `lib/` exists)
7. `cd "$PROJECT_DIR"` and `exec scala-cli run` with command file + core files + plugin lib files + `-- "$@"`

Key differences from project command execution:
- Plugin commands include the plugin's `lib/*.scala` on the classpath (project commands do not have a lib directory)
- Plugin commands do NOT include hook files (hooks are Phase 4)

Key differences from shared command execution:
- No hook discovery (Phase 4 will add project hooks for plugin commands)
- Plugin lib files added to classpath (shared commands don't have a lib directory)

### Step 2: Error handling

Three error cases, each with a clear message:

1. **Invalid syntax** (multiple slashes, empty parts, invalid characters):
   ```
   Error: Invalid plugin command syntax 'foo/bar/baz'
   Use <plugin>/<command> format (e.g., kanon/implement)
   ```

2. **Unknown plugin name** (not found by `discover_plugins()`):
   ```
   Error: Plugin 'nonexistent' not found
   Run 'iw --list' to see available commands
   ```

3. **Unknown command within known plugin**:
   ```
   Error: Command 'nonexistent' not found in plugin 'kanon'
   Run 'iw --list' to see available commands
   ```

These mirror the error messages already used in `describe_command()` for consistency, with the addition of the `--list` suggestion.

### Step 3: Write BATS E2E tests

New test file: `.iw/tests/e2e/plugin-commands-execute.bats`

Test infrastructure (following `project-commands-execute.bats` patterns):
- Create temp dirs with mock plugin structures in `setup()`
- Mock plugin commands are minimal `.scala` files with `@main` and `println`
- Export `IW_SERVER_DISABLED=1` in `setup()`
- Override `IW_PLUGIN_DIRS` to point at temp fixtures
- Each test creates plugin dir with `commands/` and optionally `lib/`

Test cases:
1. **Basic execution**: `iw kanon/implement` finds and runs the command, produces expected output
2. **Argument passing**: `iw kanon/implement arg1 arg2` passes arguments through correctly
3. **Core on classpath**: Plugin command can import from core (uses core classes successfully)
4. **Plugin lib on classpath**: Plugin command can import from plugin's `lib/` directory
5. **Unknown plugin name**: Clear error message suggesting `--list`
6. **Unknown command in known plugin**: Clear error message suggesting `--list`
7. **Invalid syntax**: Multiple slashes, empty parts produce clear error

## Files to Modify

- **`iw-run`** -- add plugin command branch to `execute_command()` (between project command and shared command branches)
- **`.iw/tests/e2e/plugin-commands-execute.bats`** -- new: execution tests

## Code Reference

### Current `execute_command()` structure (lines 400-502 of `iw-run`)

```
execute_command() {
    if [[ "$cmd_name" == ./* ]]; then
        # Project command branch (lines 410-437)
        # - strips ./ prefix, validates, finds core files
        # - exec scala-cli with cmd_file + core_files
    else
        # Shared command branch (lines 439-501)
        # - validates name, finds command file
        # - discovers hooks (shared + project)
        # - exec scala-cli with cmd_file + hook_files + core_files
    fi
}
```

### Target structure after Phase 3

```
execute_command() {
    if [[ "$cmd_name" == ./* ]]; then
        # Project command (unchanged)
    elif [[ "$cmd_name" != ./* ]] && [[ "$cmd_name" == */* ]]; then
        # Plugin command (NEW)
        # - validate syntax, split plugin_name/plugin_cmd_name
        # - discover_plugins() to find plugin_dir
        # - find core files + plugin lib files
        # - exec scala-cli with cmd_file + core_files + lib_files
    else
        # Shared command (unchanged)
    fi
}
```

### `describe_command()` plugin branch (reference pattern, lines 234-311)

The validation, plugin discovery, and error handling in `describe_command()` should be closely mirrored. The main difference is that `execute_command()` runs the command via `scala-cli run` instead of printing metadata.

## Testing Strategy

All testing is BATS E2E, following existing patterns from `project-commands-execute.bats`.

Each test:
1. Creates a temp directory structure with mock plugins in `setup()`
2. Sets `IW_PLUGIN_DIRS` to point at the fixture (avoids needing XDG setup)
3. Runs `./iw <plugin>/<command>` and asserts on output or exit code
4. Cleans up in `teardown()`

Plugin commands need to actually compile and run (unlike Phase 2's list/describe tests which only read comment headers). Test `.scala` files must be valid scala-cli scripts with `@main` and `println`.

Mock plugin lib files should define a simple object/function that the command file imports, proving the lib is on the classpath.

Key patterns:
- `IW_SERVER_DISABLED=1` in every `setup()`
- Temp dirs via `$BATS_TEST_TMPDIR` or `mktemp -d`
- Plugin command files use full sub-package imports (e.g., `iw.core.adapters.*`, `iw.core.model.*`) not `iw.core.*`

## Acceptance Criteria

- [ ] `iw kanon/implement` executes the plugin command successfully
- [ ] Plugin command arguments are passed through
- [ ] Plugin `lib/*.scala` files are included on classpath
- [ ] Core files are included on classpath for plugin commands
- [ ] Unknown plugin -> clear error message with suggestion to run `iw --list`
- [ ] Unknown command in known plugin -> clear error message with suggestion to run `iw --list`
- [ ] Invalid syntax -> clear error message
- [ ] All existing E2E tests still pass (no regression)
- [ ] BATS tests for plugin command execution pass
