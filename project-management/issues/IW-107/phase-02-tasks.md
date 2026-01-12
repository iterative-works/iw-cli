# Phase 2 Tasks: Improved Error Messaging

**Issue:** IW-107
**Story:** Clear error when template genuinely missing

## Setup

- [x] [setup] Read current error handling in claude-sync.scala (lines 24-26)
- [x] [setup] Review Output.* functions available for messaging

## Tests (TDD - write first)

- [x] [test] Add test to claude-sync.bats: error output shows checked path
- [x] [test] Add test: error output mentions "installation" context
- [x] [test] Add test: error output includes actionable suggestion
- [x] [test] Run tests to confirm they fail (red phase)

## Implementation

- [x] [impl] Enhance error block to show installation context message
- [x] [impl] Add Output.info lines with actionable suggestions
- [x] [impl] Show detected installation directory if IW_COMMANDS_DIR was set
- [x] [impl] Run tests to confirm they pass (green phase)

## Integration

- [x] [integration] Run full E2E test suite to verify no regressions
- [x] [integration] Manual verification: trigger error by removing template temporarily

**Phase Status:** Complete
