# Phase 3 Tasks: Presentation Layer

**Issue:** IW-238
**Phase:** 3 of 3
**Context:** [phase-03-context.md](phase-03-context.md)

## Setup

- [ ] [setup] Verify all existing tests pass (`./iw test`) to establish a clean baseline
- [ ] [setup] Add `AdvanceOutput` case class to `.iw/core/model/PhaseOutput.scala`
- [ ] [setup] Create `.iw/commands/phase-start.scala` with PURPOSE header and `@main` stub
- [ ] [setup] Create `.iw/commands/phase-commit.scala` with PURPOSE header and `@main` stub
- [ ] [setup] Create `.iw/commands/phase-pr.scala` with PURPOSE header and `@main` stub
- [ ] [setup] Create `.iw/commands/phase-advance.scala` with PURPOSE header and `@main` stub

## Tests First (TDD)

### AdvanceOutput unit test

- [ ] [test] Add test to `.iw/core/test/PhaseOutputTest.scala`: `AdvanceOutput.toJson` produces valid JSON with issueId, phaseNumber, branch, previousBranch, headSha

### phase-start E2E tests

- [ ] [test] Create `.iw/test/phase-start.bats` with test: `phase-start 1` on a feature branch creates sub-branch and outputs valid JSON with issueId, phaseNumber, branch, baselineSha
- [ ] [test] Test: `phase-start 1` — branch name is `{issue}-phase-01` (verify with `git branch --show-current`)
- [ ] [test] Test: `phase-start` with no args exits with error and usage message
- [ ] [test] Test: `phase-start abc` (invalid phase number) exits with error
- [ ] [test] Test: `phase-start 1` when already on a phase sub-branch exits with error
- [ ] [test] Test: `phase-start 1` when phase branch already exists exits with error
- [ ] [test] Test: `phase-start 2` with `--issue-id IW-999` uses the provided issue ID in output

### phase-commit E2E tests

- [ ] [test] Create `.iw/test/phase-commit.bats` with test: `phase-commit --title "Test commit"` on a phase branch with changes stages, commits, and outputs valid JSON with commitSha, filesCommitted, message
- [ ] [test] Test: commit SHA in output matches actual `git rev-parse HEAD`
- [ ] [test] Test: `phase-commit --title "Title" --items "item1,item2"` produces multi-line commit message with bullets
- [ ] [test] Test: `phase-commit` without `--title` exits with error
- [ ] [test] Test: `phase-commit --title "Test"` when not on a phase branch exits with error
- [ ] [test] Test: `phase-commit --title "Test"` with no changes to commit exits with error
- [ ] [test] Test: `phase-commit --title "Test"` updates phase task file Phase Status to Complete (if task file exists)

### phase-pr E2E tests

- [ ] [test] Create `.iw/test/phase-pr.bats` with test: `phase-pr --title "Test"` when not on a phase branch exits with error
- [ ] [test] Test: `phase-pr` without `--title` exits with error
- [ ] [test] Test: `phase-pr --title "Test"` without config file exits with error about missing config

### phase-advance E2E tests

- [ ] [test] Create `.iw/test/phase-advance.bats` with test: `phase-advance` when not on a phase or feature branch exits with error
- [ ] [test] Test: `phase-advance` when `gh` is not available exits with error

## Implementation

### AdvanceOutput model

- [ ] [impl] Implement `AdvanceOutput` case class with fields: issueId, phaseNumber, branch, previousBranch, headSha
- [ ] [test] Run PhaseOutputTest and verify new test passes

### phase-start command

- [ ] [impl] Implement argument parsing: extract positional phase-number and optional `--issue-id`
- [ ] [impl] Implement issue ID resolution: `--issue-id` arg → `IssueId.parse()`, or infer from branch via `IssueId.fromBranch()`
- [ ] [impl] Implement phase sub-branch detection: verify current branch does NOT match `-phase-\d+$`
- [ ] [impl] Implement branch creation: `PhaseBranch(featureBranch, phaseNumber).branchName` → `GitAdapter.createAndCheckoutBranch()`
- [ ] [impl] Implement baseline SHA capture: `GitAdapter.getFullHeadSha()`
- [ ] [impl] Implement review-state update (best-effort): update status to "implementing"
- [ ] [impl] Implement JSON output: `StartOutput(...).toJson` to stdout
- [ ] [test] Run phase-start E2E tests and verify all pass

### phase-commit command

- [ ] [impl] Implement argument parsing: extract `--title`, `--items`, optional `--issue-id` and `--phase-number`
- [ ] [impl] Implement branch validation: verify current branch IS a phase sub-branch, extract feature branch and phase number
- [ ] [impl] Implement stage and commit: `GitAdapter.stageAll()` → `CommitMessage.build()` → `GitAdapter.commit()`
- [ ] [impl] Implement file count: use `GitAdapter.diffNameOnly()` with parent commit SHA
- [ ] [impl] Implement task file updates: read phase task file, apply `markComplete()` + `markReviewed()`, write back (best-effort)
- [ ] [impl] Implement JSON output: `CommitOutput(...).toJson` to stdout
- [ ] [test] Run phase-commit E2E tests and verify all pass

### phase-pr command

- [ ] [impl] Implement argument parsing: extract `--title`, `--body`, `--batch`, optional `--issue-id` and `--phase-number`
- [ ] [impl] Implement branch validation and feature branch extraction from phase sub-branch name
- [ ] [impl] Implement config and tracker type detection: `ConfigFileRepository.read()` → match on `trackerType`
- [ ] [impl] Implement push: `GitAdapter.push(branchName, os.pwd, setUpstream = true)`
- [ ] [impl] Implement PR/MR creation: dispatch to `GitHubClient.createPullRequest()` or `GitLabClient.createMergeRequest()` based on tracker type
- [ ] [impl] Implement default body generation with `FileUrlBuilder.build()` for artifact links
- [ ] [impl] Implement review-state update: status "awaiting_review" with pr_url
- [ ] [impl] Implement `--batch` path: squash-merge via `ProcessAdapter.run`, then delegate to same advance logic as `phase-advance`
- [ ] [impl] Implement JSON output: `PrOutput(...).toJson` to stdout
- [ ] [test] Run phase-pr E2E tests and verify all pass

### phase-advance command

- [ ] [impl] Implement argument parsing: optional `--issue-id` and `--phase-number`
- [ ] [impl] Implement branch detection: determine if on phase sub-branch or feature branch
- [ ] [impl] Implement PR merge verification: check phase PR/MR is merged via `gh pr list` / `glab mr list`
- [ ] [impl] Implement checkout: if on phase sub-branch, `GitAdapter.checkoutBranch(featureBranch, os.pwd)`
- [ ] [impl] Implement advance: `ProcessAdapter.run` for `git fetch origin` + `git reset --hard origin/{branch}`
- [ ] [impl] Implement review-state update: status "phase_merged"
- [ ] [impl] Implement JSON output: `AdvanceOutput(...).toJson` to stdout
- [ ] [test] Run phase-advance E2E tests and verify all pass

## Integration

- [ ] [int] Run full unit test suite (`./iw test unit`) — all tests pass, no regressions
- [ ] [int] Run full test suite (`./iw test`) — all tests pass including new E2E tests
- [ ] [int] Verify all new files have PURPOSE comments (two lines starting with `// PURPOSE:`)
- [ ] [int] Verify all four commands are accessible via `iw phase-start`, `iw phase-commit`, `iw phase-pr`, `iw phase-advance`
- [ ] [int] Verify JSON output is valid JSON (parseable by `jq`)
- [ ] [int] Verify error messages go to stderr (not stdout)

**Phase Status:** Not Started
