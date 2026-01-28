# Review Packet: Phase 3 - Validate command usage and provide help

**Issue:** IW-135
**Phase:** 3 of 3
**Branch:** IW-135-phase-03
**Baseline:** 5e00e79

## Goals

Improve the `iw config` command's user experience by providing helpful usage information and clear error messages.

## Scenarios (Acceptance Criteria)

- [x] `iw config` with no arguments shows usage (exit 1)
- [x] `iw config get` without field shows "Missing required argument" error (exit 1)
- [x] `iw config --invalid` shows "Unknown option" error (exit 1)
- [x] Usage output includes "iw config get <field>" and "iw config --json"
- [x] Usage output includes list of available field names with descriptions

## Entry Points

Start your review with these files:

1. **`.iw/commands/config.scala`** - Updated command implementation
   - Lines 10-14: Updated main function pattern matching
   - Lines 67-83: New `showUsage()` function
   - Lines 85-88: New `handleGetMissingField()` function
   - Lines 90-98: New `handleUnknownArgs()` function

2. **`.iw/test/config.bats`** - 5 new E2E tests for usage scenarios

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    iw config [args]                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   config.scala (@main)                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ args.toList match                                    │   │
│  │   case "get" :: field :: Nil => handleGet(field)    │   │
│  │   case "get" :: Nil => handleGetMissingField()      │ ← NEW
│  │   case "--json" :: Nil => handleJson()              │   │
│  │   case Nil => showUsage()                           │ ← NEW
│  │   case other => handleUnknownArgs(other)            │ ← NEW
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Test Summary

| Test Type | Count | Files |
|-----------|-------|-------|
| E2E (BATS) | 5 new (19 total) | `.iw/test/config.bats` |

**Test Coverage:**
- No arguments: 1 test
- Missing field argument: 1 test
- Unknown option: 1 test
- Usage content verification: 2 tests

## Files Changed

| File | Change | Lines |
|------|--------|-------|
| `.iw/commands/config.scala` | Modified | +35 |
| `.iw/test/config.bats` | Modified | +57 |
| `project-management/issues/IW-135/phase-03-tasks.md` | Modified | checkboxes |

## Key Implementation Details

### 1. showUsage Function

```scala
def showUsage(): Unit =
  Output.info("iw config - Query project configuration")
  Output.info("")
  Output.info("Usage:")
  Output.info("  iw config get <field>  Get a specific configuration field")
  Output.info("  iw config --json       Export full configuration as JSON")
  Output.info("")
  Output.info("Available fields:")
  Output.info("  trackerType     Issue tracker type (GitHub, GitLab, Linear, YouTrack)")
  Output.info("  team            Team identifier (Linear/YouTrack)")
  Output.info("  projectName     Project name")
  Output.info("  repository      Repository in owner/repo format (GitHub/GitLab)")
  Output.info("  teamPrefix      Issue ID prefix (GitHub/GitLab)")
  Output.info("  version         Tool version")
  Output.info("  youtrackBaseUrl Base URL for YouTrack/GitLab self-hosted")
  sys.exit(1)
```

### 2. Error Handling Functions

```scala
def handleGetMissingField(): Unit =
  Output.error("Missing required argument: <field>")
  Output.info("")
  showUsage()

def handleUnknownArgs(args: List[String]): Unit =
  args.headOption match
    case Some(arg) if arg.startsWith("--") =>
      Output.error(s"Unknown option: $arg")
    case Some(arg) =>
      Output.error(s"Unknown subcommand: $arg")
    case None => ()
  Output.info("")
  showUsage()
```

**Design decisions:**
- All error paths exit with code 1 (consistent with Unix conventions)
- Usage is shown after error messages for context
- Distinguishes between unknown options (`--foo`) and unknown subcommands (`foo`)
- Field list helps users discover available options without reading docs

## Example Output

```bash
$ iw config
iw config - Query project configuration

Usage:
  iw config get <field>  Get a specific configuration field
  iw config --json       Export full configuration as JSON

Available fields:
  trackerType     Issue tracker type (GitHub, GitLab, Linear, YouTrack)
  team            Team identifier (Linear/YouTrack)
  projectName     Project name
  repository      Repository in owner/repo format (GitHub/GitLab)
  teamPrefix      Issue ID prefix (GitHub/GitLab)
  version         Tool version
  youtrackBaseUrl Base URL for YouTrack/GitLab self-hosted

$ iw config --invalid
Error: Unknown option: --invalid

iw config - Query project configuration
...
```
