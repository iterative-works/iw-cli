# Phase 3 Tasks: Builder and Updater — new field support

## Setup
- [ ] [setup] Read `ReviewStateBuilder.scala` and `ReviewStateBuilderTest.scala` to understand existing `BuildInput` fields and JSON construction patterns (reference: `input.status.foreach(v => obj("status") = ujson.Str(v))` at line 37)
- [ ] [setup] Read `ReviewStateUpdater.scala` and `ReviewStateUpdaterTest.scala` to understand existing `UpdateInput` fields, clear flags, and merge patterns (reference: `update.status` block at lines 70-74)

## Tests (TDD - write first)

### Builder tests — activity field
- [ ] [test] Write test: `activity = Some("working")` writes `"activity": "working"` in output JSON (in `ReviewStateBuilderTest.scala`)
- [ ] [test] Write test: `activity = Some("waiting")` writes `"activity": "waiting"` in output JSON

### Builder tests — workflowType field
- [ ] [test] Write test: `workflowType = Some("agile")` writes `"workflow_type": "agile"` in output JSON
- [ ] [test] Write test: `workflowType = Some("waterfall")` writes `"workflow_type": "waterfall"` in output JSON
- [ ] [test] Write test: `workflowType = Some("diagnostic")` writes `"workflow_type": "diagnostic"` in output JSON

### Builder tests — combined and existing test updates
- [ ] [test] Write test: build with both `activity` and `workflowType` set produces valid JSON with both keys
- [ ] [test] Update "build with all fields includes optional fields" test to include `activity = Some("working")` and `workflowType = Some("agile")`, assert both values in parsed JSON
- [ ] [test] Update "built JSON passes ReviewStateValidator.validate()" test to include `activity = Some("working")` and `workflowType = Some("agile")`
- [ ] [test] Update "optional fields are omitted when not provided" test to assert `activity` and `workflow_type` are absent

### Updater tests — activity merge
- [ ] [test] Write test: `activity = Some("working")` sets `"activity": "working"` in output JSON (in `ReviewStateUpdaterTest.scala`)
- [ ] [test] Write test: `activity = Some("waiting")` replaces existing `"activity": "working"` with `"activity": "waiting"`
- [ ] [test] Write test: `clearActivity = true` with no `activity` value removes `"activity"` key from existing JSON
- [ ] [test] Write test: `clearActivity = true` with `activity = Some(...)` removes the key (clear wins)

### Updater tests — workflowType merge
- [ ] [test] Write test: `workflowType = Some("agile")` sets `"workflow_type": "agile"` in output JSON
- [ ] [test] Write test: `workflowType = Some("waterfall")` replaces existing `"workflow_type": "agile"`
- [ ] [test] Write test: `clearWorkflowType = true` with no `workflowType` value removes `"workflow_type"` key
- [ ] [test] Write test: `clearWorkflowType = true` with `workflowType = Some(...)` removes the key (clear wins)

### Updater tests — preservation and validation
- [ ] [test] Write test: existing `"activity"` and `"workflow_type"` are preserved when not mentioned in `UpdateInput`
- [ ] [test] Update "merged result passes ReviewStateValidator" test to include `activity` and `workflowType` fields

### Run tests to confirm they fail
- [ ] [verify] Run `./iw test unit` — all new tests must fail (fields not yet in `BuildInput`/`UpdateInput`), existing tests must pass

## Implementation

### Builder changes (`ReviewStateBuilder.scala`)
- [ ] [impl] Add `activity: Option[String] = None` to `BuildInput` (after `phaseCheckpoints`, line 22)
- [ ] [impl] Add `workflowType: Option[String] = None` to `BuildInput` (after `activity`)
- [ ] [impl] Add `input.activity.foreach(v => obj("activity") = ujson.Str(v))` in `build()` (after `input.message` line 59, before `input.actions` block line 61)
- [ ] [impl] Add `input.workflowType.foreach(v => obj("workflow_type") = ujson.Str(v))` in `build()` (after `activity` line)

### Updater changes (`ReviewStateUpdater.scala`)
- [ ] [impl] Add `activity: Option[String] = None` to `UpdateInput` scalar fields section (after `gitSha`, line 14)
- [ ] [impl] Add `workflowType: Option[String] = None` to `UpdateInput` (after `activity`)
- [ ] [impl] Add `clearActivity: Boolean = false` to `UpdateInput` clear flags section (after `clearDisplay`, line 43)
- [ ] [impl] Add `clearWorkflowType: Boolean = false` to `UpdateInput` (after `clearActivity`)
- [ ] [impl] Add merge logic for `activity` with `clearActivity` in `merge()` (after `prUrl` block line 92, following the `status` pattern at lines 70-74)
- [ ] [impl] Add merge logic for `workflowType` with `clearWorkflowType` in `merge()` (after `activity` block, using JSON key `"workflow_type"`)

## Verification
- [ ] [verify] Run `./iw test unit` — all tests (existing + new) must pass
- [ ] [verify] Run `./iw test e2e` — no regressions in end-to-end tests
- [ ] [verify] Commit all changes with a descriptive message referencing IW-274
