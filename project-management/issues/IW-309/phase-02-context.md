# Phase 2: `implement` command — workflow-aware dispatcher

## Goals

Provide `iw implement [issueId]` as the standard entry point for implementation work. The command reads `review-state.json` to determine the workflow type, maps it to the correct slash command prefix, and spawns `claude` as an interactive foreground process. When `--batch` is passed, it delegates to `iw batch-implement` instead.

## Scope

**Included:**
- New command file: `.iw/commands/implement.scala`
- E2E tests: `.iw/test/implement.bats`
- Interactive mode: read workflow type, resolve code, spawn `claude` with correct prompt
- Batch mode: delegate to `iw batch-implement` with all flags passed through
- Error handling for missing issue ID, missing review-state.json, missing workflow_type, missing `claude` CLI, unrecognized workflow type

**Excluded:**
- Changes to existing commands or adapters
- Changes to `batch-implement.scala`
- New model or adapter code (all building blocks already exist)
- Dashboard or server changes

## Dependencies

Existing code used by this phase (all already present in the codebase):

| Component | Location | Usage |
|-----------|----------|-------|
| `BatchImplement.resolveWorkflowCode()` | `.iw/core/model/BatchImplement.scala` | Maps `Option[String]` workflow type to short code ("ag"/"wf"/"dx") |
| `ReviewStateAdapter.read()` | `.iw/core/adapters/ReviewStateAdapter.scala` | Reads review-state.json, returns `Either[String, String]` (raw JSON) |
| `ProcessAdapter.runInteractive()` | `.iw/core/adapters/Process.scala` | Spawns subprocess with inherited stdin/stdout/stderr |
| `ProcessAdapter.commandExists()` | `.iw/core/adapters/Process.scala` | Checks if a CLI tool is on PATH |
| `GitAdapter.getCurrentBranch()` | `.iw/core/adapters/Git.scala` | Returns current branch name |
| `IssueId.parse()` / `IssueId.fromBranch()` | `.iw/core/model/IssueId.scala` | Issue ID resolution from arg or branch name |
| `CommandHelpers.exitOnError()` | `.iw/core/adapters/CommandHelpers.scala` | Unwraps Either, exits on Left |
| `Output.error()` / `Output.info()` | `.iw/core/output/Output.scala` | CLI messages |
| `PhaseArgs.namedArg()` | `.iw/core/model/PhaseArgs.scala` | Named argument parsing from arg list |
| `findIwRun()` pattern | `.iw/commands/analyze.scala` | Locates iw-run relative to IW_COMMANDS_DIR |

No new model or adapter code is needed.

## Approach

1. **Write failing E2E tests** (`.iw/test/implement.bats`)
   - Create mock `claude` and `iw` scripts that record their arguments to a file
   - Test error cases: no issue ID, missing review-state.json, missing workflow_type, unrecognized workflow type
   - Test interactive mode: correct `claude` command construction for each workflow type (agile, waterfall, diagnostic)
   - Test `--phase N` passthrough to the slash command prompt
   - Test `--model MODEL` passthrough to claude
   - Test `--batch` delegation to `iw batch-implement`

2. **Implement the command** (`.iw/commands/implement.scala`)
   - Follow the existing command pattern: `@main` entry point, `PURPOSE:` header comments
   - Parse args: optional positional issue ID, `--batch`, `--phase N`, `--model MODEL`, plus batch-mode flags (`--max-turns`, `--max-budget-usd`)
   - Resolve issue ID from positional arg or current branch via `IssueId.fromBranch()`
   - If `--batch`: delegate to `iw batch-implement` via `ProcessAdapter.runInteractive()`, passing through issue ID and all relevant flags; exit with subprocess exit code
   - If interactive mode:
     1. Read `review-state.json` from `project-management/issues/{issueId}/review-state.json`
     2. Extract `workflow_type` from JSON
     3. Map to code via `BatchImplement.resolveWorkflowCode()`
     4. Check `claude` CLI exists on PATH
     5. Build claude command: `claude --dangerously-skip-permissions -p "/iterative-works:{code}-implement {issueId} --phase {N}"` (with `--phase` if given), plus `--model {model}` if given
     6. Spawn via `ProcessAdapter.runInteractive()`
     7. Exit with claude's exit code

