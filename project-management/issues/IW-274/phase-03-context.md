# Phase 3: Builder and Updater — new field support

**Issue:** IW-274
**Layer:** Layer 4 (Merge Logic)
**Estimate:** 0.5-1.5h

## Goals

Add `activity` and `workflowType` support to `ReviewStateBuilder` (creates new review-state.json) and `ReviewStateUpdater` (merges partial updates into existing review-state.json). After this phase, both fields can be written to new JSON files and merged into existing ones, following the established patterns for scalar fields.

## Scope

**In scope:**
- Add `activity: Option[String]` and `workflowType: Option[String]` to `ReviewStateBuilder.BuildInput`
- Add JSON writing for `activity` and `workflow_type` in `ReviewStateBuilder.build()`
- Add `activity: Option[String]` and `workflowType: Option[String]` to `ReviewStateUpdater.UpdateInput`
- Add `clearActivity: Boolean` and `clearWorkflowType: Boolean` to `ReviewStateUpdater.UpdateInput`
- Add merge logic for both fields in `ReviewStateUpdater.merge()`
- Write unit tests covering all new builder and updater paths
- Update existing "all fields" and validator-pass tests to include new fields

**Out of scope:**
- CLI command flag additions (Phase 4)
- WorktreeSummary changes (Phase 5)
- Documentation updates (Phase 6)
- Schema or validator changes (done in Phase 1)
- Domain model changes (done in Phase 2)

## Dependencies

Phase 1 (Schema + Validator) and Phase 2 (Domain Model) must be complete. The schema defines the enum constraints, the validator enforces them, and `ReviewState` already carries `activity` and `workflowType` fields.

## Approach

### Step 1: Add fields to `BuildInput` and update `build()`

In `.iw/core/model/ReviewStateBuilder.scala`, add two new optional fields to `BuildInput`:

```scala
case class BuildInput(
  // ... existing fields ...
  activity: Option[String] = None,
  workflowType: Option[String] = None
)
```

Add JSON writing in `build()`, following the existing scalar field pattern used by `status`, `message`, `prUrl`, and `gitSha`:

```scala
input.activity.foreach(v => obj("activity") = ujson.Str(v))
input.workflowType.foreach(v => obj("workflow_type") = ujson.Str(v))
```

Place these lines after the `input.message` line (line 59) and before the `input.actions` block (line 61), to keep workflow-related fields grouped together. Note the snake_case JSON key `workflow_type` for the camelCase Scala field `workflowType`.

### Step 2: Add fields to `UpdateInput` and update `merge()`

In `.iw/core/model/ReviewStateUpdater.scala`, add four new fields to `UpdateInput`:

```scala
case class UpdateInput(
  // ... existing scalar fields ...
  activity: Option[String] = None,
  workflowType: Option[String] = None,
  // ... existing clear flags ...
  clearActivity: Boolean = false,
  clearWorkflowType: Boolean = false
)
```

Add merge logic in `merge()`, following the exact pattern used for `status` (lines 70-74) and `message` (lines 76-80):

```scala
update.activity.foreach { v =>
  if update.clearActivity then existing.obj.remove("activity")
  else existing("activity") = ujson.Str(v)
}
if update.clearActivity && update.activity.isEmpty then existing.obj.remove("activity")

update.workflowType.foreach { v =>
  if update.clearWorkflowType then existing.obj.remove("workflow_type")
  else existing("workflow_type") = ujson.Str(v)
}
if update.clearWorkflowType && update.workflowType.isEmpty then existing.obj.remove("workflow_type")
```

Place these lines after the `update.prUrl` / `clearPrUrl` block (line 92) and before `update.gitSha` (line 94).

### Step 3: Write tests (TDD — tests first, then implementation)

Write failing tests first, then implement to make them pass.

## Files to Modify

### `.iw/core/model/ReviewStateBuilder.scala`
- Add `activity: Option[String] = None` to `BuildInput` (after `phaseCheckpoints`, line 22)
- Add `workflowType: Option[String] = None` to `BuildInput` (after `activity`)
- Add `input.activity.foreach(v => obj("activity") = ujson.Str(v))` in `build()` (after line 59, the `input.message` line)
- Add `input.workflowType.foreach(v => obj("workflow_type") = ujson.Str(v))` in `build()` (after `activity` line)

### `.iw/core/model/ReviewStateUpdater.scala`
- Add `activity: Option[String] = None` to `UpdateInput` scalar fields section (after `gitSha`, line 14)
- Add `workflowType: Option[String] = None` to `UpdateInput` (after `activity`)
- Add `clearActivity: Boolean = false` to `UpdateInput` clear flags section (after `clearDisplay`, line 43)
- Add `clearWorkflowType: Boolean = false` to `UpdateInput` (after `clearActivity`)
- Add merge logic for `activity` with `clearActivity` in `merge()` (after `prUrl` block, line 92)
- Add merge logic for `workflowType` with `clearWorkflowType` in `merge()` (after `activity` block)

