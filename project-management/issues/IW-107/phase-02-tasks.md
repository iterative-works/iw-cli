# Phase 2 Tasks: Improved Error Messaging

**Issue:** IW-107
**Story:** Clear error when template genuinely missing

## Setup

- [ ] [setup] Read current error handling in claude-sync.scala (lines 24-26)
- [ ] [setup] Review Output.* functions available for messaging

## Tests (TDD - write first)

- [ ] [test] Add test to claude-sync.bats: error output shows checked path
- [ ] [test] Add test: error output mentions "installation" context
- [ ] [test] Add test: error output includes actionable suggestion
- [ ] [test] Run tests to confirm they fail (red phase)

## Implementation

- [ ] [impl] Enhance error block to show installation context message
- [ ] [impl] Add Output.info lines with actionable suggestions
- [ ] [impl] Show detected installation directory if IW_COMMANDS_DIR was set
- [ ] [impl] Run tests to confirm they pass (green phase)

## Integration

- [ ] [integration] Run full E2E test suite to verify no regressions
- [ ] [integration] Manual verification: trigger error by removing template temporarily

**Phase Status:** Ready
