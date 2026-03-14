# Diagnostic Analysis: review-state update should support --help flag

**Issue:** IW-202
**Created:** 2026-03-14
**Status:** Draft
**Severity:** Medium

## Problem Statement

Running `./iw review-state update --help` executes a default (no-op) update instead of
displaying usage information. The `--help` flag is silently ignored by the argument
parser, causing the command to proceed through its full execution path -- inferring
issue ID from the branch, reading the existing `review-state.json`, building an
`UpdateInput` with all-None/default values, merging (which updates `last_updated`),
and writing back. This modifies the file's `last_updated` timestamp as a side effect
of what the user intended as a read-only help request.

The same bug exists in the `write` and `validate` subcommands, and the top-level
`review-state` dispatcher also lacks `--help` handling (it would report "Unknown
subcommand: --help").

**User-visible impact:**

- `--help` silently mutates `review-state.json` (updates `last_updated`) instead of showing help
- Users and agents cannot discover available flags for `update`, `write`, or `validate` subcommands
- Inconsistent with other iw commands (`feedback`, `dashboard`, `issue create`) which all support `--help`

## Affected Components

- `.iw/commands/review-state/update.scala`: No `--help` check; silently proceeds to update
- `.iw/commands/review-state/write.scala`: No `--help` check; would attempt to infer issue ID and write
- `.iw/commands/review-state/validate.scala`: No `--help` check; would report "No file path provided"
- `.iw/commands/review-state.scala`: No `--help` check; would report "Unknown subcommand: --help"
- `.iw/test/review-state.bats`: No tests for `--help` behavior on any subcommand

**Blast radius:** All users and agents invoking any review-state subcommand with `--help`. The `update` subcommand is the most harmful case because it silently mutates state. The `write` and `validate` commands fail with misleading errors rather than showing help.

---

## Defect 1: `review-state update --help` mutates state instead of showing help

### Reproduction Steps

**Environment:** Any OS where iw-cli runs, with a git branch matching an issue ID pattern (e.g., `IW-202`)

**Prerequisites:**
- A valid `review-state.json` exists at `project-management/issues/<issue-id>/review-state.json`
- Current git branch matches an issue ID pattern (or `--issue-id` is provided)

**Steps to reproduce:**
1. Note the `last_updated` value in the existing `review-state.json`
2. Run `./iw review-state update --help`
3. Check the `last_updated` value in `review-state.json`

**Expected behavior:** Command prints available options (`--status`, `--display-text`, etc.) and exits 0 without reading or modifying any files.

**Actual behavior:** Command silently proceeds through the full update path, reads the file, builds an empty `UpdateInput`, merges (updating `last_updated`), validates, and writes back. The `last_updated` timestamp is changed. Exit code is 0 with a "Review state updated" success message.

**Reproducibility:** Always

### Root Cause Hypotheses

#### Hypothesis 1: Missing --help guard at top of update function -- Most Likely

**Evidence:**
- Line 44 of `update.scala` enters `@main def update(args: String*)` with no `--help` check
- The `extractFlag` helper (line 187) only looks for specific known flags and ignores `--help`
- `argList.contains("--needs-attention")` style boolean checks do not include `--help`
- Established pattern in `feedback.scala` (line 19), `dashboard.scala` (line 31), and `issue.scala` (line 49) all check for `--help`/`-h` before any processing
- The USAGE comments at the top of `update.scala` (lines 3-38) already document all flags, so the help text content is well-defined

**Likelihood:** High -- this is definitively the root cause; it is not a hypothesis, it is directly observable in the code.

**Investigation approach:**
1. Confirmed: no `--help` or `-h` check exists anywhere in `update.scala`
2. Confirmed: the existing `extractFlag` and `parseArrayField` helpers ignore unknown flags

**Fix strategy:**
- Add an early guard at the top of the `update` function (after `val argList = args.toList`) that checks `argList.contains("--help") || argList.contains("-h")`, calls a `showHelp()` function, and exits 0
- The `showHelp()` function should print usage information matching the USAGE/ARGS comments already in the file header (lines 3-38)
- Follow the pattern established in `feedback.scala`: `args.contains` check followed by `showHelp()` and `sys.exit(0)`
- Estimated effort: 1-2 hours (including the `showHelp` function, test, and verification)

**Risk of fix:** Low -- adding an early return before any processing is a minimal, safe change. No existing behavior is altered for any other flag combination.

---

## Defect 2: `review-state write --help` fails with misleading error instead of showing help

### Reproduction Steps

**Environment:** Same as Defect 1

**Prerequisites:**
- Current git branch does not match an issue ID pattern (to see the error path), OR
- Branch does match (to see it attempt to write a file)

**Steps to reproduce:**
1. Run `./iw review-state write --help`

**Expected behavior:** Command prints available write options and exits 0.

**Actual behavior:** If on a non-issue branch, fails with "Cannot infer issue ID" error. If on an issue branch, attempts to build and write a review-state.json with minimal/empty content.

**Reproducibility:** Always

### Root Cause Hypotheses

#### Hypothesis 1: Missing --help guard at top of write function -- Most Likely

**Evidence:**
- Line 28 of `write.scala` enters `@main def write(args: String*)` with no `--help` check
- Same structural problem as `update.scala`

**Likelihood:** High -- directly observable.

**Investigation approach:**
1. Confirmed: no `--help` or `-h` check in `write.scala`

**Fix strategy:**
- Add early `--help`/`-h` guard in `write.scala` before the `--from-stdin` check (line 31)
- Add `showHelp()` function with content derived from the file header comments (lines 3-22)
- Estimated effort: 1-2 hours

**Risk of fix:** Low -- same minimal early-return pattern.

---

## Defect 3: `review-state validate --help` fails with misleading error instead of showing help

### Reproduction Steps

**Environment:** Same as Defect 1

**Steps to reproduce:**
1. Run `./iw review-state validate --help`

**Expected behavior:** Command prints validate usage and exits 0.

**Actual behavior:** `--help` starts with `--` so `filePaths` (line 16: `args.filterNot(_.startsWith("--"))`) will be empty, and `useStdin` is false, so it prints "No file path provided" and exits 1.

**Reproducibility:** Always

### Root Cause Hypotheses

#### Hypothesis 1: Missing --help guard at top of validate function -- Most Likely

**Evidence:**
- Line 14 of `validate.scala` enters `@main def validate(args: String*)` with no `--help` check
- `--help` is filtered out by `_.startsWith("--")` leaving `filePaths` empty, triggering the usage error

**Likelihood:** High -- directly observable.

**Fix strategy:**
- Add early `--help`/`-h` guard before line 15 in `validate.scala`
- Add `showHelp()` function with content from file header comments (lines 3-9)
- Estimated effort: 0.5-1 hour (simpler command, fewer flags to document)

**Risk of fix:** Low

---

## Defect 4: `review-state --help` reports "Unknown subcommand" instead of showing help

### Reproduction Steps

**Steps to reproduce:**
1. Run `./iw review-state --help`

**Expected behavior:** Shows available subcommands (validate, write, update) and exits 0.

**Actual behavior:** Prints "Unknown subcommand: --help" and exits 1.

**Reproducibility:** Always

### Root Cause Hypotheses

#### Hypothesis 1: No --help case in the dispatcher's match expression -- Most Likely

**Evidence:**
- `review-state.scala` line 28 matches on `subcommand` with cases for "validate", "write", "update", and a default wildcard
- `--help` falls through to the wildcard default at line 35, printing "Unknown subcommand: --help"
- The dispatcher already has usage text at lines 17-22 (shown when `args.isEmpty`), but it is not reachable via `--help`

**Likelihood:** High -- directly observable.

**Fix strategy:**
- Add a `case "--help" | "-h" =>` branch in the match at line 28 that prints the same usage info already present at lines 17-22 and exits 0
- Alternatively, check `args.head` for `--help`/`-h` before the match
- Estimated effort: 0.5 hour

**Risk of fix:** Low

---

## Technical Risks & Uncertainties

### Resolved: Scope of fix for this issue

All four defects will be fixed under IW-202. The per-command pattern (no shared utility) will be followed, consistent with how other commands handle --help.

### Resolved: Help text content source

Help text will use the simple format matching the header comments already in each file. The header comments are the source of truth, with `showHelp()` mirroring them.

---

## Total Estimates

**Per-Defect Breakdown:**
- Defect 1 (update --help): 1-2 hours (investigate + fix + test + verify)
- Defect 2 (write --help): 1-2 hours
- Defect 3 (validate --help): 0.5-1 hour
- Defect 4 (dispatcher --help): 0.5-1 hour

**Total Range:** 3-6 hours (all four defects)

If scoped to Defect 1 only: 1-2 hours

**Confidence:** High

**Reasoning:**
- Root cause is definitively identified (no guesswork involved)
- Established pattern exists in multiple other commands to follow
- Fix is purely additive (early guard + help text function), no existing behavior changes
- E2E test infrastructure already exists in `review-state.bats`

## Testing Strategy

**For each defect:**

1. **Regression test** -- Add BATS test that runs `./iw review-state update --help` and asserts:
   - Exit code is 0
   - Output contains key flag names (e.g., `--status`, `--display-text`, `--display-type`)
   - No file is modified (create a review-state.json, run with --help, verify `last_updated` unchanged)
2. **Fix verification** -- Confirm the new test passes after adding the `--help` guard
3. **Side-effect check** -- Run full `./iw test` to verify no regressions

**Test Data:**
- For the update --help mutation test: create a known review-state.json, run `--help`, compare file contents before/after (should be identical)
- For write/validate --help: simply verify exit 0 and output contains usage text

**Proposed test cases to add to `review-state.bats`:**

```bash
@test "review-state update: --help shows usage and exits 0" {
    run "$PROJECT_ROOT/iw" review-state update --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--status"* ]]
    [[ "$output" == *"--display-text"* ]]
}

@test "review-state update: --help does not modify state file" {
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"
    local before
    before="$(cat "$TEST_TMPDIR/state.json")"

    run "$PROJECT_ROOT/iw" review-state update --help --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    local after
    after="$(cat "$TEST_TMPDIR/state.json")"
    [ "$before" = "$after" ]
}
```

## Dependencies

### Prerequisites
- None -- all code and test infrastructure is available in the repository

### External Blockers
- None

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers (scope: all 4 defects or just update? help text format?)
2. Run **dx-create-tasks** with the issue ID
3. Run **dx-fix** for systematic investigate-and-fix

## Key Files

- `/home/mph/Devel/projects/iw-cli-IW-202/.iw/commands/review-state/update.scala` -- Primary buggy file (no --help handling, line 44 onward)
- `/home/mph/Devel/projects/iw-cli-IW-202/.iw/commands/review-state/write.scala` -- Same bug (line 28)
- `/home/mph/Devel/projects/iw-cli-IW-202/.iw/commands/review-state/validate.scala` -- Same bug (line 14)
- `/home/mph/Devel/projects/iw-cli-IW-202/.iw/commands/review-state.scala` -- Dispatcher, no --help case (line 28 match)
- `/home/mph/Devel/projects/iw-cli-IW-202/.iw/test/review-state.bats` -- E2E tests, no --help tests
- `/home/mph/Devel/projects/iw-cli-IW-202/.iw/commands/feedback.scala` -- Reference pattern for --help (lines 18-21, 49-63)
- `/home/mph/Devel/projects/iw-cli-IW-202/.iw/commands/dashboard.scala` -- Reference pattern for --help (lines 31-37, 196-228)