### `.iw/core/test/ReviewStateBuilderTest.scala`
- Add new tests for `activity` and `workflowType` in builder
- Update "build with all fields" test to include both new fields
- Update "optional fields are omitted" test to assert new fields absent
- Update "built JSON passes ReviewStateValidator" test to include new fields

### `.iw/core/test/ReviewStateUpdaterTest.scala`
- Add new tests for `activity` and `workflowType` merge and clear operations
- Update "merged result passes ReviewStateValidator" test to include new fields

## Testing Strategy

### Unit tests to write

**ReviewStateBuilder — activity field:**
1. `activity = Some("working")` writes `"activity": "working"` in output JSON
2. `activity = Some("waiting")` writes `"activity": "waiting"` in output JSON
3. `activity = None` omits `activity` key from output JSON (covered by existing "optional fields omitted" test after update)

**ReviewStateBuilder — workflowType field:**
4. `workflowType = Some("agile")` writes `"workflow_type": "agile"` in output JSON
5. `workflowType = Some("waterfall")` writes `"workflow_type": "waterfall"` in output JSON
6. `workflowType = Some("diagnostic")` writes `"workflow_type": "diagnostic"` in output JSON
7. `workflowType = None` omits `workflow_type` key from output JSON (covered by existing "optional fields omitted" test after update)

**ReviewStateBuilder — combined:**
8. Build with both `activity` and `workflowType` set produces valid JSON with both keys
9. Update "build with all fields" test to include both new fields and verify values
10. Update "built JSON passes ReviewStateValidator" test to include both new fields

**ReviewStateUpdater — activity merge:**
11. `activity = Some("working")` sets `"activity": "working"` in output JSON
12. `activity = Some("waiting")` replaces existing `"activity": "working"` with `"activity": "waiting"`
13. `clearActivity = true` with no `activity` value removes `"activity"` key from existing JSON
14. `clearActivity = true` with `activity = Some(...)` removes the key (clear wins, matching `status`/`message` pattern)

**ReviewStateUpdater — workflowType merge:**
15. `workflowType = Some("agile")` sets `"workflow_type": "agile"` in output JSON
16. `workflowType = Some("waterfall")` replaces existing `"workflow_type": "agile"`
17. `clearWorkflowType = true` with no `workflowType` value removes `"workflow_type"` key
18. `clearWorkflowType = true` with `workflowType = Some(...)` removes the key (clear wins)

**ReviewStateUpdater — preservation:**
19. Existing `"activity"` and `"workflow_type"` are preserved when not mentioned in `UpdateInput`
20. Update "merged result passes ReviewStateValidator" to include both new fields

### Existing scalar field pattern to follow

The `status` field in `ReviewStateUpdater.merge()` (lines 70-74) is the canonical pattern:

```scala
update.status.foreach { v =>
  if update.clearStatus then existing.obj.remove("status")
  else existing("status") = ujson.Str(v)
}
if update.clearStatus && update.status.isEmpty then existing.obj.remove("status")
```

Both `activity` and `workflowType` follow this exact pattern. The only difference is the JSON key: `activity` maps to `"activity"` (same name), `workflowType` maps to `"workflow_type"` (snake_case).

The `BuildInput` scalar pattern (e.g., `status`, `message`) is even simpler:

```scala
input.status.foreach(v => obj("status") = ujson.Str(v))
```

### Running tests

```bash
./iw test unit
```

## Acceptance Criteria

1. `ReviewStateBuilder.BuildInput` accepts `activity: Option[String]` and `workflowType: Option[String]`
2. `ReviewStateBuilder.build()` writes `"activity"` and `"workflow_type"` keys when values are provided
3. `ReviewStateBuilder.build()` omits both keys when values are `None`
4. `ReviewStateUpdater.UpdateInput` accepts `activity`, `workflowType`, `clearActivity`, `clearWorkflowType`
5. `ReviewStateUpdater.merge()` sets `"activity"` and `"workflow_type"` when provided
6. `ReviewStateUpdater.merge()` removes `"activity"` when `clearActivity = true`
7. `ReviewStateUpdater.merge()` removes `"workflow_type"` when `clearWorkflowType = true`
8. `ReviewStateUpdater.merge()` preserves existing `"activity"` and `"workflow_type"` when not mentioned in update
9. Built JSON with both new fields passes `ReviewStateValidator.validate()`
10. Merged JSON with both new fields passes `ReviewStateValidator.validate()`
11. All existing tests still pass (no regressions)
12. All new tests pass
13. `./iw test unit` is green
