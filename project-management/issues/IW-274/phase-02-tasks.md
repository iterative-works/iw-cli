# Phase 2 Tasks: Domain Model — ReviewState fields and codec

## Setup
- [x] [setup] Read `ReviewState.scala` to understand current case class fields and their order
- [x] [setup] Read `ServerStateCodec.scala` to understand the `macroRW[ReviewState]` codec and how other models handle field naming
- [x] [setup] Read `ReviewStateService.scala` to understand the custom `bimap` reader/writer for snake_case JSON (lines 76-107)
- [x] [setup] Read `SampleDataGenerator.scala` to locate all `ReviewState(...)` construction sites in production code
- [x] [setup] Read existing test files (`ServerStateCodecTest.scala`, `ReviewStateServiceTest.scala`, `ReviewStateTest.scala`, `TestFixtures.scala`) to locate all `ReviewState(...)` construction sites in tests

## Tests (TDD - write first)

### ServerStateCodec roundtrip (internal cache, camelCase keys)
- [x] [test] Write test: `ReviewState` with `activity = Some("working")` and `workflowType = Some("agile")` roundtrips correctly through `macroRW` codec
- [x] [test] Write test: `ReviewState` with `activity = None` and `workflowType = None` roundtrips correctly (backward compat — absent keys in JSON)

### ReviewStateService parsing (disk format, snake_case keys)
- [x] [test] Write test: JSON with both `"activity": "working"` and `"workflow_type": "agile"` parses to `activity = Some("working")`, `workflowType = Some("agile")`
- [x] [test] Write test: JSON with neither `"activity"` nor `"workflow_type"` parses to `activity = None`, `workflowType = None` (backward compat)
- [x] [test] Write test: JSON with only `"activity": "waiting"` present parses correctly (other field `None`)
- [x] [test] Write test: JSON with only `"workflow_type": "diagnostic"` present parses correctly (other field `None`)
- [x] [test] Write test: JSON with non-string `"activity"` value (e.g., integer) results in `activity = None`
- [x] [test] Write test: JSON with non-string `"workflow_type"` value (e.g., boolean) results in `workflowType = None`

### ReviewStateService writing (bimap writer)
- [x] [test] Write test: `ReviewState` with `activity = Some("waiting")` writes `"activity": "waiting"` in JSON output
- [x] [test] Write test: `ReviewState` with `workflowType = Some("waterfall")` writes `"workflow_type": "waterfall"` in JSON output
- [x] [test] Write test: `ReviewState` with both fields `None` does not include `"activity"` or `"workflow_type"` keys in JSON output

### Run tests to confirm they fail
- [x] [verify] Run `./iw test unit` — all new tests must fail (fields don't exist yet), existing tests must still pass

## Implementation

### Add fields to `ReviewState` case class (`.iw/core/model/ReviewState.scala`)
- [x] [impl] Add `activity: Option[String] = None` field after `artifacts` in `ReviewState` case class
- [x] [impl] Add `workflowType: Option[String] = None` field after `activity` in `ReviewState` case class
- [x] [impl] Update ScalaDoc `@param` list to document `activity` ("working" | "waiting") and `workflowType` ("agile" | "waterfall" | "diagnostic")

### Update ReviewStateService bimap (`.iw/core/dashboard/ReviewStateService.scala`)
- [x] [impl] Add `activity` read in bimap reader — extract from `obj.get("activity")` with `ujson.Str` pattern match (after `message`, before `ReviewState(...)` construction)
- [x] [impl] Add `workflow_type` read in bimap reader — extract from `obj.get("workflow_type")` with `ujson.Str` pattern match
- [x] [impl] Update `ReviewState(...)` construction call (line 105) to pass `activity` and `workflowType`
- [x] [impl] Add `activity` write in bimap writer — `state.activity.foreach(a => obj("activity") = ujson.Str(a))` (after `message` write)
- [x] [impl] Add `workflow_type` write in bimap writer — `state.workflowType.foreach(wt => obj("workflow_type") = ujson.Str(wt))`

### Fix ReviewState construction sites — production code
- [x] [impl] Update `SampleDataGenerator.scala` — add `activity = None, workflowType = None` (or appropriate sample values) at lines ~291, 308, 323, 336 (not needed — default values handle this)

### Fix ReviewState construction sites — test files
- [x] [impl] Update `ServerStateCodecTest.scala` — add new fields to existing `ReviewState(...)` constructions (default values handle existing sites; new tests use explicit fields)
- [x] [impl] Update `TestFixtures.scala` — add new fields to all `ReviewState(...)` constructions (~lines 501, 513, 524, 533) (default values handle existing sites)
- [x] [impl] Update `ReviewStateServiceTest.scala` — add new fields to existing `ReviewState(...)` constructions (~lines 183, 208, 289, 323) (default values handle existing sites)
- [x] [impl] Update `ReviewStateTest.scala` — add new fields to all `ReviewState(...)` constructions (~lines 12, 26, 54, 60) (default values handle existing sites)
- [x] [impl] Update `CachedReviewStateTest.scala` — add new fields to `ReviewState(...)` construction (~line 10) (default values handle existing sites)
- [x] [impl] Update `WorktreeCardServiceTest.scala` — add new fields to `ReviewState(...)` construction (~line 279) (default values handle existing sites)
- [x] [impl] Update `WorktreeListViewTest.scala` — add new fields to all `ReviewState(...)` constructions (default values handle existing sites)
- [x] [impl] Update `DashboardServiceTest.scala` — add new fields to all `ReviewState(...)` constructions (~lines 47, 103, 134) (default values handle existing sites)
- [x] [impl] Update `ProjectSummaryTest.scala` — add new fields to `ReviewState(...)` construction (~line 45) (default values handle existing sites)
- [x] [impl] Update `ServerStateServiceTest.scala` — add new fields to `ReviewState(...)` construction (~line 302) (default values handle existing sites)
- [x] [impl] Update `ServerStateTest.scala` — add new fields to `ReviewState(...)` construction (~line 184) (default values handle existing sites)
- [x] [impl] Update `WorktreeDetailViewTest.scala` — add new fields to `ReviewState(...)` construction (~line 58) (default values handle existing sites)

## Verification
- [x] [verify] Run `./iw test unit` — all tests (existing + new) must pass
- [ ] [verify] Run `./iw test e2e` — no regressions in end-to-end tests
- [x] [verify] Confirm no compilation warnings introduced
- [ ] [verify] Commit all changes with a descriptive message referencing IW-274
**Phase Status:** In Progress
