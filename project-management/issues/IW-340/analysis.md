# Technical Analysis: Add --commit flag to review-state update command

**Issue:** IW-340
**Created:** 2026-04-13
**Status:** Draft

## Problem Statement

The `review-state update` command writes `review-state.json` to disk but does not commit it. When kanon workflow blueprints call `./iw review-state update` at state transitions (`context_ready`, `tasks_ready`, `review_failed`, `all_complete`), review-state.json is left as an uncommitted change. This causes "dirty working tree" errors at phase boundaries and forces a fragile `commitLeftovers()` workaround in batch-implement mode that creates messy git history.

## Proposed Solution

### High-Level Approach

Add an optional `--commit` boolean flag to `commands/review-state/update.scala`. When present, after writing review-state.json, the command stages and commits the file using the existing `GitAdapter.commitFileWithRetry()`. The commit message follows the established convention: `chore(<issue-id>): update review-state to <status>`.

This is a small, self-contained change. The issue ID and status are already parsed in `update.scala` (lines 57–65 and 82 respectively), and the commit pattern is well-established across `phase-start.scala`, `phase-pr.scala`, `phase-advance.scala`, and `phase-merge.scala`.

### Why This Approach

The alternative would be to always commit (no flag). However, that would change existing behavior and could break callers that intentionally batch multiple changes before committing. A flag preserves backward compatibility and gives callers explicit control.

## Architecture Design

### Presentation Layer (Command Scripts)

This is the only layer that needs changes. No domain, application, or infrastructure changes are required — all necessary infrastructure already exists.

**Components:**
- `commands/review-state/update.scala` — add `--commit` flag parsing and post-write commit logic
- `commands/review-state/write.scala` — add same `--commit` flag (same pattern, same rationale)

**Responsibilities:**
- Parse the `--commit` boolean flag from CLI args
- After successful write, if `--commit` is present, call `GitAdapter.commitFileWithRetry()` with the output path
- Construct commit message from already-parsed `issueId` and `status` (falling back to generic message if status absent)
- Report commit success/failure via `Output`
- Update `showHelp()` to document `--commit`
- Update file header ARGS comments

**Existing Infrastructure Used:**
- `GitAdapter.commitFileWithRetry(path: os.Path, message: String, dir: os.Path): Either[String, String]` — stages and commits a single file with retry on transient failures (e.g., `index.lock`). Located at `core/adapters/Git.scala:162`.
- `Output.error()`, `Output.success()` — CLI output formatting

**Estimated Effort:** 1–2 hours for update.scala, 0.5–1 hour for write.scala

## Technical Decisions

### Patterns

- Follow the exact same commit-after-write pattern used in `phase-start.scala` (lines 71–81): call `commitFileWithRetry`, handle `Left` with a warning via `Output.error`, do not exit on commit failure
- Boolean flag detection: `argList.contains("--commit")` — consistent with how `--clear-*` and `--needs-attention` flags are already detected
- No new dependencies: uses existing `GitAdapter.commitFileWithRetry` and `Output`

### Commit Message Construction

Use the `--status` flag value if provided: `chore(<issueId>): update review-state to <status>`. If `--status` is not among the flags (e.g., user is only updating display text), use the generic: `chore(<issueId>): update review-state`. This avoids re-parsing the merged JSON and is consistent with how the issue described the feature.

### Commit Failure Behavior

When `--commit` is provided but the commit fails, the command should:
1. Still succeed at the primary task (writing the file) — the file has already been written
2. Print a warning about the commit failure via `Output.error`
3. Exit with code 0 (not fail the overall command)

This matches the existing pattern in phase commands, where commit failure of review-state is treated as a non-fatal warning. Callers that need to guarantee the commit can check git status afterward.

## Technical Risks & Uncertainties

### Resolved: write.scala also gets --commit

Both `update.scala` and `write.scala` get the `--commit` flag. The `write` command is used at initial creation (e.g., by `wf-create-analysis`) and has the same uncommitted-file problem.

### Resolved: Commit message when --status is not provided

When `--commit` is used but `--status` is not among the flags, the commit message uses the generic form: `chore(<issueId>): update review-state`. When `--status` is provided, it includes it: `chore(<issueId>): update review-state to <status>`.

## Total Estimates

**Per-Layer Breakdown:**
- Presentation Layer (update.scala): 1–2 hours
- Presentation Layer (write.scala): 0.5–1 hour
- E2E Tests: 1–2 hours

**Total Range:** 2.5 – 5 hours (including both commands and tests)

**Confidence:** High — pattern is well-established in four other commands, all required infrastructure exists.

## Testing Strategy

### E2E Tests (BATS)

Tests for `--commit` require a git-initialized temp directory (existing E2E tests use plain temp dirs). Must export `IW_SERVER_DISABLED=1` in setup.

**Tests for update.scala:**
1. `--commit` flag stages and commits review-state.json — verify with `git log --oneline -1` and `git status` (clean tree)
2. `--commit` commit message contains issue ID
3. `--commit` commit message contains status when `--status` is provided
4. `--commit` without `--status` produces a generic commit message (no status in message)
5. Without `--commit`, file is written but not committed (working tree is dirty)
6. `--commit` with validation failure does NOT commit (file unchanged, no commit)
7. `--help` output includes `--commit` flag

**Tests for write.scala:**
1. `--commit` flag stages and commits the written file
2. Without `--commit`, file is written but not committed
3. `--help` output includes `--commit` flag

**Test Data Strategy:**
- Create a git-initialized temp directory in setup
- Seed with a valid review-state.json that is already committed
- Run update with `--commit` and verify git log

**Regression Coverage:**
- Existing tests (without `--commit`) must continue to pass unchanged
- The flag is purely additive; no existing behavior is modified

## Implementation Sequence

**Recommended Layer Order:**

1. **Presentation Layer — update.scala**: Add `--commit` flag parsing, post-write commit logic, help text update, ARGS comment update.
2. **Presentation Layer — write.scala**: Same pattern applied to the write command.
3. **E2E Tests**: BATS tests for both commands with `--commit`.

**Ordering Rationale:**
- No domain or infrastructure changes, so implementation starts and ends in the presentation layer.
- Both commands can be done in parallel since they are independent scripts.
- Tests follow TDD — write failing tests first, then implement.

## Deployment Considerations

- Ship the flag in iw-cli first
- Then update kanon workflow blueprints to add `--commit` to all `review-state update` calls
- The flag is backward-compatible — old callers are unaffected
- Rollback: remove `--commit` from kanon blueprints (they revert to current behavior)

## Dependencies

- **Prerequisites:** None. All required infrastructure exists.
- **External Blockers:** None.
