# Phase 1: Domain Layer

## Goals

Add a `ProjectRegistration` value object and extend `ServerState` and its codec to store registered projects independently from issue worktrees. This phase provides the foundation for all subsequent layers.

## Scope

### In Scope
- `ProjectRegistration` case class with `create()` validation factory
- `ServerState.projects` field (`Map[String, ProjectRegistration]`)
- `ServerState` pure functions for project management (list, remove)
- `ServerStateCodec` and `StateJson` extensions for project serialization
- Backward-compatible deserialization (missing `projects` field defaults to `Map.empty`)
- Tests for all of the above

### Out of Scope
- Application services (Phase 2)
- HTTP endpoints (Phase 3)
- Dashboard views (Phase 4)
- CLI command changes (Phase 5)

## Dependencies

- No dependencies on other phases (this is the foundation layer)

## Approach

Follow the existing `WorktreeRegistration` pattern exactly:

### 1. ProjectRegistration (`model/ProjectRegistration.scala`)

New file. Case class with fields:
- `path: String` — absolute filesystem path to the main project directory (also the map key in `ServerState.projects`)
- `projectName: String` — display name (typically last path component, e.g., "iw-cli")
- `trackerType: String` — tracker type string (e.g., "linear", "github")
- `team: String` — team identifier (e.g., "IWLE", "iterative-works/iw-cli")
- `trackerUrl: Option[String]` — optional issue tracker URL
- `registeredAt: Instant` — when the project was first registered

Companion object with `create()` validation factory returning `Either[String, ProjectRegistration]`. Validates:
- `path` is non-empty after trim
- `projectName` is non-empty after trim
- `trackerType` is non-empty after trim
- `team` is non-empty after trim

Pattern: identical to `WorktreeRegistration.create()` (see `.iw/core/model/WorktreeRegistration.scala`).

### 2. ServerState Extension (`model/ServerState.scala`)

Add field:
```scala
projects: Map[String, ProjectRegistration] = Map.empty
```

Default value ensures backward compatibility with existing `ServerState(worktrees = ...)` construction.

Add pure functions:
- `listProjects: List[ProjectRegistration]` — returns projects sorted by `projectName` (same pattern as `listByIssueId`)
- `removeProject(path: String): ServerState` — removes project by path key (no cascade to caches — projects have no associated caches)

### 3. ServerStateCodec Extension (`model/ServerStateCodec.scala`)

Add:
```scala
given ReadWriter[ProjectRegistration] = macroRW[ProjectRegistration]
```

Extend `StateJson`:
```scala
case class StateJson(
  worktrees: Map[String, WorktreeRegistration],
  issueCache: Map[String, CachedIssue] = Map.empty,
  progressCache: Map[String, CachedProgress] = Map.empty,
  prCache: Map[String, CachedPR] = Map.empty,
  reviewStateCache: Map[String, CachedReviewState] = Map.empty,
  projects: Map[String, ProjectRegistration] = Map.empty  // new field
)
```

Default value `Map.empty` ensures backward-compatible deserialization of existing `state.json` files that lack the `projects` key.

### 4. StateRepository and ServerStateService

These files convert between `ServerState` and `StateJson`. They construct both types by listing all fields positionally. After adding `projects` to both, these constructors need the extra field.

In `StateRepository.scala`:
- `read()`: add `stateJson.projects` to `ServerState(...)` constructor
- `write()`: add `state.projects` to `StateJson(...)` constructor

In `ServerStateService.scala`:
- Update the initial empty state: `ServerState(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty)` → add `Map.empty` for projects
- Same for `read()` in the companion object

Note: This is mechanical — just threading the new field through existing constructors. The alternative (using named parameters everywhere) would be a larger refactoring not warranted here since the existing code uses positional.

## Files to Create
- `.iw/core/model/ProjectRegistration.scala`
- `.iw/core/test/ProjectRegistrationTest.scala`

## Files to Modify
- `.iw/core/model/ServerState.scala` — add `projects` field, `listProjects`, `removeProject`
- `.iw/core/model/ServerStateCodec.scala` — add `ReadWriter[ProjectRegistration]`, extend `StateJson`
- `.iw/core/dashboard/StateRepository.scala` — thread `projects` through read/write
- `.iw/core/dashboard/ServerStateService.scala` — thread `projects` through empty state constructors
- `.iw/core/test/ServerStateTest.scala` — add tests for projects field
- `.iw/core/test/ServerStateCodecTest.scala` — add roundtrip and backward-compat tests
- `.iw/core/test/TestFixtures.scala` — add sample project registrations to `SampleData`

## Testing Strategy

**ProjectRegistration tests:**
- `create()` with valid inputs → `Right(ProjectRegistration(...))`
- `create()` with empty path → `Left("...")`
- `create()` with empty projectName → `Left("...")`
- `create()` with empty trackerType → `Left("...")`
- `create()` with empty team → `Left("...")`
- Direct construction and field access

**ServerState tests:**
- `ServerState` with `projects = Map.empty` (default)
- `listProjects` returns projects sorted by `projectName`
- `removeProject` removes entry
- `removeProject` is idempotent for non-existent key
- Existing worktree tests still pass (no regression)

**ServerStateCodec tests:**
- `ProjectRegistration` roundtrip serialization
- Full `StateJson` with both worktrees and projects — roundtrip
- Backward compatibility: JSON without `projects` key parses to `Map.empty`
- Backward compatibility: JSON with `projects` key and JSON without it both parse correctly

**TestFixtures:**
- Add 2-3 sample `ProjectRegistration` entries to `SampleData`

## Acceptance Criteria

- [ ] `ProjectRegistration` case class exists with `create()` validation
- [ ] `ServerState.projects` field with default `Map.empty`
- [ ] `ServerState.listProjects` and `removeProject` pure functions
- [ ] `ServerStateCodec` handles `ProjectRegistration` serialization
- [ ] `StateJson.projects` field with default `Map.empty`
- [ ] `StateRepository` threads `projects` through read/write
- [ ] `ServerStateService` threads `projects` through empty state
- [ ] All existing tests pass (no regression)
- [ ] All new tests pass
- [ ] `./iw test unit` passes
