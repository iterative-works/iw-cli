# Phase 4 Tasks: Combined development mode flag

**Issue:** IW-82
**Phase:** 4 of 5
**Created:** 2026-01-23

## Task Groups

### Group 1: CLI Flag and Temp Directory (Setup)

- [ ] [test] Write test for temp directory path generation
- [ ] [impl] Add `--dev` flag parsing to dashboard.scala
- [ ] [impl] Generate timestamped temp directory when `--dev` is used
- [ ] [impl] Auto-enable sample data flag when `--dev` is true
- [ ] [impl] Create isolated config.json in temp directory

### Group 2: CaskServer devMode Support

- [ ] [test] Write test for CaskServer with devMode parameter
- [ ] [impl] Add `devMode` parameter to CaskServer.start()
- [ ] [impl] Pass devMode to CaskServer constructor
- [ ] [impl] Pass devMode from dashboard route to DashboardService

### Group 3: Dashboard Banner UI

- [ ] [test] Write test for DashboardService with devMode=true renders banner
- [ ] [test] Write test for DashboardService with devMode=false does NOT render banner
- [ ] [impl] Add `devMode` parameter to DashboardService.renderDashboard
- [ ] [impl] Add dev-mode-banner div to dashboard header when devMode=true
- [ ] [impl] Add CSS styles for dev-mode-banner

### Group 4: Integration and Verification

- [ ] [test] Write integration test for dashboard --dev CLI command
- [ ] [impl] Add console output showing temp paths when --dev is used
- [ ] [verify] Manual test: `./iw dashboard --dev` shows banner
- [ ] [verify] Manual test: `./iw dashboard` does NOT show banner
- [ ] [verify] Manual test: Production state file unchanged after dev mode use

## TDD Flow

For each [test]/[impl] pair:
1. Write the failing test first
2. Run test to confirm it fails
3. Implement the minimum code to pass
4. Run test to confirm it passes
5. Refactor if needed

## Notes

- The `--dev` flag should work independently but also combine well with explicit `--state-path`
- If both `--dev` and `--state-path` are provided, explicit path takes precedence
- The banner should be visually distinct but not intrusive (yellow/warning style)
- Console output should be clear about which paths are being used for transparency
