# Phase 2: Application Layer — Tasks

## Setup

- [ ] Create `ProjectRegistrationService.scala` with PURPOSE comments and package declaration
- [ ] Create `ProjectRegistrationServiceTest.scala` with test class skeleton

## Tests First (TDD)

### ProjectRegistrationService
- [ ] Test: `register()` new project creates entry with correct fields and `wasCreated = true`
- [ ] Test: `register()` existing project updates fields, preserves `registeredAt`, returns `wasCreated = false`
- [ ] Test: `register()` with empty path returns `Left`
- [ ] Test: `register()` with empty projectName returns `Left`
- [ ] Test: `deregister()` removes project from state
- [ ] Test: `deregister()` is idempotent for non-existent path

### MainProjectService.resolveProjects
- [ ] Test: no worktrees and no registered projects returns empty list
- [ ] Test: worktrees only returns same as `deriveFromWorktrees()`
- [ ] Test: registered projects only (no worktrees) returns registered projects as MainProject
- [ ] Test: both registered and derived projects are merged with deduplication by path
- [ ] Test: registered project with zero worktrees appears in output

### ServerStateService
- [ ] Test: `updateProject()` adds new project
- [ ] Test: `updateProject()` updates existing project
- [ ] Test: `updateProject()` with None removes project
- [ ] Test: `pruneProjects()` removes entries that fail validation

### DashboardService
- [ ] Test: `renderDashboard()` with registered projects and no worktrees still renders projects
- [ ] Test: existing `renderDashboard()` tests still pass (regression check)

## Implementation

- [ ] Implement `ProjectRegistrationService.register()`
- [ ] Implement `ProjectRegistrationService.deregister()`
- [ ] Implement `MainProjectService.resolveProjects()`
- [ ] Implement `ServerStateService.updateProject()`
- [ ] Implement `ServerStateService.pruneProjects()`
- [ ] Update `DashboardService.renderDashboard()` to use `resolveProjects()`

## Verification

- [ ] Run `./iw test unit` — all tests pass
- [ ] No compilation warnings
- [ ] Existing tests show no regression
