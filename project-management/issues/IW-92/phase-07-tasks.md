# Phase 7 Tasks: Worktree list synchronization

**Issue:** IW-92
**Phase:** 7 of 7
**Story:** Worktree list synchronization
**Goal:** Dashboard detects worktree additions/removals and updates surgically via hx-swap-oob

## Task Groups

### Setup

- [x] [impl] [x] [reviewed] Review existing `WorktreeListView.scala` card ID patterns
- [x] [impl] [x] [reviewed] Review existing `CaskServer.scala` endpoint patterns
- [x] [impl] [x] [reviewed] Review HTMX hx-swap-oob documentation and behavior

### Tests - Diff Detection

- [x] [impl] [x] [reviewed] Test `detectChanges()` identifies new worktrees (additions)
- [x] [impl] [x] [reviewed] Test `detectChanges()` identifies removed worktrees (deletions)
- [x] [impl] [x] [reviewed] Test `detectChanges()` identifies order changes (reorders)
- [x] [impl] [x] [reviewed] Test `detectChanges()` returns empty for identical lists
- [x] [impl] [x] [reviewed] Test `detectChanges()` handles empty old list (all additions)
- [x] [impl] [x] [reviewed] Test `detectChanges()` handles empty new list (all deletions)

### Implementation - Diff Detection

- [x] [impl] [x] [reviewed] Create `WorktreeListSync.scala` with diff logic
- [x] [impl] [x] [reviewed] Add `detectChanges(oldIds: List[String], newIds: List[String])` method
- [x] [impl] [x] [reviewed] Return structured changes: `ListChanges(additions, deletions, reorders)`

### Tests - OOB Generation

- [x] [impl] [x] [reviewed] Test addition OOB includes `hx-swap-oob="beforeend:#worktree-list"`
- [x] [impl] [x] [reviewed] Test addition OOB contains full card HTML with HTMX attributes
- [x] [impl] [x] [reviewed] Test deletion OOB includes `hx-swap-oob="delete"` with correct ID
- [x] [impl] [x] [reviewed] Test reorder generates delete followed by positional add
- [x] [impl] [x] [reviewed] Test empty changes returns empty response body

### Implementation - OOB Generation

- [x] [impl] [x] [reviewed] Add `generateAdditionOob(registration, cachedData)` returning card HTML with OOB
- [x] [impl] [x] [reviewed] Add `generateDeletionOob(issueId)` returning minimal delete element
- [x] [impl] [x] [reviewed] Add `generateReorderOob(registration, cachedData, position)` for moves
- [x] [impl] [x] [reviewed] Add `generateChangesResponse(changes)` combining all OOB swaps

### Tests - Changes Endpoint

- [x] [impl] [x] [reviewed] Test `GET /api/worktrees/changes?since=0` returns all as additions
- [x] [impl] [x] [reviewed] Test `GET /api/worktrees/changes?since=<recent>` returns empty
- [x] [impl] [x] [reviewed] Test endpoint returns `X-Worktree-Timestamp` header for next poll
- [x] [impl] [x] [reviewed] Test endpoint returns valid HTML with OOB attributes

### Implementation - Changes Endpoint

- [ ] [impl] [ ] [reviewed] Add worktree change tracking to `ServerStateService` (timestamps per add/remove)
- [x] [impl] [x] [reviewed] Add `GET /api/worktrees/changes` endpoint to `CaskServer.scala`
- [x] [impl] [x] [reviewed] Parse `since` query parameter, default to 0
- [x] [impl] [x] [reviewed] Call diff logic, generate OOB response
- [x] [impl] [x] [reviewed] Return timestamp header for client's next poll

### Tests - Dashboard Integration

- [x] [impl] [x] [reviewed] Test worktree list container has `hx-get` for changes endpoint
- [x] [impl] [x] [reviewed] Test worktree list container has `hx-trigger="every 30s"`
- [x] [impl] [x] [reviewed] Test worktree list container has `hx-swap="none"`
- [x] [impl] [x] [reviewed] Test all cards have predictable IDs (`card-{issueId}`)

### Implementation - Dashboard Integration

- [x] [impl] [x] [reviewed] Update `WorktreeListView` to add OOB polling attributes to container
- [x] [impl] [x] [reviewed] Ensure all cards have `id="card-{issueId}"` attribute
- [x] [impl] [x] [reviewed] Add timestamp tracking to dashboard (initial render sets `since`)
- [x] [impl] [x] [reviewed] Update `hx-get` URL to include dynamic `since` parameter

### Integration

- [ ] [integration] Integration test: Add worktree, poll changes, verify addition OOB
- [ ] [integration] Integration test: Remove worktree, poll changes, verify deletion OOB
- [ ] [integration] Integration test: New card starts polling after OOB swap
- [ ] [integration] E2E: Full flow with real browser/HTMX (manual or Playwright)

## Acceptance Criteria Checklist

- [ ] New worktrees appear on dashboard within 30s of registration
- [ ] Removed worktrees disappear from dashboard within 30s
- [ ] Existing cards not replaced during list sync
- [ ] New cards start content polling automatically
- [ ] Order updates reflect activity priority
- [ ] No JavaScript diff logic (server-driven OOB)
- [ ] All existing Phase 1-6 functionality preserved

## Notes

- Key HTMX pattern: `hx-swap="none"` on poll trigger, only OOB swaps processed
- Card IDs must be stable: `card-{issueId}` (e.g., `card-IW-92`)
- Reorders are delete + re-add; minimize by only reordering when truly needed
- New cards inherit all HTMX attributes from Phase 3 (content polling)
- Silent on no changes - empty response body is valid

**Phase Status:** Complete
