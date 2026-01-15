# Phase 6 Tasks: State Management Refactoring

**Issue:** IW-92
**Phase:** 6 of 6
**Status:** Not started

## Tasks

### 6a: Fix Immediate Corruption Bug
- [ ] [impl] [ ] [reviewed] Fix StateRepository to use unique temp file names (UUID suffix)

### 6b: Centralized State Service
- [ ] [impl] [ ] [reviewed] Create ServerStateService with in-memory state and ReentrantLock
- [ ] [impl] [ ] [reviewed] Add per-entry update API for all 5 caches
- [ ] [impl] [ ] [reviewed] Add initialization (load from disk) and persistence (write after mutation)

### 6c: Migrate Endpoints
- [ ] [impl] [ ] [reviewed] Migrate CaskServer to use ServerStateService
- [ ] [impl] [ ] [reviewed] Make dashboard endpoint read-only (no state writes)
- [ ] [impl] [ ] [reviewed] Update WorktreeCardService to refresh all caches per-card (issue, progress, PR, reviewState)

### 6d: Verification
- [ ] [impl] [ ] [reviewed] Add tests for concurrent state access
- [ ] [impl] [ ] [reviewed] Manual verification: reload dashboard, confirm no corruption
- [ ] [impl] [ ] [reviewed] Verify review state updates via HTMX card refresh

## Acceptance Criteria

- [ ] No file corruption under concurrent load
- [ ] No data loss after page reload with multiple worktrees
- [ ] Review state updates appear via HTMX without full page refresh
- [ ] All existing tests pass
- [ ] Dashboard loads remain fast (< 100ms)

## Notes

- This is a critical bug fix disguised as refactoring
- The lock is acceptable for single-server CLI tool
- Unique temp files are belt-and-suspenders protection
- Per-entry updates for reviewStateCache is the design fix
