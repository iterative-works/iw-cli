# Phase 2: Domain Model — ReviewState fields and codec

**Issue:** IW-274
**Layer:** Layer 2a (Domain Model — ReviewState only)
**Estimate:** 1-2h

## Goals

Add `activity` and `workflowType` fields to the `ReviewState` case class and update both codec paths (server state cache and review-state.json parsing) to handle the new fields. After this phase, `ReviewState` carries both new fields and they round-trip correctly through both serialization paths.

## Scope

**In scope:**
- Add `activity: Option[String]` field to `ReviewState` case class
- Add `workflowType: Option[String]` field to `ReviewState` case class
- Update `ServerStateCodec.scala` — the `macroRW[ReviewState]` codec (used for internal server state cache `state.json`)
- Update `ReviewStateService.parseReviewStateJson` — the custom `bimap` reader (used for reading `review-state.json` files from disk, handles snake_case JSON keys)
- Update `ServerStateCodecTest.scala` — roundtrip tests for the new fields
- Update all call sites that construct `ReviewState(...)` to include the two new fields
- Write unit tests for both codec paths covering presence/absence of new fields

**Out of scope:**
- WorktreeSummary redesign (Phase 5)
- Builder/Updater changes (Phase 3)
- CLI command flag additions (Phase 4)
- Schema or validator changes (done in Phase 1)
- Documentation updates (Phase 6)

## Dependencies

Phase 1 must be complete. The schema and validator already define `activity` and `workflow_type` with their enum constraints.

## Approach

### Step 1: Add fields to `ReviewState` case class

In `.iw/core/model/ReviewState.scala`, add two new optional fields after `artifacts`:

```scala
case class ReviewState(
  display: Option[Display],
  badges: Option[List[Badge]],
  taskLists: Option[List[TaskList]],
  needsAttention: Option[Boolean],
  message: Option[String],
  artifacts: List[ReviewArtifact],
  activity: Option[String],       // "working" | "waiting"
  workflowType: Option[String]    // "agile" | "waterfall" | "diagnostic"
)
```

Both fields use `Option[String]` with default `None` so existing construction sites compile without changes if defaults are used, but explicit call sites must be updated.

### Step 2: Handle `ServerStateCodec` (internal state cache)

`ServerStateCodec` uses `macroRW[ReviewState]` (line 39). With upickle's `macroRW`, Scala field names map directly to JSON keys. This means:
- `activity` maps to JSON key `"activity"` (no conflict — same in both formats)
- `workflowType` maps to JSON key `"workflowType"` (camelCase, fine for internal cache)

Since `macroRW` handles `Option` fields automatically (absent = `None`), no manual change to the codec itself is needed — `macroRW[ReviewState]` will pick up the new fields via macro derivation. However, **verify** this with a roundtrip test.

### Step 3: Handle `ReviewStateService.parseReviewStateJson` (disk format)

The custom `bimap` reader in `ReviewStateService.scala` (lines 76-107) manually reads snake_case JSON keys from `review-state.json` files. This reader must be updated to:

1. **Read** `activity` from JSON key `"activity"` (same name, no snake_case conversion needed)
2. **Read** `workflow_type` from JSON key `"workflow_type"` and map to `workflowType` Scala field
3. **Write** `workflowType` back to JSON key `"workflow_type"`

Add to the reader (around line 100-103):
```scala
val activity = obj.get("activity").flatMap {
  case ujson.Str(s) => Some(s)
  case _ => None
}
val workflowType = obj.get("workflow_type").flatMap {
  case ujson.Str(s) => Some(s)
  case _ => None
}
```

Update the construction call (line 105):
```scala
ReviewState(display, badges, taskLists, needsAttention, message, artifacts, activity, workflowType)
```

Update the writer (around line 78-84):
```scala
state.activity.foreach(a => obj("activity") = ujson.Str(a))
state.workflowType.foreach(wt => obj("workflow_type") = ujson.Str(wt))
```

### Step 4: Fix all `ReviewState(...)` construction sites

Every place that constructs a `ReviewState` directly must add the two new `Option[String]` parameters. Key locations:

**Test files (add `activity = None, workflowType = None` or use named parameters):**
- `.iw/core/test/ServerStateCodecTest.scala` — lines 63-70
- `.iw/core/test/TestFixtures.scala` — lines 501, 513, 524, 533
- `.iw/core/test/ReviewStateServiceTest.scala` — lines 183, 208, 289, 323
- `.iw/core/test/ReviewStateTest.scala` — lines 12, 26, 54, 60
- `.iw/core/test/WorktreeCardServiceTest.scala` — line 279
- `.iw/core/test/WorktreeListViewTest.scala` — multiple construction sites
- `.iw/core/test/DashboardServiceTest.scala` — lines 47, 103, 134
- `.iw/core/test/ProjectSummaryTest.scala` — line 45
- `.iw/core/test/ServerStateServiceTest.scala` — line 302
- `.iw/core/test/ServerStateTest.scala` — line 184
- `.iw/core/test/CachedReviewStateTest.scala` — line 10
- `.iw/core/test/WorktreeDetailViewTest.scala` — line 58

