# Phase 3 Tasks: Simplify DashboardService after worktree list removal

## Setup
- [x] [setup] Verify all unused parameters identified in phase-03-context.md (issueCache, progressCache, prCache, config, val now)

## Tests (Existing - No New Tests Needed)
- [x] [test] Update 27 call sites in DashboardServiceTest.scala to use new 4-parameter signature
- [x] [test] Remove unused test imports if issueCache, progressCache, prCache no longer referenced
- [x] [test] Run ./iw test unit and verify all tests pass

## Implementation (Green)
- [x] [impl] Update DashboardService.renderDashboard signature: remove issueCache, progressCache, prCache, config parameters
- [x] [impl] Remove val now = Instant.now() from renderDashboard body
- [x] [impl] Update renderDashboard Scaladoc to reflect simplified signature
- [x] [impl] Update CaskServer.dashboard() to call renderDashboard with new 4-parameter signature
- [x] [impl] Remove config/configPath loading from CaskServer.dashboard() (lines 46-47)

## Integration
- [x] [integration] Verify no unused imports in DashboardService.scala (all still used by other methods)
- [x] [integration] Run ./iw test unit to confirm all unit tests pass
- [x] [integration] Run ./iw test e2e to confirm E2E tests pass
- [x] [integration] Verify compiler produces no warnings about unused parameters or imports
- [x] [integration] Commit changes with clear message about simplified DashboardService signature
