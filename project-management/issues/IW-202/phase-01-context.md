# Phase 1: `update --help` mutates state instead of showing help

## Defect Description

Running `./iw review-state update --help` silently executes a full update cycle instead of displaying usage information. The `--help` flag is ignored by the argument parser, causing the command to infer the issue ID from the branch, read the existing `review-state.json`, build an `UpdateInput` with all-None/default values, merge (which updates `last_updated`), validate, and write back. This modifies `last_updated` as a side effect of a read-only help request.

## Reproduction Steps

1. Ensure a valid `review-state.json` exists at `project-management/issues/<issue-id>/review-state.json`
2. Note the `last_updated` value
3. Run `./iw review-state update --help`
4. Observe `last_updated` has changed — file was silently modified

**Expected:** Command prints available options and exits 0 without modifying any files.
**Actual:** Command runs full update path, modifying `last_updated`. Exit 0 with "Review state updated" message.

## Root Cause

No `--help` or `-h` guard exists in `update.scala`. The `extractFlag` helper only looks for specific known flags and ignores `--help`. The established pattern in `feedback.scala`, `dashboard.scala`, and `issue.scala` all check for `--help`/`-h` before any processing.

**File:** `.iw/commands/review-state/update.scala` — line 44 enters `@main def update(args: String*)` with no `--help` check.

## Fix Strategy

1. Add an early guard at the top of the `update` function (after `val argList = args.toList`) that checks `argList.contains("--help") || argList.contains("-h")`
2. Call a `showHelp()` function that prints usage from the header comments (lines 3-38)
3. Exit 0 without reading or modifying any files
4. Follow the pattern from `feedback.scala` lines 18-21 and 49-63

## Testing Requirements

- E2E test: `./iw review-state update --help` exits 0 and output contains key flags (`--status`, `--display-text`)
- E2E test: `./iw review-state update --help` does NOT modify `review-state.json` (compare before/after)
- Run full test suite to verify no regressions

## Acceptance Criteria

- `./iw review-state update --help` prints usage and exits 0
- `./iw review-state update -h` prints usage and exits 0
- No files are read or modified when `--help` is used
- Help text lists all available flags from the header comments
- All existing tests continue to pass
