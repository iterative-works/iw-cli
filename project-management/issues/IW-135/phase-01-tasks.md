# Phase 1 Tasks: Query specific configuration value by field name

**Issue:** IW-135
**Phase:** 1 of 3
**Context:** [phase-01-context.md](phase-01-context.md)

## Task Checklist

### Setup

- [x] [impl] [x] [reviewed] Create `.iw/commands/config.scala` with basic structure and PURPOSE header

### Tests First (TDD)

- [x] [test] [x] [reviewed] Add E2E test: `iw config get trackerType` returns tracker type for GitHub config
- [x] [test] [x] [reviewed] Add E2E test: `iw config get repository` returns repository value
- [x] [test] [x] [reviewed] Add E2E test: `iw config get teamPrefix` returns team prefix value
- [x] [test] [x] [reviewed] Add E2E test: `iw config get projectName` returns project name
- [x] [test] [x] [reviewed] Add E2E test: `iw config get nonexistent` returns error with exit code 1
- [x] [test] [x] [reviewed] Add E2E test: `iw config get youtrackBaseUrl` when unset returns error
- [x] [test] [x] [reviewed] Add E2E test: `iw config get trackerType` without config file returns error
- [x] [test] [x] [reviewed] Add E2E test: `iw config get trackerType` with Linear tracker config
- [x] [test] [x] [reviewed] Add E2E test: `iw config get team` with Linear tracker returns team value

### Implementation

- [x] [impl] [x] [reviewed] Add upickle `ReadWriter[IssueTrackerType]` derivation in Config.scala
- [x] [impl] [x] [reviewed] Add upickle `ReadWriter[ProjectConfiguration]` derivation in Config.scala
- [x] [impl] [x] [reviewed] Implement `handleGet(field: String)` function in config.scala
- [x] [impl] [x] [reviewed] Handle missing config file case with appropriate error message
- [x] [impl] [x] [reviewed] Handle unknown field case with appropriate error message
- [x] [impl] [x] [reviewed] Handle optional fields that are None/null with appropriate error
- [x] [impl] [x] [reviewed] Wire up `get` subcommand in main function

### Integration

- [x] [test] [x] [reviewed] Run all E2E tests and verify they pass
- [x] [impl] [x] [reviewed] Verify command works with all tracker types (GitHub, GitLab, Linear, YouTrack)

## Task Details

### Create config.scala basic structure

```scala
// PURPOSE: Query and export project configuration values
// PURPOSE: Provides programmatic access to .iw/config.conf for workflows
// USAGE: iw config get <field>
// ARGS:
//   get <field>: Get value of a specific configuration field
// EXAMPLE: iw config get trackerType
// EXAMPLE: iw config get repository

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.Output

@main def config(args: String*): Unit =
  args.toList match
    case "get" :: field :: Nil => handleGet(field)
    case _ =>
      Output.error("Usage: iw config get <field>")
      sys.exit(1)

def handleGet(field: String): Unit =
  // TODO: Implement
  ???
```

### Add ReadWriter derivations

In `.iw/core/model/Config.scala`, add imports and derivations:

```scala
import upickle.default.*

// Near end of file, after case class definitions:
object ProjectConfigurationJson:
  given ReadWriter[IssueTrackerType] = readwriter[String].bimap(
    _.toString,
    s => IssueTrackerType.valueOf(s)
  )

  given ReadWriter[ProjectConfiguration] = macroRW
```

### E2E Test Pattern

In `.iw/test/config.bats`:

```bash
@test "config get trackerType returns tracker type" {
    # Setup: create a git repo with GitHub config
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = github
  repository = "owner/repo"
  teamPrefix = "IW"
}
project {
  name = test-project
}
EOF

    # Run command
    run "$PROJECT_ROOT/iw" config get trackerType

    # Assert
    [ "$status" -eq 0 ]
    [ "$output" = "GitHub" ]
}
```

## Notes

- upickle serializes enum values using their `.toString` method by default
- ujson can access fields using `parsed(fieldName)` syntax
- Optional fields serialize as `null` in JSON when `None`
- Need to handle different value types (String, Option[String])

**Phase Status:** Complete
