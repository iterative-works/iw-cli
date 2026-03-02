# Phase 1: Domain Layer — Tasks

## Setup

- [ ] Create `ProjectRegistration.scala` with PURPOSE comments and package declaration
- [ ] Create `ProjectRegistrationTest.scala` with test class skeleton

## Tests First (TDD)

### ProjectRegistration
- [ ] Test: direct construction with all fields and field access
- [ ] Test: `create()` with valid inputs returns `Right(ProjectRegistration(...))`
- [ ] Test: `create()` with empty path returns `Left`
- [ ] Test: `create()` with empty projectName returns `Left`
- [ ] Test: `create()` with empty trackerType returns `Left`
- [ ] Test: `create()` with empty team returns `Left`
- [ ] Test: `create()` with whitespace-only path returns `Left`

### ServerState Projects
- [ ] Test: `ServerState` with default empty projects map
- [ ] Test: `listProjects` returns projects sorted by `projectName`
- [ ] Test: `listProjects` with multiple projects uses alphabetical sort
- [ ] Test: `removeProject` removes entry by path key
- [ ] Test: `removeProject` is idempotent for non-existent key
- [ ] Test: existing worktree tests still pass (verify no regression)

### ServerStateCodec
- [ ] Test: `ProjectRegistration` roundtrip serialization
- [ ] Test: full `StateJson` with worktrees AND projects — roundtrip
- [ ] Test: backward compatibility — JSON without `projects` key parses with empty map
- [ ] Test: `StateJson` with projects field roundtrips correctly

### TestFixtures
- [ ] Add 2-3 sample `ProjectRegistration` entries to `SampleData`

## Implementation

- [ ] Implement `ProjectRegistration` case class with fields: path, projectName, trackerType, team, trackerUrl (Option), registeredAt (Instant)
- [ ] Implement `ProjectRegistration.create()` validation factory
- [ ] Add `projects: Map[String, ProjectRegistration] = Map.empty` to `ServerState`
- [ ] Add `listProjects: List[ProjectRegistration]` to `ServerState`
- [ ] Add `removeProject(path: String): ServerState` to `ServerState`
- [ ] Add `given ReadWriter[ProjectRegistration]` to `ServerStateCodec`
- [ ] Add `projects` field to `StateJson`
- [ ] Thread `projects` through `StateRepository.read()` and `write()`
- [ ] Thread `projects` through `ServerStateService` empty state constructors

## Verification

- [ ] Run `./iw test unit` — all tests pass
- [ ] No compilation warnings
- [ ] Existing tests show no regression
