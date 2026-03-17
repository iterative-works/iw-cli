# Phase 1 Tasks: Schema and Validator

## Setup
- [ ] [setup] Read `ReviewStateValidator.scala`, `ReviewStateValidatorTest.scala`, and `review-state.schema.json` to understand existing patterns for enum validation (reference: `ValidDisplayTypes` and `display.type` validation in `validateDisplay`)

## Tests (TDD - write first)

### activity field
- [ ] [test] Write test: `"activity": "working"` in valid JSON passes validation (in `ReviewStateValidatorTest.scala`)
- [ ] [test] Write test: `"activity": "waiting"` in valid JSON passes validation
- [ ] [test] Write test: `"activity": "idle"` returns enum error on field `activity`
- [ ] [test] Write test: `"activity": 42` returns type error on field `activity`

### workflow_type field
- [ ] [test] Write test: `"workflow_type": "agile"` passes validation
- [ ] [test] Write test: `"workflow_type": "waterfall"` passes validation
- [ ] [test] Write test: `"workflow_type": "diagnostic"` passes validation
- [ ] [test] Write test: `"workflow_type": "kanban"` returns enum error on field `workflow_type`
- [ ] [test] Write test: `"workflow_type": true` returns type error on field `workflow_type`

### Combined
- [ ] [test] Update "valid full JSON" test to include `"activity": "working"` and `"workflow_type": "agile"` (add fields between `"message"` and `"artifacts"`)

### Run tests to confirm they fail
- [ ] [verify] Run `./iw test unit` — all new tests must fail (activity/workflow_type rejected as unknown properties), existing tests must pass

## Implementation

### Schema JSON (`schemas/review-state.schema.json`)
- [ ] [impl] Add `activity` property to `properties` object — `{"type": "string", "enum": ["working", "waiting"], "description": "..."}` — place after `message`, before `artifacts`
- [ ] [impl] Add `workflow_type` property to `properties` object — `{"type": "string", "enum": ["agile", "waterfall", "diagnostic"], "description": "..."}` — place after `activity`
- [ ] [impl] Update `status` field description to list canonical vocabulary: `triage`, `analyzing`, `analysis_ready`, `creating_tasks`, `tasks_ready`, `context_ready`, `implementing`, `awaiting_review`, `review_failed`, `phase_merged`, `all_complete`

### Validator — allow new properties (`ReviewStateValidator.scala`)
- [ ] [impl] Add `"activity"` and `"workflow_type"` to `AllowedRootProperties` set (line 19-22)

### Validator — enum constants (`ReviewStateValidator.scala`)
- [ ] [impl] Add `ValidActivityValues: Set[String] = Set("working", "waiting")` constant after `ValidDisplayTypes`
- [ ] [impl] Add `ValidWorkflowTypes: Set[String] = Set("agile", "waterfall", "diagnostic")` constant after `ValidActivityValues`

### Validator — enum validation (`ReviewStateValidator.scala`)
- [ ] [impl] Add `activity` validation block in `validateOptionalFieldTypes` — check `strOpt` for type, then check `ValidActivityValues` for enum membership (follow `display.type` pattern from `validateDisplay` lines 280-287)
- [ ] [impl] Add `workflow_type` validation block in `validateOptionalFieldTypes` — check `strOpt` for type, then check `ValidWorkflowTypes` for enum membership (same pattern)

## Integration
- [ ] [verify] Run `./iw test unit` — all tests (existing + new) must pass
- [ ] [verify] Run `./iw test e2e` — no regressions in end-to-end tests
- [ ] [verify] Commit all changes with a descriptive message referencing IW-274
