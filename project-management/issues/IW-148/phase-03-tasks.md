# Phase 3: Infrastructure Layer — Tasks

## Setup

- [ ] Read existing `CaskServer.scala`, `ServerClient.scala`, `CaskServerTest.scala` patterns

## Tests First (TDD)

### CaskServer — PUT /api/v1/projects/:projectName
- [ ] Test: PUT registers new project → 201 with correct response JSON
- [ ] Test: PUT updates existing project → 200
- [ ] Test: PUT with missing required field → 400 MISSING_FIELD
- [ ] Test: PUT with malformed JSON body → 400 MALFORMED_JSON
- [ ] Test: PUT with empty path → 400 VALIDATION_ERROR

### CaskServer — project details with registered projects
- [ ] Test: GET /projects/:projectName for registered project with no worktrees → 200

### CaskServer — project pruning
- [ ] Test: dashboard auto-prunes projects whose path does not exist on disk

### ServerClient.registerProject
- [ ] Test: registerProject returns Right(()) when server is disabled

## Implementation

- [ ] Implement `CaskServer.registerProject()` endpoint
- [ ] Add project pruning to `CaskServer.dashboard()`
- [ ] Update `CaskServer.projectDetails()` to use registered projects for lookup
- [ ] Implement `ServerClient.registerProject()` method

## Verification

- [ ] Run `./iw test unit` — all tests pass
- [ ] No compilation warnings
- [ ] Existing tests show no regression

**Phase Status:** Not Started
