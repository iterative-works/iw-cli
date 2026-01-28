# Phase 3 Tasks: Validate command usage and provide help

**Issue:** IW-135
**Phase:** 3 of 3
**Context:** [phase-03-context.md](phase-03-context.md)

## Task Checklist

### Tests First (TDD)

- [ ] [test] Add E2E test: `iw config` with no arguments shows usage
- [ ] [test] Add E2E test: `iw config get` without field shows "Missing required argument" error
- [ ] [test] Add E2E test: `iw config --invalid` shows "Unknown option" error
- [ ] [test] Add E2E test: Usage output includes "iw config get <field>"
- [ ] [test] Add E2E test: Usage output includes list of available fields

### Implementation

- [ ] [impl] Add `showUsage()` function with command description and available fields
- [ ] [impl] Add `handleGetMissingField()` function for missing field argument
- [ ] [impl] Add `handleUnknownArgs()` function for unknown options/subcommands
- [ ] [impl] Update main function pattern matching to handle all cases

### Integration

- [ ] [test] Run all E2E tests and verify they pass
- [ ] [impl] Verify all error paths exit with code 1

## Task Details

### showUsage function

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
```

### Update main function

```scala
@main def config(args: String*): Unit =
  args.toList match
    case "get" :: field :: Nil => handleGet(field)
    case "get" :: Nil => handleGetMissingField()
    case "--json" :: Nil => handleJson()
    case Nil => showUsage()
    case other => handleUnknownArgs(other)
```

### E2E Test Pattern

```bash
@test "config with no arguments shows usage" {
    # Setup: create GitHub config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IW"
}
project {
  name = test-project
}
EOF

    # Run command with no args
    run "$PROJECT_ROOT/iw" config

    # Assert
    [ "$status" -eq 1 ]
    [[ "$output" == *"Usage:"* ]]
    [[ "$output" == *"iw config get <field>"* ]]
    [[ "$output" == *"iw config --json"* ]]
}
```

## Notes

- All error/usage paths exit with code 1 (not 0)
- Usage text should be helpful but concise
- Include field descriptions to help users discover options
