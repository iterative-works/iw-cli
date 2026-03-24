# Phase 1: `analyze` command — triage shortcut

## Goals

Provide a discoverable `iw analyze <issueId>` entry point that replaces the verbose incantation `iw start <issueId> --prompt "/iterative-works:triage-issue"`. The command is a thin subprocess delegation — no new domain logic, adapters, or abstractions.

## Scope

**Included:**
- New command file: `.iw/commands/analyze.scala`
- E2E tests: `.iw/test/analyze.bats`
- Error handling for missing issue ID

**Excluded:**
- The `implement` command (Phase 2)
- Any changes to existing commands, model, or adapters
- Workflow detection or `review-state.json` reading

## Dependencies

Existing code used by this phase (all already present in the codebase):

| Component | Location | Usage |
|-----------|----------|-------|
| `ProcessAdapter.runInteractive()` | `.iw/core/adapters/Process.scala` | Spawn `iw start` as a subprocess with inherited I/O |
| `Output.error()` / `Output.info()` | `.iw/core/output/Output.scala` | Error and usage messages |
| `iw start --prompt` | `.iw/commands/start.scala` | The underlying command being delegated to |
| Bootstrap script | `./iw` | Resolves and runs commands from `.iw/commands/` |

No new model or adapter code is needed.

## Approach

1. **Write failing E2E tests** (`.iw/test/analyze.bats`)
   - Test: error exit when no issue ID is provided
   - Test: correct delegation to `iw start <issueId> --prompt "/iterative-works:triage-issue"`

2. **Implement the command** (`.iw/commands/analyze.scala`)
   - Follow the existing command pattern: `@main` entry point, `PURPOSE:` header comments
   - Parse args — require at least one positional argument (the issue ID)
   - On missing issue ID: print error + usage, exit 1
   - Delegate to `iw start <issueId> --prompt "/iterative-works:triage-issue"` via `ProcessAdapter.runInteractive()`
   - Exit with the subprocess exit code

3. **Verify all tests pass** — both new `analyze.bats` and existing `start-prompt.bats` (regression)

## Files to Create/Modify

| File | Action | Description |
|------|--------|-------------|
| `.iw/commands/analyze.scala` | **Create** | New command — thin wrapper delegating to `iw start` |
| `.iw/test/analyze.bats` | **Create** | E2E tests for the `analyze` command |

No existing files are modified.

## Testing Strategy

### E2E Tests (`.iw/test/analyze.bats`)

Tests follow the established BATS patterns from `start.bats` / `start-prompt.bats`:

**Setup:**
- `export IW_SERVER_DISABLED=1` to prevent dashboard server interaction
- `export IW_TMUX_SOCKET="$TMUX_SOCKET"` for tmux socket isolation
- Temporary git repo with `.iw/config.conf` (Linear tracker, team TEST)

**Teardown:**
- Kill tmux sessions on test socket
- Clean up worktree sibling directories (`testproject-*`)
- Remove temporary directory

**Test cases:**

1. **Error when no issue ID provided** — run `./iw analyze` with no args, assert exit code 1 and output contains usage/error message

2. **Delegates to `iw start` with correct args** — run `./iw analyze IWLE-123`, assert exit code 0, assert worktree is created (proving `iw start` was called), assert tmux session exists

3. **Prompt flag is passed correctly** — after `./iw analyze IWLE-456`, capture tmux pane content and verify it contains `claude --dangerously-skip-permissions` and `/iterative-works:triage-issue` (proving `--prompt` was forwarded)

### Regression

- Existing `start-prompt.bats` tests must continue to pass (no changes to `start.scala`)

## Acceptance Criteria

- [ ] `iw analyze <issueId>` creates a worktree and tmux session with claude agent launched using the triage prompt
- [ ] `iw analyze` (no args) exits with code 1 and shows a clear error message
- [ ] All tests in `analyze.bats` pass
- [ ] All existing tests continue to pass (specifically `start-prompt.bats`)
- [ ] Command file has `PURPOSE:` header comments
- [ ] No changes to existing files
