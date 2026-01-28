# Phase 2 Context: Export full configuration as JSON

**Issue:** IW-135
**Phase:** 2 of 3
**Story:** Export full configuration as JSON

## Goals

Implement the `iw config --json` flag that:
1. Reads the project configuration from `.iw/config.conf`
2. Serializes it to JSON using upickle (already implemented in Phase 1)
3. Outputs the complete JSON to stdout
4. Returns appropriate exit codes (0 for success, 1 for not found)

## What Was Built in Phase 1

- `.iw/commands/config.scala` - Command with `get` subcommand
- `.iw/core/model/Config.scala` - `ProjectConfigurationJson` object with upickle derivations:
  - `given ReadWriter[IssueTrackerType]`
  - `given ReadWriter[ProjectConfiguration]`
- Config loading via `ConfigFileRepository.read(configPath)`

## Scope

**In Scope:**
- Add `--json` flag to config command
- Output complete configuration as JSON
- Compact JSON output (single line, suitable for scripting)
- E2E tests for JSON output scenarios

**Out of Scope:**
- Pretty-printed JSON (users can pipe to `jq` if needed)
- Usage help improvements (Phase 3)

## Dependencies

**From Phase 1:**
- `ProjectConfigurationJson` with upickle derivations (already implemented)
- Config loading pattern in `handleGet`

**From Existing Codebase:**
- `ConfigFileRepository.read()` - reads config from disk
- `Output.info()` - outputs to stdout

## Technical Approach

### 1. Update Main Function Pattern Matching

```scala
@main def config(args: String*): Unit =
  args.toList match
    case "get" :: field :: Nil => handleGet(field)
    case "--json" :: Nil => handleJson()
    case _ =>
      Output.error("Usage: iw config get <field> | iw config --json")
      sys.exit(1)
```

### 2. Implement handleJson Function

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

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `.iw/commands/config.scala` | Modify | Add `--json` flag and `handleJson()` function |
| `.iw/test/config.bats` | Modify | Add E2E tests for `--json` flag |

## Testing Strategy

### E2E Tests (BATS)
```bash
# Test cases from Gherkin scenarios:
@test "config --json outputs valid JSON with GitHub config"
@test "config --json includes trackerType field"
@test "config --json includes repository field"
@test "config --json includes teamPrefix field"
@test "config --json without config file returns error"
@test "config --json with Linear config outputs valid JSON"
```

### Test Validation
- Use `jq` to validate JSON output
- Check for expected field values in JSON

## Acceptance Criteria

From the Gherkin spec:

1. **Export configuration as JSON**
   - Given valid `.iw/config.conf` with GitHub tracker
   - When `iw config --json`
   - Then output is valid JSON
   - And JSON contains `"trackerType":"GitHub"`
   - And JSON contains `"repository":"iterative-works/iw-cli"`
   - And JSON contains `"teamPrefix":"IW"`
   - And exit code is 0

2. **Export when no configuration exists**
   - Given no `.iw/config.conf`
   - When `iw config --json`
   - Then output contains "Configuration not found"
   - And exit code is 1

## Expected JSON Structure

```json
{
  "trackerType": "GitHub",
  "team": "",
  "projectName": "iw-cli",
  "version": "latest",
  "youtrackBaseUrl": null,
  "repository": "iterative-works/iw-cli",
  "teamPrefix": "IW"
}
```

**Notes:**
- Compact output (no pretty-printing)
- Optional fields serialize as `null` when not set
- `team` field is empty string for GitHub/GitLab trackers
- All fields from `ProjectConfiguration` are included

## Notes

- Reuse the `ProjectConfigurationJson` derivations from Phase 1
- Output should be compact (single line) for easy parsing by scripts
- Users can pipe to `jq` for pretty-printing: `iw config --json | jq .`
- Error handling matches Phase 1 pattern for missing config
