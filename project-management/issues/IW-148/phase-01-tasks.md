# Phase 1: Domain Layer — Tasks

## Setup

- [x] Create `ProjectRegistration.scala` with PURPOSE comments and package declaration
- [x] Create `ProjectRegistrationTest.scala` with test class skeleton

## Tests First (TDD)

### ProjectRegistration
- [x] Test: direct construction with all fields and field access
- [x] Test: `create()` with valid inputs returns `Right(ProjectRegistration(...))`
- [x] Test: `create()` with empty path returns `Left`
- [x] Test: `create()` with empty projectName returns `Left`
- [x] Test: `create()` with empty trackerType returns `Left`
- [x] Test: `create()` with empty team returns `Left`
- [x] Test: `create()` with whitespace-only path returns `Left`

### ServerState Projects
- [x] Test: `ServerState` with default empty projects map
- [x] Test: `listProjects` returns projects sorted by `projectName`
- [x] Test: `listProjects` with multiple projects uses alphabetical sort
- [x] Test: `removeProject` removes entry by path key
- [x] Test: `removeProject` is idempotent for non-existent key
- [x] Test: existing worktree tests still pass (verify no regression)

### ServerStateCodec
- [x] Test: `ProjectRegistration` roundtrip serialization
- [x] Test: full `StateJson` with worktrees AND projects — roundtrip
- [x] Test: backward compatibility — JSON without `projects` key parses with empty map
- [x] Test: `StateJson` with projects field roundtrips correctly

### TestFixtures
- [x] Add 2-3 sample `ProjectRegistration` entries to `SampleData`

## Implementation

- [x] Implement `ProjectRegistration` case class with fields: path, projectName, trackerType, team, trackerUrl (Option), registeredAt (Instant)
- [x] Implement `ProjectRegistration.create()` validation factory
- [x] Add `projects: Map[String, ProjectRegistration] = Map.empty` to `ServerState`
- [x] Add `listProjects: List[ProjectRegistration]` to `ServerState`
- [x] Add `removeProject(path: String): ServerState` to `ServerState`
- [x] Add `given ReadWriter[ProjectRegistration]` to `ServerStateCodec`
- [x] Add `projects` field to `StateJson`
- [x] Thread `projects` through `StateRepository.read()` and `write()`
- [x] Thread `projects` through `ServerStateService` empty state constructors

## Verification

- [x] Run `./iw test unit` — all tests pass
- [x] No compilation warnings
- [x] Existing tests show no regression
