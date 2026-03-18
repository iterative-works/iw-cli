# Phase 4 Tasks: CLI Commands — write/update flags

## Setup
- [x] [setup] Read `write.scala` and `update.scala` to understand flag extraction patterns (`extractFlag`, `argList.contains`)
- [x] [setup] Read `ReviewStateBuilder.scala` and `ReviewStateUpdater.scala` to confirm `BuildInput`/`UpdateInput` already have `activity`, `workflowType`, `clearActivity`, `clearWorkflowType` from Phase 3

## Tests (TDD - write first)

### Write command E2E tests
- [x] [test] Write BATS test: `review-state write: --activity flag writes activity field` — run with `--activity working`, assert `activity == "working"` in output JSON
- [x] [test] Write BATS test: `review-state write: --workflow-type flag writes workflow_type field` — run with `--workflow-type agile`, assert `workflow_type == "agile"` in output JSON
- [x] [test] Write BATS test: `review-state write: --activity and --workflow-type together produces valid JSON` — run with both flags, assert both fields present, validate output
- [x] [test] Write BATS test: `review-state write: invalid --activity value fails validation` — run with `--activity invalid`, assert exit 1
- [x] [test] Write BATS test: `review-state write: invalid --workflow-type value fails validation` — run with `--workflow-type invalid`, assert exit 1
- [x] [test] Write BATS test: `review-state write --help shows activity and workflow-type flags` — assert `--activity` and `--workflow-type` in help output

### Update command E2E tests
- [x] [test] Write BATS test: `review-state update: --activity flag sets activity field` — create minimal state, update with `--activity working`, assert field set
- [x] [test] Write BATS test: `review-state update: --workflow-type flag sets workflow_type field` — create minimal state, update with `--workflow-type waterfall`, assert field set
- [x] [test] Write BATS test: `review-state update: --clear-activity removes activity field` — create state with `activity`, update with `--clear-activity`, assert key absent
- [x] [test] Write BATS test: `review-state update: --clear-workflow-type removes workflow_type field` — create state with `workflow_type`, update with `--clear-workflow-type`, assert key absent
- [x] [test] Write BATS test: `review-state update: activity and workflow_type preserved when not updated` — create state with both fields, update `--message`, assert both fields preserved
- [x] [test] Write BATS test: `review-state update: invalid --activity value fails validation` — create minimal state, update with `--activity bogus`, assert exit 1
- [x] [test] Write BATS test: `review-state update --help shows activity and workflow-type flags` — assert all four flags in help output

### Run tests to confirm they fail
- [x] [verify] Run `bats .iw/test/review-state.bats` — all new tests must fail, existing tests must pass

## Implementation

### Write command changes (`.iw/commands/review-state/write.scala`)
- [x] [impl] Add `--activity` and `--workflow-type` to ARGS header comments (after `--pr-url` line 16)
- [x] [impl] Add `extractFlag` calls for `--activity` and `--workflow-type` in `handleFlags()` (after `prUrl` at line 138)
- [x] [impl] Pass `activity` and `workflowType` to `BuildInput` constructor (lines 173-188)
- [x] [impl] Add help text for `--activity` and `--workflow-type` in `showHelp()` (after `--pr-url` line 59)

### Update command changes (`.iw/commands/review-state/update.scala`)
- [x] [impl] Add `--activity`, `--clear-activity`, `--workflow-type`, `--clear-workflow-type` to ARGS header comments (after `--clear-pr-url` line 31)
- [x] [impl] Add flag extraction for `--activity`, `--clear-activity`, `--workflow-type`, `--clear-workflow-type` (after `clearPrUrl` at line 94)
- [x] [impl] Pass `activity`, `workflowType`, `clearActivity`, `clearWorkflowType` to `UpdateInput` constructor (lines 147-172)
- [x] [impl] Add help text for all four flags in `showHelp()` (after `--clear-pr-url` line 226)

## Verification
- [x] [verify] Run `bats .iw/test/review-state.bats` — all tests (existing + new) must pass
- [x] [verify] Run `./iw test unit` — no regressions in unit tests
- [x] [verify] Run `./iw test e2e` — no regressions in all E2E tests
- [x] [verify] Commit all changes with a descriptive message referencing IW-274
