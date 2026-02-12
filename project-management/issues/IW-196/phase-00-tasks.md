# Phase 0 Tasks: Move Check types from dashboard/ to model/

## Setup

- [x] [setup] Read current `DoctorChecks.scala` and `GitHubHookDoctor.scala` in `core/dashboard/`
- [x] [setup] Identify all dashboard-internal files that reference `Check` type

## Implementation

- [x] [impl] Copy `DoctorChecks.scala` to `core/model/`, update package to `iw.core.model`
- [x] [impl] Copy `GitHubHookDoctor.scala` to `core/model/`, update package to `iw.core.model`
- [x] [impl] Update imports in command files: `doctor.scala`, `github.hook-doctor.scala`, `start.hook-doctor.scala`, `issue.hook-doctor.scala`, `legacy-branches.hook-doctor.scala`
- [x] [impl] Update imports in test files: `DoctorChecksTest.scala`, `GitHubHookDoctorTest.scala`
- [x] [impl] Update imports in dashboard test files that reference `Check`: `CaskServerTest.scala`, `SampleDataTest.scala`, `WorktreeListViewTest.scala`, `SearchResultsViewTest.scala`, `ProcessManagerTest.scala`, `CreateWorktreeModalTest.scala`, `CreationErrorViewTest.scala`, `CreationSuccessViewTest.scala`
- [x] [impl] Update any dashboard-internal source files that import `Check` or `CheckResult` to use `iw.core.model`
- [x] [impl] Delete original `DoctorChecks.scala` from `core/dashboard/`
- [x] [impl] Delete original `GitHubHookDoctor.scala` from `core/dashboard/`

## Verification

- [ ] [verify] Run unit tests: `./iw test unit`
- [ ] [verify] Run E2E tests: `./iw test e2e`
- [ ] [verify] Grep for any remaining `iw.core.dashboard.Check` or `iw.core.dashboard.DoctorChecks` imports
- [ ] [verify] Grep for any remaining `iw.core.dashboard.GitHubHookDoctor` imports
