# Phase 3: Infrastructure Layer

## Goals

Add HTTP endpoint for project registration and extend `ServerClient` for CLI-to-server communication. After this phase, projects can be registered via HTTP API and the dashboard auto-prunes stale project registrations.

## Scope

### In Scope
- `CaskServer` extension — `PUT /api/v1/projects/:projectName` endpoint (parallel to `PUT /api/v1/worktrees/:issueId`)
- `ServerClient.registerProject()` — CLI-to-server HTTP client method (parallel to `registerWorktree()`)
- Auto-pruning of stale project registrations in `CaskServer.dashboard()` (parallel to existing worktree pruning)
- `CaskServer.projectDetails()` update — use `resolveProjects()` for project lookup (supports registered projects with zero worktrees)
- Tests for all of the above

### Out of Scope
- View updates for zero-worktree projects (Phase 4)
- CLI command integration (Phase 5)
- `StateRepository` changes (already done in Phase 1)
- `ServerStateService.updateProject()` / `pruneProjects()` (already done in Phase 2)

## Dependencies

- Phase 1 (Domain Layer): `ProjectRegistration`, `ServerState.projects`, codec support
- Phase 2 (Application Layer): `ProjectRegistrationService.register()` / `deregister()`, `MainProjectService.resolveProjects()`, `ServerStateService.updateProject()` / `pruneProjects()`

## What Was Built in Prior Phases

### Phase 1 (Domain Layer)
- `ProjectRegistration` case class with `create()` validation factory
- `ServerState.projects: Map[String, ProjectRegistration]` field with `listProjects` and `removeProject`
- Full serialization support via `ServerStateCodec`
- `StateRepository` and `ServerStateService` thread `projects` through read/write

### Phase 2 (Application Layer)
- `ProjectRegistrationService.register()` — pure business logic for registering/deregistering projects
- `ProjectRegistrationService.deregister()` — removes project from state
- `MainProjectService.resolveProjects()` — merges registered + derived projects, deduplicates by path
- `ServerStateService.updateProject(path)(f)` — thread-safe project update
- `ServerStateService.pruneProjects(isValid)` — removes stale project registrations
- `DashboardService.renderDashboard()` now accepts `registeredProjects` and uses `resolveProjects()`
- `CaskServer.dashboard()` already passes `state.projects` to `renderDashboard()`

## Approach

### 1. CaskServer — PUT /api/v1/projects/:projectName endpoint

Follow the exact pattern of `PUT /api/v1/worktrees/:issueId` (line 388-490 of CaskServer.scala).

```scala
@cask.put("/api/v1/projects/:projectName")
def registerProject(projectName: String, request: cask.Request): cask.Response[ujson.Value]
```

**Request body:**
```json
{
  "path": "/home/user/projects/my-app",
  "trackerType": "GitHub",
  "team": "iterative-works",
  "trackerUrl": "https://github.com/iterative-works/my-app/issues"  // optional
}
```

**Logic:**
1. Parse JSON body (same error handling pattern as `registerWorktree`)
2. Extract fields: `path`, `trackerType`, `team`, `trackerUrl` (optional)
3. Call `ProjectRegistrationService.register(path, projectName, trackerType, team, trackerUrl, Instant.now(), state)`
4. On success: update state via `stateService.updateProject(path)(_ => Some(registration))`
5. Return 201 (created) or 200 (updated) with JSON response

**Response:**
```json
{
  "status": "registered",
  "projectName": "my-app",
  "path": "/home/user/projects/my-app"
}
```

### 2. CaskServer.dashboard() — auto-prune stale projects

Add project pruning alongside existing worktree pruning (line 39-41 of CaskServer.scala):

```scala
// Auto-prune non-existent projects
val prunedProjectPaths = stateService.pruneProjects(
  p => os.exists(os.Path(p.path, os.pwd))
)
```

### 3. CaskServer.projectDetails() — use resolveProjects for lookup

Currently `projectDetails()` only derives projects from worktrees. When a project is registered but has zero worktrees, it won't be found. Update to also check `state.projects` for the project:

```scala
// Derive project from worktrees OR look up from registered projects
val mainProjectOpt = projects.headOption.orElse {
  // Try registered projects
  state.projects.values.find(_.projectName == projectName).map { reg =>
    MainProject(
      path = os.Path(reg.path),
      projectName = reg.projectName,
      trackerType = reg.trackerType,
      team = reg.team,
      trackerUrl = reg.trackerUrl
    )
  }
}
```

### 4. ServerClient.registerProject() — CLI-to-server HTTP client

Follow the exact pattern of `ServerClient.registerWorktree()`:

```scala
def registerProject(
  projectName: String,
  path: String,
  trackerType: String,
  team: String,
  trackerUrl: Option[String],
  statePath: String = defaultStatePath
): Either[String, Unit]
```

**Logic:**
1. Check `isServerDisabled`
2. `ensureServerRunning(statePath)`
3. Build JSON body with `path`, `trackerType`, `team`, `trackerUrl` (if present)
4. PUT to `http://localhost:$port/api/v1/projects/$projectName`
5. Return `Right(())` on 200/201, `Left(error)` otherwise

## Files to Create
- None (all changes are to existing files)

## Files to Modify
- `.iw/core/dashboard/CaskServer.scala` — add `registerProject` endpoint, add project pruning, update `projectDetails()` to use registered projects
- `.iw/core/adapters/ServerClient.scala` — add `registerProject()` method
- `.iw/core/test/CaskServerTest.scala` — add tests for project registration endpoint
- `.iw/core/test/ServerClientTest.scala` — add tests for `registerProject()` (if practical)

## Testing Strategy

**CaskServer endpoint tests (integration — real server, real state):**
- `PUT /api/v1/projects/:projectName` registers new project → 201
- `PUT /api/v1/projects/:projectName` updates existing project → 200
- `PUT /api/v1/projects/:projectName` with missing field → 400
- `PUT /api/v1/projects/:projectName` with malformed JSON → 400
- `PUT /api/v1/projects/:projectName` with empty path → 400 (validation error)

**CaskServer project details (integration):**
- `GET /projects/:projectName` for registered project with no worktrees → 200 (not 404)

**ServerClient tests (if practical — may need server mock or skip):**
- `registerProject()` returns `Right(())` when server disabled
- Note: Full integration tests for ServerClient require a running server; the endpoint tests above cover the HTTP contract

## Acceptance Criteria

- [ ] `PUT /api/v1/projects/:projectName` registers and updates projects
- [ ] Dashboard auto-prunes stale project registrations on load
- [ ] `projectDetails()` finds registered projects with zero worktrees
- [ ] `ServerClient.registerProject()` exists and follows `registerWorktree()` pattern
- [ ] All existing tests pass (no regression)
- [ ] All new tests pass
- [ ] `./iw test unit` passes
