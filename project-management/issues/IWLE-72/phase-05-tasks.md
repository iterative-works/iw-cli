# Phase 5 Tasks: Open existing worktree tmux session

**Issue:** IWLE-72
**Phase:** 5 of 7
**Status:** In Progress

---

## Setup

- [x] [setup] [ ] [reviewed] Review existing code structure (IssueId, WorktreePath, TmuxAdapter, GitAdapter)
- [x] [setup] [ ] [reviewed] Understand patterns from phase-04 implementation

## Domain Layer - IssueId Extension

- [x] [test] [ ] [reviewed] Write unit tests for `IssueId.fromBranch` - exact match (IWLE-123)
- [x] [test] [ ] [reviewed] Write unit tests for `IssueId.fromBranch` - with suffix (IWLE-123-description)
- [x] [test] [ ] [reviewed] Write unit tests for `IssueId.fromBranch` - lowercase normalization
- [x] [test] [ ] [reviewed] Write unit tests for `IssueId.fromBranch` - invalid branches (main, feature-branch)
- [x] [impl] [ ] [reviewed] Implement `IssueId.fromBranch` method for branch name extraction

## Infrastructure Layer - Git Extension

- [x] [test] [ ] [reviewed] Write integration tests for `GitAdapter.getCurrentBranch` - returns branch name
- [x] [test] [ ] [reviewed] Write integration tests for `GitAdapter.getCurrentBranch` - handles detached HEAD
- [x] [impl] [ ] [reviewed] Implement `GitAdapter.getCurrentBranch` method

## Infrastructure Layer - Tmux Extension

- [x] [test] [ ] [reviewed] Write unit tests for `TmuxAdapter.isInsideTmux` - with TMUX env var set
- [x] [test] [ ] [reviewed] Write unit tests for `TmuxAdapter.isInsideTmux` - without TMUX env var
- [x] [test] [ ] [reviewed] Write integration tests for `TmuxAdapter.currentSessionName` - inside tmux
- [x] [impl] [ ] [reviewed] Implement `TmuxAdapter.isInsideTmux` property
- [x] [impl] [ ] [reviewed] Implement `TmuxAdapter.currentSessionName` method

## Command Implementation

- [x] [test] [ ] [reviewed] Write E2E tests for `./iw open IWLE-123` - attach to existing session
- [x] [test] [ ] [reviewed] Write E2E tests for `./iw open IWLE-123` - create session for existing worktree
- [x] [test] [ ] [reviewed] Write E2E tests for `./iw open` - infer issue from branch
- [x] [test] [ ] [reviewed] Write E2E tests for `./iw open IWLE-999` - worktree not found error
- [x] [test] [ ] [reviewed] Write E2E tests for `./iw open invalid-123` - invalid format error
- [x] [test] [ ] [reviewed] Write E2E tests for `./iw open` - cannot infer from non-issue branch
- [x] [test] [ ] [reviewed] Write E2E tests for `./iw open IWLE-123` - missing config error
- [x] [test] [ ] [reviewed] Write E2E tests for nested tmux scenario - different session
- [x] [test] [ ] [reviewed] Write E2E tests for nested tmux scenario - already in target session
- [x] [impl] [ ] [reviewed] Implement `open.scala` command with full workflow

## Integration

- [x] [integration] [ ] [reviewed] Verify session attach works correctly
- [x] [integration] [ ] [reviewed] Verify session creation for existing worktree
- [x] [integration] [ ] [reviewed] Verify branch inference with real git branches
- [x] [integration] [ ] [reviewed] Verify nested tmux detection

## Progress

**Completed:** 29/29 tasks (awaiting review)
**Test tasks:** 18 (all passing)
**Implementation tasks:** 6 (all complete)
**Setup tasks:** 2 (all complete)
**Integration tasks:** 4 (all verified)
