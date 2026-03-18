# Phase 4: CLI Commands — write/update flags

**Issue:** IW-274
**Layer:** Layer 5 (CLI/Adapter)
**Estimate:** 1-2h

## Goals

Add `--activity` and `--workflow-type` CLI flags to the `review-state write` and `review-state update` commands. After this phase, users and scripts can set and clear these fields from the command line, completing the end-to-end path from CLI input through to validated JSON output.

## Scope

**In scope:**
- Add `--activity <working|waiting>` flag to `review-state write`
- Add `--workflow-type <agile|waterfall|diagnostic>` flag to `review-state write`
- Add `--activity <working|waiting>` flag to `review-state update`
- Add `--workflow-type <agile|waterfall|diagnostic>` flag to `review-state update`
- Add `--clear-activity` flag to `review-state update`
- Add `--clear-workflow-type` flag to `review-state update`
- Update `--help` output in both commands to document new flags
- Update ARGS header comments in both commands
- Write E2E tests (BATS) covering all new flags

**Out of scope:**
- WorktreeSummary redesign (Phase 5)
- Worktrees command changes (Phase 5)
- Formatter changes (Phase 6)
- Documentation/llms.txt updates (Phase 7)
- Schema, validator, domain model, builder, updater changes (done in Phases 1-3)

## Dependencies

Phases 1-3 must be complete:
- Phase 1: Schema validates `activity` and `workflow_type` enum values
- Phase 2: `ReviewState` domain model carries both fields
- Phase 3: `BuildInput` and `UpdateInput` already accept `activity`, `workflowType`, `clearActivity`, `clearWorkflowType`

The CLI commands just need to parse flags and pass values to the already-working builder/updater.

## Approach

### Step 1: Write failing E2E tests (TDD)

Add tests to `.iw/test/review-state.bats` covering the new flags. Write tests first, confirm they fail, then implement.

### Step 2: Add flags to `review-state write`

In `.iw/commands/review-state/write.scala`:

1. **Update ARGS header comments** (lines 6-21): Add two new entries after the `--pr-url` line:
   ```
   //   --activity <value>         Activity state: working, waiting
   //   --workflow-type <value>    Workflow type: agile, waterfall, diagnostic
   ```

2. **Add flag extraction in `handleFlags()`** (after `prUrl` extraction at line 138): Follow the same `extractFlag` pattern used by `--status` (line 108), `--message` (line 137), and `--pr-url` (line 138):
   ```scala
   val activity = extractFlag(argList, "--activity")
   val workflowType = extractFlag(argList, "--workflow-type")
   ```

3. **Pass to BuildInput** (lines 173-188): Add the two new fields to the `ReviewStateBuilder.BuildInput` constructor call, after `phaseCheckpoints`:
   ```scala
   activity = activity,
   workflowType = workflowType
   ```

4. **Update `showHelp()`** (lines 41-67): Add help text for both flags after the `--pr-url` line:
   ```scala
   println("  --activity <value>                        Activity state: working, waiting")
   println("  --workflow-type <value>                   Workflow type: agile, waterfall, diagnostic")
   ```

5. **Update EXAMPLE comment** (line 22): Add an example showing the new flags.

### Step 3: Add flags to `review-state update`

In `.iw/commands/review-state/update.scala`:

1. **Update ARGS header comments** (lines 5-36): Add entries after the `--clear-pr-url` line:
   ```
   //   --activity <value>         Activity state: working, waiting
   //   --clear-activity           Remove activity field
   //   --workflow-type <value>    Workflow type: agile, waterfall, diagnostic
   //   --clear-workflow-type      Remove workflow_type field
   ```

2. **Add flag extraction** (after `prUrl`/`clearPrUrl` extraction at lines 93-94): Follow the same pattern used for `--status`/`--clear-status` (lines 78-79):
   ```scala
   val activity = extractFlag(argList, "--activity")
   val clearActivity = argList.contains("--clear-activity")

   val workflowType = extractFlag(argList, "--workflow-type")
   val clearWorkflowType = argList.contains("--clear-workflow-type")
   ```

3. **Pass to UpdateInput** (lines 147-172): Add the four new fields to the `ReviewStateUpdater.UpdateInput` constructor call. Add `activity` and `workflowType` after `prUrl` (line 152), and `clearActivity`/`clearWorkflowType` after `clearDisplay` (line 171):
   ```scala
   activity = activity,
   workflowType = workflowType,
   // ...
   clearActivity = clearActivity,
   clearWorkflowType = clearWorkflowType
   ```

4. **Update `showHelp()`** (lines 192-234): Add help text for all four flags after the `--clear-pr-url` entry.

### Step 4: Verify tests pass

Run `./iw test` to confirm all new E2E tests pass and no regressions in existing tests.

## Files to Modify

### `.iw/commands/review-state/write.scala`
- Lines 6-21: Add `--activity` and `--workflow-type` to ARGS header comments
- Line 22: Update EXAMPLE comment to show new flags
- Lines 41-67: Add help text for both flags in `showHelp()`
- After line 138: Add `extractFlag` calls for `--activity` and `--workflow-type`
- Lines 173-188: Add `activity` and `workflowType` to `BuildInput` constructor

### `.iw/commands/review-state/update.scala`
- Lines 5-36: Add `--activity`, `--clear-activity`, `--workflow-type`, `--clear-workflow-type` to ARGS header comments
- After line 94: Add `extractFlag` and `argList.contains` calls for all four flags
- Lines 147-172: Add `activity`, `workflowType`, `clearActivity`, `clearWorkflowType` to `UpdateInput` constructor
- Lines 192-234: Add help text for all four flags in `showHelp()`

