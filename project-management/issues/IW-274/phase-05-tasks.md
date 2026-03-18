# Phase 5 Tasks: WorktreeSummary redesign, worktrees command, and formatter

## Setup
- [x] [setup] Read `WorktreeSummary.scala`, `worktrees.scala`, `WorktreesFormatter.scala`, and `WorktreesFormatterTest.scala` to understand current structure
- [x] [setup] Read `WorkflowProgress.scala`, `IssueData.scala`, `PullRequestData.scala`, `WorktreeRegistration.scala` to understand data source fields
- [x] [setup] Read `ReviewState.scala` to confirm `activity` and `workflowType` fields from Phase 2

## Tests (TDD - write first)

### Unit tests — WorktreesFormatter
- [x] [test] Update all 8 existing `WorktreesFormatterTest` tests to use new `WorktreeSummary` constructor (add new fields as `None`/defaults)
- [x] [test] Write test: `format worktree with activity working shows indicator` — assert `▶` in output
- [x] [test] Write test: `format worktree with activity waiting shows indicator` — assert `⏸` in output
- [x] [test] Write test: `format worktree with workflow type shows abbreviation` — assert `AG`/`WF`/`DX` for each type
- [x] [test] Write test: `format worktree with phase progress shows progress` — assert `Phase 2/4` in output
- [x] [test] Write test: `format worktree with task progress shows progress` — assert `5/12 tasks` in output
- [x] [test] Write test: `format worktree with all new fields combines them` — assert activity + type + progress all present
- [x] [test] Write test: `format worktree with no new fields shows original format` — verify graceful degradation

### E2E tests — worktrees --json
- [x] [test] Write BATS test: `worktrees --json includes workflowDisplay field (renamed from reviewDisplay)` — create state with display, run `./iw worktrees --json`, assert `workflowDisplay` key in output
- [x] [test] Write BATS test: `worktrees --json includes activity and workflowType fields` — write review-state with `--activity working --workflow-type agile`, run `./iw worktrees --json`, assert both fields present
- [x] [test] Write BATS test: `worktrees --json includes progress fields` — create state with progress cache, run `./iw worktrees --json`, assert `currentPhase`, `totalPhases`, `completedTasks`, `totalTasks` in output
- [x] [test] Write BATS test: `worktrees --json includes URL and timestamp fields` — assert `issueUrl`, `prUrl`, `registeredAt`, `lastActivityAt` keys in output

### Run tests to confirm failures
- [x] [verify] Run unit tests — new tests must fail, existing tests may fail due to constructor change
- [x] [verify] Run E2E tests — new BATS tests must fail

## Implementation

### Model change
- [x] [impl] Update `WorktreeSummary.scala` — add `issueUrl`, `prUrl`, `activity`, `workflowType`, `workflowDisplay` (renamed from `reviewDisplay`), `currentPhase`, `totalPhases`, `completedTasks`, `totalTasks`, `registeredAt`, `lastActivityAt`

### Command change
- [x] [impl] Update `worktrees.scala` — populate `activity` from `reviewStateCache.get(issueId).flatMap(_.state.activity)`
- [x] [impl] Update `worktrees.scala` — populate `workflowType` from `reviewStateCache.get(issueId).flatMap(_.state.workflowType)`
- [x] [impl] Update `worktrees.scala` — rename `reviewDisplay` extraction to `workflowDisplay`
- [x] [impl] Update `worktrees.scala` — populate `issueUrl` from `issueCache.get(issueId).map(_.data.url)`
- [x] [impl] Update `worktrees.scala` — populate `prUrl` from `prCache.get(issueId).map(_.pr.url)`
- [x] [impl] Update `worktrees.scala` — populate `currentPhase`, `totalPhases`, `completedTasks`, `totalTasks` from `progressCache`
- [x] [impl] Update `worktrees.scala` — populate `registeredAt` and `lastActivityAt` from `WorktreeRegistration` timestamps

### Formatter change
- [x] [impl] Update `WorktreesFormatter.scala` — add activity indicator (`▶`/`⏸`)
- [x] [impl] Update `WorktreesFormatter.scala` — add workflow type abbreviation (AG/WF/DX)
- [x] [impl] Update `WorktreesFormatter.scala` — add progress display (phase and task counts)
- [x] [impl] Update `WorktreesFormatter.scala` — rename `reviewDisplay` references to `workflowDisplay`

## Verification
- [x] [verify] Run `./iw test unit` — all tests pass (existing + new), no regressions
- [x] [verify] Run `./iw test e2e` — all tests pass (existing + new), no regressions
- [x] [verify] Run `scala-cli compile --scalac-option -Werror .iw/core/` — no compilation warnings
- [x] [verify] Commit all changes with descriptive message referencing IW-274
