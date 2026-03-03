# Phase 4: Presentation Layer

## Goals

Update view text and add tests to verify the complete presentation path for registered projects with zero worktrees. After this phase, the user experience is correct for projects that exist in the registry but have no active worktrees.

## Scope

### In Scope
- Update `ProjectDetailsView.renderNotFound()` text — now only shown for truly unregistered projects (Phase 3 made registered projects bypass this path)
- Update `MainProjectsView` empty state text — mention `./iw register` alongside `./iw start`
- Add tests verifying "0 worktrees" text renders correctly for registered projects
- Add test verifying `renderNotFound()` text is accurate for the unregistered case
- Verify `ProjectDetailsView.render()` empty state includes "Create Worktree" button (existing test coverage check)

### Out of Scope
- HTTP endpoint changes (Phase 3, done)
- Dashboard rendering logic (Phase 2, done)
- CaskServer projectDetails() lookup (Phase 3, done)
- CLI integration (Phase 5)

## Dependencies

- Phase 1 (Domain Layer): `ProjectRegistration`
- Phase 2 (Application Layer): `MainProjectService.resolveProjects()`, `DashboardService.renderDashboard()`
- Phase 3 (Infrastructure Layer): `CaskServer.projectDetails()` falls back to registered projects

## What Was Built in Prior Phases

### Phase 1 (Domain Layer)
- `ProjectRegistration` case class
- `ServerState.projects` field

### Phase 2 (Application Layer)
- `MainProjectService.resolveProjects()` merges registered + derived projects
- `DashboardService.renderDashboard()` accepts `registeredProjects` param, uses `resolveProjects()`
- `CaskServer.dashboard()` passes `state.projects` to `renderDashboard()`

### Phase 3 (Infrastructure Layer)
- `CaskServer.projectDetails()` falls back to registered projects when no worktrees derive to matching project name
- Auto-pruning of stale project registrations on dashboard load

## Approach

### 1. ProjectDetailsView.renderNotFound() text update

Current text: "No worktrees are registered for project '$projectName'."
This is misleading because the page now only shows for truly unregistered projects. Update to:
"Project '$projectName' is not registered."
And add: "Run './iw register' from the project directory to register it."

### 2. MainProjectsView empty state text update

Current text: "Run './iw start <issue-id>' from a project directory to register it"
Update to also mention `./iw register`:
"Run './iw register' from a project directory, or './iw start <issue-id>' to create a worktree"

### 3. Tests

- Verify `MainProjectsView` renders "0 worktrees" for projects with `worktreeCount = 0`
- Verify `renderNotFound()` text mentions registration
- Verify existing `ProjectDetailsView.render()` empty state includes "Create Worktree" button

## Files to Modify
- `.iw/core/dashboard/presentation/views/ProjectDetailsView.scala` — update `renderNotFound()` text
- `.iw/core/dashboard/presentation/views/MainProjectsView.scala` — update empty state text
- `.iw/core/test/ProjectDetailsViewTest.scala` — update renderNotFound test assertions
- `.iw/core/test/MainProjectsViewTest.scala` — add "0 worktrees" text test

## Testing Strategy

Unit tests only — view rendering is pure (no I/O).

## Acceptance Criteria

- [ ] `renderNotFound()` text accurately describes the unregistered project case
- [ ] Empty state in MainProjectsView mentions `./iw register`
- [ ] "0 worktrees" text renders for projects with zero worktrees
- [ ] All existing tests pass (no regression)
- [ ] `./iw test unit` passes
