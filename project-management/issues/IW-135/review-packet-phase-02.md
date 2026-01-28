# Review Packet: Phase 2 - Export full configuration as JSON

**Issue:** IW-135
**Phase:** 2 of 3
**Branch:** IW-135-phase-02
**Baseline:** 7e3460d

## Goals

Implement the `iw config --json` flag that outputs the complete project configuration as JSON.

## Scenarios (Acceptance Criteria)

- [x] `iw config --json` outputs valid JSON with GitHub config
- [x] JSON includes `trackerType` field with correct value ("GitHub")
- [x] JSON includes `repository` field
- [x] `iw config --json` without config file returns "Configuration not found" error (exit 1)
- [x] `iw config --json` with Linear config outputs valid JSON

## Entry Points

Start your review with these files:

1. **`.iw/commands/config.scala`** - Updated command implementation
   - Line 13: New pattern match for `--json` flag
   - Lines 54-66: New `handleJson()` function

2. **`.iw/test/config.bats`** - 5 new E2E tests for JSON output

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     iw config --json                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   config.scala (@main)                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ args.toList match                                    │   │
│  │   case "get" :: field :: Nil => handleGet(field)    │   │
│  │   case "--json" :: Nil => handleJson()              │ ← NEW
│  │   case _ => error + exit(1)                          │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      handleJson()                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 1. Build config path from Constants                  │   │
│  │ 2. ConfigFileRepository.read(configPath)             │   │
│  │    └── None → error "Configuration not found"        │   │
│  │    └── Some(config) → serialize to JSON              │   │
│  │ 3. upickle.default.write(config) → JSON string       │   │
│  │ 4. Output.info(json) + exit(0)                       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Test Summary

| Test Type | Count | Files |
|-----------|-------|-------|
| E2E (BATS) | 5 new (14 total) | `.iw/test/config.bats` |

**Test Coverage:**
- Valid JSON output: 2 tests
- Field content verification: 2 tests
- Error handling: 1 test
- Linear tracker: 1 test

## Files Changed

| File | Change | Lines |
|------|--------|-------|
| `.iw/commands/config.scala` | Modified | +14 |
| `.iw/test/config.bats` | Modified | +69 |
| `project-management/issues/IW-135/phase-02-tasks.md` | Modified | checkboxes |

## Key Implementation Details

### 1. handleJson Function (config.scala)

```scala
def handleJson(): Unit =
  val configPath = os.Path(System.getProperty(Constants.SystemProps.UserDir)) / Constants.Paths.IwDir / Constants.Paths.ConfigFileName

  ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("Configuration not found. Run 'iw init' first.")
      sys.exit(1)
    case Some(config) =>
      import upickle.default.*
      val json = write(config)
      Output.info(json)
      sys.exit(0)
```

**Design decisions:**
- Reuses `ProjectConfigurationJson` derivations from Phase 1
- Outputs compact JSON (single line) - users can pipe to `jq` for formatting
- Error handling matches `handleGet` pattern for consistency

### 2. Pattern Matching Update

```scala
args.toList match
  case "get" :: field :: Nil => handleGet(field)
  case "--json" :: Nil => handleJson()  // NEW
  case _ =>
    Output.error("Usage: iw config get <field> | iw config --json")
    sys.exit(1)
```

## Example Output

```bash
$ iw config --json
{"trackerType":"GitHub","team":"","projectName":"iw-cli","repository":"iterative-works/iw-cli","teamPrefix":"IW"}

$ iw config --json | jq .
{
  "trackerType": "GitHub",
  "team": "",
  "projectName": "iw-cli",
  "repository": "iterative-works/iw-cli",
  "teamPrefix": "IW"
}
```
