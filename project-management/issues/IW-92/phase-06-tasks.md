# Phase 6 Tasks: State Management Refactoring

**Issue:** IW-92
**Phase:** 6 of 6
**Status:** Complete

## Tasks

### 6a: Fix Immediate Corruption Bug
- [x] [impl] [x] [reviewed] Fix StateRepository to use unique temp file names (UUID suffix)

### 6b: Centralized State Service
- [x] [impl] [x] [reviewed] Create ServerStateService with in-memory state and ReentrantLock
- [x] [impl] [x] [reviewed] Add per-entry update API for all 5 caches
- [x] [impl] [x] [reviewed] Add initialization (load from disk) and persistence (write after mutation)

### 6c: Migrate Endpoints
- [x] [impl] [x] [reviewed] Migrate CaskServer to use ServerStateService
- [x] [impl] [x] [reviewed] Make dashboard endpoint read-only (no state writes)
- [x] [impl] [x] [reviewed] Update WorktreeCardService to refresh all caches per-card (issue, progress, PR, reviewState)

### 6d: Verification
- [x] [impl] [x] [reviewed] Add tests for concurrent state access
- [x] [impl] [x] [reviewed] Manual verification: reload dashboard, confirm no corruption
- [x] [impl] [x] [reviewed] Verify review state updates via HTMX card refresh

## Acceptance Criteria

- [x] No file corruption under concurrent load
- [x] No data loss after page reload with multiple worktrees
- [x] Review state updates appear via HTMX without full page refresh
- [x] All existing tests pass
- [x] Dashboard loads remain fast (< 100ms)

## Notes

- This is a critical bug fix disguised as refactoring
- The lock is acceptable for single-server CLI tool
- Unique temp files are belt-and-suspenders protection
- Per-entry updates for reviewStateCache is the design fix

**Phase Status:** Complete