**Production code:**
- `.iw/core/dashboard/ReviewStateService.scala` — line 105 (the bimap reader, handled in Step 3)
- `.iw/core/dashboard/domain/SampleDataGenerator.scala` — lines 291, 308, 323, 336

### Step 5: Write tests (TDD)

Write failing tests first, then implement to make them pass.

## Files to Modify

### `.iw/core/model/ReviewState.scala`
- Add `activity: Option[String] = None` and `workflowType: Option[String] = None` to `ReviewState` case class
- Update ScalaDoc to document new fields

### `.iw/core/dashboard/ReviewStateService.scala`
- Update custom `bimap` reader to read `"activity"` and `"workflow_type"` from JSON
- Update custom `bimap` writer to write `"activity"` and `"workflow_type"` to JSON
- Update `ReviewState(...)` construction call at line 105

### `.iw/core/dashboard/domain/SampleDataGenerator.scala`
- Update all `ReviewState(...)` constructions (lines 291, 308, 323, 336) to include new fields

### `.iw/core/test/ServerStateCodecTest.scala`
- Update existing `ReviewState(...)` construction to include new fields
- Add test: roundtrip with `activity` and `workflowType` populated
- Add test: roundtrip with `activity` and `workflowType` absent (backward compat)

### `.iw/core/test/ReviewStateServiceTest.scala`
- Add test: `parseReviewStateJson` with `activity` and `workflow_type` present
- Add test: `parseReviewStateJson` with both fields absent (backward compat)
- Add test: `parseReviewStateJson` with only `activity` present
- Add test: `parseReviewStateJson` with only `workflow_type` present
- Update all existing `ReviewState(...)` constructions to include new fields

### `.iw/core/test/TestFixtures.scala`
- Update all `ReviewState(...)` constructions to include new fields

### `.iw/core/test/ReviewStateTest.scala`
- Update all `ReviewState(...)` constructions to include new fields

### `.iw/core/test/CachedReviewStateTest.scala`
- Update `ReviewState(...)` construction to include new fields

### `.iw/core/test/WorktreeCardServiceTest.scala`
- Update `ReviewState(...)` construction to include new fields

### `.iw/core/test/WorktreeListViewTest.scala`
- Update all `ReviewState(...)` constructions to include new fields

### `.iw/core/test/DashboardServiceTest.scala`
- Update all `ReviewState(...)` constructions to include new fields

### `.iw/core/test/ProjectSummaryTest.scala`
- Update `ReviewState(...)` construction to include new fields

### `.iw/core/test/ServerStateServiceTest.scala`
- Update `ReviewState(...)` construction to include new fields

### `.iw/core/test/ServerStateTest.scala`
- Update `ReviewState(...)` construction to include new fields

### `.iw/core/test/WorktreeDetailViewTest.scala`
- Update `ReviewState(...)` construction to include new fields

## Testing Strategy

### Unit tests to write

**ServerStateCodec roundtrip (internal cache format, camelCase keys):**
1. `ReviewState` with `activity = Some("working")` and `workflowType = Some("agile")` roundtrips correctly
2. `ReviewState` with both fields `None` roundtrips correctly (backward compat — already covered by existing test, but verify after field addition)

**ReviewStateService parsing (disk format, snake_case keys):**
3. JSON with `"activity": "working"` parses to `activity = Some("working")`
4. JSON with `"workflow_type": "agile"` parses to `workflowType = Some("agile")`
5. JSON with both `"activity"` and `"workflow_type"` present parses correctly
6. JSON with neither field present parses to `activity = None, workflowType = None` (backward compat)
7. JSON with non-string `"activity"` value (e.g., integer) results in `activity = None` (graceful handling)
8. JSON with non-string `"workflow_type"` value results in `workflowType = None` (graceful handling)

**ReviewStateService writing (bimap writer):**
9. `ReviewState` with `activity = Some("waiting")` writes `"activity": "waiting"` in JSON output
10. `ReviewState` with `workflowType = Some("waterfall")` writes `"workflow_type": "waterfall"` in JSON output
11. `ReviewState` with both fields `None` does not include those keys in JSON output

### Running tests

```bash
./iw test unit
```

## Acceptance Criteria

1. `ReviewState` case class has `activity: Option[String]` and `workflowType: Option[String]` fields
2. `ServerStateCodec.macroRW[ReviewState]` roundtrips both new fields correctly (camelCase JSON keys)
3. `ReviewStateService.parseReviewStateJson` reads `"activity"` and `"workflow_type"` (snake_case) from review-state.json format
4. `ReviewStateService.parseReviewStateJson` writes `"activity"` and `"workflow_type"` (snake_case) when serializing
5. Both fields are optional — absent in JSON maps to `None`, `None` omits the field from JSON output
6. All existing tests pass (no regressions from adding fields)
7. All new tests pass
8. `./iw test unit` is green
9. No compilation warnings introduced
