# Phase 1: Add --commit flag to review-state commands

## Goals

Add an optional `--commit` boolean flag to `review-state update` and `review-state write` commands. When present, after writing review-state.json, the command stages and commits the file using `GitAdapter.commitFileWithRetry()`.

## Scope

### In Scope
- `--commit` flag parsing in `commands/review-state/update.scala`
- `--commit` flag parsing in `commands/review-state/write.scala`
- Post-write commit logic using `GitAdapter.commitFileWithRetry()`
- Commit message construction from issue ID and status
- Help text updates for both commands
- ARGS comment updates for both commands
- E2E BATS tests for the new flag

### Out of Scope
- Changes to core library code
- Changes to `GitAdapter` or any infrastructure
- Changes to `review-state validate` command
- Always-commit behavior (flag is opt-in)

## Dependencies

- `GitAdapter.commitFileWithRetry(path, message, dir)` at `core/adapters/Git.scala:162` — already exists
- `Output.error()`, `Output.success()` — already available in both commands

## Approach

### Pattern Reference

Follow the commit-after-write pattern from `phase-start.scala` (lines 70–81):
```scala
GitAdapter
  .commitFileWithRetry(
    reviewStatePath,
    s"chore(${issueId.value}): update review-state for phase ${phaseNumber.value}",
    os.pwd
  )
  .left
  .foreach(err =>
    Output.error(s"Warning: Failed to commit review-state update: $err")
  )
```

### Flag Detection

Use `argList.contains("--commit")` — consistent with existing boolean flags like `--needs-attention` and `--clear-*`.

### Commit Message Format

- With `--status`: `chore(<issueId>): update review-state to <status>`
- Without `--status`: `chore(<issueId>): update review-state`

### Commit Failure Behavior

Non-fatal: print warning via `Output.error`, exit 0 (file was already written successfully).

## Files to Modify

1. `commands/review-state/update.scala` — add `--commit` flag, post-write commit, help text, ARGS comment
2. `commands/review-state/write.scala` — add `--commit` flag, post-write commit, help text, ARGS comment
3. `test/review-state.bats` — add E2E tests for `--commit` flag

## Testing Strategy

Tests require a git-initialized temp directory (existing tests use plain temp dirs). Must export `IW_SERVER_DISABLED=1`.

### update.scala tests
1. `--commit` stages and commits review-state.json (clean tree after)
2. Commit message contains issue ID and status when `--status` provided
3. Without `--status`, commit message uses generic form (no status)
4. Without `--commit`, file is written but not committed (dirty tree)
5. `--help` output includes `--commit`

### write.scala tests
1. `--commit` stages and commits the written file (clean tree after)
2. Without `--commit`, file is written but not committed
3. `--help` output includes `--commit`

## Acceptance Criteria

- [ ] `./iw review-state update --commit` writes and commits review-state.json
- [ ] `./iw review-state write --commit` writes and commits review-state.json
- [ ] Commit messages follow `chore(<issueId>): update review-state [to <status>]` format
- [ ] Commit failure is non-fatal (warning printed, exit 0)
- [ ] `--help` for both commands documents `--commit`
- [ ] All existing tests continue to pass
- [ ] All new tests pass
- [ ] Code compiles with `-Werror`
