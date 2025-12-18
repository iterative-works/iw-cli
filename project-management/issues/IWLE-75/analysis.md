# Story-Driven Analysis: iw start fails when running inside tmux - should switch session instead of attach

**Issue:** IWLE-75
**Created:** 2025-12-18
**Status:** Draft
**Classification:** Simple

## Problem Statement

When a developer runs `iw start` from within an existing tmux session, the command successfully creates a new tmux session but then fails when trying to attach to it. This happens because tmux doesn't allow attaching to a session from within tmux - you must use `switch-client` instead.

**User Impact:** Developers working in tmux (a common workflow for terminal-heavy development) cannot seamlessly start new worktree sessions. They must manually run the switch command after `iw start` fails, breaking their flow and creating unnecessary friction.

**Current Behavior:** Command fails at the end with an error when attempting `tmux attach`.

**Desired Behavior:** Automatically detect tmux environment and switch to the new session instead of trying to attach.

## User Stories

### Story 1: Successful session switch when inside tmux

```gherkin
Feature: Automatic tmux session switching
  As a developer working in tmux
  I want iw start to automatically switch to the new session
  So that I don't have to manually run switch commands

Scenario: Starting new worktree session from within tmux
  Given I am inside an active tmux session "old-session"
  And I run "iw start ISSUE-123"
  When the new tmux session "ISSUE-123" is created
  Then iw start automatically switches to session "ISSUE-123"
  And I see the new session's shell prompt
  And the session "old-session" remains available in the background
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward because:
- TmuxAdapter already has `isInsideTmux` detection
- We just need to add `switchSession` method alongside existing `attachSession`
- start.scala needs conditional logic based on tmux detection
- Pattern already exists in open.scala for tmux detection

**Key Technical Work:**
- Add `TmuxAdapter.switchSession(name: String)` method
- Modify start.scala to check `isInsideTmux` before attach/switch
- Use `tmux switch-client -t <session>` command

**Acceptance:**
- When running `iw start` inside tmux, session switches without error
- User ends up in the new session's shell
- No manual intervention needed

---

### Story 2: Graceful handling when outside tmux

```gherkin
Feature: Normal attach behavior outside tmux
  As a developer working in a regular terminal
  I want iw start to attach to sessions normally
  So that existing behavior is preserved

Scenario: Starting new worktree session from regular terminal
  Given I am NOT inside a tmux session
  And I run "iw start ISSUE-456"
  When the new tmux session "ISSUE-456" is created
  Then iw start attaches to session "ISSUE-456" using tmux attach
  And I see the new session's shell prompt
```

**Estimated Effort:** 1h
**Complexity:** Straightforward

**Technical Feasibility:**
This is trivial because:
- Existing behavior is already working
- Just need to preserve current code path when NOT inside tmux
- Test the conditional logic flows correctly

**Acceptance:**
- When running `iw start` outside tmux, behavior unchanged from current
- Session attaches normally
- No regressions in non-tmux workflow

---

### Story 3: Clear error message if switch fails

```gherkin
Feature: Helpful fallback when automatic switch fails
  As a developer
  I want clear instructions if automatic switching fails
  So that I can manually recover

Scenario: Switch command fails for unexpected reason
  Given I am inside a tmux session "old-session"
  And I run "iw start ISSUE-789"
  When the new session "ISSUE-789" is created
  But tmux switch-client command fails
  Then I see an error message "Could not switch to session ISSUE-789"
  And I see instructions "Run manually: tmux switch-client -t ISSUE-789"
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- Same pattern as existing fallback in start.scala (line 86-88)
- Just update the fallback message for switch scenario
- TmuxAdapter methods already return Either[String, Unit] for error handling

**Acceptance:**
- If switch fails, user gets actionable error message
- Message includes exact command to run manually
- User isn't left hanging without knowing what to do

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Successful session switch when inside tmux

**Infrastructure Layer:**
- `TmuxAdapter.switchSession(name: String): Either[String, Unit]` - new method to switch sessions
- `TmuxAdapter.isInsideTmux: Boolean` - existing method, already implemented

**Application Layer:**
- start.scala - conditional logic to choose attach vs. switch based on tmux detection
- Decision point: check `TmuxAdapter.isInsideTmux` before session join

---

### For Story 2: Graceful handling when outside tmux

**Application Layer:**
- start.scala - preserve existing `TmuxAdapter.attachSession` code path
- No new components - just ensure existing flow still works

---

### For Story 3: Clear error message if switch fails

**Application Layer:**
- start.scala - error handling for `TmuxAdapter.switchSession` failures
- User-facing error message with manual recovery command

**Pattern:**
Same error handling pattern as existing attach failure (lines 86-88 in start.scala)

## Technical Risks & Uncertainties

### RESOLVED: Behavior when switch-client fails in edge cases

**Decision:** Option A - Leave orphaned session

If switch fails, leave the new session running and show the manual switch command. User can manually switch or clean up.

**Rationale:** Simpler implementation, no destructive actions, user retains control over the session.

