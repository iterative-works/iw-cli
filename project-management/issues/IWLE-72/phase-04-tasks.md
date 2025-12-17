# Phase 4 Tasks: Create worktree for issue with tmux session

**Issue:** IWLE-72
**Phase:** 4 of 7
**Status:** Complete

---

## Setup

- [x] [setup] Review existing code structure (Output, Config, Process, Git modules)
- [x] [setup] Understand existing test patterns from phases 1-3

## Domain Layer - IssueId

- [x] [test] Write unit tests for `IssueId.parse` - valid formats (IWLE-123, ABC-1, XY-99999)
- [x] [test] Write unit tests for `IssueId.parse` - invalid formats (lowercase, no dash, no number, empty)
- [x] [test] Write unit tests for `IssueId.toBranchName` - returns value unchanged
- [x] [impl] Implement `IssueId` value object with validation

## Domain Layer - WorktreePath

- [x] [test] Write unit tests for `WorktreePath.directoryName` - combines project name and issue ID
- [x] [test] Write unit tests for `WorktreePath.resolve` - creates sibling path correctly
- [x] [test] Write unit tests for `WorktreePath.sessionName` - matches directory name
- [x] [impl] Implement `WorktreePath` value object

## Infrastructure Layer - TmuxAdapter

- [x] [test] Write integration tests for `TmuxAdapter.sessionExists` - session present vs absent
- [x] [test] Write integration tests for `TmuxAdapter.createSession` - creates session in specified directory
- [x] [test] Write integration tests for `TmuxAdapter.killSession` - kills existing session
- [x] [impl] Implement `TmuxAdapter` object with session management methods

## Infrastructure Layer - GitWorktreeAdapter

- [x] [test] Write integration tests for `GitWorktreeAdapter.worktreeExists` - worktree present vs absent
- [x] [test] Write integration tests for `GitWorktreeAdapter.createWorktree` - creates worktree with new branch
- [x] [test] Write integration tests for `GitWorktreeAdapter.createWorktreeForBranch` - uses existing branch
- [x] [test] Write integration tests for `GitWorktreeAdapter.branchExists` - branch present vs absent
- [x] [impl] Implement `GitWorktreeAdapter` object with worktree operations

## Command Implementation

- [x] [test] Write E2E tests for `./iw start IWLE-123` - successful creation
- [x] [test] Write E2E tests for `./iw start` - missing issue ID error
- [x] [test] Write E2E tests for `./iw start invalid-123` - invalid format error
- [x] [test] Write E2E tests for `./iw start IWLE-123` - existing directory error
- [x] [test] Write E2E tests for `./iw start IWLE-123` - existing session error (hint to use open)
- [x] [test] Write E2E tests for `./iw start IWLE-123` - missing config error (hint to run init)
- [x] [test] Write E2E tests for `./iw start IWLE-123` - uses existing branch if present
- [x] [impl] Implement `start.scala` command with full workflow

## Integration

- [x] [integration] Verify worktree created as sibling directory
- [x] [integration] Verify branch matches issue ID
- [x] [integration] Verify tmux session created with correct working directory
- [x] [integration] Verify cleanup on tmux session creation failure

## Progress

**Completed:** 29/29 tasks
**Test tasks:** 19
**Implementation tasks:** 6
**Setup tasks:** 2
**Integration tasks:** 4
