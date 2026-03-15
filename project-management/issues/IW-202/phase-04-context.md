# Phase 4: `review-state --help` reports "Unknown subcommand" instead of showing help

## Defect Description

Running `./iw review-state --help` does not display usage information. Instead, `--help` is treated as a subcommand name, falls through to the wildcard default in the `match` expression, and prints "Unknown subcommand: --help" with exit code 1.

The dispatcher already has usage text (printed when `args.isEmpty`), but it is not reachable via `--help`.

## Reproduction Steps

1. Run `./iw review-state --help`
2. Observe: "Unknown subcommand: --help" (exit 1)

**Expected:** Command prints available subcommands (validate, write, update) and exits 0.
**Actual:** Command prints "Unknown subcommand: --help" and exits 1.

## Root Cause

No `--help` or `-h` case in the dispatcher's `match` expression at line 28 of `review-state.scala`. The `--help` argument is taken as `args.head` (the subcommand), and the `match` only handles "validate", "write", "update", and a wildcard default.

**File:** `.iw/commands/review-state.scala` — line 28 match expression has no `--help`/`-h` case.

## Fix Strategy

1. Add a `case "--help" | "-h" =>` branch in the `match` at line 28 that prints usage info and exits 0
2. The usage info already exists at lines 17-22 (shown when `args.isEmpty`) — extract to a `showHelp()` function or inline
3. The empty-args case currently prints "No subcommand provided" as an error (exit 1), while `--help` should print the same info as success (exit 0)

Two options for code structure:
- **Option A:** Add `--help`/`-h` check before the match (early guard), similar to Phases 1-3
- **Option B:** Add `--help`/`-h` case inside the existing match

Either works. Option A is more consistent with the pattern from prior phases.

## Previous Phase Context

- Phase 1 fixed the identical bug in `update.scala` (subcommand)
- Phase 2 fixed the identical bug in `write.scala` (subcommand)
- Phase 3 fixed the identical bug in `validate.scala` (subcommand)
- This phase fixes the dispatcher itself (`review-state.scala`) — the parent command

## Testing Requirements

- E2E test: `./iw review-state --help` exits 0 and output contains subcommand names ("validate", "write", "update")
- E2E test: `./iw review-state -h` exits 0 with usage text
- Run full test suite to verify no regressions

## Acceptance Criteria

- `./iw review-state --help` prints available subcommands and exits 0
- `./iw review-state -h` prints available subcommands and exits 0
- Help text lists all subcommands (validate, write, update)
- All existing tests continue to pass
