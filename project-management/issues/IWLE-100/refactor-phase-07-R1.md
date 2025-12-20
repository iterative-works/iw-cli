# Refactoring R1: Fix path handling and build system

**Phase:** 7
**Created:** 2025-12-20
**Status:** Planned

## Decision Summary

When testing the dashboard server, it crashed with `os.PathError$InvalidSegment` because `Constants.Paths.ConfigFile = ".iw/config.conf"` contains a `/` character, which `os-lib` doesn't allow in a single path segment.

Additionally, the `//> using file` directives in command scripts use relative paths that resolve incorrectly when scripts are run from different directories than expected. This causes compile errors like "File not found: .iw/commands/.iw/core/ServerConfig.scala" because the relative paths are resolved from the script location.

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

**File:** `.iw/commands/server.scala` (lines 7-13)
```scala
//> using file .iw/core/ServerConfig.scala
//> using file .iw/core/ServerConfigRepository.scala
// ... more relative paths that break when cwd differs
```

**File:** `.iw/commands/server-daemon.scala` (lines 7-14)
```scala
//> using file .iw/core/CaskServer.scala
// ... same issue
```

**File:** `.iw/commands/dashboard.scala` (lines 5-6)
```scala
//> using file "../core/project.scala"
//> using file "../core"
```

## Target State

### Path Constants

Split paths into components that work with `os-lib`:

```scala
object Paths:
  val IwDir = ".iw"
  val ConfigFileName = "config.conf"
  // For building paths: os.pwd / Paths.IwDir / Paths.ConfigFileName
```

### Build System

Option A: Use `//> using files` with proper paths from project root
Option B: Remove `//> using file` and use scala-cli classpath arguments

Preferred approach: **Option A** - Standardize on using `../core` pattern from commands directory, ensuring scripts are always run via `./iw` wrapper.

## Constraints

- PRESERVE: All existing functionality must work unchanged
- PRESERVE: All tests must pass
- PRESERVE: `./iw` wrapper script invocation pattern
- DO NOT TOUCH: Business logic in any file
- DO NOT TOUCH: Test files (unless imports change)

## Tasks

### Path Handling

- [ ] [impl] [Analysis] Identify all usages of Constants.Paths in codebase
- [ ] [impl] [Refactor] Update Constants.Paths.ConfigFile to separate IwDir and ConfigFileName
- [ ] [impl] [Refactor] Update CaskServer.scala to use proper path construction
- [ ] [impl] [Verify] Run unit tests to ensure no regressions

### Build System

- [ ] [impl] [Analysis] Document current `//> using file` patterns in all command scripts
- [ ] [impl] [Refactor] Standardize dashboard.scala pattern (`../core/project.scala`, `../core`)
- [ ] [impl] [Refactor] Update server.scala to use standardized pattern
- [ ] [impl] [Refactor] Update server-daemon.scala to use standardized pattern
- [ ] [impl] [Verify] Test `./iw server status` works correctly
- [ ] [impl] [Verify] Test `./iw dashboard` works correctly
- [ ] [impl] [Cleanup] Remove any dead or duplicate using directives

## Verification

- [ ] All existing unit tests pass
- [ ] `./iw server start` starts the server successfully
- [ ] `./iw server status` shows correct status
- [ ] `./iw dashboard` opens dashboard without crashes
- [ ] Dashboard displays worktrees correctly (path bug fixed)
- [ ] No regressions in other commands

## Notes

- The `./iw` wrapper script should handle working directory setup
- All command scripts should assume they're run from project root via `./iw`
- The `../core` pattern works because commands are in `.iw/commands/` directory
- Consider adding a check/assertion for working directory at startup
