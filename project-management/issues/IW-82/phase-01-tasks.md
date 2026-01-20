# Phase 1 Tasks: Run server with custom state file

**Issue:** IW-82
**Phase:** 1 of 5
**Story:** Run server with custom state file

## Implementation Tasks

### Setup
- [ ] [test] Create test directory for integration tests at `modules/server/test/src/`
- [ ] [test] Set up integration test suite for dashboard command

### Tests (TDD - Write First)
- [ ] [test] Test that `--state-path=<path>` CLI argument is parsed correctly
- [ ] [test] Test that custom state path is used when provided
- [ ] [test] Test that production state path is used when no custom path provided
- [ ] [test] Test that state file is created at custom path when worktree is registered

### Implementation
- [x] [impl] Add `statePath: String = ""` parameter to `dashboard` main function
- [x] [impl] Implement state path resolution logic (use custom or default to production path)
- [x] [impl] Pass effective state path to `startServerAndOpenBrowser`
- [x] [impl] Print effective state path on startup for debugging

### Integration
- [ ] [test] Integration test: start server with custom state path, verify production state untouched
- [ ] [manual] Manual verification: follow the manual test steps from phase-01-context.md

## Acceptance Criteria Checklist

- [ ] `./iw dashboard --state-path=<path>` starts server with custom state file
- [ ] Production state remains untouched when custom path is used
- [ ] Server persists worktrees to custom path
- [ ] Browser opens to dashboard
- [ ] No `--state-path` flag = uses production path (backward compatible)

## Notes

- scala-cli automatically maps `--state-path=foo` to `statePath` parameter
- `CaskServer.start()` already accepts `statePath` parameter - no changes needed there
- `StateRepository` handles creating parent directories when writing state
- Config continues to use production path for now (isolation comes in Phase 4)
