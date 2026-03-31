# Phase 4 Tasks: Hooks and Version Checking

**Issue:** IW-323
**Phase:** 4 - Hooks and Version Checking
**Status:** Complete

## Setup

- [x] [setup] Read current `iw-run` to understand `execute_command()` branches, hook discovery pattern, and `extract_hook_classes()` function
- [x] [setup] Read `.iw/commands/version.scala` to understand current hardcoded version
- [x] [setup] Create `.iw/VERSION` file containing bare version string `0.3.7`
- [x] [setup] Create BATS test file `.iw/test/version-check.bats` with setup/teardown scaffolding (temp dirs, `IW_SERVER_DISABLED=1`, source `iw-run` functions)
- [x] [setup] Create BATS test file `.iw/test/plugin-hooks.bats` with setup/teardown scaffolding (temp dirs, `IW_SERVER_DISABLED=1`, `IW_PLUGIN_DIRS` pointing at fixtures, mock plugin with `hooks/` directory)

## Tests — Version File and Reading

- [x] [test] `read_iw_version()` returns correct version string from `.iw/VERSION`
- [x] [test] `read_iw_version()` trims whitespace/newlines from VERSION file

## Tests — Semver Comparison

- [x] [test] `compare_versions` equal versions: `0.3.7` >= `0.3.7` returns 0 (pass)
- [x] [test] `compare_versions` higher patch: `0.3.8` >= `0.3.7` returns 0 (pass)
- [x] [test] `compare_versions` higher minor: `0.4.0` >= `0.3.7` returns 0 (pass)
- [x] [test] `compare_versions` higher major: `1.0.0` >= `0.3.7` returns 0 (pass)
- [x] [test] `compare_versions` lower patch: `0.3.6` >= `0.3.7` returns 1 (fail)
- [x] [test] `compare_versions` lower minor: `0.2.9` >= `0.3.7` returns 1 (fail)
- [x] [test] `compare_versions` lower major: `0.3.7` >= `1.0.0` returns 1 (fail)

## Tests — Version Requirement Checking

- [x] [test] `check_version_requirement` with `// REQUIRES: iw-cli >= 0.1.0` passes silently (version satisfied)
- [x] [test] `check_version_requirement` with `// REQUIRES: iw-cli >= 99.0.0` fails with error containing upgrade hint
- [x] [test] `check_version_requirement` with no REQUIRES header passes silently
- [x] [test] `check_version_requirement` with malformed `// REQUIRES: garbage` warns but does not fail

## Tests — Hook Discovery

- [x] [test] Shared command execution discovers hook files from plugin `hooks/` directory (hook files included in classpath)
- [x] [test] Plugin command execution discovers hook files from project `.iw/commands/` directory
- [x] [test] Plugin without `hooks/` directory is silently skipped during shared command hook discovery
- [x] [test] Hook classes from plugin hooks are extracted and passed in `IW_HOOK_CLASSES` env var

## Implementation — Version Infrastructure

- [x] [impl] Add `read_iw_version()` function to `iw-run`: reads `$INSTALL_DIR/VERSION`, trims whitespace, returns version string
- [x] [impl] Add `compare_versions()` function to `iw-run`: splits on dots, compares major/minor/patch numerically, returns 0 if a >= b, 1 otherwise
- [x] [impl] Add `check_version_requirement()` function to `iw-run`: greps `// REQUIRES:` from command file, parses `iw-cli >= X.Y.Z`, calls `compare_versions()`, fails with upgrade hint or warns on malformed header
- [x] [impl] Update `version.scala` to read version from `.iw/VERSION` file instead of hardcoding `iwVersion`

## Implementation — Hook Discovery

- [x] [impl] Extend shared command branch in `execute_command()`: after existing hook sources, iterate `discover_plugins()` results and scan each `$plugin_dir/hooks/*.hook-${actual_name}.scala`
- [x] [impl] Extend plugin command branch in `execute_command()`: scan `$PROJECT_DIR/.iw/commands/*.hook-${plugin_cmd_name}.scala` for hooks, extract hook classes, pass `IW_HOOK_CLASSES` env var

## Implementation — Wiring

- [x] [impl] Call `check_version_requirement "$cmd_file"` before `exec scala-cli` in project command branch
- [x] [impl] Call `check_version_requirement "$cmd_file"` before `exec scala-cli` in plugin command branch
- [x] [impl] Call `check_version_requirement "$cmd_file"` before `exec scala-cli` in shared command branch

## Integration

- [x] [integration] Run `version-check.bats` — all tests pass
- [x] [integration] Run `plugin-hooks.bats` — all tests pass
- [x] [integration] Run `iw version` — still displays correct version (regression)
- [x] [integration] Run existing E2E tests (`./iw test e2e`) — no regressions
- [x] [integration] Run unit tests (`./iw test unit`) — no regressions
- [x] [integration] Compile core with `-Werror` — no warnings introduced
