# Phase 2 Tasks: Plugin Discovery and Listing

**Issue:** IW-323
**Phase:** 2 - Plugin Discovery and Listing
**Status:** Not Started

## Setup

- [ ] [setup] Read `iw-run` script thoroughly, focusing on `list_commands()`, `describe_command()`, and `parse_command_header()` functions
- [ ] [setup] Create BATS test file `.iw/tests/e2e/plugin-discovery.bats` with setup/teardown scaffolding (temp dirs, `IW_SERVER_DISABLED=1`, mock plugin directory structure)
- [ ] [setup] Create BATS test file `.iw/tests/e2e/plugin-commands-list.bats` with setup/teardown scaffolding
- [ ] [setup] Create BATS test file `.iw/tests/e2e/plugin-commands-describe.bats` with setup/teardown scaffolding
- [ ] [setup] Create minimal mock plugin command fixtures (`.scala` files with `// PURPOSE:` and `// USAGE:` headers) for use across all test files

## Tests (BATS E2E) — Plugin Discovery

- [ ] [test] `plugin-discovery.bats`: Plugin found via XDG auto-discovery — set `XDG_DATA_HOME` to temp dir containing `iw/plugins/testplugin/commands/`, run `iw-run --list`, assert plugin section appears
- [ ] [test] `plugin-discovery.bats`: Plugin found via `IW_PLUGIN_DIRS` env var — set `IW_PLUGIN_DIRS` to temp dir containing `testplugin/commands/`, run `iw-run --list`, assert plugin section appears
- [ ] [test] `plugin-discovery.bats`: Non-existent plugin dir in `IW_PLUGIN_DIRS` is skipped without error — set `IW_PLUGIN_DIRS` to non-existent path, run `iw-run --list`, assert no error and exit 0
- [ ] [test] `plugin-discovery.bats`: Plugin without `commands/` subdirectory is skipped — create plugin dir with no `commands/` subdir, assert no plugin section in output
- [ ] [test] `plugin-discovery.bats`: Empty plugins directory produces no plugin sections — create empty `iw/plugins/` dir, assert output has no `Plugin commands` header
- [ ] [test] `plugin-discovery.bats`: `IW_PLUGIN_DIRS` takes precedence over XDG for same plugin name — create same-named plugin in both locations with different commands, assert the `IW_PLUGIN_DIRS` version is used

## Tests (BATS E2E) — Plugin Command Listing

- [ ] [test] `plugin-commands-list.bats`: Plugin commands shown in separate section with `Plugin commands (testplugin):` header
- [ ] [test] `plugin-commands-list.bats`: Commands listed as `testplugin/<command>` format (e.g., `testplugin/implement`)
- [ ] [test] `plugin-commands-list.bats`: Plugin name derived from directory basename
- [ ] [test] `plugin-commands-list.bats`: Hook files (`*.hook-*.scala`) excluded from plugin command listing
- [ ] [test] `plugin-commands-list.bats`: `lib/` directory files excluded from plugin command listing
- [ ] [test] `plugin-commands-list.bats`: Multiple plugins shown in separate sections
- [ ] [test] `plugin-commands-list.bats`: Plugin commands appear between shared and project command sections in output

## Tests (BATS E2E) — Plugin Command Describe

- [ ] [test] `plugin-commands-describe.bats`: `--describe testplugin/implement` shows purpose, usage, and `Source: plugin (testplugin)` line
- [ ] [test] `plugin-commands-describe.bats`: Unknown plugin name gives clear error message and non-zero exit
- [ ] [test] `plugin-commands-describe.bats`: Unknown command within known plugin gives clear error message and non-zero exit
- [ ] [test] `plugin-commands-describe.bats`: Invalid plugin/command syntax (e.g., `foo/bar/baz`) gives clear error

## Implementation

- [ ] [impl] Add `discover_plugins()` function to `iw-run`: scan `${XDG_DATA_HOME:-$HOME/.local/share}/iw/plugins/*/` for plugin directories, then scan `IW_PLUGIN_DIRS` (colon-separated) entries; skip dirs without `commands/` subdir; produce `plugin_name:plugin_dir` pairs; `IW_PLUGIN_DIRS` entries scanned first (takes precedence for duplicate names)
- [ ] [impl] Extend `list_commands()` in `iw-run`: after shared commands and before project commands, call `discover_plugins()` and for each plugin, print `Plugin commands (<name>):` header, iterate `commands/*.scala` (skipping hook files and `lib/`), display as `<plugin>/<command>` with purpose/usage from `parse_command_header()`
- [ ] [impl] Extend `describe_command()` in `iw-run`: detect `<plugin>/<command>` syntax (contains `/` but does not start with `./`), split on first `/`, validate both parts (alphanumeric/dash/underscore), use `discover_plugins()` to find plugin dir, resolve to `<plugin_dir>/commands/<command>.scala`, display with `Source: plugin (<name>)` line
- [ ] [impl] Add error handling for describe: unknown plugin name, unknown command within known plugin, invalid syntax — each with a distinct error message

## Integration

- [ ] [verify] Run all plugin-discovery.bats tests — all pass
- [ ] [verify] Run all plugin-commands-list.bats tests — all pass
- [ ] [verify] Run all plugin-commands-describe.bats tests — all pass
- [ ] [verify] Run existing E2E tests (if any) to confirm no regressions
- [ ] [verify] Run `./iw test unit` to confirm no regressions in Scala unit tests
- [ ] [verify] Compile core with `-Werror` to confirm no warnings introduced