---

### RESOLVED: Should open.scala behavior change too?

**Decision:** Option B - Update both commands

Both `start.scala` and `open.scala` should switch sessions automatically when inside tmux, for consistent UX.

**Rationale:** Consistent behavior across commands is more important than minimizing scope. Users expect the same tmux handling in both commands.

---

### RESOLVED: Testing approach for tmux scenarios

**Decision:** Option A - Mock TMUX env var

Set TMUX environment variable in test scripts without actually running tmux.

**Rationale:** Faster and simpler test setup. The actual tmux commands (`switch-client`, `attach`) are already tested elsewhere; we just need to verify our conditional logic picks the right path based on environment detection.

## Total Estimates

**Story Breakdown:**
- Story 1 (Successful session switch when inside tmux): 2-3 hours
- Story 2 (Graceful handling when outside tmux): 1 hour
- Story 3 (Clear error message if switch fails): 1-2 hours

**Total Range:** 5-7 hours (includes open.scala update per resolved decision)

**Confidence:** High

**Reasoning:**
- Well-understood problem with clear technical solution
- Existing code patterns to follow (open.scala already has tmux detection)
- TmuxAdapter infrastructure already in place
- Scope includes both start.scala and open.scala for consistency
- All uncertainties resolved via CLARIFY decisions

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: TmuxAdapter.switchSession method behavior
2. **Integration Tests**: Verify correct tmux command is executed based on environment
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenarios (pending CLARIFY on testing approach)

**Story-Specific Testing Notes:**

**Story 1 - Session switch when inside tmux:**
- Unit: Test TmuxAdapter.switchSession returns success when command succeeds
- Unit: Test start.scala chooses switch path when isInsideTmux is true
- Integration: Verify `tmux switch-client -t <session>` is the executed command
- E2E: Run iw start inside tmux, verify switch occurs (pending CLARIFY)

**Story 2 - Normal attach outside tmux:**
- Unit: Test start.scala chooses attach path when isInsideTmux is false
- Integration: Verify `tmux attach -t <session>` is still executed when not in tmux
- E2E: Run iw start outside tmux, verify attach occurs

**Story 3 - Error message if switch fails:**
- Unit: Test error message includes correct switch-client command
- Unit: Test fallback behavior when switchSession returns Left(error)
- Integration: Simulate switch-client failure, verify error message shown
- E2E: Force switch failure scenario, verify user sees helpful message

**Test Data Strategy:**
- Use temporary test sessions with unique names to avoid conflicts
- Clean up test sessions after each test run
- Mock TMUX environment variable for unit tests
- Consider real tmux for integration tests (pending CLARIFY)

**Regression Coverage:**
- Verify existing start.scala behavior outside tmux still works
- Ensure open.scala is not affected (unless we decide to update it per CLARIFY)
- Check that attach fallback message is still shown when appropriate

## Deployment Considerations

### Database Changes
None - this is a CLI tool behavior change only.

### Configuration Changes
None - detection is automatic via environment variable.

### Rollout Strategy
This is a local dev tool, so rollout is simple:
- Users pull updated code
- Next `iw start` run will use new behavior
- No migration or backward compatibility needed

### Rollback Plan
If issues arise, users can:
- Revert to previous commit
- Or manually run `tmux switch-client -t <session>` as workaround
- No persistent state to roll back

## Dependencies

### Prerequisites
- tmux must be installed (already a requirement for iw-cli)
- TMUX environment variable correctly set by tmux (standard tmux behavior)
- Existing TmuxAdapter infrastructure in place (already exists)

### Story Dependencies
- **Story 1** must be complete before Stories 2 and 3
- Stories 2 and 3 can be implemented in parallel after Story 1
- All stories depend on `TmuxAdapter.switchSession` being implemented

### External Blockers
None identified.

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Successful session switch when inside tmux** - Core functionality, establishes the switch mechanism and detection logic
2. **Story 2: Graceful handling when outside tmux** - Ensures no regression in existing workflow, validation of conditional logic
3. **Story 3: Clear error message if switch fails** - Polish and edge case handling, completes the user experience

**Iteration Plan:**

- **Iteration 1** (Story 1): Core switch functionality - delivers value to tmux users immediately
- **Iteration 2** (Stories 2-3): Regression prevention and error handling - ensures robustness

## Documentation Requirements

- [ ] Update start.scala header comments to document tmux detection behavior
- [ ] Add code comments explaining switch vs. attach decision logic
- [ ] Consider updating project README if tmux workflow is documented there
- [ ] Document manual recovery commands in error messages (built into Story 3)

---

**Analysis Status:** Ready for Implementation

**Resolved Decisions:**
- Orphaned session cleanup: Leave session, show manual command (Option A)
- Scope: Update both start.scala AND open.scala for consistency (Option B)
- Testing: Mock TMUX env var (Option A)

**Next Steps:**
1. Run `/ag-create-tasks IWLE-75` to map stories to implementation phases
2. Run `/ag-implement IWLE-75` for iterative story-by-story implementation
