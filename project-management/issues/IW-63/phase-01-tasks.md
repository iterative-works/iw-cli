# Phase 1 Tasks: Register current worktree to dashboard

**Issue:** IW-63
**Phase:** 1 - Register current worktree to dashboard
**Status:** Complete

## Setup

- [x] [impl] [x] [reviewed] Create register.scala command file with PURPOSE comments and imports

## Tests (E2E)

- [x] [test] [x] [reviewed] Write BATS test: success case - register in valid worktree with issue branch
- [x] [test] [x] [reviewed] Write BATS test: error case - not in git repository
- [x] [test] [x] [reviewed] Write BATS test: error case - branch without issue ID (e.g., main)
- [x] [test] [x] [reviewed] Write BATS test: error case - missing .iw/config.conf

## Implementation

- [x] [impl] [x] [reviewed] Implement config loading with proper error handling
- [x] [impl] [x] [reviewed] Implement branch detection and issue ID extraction
- [x] [impl] [x] [reviewed] Implement dashboard registration call with warning on failure
- [x] [impl] [x] [reviewed] Implement success message output

## Integration

- [x] [verify] [x] [reviewed] Run all E2E tests and verify they pass
- [x] [verify] [x] [reviewed] Manual test: run `iw register` in current worktree

## Notes

- Follow TDD: write tests before implementation
- Use existing patterns from open.scala and start.scala
- All infrastructure already exists, this is orchestration only