### `.iw/test/review-state.bats`
- Add new E2E tests for write and update with new flags (see Testing Strategy)

## Testing Strategy

### Unit tests to write

No new unit tests needed. The builder and updater logic is already tested in Phase 3 (`ReviewStateBuilderTest.scala` and `ReviewStateUpdaterTest.scala`). This phase is purely the CLI adapter layer.

### E2E tests to write

Add these tests to `.iw/test/review-state.bats`. All tests use the existing `setup()` which exports `IW_SERVER_DISABLED=1` and creates `TEST_TMPDIR`. Tests use `python3 -c` for JSON assertions, matching the established pattern.

**Write command tests:**

1. **`review-state write: --activity flag writes activity field`**
   - Run: `iw review-state write --issue-id IW-1 --activity working --output "$TEST_TMPDIR/state.json"`
   - Assert: exit 0, output JSON has `activity == "working"`

2. **`review-state write: --workflow-type flag writes workflow_type field`**
   - Run: `iw review-state write --issue-id IW-1 --workflow-type agile --output "$TEST_TMPDIR/state.json"`
   - Assert: exit 0, output JSON has `workflow_type == "agile"`

3. **`review-state write: --activity and --workflow-type together produces valid JSON`**
   - Run: `iw review-state write --issue-id IW-1 --activity waiting --workflow-type diagnostic --output "$TEST_TMPDIR/state.json"`
   - Assert: exit 0, both fields present in JSON
   - Follow-up: `iw review-state validate` on the output file succeeds

4. **`review-state write: invalid --activity value fails validation`**
   - Run: `iw review-state write --issue-id IW-1 --activity invalid --output "$TEST_TMPDIR/state.json"`
   - Assert: exit 1, validation error mentioning `activity`

5. **`review-state write: invalid --workflow-type value fails validation`**
   - Run: `iw review-state write --issue-id IW-1 --workflow-type invalid --output "$TEST_TMPDIR/state.json"`
   - Assert: exit 1, validation error mentioning `workflow_type`

6. **`review-state write --help shows activity and workflow-type flags`**
   - Assert: `--activity` and `--workflow-type` appear in help output

**Update command tests:**

7. **`review-state update: --activity flag sets activity field`**
   - Setup: create minimal review-state.json in `$TEST_TMPDIR`
   - Run: `iw review-state update --issue-id IW-1 --activity working --input "$TEST_TMPDIR/state.json"`
   - Assert: exit 0, output JSON has `activity == "working"`

8. **`review-state update: --workflow-type flag sets workflow_type field`**
   - Setup: create minimal review-state.json
   - Run: `iw review-state update --issue-id IW-1 --workflow-type waterfall --input "$TEST_TMPDIR/state.json"`
   - Assert: exit 0, output JSON has `workflow_type == "waterfall"`

9. **`review-state update: --clear-activity removes activity field`**
   - Setup: create review-state.json with `"activity": "working"`
   - Run: `iw review-state update --issue-id IW-1 --clear-activity --input "$TEST_TMPDIR/state.json"`
   - Assert: exit 0, `activity` key absent from output JSON

10. **`review-state update: --clear-workflow-type removes workflow_type field`**
    - Setup: create review-state.json with `"workflow_type": "agile"`
    - Run: `iw review-state update --issue-id IW-1 --clear-workflow-type --input "$TEST_TMPDIR/state.json"`
    - Assert: exit 0, `workflow_type` key absent from output JSON

11. **`review-state update: activity and workflow_type preserved when not updated`**
    - Setup: create review-state.json with both fields set
    - Run: `iw review-state update --issue-id IW-1 --message "test" --input "$TEST_TMPDIR/state.json"`
    - Assert: exit 0, both fields still present with original values

12. **`review-state update: invalid --activity value fails validation`**
    - Setup: create minimal review-state.json
    - Run: `iw review-state update --issue-id IW-1 --activity bogus --input "$TEST_TMPDIR/state.json"`
    - Assert: exit 1, validation error

13. **`review-state update --help shows activity and workflow-type flags`**
    - Assert: `--activity`, `--clear-activity`, `--workflow-type`, `--clear-workflow-type` appear in help output

### Running tests

```bash
# All tests (unit + E2E)
./iw test

# Just E2E
./iw test e2e

# Just the review-state BATS file
bats .iw/test/review-state.bats
```

## Acceptance Criteria

1. `iw review-state write --activity working` writes `"activity": "working"` to output JSON
2. `iw review-state write --workflow-type agile` writes `"workflow_type": "agile"` to output JSON
3. `iw review-state write` with invalid `--activity` or `--workflow-type` values exits 1 with validation error
4. `iw review-state update --activity waiting` sets `"activity": "waiting"` in existing file
5. `iw review-state update --workflow-type waterfall` sets `"workflow_type": "waterfall"` in existing file
6. `iw review-state update --clear-activity` removes `activity` from existing file
7. `iw review-state update --clear-workflow-type` removes `workflow_type` from existing file
8. Existing `activity` and `workflow_type` values are preserved when not mentioned in an update
9. `--help` output for both commands documents all new flags
10. ARGS header comments in both command files document all new flags
11. All new E2E tests pass
12. All existing tests pass (no regressions)
13. `./iw test` is green (both unit and E2E)
