# Refactoring R1: Fix path handling and build system

**Phase:** 7
**Created:** 2025-12-20
**Status:** Complete

## Decision Summary

When testing the dashboard server, it crashed with `os.PathError$InvalidSegment` because `Constants.Paths.ConfigFile = ".iw/config.conf"` contains a `/` character, which `os-lib` doesn't allow in a single path segment.

Additionally, command scripts have `//> using file` directives that conflict with the glob-based approach already used by the `./iw` wrapper. The wrapper passes `"$CORE_DIR"/*.scala` but command scripts also declare their own file dependencies, causing confusion and including test files that require munit.

## Current State

### Path Constants Issue

**File:** `.iw/core/Constants.scala` line 25
```scala
val ConfigFile = ".iw/config.conf"  // Contains "/" - invalid for os-lib segment
```

**File:** `.iw/core/CaskServer.scala` line 32
```scala
val configPath = os.pwd / Constants.Paths.ConfigFile  // Crashes!
```

### Build System Issue

**File:** `./iw` wrapper (line 166) - GOOD approach:
```bash
exec scala-cli run "$cmd_file" $hook_files "$CORE_DIR"/*.scala -- "$@"
```

**Problem:** Command scripts ALSO have `//> using file` directives that:
1. Conflict with the wrapper's glob approach
2. Use relative paths that break when resolved from wrong directory

**File:** `.iw/commands/server.scala` (lines 7-13) - REMOVE these:
```scala
//> using file .iw/core/ServerConfig.scala
//> using file .iw/core/ServerConfigRepository.scala
...
```

**File:** `.iw/commands/server-daemon.scala` (lines 7-14) - REMOVE these:
```scala
//> using file .iw/core/CaskServer.scala
...
```

**File:** `.iw/commands/dashboard.scala` (lines 5-6) - REMOVE these:
```scala
//> using file "../core/project.scala"
//> using file "../core"
```

**Additional issue:** The glob `"$CORE_DIR"/*.scala` includes test files in `.iw/core/test/` which require munit dependency not available at runtime.

## Target State

### Path Constants

Split paths into components that work with `os-lib`:

```scala
object Paths:
  val IwDir = ".iw"
  val ConfigFileName = "config.conf"
  // Usage: os.pwd / Paths.IwDir / Paths.ConfigFileName
```

### Build System

1. **Remove ALL `//> using file` directives** from command scripts
2. **Keep `project.scala`** with dependency declarations (each command can reference it or declare own deps)
3. **Update `./iw` wrapper** to exclude test files from glob:
   ```bash
   # Find core files excluding test directory
   CORE_FILES=$(find "$CORE_DIR" -maxdepth 1 -name "*.scala" | tr '\n' ' ')
   exec scala-cli run "$cmd_file" $hook_files $CORE_FILES -- "$@"
   ```

## Constraints

- PRESERVE: All existing functionality must work unchanged
- PRESERVE: All tests must pass (run via `./iw test`, not directly)
- PRESERVE: `./iw` wrapper script invocation pattern
- DO NOT TOUCH: Business logic in any file
- DO NOT TOUCH: Test files

## Tasks

### Path Handling

- [x] [impl] [Refactor] Update Constants.Paths: split ConfigFile into IwDir + ConfigFileName
- [x] [impl] [Refactor] Update CaskServer.scala line 32 to use `os.pwd / Paths.IwDir / Paths.ConfigFileName`
- [x] [impl] [Verify] Run unit tests to ensure no regressions

### Build System - Command Scripts

- [x] [impl] [Refactor] Remove `//> using file` directives from dashboard.scala
- [x] [impl] [Refactor] Remove `//> using file` directives from server.scala
- [x] [impl] [Refactor] Remove `//> using file` directives from server-daemon.scala
- [x] [impl] [Analysis] Check all other command scripts for `//> using file` directives
- [x] [impl] [Refactor] Remove `//> using file` directives from any other commands found (none found)

### Build System - Wrapper Script

- [x] [impl] [Refactor] Update `./iw` wrapper to exclude test directory from glob
- [x] [impl] [Verify] Test `./iw server status` works correctly
- [x] [impl] [Verify] Test `./iw dashboard` opens without crashes
- [x] [impl] [Verify] Test `./iw test` still runs tests correctly

## Verification

- [x] All existing unit tests pass via `./iw test unit`
- [x] `./iw server start` starts the server successfully
- [x] `./iw server status` shows correct status
- [x] `./iw dashboard` opens dashboard without crashes
- [x] Dashboard displays worktrees correctly (path bug fixed)
- [ ] No regressions in other commands (`./iw start`, `./iw open`, etc.) - E2E tests have pre-existing failures

## Notes

- The `./iw` wrapper handles all file discovery - commands don't need to declare dependencies
- `project.scala` stays as the central dependency declaration
- Test files are only included when running `./iw test` (which has its own invocation)
- Commands can still have their own `//> using dep` for command-specific dependencies
