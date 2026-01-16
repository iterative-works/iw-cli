# Phase 7 Tasks: Worktree list synchronization

**Issue:** IW-92
**Phase:** 7 of 7
**Story:** Worktree list synchronization
**Goal:** Dashboard detects worktree additions/removals and updates surgically via hx-swap-oob

## Task Groups

### Setup

- [ ] [impl] Review existing `WorktreeListView.scala` card ID patterns
- [ ] [impl] Review existing `CaskServer.scala` endpoint patterns
- [ ] [impl] Review HTMX hx-swap-oob documentation and behavior

### Tests - Diff Detection

- [ ] [impl] Test `detectChanges()` identifies new worktrees (additions)
- [ ] [impl] Test `detectChanges()` identifies removed worktrees (deletions)
- [ ] [impl] Test `detectChanges()` identifies order changes (reorders)
- [ ] [impl] Test `detectChanges()` returns empty for identical lists
- [ ] [impl] Test `detectChanges()` handles empty old list (all additions)
- [ ] [impl] Test `detectChanges()` handles empty new list (all deletions)

### Implementation - Diff Detection

- [ ] [impl] Create `WorktreeListSync.scala` with diff logic
- [ ] [impl] Add `detectChanges(oldIds: List[String], newIds: List[String])` method
- [ ] [impl] Return structured changes: `ListChanges(additions, deletions, reorders)`

### Tests - OOB Generation

- [ ] [impl] Test addition OOB includes `hx-swap-oob="beforeend:#worktree-list"`
- [ ] [impl] Test addition OOB contains full card HTML with HTMX attributes
- [ ] [impl] Test deletion OOB includes `hx-swap-oob="delete"` with correct ID
- [ ] [impl] Test reorder generates delete followed by positional add
- [ ] [impl] Test empty changes returns empty response body

### Implementation - OOB Generation

- [ ] [impl] Add `generateAdditionOob(registration, cachedData)` returning card HTML with OOB
- [ ] [impl] Add `generateDeletionOob(issueId)` returning minimal delete element
- [ ] [impl] Add `generateReorderOob(registration, cachedData, position)` for moves
- [ ] [impl] Add `generateChangesResponse(changes)` combining all OOB swaps

### Tests - Changes Endpoint

- [ ] [impl] Test `GET /api/worktrees/changes?since=0` returns all as additions
- [ ] [impl] Test `GET /api/worktrees/changes?since=<recent>` returns empty
- [ ] [impl] Test endpoint returns `X-Worktree-Timestamp` header for next poll
- [ ] [impl] Test endpoint returns valid HTML with OOB attributes

### Implementation - Changes Endpoint

- [ ] [impl] Add worktree change tracking to `ServerStateService` (timestamps per add/remove)
- [ ] [impl] Add `GET /api/worktrees/changes` endpoint to `CaskServer.scala`
- [ ] [impl] Parse `since` query parameter, default to 0
- [ ] [impl] Call diff logic, generate OOB response
- [ ] [impl] Return timestamp header for client's next poll

### Tests - Dashboard Integration

- [ ] [impl] Test worktree list container has `hx-get` for changes endpoint
- [ ] [impl] Test worktree list container has `hx-trigger="every 30s"`
- [ ] [impl] Test worktree list container has `hx-swap="none"`
- [ ] [impl] Test all cards have predictable IDs (`card-{issueId}`)

### Implementation - Dashboard Integration

- [ ] [impl] Update `WorktreeListView` to add OOB polling attributes to container
- [ ] [impl] Ensure all cards have `id="card-{issueId}"` attribute
- [ ] [impl] Add timestamp tracking to dashboard (initial render sets `since`)
- [ ] [impl] Update `hx-get` URL to include dynamic `since` parameter

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

**Phase Status:** Not Started
