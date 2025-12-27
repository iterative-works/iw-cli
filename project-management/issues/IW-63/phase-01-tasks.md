# Phase 1 Tasks: Register current worktree to dashboard

**Issue:** IW-63
**Phase:** 1 - Register current worktree to dashboard
**Status:** Not Started

## Setup

- [ ] [impl] Create register.scala command file with PURPOSE comments and imports

## Tests (E2E)

- [ ] [test] Write BATS test: success case - register in valid worktree with issue branch
- [ ] [test] Write BATS test: error case - not in git repository
- [ ] [test] Write BATS test: error case - branch without issue ID (e.g., main)
- [ ] [test] Write BATS test: error case - missing .iw/config.conf

## Implementation

- [ ] [impl] Implement config loading with proper error handling
- [ ] [impl] Implement branch detection and issue ID extraction
- [ ] [impl] Implement dashboard registration call with warning on failure
- [ ] [impl] Implement success message output

## Integration

- [ ] [verify] Run all E2E tests and verify they pass
- [ ] [verify] Manual test: run `iw register` in current worktree

## Notes

- Follow TDD: write tests before implementation
- Use existing patterns from open.scala and start.scala
- All infrastructure already exists, this is orchestration only
