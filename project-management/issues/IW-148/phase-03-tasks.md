# Phase 3: Infrastructure Layer — Tasks

## Setup

- [x] Read existing `CaskServer.scala`, `ServerClient.scala`, `CaskServerTest.scala` patterns

## Tests First (TDD)

### CaskServer — PUT /api/v1/projects/:projectName
- [x] Test: PUT registers new project → 201 with correct response JSON
- [x] Test: PUT updates existing project → 200
- [x] Test: PUT with missing required field → 400 MISSING_FIELD
- [x] Test: PUT with malformed JSON body → 400 MALFORMED_JSON
- [x] Test: PUT with empty path → 400 VALIDATION_ERROR

### CaskServer — project details with registered projects
- [x] Test: GET /projects/:projectName for registered project with no worktrees → 200

### CaskServer — project pruning
- [x] Test: dashboard auto-prunes projects whose path does not exist on disk

### ServerClient.registerProject
- [x] Test: registerProject returns Right(()) when server is disabled

## Implementation

- [x] Implement `CaskServer.registerProject()` endpoint
- [x] Add project pruning to `CaskServer.dashboard()`
- [x] Update `CaskServer.projectDetails()` to use registered projects for lookup
- [x] Implement `ServerClient.registerProject()` method

## Verification

- [x] Run `./iw test unit` — all tests pass
- [x] No compilation warnings
- [x] Existing tests show no regression

**Phase Status:** Complete
