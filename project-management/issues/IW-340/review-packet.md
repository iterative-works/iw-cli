---
generated_from: b8e160b27160e6be1f34cfe354ade292dba89d6a
generated_at: 2026-04-14T06:31:08Z
branch: IW-340
issue_id: IW-340
phase: 1
files_analyzed:
  - commands/review-state/update.scala
  - commands/review-state/write.scala
  - test/review-state.bats
---

# Review Packet: IW-340 - Add --commit flag to review-state commands

## Goals

This feature adds an optional `--commit` boolean flag to the `review-state update` and `review-state write` commands. When present, after writing `review-state.json`, the command stages and commits the file using the existing `GitAdapter.commitFileWithRetry()`.

The problem being solved: kanon workflow blueprints call `./iw review-state update` at state transitions (`context_ready`, `tasks_ready`, `review_failed`, `all_complete`), which leaves `review-state.json` as an uncommitted change. This caused "dirty working tree" errors at phase boundaries and forced a fragile `commitLeftovers()` workaround in batch-implement mode that created messy git history.

Key objectives:
- Add `--commit` flag to `review-state update` — writes and commits in one step
- Add `--commit` flag to `review-state write` — same behaviour for initial creation
- Commit message follows `chore(<issueId>): update review-state [to <status>]` convention
- Commit failure is non-fatal: a warning is printed but the command exits 0 (file was already written)
- Flag is purely opt-in — existing callers are unaffected

## Scenarios

- [ ] `review-state update --commit` writes review-state.json and leaves a clean working tree
- [ ] `review-state update --commit --status <s>` produces commit message `chore(<id>): update review-state to <s>`
- [ ] `review-state update --commit` without `--status` produces generic commit message (no "to" clause)
- [ ] `review-state update` without `--commit` writes the file but does not commit (dirty tree)
- [ ] `review-state update --help` documents `--commit`
- [ ] `review-state write --commit` writes review-state.json and leaves a clean working tree
- [ ] `review-state write --from-stdin --commit` also stages and commits
- [ ] `review-state write --commit --status <s>` produces commit message `chore(<id>): update review-state to <s>`
- [ ] `review-state write` without `--commit` writes the file but does not commit (dirty tree)
- [ ] `review-state write --help` documents `--commit`
- [ ] All existing tests continue to pass unchanged

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `commands/review-state/update.scala` | `update()` main, lines 227-237 | Core change: `--commit` detection and `commitFileWithRetry` call |
| `commands/review-state/write.scala` | `commitIfRequested()`, lines 274-289 | Shared helper used by both `handleFlags` and `handleStdin` |
| `test/review-state.bats` | `@test "review-state update: --commit..."` (line 723+) | E2E coverage for all new flag behaviour |

## Diagrams

### Control Flow — update.scala with --commit

```
update() args
  |
  +-- parse --issue-id / infer from branch
  +-- parse --input / derive from issue_id
  +-- read existing JSON
  +-- build UpdateInput from flags
  +-- ReviewStateUpdater.merge()
  +-- ReviewStateValidator.validate()
       |
       +-- invalid --> exit 1
  |
  +-- os.write.over(inputPath, mergedJson)
  |
  +-- argList.contains("--commit")?
       |
       YES --> commitFileWithRetry(inputPath, msg, dir)
                |
                +-- Left(err) --> Output.warning(...)  [non-fatal, continues]
                +-- Right(_)  --> (silent success)
  |
  Output.success(...)
```

### Control Flow — write.scala with --commit

```
write() args
  |
  +-- --from-stdin?  --> handleStdin(argList)
  +-- else           --> handleFlags(argList)
                              |
                              ... build + validate + write ...
  Both paths call:
  commitIfRequested(argList, issueId, outputPath)
    |
    +-- argList.contains("--commit")?
         YES --> commitFileWithRetry(outputPath, msg, dir)
                  +-- Left(err) --> Output.warning(...)
```

### Relationship to existing infrastructure

```
update.scala / write.scala
        |
        | calls
        v
GitAdapter.commitFileWithRetry(path, message, dir)   [core/adapters/Git.scala:162]
        |
        | stages single file + commits with retry on lock contention
        v
git (subprocess)
```

