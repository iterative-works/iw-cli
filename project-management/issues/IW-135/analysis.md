# Story-Driven Analysis: Add iw config command for querying project configuration

**Issue:** IW-135
**Created:** 2026-01-28
**Status:** Draft
**Classification:** Simple

## Problem Statement

Workflows (kanon) need to query project configuration (tracker type, project settings) without directly reading `.iw/config.conf`. Currently, workflows must parse HOCON files directly, creating tight coupling to iw-cli's internal configuration format. This violates the clean architecture boundary: workflows should only interact with iw-cli through its command interface.

The `iw config` command provides a stable CLI interface for querying configuration, allowing:
- Workflows to remain format-agnostic (they don't care about HOCON vs JSON vs YAML)
- Configuration format changes without breaking workflows
- Clear separation of concerns: iw-cli owns config parsing, workflows own orchestration

## User Stories

### Story 1: Query specific configuration value by path

```gherkin
Feature: Configuration value retrieval
  As a workflow script author
  I want to query a specific config value by field name
  So that I can make decisions based on project configuration

Scenario: Retrieve tracker type successfully
  Given a valid .iw/config.conf exists
  And the config specifies GitHub as the tracker
  When I run "iw config get trackerType"
  Then the output is "GitHub"
  And the exit code is 0

Scenario: Retrieve repository value
  Given a valid .iw/config.conf exists
  And the config specifies repository = "iterative-works/iw-cli"
  When I run "iw config get repository"
  Then the output is "iterative-works/iw-cli"
  And the exit code is 0

Scenario: Request non-existent field
  Given a valid .iw/config.conf exists
  When I run "iw config get nonexistent"
  Then the output contains "Unknown configuration field: nonexistent"
  And the exit code is 1

Scenario: Request optional field that is not set
  Given a valid .iw/config.conf exists
  And the config does not specify youtrackBaseUrl
  When I run "iw config get youtrackBaseUrl"
  Then the output is empty or indicates not set
  And the exit code is 1

Scenario: No configuration file exists
  Given .iw/config.conf does not exist
  When I run "iw config get trackerType"
  Then the output contains "Configuration not found"
  And the exit code is 1
```

**Estimated Effort:** 3-4 hours
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward because:
- Configuration parsing already exists (`ConfigFileRepository.read()`)
- JSON serialization via upickle, path lookup via ujson
- Error handling patterns established in existing commands

**Acceptance:**
- Can retrieve any configuration field by camelCase field name
- Outputs plain text (just the value, no formatting)
- Returns exit code 0 for success, 1 for not found/not set
- Clear error messages for missing config file or unknown fields

---

### Story 2: Export full configuration as JSON

```gherkin
Feature: Full configuration export
  As a workflow script author
  I want to get the entire configuration as JSON
  So that I can parse it programmatically in my script

Scenario: Export configuration as JSON
  Given a valid .iw/config.conf exists
  And the config specifies GitHub tracker with repository "iterative-works/iw-cli"
  When I run "iw config --json"
  Then the output is valid JSON
  And the JSON contains "trackerType":"GitHub"
  And the JSON contains "repository":"iterative-works/iw-cli"
  And the JSON contains "teamPrefix":"IW"
  And the JSON contains "projectName":"iw-cli"
  And the exit code is 0

Scenario: Export when no configuration exists
  Given .iw/config.conf does not exist
  When I run "iw config --json"
  Then the output contains "Configuration not found"
  And the exit code is 1
```

**Estimated Effort:** 2-3 hours
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward because:
- upickle provides JSON serialization via `write(config)`
- Config already loaded via `ConfigFileRepository.read()`
- Error handling matches Story 1 patterns

**Acceptance:**
- Outputs well-formed JSON to stdout
- JSON structure mirrors HOCON structure
- Handles all configuration value types (strings, booleans, numbers, nested objects)
- Same error handling as Story 1 for missing config

---

### Story 3: Validate command usage and provide help

```gherkin
Feature: Configuration command usage guidance
  As a developer using iw-cli
  I want clear error messages for incorrect usage
  So that I understand how to use the config command correctly

Scenario: No arguments provided
  When I run "iw config"
  Then the output shows usage information
  And the usage includes "iw config get <path>"
  And the usage includes "iw config --json"
  And the exit code is 1

Scenario: Get command without path argument
  When I run "iw config get"
  Then the output contains "Missing required argument: <path>"
  And the output shows usage information
  And the exit code is 1

Scenario: Invalid flag provided
  When I run "iw config --invalid"
  Then the output contains "Unknown option: --invalid"
  And the output shows usage information
  And the exit code is 1
```

**Estimated Effort:** 1-2 hours
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward because:
- Standard argument parsing pattern used in all commands
- Usage text is simple string formatting
- Error messages follow established conventions

**Acceptance:**
- Clear, concise usage message
- Helpful error messages for common mistakes
- Consistent with other iw commands' help output

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Query specific configuration value by field name

**Domain Layer:**
- No new domain types needed (reuse existing `ProjectConfiguration`, `IssueTrackerType`)
- Add `ReadWriter[ProjectConfiguration]` derivation for upickle serialization

**Application Layer:**
- Logic within command to serialize config to JSON, then query by field name via ujson

**Infrastructure Layer:**
- `ConfigFileRepository.read(path)` (already exists)
- upickle/ujson for JSON serialization and field lookup

**Presentation Layer:**
- `.iw/commands/config.scala` - new command script
- Plain text output formatter (value only, no decoration)
- Error message formatter for unknown fields

---

### For Story 2: Export full configuration as JSON

**Domain Layer:**
- Same `ReadWriter[ProjectConfiguration]` as Story 1

**Application Layer:**
- Serialize config to JSON using upickle `write()`

**Infrastructure Layer:**
- `ConfigFileRepository.read(path)` (already exists)
- upickle for JSON serialization

**Presentation Layer:**
- Same `.iw/commands/config.scala` command
- Compact JSON output (single line)

---

### For Story 3: Validate command usage and provide help

**Domain Layer:**
- No domain types needed

**Application Layer:**
- Argument validation logic
- Usage text generation

**Infrastructure Layer:**
- None (pure string processing)

**Presentation Layer:**
- Same `.iw/commands/config.scala` command
- Usage message formatter (consistent with other commands)
- Error message formatter

## Technical Risks & Uncertainties

### RESOLVED: Path notation and JSON serialization

**Decision:** Serialize `ProjectConfiguration` to JSON using upickle, then use ujson to query paths for `get`.

**Rationale:**
- upickle is already a project dependency
- Single source of truth: JSON structure derives from case class
- No manual mapping to maintain
- Paths naturally match field names

**Implementation approach:**
```scala
import upickle.default.*

// Derive JSON codec
given ReadWriter[ProjectConfiguration] = macroRW

// For --json: serialize the model
val json = write(config)

// For get <path>: query using ujson
val value = ujson.read(json)(fieldName)
```

**Supported paths** (flat camelCase, matching case class fields):
- `trackerType` - Issue tracker type (GitHub, GitLab, Linear, YouTrack)
- `repository` - Repository in owner/repo format
- `teamPrefix` - Issue ID prefix (e.g., "IW")
- `youtrackBaseUrl` - Base URL for YouTrack/GitLab
- `projectName` - Project name
- `team` - Team identifier (for Linear/YouTrack)
- `version` - Tool version

Optional fields return empty/null when not configured (exit code 1).

---

### RESOLVED: JSON output structure for Story 2

**Decision:** Serialize `ProjectConfiguration` case class to JSON using upickle. Compact output by default.

**Rationale:**
- Ensures JSON reflects actual domain model values (including defaults, validated data)
- Field names match `get` paths (both use case class field names)
- upickle already in project dependencies
- Compact output is easier for scripts; users can pipe to `jq` for pretty-printing

**Example output:**
```json
{"trackerType":"GitHub","team":"","projectName":"iw-cli","repository":"iterative-works/iw-cli","teamPrefix":"IW","version":"latest"}
```

---

### RESOLVED: Command structure

**Decision:** Use subcommand structure with explicit `get` to anticipate future `set`.

**Command interface:**
```bash
iw config get <field>    # Get a specific field value
iw config --json         # Export full config as JSON
iw config                # Show usage
```

**Rationale:**
- Matches original issue examples (`iw config get tracker.type`)
- Clean extension path for `iw config set <field> <value>` later
- Consistent subcommand pattern

**Scope:** `set` subcommand excluded from initial implementation.

## Total Estimates

**Story Breakdown:**
- Story 1 (Query specific config value): 3-4 hours
- Story 2 (Export full config as JSON): 2-3 hours
- Story 3 (Usage validation and help): 1-2 hours

**Total Range:** 6-9 hours

**Confidence:** High

**Reasoning:**
- All required infrastructure exists (config parsing, file I/O, error handling patterns)
- Follows established command patterns from `version.scala`, `init.scala`
- No external API calls or complex business logic
- Main effort is argument parsing, path lookup, and output formatting
- Typesafe Config library provides built-in path queries and JSON rendering
- CLARIFY markers are minor design decisions, not blockers

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure logic for path parsing, output formatting
2. **Integration Tests**: Read config file, query values, produce output
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenarios via BATS

**Story-Specific Testing Notes:**

**Story 1: Query specific configuration value**
- Unit: Path parsing logic, error message generation
- Integration: `ConfigFileRepository.read()` -> path lookup -> output value
- E2E: BATS test creates `.iw/config.conf`, runs `iw config get tracker.type`, asserts output

**Story 2: Export full configuration as JSON**
- Unit: JSON serialization logic (if custom formatter needed)
- Integration: `ConfigFileRepository.read()` -> JSON render -> output
- E2E: BATS test creates `.iw/config.conf`, runs `iw config --json`, validates JSON with `jq`

**Story 3: Validate command usage**
- Unit: Argument validation logic, usage text generation
- Integration: None (pure string processing)
- E2E: BATS tests for various invalid invocations, assert error messages and exit codes

**Test Data Strategy:**
- E2E tests create temporary `.iw/config.conf` fixtures with known values
- Use existing test patterns from `test/e2e/` directory
- Cover all tracker types (github, gitlab, linear, youtrack) in config variations

**Regression Coverage:**
- Verify `iw config` doesn't modify config file (read-only guarantee)
- Test with missing `.iw` directory (should fail gracefully)
- Test with malformed HOCON (should report parse error)

## Deployment Considerations

### Database Changes
None required (no persistence layer changes)

### Configuration Changes
None required (command reads existing config, doesn't add new fields)

### Rollout Strategy
- Deploy as single new command file `.iw/commands/config.scala`
- No migration needed (doesn't change existing config format)
- Backwards compatible (doesn't affect existing commands)
- Can deploy immediately once implemented and tested

### Rollback Plan
If the command has issues in production:
- Remove or rename `.iw/commands/config.scala` to disable the command
- No data corruption risk (read-only operation)
- No impact on other commands (fully isolated)

## Dependencies

### Prerequisites
- Existing `.iw/config.conf` file (created by `iw init`)
- Typesafe Config library (already in dependencies)
- `ConfigFileRepository` and `ProjectConfiguration` (already implemented)

### Story Dependencies
- Story 2 and Story 3 are independent of Story 1
- Could implement all three stories in parallel if desired
- Recommend sequential implementation for testing simplicity: 1 -> 2 -> 3

### External Blockers
None (all dependencies satisfied by existing codebase)

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Query specific config value** - Core functionality, establishes command structure and config reading patterns
2. **Story 2: Export full config as JSON** - Builds on Story 1's config loading, adds JSON serialization
3. **Story 3: Usage validation and help** - Polish and user experience, can be done last

**Iteration Plan:**

- **Iteration 1** (Story 1): Core value queries working, enables workflow integration immediately
- **Iteration 2** (Story 2): JSON export for programmatic use cases
- **Iteration 3** (Story 3): Help text and validation for better UX

**Rationale:**
- Story 1 delivers immediate value for workflows (can query tracker type, repository, etc.)
- Story 2 enables more sophisticated workflow scripts that need multiple config values
- Story 3 improves developer experience but doesn't block functionality

## Documentation Requirements

- [ ] Update project README with `iw config` command examples
- [ ] Add inline command documentation (PURPOSE, USAGE, ARGS, EXAMPLE comments in `.iw/commands/config.scala`)
- [ ] Gherkin scenarios serve as living documentation for behavior
- [ ] Update workflow integration docs to show config querying examples
- [ ] No API documentation needed (CLI command, not library)
- [ ] No migration guide needed (new command, no breaking changes)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with Michal (path query strategy, JSON structure, command structure)
2. Once approved, implement Story 1 to unblock workflow integration
3. Add E2E tests alongside implementation (TDD approach)
4. Implement Stories 2-3 in sequence
5. Update documentation with usage examples
