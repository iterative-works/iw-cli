# Phase 2: Application Layer — Tasks

## Setup

- [x] Create `ProjectRegistrationService.scala` with PURPOSE comments and package declaration
- [x] Create `ProjectRegistrationServiceTest.scala` with test class skeleton

## Tests First (TDD)

### ProjectRegistrationService
- [x] Test: `register()` new project creates entry with correct fields and `wasCreated = true`
- [x] Test: `register()` existing project updates fields, preserves `registeredAt`, returns `wasCreated = false`
- [x] Test: `register()` with empty path returns `Left`
- [x] Test: `register()` with empty projectName returns `Left`
- [x] Test: `deregister()` removes project from state
- [x] Test: `deregister()` is idempotent for non-existent path

### MainProjectService.resolveProjects
- [x] Test: no worktrees and no registered projects returns empty list
- [x] Test: worktrees only returns same as `deriveFromWorktrees()`
- [x] Test: registered projects only (no worktrees) returns registered projects as MainProject
- [x] Test: both registered and derived projects are merged with deduplication by path
- [x] Test: registered project with zero worktrees appears in output

### ServerStateService
- [x] Test: `updateProject()` adds new project
- [x] Test: `updateProject()` updates existing project
- [x] Test: `updateProject()` with None removes project
- [x] Test: `pruneProjects()` removes entries that fail validation

### DashboardService
- [x] Test: `renderDashboard()` with registered projects and no worktrees still renders projects
- [x] Test: existing `renderDashboard()` tests still pass (regression check)

## Implementation

- [x] Implement `ProjectRegistrationService.register()`
- [x] Implement `ProjectRegistrationService.deregister()`
- [x] Implement `MainProjectService.resolveProjects()`
- [x] Implement `ServerStateService.updateProject()`
- [x] Implement `ServerStateService.pruneProjects()`
- [x] Update `DashboardService.renderDashboard()` to use `resolveProjects()`

## Verification

- [x] Run `./iw test unit` — all tests pass
- [x] No compilation warnings
- [x] Existing tests show no regression
