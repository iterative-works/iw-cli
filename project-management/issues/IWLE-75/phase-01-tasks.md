# Phase 1 Tasks: Core switch functionality for start.scala

**Issue:** IWLE-75
**Phase:** 1 of 2
**Status:** Complete

## Tasks

### Setup
- [x] Create sub-branch `IWLE-75-phase-01` from feature branch

### Tests First (TDD)
- [x] Write test: `TmuxAdapter.switchSession` returns Left when session doesn't exist
- [x] Write test: `TmuxAdapter.switchSession` returns Left when not in tmux (verifies command fails gracefully)
- [x] Run tests - verify they fail (no implementation yet)

### Implementation
- [x] Add `switchSession(name: String): Either[String, Unit]` method to TmuxAdapter
- [x] Modify start.scala to check `isInsideTmux` before session join
- [x] Run tests - verify they pass

### Verification
- [x] Run full test suite (`./iw test unit`) - all tests pass
- [ ] Manual verification if tmux available (documented in context)

## Progress

**Completed:** 7/8 tasks
**Current:** Ready for manual verification
