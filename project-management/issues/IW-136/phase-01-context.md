# Phase 1: JSON Schema formally defines contract

**Issue:** IW-136
**Phase:** 1 of 3
**Story:** JSON Schema formally defines contract

## Goals

Establish a formal JSON Schema (Draft-07) defining the review-state.json contract. This becomes the authoritative reference for all producers (workflow tools) and consumers (dashboard, CLI commands).

Create documentation explaining the versioning policy for schema evolution.

## Scope

### In Scope
- `schemas/review-state.schema.json` - JSON Schema Draft-07 file
- `schemas/README.md` - Versioning policy and field documentation
- Test fixtures with valid and invalid review-state.json examples
- Meta-validation: the schema file itself must be valid JSON Schema

### Out of Scope
- Code changes (no Scala code in this phase)
- Validation command (Phase 2)
- Write command (Phase 3)

## Dependencies

- No previous phase dependencies (this is Phase 1)
- Analysis decisions for field types, required/optional fields, and status enum

## Technical Approach

### Schema Definition

Create JSON Schema Draft-07 at `schemas/review-state.schema.json` with:

**Required fields:**
- `version` - integer, minimum 1 (schema version for evolution)
- `issue_id` - string (e.g., "IW-136")
- `status` - string (open enum, known values documented)
- `artifacts` - array of artifact objects (may be empty)
- `last_updated` - string, ISO 8601 format

**Optional fields:**
- `phase` - integer or string (e.g., 2 or "final" or "1-R1")
- `step` - string (e.g., "analysis", "tasks", "implementation", "review", "complete")
- `branch` - string (git branch name)
- `pr_url` - string (PR URL)
- `git_sha` - string (commit SHA)
- `message` - string (human-readable description)
- `batch_mode` - boolean
- `phase_checkpoints` - object (map of phase number strings to checkpoint objects)
- `available_actions` - array of action objects

**Artifact object schema:**
- `label` - string, required
- `path` - string, required

**Action object schema:**
- `id` - string, required
- `label` - string, required
- `skill` - string, required

**Phase checkpoint object schema:**
- `context_sha` - string, required

### Status Values (Open Enum)

Document known values but accept any string:
- `analysis_ready`, `context_ready`, `tasks_ready`
- `implementing`, `awaiting_review`, `review_failed`
- `phase_merged`, `refactoring_complete`, `all_complete`

The schema validates that status is a string but does NOT restrict to enum values (open enum approach per Decision 7).

### Versioning Policy (README.md)

Document:
- Current schema version: 1
- Version bumps only for breaking changes (removed field, changed type)
- Adding optional fields does NOT require version bump
- Schema location: `schemas/review-state.schema.json`

### Test Fixtures

Create test fixture files for use by Phase 2 validation:
- Valid: minimal (required fields only), full (all fields), empty artifacts
- Invalid: missing required fields, wrong types, malformed JSON

## Files to Create

1. `schemas/review-state.schema.json` - The formal schema
2. `schemas/README.md` - Versioning policy documentation
3. `.iw/core/test/resources/review-state/valid-minimal.json` - Minimal valid state
4. `.iw/core/test/resources/review-state/valid-full.json` - Full valid state
5. `.iw/core/test/resources/review-state/invalid-missing-required.json` - Missing required fields
6. `.iw/core/test/resources/review-state/invalid-wrong-types.json` - Wrong field types

## Testing Strategy

- Meta-validate the schema file (verify it's valid JSON Schema Draft-07)
- Validate all existing review-state.json files in project-management/issues/ against the schema
- Validate test fixtures: valid ones pass, invalid ones fail
- E2E test (BATS): Verify schema file exists at expected location and is valid JSON

## Acceptance Criteria

- [ ] Schema file exists at `schemas/review-state.schema.json`
- [ ] Schema uses JSON Schema Draft-07 (`$schema` property)
- [ ] All fields from analysis are defined with correct types
- [ ] Required fields: version, issue_id, status, artifacts, last_updated
- [ ] `available_actions` included as optional array of action objects
- [ ] `phase` accepts both integer and string (oneOf)
- [ ] Each field has a `description` property
- [ ] Examples provided for complex fields (artifacts, available_actions, phase_checkpoints)
- [ ] `schemas/README.md` documents versioning policy
- [ ] Test fixtures created for valid and invalid cases
- [ ] Existing review-state.json files in codebase are compatible with schema
- [ ] Schema file is itself valid JSON
