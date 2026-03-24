# Phase 1 Tasks: `analyze` command — triage shortcut

## Setup

- [x] [setup] Read existing command patterns (`start.scala`, `start-prompt.bats`) for reference

## Tests

- [x] [test] Write `analyze.bats` — error when no issue ID provided (exit 1, usage message)
- [x] [test] Write `analyze.bats` — delegates to `iw start` with `--prompt "/iterative-works:triage-issue"` (worktree created, session exists)
- [x] [test] Write `analyze.bats` — claude command contains the triage prompt (capture pane content)

## Implementation

- [x] [impl] Create `.iw/commands/analyze.scala` — parse args, delegate to `iw start --prompt`
- [x] [impl] Verify all new tests pass
- [x] [impl] Verify existing `start-prompt.bats` tests still pass (regression)
