# Phase 3 Tasks: batch-implement command script

**Issue:** IW-275
**Phase:** 3 of 3

## Tasks

### Setup

- [x] [setup] Create `.iw/commands/batch-implement.scala` with PURPOSE comments, `//> using dep` directives matching other commands, package imports (`iw.core.model.*`, `iw.core.adapters.*`, `iw.core.output.*`), and an empty `@main def batchImplement(args: String*): Unit` stub that prints usage
- [x] [setup] Create `.iw/test/batch-implement.bats` with PURPOSE comments, `IW_SERVER_DISABLED=1` in `setup()`, helper to create a temporary git repo with `tasks.md` and `review-state.json`, and an empty placeholder test

### Tests First (TDD)

- [x] [test] Write BATS test: missing `tasks.md` → command exits non-zero with an error message mentioning `tasks.md`
- [x] [test] Write BATS test: `claude` CLI not on PATH → command exits non-zero with an error message mentioning `claude`
- [x] [test] Write BATS test: dirty working tree → command exits non-zero with an error message mentioning commit or stash
- [x] [test] Write BATS test: no issue ID on CLI and branch name has no issue ID pattern → command exits non-zero with a usage hint
- [x] [test] Write BATS test: issue ID taken from positional arg → verify it appears in the logged command invocation (use a stub `claude` that records its arguments)
- [x] [test] Write BATS test: workflow code taken from positional arg → verify it appears in the logged command invocation
- [x] [test] Write BATS test: auto-detect issue ID from branch name (`IW-275/something`) → correct issue ID extracted

### Implementation

#### Argument parsing & auto-detection

- [x] [impl] Parse positional args: optional `ISSUE_ID` (matches `[A-Z]+-[0-9]+`) and optional `ag|wf` workflow code; parse named args `--max-budget-usd`, `--model` (default `"opus"`), `--max-turns` (default `50`), `--max-retries` (default `1`)
- [x] [impl] Auto-detect issue ID from branch name via `IssueId.fromBranch(GitAdapter.getCurrentBranch(cwd))` when not provided as positional arg; exit with usage hint if still unresolved
- [x] [impl] Auto-detect workflow code from `review-state.json` via `ReviewStateAdapter.read` + `ujson` when not provided as positional arg; call `BatchImplement.resolveWorkflowCode` and exit on error

#### Pre-flight checks

- [x] [impl] Check `tasks.md` exists in the issue directory; exit with helpful message if missing
- [x] [impl] Check `claude` CLI available via `ProcessAdapter.commandExists`; exit with helpful message if missing
- [x] [impl] Detect forge type via `GitAdapter.getRemoteUrl` + `ForgeType.resolve`; check the appropriate forge CLI (`gh` or `glab`) is available; exit with helpful message if missing
- [x] [impl] Check clean working tree via `GitAdapter.hasUncommittedChanges`; exit with message suggesting commit or stash if dirty

#### Logging

- [x] [impl] Open a log file at `{issue-dir}/batch-implement.log` for append; write a session header (timestamp, resolved issue ID, workflow, model, arg values); define a `log(msg)` helper that writes to both stderr and the log file

#### Phase loop with claude invocation

- [x] [impl] Implement the outer phase loop: read `tasks.md`, call `BatchImplement.nextPhase(MarkdownTaskParser.parsePhaseIndex(lines))`, exit loop when `None`
- [x] [impl] Invoke `claude -p "/iterative-works:{wf|ag}-implement {ISSUE_ID} --phase {N}"` via `ProcessAdapter.runStreaming` with a 30-minute timeout; log the command before running; treat non-zero exit as a recoverable failure (enter recovery loop)
- [x] [impl] After each claude invocation, commit any uncommitted changes claude left behind using `GitAdapter.stageAll` + `GitAdapter.commit` (skip if tree is already clean)

#### Outcome handling

- [x] [impl] Read `review-state.json` status after claude exits; call `BatchImplement.decideOutcome(status)` to get the `PhaseOutcome`
- [x] [impl] Handle `MergePR` outcome: merge the phase PR via `ProcessAdapter.run` with the appropriate forge CLI command (`gh pr merge --merge <url>` or `glab mr merge <id> --yes`); read the PR URL from `review-state.json` via ujson
- [x] [impl] After a successful merge: checkout the feature branch via `GitAdapter.checkoutBranch`, advance it via `GitAdapter.fetchAndReset`; update review state via `ReviewStateAdapter.update`; mark phase done via `BatchImplement.markPhaseComplete` and write updated `tasks.md`; commit the `tasks.md` change
- [x] [impl] Handle `MarkDone` outcome: mark phase done via `BatchImplement.markPhaseComplete`, write and commit updated `tasks.md`; log that the phase was already merged
- [x] [impl] Handle `Fail` outcome: log the failure reason and exit non-zero

#### Recovery loop

- [x] [impl] Handle `Recover` outcome: build a status-specific prompt inline (mirroring the `recovery_prompt_for_status` logic from the shell script); invoke claude with `--resume` flag via `ProcessAdapter.runStreaming`; commit any uncommitted changes; re-read status and re-decide outcome; repeat up to `--max-retries` times; if still `Recover` after max attempts, exit non-zero with an error message

#### Completion flow

- [x] [impl] After the phase loop exits (all phases done), invoke `claude -p "/iterative-works:{wf|ag}-implement {ISSUE_ID}"` (without `--phase`) via `ProcessAdapter.runStreaming` for the final completion step (release notes, final PR); log before and after

### Integration & Verification

- [x] [verify] Run `./iw test compile` and verify `batch-implement.scala` compiles without errors or warnings
- [x] [verify] Run `scala-cli compile --scalac-option -Werror .iw/core/` and confirm no regressions in core
- [x] [verify] Run `./iw test unit` and confirm all existing unit tests still pass
- [x] [verify] Run `./iw test e2e` and confirm all E2E tests pass including the new `batch-implement.bats`
- [x] [verify] Commit all changes
**Phase Status:** Complete
