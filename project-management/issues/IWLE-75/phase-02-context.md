# Phase 2: Apply switch pattern to open.scala

**Issue:** IWLE-75
**Phase:** 2 of 2
**Estimated:** 2-3 hours

## Goals

Apply the same tmux session switching pattern from Phase 1 to `open.scala`, replacing the current "please detach" error with automatic session switching for consistent UX across both commands.

**Value delivered:** Developers can use `iw open` seamlessly when already inside a tmux session, matching the behavior of `iw start`.

## Scope

### In Scope
- Modify `open.scala` to use `TmuxAdapter.switchSession` when inside tmux
- Handle the "already in target session" case (keep existing behavior - just report it)
- Error handling with fallback manual command message
- Update both attach paths (existing session and newly created session)

### Out of Scope
- Changes to `TmuxAdapter` (already done in Phase 1)
- Changes to `start.scala` (already done in Phase 1)
- New unit tests for `switchSession` (already done in Phase 1)

## Dependencies

### From Phase 1 (Available)
- `TmuxAdapter.switchSession(name: String): Either[String, Unit]` - uses `tmux switch-client -t <session>`
- Pattern: Check `isInsideTmux`, then switch or attach accordingly

### Existing Infrastructure
- `TmuxAdapter.isInsideTmux: Boolean` - detects TMUX env var
- `TmuxAdapter.currentSessionName: Option[String]` - gets current session name
- `TmuxAdapter.attachSession(name: String): Either[String, Unit]` - existing attach logic

## Technical Approach

### Current Code (lines 45-58 - nested tmux handling)

```scala
// Handle nested tmux scenario
if TmuxAdapter.isInsideTmux then
  TmuxAdapter.currentSessionName match
    case Some(current) if current == sessionName =>
      Output.info(s"Already in session '$sessionName'")
      sys.exit(0)
    case Some(current) =>
      Output.error(s"Already inside tmux session '$current'")
      Output.info("Detach first with: Ctrl+B, D")
      Output.info(s"Then run: ./iw open ${issueId.value}")
      sys.exit(1)
    case None =>
      Output.error("Inside tmux but cannot determine session name")
      sys.exit(1)
```

### New Logic for nested tmux handling

```scala
// Handle nested tmux scenario - switch instead of error
if TmuxAdapter.isInsideTmux then
  TmuxAdapter.currentSessionName match
    case Some(current) if current == sessionName =>
      Output.info(s"Already in session '$sessionName'")
      sys.exit(0)
    case _ =>
      // Inside tmux but in different session - switch to target
      if TmuxAdapter.sessionExists(sessionName) then
        Output.info(s"Switching to session '$sessionName'...")
        TmuxAdapter.switchSession(sessionName) match
          case Left(error) =>
            Output.error(error)
            Output.info(s"Switch manually with: tmux switch-client -t $sessionName")
            sys.exit(1)
          case Right(_) =>
            () // Successfully switched
      else
        // Session doesn't exist, create it then switch
        Output.info(s"Creating session '$sessionName' for existing worktree...")
        TmuxAdapter.createSession(sessionName, targetPath) match
          case Left(error) =>
            Output.error(s"Failed to create session: $error")
            sys.exit(1)
          case Right(_) =>
            Output.success("Session created")
            Output.info(s"Switching to session '$sessionName'...")
            TmuxAdapter.switchSession(sessionName) match
              case Left(error) =>
                Output.error(error)
                Output.info(s"Switch manually with: tmux switch-client -t $sessionName")
                sys.exit(1)
              case Right(_) =>
                ()
else
  // Not inside tmux - use existing attach logic (lines 60-83)
  ...
```

### Key Changes

1. **Lines 51-58**: Replace "detach first" error with automatic switch
2. **Lines 61-68**: When session exists outside tmux - keep attach
3. **Lines 70-83**: When creating session outside tmux - keep attach
4. **Add**: Inside tmux paths for both existing and new sessions using switch

## Files to Modify

| File | Change |
|------|--------|
| `.iw/commands/open.scala` | Replace nested tmux error handling with switch logic |

## Testing Strategy

### No New Unit Tests Needed

- `TmuxAdapter.switchSession` is already tested from Phase 1
- The conditional logic in `open.scala` mirrors `start.scala` which is working

### Manual Verification

After implementation, manually test:
1. Start a tmux session: `tmux new -s test`
2. Run `iw open ISSUE-123` inside tmux (where session exists)
3. Verify it switches to the target session
4. Run `iw open ISSUE-456` inside tmux (where session needs creation)
5. Verify it creates session and switches
6. Run `iw open ISSUE-789` outside tmux
7. Verify it attaches normally (existing behavior preserved)

## Acceptance Criteria

- [ ] When inside tmux and target session exists: switches to it
- [ ] When inside tmux and target session doesn't exist: creates and switches
- [ ] When inside tmux and already in target session: reports "Already in session" (unchanged)
- [ ] When outside tmux: attaches normally (existing behavior preserved)
- [ ] On switch failure: shows manual `tmux switch-client -t <session>` command
- [ ] All existing tests continue to pass

## Error Handling

Per resolved decision (Option A - Leave orphaned session):
- If switch fails, the session remains running
- User sees clear error message with manual recovery command
- Consistent with Phase 1 error handling in `start.scala`

## Notes

- The `currentSessionName` check for "already in target session" stays the same
- Simplify the None case - just try to switch, it will fail gracefully if something is wrong
- Follow exact pattern from Phase 1 for consistency
- This completes the IWLE-75 fix for both commands
