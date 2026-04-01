# Phase 4: Hooks and Version Checking

## Goals

Add two independent features to `iw-run`:

1. **Plugin hook discovery** -- Extend hook discovery so that (a) plugin `hooks/` directories are scanned when running shared (core) commands, and (b) project `.iw/commands/` is scanned for hooks when running plugin commands.
2. **`// REQUIRES:` version checking** -- Before executing any command, parse an optional `// REQUIRES: iw-cli >= X.Y.Z` header and gate execution on the installed version being sufficient.

After this phase, the plugin system supports full hook integration and version-gated commands.

## Scope

### In Scope

- Scan plugin `hooks/` directories for hook files when executing shared commands (third hook source alongside shared hooks and project hooks)
- Scan project `.iw/commands/` for hooks matching plugin command names when executing plugin commands
- Extract hook classes and pass `IW_HOOK_CLASSES` env var for both new hook paths
- Create `.iw/VERSION` file as the single source of truth for the version string, readable by bash
- Update `version.scala` to read from `.iw/VERSION` instead of hardcoding `iwVersion`
- Implement `check_version_requirement()` bash function in `iw-run`:
  - Parse `// REQUIRES: iw-cli >= X.Y.Z` from command `.scala` files
  - Compare semver (major.minor.patch only, no pre-release)
  - Fail with upgrade hint if version is too old
  - Warn and continue on malformed header
  - Pass silently if no header present
- Call version check before executing any command (shared, plugin, and project)
- Inline tests for the semver comparison function (bash function exercised by BATS)

### Out of Scope

- Plugin-to-plugin hook discovery (not supported per analysis)
- Full E2E test suite for hooks and version checking (Phase 5)
- Changes to `list_commands()` or `describe_command()` (done in Phase 2)
- Pre-release version tags or version ranges beyond `>=`

## Dependencies

- **Phase 1 (complete):** `Constants.CommandHeaders.Requires` constant defined
- **Phase 2 (complete):** `discover_plugins()` function for finding plugin directories
- **Phase 3 (complete):** Plugin command execution branch in `execute_command()` (lines 439-504)

## Approach

### Step 1: Create `.iw/VERSION` file

Create `.iw/VERSION` containing the bare version string (e.g., `0.3.7`). This file is the single source of truth, readable by both bash (`cat .iw/VERSION`) and Scala.

Update `version.scala` to read from this file instead of hardcoding `val iwVersion = "0.3.7"`. The Scala command can read the file at runtime from the `IW_COMMANDS_DIR` parent path.

### Step 2: Add `read_iw_version()` to `iw-run`

Simple function that reads the version from `$INSTALL_DIR/VERSION` (or `$IW_CORE_DIR/../VERSION` when `IW_CORE_DIR` is overridden in tests). Returns the version string.

### Step 3: Implement `compare_versions()` in `iw-run`

Pure semver comparison function for `major.minor.patch` strings. Returns 0 if first >= second, 1 otherwise. This function is independently testable.

Logic:
```bash
compare_versions() {
    local version_a="$1"
    local version_b="$2"
    # Split on dots, compare major, then minor, then patch numerically
    # Return 0 if a >= b, 1 otherwise
}
```

### Step 4: Implement `check_version_requirement()` in `iw-run`

Reads `// REQUIRES: iw-cli >= X.Y.Z` from the first N lines of a command file. Calls `compare_versions()` against the installed version. On failure, prints error and exits. On malformed header, warns and continues.

```bash
check_version_requirement() {
    local cmd_file="$1"
    local required_line
    required_line=$(grep "^// REQUIRES:" "$cmd_file" | head -1 || true)
    # Parse, validate, compare, fail or continue
}
```

### Step 5: Add plugin hook discovery to shared command execution

In the shared command branch of `execute_command()` (lines 506-568), after finding shared hooks and project hooks, add a third source: iterate over `discover_plugins()` results and scan each `$plugin_dir/hooks/` for `*.hook-${actual_name}.scala` files.

Current hook sources for shared commands:
1. `$COMMANDS_DIR/*.hook-${actual_name}.scala` (shared hooks)
2. `$PROJECT_DIR/.iw/commands/*.hook-${actual_name}.scala` (project hooks)

Add:
3. `$plugin_dir/hooks/*.hook-${actual_name}.scala` for each discovered plugin

### Step 6: Add project hook discovery to plugin command execution

In the plugin command branch of `execute_command()` (lines 439-504), add hook discovery from the project `.iw/commands/` directory for hooks matching the plugin command name.

Pattern: `$PROJECT_DIR/.iw/commands/*.hook-${plugin_cmd_name}.scala`

Extract hook classes and pass `IW_HOOK_CLASSES` env var, mirroring the shared command pattern.

### Step 7: Wire version checking into all execution paths

Call `check_version_requirement "$cmd_file"` before the `exec scala-cli` call in all three branches of `execute_command()`:
- Project command branch (line ~437)
- Plugin command branch (line ~504)
- Shared command branch (line ~567)

## Files to Modify

| File | Change |
|------|--------|
| `.iw/VERSION` | **New.** Contains bare version string `0.3.7` |
| `.iw/commands/version.scala` | Read version from `VERSION` file instead of hardcoding |
| `iw-run` | Add `read_iw_version()`, `compare_versions()`, `check_version_requirement()` functions |
| `iw-run` | Extend shared command branch: add plugin hooks discovery loop |
| `iw-run` | Extend plugin command branch: add project hook discovery + hook class extraction + `IW_HOOK_CLASSES` |
| `iw-run` | Call `check_version_requirement()` in all three execution branches |