No new infrastructure. The same function is already used by `phase-start.scala`, `phase-pr.scala`, `phase-advance.scala`, and `phase-merge.scala`.

## Test Summary

All tests are E2E (BATS). There are no unit tests for these command scripts — this is consistent with the rest of the codebase where command scripts are covered exclusively by BATS.

### New tests (10 total)

**update.scala — 5 tests** (lines 723-804 in `test/review-state.bats`):

| # | Test name | Type | Verifies |
|---|-----------|------|----------|
| 1 | `--commit stages and commits review-state.json (clean tree after)` | E2E | `git status --porcelain` is empty after commit |
| 2 | `--commit message contains issue ID and status` | E2E | `git log -1 --format=%s` contains issue ID and status value |
| 3 | `--commit without --status uses generic commit message` | E2E | Commit message contains issue ID but NOT ` to ` |
| 4 | `without --commit, file is written but not committed` | E2E | `git status --porcelain` is non-empty (dirty) |
| 5 | `--help includes --commit flag` | E2E | `--help` output contains the string `--commit` |

**write.scala — 5 tests** (lines 810-876 in `test/review-state.bats`):

| # | Test name | Type | Verifies |
|---|-----------|------|----------|
| 1 | `--commit stages and commits the written file (clean tree after)` | E2E | Clean tree via `git status --porcelain` |
| 2 | `without --commit, file is written but not committed` | E2E | File exists but tree is dirty |
| 3 | `--from-stdin --commit stages and commits (clean tree after)` | E2E | stdin path also commits correctly |
| 4 | `--commit message contains issue ID and status` | E2E | Commit message contains issue ID and status value |
| 5 | `--help includes --commit flag` | E2E | `--help` output contains `--commit` |

### Test infrastructure note

The new tests introduce a `setup_git_repo` helper (line 714-721) that initialises a bare git repo in the temp directory and makes an initial empty commit. This is needed because existing tests use plain temp dirs without git, but `commitFileWithRetry` requires a git working tree.

### Existing tests

The full existing suite (70+ tests) covers `validate`, `write`, and `update` without `--commit`. All must continue to pass — the flag is purely additive.

## Files Changed

| File | Change Type | Summary |
|------|-------------|---------|
| `commands/review-state/update.scala` | Modified | Added `--commit` flag detection (line 40 ARGS comment, lines 227-237 commit logic, line 319 help text) |
| `commands/review-state/write.scala` | Modified | Added `--commit` flag (line 24 ARGS comment, lines 274-289 `commitIfRequested` helper, line 100 help text); helper called from both `handleFlags` and `handleStdin` paths |
| `test/review-state.bats` | Modified | Added `setup_git_repo` helper and 10 new `@test` blocks for `--commit` behaviour |
| `project-management/issues/IW-340/analysis.md` | Added | Technical analysis document |
| `project-management/issues/IW-340/phase-01-context.md` | Added | Phase 1 scope, approach, acceptance criteria |
| `project-management/issues/IW-340/phase-01-tasks.md` | Added | Detailed task breakdown |
| `project-management/issues/IW-340/tasks.md` | Added | Top-level task index |
| `project-management/issues/IW-340/implementation-log.md` | Added | Implementation notes (2 review iterations, extracted helper, fixed Output.error to Output.warning) |
| `project-management/issues/IW-340/review-state.json` | Added/Updated | Issue tracking state |

### Key implementation detail worth reviewing

In `write.scala`, the `commitIfRequested` helper is shared between `handleStdin` and `handleFlags` (lines 274-289). In `update.scala` the equivalent logic is inlined directly (lines 227-237). This slight asymmetry is intentional: `write.scala` has two code paths that both need to commit, so a helper avoids duplication; `update.scala` has only one write path so inlining is fine. Verify this is the right call.

The commit uses `outputPath / os.up` as the working directory — this is the parent directory of `review-state.json`, not `os.pwd`. Cross-check that `commitFileWithRetry` behaves correctly when the repo root is an ancestor of `outputPath / os.up` rather than equal to it. The existing phase commands all pass `os.pwd` (which is always the repo root), so this is a subtle behavioural difference worth a quick read of `Git.scala:162`.
