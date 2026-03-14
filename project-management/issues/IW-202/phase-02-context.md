# Phase 2: `write --help` fails with misleading error instead of showing help

## Defect Description

Running `./iw review-state write --help` does not display usage information. Instead, the behavior depends on context:
- If on a branch matching an issue ID: the command attempts to build and write a review-state.json with minimal/empty content, potentially overwriting an existing file.
- If on a non-issue branch: fails with "Cannot infer issue ID" error and exit code 1.

In neither case does the user see help text.

## Reproduction Steps

1. Run `./iw review-state write --help`
2. If on a non-issue branch: observe "Cannot infer issue ID" error (exit 1)
3. If on an issue branch (e.g. `IW-202`): observe it attempts to write a file

**Expected:** Command prints available options and exits 0 without reading or writing any files.
**Actual:** Command either errors with misleading message or silently writes a file.

## Root Cause

No `--help` or `-h` guard exists in `write.scala`. The function enters `@main def write(args: String*)` at line 28 and immediately branches on `--from-stdin` at line 31, with no help check before that. The `--help` flag is not recognized by any of the flag-parsing helpers (`extractFlag`, `extractRepeatedFlag`), so it falls through to the `handleFlags` path where it gets ignored while processing continues.

**File:** `.iw/commands/review-state/write.scala` — line 28 enters with no `--help` check.

## Fix Strategy

1. Add an early guard at the top of the `write` function (after `val argList = args.toList`, before the `--from-stdin` check) that checks `argList.contains("--help") || argList.contains("-h")`
2. Call a `showHelp()` function that prints usage derived from the header comments (lines 3-22)
3. Exit 0 without reading or writing any files
4. Follow the pattern established in `update.scala` (Phase 1 fix) and `feedback.scala`

## Previous Phase Context

Phase 1 fixed the identical bug in `update.scala` by adding an early `--help`/`-h` guard and `showHelp()` function. This phase applies the same pattern to `write.scala`.

## Testing Requirements

- E2E test: `./iw review-state write --help` exits 0 and output contains key flags (`--status`, `--display-text`, `--from-stdin`)
- E2E test: `./iw review-state write -h` exits 0 with usage text
- Run full test suite to verify no regressions

## Acceptance Criteria

- `./iw review-state write --help` prints usage and exits 0
- `./iw review-state write -h` prints usage and exits 0
- No files are read or written when `--help` is used
- Help text lists all available flags from the header comments
- All existing tests continue to pass
