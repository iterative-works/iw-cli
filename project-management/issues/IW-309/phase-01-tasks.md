# Phase 1 Tasks: `analyze` command — triage shortcut

## Setup

- [ ] [setup] Read existing command patterns (`start.scala`, `start-prompt.bats`) for reference

## Tests

- [ ] [test] Write `analyze.bats` — error when no issue ID provided (exit 1, usage message)
- [ ] [test] Write `analyze.bats` — delegates to `iw start` with `--prompt "/iterative-works:triage-issue"` (worktree created, session exists)
- [ ] [test] Write `analyze.bats` — claude command contains the triage prompt (capture pane content)

## Implementation

- [ ] [impl] Create `.iw/commands/analyze.scala` — parse args, delegate to `iw start --prompt`
- [ ] [impl] Verify all new tests pass
- [ ] [impl] Verify existing `start-prompt.bats` tests still pass (regression)