3. **Verify all tests pass** — both new `implement.bats` and existing tests (regression)

## Files to Create/Modify

| File | Action | Description |
|------|--------|-------------|
| `.iw/commands/implement.scala` | **Create** | New command — workflow-aware dispatcher |
| `.iw/test/implement.bats` | **Create** | E2E tests for the `implement` command |

No existing files are modified.

## Testing Strategy

### E2E Tests (`.iw/test/implement.bats`)

Tests follow the established BATS patterns from `analyze.bats`:

**Setup:**
- `export IW_SERVER_DISABLED=1` to prevent dashboard server interaction
- `export IW_TMUX_SOCKET="$TMUX_SOCKET"` for tmux socket isolation
- Temporary git repo with `.iw/config.conf`
- Create a mock `claude` script on PATH that records its arguments to a file (e.g., `$TEST_DIR/claude-args`), allowing verification of correct command construction without actually running claude
- For `--batch` tests: create a mock `iw` wrapper or use a mock `batch-implement` that records its arguments

**Teardown:**
- Kill tmux sessions on test socket
- Clean up worktree sibling directories (`testproject-*`)
- Remove temporary directory

**Test cases:**

1. **Error when no issue ID and not on an issue branch** — run `./iw implement` with no args on a non-issue branch, assert exit code 1 and output contains error/usage message

2. **Error when review-state.json is missing** — run with a valid issue ID but no review-state.json, assert exit code 1 and output contains guidance about review-state.json

3. **Error when workflow_type is missing from review-state.json** — create review-state.json without `workflow_type` field, assert exit code 1 and output contains guidance

4. **Error for unrecognized workflow type** — create review-state.json with `workflow_type: "unknown"`, assert exit code 1

5. **Correct claude command for agile workflow** — create review-state.json with `workflow_type: "agile"`, run `./iw implement IWLE-123`, verify mock claude received args containing `/iterative-works:ag-implement`

6. **Correct claude command for waterfall workflow** — same as above with `workflow_type: "waterfall"`, verify `/iterative-works:wf-implement`

7. **Correct claude command for diagnostic workflow** — same as above with `workflow_type: "diagnostic"`, verify `/iterative-works:dx-implement`

8. **--phase N passthrough** — run with `--phase 2`, verify mock claude args contain `--phase 2` in the prompt string

9. **--model passthrough** — run with `--model sonnet`, verify mock claude args contain `--model sonnet`

10. **--batch delegates to batch-implement** — run with `--batch`, verify it invokes `iw batch-implement` with the issue ID and other flags passed through

11. **Issue ID resolved from branch** — checkout a branch named like an issue ID (e.g., `IWLE-999`), run `./iw implement` without explicit ID, verify correct issue ID is used

**Mock script pattern:**
```bash
# Create mock claude that records args
mkdir -p "$TEST_DIR/bin"
cat > "$TEST_DIR/bin/claude" << 'MOCK'
#!/bin/bash
echo "$@" > "$MOCK_ARGS_FILE"
exit 0
MOCK
chmod +x "$TEST_DIR/bin/claude"
export PATH="$TEST_DIR/bin:$PATH"
```

### Regression

- Existing `analyze.bats` tests must continue to pass
- Existing `batch-implement` tests (if any) must continue to pass

## Acceptance Criteria

- [ ] `iw implement IWLE-123` reads workflow type from review-state.json and spawns claude with the correct `/iterative-works:{code}-implement` prompt
- [ ] `iw implement` (no args, on an issue branch) resolves issue ID from branch name
- [ ] `iw implement` (no args, not on issue branch) exits with code 1 and shows error
- [ ] Missing review-state.json exits with code 1 and shows guidance
- [ ] Missing workflow_type exits with code 1 and shows guidance
- [ ] Unrecognized workflow type exits with code 1 and shows error
- [ ] `--phase N` is included in the slash command prompt
- [ ] `--model MODEL` is passed through to claude
- [ ] `--batch` delegates to `iw batch-implement` with all relevant flags
- [ ] All tests in `implement.bats` pass
- [ ] All existing tests continue to pass
- [ ] Command file has `PURPOSE:` header comments
- [ ] No changes to existing files
