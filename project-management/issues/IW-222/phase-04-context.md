# Phase 4: Presentation Layer — --prompt support for start/open

## Goals

Add `--prompt <text>` flag support to the `iw start` and `iw open` commands. When `--prompt` is provided, the command creates/opens the tmux session as usual but instead of attaching or switching to it, sends a claude agent launch command into the session via `TmuxAdapter.sendKeys`. This enables agents to spawn worktrees with a running Claude instance without blocking on an interactive tmux attach.

## Scope

### In Scope

1. **Updated `commands/start.scala`** — Parse `--prompt <text>` flag; when present, skip attach/switch and instead send keys to launch the agent
2. **Updated `commands/open.scala`** — Parse `--prompt <text>` flag; same behavior modification
3. **E2E tests** for `--prompt` behavior in both commands

### Out of Scope

- Changes to `TmuxAdapter.sendKeys` — already implemented in Phase 2
- Changes to model types or other adapters
- Any new formatter or output changes
- Dashboard server integration for `--prompt`

## Dependencies on Prior Phases

### From Phase 2 (Infrastructure Layer)

- **`TmuxAdapter.sendKeys(sessionName: String, keys: String): Either[String, Unit]`** — Sends keystrokes to a tmux session pane and presses Enter. Used to type the claude command into the session.

### From Existing Code

- `start.scala` creates worktree + tmux session, then attaches/switches. Phase 4 adds a branch: if `--prompt`, send keys instead of attach/switch.
- `open.scala` opens an existing worktree session (creating if needed), then attaches/switches. Phase 4 adds same branch.

## Approach

### Behavior Change

When `--prompt <text>` is provided:

1. **`start`**: Create worktree and tmux session as normal, register with dashboard as normal. Then instead of `switchSession`/`attachSession`, call `TmuxAdapter.sendKeys(sessionName, s"""claude --dangerously-skip-permissions --prompt "$text"""")`. Print a success message with the session name (so the caller knows where to find it).

2. **`open`**: Resolve issue ID, find/create session as normal, update dashboard as normal. Then instead of `switchSession`/`attachSession`, call `TmuxAdapter.sendKeys(sessionName, s"""claude --dangerously-skip-permissions --prompt "$text"""")`. Print a success message.

### Argument Parsing

Both commands need to extract `--prompt <text>` from their args before processing the remaining positional arguments.

Pattern: scan `args` for `--prompt` followed by the next arg. Remove both from the args list. Pass remaining args to existing logic.

For `start`, the remaining arg is the required issue ID.
For `open`, the remaining arg is the optional issue ID.

### Edge Cases

- `--prompt` without a following argument: error with usage hint
- `--prompt ""` (empty string): allow it (sends just `claude --dangerously-skip-permissions --prompt ""`)
- Session creation fails before sendKeys: existing error handling covers this
- `sendKeys` fails: report error but session was already created, so suggest manual intervention

## Component Specifications

### Updated `commands/start.scala`

**Changes:**
1. Parse `--prompt <text>` from `args`
2. After session creation succeeds, check if prompt was provided
3. If prompt: call `sendKeys` with the claude command, print success, exit
4. If no prompt: existing attach/switch behavior (unchanged)

### Updated `commands/open.scala`

**Changes:**
1. Parse `--prompt <text>` from `args`
2. After session is ready (found existing or created new), check if prompt was provided
3. If prompt: call `sendKeys` with the claude command, print success, exit
4. If no prompt: existing attach/switch behavior (unchanged)

### Command Construction

The command sent via `sendKeys` is:

```
claude --dangerously-skip-permissions --prompt "<text>"
```

Where `<text>` is the user-provided prompt string. Double quotes in `<text>` need to be escaped for the shell command.

## Files to Modify

| File | Description |
|------|-------------|
| `.iw/commands/start.scala` | Add `--prompt` flag parsing and sendKeys branch |
| `.iw/commands/open.scala` | Add `--prompt` flag parsing and sendKeys branch |

## Files to Create

| File | Description |
|------|-------------|
| `.iw/test/start-prompt.bats` | E2E tests for `iw start --prompt` |
| `.iw/test/open-prompt.bats` | E2E tests for `iw open --prompt` |

## Testing Strategy

### E2E Tests

**`start-prompt.bats`** — Tests for `iw start --prompt`:

- `iw start --prompt "do something" IWLE-123` creates worktree, creates session, sends keys, does NOT attach
- `iw start --prompt "do something" IWLE-123` prints success message with session name
- `iw start --prompt` (missing prompt text) fails with error
- `iw start IWLE-123` (no --prompt) still works as before (regression)
- Verify session receives the expected keystrokes (check tmux pane content)

**`open-prompt.bats`** — Tests for `iw open --prompt`:

- `iw open --prompt "do something" IWLE-123` with existing worktree sends keys to session
- `iw open --prompt "do something"` (no issue ID, infer from branch) works with sendKeys
- `iw open --prompt` (missing prompt text) fails with error
- `iw open IWLE-123` (no --prompt) still works as before (regression)
- Verify session receives the expected keystrokes

**Test Verification of sendKeys:**
Use `tmux capture-pane -p -t <session>` to capture pane content and verify the claude command was typed.

### Unit Tests

No new unit tests needed — the behavior change is in the command scripts (integration level), and `TmuxAdapter.sendKeys` is already unit-tested from Phase 2.

## Acceptance Criteria

- [ ] `iw start --prompt "text" <issue-id>` creates worktree and session, sends claude command, does not attach
- [ ] `iw open --prompt "text" <issue-id>` opens session, sends claude command, does not attach
- [ ] `iw open --prompt "text"` (infer issue from branch) works
- [ ] `--prompt` without following text shows error
- [ ] Existing `start` behavior (no `--prompt`) is unchanged
- [ ] Existing `open` behavior (no `--prompt`) is unchanged
- [ ] E2E tests pass for both commands with `--prompt`
- [ ] All existing E2E tests still pass (`./iw test e2e`)
- [ ] All existing unit tests still pass (`./iw test unit`)
