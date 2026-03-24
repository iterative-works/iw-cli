# Phase 2 Tasks: `implement` command — workflow-aware dispatcher

## Setup

- [ ] [setup] Read existing command patterns (`batch-implement.scala`, `analyze.scala`) for reference
- [ ] [setup] Read `BatchImplement.resolveWorkflowCode()` to understand workflow type mapping
- [ ] [setup] Read `ReviewStateAdapter.read()` to understand review-state.json reading
- [ ] [setup] Create mock `claude` script pattern that records args to a file for test verification
- [ ] [setup] Create mock `iw` wrapper for `--batch` delegation tests

## Tests

### Error cases

- [ ] [test] Error when no issue ID provided and not on an issue branch (exit 1, usage message)
- [ ] [test] Error when review-state.json is missing (exit 1, guidance message)
- [ ] [test] Error when workflow_type is missing from review-state.json (exit 1, guidance message)
- [ ] [test] Error for unrecognized workflow type (exit 1, error message)

### Interactive mode — workflow dispatch

- [ ] [test] Correct claude command for agile workflow (`workflow_type: "agile"` → `/iterative-works:ag-implement`)
- [ ] [test] Correct claude command for waterfall workflow (`workflow_type: "waterfall"` → `/iterative-works:wf-implement`)
- [ ] [test] Correct claude command for diagnostic workflow (`workflow_type: "diagnostic"` → `/iterative-works:dx-implement`)

### Flag passthrough

- [ ] [test] `--phase N` is included in the slash command prompt string
- [ ] [test] `--model MODEL` is passed through to claude args

### Batch mode

- [ ] [test] `--batch` delegates to `iw batch-implement` with issue ID and flags passed through

### Issue ID resolution

- [ ] [test] Issue ID resolved from branch name when no explicit ID provided

## Implementation

- [ ] [impl] Create `.iw/commands/implement.scala` with `PURPOSE:` header and `@main` entry point
- [ ] [impl] Implement arg parsing: positional issue ID, `--batch`, `--phase N`, `--model MODEL`, `--max-turns`, `--max-budget-usd`
- [ ] [impl] Implement issue ID resolution from positional arg or branch via `IssueId.fromBranch()`
- [ ] [impl] Implement `--batch` path: delegate to `iw batch-implement` via `ProcessAdapter.runInteractive()` with flag passthrough
- [ ] [impl] Implement interactive path: read review-state.json, extract workflow_type
- [ ] [impl] Implement interactive path: map workflow type to code via `BatchImplement.resolveWorkflowCode()`
- [ ] [impl] Implement interactive path: check `claude` CLI exists on PATH
- [ ] [impl] Implement interactive path: build and spawn claude command with correct prompt and flags
- [ ] [impl] Implement error handling for all failure cases (missing file, missing field, unknown type, missing claude)

## Integration

- [ ] [verify] All `implement.bats` tests pass
- [ ] [verify] Existing `analyze.bats` tests still pass (regression)
- [ ] [verify] Existing `batch-implement` tests still pass (regression)
- [ ] [verify] Core compilation with `-Werror` passes
- [ ] [verify] No changes to existing files
