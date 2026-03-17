# Phase 1: Schema and Validator

**Issue:** IW-274
**Layers:** Layer 1 (Schema) + Layer 3 (Validator)
**Estimate:** 1-2.5h

## Goals

Add `activity` and `workflow_type` as optional properties to the review-state JSON Schema and enforce their enum constraints in `ReviewStateValidator`. After this phase, the schema formally defines both fields and the validator rejects invalid values while accepting valid ones (including absence).

## Scope

**In scope:**
- Add `activity` property to `review-state.schema.json`
- Add `workflow_type` property to `review-state.schema.json`
- Add status vocabulary documentation to the `status` field description
- Add `"activity"` and `"workflow_type"` to `AllowedRootProperties` in `ReviewStateValidator`
- Add enum validation for `activity` values: `"working"`, `"waiting"`
- Add enum validation for `workflow_type` values: `"agile"`, `"waterfall"`, `"diagnostic"`
- Write unit tests covering all new validation paths

**Out of scope:**
- Domain model changes (`ReviewState.scala`, codecs)
- Builder/Updater changes
- CLI command flag additions
- WorktreeSummary changes
- Documentation updates (llms.txt, skills)

## Dependencies

None. This is the first phase and has no prerequisites.

## Approach

### Step 1: Schema changes (`review-state.schema.json`)

Add two new properties to the root `properties` object:

```json
"activity": {
  "type": "string",
  "enum": ["working", "waiting"],
  "description": "Binary scheduling signal. 'working' = agent actively processing. 'waiting' = blocked on human action. Absent means activity unknown."
},
"workflow_type": {
  "type": "string",
  "enum": ["agile", "waterfall", "diagnostic"],
  "description": "Which workflow is running. Used by external tooling for scheduling and filtering. Absent means workflow type unknown."
}
```

Update the `status` field description to document the canonical vocabulary:

```
"Machine-readable workflow state identifier. Known values: triage, analyzing, analysis_ready, creating_tasks, tasks_ready, context_ready, implementing, awaiting_review, review_failed, phase_merged, all_complete. Free-form string — unlisted values are accepted."
```

Place the new properties after `message` and before `artifacts` to keep semantic grouping (workflow state fields together).

### Step 2: Validator — allow new properties

Add `"activity"` and `"workflow_type"` to the `AllowedRootProperties` set in `ReviewStateValidator.scala`.

### Step 3: Validator — enum validation

Add two new enum constant sets following the `ValidDisplayTypes` pattern:

```scala
private val ValidActivityValues: Set[String] = Set("working", "waiting")
private val ValidWorkflowTypes: Set[String] = Set("agile", "waterfall", "diagnostic")
```

Add validation in `validateOptionalFieldTypes` for both fields:
- `activity`: must be a string, and if present must be one of `ValidActivityValues`
- `workflow_type`: must be a string, and if present must be one of `ValidWorkflowTypes`

Follow the exact pattern used for `display.type` validation (check `strOpt` first, then check enum membership).

### Step 4: Write tests (TDD — tests first, then implementation)

Write failing tests, then implement to make them pass.

## Files to Modify

### `schemas/review-state.schema.json`
- Add `activity` property with string type and `["working", "waiting"]` enum
- Add `workflow_type` property with string type and `["agile", "waterfall", "diagnostic"]` enum
- Update `status` field description with canonical vocabulary

### `.iw/core/model/ReviewStateValidator.scala`
- Add `"activity"` and `"workflow_type"` to `AllowedRootProperties` (line 19-22)
- Add `ValidActivityValues` constant: `Set("working", "waiting")`
- Add `ValidWorkflowTypes` constant: `Set("agile", "waterfall", "diagnostic")`
- Add validation block for `activity` in `validateOptionalFieldTypes` (type check + enum check)
- Add validation block for `workflow_type` in `validateOptionalFieldTypes` (type check + enum check)

### `.iw/core/test/ReviewStateValidatorTest.scala`
- Add tests for valid `activity` values (`"working"`, `"waiting"`)
- Add test for invalid `activity` value (e.g., `"idle"`)
- Add test for wrong type for `activity` (e.g., integer)
- Add test for absent `activity` (already covered by existing minimal-valid test)
- Add tests for valid `workflow_type` values (`"agile"`, `"waterfall"`, `"diagnostic"`)
- Add test for invalid `workflow_type` value (e.g., `"kanban"`)
- Add test for wrong type for `workflow_type` (e.g., integer)
- Add test for absent `workflow_type` (already covered by existing minimal-valid test)
- Add test for full JSON with both new fields present and valid
- Update the "valid full JSON" test to include both new fields

## Testing Strategy

### Unit tests to write

**activity field:**
1. `"activity": "working"` in valid JSON passes validation
2. `"activity": "waiting"` in valid JSON passes validation
3. `"activity": "idle"` returns enum error on field `activity`
4. `"activity": 42` returns type error on field `activity`
5. Absent `activity` passes (already covered by `valid minimal JSON returns no errors`)

**workflow_type field:**
6. `"workflow_type": "agile"` passes validation
7. `"workflow_type": "waterfall"` passes validation
8. `"workflow_type": "diagnostic"` passes validation
9. `"workflow_type": "kanban"` returns enum error on field `workflow_type`
10. `"workflow_type": true` returns type error on field `workflow_type`
11. Absent `workflow_type` passes (already covered)

**Combined:**
12. Update "valid full JSON" test to include `"activity": "working"` and `"workflow_type": "agile"`

### Validation pattern to follow

The existing `display.type` enum validation (lines 281-287 of `ReviewStateValidator.scala`) is the reference pattern:

```scala
obj.get("activity").foreach { v =>
  if !v.strOpt.isDefined then
    errors += ValidationError("activity", "Field 'activity' must be a string")
  else
    val activityValue = v.str
    if !ValidActivityValues.contains(activityValue) then
      errors += ValidationError("activity", s"Field 'activity' must be one of: ${ValidActivityValues.mkString(", ")}")
}
```

Same pattern for `workflow_type`.

### Running tests

```bash
./iw test unit
```

## Acceptance Criteria

1. `review-state.schema.json` defines `activity` with enum `["working", "waiting"]`
2. `review-state.schema.json` defines `workflow_type` with enum `["agile", "waterfall", "diagnostic"]`
3. `status` field description lists canonical vocabulary values
4. `ReviewStateValidator` accepts valid `activity` values and rejects invalid ones
5. `ReviewStateValidator` accepts valid `workflow_type` values and rejects invalid ones
6. `ReviewStateValidator` accepts JSON with both fields absent (backward compatible)
7. `ReviewStateValidator` rejects unknown root properties (existing behavior preserved)
8. All existing tests still pass
9. All new tests pass
10. `./iw test unit` is green
