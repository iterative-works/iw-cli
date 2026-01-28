# Phase 2 Tasks: Export full configuration as JSON

**Issue:** IW-135
**Phase:** 2 of 3
**Context:** [phase-02-context.md](phase-02-context.md)

## Task Checklist

### Tests First (TDD)

- [x] [test] [x] [reviewed] Add E2E test: `iw config --json` outputs valid JSON with GitHub config
- [x] [test] [x] [reviewed] Add E2E test: `iw config --json` includes trackerType field with correct value
- [x] [test] [x] [reviewed] Add E2E test: `iw config --json` includes repository field
- [x] [test] [x] [reviewed] Add E2E test: `iw config --json` without config file returns error
- [x] [test] [x] [reviewed] Add E2E test: `iw config --json` with Linear config outputs valid JSON

### Implementation

- [x] [impl] [x] [reviewed] Add `handleJson()` function to config.scala
- [x] [impl] [x] [reviewed] Update main function pattern matching to handle `--json` flag
- [x] [impl] [x] [reviewed] Handle missing config file case with appropriate error message

### Integration

- [x] [test] [x] [reviewed] Run all E2E tests and verify they pass
- [x] [impl] [x] [reviewed] Verify command works with all tracker types

**Phase Status:** Complete

## Task Details

### Add handleJson function

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

### Update main function

```scala
@main def config(args: String*): Unit =
  args.toList match
    case "get" :: field :: Nil => handleGet(field)
    case "--json" :: Nil => handleJson()
    case _ =>
      Output.error("Usage: iw config get <field> | iw config --json")
      sys.exit(1)
```

### E2E Test Pattern

```bash
@test "config --json outputs valid JSON with GitHub config" {
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

    # Run command
    run "$PROJECT_ROOT/iw" config --json

    # Assert
    [ "$status" -eq 0 ]
    # Validate JSON (requires jq)
    echo "$output" | jq . > /dev/null 2>&1
    [ $? -eq 0 ]
}
```

## Notes

- Reuse existing `ProjectConfigurationJson` derivations from Phase 1
- Output is compact JSON (single line) - users can pipe to `jq` for formatting
- Error handling follows Phase 1 patterns
