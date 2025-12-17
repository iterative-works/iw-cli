# Phase 6 Context: Remove worktree and cleanup resources

**Issue:** IWLE-72
**Phase:** 6 of 7
**Status:** Ready for Implementation

---

## 1. Goals

Implement the `./iw rm <issue-id>` command that cleanly removes a worktree and its associated tmux session, with safety checks to prevent data loss.

**Primary objectives:**
- Kill the tmux session associated with the issue
- Remove the git worktree directory
- Warn about uncommitted changes before deletion
- Prevent removal of the currently active session
- Support `--force` flag to bypass confirmations

**Non-goals:**
- Do NOT delete git branches (branch lifecycle tied to PR/MR process)
- Do NOT attempt to push or sync changes before removal

---

## 2. Scope

### In Scope

1. **Command implementation:** `./iw rm <issue-id>` with full workflow
2. **Safety checks:**
   - Detect uncommitted changes in worktree
   - Detect if user is attached to the target session
   - Require confirmation for uncommitted changes (unless `--force`)
3. **Cleanup operations:**
   - Kill tmux session (if exists)
   - Remove git worktree (git worktree remove)
4. **Edge cases:**
   - Session exists but worktree doesn't
   - Worktree exists but session doesn't
   - Neither exists (already cleaned up)
   - Directory exists but is not a git worktree

### Out of Scope

