# Phase 4 Tasks: --prompt support for start/open

## Setup

- [ ] Read existing `start.scala` and `open.scala` to understand current arg parsing
- [ ] Read `TmuxAdapter.sendKeys` signature and tests from Phase 2

## Tests (write first — TDD)

- [ ] Create `start-prompt.bats` with E2E tests:
  - `iw start --prompt "text" <issue-id>` creates worktree/session and sends keys without attaching
  - `iw start --prompt` without text fails with error
  - `iw start <issue-id>` (no --prompt) still works (regression test)
  - Verify pane content contains the claude command
- [ ] Create `open-prompt.bats` with E2E tests:
  - `iw open --prompt "text" <issue-id>` with existing worktree sends keys without attaching
  - `iw open --prompt "text"` (infer from branch) sends keys without attaching
  - `iw open --prompt` without text fails with error
  - `iw open <issue-id>` (no --prompt) still works (regression test)
  - Verify pane content contains the claude command

## Implementation

- [ ] Update `start.scala`:
  - Extract `--prompt <text>` from args before processing
  - After session creation, if prompt present: call `sendKeys` with claude command, print success, exit
  - If no prompt: existing attach/switch behavior unchanged
- [ ] Update `open.scala`:
  - Extract `--prompt <text>` from args before processing
  - After session ready, if prompt present: call `sendKeys` with claude command, print success, exit
  - If no prompt: existing attach/switch behavior unchanged

## Verification

- [ ] Run `./iw test e2e` — all existing + new tests pass
- [ ] Run `./iw test unit` — all unit tests pass
- [ ] Manual smoke test: `iw start --prompt "hello" TEST-1` in a real tmux session
