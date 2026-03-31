# Implementation Log: Support Plugin Command Directories

Issue: IW-323

This log tracks the evolution of implementation across phases.

---

## Phase 1: Domain Constants (2026-03-30)

**Layer:** Domain (model/)

**What was built:**
- `Constants.EnvVars.IwPluginDirs` — env var name for plugin directory override
- `Constants.Paths.PluginsDir` — XDG path segment for plugin discovery
- `Constants.CommandHeaders` — new object with `Requires` field for version gating header

**Dependencies on other layers:**
- None — first phase, pure constants

**Testing:**
- Unit tests: 3 tests added (one per new constant)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260330-233458.md
- Result: Pass (no critical issues)

**Files changed:**
```
M	.iw/core/model/Constants.scala
M	.iw/core/test/ConstantsTest.scala
```

---

## Phase 2: Plugin Discovery and Listing (2026-03-31)

**Layer:** Infrastructure (iw-run shell script)

**What was built:**
- `discover_plugins()` — scans `IW_PLUGIN_DIRS` (colon-separated, first priority) then `$XDG_DATA_HOME/iw/plugins/*/` for plugin directories with `commands/` subdirs
- Extended `list_commands()` — plugin commands shown in `Plugin commands (<name>):` sections between shared and project commands, using `<plugin>/<command>` format
- Extended `describe_command()` — resolves `<plugin>/<command>` syntax, validates input, shows metadata with `Source: plugin (<name>)` attribution
- Plugin name validation against `^[a-zA-Z0-9_-]+$` in discovery and describe

**Dependencies on other layers:**
- Phase 1: Uses `IW_PLUGIN_DIRS` env var name (canonical: `Constants.EnvVars.IwPluginDirs`)
- Phase 1: Uses `plugins` path segment (canonical: `Constants.Paths.PluginsDir`)

**Testing:**
- E2E tests: 17 BATS tests added across 3 files
  - `plugin-discovery.bats` (6 tests): XDG discovery, env var discovery, error resilience, precedence
  - `plugin-commands-list.bats` (7 tests): section headers, format, hook/lib exclusion, ordering
  - `plugin-commands-describe.bats` (4 tests): full metadata, error cases

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20260331-111837.md
- Result: Pass (0 critical, 6 warnings addressed, 6 suggestions noted)

**Files changed:**
```
M	iw-run
A	.iw/test/plugin-commands-describe.bats
A	.iw/test/plugin-commands-list.bats
A	.iw/test/plugin-discovery.bats
```

---

## Phase 3: Plugin Command Execution (2026-03-31)

**Layer:** Infrastructure (iw-run shell script)

**What was built:**
- Extended `execute_command()` with new `elif` branch for `<plugin>/<command>` syntax (lines 439-504)
- Plugin name/command validation (alphanumeric, dash, underscore; single slash only)
- Plugin resolution via `discover_plugins()` (from Phase 2)
- Classpath assembly: core files + plugin `lib/*.scala` files
- Error handling: unknown plugin, unknown command, invalid syntax — all with `--list` suggestion

**Dependencies on other layers:**
- Phase 1: Uses `IW_PLUGIN_DIRS` env var name
- Phase 2: Uses `discover_plugins()` function for plugin directory resolution

**Testing:**
- E2E tests: 9 BATS tests added
  - `plugin-commands-execute.bats` (9 tests): basic execution, argument passing, core classpath, plugin lib classpath, unknown plugin error, unknown command error, invalid syntax (3 variants)

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20260331-124113.md
- Result: Pass (0 critical, 7 warnings — all pre-existing patterns or minor, 4 suggestions)

**Files changed:**
```
M	iw-run
A	.iw/test/plugin-commands-execute.bats
```

---
