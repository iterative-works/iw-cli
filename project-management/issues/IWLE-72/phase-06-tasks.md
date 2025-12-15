# Phase 6 Tasks: Remove worktree and cleanup resources

**Issue:** IWLE-72
**Phase:** 6 - Remove worktree and cleanup resources
**Status:** In Progress

---

## Setup

- [x] [test] [ ] [reviewed] Read existing test patterns (open.bats, start.bats) for consistency
- [x] [impl] [ ] [reviewed] Set up rm.bats test file with setup/teardown scaffolding

## Unit Tests - DeletionSafety

- [x] [test] [ ] [reviewed] Write test: DeletionSafety.isSafe returns true when no issues (clean, not active)
- [x] [test] [ ] [reviewed] Write test: DeletionSafety.isSafe returns false with uncommitted changes
- [x] [test] [ ] [reviewed] Write test: DeletionSafety.isSafe returns false when in active session
- [x] [test] [ ] [reviewed] Write test: DeletionSafety.isSafe returns false when both conditions
- [x] [impl] [ ] [reviewed] Implement DeletionSafety value object in DeletionSafety.scala

## Integration Tests - GitAdapter Extensions

- [x] [test] [ ] [reviewed] Write test: hasUncommittedChanges returns false for clean worktree
- [x] [test] [ ] [reviewed] Write test: hasUncommittedChanges returns true for modified files
- [x] [test] [ ] [reviewed] Write test: hasUncommittedChanges returns true for untracked files
- [x] [impl] [ ] [reviewed] Implement hasUncommittedChanges method in Git.scala

## Integration Tests - TmuxAdapter Extensions

- [x] [test] [ ] [reviewed] Write test: isCurrentSession returns true when in matching session
- [x] [test] [ ] [reviewed] Write test: isCurrentSession returns false when in different session
- [x] [test] [ ] [reviewed] Write test: isCurrentSession returns false when not in tmux
- [x] [impl] [ ] [reviewed] Implement isCurrentSession method in Tmux.scala

## Integration Tests - GitWorktreeAdapter Extensions

- [x] [test] [ ] [reviewed] Write test: removeWorktree succeeds for clean worktree
- [x] [test] [ ] [reviewed] Write test: removeWorktree with force succeeds even with uncommitted changes
- [x] [impl] [ ] [reviewed] Implement removeWorktree method in GitWorktree.scala

## E2E Tests - rm.bats

- [x] [test] [ ] [reviewed] Write E2E test: successfully remove worktree with session
- [x] [test] [ ] [reviewed] Write E2E test: successfully remove worktree without session
- [x] [test] [ ] [reviewed] Write E2E test: --force bypasses confirmation for uncommitted changes
- [x] [test] [ ] [reviewed] Write E2E test: error when removing active session
- [x] [test] [ ] [reviewed] Write E2E test: error for non-existent worktree
- [x] [test] [ ] [reviewed] Write E2E test: error for invalid issue ID format
- [x] [test] [ ] [reviewed] Write E2E test: error without config file

## Implementation - rm.scala Command

- [x] [impl] [ ] [reviewed] Implement argument parsing (issue-id, --force flag)
- [x] [impl] [ ] [reviewed] Implement issue ID validation and config loading
- [x] [impl] [ ] [reviewed] Implement active session detection (prevent self-removal)
- [x] [impl] [ ] [reviewed] Implement uncommitted changes check with confirmation prompt
- [x] [impl] [ ] [reviewed] Implement tmux session cleanup (kill if exists)
- [x] [impl] [ ] [reviewed] Implement worktree removal workflow
- [x] [impl] [ ] [reviewed] Wire up complete command workflow with error handling

## Verification

- [x] [verify] [ ] [reviewed] Run all unit tests pass
- [x] [verify] [ ] [reviewed] Run all E2E tests pass
- [x] [verify] [ ] [reviewed] Manual test: remove worktree with uncommitted changes (with confirmation)
- [x] [verify] [ ] [reviewed] Manual test: verify branch is NOT deleted after worktree removal
