# Phase 1 Tasks: JSON Schema formally defines contract

**Issue:** IW-136
**Phase:** 1 of 3

## Setup

- [x] [setup] Create `schemas/` directory at project root

## Schema Definition

- [x] [impl] Create `schemas/review-state.schema.json` with JSON Schema Draft-07 structure
- [x] [impl] Define required fields: version, issue_id, status, artifacts, last_updated
- [x] [impl] Define optional fields: phase (oneOf integer/string), step, branch, pr_url, git_sha, message, batch_mode, phase_checkpoints, available_actions
- [x] [impl] Add descriptions for every field
- [x] [impl] Add examples for complex fields (artifacts, available_actions, phase_checkpoints)
- [x] [impl] Define nested schemas: artifact object, action object, phase checkpoint object

## Documentation

- [x] [docs] Create `schemas/README.md` with versioning policy

## Test Fixtures

- [x] [test] Create `.iw/core/test/resources/review-state/valid-minimal.json` - required fields only
- [x] [test] Create `.iw/core/test/resources/review-state/valid-full.json` - all fields populated
- [x] [test] Create `.iw/core/test/resources/review-state/invalid-missing-required.json` - missing required fields
- [x] [test] Create `.iw/core/test/resources/review-state/invalid-wrong-types.json` - wrong field types

## Validation

- [x] [test] Verify schema file is valid JSON (parseable)
- [x] [test] Verify all existing review-state.json files in project-management/issues/ are compatible with schema
- [x] [test] Create BATS E2E test verifying schema file exists and is valid JSON