## Code Reference

### Current shared command hook discovery (iw-run lines 525-548)

```bash
# Find shared hooks
local shared_hooks
shared_hooks=$(find "$COMMANDS_DIR" -maxdepth 1 -name "*.hook-${actual_name}.scala" 2>/dev/null || true)
hook_files="$shared_hooks"

# Find project hooks (if project commands directory exists and differs from shared)
local project_cmds_dir
project_cmds_dir=$(cd "$PROJECT_DIR/.iw/commands" 2>/dev/null && pwd || true)
local shared_cmds_dir
shared_cmds_dir=$(cd "$COMMANDS_DIR" 2>/dev/null && pwd || true)
if [ -d "$PROJECT_DIR/.iw/commands" ] && [ "$project_cmds_dir" != "$shared_cmds_dir" ]; then
    local project_hooks
    project_hooks=$(find "$PROJECT_DIR/.iw/commands" -maxdepth 1 -name "*.hook-${actual_name}.scala" 2>/dev/null || true)
    if [ -n "$project_hooks" ]; then
        if [ -n "$hook_files" ]; then
            hook_files="$hook_files $project_hooks"
        else
            hook_files="$project_hooks"
        fi
    fi
fi
```

### Current plugin command execution (iw-run lines 501-504, no hooks)

```bash
# Plugin commands: Execute with core library and plugin lib, NO hook discovery
cd "$PROJECT_DIR"
# shellcheck disable=SC2086
exec scala-cli run -q --suppress-outdated-dependency-warning "$cmd_file" $core_files $lib_files -- "$@"
```

### Hook class extraction and passing pattern (iw-run lines 551-567)

```bash
local hook_classes=""
if [ -n "$hook_files" ]; then
    hook_classes=$(extract_hook_classes "$hook_files")
fi

cd "$PROJECT_DIR"
IW_HOOK_CLASSES="$hook_classes" exec scala-cli run -q --suppress-outdated-dependency-warning "$cmd_file" $hook_files $core_files -- "$@"
```

### `extract_hook_classes()` function (iw-run lines 378-398)

Extracts object names matching `object XyzHookDoctor` from hook files. Returns comma-separated list.

### Existing hook file example (`.iw/commands/ci.hook-doctor.scala`)

```scala
// PURPOSE: Doctor checks for CI workflow configuration
// PURPOSE: Exposes checks to verify CI workflow file exists based on tracker type

import iw.core.model.*

object CIHookDoctor:
  val workflowExists: Check = Check("CI workflow", CIChecks.checkWorkflowExists, "Quality")
```

### `parse_command_header()` (iw-run lines 41-59)

Already parses PURPOSE, USAGE, ARGS, EXAMPLES. The REQUIRES header follows the same `// REQUIRES:` prefix pattern but is parsed separately by `check_version_requirement()` since it gates execution rather than displaying metadata.

### Current version source (`version.scala` line 10)

```scala
val iwVersion = "0.3.7"
```

## Testing Strategy

Phase 4 testing focuses on the independently testable bash functions. Full E2E scenarios (hook files actually compiled and executed, version gating with real plugin commands) belong to Phase 5.

### Inline BATS tests for `compare_versions()`

Create `.iw/test/version-check.bats` with unit-style tests for the pure comparison function:

- `0.3.7` >= `0.3.7` (equal versions, passes)
- `0.4.0` >= `0.3.7` (higher minor, passes)
- `1.0.0` >= `0.3.7` (higher major, passes)
- `0.3.6` >= `0.3.7` (lower patch, fails)
- `0.2.9` >= `0.3.7` (lower minor, fails)

### BATS tests for `check_version_requirement()`

- Command with `// REQUIRES: iw-cli >= 0.1.0` runs normally (satisfied)
- Command with `// REQUIRES: iw-cli >= 99.0.0` fails with error message containing upgrade hint
- Command without any REQUIRES header runs normally
- Command with malformed `// REQUIRES: garbage` warns but continues

### BATS tests for hook discovery additions

- Shared command discovers hooks from plugin `hooks/` directory (verify hook files are found)
- Plugin command discovers hooks from project `.iw/commands/` directory (verify hook files are found)
- Plugin without `hooks/` directory is silently skipped

These tests exercise the discovery logic. Full compilation+execution of hooks with real Scala code is deferred to Phase 5 E2E tests.

### VERSION file tests

- `read_iw_version()` returns the correct version from `.iw/VERSION`
- `iw version` still prints the correct version (regression check)

## Acceptance Criteria

- [ ] `.iw/VERSION` file exists and contains the version string
- [ ] `version.scala` reads from `.iw/VERSION` instead of hardcoding
- [ ] `iw version` still displays the correct version (regression)
- [ ] `compare_versions()` correctly compares semver `major.minor.patch` strings
- [ ] `check_version_requirement()` blocks execution when version is insufficient
- [ ] `check_version_requirement()` passes silently when no REQUIRES header exists
- [ ] `check_version_requirement()` warns and continues on malformed REQUIRES header
- [ ] Shared commands discover hooks from plugin `hooks/` directories
- [ ] Plugin commands discover hooks from project `.iw/commands/` directory
- [ ] Hook classes are extracted and passed via `IW_HOOK_CLASSES` for both new paths
- [ ] All existing E2E tests still pass (no regression)
- [ ] BATS tests for version comparison and hook discovery pass