- Branch deletion (explicitly excluded per design decision #6)
- Interactive selection of worktrees to remove
- Batch removal of multiple worktrees
- Automatic backup before removal

---

## 3. Dependencies

### From Previous Phases

**Phase 1:**
- `Output` utilities for formatted console output
- Bootstrap script infrastructure

**Phase 4:**
- `IssueId` value object for issue ID validation and parsing
- `WorktreePath` for calculating worktree paths and session names
- `TmuxAdapter` for session management (killSession, sessionExists)
- `GitWorktreeAdapter` for worktree operations (worktreeExists)
- `ProcessAdapter.run()` for executing shell commands

**Phase 2:**
- `ConfigFileRepository` for reading project configuration

### External Dependencies

- Git (for `git status` and `git worktree remove`)
- Tmux (for session management)

---

## 4. Technical Approach

### Domain Layer Extensions

**New value object: `DeletionSafety`**
```scala
case class DeletionSafety(
  hasUncommittedChanges: Boolean,
  isActiveSession: Boolean
)

object DeletionSafety:
  def isSafe(safety: DeletionSafety): Boolean =
    !safety.hasUncommittedChanges && !safety.isActiveSession
```

### Infrastructure Layer Extensions

**GitAdapter extensions:**
```scala
def hasUncommittedChanges(path: Path): Either[String, Boolean]
  // Run: git -C <path> status --porcelain
  // Return: true if output is non-empty
```

**TmuxAdapter extensions:**
```scala
def isCurrentSession(sessionName: String): Either[String, Boolean]
  // Check if TMUX env var indicates we're in this session
  // Or use: tmux display-message -p '#S' to get current session name
```

### Command Workflow

```
1. Parse and validate issue ID
2. Load configuration (for project name)
3. Calculate WorktreePath for issue ID
4. Check if worktree exists
   - If not: report "Worktree not found" and exit
5. Check if session is current session
   - If yes: report error "Cannot remove - you are in this session"
6. Check for uncommitted changes
   - If has changes and not --force: prompt for confirmation
   - If user declines: abort
7. Kill tmux session (if exists)
8. Remove git worktree
9. Report success
```

### Error Handling

| Scenario | Response |
|----------|----------|
| Invalid issue ID format | Error with format hint |
| Worktree not found | Error with suggestion to check ID |
| Currently in target session | Error with "detach first" hint |
| Uncommitted changes (no --force) | Warning + confirmation prompt |
| Session kill fails | Warning, continue with worktree removal |
| Worktree removal fails | Error with git output |

---

## 5. Files to Modify

### New Files

| File | Purpose |
|------|---------|
| `.iw/core/DeletionSafety.scala` | DeletionSafety value object |
| `.iw/core/test/DeletionSafetyTest.scala` | Unit tests |
| `.iw/test/rm.bats` | E2E tests |

### Modified Files

| File | Changes |
|------|---------|
| `.iw/commands/rm.scala` | Full implementation (currently stub) |
| `.iw/core/Git.scala` | Add `hasUncommittedChanges(path)` method |
| `.iw/core/Tmux.scala` | Add `isCurrentSession(name)` method |

### Existing Files to Reuse

- `.iw/core/IssueId.scala` - Issue ID parsing
- `.iw/core/WorktreePath.scala` - Path and session name calculation
- `.iw/core/GitWorktree.scala` - Worktree removal
- `.iw/core/Output.scala` - Console output
- `.iw/core/ConfigRepository.scala` - Configuration loading
- `.iw/core/Prompt.scala` - Confirmation dialogs

---

## 6. Testing Strategy

### Unit Tests (DeletionSafetyTest.scala)

1. `DeletionSafety.isSafe` returns true when no issues
2. `DeletionSafety.isSafe` returns false with uncommitted changes
3. `DeletionSafety.isSafe` returns false when in active session
4. `DeletionSafety.isSafe` returns false when both conditions

### Integration Tests

**GitAdapter tests (in GitTest.scala):**
1. `hasUncommittedChanges` returns false for clean worktree
2. `hasUncommittedChanges` returns true for modified files
3. `hasUncommittedChanges` returns true for untracked files
4. `hasUncommittedChanges` returns error for non-git directory

**TmuxAdapter tests (in TmuxAdapterTest.scala):**
1. `isCurrentSession` returns true when in matching session
2. `isCurrentSession` returns false when in different session
3. `isCurrentSession` returns false when not in tmux

### E2E Tests (rm.bats)

1. Successfully remove worktree with session - both cleaned up
2. Successfully remove worktree without session - only worktree removed
3. Remove worktree with --force bypasses confirmation
4. Error when removing active session - clear message
5. Confirmation prompt for uncommitted changes
6. Error for non-existent worktree
7. Error for invalid issue ID format
8. Handles partial state (session exists, worktree doesn't)
9. Missing configuration file error

---

## 7. Acceptance Criteria

From analysis.md Story 6:

- [x] Kills tmux session and removes worktree directory
- [x] Does NOT delete the git branch (branch cleanup is part of PR/MR process)
- [x] Protects against removing active session (user is attached)
- [x] Warns about uncommitted changes and requires confirmation
- [x] Handles partial cleanup (e.g., session already dead)
- [x] Provides `--force` flag to bypass confirmations

### Scenario Verification

```gherkin
Scenario: Remove worktree with tmux session
  Given worktree "kanon-IWLE-123" exists
  And tmux session "kanon-IWLE-123" is running
  And I am not attached to that session
  When I run "./iw rm IWLE-123"
  Then I see "Killing tmux session kanon-IWLE-123..."
  And I see "Removing worktree kanon-IWLE-123..."
  And tmux session "kanon-IWLE-123" does not exist
  And the directory "../kanon-IWLE-123" does not exist
  And git branch "IWLE-123" still exists (not deleted)

Scenario: Protect against removing active session
  Given worktree "kanon-IWLE-123" exists
  And I am attached to tmux session "kanon-IWLE-123"
  When I run "./iw rm IWLE-123"
  Then I see the error "Cannot remove worktree - you are in its tmux session"
  And I see the hint "Detach from session first (Ctrl+B, D)"
  And the worktree still exists

Scenario: Uncommitted changes require confirmation
  Given worktree "kanon-IWLE-123" exists
  And the worktree has uncommitted changes
  When I run "./iw rm IWLE-123"
  Then I see the warning "Worktree has uncommitted changes"
  And I see the prompt "Continue? (y/N)"
  And the command waits for confirmation before deleting
```

---

## 8. Implementation Notes

### Git Worktree Removal

Use `git worktree remove` command:
```bash
# Standard removal (fails if uncommitted changes)
git worktree remove <path>

# Force removal (ignores uncommitted changes)
git worktree remove --force <path>
```

### Session Detection

To detect if we're in the target session:
```bash
# Get current session name (only works inside tmux)
tmux display-message -p '#S'
```

Or check the `TMUX` environment variable which contains the socket path and session info.

### Uncommitted Changes Detection

```bash
# Returns empty if clean, otherwise shows changed files
git -C <worktree-path> status --porcelain
```

### Cleanup Order

1. Kill session first (session references the worktree directory)
2. Then remove worktree (now no processes are using it)

---

## 9. Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| User loses uncommitted work | Require confirmation, show warning with file list |
| Session kill fails (zombie) | Continue with worktree removal, warn user |
| Worktree removal fails | Show git error output, suggest manual cleanup |
| Race condition (user opens session during check) | Acceptable risk - user initiated removal |

---

## 10. Related Documentation

- Analysis: `analysis.md` → Story 6
- Phase 4 implementation: `phase-04-context.md` (reusable adapters)
- Phase 5 implementation: `phase-05-context.md` (session detection patterns)
