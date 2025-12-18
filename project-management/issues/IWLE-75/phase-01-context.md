# Phase 1: Core switch functionality for start.scala

**Issue:** IWLE-75
**Phase:** 1 of 2
**Estimated:** 3-4 hours

## Goals

This phase delivers the core fix for the reported bug: when `iw start` is run inside an existing tmux session, it should automatically switch to the new session instead of failing when trying to attach.

**Value delivered:** Developers working in tmux can seamlessly start new worktree sessions without manual intervention.

## Scope

### In Scope
- Add `TmuxAdapter.switchSession(name: String): Either[String, Unit]` method
- Modify `start.scala` to detect if running inside tmux (`TmuxAdapter.isInsideTmux`)
- Use `tmux switch-client -t <session>` when inside tmux
- Use existing `tmux attach-session -t <session>` when outside tmux
- Error handling with fallback manual command message
- Unit tests for `switchSession` method
- Unit tests verifying conditional logic in start.scala

### Out of Scope
- Changes to `open.scala` (Phase 2)
- E2E tests with real tmux sessions (using env var mocking per decision)
- Changes to other commands

## Dependencies

### From Previous Phases
None - this is Phase 1.

### Existing Infrastructure (Already Available)
- `TmuxAdapter.isInsideTmux: Boolean` - detects TMUX env var
- `TmuxAdapter.attachSession(name: String): Either[String, Unit]` - existing attach logic
- `TmuxAdapter.createSession(name: String, workDir: os.Path): Either[String, Unit]`
- `Constants.EnvVars.Tmux` - the "TMUX" constant
- Test infrastructure in `.iw/core/test/TmuxAdapterTest.scala`

## Technical Approach

### 1. Add switchSession to TmuxAdapter

Add new method to `.iw/core/Tmux.scala`:

```scala
/** Switch to an existing tmux session (when already inside tmux) */
def switchSession(name: String): Either[String, Unit] =
  val result = ProcessAdapter.run(Seq("tmux", "switch-client", "-t", name))
  if result.exitCode == 0 then Right(())
  else Left(s"Failed to switch to session: ${result.stderr}")
```

Pattern follows existing `attachSession` method exactly.

### 2. Modify start.scala session join logic

Current code (lines 80-88):
```scala
// Attach to session
Output.info(s"Attaching to session...")
TmuxAdapter.attachSession(sessionName) match
  case Left(error) =>
    Output.error(error)
    Output.info(s"Session created. Attach manually with: tmux attach -t $sessionName")
    sys.exit(1)
  case Right(_) =>
    () // Successfully attached and detached
```

New logic:
```scala
// Join session (switch if inside tmux, attach if outside)
if TmuxAdapter.isInsideTmux then
  Output.info(s"Switching to session '$sessionName'...")
  TmuxAdapter.switchSession(sessionName) match
    case Left(error) =>
      Output.error(error)
      Output.info(s"Session created. Switch manually with: tmux switch-client -t $sessionName")
      sys.exit(1)
    case Right(_) =>
      () // Successfully switched
else
  Output.info(s"Attaching to session...")
  TmuxAdapter.attachSession(sessionName) match
    case Left(error) =>
      Output.error(error)
      Output.info(s"Session created. Attach manually with: tmux attach -t $sessionName")
      sys.exit(1)
    case Right(_) =>
      () // Successfully attached and detached
```

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/Tmux.scala` | Add `switchSession` method |
| `.iw/commands/start.scala` | Add tmux detection + conditional switch/attach |
| `.iw/core/test/TmuxAdapterTest.scala` | Add tests for `switchSession` |

## Testing Strategy

### Unit Tests (TmuxAdapterTest.scala)

1. **Test switchSession returns Right when command succeeds**
   - Create a test session
   - Call switchSession (will fail outside tmux - expected)
   - Verify Left is returned with appropriate error message

2. **Test switchSession returns Left when session doesn't exist**
   - Call switchSession with non-existent session name
   - Verify Left is returned

3. **Test switchSession returns Left when not in tmux**
   - Without TMUX env var set
   - Call switchSession
   - Verify Left is returned (cannot switch when not in tmux)

### Integration Testing Notes

Per resolved decision, we use **mocked TMUX env var** for testing:
- Unit tests verify the conditional logic paths
- We don't need real tmux switching in CI
- Manual testing confirms actual tmux behavior

### Manual Verification

After implementation, manually test:
1. Start a tmux session: `tmux new -s test`
2. Run `iw start ISSUE-123` inside tmux
3. Verify it switches to the new session
4. Run `iw start ISSUE-456` outside tmux
5. Verify it attaches normally

## Acceptance Criteria

- [ ] `TmuxAdapter.switchSession` method exists and follows existing patterns
- [ ] `start.scala` detects tmux environment before joining session
- [ ] When inside tmux: uses `switch-client` command
- [ ] When outside tmux: uses `attach-session` command (existing behavior)
- [ ] On switch failure: shows manual `tmux switch-client -t <session>` command
- [ ] On attach failure: shows manual `tmux attach -t <session>` command (existing)
- [ ] Unit tests pass for new `switchSession` method
- [ ] All existing tests continue to pass

## Error Handling

Per resolved decision (Option A - Leave orphaned session):
- If switch/attach fails, the session remains running
- User sees clear error message with manual recovery command
- No cleanup of the newly created session
- This is consistent with existing attach failure behavior

## Notes

- The `isInsideTmux` check already exists and is well-tested
- Follow the exact pattern of `attachSession` for `switchSession`
- Error messages should be actionable (include the exact command to run)
- Phase 2 will apply the same pattern to `open.scala`
