# Phase 2: Plugin Discovery and Listing

## Goals

Extend `iw-run` so that plugin command directories are discovered and their commands appear in `--list` and `--describe` output. After this phase, users can see what plugin commands are available, but cannot yet execute them.

## Scope

### In Scope
- `discover_plugins()` function: scans XDG and `IW_PLUGIN_DIRS` env var for plugin directories
- Extend `list_commands()`: show plugin commands grouped by plugin name (`Plugin commands (kanon):` section) with `kanon/<cmd>` entries, between shared and project command sections
- Extend `describe_command()`: resolve `<plugin>/<command>` syntax, show purpose/usage/args/examples and source attribution
- Skip plugins without a `commands/` subdirectory
- Exclude hook files (`*.hook-*.scala`) from plugin command listings
- Exclude `lib/` files from plugin command listings
- BATS E2E tests for discovery and listing

### Out of Scope
- Command execution (`execute_command()` changes) — Phase 3
- Plugin `lib/` classpath inclusion — Phase 3
- Hook discovery from plugin `hooks/` directories — Phase 4
- `// REQUIRES:` version checking — Phase 4
- Plugin-to-plugin interactions — not planned

## Dependencies

- **Phase 1 (complete):** Constants in `Constants.scala` define canonical names:
  - `Constants.EnvVars.IwPluginDirs` = `"IW_PLUGIN_DIRS"`
  - `Constants.Paths.PluginsDir` = `"plugins"`
  - `Constants.CommandHeaders.Requires` = `"REQUIRES"`
- The bash script uses string literals directly (bash cannot import Scala constants). The Scala constants serve as the canonical source of truth for documentation and cross-language consistency.

## Approach

### Step 1: Add `discover_plugins()` function

New function in `iw-run` that returns plugin name + directory pairs.

Discovery logic:
1. Determine XDG data dir: `${XDG_DATA_HOME:-$HOME/.local/share}`
2. Scan `$xdg_data_home/iw/plugins/*/` for subdirectories (follows symlinks)
3. If `IW_PLUGIN_DIRS` is set (colon-separated), scan each entry for subdirectories (each entry is a parent dir containing plugin subdirectories, same structure as the XDG plugins dir)
4. For each candidate: skip if it has no `commands/` subdirectory
5. Produce a list of `plugin_name:plugin_dir` pairs

Design notes:
- Plugin name is the directory basename (e.g., `kanon` from `.../plugins/kanon/`)
- `IW_PLUGIN_DIRS` is additive on top of XDG, not a replacement (dev convenience)
- If the same plugin name appears in both XDG and `IW_PLUGIN_DIRS`, `IW_PLUGIN_DIRS` takes precedence (first match wins, env var scanned first)

### Step 2: Extend `list_commands()` with plugin section

After listing shared commands and before listing project commands, insert plugin command sections:

```
Plugin commands (kanon):

Command: kanon/implement
Purpose: ...
Usage:   ...

Command: kanon/batch-implement
Purpose: ...
Usage:   ...
```

For each discovered plugin:
- Print `Plugin commands (<name>):` header
- Iterate `commands/*.scala` in the plugin directory
- Skip hook files (`*.hook-*.scala`)
- Display as `<plugin>/<command>` (e.g., `kanon/implement`)
- Use existing `parse_command_header()` for PURPOSE and USAGE

### Step 3: Extend `describe_command()` with plugin command support

Detect `<plugin>/<command>` syntax (contains `/` but does not start with `./`):
- Split on `/` to get plugin name and command name
- Validate both parts (alphanumeric, dash, underscore)
- Use `discover_plugins()` to find the plugin directory
- Resolve to `<plugin_dir>/commands/<command>.scala`
- Display with `=== Command: kanon/implement ===` header
- Add `Source: plugin (kanon)` line to output

### Step 4: Write BATS E2E tests

Two test files covering discovery and listing:

**`plugin-discovery.bats`:**
- Plugin found via XDG auto-discovery (mock `XDG_DATA_HOME`)
- Plugin found via `IW_PLUGIN_DIRS` env var
- Non-existent plugin dir is skipped (no error)
- Plugin without `commands/` subdirectory is skipped
- Empty plugins directory produces no plugin sections

**`plugin-commands-list.bats`:**
- Plugin commands shown in separate section with `kanon/<command>` format
- Plugin name derived from directory basename
- Hook files excluded from listing
- Multiple plugins shown in separate sections
- `lib/` files excluded from listing

**`plugin-commands-describe.bats`:**
- `--describe kanon/implement` shows purpose, usage, source
- Unknown plugin name gives clear error
- Unknown command within known plugin gives clear error
- Invalid plugin/command syntax gives clear error

Test infrastructure:
- Each test creates temp dirs with mock plugin structures
- Mock plugin commands are minimal `.scala` files with `// PURPOSE:` and `// USAGE:` headers (no need to actually compile)
- Tests export `IW_SERVER_DISABLED=1` in `setup()`
- Override `XDG_DATA_HOME` and/or `IW_PLUGIN_DIRS` to point at temp fixtures

## Files to Modify

- **`iw-run`** — add `discover_plugins()`, extend `list_commands()` and `describe_command()`
- **`.iw/tests/e2e/plugin-discovery.bats`** — new: plugin directory scanning tests
- **`.iw/tests/e2e/plugin-commands-list.bats`** — new: `--list` with plugin commands
- **`.iw/tests/e2e/plugin-commands-describe.bats`** — new: `--describe` with plugin commands

## Testing Strategy

All testing is BATS E2E, following existing patterns from `project-commands-*.bats`.

Each test file:
1. Creates a temp directory structure with mock plugins in `setup()`
2. Sets `XDG_DATA_HOME` or `IW_PLUGIN_DIRS` to point at the fixture
3. Runs `iw-run --list` or `iw-run --describe` and asserts on output
4. Cleans up in `teardown()`

No Scala compilation is needed for listing/describing — the tests only need `.scala` files with comment headers.

Key patterns:
- `IW_SERVER_DISABLED=1` in every `setup()`
- Temp dirs via BATS `$BATS_TEST_TMPDIR` or `mktemp -d`
- Assert on stdout content with `[[ "$output" == *"expected"* ]]` or `grep`

## Acceptance Criteria

- [ ] `discover_plugins()` finds plugins under `$XDG_DATA_HOME/iw/plugins/*/`
- [ ] `discover_plugins()` finds plugins via `$IW_PLUGIN_DIRS` colon-separated paths
- [ ] Plugins without `commands/` subdirectory are silently skipped
- [ ] `iw --list` shows plugin commands in `Plugin commands (<name>):` sections
- [ ] Plugin commands listed as `<plugin>/<command>` (e.g., `kanon/implement`)
- [ ] Hook files (`*.hook-*.scala`) excluded from plugin command listings
- [ ] `lib/` directory files excluded from plugin command listings
- [ ] `iw --describe kanon/implement` resolves and displays plugin command details
- [ ] `iw --describe kanon/implement` shows source attribution (plugin name)
- [ ] Unknown plugin or command in describe gives clear error message
- [ ] All existing E2E tests still pass (no regression)
- [ ] BATS tests for plugin discovery pass
- [ ] BATS tests for plugin command listing pass
- [ ] BATS tests for plugin command describe pass
