# Phase 5 Tasks: Validate development mode isolation

**Issue:** IW-82
**Phase:** 5 - Validate development mode isolation
**Created:** 2026-01-23

## Overview

This phase creates E2E tests to validate that `--dev` mode provides complete isolation from production state.

## Tasks

### Setup

- [x] [impl] [x] [reviewed] Create test file `.iw/test/dashboard-dev-mode.bats` with BATS boilerplate

### Test: Dev Mode Uses Temp Directory

- [x] [test] [x] [reviewed] Write test: `--dev flag creates temp directory`
  - Start server with `--dev`
  - Capture output showing temp directory path
  - Verify path matches pattern `/tmp/iw-dev-*`

- [x] [test] [x] [reviewed] Write test: `--dev flag creates state.json in temp directory`
  - Start server with `--dev`
  - Extract temp directory from output
  - Verify state.json exists in temp directory

- [x] [test] [x] [reviewed] Write test: `--dev flag creates config.json in temp directory`
  - Start server with `--dev`
  - Extract temp directory from output
  - Verify config.json exists in temp directory

### Test: Production State Isolation

- [x] [test] [x] [reviewed] Write test: `production state file unchanged after dev mode`
  - Create baseline production state file
  - Record SHA256 hash
  - Start server with `--dev`, perform operations, stop
  - Verify production state file hash unchanged

- [x] [test] [x] [reviewed] Write test: `production config file unchanged after dev mode`
  - Create baseline production config file
  - Record SHA256 hash
  - Start server with `--dev`, stop
  - Verify production config file hash unchanged

### Test: Sample Data in Dev Mode

- [x] [test] [x] [reviewed] Write test: `--dev flag enables sample data by default`
  - Start server with `--dev`
  - Verify output includes "Sample data" messages
  - Verify sample worktrees are present in temp state file

### Documentation

- [x] [impl] [x] [reviewed] Update dashboard help text with isolation guarantees
  - Add `--help` output describing dev mode isolation
  - Document that `--dev` creates isolated temp directory
  - Document that production state is never touched

## Technical Notes

### Test Strategy

Tests use BATS framework (existing pattern in `.iw/test/`).

Server lifecycle in tests:
1. Start server with `&` to background it
2. Sleep briefly to let it initialize
3. Perform any API calls needed
4. Kill server with `kill %1`
5. Verify file states

### Expected Output Patterns

From `--dev`:
```
Dev mode enabled:
  - Temp directory: /tmp/iw-dev-<timestamp>
  - State file: /tmp/iw-dev-<timestamp>/state.json
  - Config file: /tmp/iw-dev-<timestamp>/config.json
  - Sample data: enabled
```

### Production Paths

- State: `~/.local/share/iw/server/state.json`
- Config: `~/.local/share/iw/server/config.json`

### Hash Verification

```bash
BASELINE_HASH=$(sha256sum "$FILE" | cut -d' ' -f1)
# ... operations ...
AFTER_HASH=$(sha256sum "$FILE" | cut -d' ' -f1)
[ "$BASELINE_HASH" = "$AFTER_HASH" ]
```

## Acceptance Criteria

From Story 5:
- [x] E2E test verifies production state isolation
- [x] Test creates baseline production state
- [x] Test runs dev mode and performs operations
- [x] Test verifies production state byte-for-byte identical
- [x] Documentation clearly states isolation guarantees
