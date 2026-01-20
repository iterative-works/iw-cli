# Phase 3 Tasks: Run server with custom project directory

**Issue:** IW-82
**Phase:** 3 - Run server with custom project directory
**Created:** 2026-01-20

## Setup

- [ ] [test] Create test fixture for project path scenarios
- [ ] [test] Create CaskServer unit tests for project path constructor parameter

## CaskServer Changes

- [ ] [test] Write test: CaskServer uses projectPath for config loading when provided
- [ ] [impl] Add projectPath parameter to CaskServer constructor
- [ ] [test] Write test: CaskServer uses os.pwd when projectPath not provided
- [ ] [impl] Update dashboard route to use projectPath for config loading
- [ ] [test] Write test: Auto-prune uses projectPath for existence check
- [ ] [impl] Update auto-prune to use projectPath instead of os.pwd
- [ ] [impl] Update CaskServer.start() companion object to accept projectPath

## CLI Changes

- [ ] [test] Write test: dashboard command parses --project flag
- [ ] [impl] Add --project flag parsing to dashboard.scala
- [ ] [impl] Pass projectPath to CaskServer.start()
- [ ] [impl] Update usage string with --project option

## UI Indicator

- [ ] [test] Write test: Dashboard shows project indicator when custom project used
- [ ] [impl] Pass projectPath to DashboardService.renderDashboard
- [ ] [impl] Add project path indicator to dashboard header

## Integration

- [ ] [test] Write integration test: Dashboard loads config from custom project
- [ ] [test] Write integration test: --project combines with --state-path and --sample-data
- [ ] [verify] Manual verification of full flow

## Completion Checklist

- [ ] All tests pass
- [ ] Code review passed
- [ ] No compiler warnings
