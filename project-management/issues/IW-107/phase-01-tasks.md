# Phase 1 Tasks: Template Path Resolution Fix

**Issue:** IW-107
**Story:** Run claude-sync from any project successfully

## Setup

- [x] [setup] Read current claude-sync.scala implementation (line 17)
- [x] [setup] Verify IW_COMMANDS_DIR is set by iw-run bootstrap

## Tests (TDD - write first)

- [x] [test] Create e2e/claude-sync.bats test file with setup
- [x] [test] Write test: claude-sync finds template from IW_COMMANDS_DIR
- [x] [test] Write test: claude-sync fallback to os.pwd when IW_COMMANDS_DIR unset
- [x] [test] Run tests to confirm they fail (red phase)

## Implementation

- [x] [impl] [x] [reviewed] Add IwCommandsDir to Constants.EnvVars
- [x] [impl] [x] [reviewed] Modify claude-sync.scala to resolve promptFile from IW_COMMANDS_DIR
- [x] [impl] [x] [reviewed] Implement fallback to os.pwd when IW_COMMANDS_DIR not set
- [x] [impl] [x] [reviewed] Run tests to confirm they pass (green phase)

**Phase Status:** Complete

## Integration

- [x] [integration] Run full E2E test suite to verify no regressions
- [x] [integration] Manual verification: run iw claude-sync from iw-cli repo
