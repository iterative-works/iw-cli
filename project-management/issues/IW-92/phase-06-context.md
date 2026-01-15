# Phase 6 Context: State Management Refactoring

**Issue:** IW-92
**Phase:** 6 of 6
**Type:** Post-implementation refactoring

## Trigger

During code review discussion, discovered critical bugs in state management:

1. **Race condition causing file corruption**: Multiple concurrent requests write to the same temp file (`state.json.tmp`), causing JSON corruption (extra `}}}` observed in state file)

2. **Race condition causing data loss**: Non-atomic read-modify-write pattern means concurrent requests overwrite each other's changes (last write wins), leading to 0 worktrees after restart

3. **Design flaw in reviewStateCache**: Dashboard bulk-replaces entire `reviewStateCache` instead of per-entry updates, so HTMX card refresh doesn't update review state - the most important status indicator users wait to see change

## Decision

Introduce centralized `ServerStateService` that:

1. Holds state in memory (single source of truth)
2. Synchronizes all writes through a lock
3. Uses unique temp file names as secondary protection
4. Exposes per-entry update API for all caches
5. Makes dashboard read-only (no state writes)

## Scope

### Files Affected
- `.iw/core/CaskServer.scala` - Remove direct repository access, use ServerStateService
- `.iw/core/ServerStateService.scala` - NEW: Centralized state management
- `.iw/core/StateRepository.scala` - Fix temp file naming
- `.iw/core/WorktreeCardService.scala` - Update to refresh all caches per-card
- `.iw/core/DashboardService.scala` - Remove state writes, become read-only

### Components
- State persistence layer
- Per-card refresh logic
- Dashboard rendering

### Boundaries (DO NOT TOUCH)
- Domain models (ServerState, CachedIssue, etc.) - structure is fine
- HTMX/CSS/presentation layer - working correctly
- Existing pure services (WorktreeRegistrationService, etc.)

## Approach

### Phase 6a: Fix immediate corruption bug
- Unique temp file names in StateRepository

### Phase 6b: Centralized state service
- Create ServerStateService with in-memory state + lock
- Per-entry update API for all 5 caches
- Load once at startup, persist after each mutation

### Phase 6c: Migrate endpoints
- CaskServer uses ServerStateService instead of direct repository
- Dashboard becomes read-only
- Per-card refresh updates all caches for that worktree

### Phase 6d: Verification
- Test concurrent access
- Verify no data loss
- Confirm review state updates via HTMX

## Refactoring Decisions

### R1: Centralized State Management (2026-01-15)

**Trigger:** Race condition causing state file corruption and data loss during concurrent HTMX card refreshes

**Decision:** Create ServerStateService singleton with synchronized access, per-entry updates for all caches, and unique temp file names

**Scope:**
- Files affected: CaskServer, StateRepository, WorktreeCardService, DashboardService
- Components: State persistence, per-card refresh, dashboard rendering
- Boundaries: Domain models unchanged, presentation layer unchanged

**Approach:**
1. Fix temp file naming (immediate)
2. Create centralized service with lock
3. Migrate all state access through service
4. Make per-card refresh update all relevant caches
