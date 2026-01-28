# Phase 1 Tasks: JSON Schema formally defines contract

**Issue:** IW-136
**Phase:** 1 of 3

## Setup

- [ ] [setup] Create `schemas/` directory at project root

## Schema Definition

- [ ] [impl] Create `schemas/review-state.schema.json` with JSON Schema Draft-07 structure
- [ ] [impl] Define required fields: version, issue_id, status, artifacts, last_updated
- [ ] [impl] Define optional fields: phase (oneOf integer/string), step, branch, pr_url, git_sha, message, batch_mode, phase_checkpoints, available_actions
- [ ] [impl] Add descriptions for every field
- [ ] [impl] Add examples for complex fields (artifacts, available_actions, phase_checkpoints)
- [ ] [impl] Define nested schemas: artifact object, action object, phase checkpoint object

## Documentation

- [ ] [docs] Create `schemas/README.md` with versioning policy

## Test Fixtures

- [ ] [test] Create `.iw/core/test/resources/review-state/valid-minimal.json` - required fields only
- [ ] [test] Create `.iw/core/test/resources/review-state/valid-full.json` - all fields populated
- [ ] [test] Create `.iw/core/test/resources/review-state/invalid-missing-required.json` - missing required fields
- [ ] [test] Create `.iw/core/test/resources/review-state/invalid-wrong-types.json` - wrong field types

## Validation

- [ ] [test] Verify schema file is valid JSON (parseable)
- [ ] [test] Verify all existing review-state.json files in project-management/issues/ are compatible with schema
- [ ] [test] Create BATS E2E test verifying schema file exists and is valid JSON
