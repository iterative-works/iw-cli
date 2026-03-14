# Phase 3: `validate --help` fails with misleading error instead of showing help

## Defect Description

Running `./iw review-state validate --help` does not display usage information. Instead, the `--help` flag starts with `--`, so it is filtered out by `args.filterNot(_.startsWith("--"))`, leaving `filePaths` empty. Since `useStdin` is also false, the command prints "No file path provided" and exits 1.

The user sees a misleading error message instead of help text.

## Reproduction Steps

1. Run `./iw review-state validate --help`
2. Observe: "No file path provided. Usage: iw review-state validate <file-path> or --stdin" (exit 1)

**Expected:** Command prints available options and exits 0 without reading any files.
**Actual:** Command prints misleading error about missing file path and exits 1.

## Root Cause

No `--help` or `-h` guard exists in `validate.scala`. The function enters `@main def validate(args: String*)` at line 14 with no help check. The `--help` flag is filtered out by `args.filterNot(_.startsWith("--"))` at line 16, leaving `filePaths` empty, which triggers the "No file path provided" error at line 18-20.

**File:** `.iw/commands/review-state/validate.scala` — line 14 enters with no `--help` check.

## Fix Strategy

1. Add an early guard at the top of the `validate` function (before line 15) that checks `args.contains("--help") || args.contains("-h")`
2. Call a `showHelp()` function that prints usage derived from the header comments (lines 3-9)
3. Exit 0 without reading any files
4. Follow the pattern established in Phases 1 and 2 (`update.scala`, `write.scala`)

## Previous Phase Context

- Phase 1 fixed the identical bug in `update.scala`
- Phase 2 fixed the identical bug in `write.scala`
- This phase applies the same pattern to `validate.scala`, which is simpler (fewer flags to document)

## Testing Requirements

- E2E test: `./iw review-state validate --help` exits 0 and output contains key usage info (`file-path`, `--stdin`)
- E2E test: `./iw review-state validate -h` exits 0 with usage text
- Run full test suite to verify no regressions

## Acceptance Criteria

- `./iw review-state validate --help` prints usage and exits 0
- `./iw review-state validate -h` prints usage and exits 0
- No files are read when `--help` is used
- Help text lists available arguments from the header comments
- All existing tests continue to pass
