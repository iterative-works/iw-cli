# Phase 1 Tasks: Template Path Resolution Fix

**Issue:** IW-107
**Story:** Run claude-sync from any project successfully

## Setup

- [ ] [setup] Read current claude-sync.scala implementation (line 17)
- [ ] [setup] Verify IW_COMMANDS_DIR is set by iw-run bootstrap

## Tests (TDD - write first)

- [ ] [test] Create e2e/claude-sync.bats test file with setup
- [ ] [test] Write test: claude-sync finds template from IW_COMMANDS_DIR
- [ ] [test] Write test: claude-sync fallback to os.pwd when IW_COMMANDS_DIR unset
- [ ] [test] Run tests to confirm they fail (red phase)

## Implementation

- [ ] [impl] Add IwCommandsDir to Constants.EnvVars
- [ ] [impl] Modify claude-sync.scala to resolve promptFile from IW_COMMANDS_DIR
- [ ] [impl] Implement fallback to os.pwd when IW_COMMANDS_DIR not set
- [ ] [impl] Run tests to confirm they pass (green phase)

## Integration

- [ ] [integration] Run full E2E test suite to verify no regressions
- [ ] [integration] Manual verification: run iw claude-sync from iw-cli repo
