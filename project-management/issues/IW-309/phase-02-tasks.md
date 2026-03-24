# Phase 2 Tasks: `implement` command — workflow-aware dispatcher

## Setup

- [x] [setup] Read existing command patterns (`batch-implement.scala`, `analyze.scala`) for reference
- [x] [setup] Read `BatchImplement.resolveWorkflowCode()` to understand workflow type mapping
- [x] [setup] Read `ReviewStateAdapter.read()` to understand review-state.json reading
- [x] [setup] Create mock `claude` script pattern that records args to a file for test verification
- [x] [setup] Create mock `iw` wrapper for `--batch` delegation tests

## Tests

### Error cases

- [x] [test] Error when no issue ID provided and not on an issue branch (exit 1, usage message)
- [x] [test] Error when review-state.json is missing (exit 1, guidance message)
- [x] [test] Error when workflow_type is missing from review-state.json (exit 1, guidance message)
- [x] [test] Error for unrecognized workflow type (exit 1, error message)

### Interactive mode — workflow dispatch

- [x] [test] Correct claude command for agile workflow (`workflow_type: "agile"` → `/iterative-works:ag-implement`)
- [x] [test] Correct claude command for waterfall workflow (`workflow_type: "waterfall"` → `/iterative-works:wf-implement`)
- [x] [test] Correct claude command for diagnostic workflow (`workflow_type: "diagnostic"` → `/iterative-works:dx-implement`)

### Flag passthrough

- [x] [test] `--phase N` is included in the slash command prompt string
- [x] [test] `--model MODEL` is passed through to claude args

### Batch mode

- [x] [test] `--batch` delegates to `iw batch-implement` with issue ID and flags passed through

### Issue ID resolution

- [x] [test] Issue ID resolved from branch name when no explicit ID provided

## Implementation

- [x] [impl] Create `.iw/commands/implement.scala` with `PURPOSE:` header and `@main` entry point
- [x] [impl] Implement arg parsing: positional issue ID, `--batch`, `--phase N`, `--model MODEL`, `--max-turns`, `--max-budget-usd`
- [x] [impl] Implement issue ID resolution from positional arg or branch via `IssueId.fromBranch()`
- [x] [impl] Implement `--batch` path: delegate to `iw batch-implement` via `ProcessAdapter.runInteractive()` with flag passthrough
- [x] [impl] Implement interactive path: read review-state.json, extract workflow_type
- [x] [impl] Implement interactive path: map workflow type to code via `BatchImplement.resolveWorkflowCode()`
- [x] [impl] Implement interactive path: check `claude` CLI exists on PATH
- [x] [impl] Implement interactive path: build and spawn claude command with correct prompt and flags
- [x] [impl] Implement error handling for all failure cases (missing file, missing field, unknown type, missing claude)

## Integration

- [x] [verify] All `implement.bats` tests pass
- [x] [verify] Existing `analyze.bats` tests still pass (regression)
- [x] [verify] Existing `batch-implement` tests still pass (regression)
- [x] [verify] Core compilation with `-Werror` passes
- [x] [verify] No changes to existing files
