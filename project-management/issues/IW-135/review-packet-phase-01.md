# Review Packet: Phase 1 - Query specific configuration value by field name

**Issue:** IW-135
**Phase:** 1 of 3
**Branch:** IW-135-phase-01
**Baseline:** c307aa7

## Goals

Implement the `iw config get <field>` command that:
1. Reads the project configuration from `.iw/config.conf`
2. Serializes it to JSON using upickle
3. Queries a specific field by name using ujson
4. Outputs the plain value to stdout
5. Returns appropriate exit codes (0 for success, 1 for not found)

## Scenarios (Acceptance Criteria)

- [x] `iw config get trackerType` returns "GitHub" for GitHub tracker config (exit 0)
- [x] `iw config get repository` returns repository value (exit 0)
- [x] `iw config get teamPrefix` returns team prefix value (exit 0)
- [x] `iw config get projectName` returns project name (exit 0)
- [x] `iw config get nonexistent` returns "Unknown configuration field" error (exit 1)
- [x] `iw config get youtrackBaseUrl` when unset returns "not set" error (exit 1)
- [x] `iw config get trackerType` without config file returns "Configuration not found" error (exit 1)
- [x] `iw config get trackerType` with Linear tracker returns "Linear" (exit 0)
- [x] `iw config get team` with Linear tracker returns team value (exit 0)

## Entry Points

Start your review with these files:

1. **`.iw/commands/config.scala`** - Main command implementation
   - `@main def config(args: String*)` - Entry point
   - `def handleGet(field: String)` - Core logic for field lookup

2. **`.iw/core/model/Config.scala`** - Added JSON serialization support
   - `object ProjectConfigurationJson` - upickle ReadWriter derivations (lines 231-240)

3. **`.iw/test/config.bats`** - E2E tests covering all scenarios

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     iw config get <field>                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   config.scala (@main)                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ args.toList match                                    │   │
│  │   case "get" :: field :: Nil => handleGet(field)    │   │
│  │   case _ => error + exit(1)                          │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      handleGet(field)                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 1. Build config path from Constants                  │   │
│  │ 2. ConfigFileRepository.read(configPath)             │   │
│  │    └── None → error "Configuration not found"        │   │
│  │    └── Some(config) → serialize to JSON              │   │
│  │ 3. ujson.read(json)(field)                           │   │
│  │    └── Success(null) → error "field is not set"      │   │
│  │    └── Success(value) → output value + exit(0)       │   │
│  │    └── Failure → check if optional field             │   │
│  │        └── known optional → "not set"                │   │
│  │        └── unknown → "Unknown configuration field"   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Config.scala (model layer)                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ object ProjectConfigurationJson:                     │   │
│  │   given ReadWriter[IssueTrackerType] = ...          │   │
│  │   given ReadWriter[ProjectConfiguration] = macroRW   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Data Flow

```
.iw/config.conf (HOCON)
        │
        ▼
ConfigFileRepository.read() → Option[ProjectConfiguration]
        │
        ▼
upickle.default.write(config) → JSON String
        │
        ▼
ujson.read(json)(field) → ujson.Value
        │
        ▼
Output.info(value) → stdout
```

## Test Summary

| Test Type | Count | Files |
|-----------|-------|-------|
| E2E (BATS) | 9 | `.iw/test/config.bats` |

**Test Coverage:**
- GitHub tracker configuration: 5 tests
- Linear tracker configuration: 2 tests
- Error conditions: 2 tests (missing config, unknown field)
- Optional field handling: 1 test

## Files Changed

| File | Change | Lines |
|------|--------|-------|
| `.iw/commands/config.scala` | Added | 53 |
| `.iw/core/model/Config.scala` | Modified | +11 |
| `.iw/test/config.bats` | Added | 208 |
| `project-management/issues/IW-135/phase-01-tasks.md` | Modified | checkboxes |

## Key Implementation Details

### 1. JSON Serialization (Config.scala)

```scala
object ProjectConfigurationJson:
  import upickle.default.*

  given ReadWriter[IssueTrackerType] = readwriter[String].bimap(
    _.toString,
    s => IssueTrackerType.valueOf(s)
  )

  given ReadWriter[ProjectConfiguration] = macroRW
```

**Design decision:** Place derivations in a separate object to avoid polluting the main Config namespace and to make the import explicit in commands that need it.

### 2. Field Lookup Logic (config.scala)

```scala
val optionalFields = Set("version", "youtrackBaseUrl", "repository", "teamPrefix")

Try(parsed(field)) match
  case Success(value) if value.isNull =>
    Output.error(s"Configuration field '$field' is not set")
    sys.exit(1)
  case Success(value) =>
    val outputValue = value match
      case str if str.isInstanceOf[ujson.Str] => str.str
      case num if num.isInstanceOf[ujson.Num] => num.num.toString
      case bool if bool.isInstanceOf[ujson.Bool] => bool.bool.toString
      case _ => value.toString
    Output.info(outputValue)
    sys.exit(0)
  case Failure(_) =>
    if optionalFields.contains(field) then
      Output.error(s"Configuration field '$field' is not set")
    else
      Output.error(s"Unknown configuration field: $field")
    sys.exit(1)
```

**Design decision:** Distinguish between unknown fields and known-but-unset optional fields to provide better error messages to users.

### 3. Type Handling

The command handles different JSON value types:
- `ujson.Str` → extract string value
- `ujson.Num` → convert to string
- `ujson.Bool` → convert to string
- Others → use toString (fallback)

## Questions for Reviewer

1. Is the approach of serializing to JSON and then querying with ujson acceptable, or should we use reflection/pattern matching on the case class directly?

2. Is the hardcoded `optionalFields` Set acceptable, or should we derive this from the `Option[_]` types in `ProjectConfiguration`?

3. Should the command output include a trailing newline? Currently it uses `Output.info()` which does include one.
