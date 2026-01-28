# Phase 1 Context: Query specific configuration value by field name

**Issue:** IW-135
**Phase:** 1 of 3
**Story:** Query specific configuration value by path

## Goals

Implement the `iw config get <field>` command that:
1. Reads the project configuration from `.iw/config.conf`
2. Serializes it to JSON using upickle
3. Queries a specific field by name using ujson
4. Outputs the plain value to stdout
5. Returns appropriate exit codes (0 for success, 1 for not found)

## Scope

**In Scope:**
- Create `.iw/commands/config.scala` command file
- Implement `get` subcommand with field name argument
- Add `ReadWriter[IssueTrackerType]` and `ReadWriter[ProjectConfiguration]` derivations for upickle
- Plain text output (just the value, no formatting)
- Error handling for missing config, unknown fields, unset optional fields
- E2E tests for all scenarios in the Gherkin spec

**Out of Scope:**
- `--json` flag (Phase 2)
- Usage help text (Phase 3)
- `set` subcommand (future)

## Dependencies

**From Existing Codebase:**
- `iw.core.model.ProjectConfiguration` - configuration data model
- `iw.core.model.IssueTrackerType` - enum for tracker types
- `iw.core.adapters.ConfigFileRepository.read()` - reads config from disk
- `iw.core.output.Output` - CLI output formatting
- `iw.core.model.Constants` - paths and config keys

**External Libraries:**
- upickle (already a dependency) - for JSON serialization
- ujson (comes with upickle) - for field lookup

## Technical Approach

### 1. Add upickle ReadWriter derivations

In `Config.scala`, add:
```scala
import upickle.default.*

// For enum serialization
given ReadWriter[IssueTrackerType] = readwriter[String].bimap(
  _.toString,
  s => IssueTrackerType.valueOf(s)
)

// For config serialization
given ReadWriter[ProjectConfiguration] = macroRW
```

### 2. Command Structure

```scala
// .iw/commands/config.scala
@main def config(args: String*): Unit =
  args.toList match
    case "get" :: field :: Nil => handleGet(field)
    case "get" :: Nil => // Error: missing field (Phase 3)
    case _ => // Usage help (Phase 3)
```

### 3. Field Lookup Logic

```scala
def handleGet(field: String): Unit =
  val configPath = os.Path(System.getProperty("user.dir")) / ".iw" / "config.conf"

  ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("Configuration not found. Run 'iw init' first.")
      sys.exit(1)
    case Some(config) =>
      val json = write(config)
      val parsed = ujson.read(json)

      Try(parsed(field)) match
        case Success(value) if value.isNull =>
          Output.error(s"Configuration field '$field' is not set")
          sys.exit(1)
        case Success(value) =>
          Output.info(value.str) // or .num, .bool depending on type
          sys.exit(0)
        case Failure(_) =>
          Output.error(s"Unknown configuration field: $field")
          sys.exit(1)
```

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `.iw/commands/config.scala` | Create | New command file |
| `.iw/core/model/Config.scala` | Modify | Add upickle ReadWriter derivations |
| `.iw/test/config.bats` | Create | E2E tests for config command |

## Testing Strategy

### Unit Tests (in Config.scala or separate file)
- Verify `ReadWriter[IssueTrackerType]` serializes/deserializes correctly
- Verify `ReadWriter[ProjectConfiguration]` handles all fields

### E2E Tests (BATS)
```bash
# Test cases from Gherkin scenarios:
@test "config get trackerType returns tracker type"
@test "config get repository returns repository value"
@test "config get nonexistent returns error"
@test "config get optional-unset-field returns error"
@test "config get without config file returns error"
```

### Test Data
- Create temporary `.iw/config.conf` fixtures with known values
- Test all tracker types (GitHub, GitLab, Linear, YouTrack)
- Test with all required and optional fields

## Acceptance Criteria

From the Gherkin spec:

1. **Retrieve tracker type successfully**
   - Given valid `.iw/config.conf` with GitHub tracker
   - When `iw config get trackerType`
   - Then output is "GitHub", exit code 0

2. **Retrieve repository value**
   - Given valid config with `repository = "iterative-works/iw-cli"`
   - When `iw config get repository`
   - Then output is "iterative-works/iw-cli", exit code 0

3. **Request non-existent field**
   - Given valid config
   - When `iw config get nonexistent`
   - Then output contains "Unknown configuration field: nonexistent", exit code 1

4. **Request optional field that is not set**
   - Given valid config without `youtrackBaseUrl`
   - When `iw config get youtrackBaseUrl`
   - Then output indicates not set, exit code 1

5. **No configuration file exists**
   - Given no `.iw/config.conf`
   - When `iw config get trackerType`
   - Then output contains "Configuration not found", exit code 1

## Supported Fields

| Field Name | Type | Required | Description |
|------------|------|----------|-------------|
| `trackerType` | String | Yes | Issue tracker type (GitHub, GitLab, Linear, YouTrack) |
| `team` | String | Linear/YouTrack | Team identifier |
| `projectName` | String | Yes | Project name |
| `version` | String | Optional | Tool version (defaults to "latest") |
| `youtrackBaseUrl` | String | Optional | Base URL for YouTrack/GitLab self-hosted |
| `repository` | String | GitHub/GitLab | Repository in owner/repo format |
| `teamPrefix` | String | GitHub/GitLab | Issue ID prefix (e.g., "IW") |

## Notes

- Output should be plain text - just the value, no quotes or formatting
- For enum fields like `trackerType`, output the string representation ("GitHub" not "github")
- Optional fields that are `None` should output empty and exit 1
- The command is read-only - it never modifies the config file
