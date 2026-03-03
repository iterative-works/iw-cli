# Phase 2: Application Layer

## Goals

Add business logic for project registration and extend MainProjectService to merge registered projects with worktree-derived projects. After this phase, the dashboard receives a complete project list regardless of whether projects have worktrees.

## Scope

### In Scope
- `ProjectRegistrationService` — pure business logic for registering/deregistering projects (parallel to `WorktreeRegistrationService`)
- `MainProjectService.resolveProjects()` — merge registered + derived projects, deduplicate by path
- `ServerStateService.updateProject()` — thread-safe project registration persistence (following `updateWorktree()` pattern)
- `ServerStateService.pruneProjects()` — remove stale project registrations (following `pruneWorktrees()` pattern)
- Tests for all of the above

### Out of Scope
- HTTP endpoints (Phase 3)
- View updates (Phase 4)
- CLI integration (Phase 5)

## Dependencies

- Phase 1 (Domain Layer): `ProjectRegistration`, `ServerState.projects`, codec support

## What Was Built in Phase 1

- `ProjectRegistration` case class with `create()` validation factory
- `ServerState.projects: Map[String, ProjectRegistration]` field with `listProjects` and `removeProject`
- Full serialization support via `ServerStateCodec`
- `StateRepository` and `ServerStateService` thread `projects` through read/write

## Approach

### 1. ProjectRegistrationService (new file: `.iw/core/dashboard/ProjectRegistrationService.scala`)

Pure business logic, following `WorktreeRegistrationService` pattern exactly.

**`register(path, projectName, trackerType, team, trackerUrl, timestamp, state)`:**
- If project exists at path: update fields, preserve `registeredAt`
- If project is new: create with `registeredAt = timestamp`
- Use `ProjectRegistration.create()` for validation
- Return `Either[String, (ServerState, Boolean)]` where `Boolean` = wasCreated

**`deregister(path, state)`:**
- Remove project from `state.projects`
- Return `ServerState`

### 2. MainProjectService extension (`.iw/core/dashboard/application/MainProjectService.scala`)

Add `resolveProjects()` method:

```scala
def resolveProjects(
  worktrees: List[WorktreeRegistration],
  registeredProjects: Map[String, ProjectRegistration],
  loadConfig: os.Path => Either[String, ProjectConfiguration]
): List[MainProject]
```

Logic:
1. Derive projects from worktrees (existing `deriveFromWorktrees()`)
2. Convert registered projects to `MainProject` instances
3. Merge both lists, deduplicating by path (worktree-derived takes precedence for metadata since config is fresher)
4. Return merged list

This preserves the existing `deriveFromWorktrees()` method unchanged.

### 3. ServerStateService extension (`.iw/core/dashboard/ServerStateService.scala`)

Add `updateProject()` method following the `updateWorktree()` pattern:

```scala
def updateProject(path: String)(f: Option[ProjectRegistration] => Option[ProjectRegistration]): Unit
```

Add `pruneProjects()` method following the `pruneWorktrees()` pattern:

```scala
def pruneProjects(isValid: ProjectRegistration => Boolean): Set[String]
```

### 4. DashboardService update (`.iw/core/dashboard/DashboardService.scala`)

Update `renderDashboard()` to use `resolveProjects()` instead of `deriveFromWorktrees()`:
- Pass `state.projects` alongside worktrees
- The rest of the pipeline (computeSummaries, views) stays unchanged since `computeSummaries()` already handles zero-worktree projects

## Files to Create
- `.iw/core/dashboard/ProjectRegistrationService.scala`
- `.iw/core/test/ProjectRegistrationServiceTest.scala`

## Files to Modify
- `.iw/core/dashboard/application/MainProjectService.scala` — add `resolveProjects()`
- `.iw/core/dashboard/ServerStateService.scala` — add `updateProject()`, `pruneProjects()`
- `.iw/core/dashboard/DashboardService.scala` — use `resolveProjects()` in `renderDashboard()`
- `.iw/core/test/MainProjectServiceTest.scala` — add tests for `resolveProjects()`
- `.iw/core/test/ServerStateServiceTest.scala` — add tests for `updateProject()`, `pruneProjects()` (if test file exists)

## Testing Strategy

**ProjectRegistrationService tests:**
- `register()` with new project → creates entry, returns `wasCreated = true`
- `register()` with existing project → updates entry, preserves `registeredAt`, returns `wasCreated = false`
- `register()` with invalid inputs (empty path) → returns `Left`
- `deregister()` removes project
- `deregister()` is idempotent for non-existent project

**MainProjectService.resolveProjects() tests:**
- No worktrees, no registered → empty list
- Worktrees only → same as `deriveFromWorktrees()`
- Registered only (no worktrees) → registered projects appear
- Both registered and derived → merged with deduplication by path
- Registered project with zero worktrees appears in output

**ServerStateService tests:**
- `updateProject()` adds new project
- `updateProject()` updates existing project
- `updateProject()` removes project (returns None)
- `pruneProjects()` removes invalid entries

**DashboardService tests:**
- `renderDashboard()` with registered projects and no worktrees → projects appear
- Existing tests still pass (no regression)

## Acceptance Criteria

- [ ] `ProjectRegistrationService` exists with `register()` and `deregister()`
- [ ] `MainProjectService.resolveProjects()` merges registered and derived projects
- [ ] `ServerStateService.updateProject()` and `pruneProjects()` work
- [ ] `DashboardService.renderDashboard()` uses merged project list
- [ ] All existing tests pass (no regression)
- [ ] All new tests pass
- [ ] `./iw test unit` passes
