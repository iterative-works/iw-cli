# Phase 3 Context: Validate command usage and provide help

**Issue:** IW-135
**Phase:** 3 of 3
**Story:** Validate command usage and provide help

## Goals

Improve the `iw config` command's user experience by:
1. Showing helpful usage information when called without arguments
2. Providing clear error messages for incorrect usage
3. Displaying available field names for `get` subcommand

## What Was Built in Previous Phases

**Phase 1:**
- `iw config get <field>` - Query specific configuration field
- Error handling for missing config, unknown fields, unset optional fields

**Phase 2:**
- `iw config --json` - Export full config as JSON
- Updated usage error message

**Current state of main function:**
```scala
@main def config(args: String*): Unit =
  args.toList match
    case "get" :: field :: Nil => handleGet(field)
    case "--json" :: Nil => handleJson()
    case _ =>
      Output.error("Usage: iw config get <field> | iw config --json")
      sys.exit(1)
```

## Scope

**In Scope:**
- Show usage information when `iw config` called without arguments (exit 1)
- Show error for `iw config get` without field argument (exit 1)
- Show error for unknown flags like `--invalid` (exit 1)
- Include list of available field names in usage text
- E2E tests for all usage scenarios

**Out of Scope:**
- `--help` flag (follow existing iw-cli conventions)
- Man page or external documentation

## Dependencies

**From Previous Phases:**
- Existing command structure with `get` and `--json`
- `Output` module for formatting

## Technical Approach

### 1. Update Main Function Pattern Matching

```scala
@main def config(args: String*): Unit =
  args.toList match
    case "get" :: field :: Nil => handleGet(field)
    case "get" :: Nil => handleGetMissingField()
    case "--json" :: Nil => handleJson()
    case Nil => showUsage()
    case other => handleUnknownArgs(other)
```

### 2. Implement Helper Functions

```scala
def showUsage(): Unit =
  Output.info("iw config - Query project configuration")
  Output.info("")
  Output.info("Usage:")
  Output.info("  iw config get <field>  Get a specific configuration field")
  Output.info("  iw config --json       Export full configuration as JSON")
  Output.info("")
  Output.info("Available fields:")
  Output.info("  trackerType    Issue tracker type (GitHub, GitLab, Linear, YouTrack)")
  Output.info("  team           Team identifier (Linear/YouTrack)")
  Output.info("  projectName    Project name")
  Output.info("  repository     Repository in owner/repo format (GitHub/GitLab)")
  Output.info("  teamPrefix     Issue ID prefix (GitHub/GitLab)")
  Output.info("  version        Tool version")
  Output.info("  youtrackBaseUrl Base URL for YouTrack/GitLab self-hosted")
  sys.exit(1)

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

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `.iw/commands/config.scala` | Modify | Add usage functions and update pattern matching |
| `.iw/test/config.bats` | Modify | Add E2E tests for usage scenarios |

## Testing Strategy

### E2E Tests (BATS)
```bash
# Test cases from Gherkin scenarios:
@test "config with no arguments shows usage"
@test "config get without field shows missing argument error"
@test "config --invalid shows unknown option error"
@test "config invalid-subcommand shows unknown subcommand error"
@test "config usage includes available fields list"
```

## Acceptance Criteria

From the Gherkin spec:

1. **No arguments provided**
   - When `iw config`
   - Then output shows usage information
   - And usage includes "iw config get <field>"
   - And usage includes "iw config --json"
   - And exit code is 1

2. **Get command without field argument**
   - When `iw config get`
   - Then output contains "Missing required argument: <field>"
   - And output shows usage information
   - And exit code is 1

3. **Invalid flag provided**
   - When `iw config --invalid`
   - Then output contains "Unknown option: --invalid"
   - And output shows usage information
   - And exit code is 1

## Notes

- Usage text should be concise but complete
- Follow existing iw-cli command conventions for error messages
- All error paths exit with code 1
- Include field descriptions to help users discover available options
