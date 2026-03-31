# Phase 3 Tasks: Plugin Command Execution

**Issue:** IW-323
**Phase:** 3 - Plugin Command Execution
**Status:** Not Started

## Setup

- [ ] [setup] Read `execute_command()` in `iw-run` to understand current branching (project command vs shared command) and classpath assembly
- [ ] [setup] Read `describe_command()` plugin branch (Phase 2) as the reference pattern for validation, plugin discovery, and error handling
- [ ] [setup] Create BATS test file `.iw/tests/e2e/plugin-commands-execute.bats` with setup/teardown scaffolding (temp dirs, `IW_SERVER_DISABLED=1`, `IW_PLUGIN_DIRS` pointing at fixtures, mock plugin directory structure with `commands/` and `lib/`)
- [ ] [setup] Create mock plugin command fixtures: minimal `.scala` files with `@main` and `println` that can actually compile and run under scala-cli

## Tests (BATS E2E)

- [ ] [test] Basic execution: `iw testplugin/hello` finds and runs the plugin command, produces expected stdout output
- [ ] [test] Argument passing: `iw testplugin/hello arg1 arg2` passes arguments through — command prints received args
- [ ] [test] Core on classpath: plugin command imports from core (e.g., `iw.core.model.*`) and uses a core class successfully
- [ ] [test] Plugin lib on classpath: plugin command imports from plugin's `lib/` directory — lib defines an object, command calls it and prints result
- [ ] [test] Unknown plugin name: `iw nonexistent/hello` produces clear error message mentioning `--list` and exits non-zero
- [ ] [test] Unknown command in known plugin: `iw testplugin/nonexistent` produces clear error message mentioning `--list` and exits non-zero
- [ ] [test] Invalid syntax — multiple slashes: `iw foo/bar/baz` produces clear error about `<plugin>/<command>` format and exits non-zero
- [ ] [test] Invalid syntax — empty parts: `iw /hello` and `iw testplugin/` produce clear error and exit non-zero

## Implementation

- [ ] [impl] Add plugin command detection branch in `execute_command()` between project command (`./` prefix) and shared command branches: `[[ "$cmd_name" != ./* ]] && [[ "$cmd_name" == */* ]]`
- [ ] [impl] Add syntax validation: reject multiple slashes, empty parts, invalid characters (allow alphanumeric, dash, underscore only)
- [ ] [impl] Split `cmd_name` on `/` to extract `plugin_name` and `plugin_cmd_name`
- [ ] [impl] Call `discover_plugins()` to resolve `plugin_name` to `plugin_dir`; error with "Plugin 'X' not found" + `--list` suggestion if not found
- [ ] [impl] Resolve command file at `$plugin_dir/commands/${plugin_cmd_name}.scala`; error with "Command 'X' not found in plugin 'Y'" + `--list` suggestion if missing
- [ ] [impl] Assemble classpath: find core files (`$CORE_DIR` `*.scala`, excluding `test/` and `.scala-build/`) + find plugin lib files (`$plugin_dir/lib/*.scala`, excluding `.scala-build/`, if `lib/` exists)
- [ ] [impl] Execute: `cd "$PROJECT_DIR"` and `exec scala-cli run` with command file + core files + plugin lib files + `-- "$@"`

## Integration

- [ ] [verify] Run `plugin-commands-execute.bats` — all tests pass
- [ ] [verify] Run existing E2E tests (`./iw test e2e`) — no regressions
- [ ] [verify] Run unit tests (`./iw test unit`) — no regressions
- [ ] [verify] Compile core with `-Werror` — no warnings introduced
