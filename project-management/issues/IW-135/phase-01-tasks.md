# Phase 1 Tasks: Query specific configuration value by field name

**Issue:** IW-135
**Phase:** 1 of 3
**Context:** [phase-01-context.md](phase-01-context.md)

## Task Checklist

### Setup

- [ ] [impl] Create `.iw/commands/config.scala` with basic structure and PURPOSE header

### Tests First (TDD)

- [ ] [test] Add E2E test: `iw config get trackerType` returns tracker type for GitHub config
- [ ] [test] Add E2E test: `iw config get repository` returns repository value
- [ ] [test] Add E2E test: `iw config get teamPrefix` returns team prefix value
- [ ] [test] Add E2E test: `iw config get projectName` returns project name
- [ ] [test] Add E2E test: `iw config get nonexistent` returns error with exit code 1
- [ ] [test] Add E2E test: `iw config get youtrackBaseUrl` when unset returns error
- [ ] [test] Add E2E test: `iw config get trackerType` without config file returns error
- [ ] [test] Add E2E test: `iw config get trackerType` with Linear tracker config
- [ ] [test] Add E2E test: `iw config get team` with Linear tracker returns team value

### Implementation

- [ ] [impl] Add upickle `ReadWriter[IssueTrackerType]` derivation in Config.scala
- [ ] [impl] Add upickle `ReadWriter[ProjectConfiguration]` derivation in Config.scala
- [ ] [impl] Implement `handleGet(field: String)` function in config.scala
- [ ] [impl] Handle missing config file case with appropriate error message
- [ ] [impl] Handle unknown field case with appropriate error message
- [ ] [impl] Handle optional fields that are None/null with appropriate error
- [ ] [impl] Wire up `get` subcommand in main function

### Integration

- [ ] [test] Run all E2E tests and verify they pass
- [ ] [impl] Verify command works with all tracker types (GitHub, GitLab, Linear, YouTrack)

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
