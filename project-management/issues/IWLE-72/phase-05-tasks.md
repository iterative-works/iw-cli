# Phase 5 Tasks: Open existing worktree tmux session

**Issue:** IWLE-72
**Phase:** 5 of 7
**Status:** In Progress

---

## Setup

- [ ] [setup] Review existing code structure (IssueId, WorktreePath, TmuxAdapter, GitAdapter)
- [ ] [setup] Understand patterns from phase-04 implementation

## Domain Layer - IssueId Extension

- [ ] [test] Write unit tests for `IssueId.fromBranch` - exact match (IWLE-123)
- [ ] [test] Write unit tests for `IssueId.fromBranch` - with suffix (IWLE-123-description)
- [ ] [test] Write unit tests for `IssueId.fromBranch` - lowercase normalization
- [ ] [test] Write unit tests for `IssueId.fromBranch` - invalid branches (main, feature-branch)
- [ ] [impl] Implement `IssueId.fromBranch` method for branch name extraction

## Infrastructure Layer - Git Extension

- [ ] [test] Write integration tests for `GitAdapter.getCurrentBranch` - returns branch name
- [ ] [test] Write integration tests for `GitAdapter.getCurrentBranch` - handles detached HEAD
- [ ] [impl] Implement `GitAdapter.getCurrentBranch` method

## Infrastructure Layer - Tmux Extension

- [ ] [test] Write unit tests for `TmuxAdapter.isInsideTmux` - with TMUX env var set
- [ ] [test] Write unit tests for `TmuxAdapter.isInsideTmux` - without TMUX env var
- [ ] [test] Write integration tests for `TmuxAdapter.currentSessionName` - inside tmux
- [ ] [impl] Implement `TmuxAdapter.isInsideTmux` property
- [ ] [impl] Implement `TmuxAdapter.currentSessionName` method

## Command Implementation

- [ ] [test] Write E2E tests for `./iw open IWLE-123` - attach to existing session
- [ ] [test] Write E2E tests for `./iw open IWLE-123` - create session for existing worktree
- [ ] [test] Write E2E tests for `./iw open` - infer issue from branch
- [ ] [test] Write E2E tests for `./iw open IWLE-999` - worktree not found error
- [ ] [test] Write E2E tests for `./iw open invalid-123` - invalid format error
- [ ] [test] Write E2E tests for `./iw open` - cannot infer from non-issue branch
- [ ] [test] Write E2E tests for `./iw open IWLE-123` - missing config error
- [ ] [test] Write E2E tests for nested tmux scenario - different session
- [ ] [test] Write E2E tests for nested tmux scenario - already in target session
- [ ] [impl] Implement `open.scala` command with full workflow

## Integration

- [ ] [integration] Verify session attach works correctly
- [ ] [integration] Verify session creation for existing worktree
- [ ] [integration] Verify branch inference with real git branches
- [ ] [integration] Verify nested tmux detection

## Progress

**Completed:** 0/29 tasks
**Test tasks:** 18
**Implementation tasks:** 6
**Setup tasks:** 2
**Integration tasks:** 4
