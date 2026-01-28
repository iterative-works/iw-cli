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
  I want to query a specific config value by path
  So that I can make decisions based on project configuration

Scenario: Retrieve tracker type successfully
  Given a valid .iw/config.conf exists
  And the config contains tracker.type = github
  When I run "iw config get tracker.type"
  Then the output is "github"
  And the exit code is 0

Scenario: Retrieve nested configuration value
  Given a valid .iw/config.conf exists
  And the config contains tracker.repository = "iterative-works/iw-cli"
  When I run "iw config get tracker.repository"
  Then the output is "iterative-works/iw-cli"
  And the exit code is 0

Scenario: Request non-existent configuration path
  Given a valid .iw/config.conf exists
  When I run "iw config get tracker.nonexistent"
  Then the output contains "Configuration key not found: tracker.nonexistent"
  And the exit code is 1

Scenario: No configuration file exists
  Given .iw/config.conf does not exist
  When I run "iw config get tracker.type"
  Then the output contains "Configuration not found"
  And the exit code is 1
```

**Estimated Effort:** 3-4 hours
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward because:
- Configuration parsing already exists (`ConfigFileRepository.read()`)
- Path lookup can use Typesafe Config's `hasPath()` and `getString()` methods
- Error handling patterns established in existing commands

**Acceptance:**
- Can retrieve any configuration value by dot-notation path
- Outputs plain text (just the value, no formatting)
- Returns exit code 0 for success, 1 for not found
- Clear error messages for missing config file or invalid paths

---

### Story 2: Export full configuration as JSON

```gherkin
Feature: Full configuration export
  As a workflow script author
  I want to get the entire configuration as JSON
  So that I can parse it programmatically in my script

Scenario: Export configuration as JSON
  Given a valid .iw/config.conf exists
  And the config contains:
    """
    tracker {
      type = github
      repository = "iterative-works/iw-cli"
      teamPrefix = "IW"
    }
    project {
      name = iw-cli
    }
    """
  When I run "iw config --json"
  Then the output is valid JSON
  And the JSON contains tracker.type = "github"
  And the JSON contains tracker.repository = "iterative-works/iw-cli"
  And the JSON contains tracker.teamPrefix = "IW"
  And the JSON contains project.name = "iw-cli"
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
- Typesafe Config library provides `config.root().render()` for JSON serialization
- Config already loaded via `ConfigFileRepository.read()`
- JSON output formatting is a simple library call
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

### For Story 1: Query specific configuration value by path

**Domain Layer:**
- No new domain types needed (reuse existing `ProjectConfiguration`, `IssueTrackerType`)

**Application Layer:**
- `ConfigQueryService` or logic within command to query config by path
- Method to convert `ProjectConfiguration` case class to path-accessible structure

**Infrastructure Layer:**
- `ConfigFileRepository.read(path)` (already exists)
- Typesafe Config library for path-based queries

**Presentation Layer:**
- `.iw/commands/config.scala` - new command script
- Plain text output formatter (value only, no decoration)
- Error message formatter for missing paths

---

### For Story 2: Export full configuration as JSON

**Domain Layer:**
- No new domain types needed

**Application Layer:**
- Logic to serialize full config to JSON

**Infrastructure Layer:**
- `ConfigFileRepository.read(path)` (already exists)
- Typesafe Config's JSON rendering capabilities

**Presentation Layer:**
- Same `.iw/commands/config.scala` command
- JSON formatter (pretty-printed or compact)

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

### CLARIFY: Path notation for nested values

The issue description mentions dot notation (e.g., `tracker.type`), but we need to clarify how this maps to the existing `ProjectConfiguration` case class.

**Questions to answer:**
1. Should paths query the raw HOCON structure or the parsed `ProjectConfiguration` object?
2. How do we handle optional fields (e.g., `tracker.baseUrl` which may not exist)?
3. Should we support array indexing (e.g., `hooks[0].name`) even though current config has no arrays?

**Options:**
- **Option A - Query raw HOCON**: Use Typesafe Config's path API directly on the parsed HOCON
  - Pros: Supports any config structure, forwards-compatible with config schema changes
  - Cons: Bypasses domain model validation, may return values that don't match `ProjectConfiguration`
- **Option B - Query via ProjectConfiguration**: Convert case class to Map/JSON-like structure
  - Pros: Type-safe, matches validated domain model
  - Cons: Requires maintaining mapping logic, less flexible for config evolution
- **Option C - Hybrid**: Load HOCON for path queries, but validate against `ProjectConfiguration` schema
  - Pros: Flexible queries, maintains validation
  - Cons: More complex implementation

**Impact:** Affects Story 1 and Story 2 implementation. Story 1 needs this decision to implement path lookup logic.

**Recommendation:** Option A (query raw HOCON) for simplicity and forward compatibility. The `iw config` command is a read-only query interface, so bypassing domain validation is acceptable here.

---

### CLARIFY: JSON output structure for Story 2

The issue shows example JSON output, but we need to clarify the exact structure.

**Questions to answer:**
1. Should JSON output match HOCON structure exactly (lowercase, nested objects)?
2. Should it include computed/derived fields from `ProjectConfiguration` (e.g., defaults)?
3. Should it be compact or pretty-printed by default?

**Options:**
- **Option A - Mirror HOCON exactly**: JSON structure identical to `.iw/config.conf` structure
  - Pros: Predictable, no transformation logic needed
  - Cons: May include HOCON-specific artifacts
- **Option B - Match ProjectConfiguration shape**: Serialize the case class to JSON
  - Pros: Clean, validated structure matching domain model
  - Cons: Field names differ from HOCON (e.g., `trackerType` vs `tracker.type`)
- **Option C - Pretty-printed by default**: Multi-line formatted JSON
  - Pros: Human-readable for debugging
  - Cons: Harder to parse in scripts (need `jq` piping)

**Impact:** Affects Story 2 JSON serialization. Determines which library/method to use for JSON conversion.

**Recommendation:** Option A (mirror HOCON) for consistency with `iw config get` paths, with compact output (workflows can pipe to `jq` for pretty-printing).

---

### CLARIFY: Scope exclusion for write operations

The issue description marks `iw config set` as "optional, lower priority" and should be excluded from initial scope.

**Questions to answer:**
1. Should we design the command structure to accommodate future `set` subcommand?
2. Should usage output mention that `set` is not yet implemented?

**Options:**
- **Option A - Design for future extension**: Use subcommand structure (`iw config get`, `iw config list`) anticipating `set`
  - Pros: Cleaner future extension, consistent subcommand pattern
  - Cons: More complex argument parsing for this simple feature
- **Option B - Flat command structure**: Use flags (`iw config --json`, `iw config <path>`) without subcommands
  - Pros: Simpler for read-only operations
  - Cons: May require refactoring when adding `set` later

**Impact:** Affects all stories - determines command line interface design.

**Recommendation:** Option B (flat structure) for simplicity. We can add `iw config set` as a separate subcommand later without breaking the read-only interface.

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
