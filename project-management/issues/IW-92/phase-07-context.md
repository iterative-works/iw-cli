# Phase 7 Context: Worktree list synchronization

**Issue:** IW-92
**Phase:** 7 of 7
**Story:** New story - Worktree list synchronization

## User Story

```gherkin
Feature: Dashboard worktree list synchronization
  As a user viewing the dashboard
  I want the worktree list to update when I add/remove worktrees
  So that I see accurate status without manual page refresh

Scenario: New worktree appears on dashboard
  Given the dashboard has loaded with 3 worktree cards
  When I run `iw start ISSUE-NEW` in another terminal
  And the dashboard polls for worktree list updates (within 30s)
  Then a new card appears for "ISSUE-NEW"
  And existing cards remain unchanged
  And the new card starts its own content polling

Scenario: Removed worktree disappears from dashboard
  Given the dashboard has loaded with 3 worktree cards
  When I run `iw rm ISSUE-OLD` in another terminal
  And the dashboard polls for worktree list updates
  Then the "ISSUE-OLD" card is removed from the dashboard
  And remaining cards continue unchanged

Scenario: Worktrees reorder based on activity
  Given the dashboard has worktrees [A, B, C] in that order
  When worktree C has new activity (becomes most recent)
  And the dashboard polls for list updates
  Then the order updates to [C, A, B]
  And cards move smoothly without content loss
```

## Goals

This phase adds worktree list synchronization - detecting when worktrees are added, removed, or should be reordered, and surgically updating the DOM without replacing the entire list.

1. New "changes" endpoint that returns only OOB swaps for additions/deletions/reorders
2. Dashboard polls this endpoint periodically (every 30s)
3. Server-driven updates using `hx-swap-oob` - no client-side JavaScript diffing
4. HATEOAS-style approach where server tells client exactly what DOM mutations to perform

## Scope

### In Scope

- New endpoint: `GET /api/worktrees/changes?since=<timestamp>` - Returns OOB swaps only
- Polling trigger on dashboard with `hx-swap="none"` (only OOB processed)
- Server-side diff logic to detect additions, deletions, and reorders
- OOB swap generation for each type of change
- Cards maintain unique IDs for targeting (`card-{issueId}`)

### Out of Scope

- Server-Sent Events (SSE) - keeping simple with polling
- Real-time instant updates (30s delay acceptable)
- Complex merge conflict handling (last write wins)

## Dependencies

### From Previous Phases

**Phase 1-2:**
- Cached data always available for new cards
- Skeleton card rendering for cache misses

**Phase 3:**
- Per-card HTMX polling (new cards inherit this)
- `WorktreeCardService.renderCard()` for generating card HTML

**Phase 5:**
- `WorktreePriority.priorityScore()` for determining order
- Priority-based sorting logic

**Phase 6:**
- `ServerStateService` for centralized state access
- Thread-safe state operations

### External Dependencies

- HTMX library with `hx-swap-oob` support (v1.9.10+ already included)

## Technical Approach

### Current Problem

```
Dashboard loads → Renders N cards → Cards poll for content updates
                                  ↓
                      But list of cards is FIXED
                                  ↓
New worktree added → Never appears (no mechanism to add card)
Worktree removed → Card polls 404, stuck in loading state
```

### Target Flow with hx-swap-oob

```
Dashboard loads → Renders N cards with IDs (card-ISSUE-1, card-ISSUE-2, ...)
              → Worktree list container polls /api/worktrees/changes?since=X
              → hx-swap="none" means main response ignored
              → Server returns ONLY hx-swap-oob elements:
                  - Additions: <div id="card-NEW" hx-swap-oob="beforeend:#worktree-list">...</div>
                  - Deletions: <div id="card-OLD" hx-swap-oob="delete"></div>
                  - Reorders: delete + re-add at new position
              → HTMX processes OOB swaps, surgically updates DOM
              → New cards start their own content polling automatically
```

### Key Components

1. **Changes Endpoint**
   ```
   GET /api/worktrees/changes?since=<epoch_ms>
   ```
   - Compares current worktree list vs client's known state
   - Returns empty response if no changes
   - Returns OOB swaps for additions, deletions, reorders
   - Updates `since` timestamp in response header for next poll

2. **OOB Swap Generation**

   **Addition:**
   ```html
   <div id="card-ISSUE-NEW" hx-swap-oob="beforeend:#worktree-list">
     <!-- Full card HTML with hx-get, hx-trigger, etc. -->
   </div>
   ```

   **Deletion:**
   ```html
   <div id="card-ISSUE-OLD" hx-swap-oob="delete"></div>
   ```

   **Reorder (move to top):**
   ```html
   <div id="card-ISSUE-X" hx-swap-oob="delete"></div>
   <div id="card-ISSUE-X" hx-swap-oob="afterbegin:#worktree-list">
     <!-- Full card HTML -->
   </div>
   ```

3. **Dashboard Polling Trigger**
   ```html
   <div id="worktree-list"
        hx-get="/api/worktrees/changes?since={timestamp}"
        hx-trigger="every 30s"
        hx-swap="none">
     <!-- Existing cards -->
   </div>
   ```

4. **Server-Side Diff Logic**
   - Track worktree list with timestamps
   - Compare current list vs `since` parameter
   - Detect: new IDs (additions), missing IDs (deletions), order changes (reorders)
   - Generate minimal set of OOB swaps

### State Tracking

Server needs to know what changed since client's last poll:

**Option A: Timestamp-based**
- Client sends `?since=1705312345678`
- Server compares current state vs that timestamp
- Requires tracking when each worktree was added/removed

**Option B: Version-based**
- Server maintains monotonic version counter
- Client sends `?version=42`
- Server returns changes since version 42

**Recommendation:** Timestamp-based is simpler and fits existing `lastSeenAt` patterns.

### Card ID Convention

Cards must have predictable IDs for OOB targeting:
- Format: `card-{issueId}` (e.g., `card-IW-92`, `card-IWLE-100`)
- Already partially in place from Phase 3

## Files to Modify

### Core Changes

| File | Change |
|------|--------|
| `.iw/core/CaskServer.scala` | Add `/api/worktrees/changes` endpoint |
| `.iw/core/WorktreeListView.scala` | Add OOB polling attributes to container, ensure card IDs |
| `.iw/core/DashboardService.scala` | Track worktree list state for diff |

### New Files

| File | Purpose |
|------|---------|
| `.iw/core/WorktreeListSync.scala` | Diff logic and OOB swap generation |

### Test Files

| File | Purpose |
|------|---------|
| `.iw/core/test/WorktreeListSyncTest.scala` | Unit tests for diff and OOB generation |

## Testing Strategy

### Unit Tests

1. **Diff detection**
   - `detectChanges(oldList, newList)` returns correct additions
   - `detectChanges(oldList, newList)` returns correct deletions
   - `detectChanges(oldList, newList)` returns correct reorders
   - Empty changes when lists match

2. **OOB generation**
   - `generateAdditionOob(card)` returns valid `hx-swap-oob="beforeend"` HTML
   - `generateDeletionOob(issueId)` returns valid `hx-swap-oob="delete"` HTML
   - `generateReorderOob(card, position)` returns delete + add HTML

3. **Timestamp tracking**
   - Changes endpoint respects `since` parameter
   - No changes returned for recent timestamp
   - All changes returned for old timestamp

### Integration Tests

1. **Changes endpoint**
   - `GET /api/worktrees/changes?since=0` returns all worktrees as additions
   - `GET /api/worktrees/changes?since=<recent>` returns empty for no changes
   - Response includes OOB swaps in correct format

2. **Full flow**
   - Add worktree via `iw start`
   - Poll changes endpoint
   - Verify addition OOB returned

### E2E Tests (BATS)

1. **Addition detection**
   - Start dashboard, note card count
   - Register new worktree
   - Poll changes endpoint
   - Verify new card OOB in response

2. **Deletion detection**
   - Start dashboard with N worktrees
   - Unregister one worktree
   - Poll changes endpoint
   - Verify deletion OOB in response

## Acceptance Criteria

- [ ] New worktrees appear on dashboard within 30s of registration
- [ ] Removed worktrees disappear from dashboard within 30s
- [ ] Existing cards are not replaced or re-rendered during list sync
- [ ] New cards start their own content polling automatically
- [ ] Order updates reflect activity priority (most recent first)
- [ ] No JavaScript required for diff logic (server-driven)
- [ ] All existing Phase 1-6 functionality preserved

## Notes

- This is true HATEOAS: server returns hypermedia (HTML with instructions), client executes
- `hx-swap="none"` is key - main response body ignored, only OOB swaps processed
- Cards must have stable, predictable IDs (`card-{issueId}`)
- Reorder is expensive (delete + re-add), only do when order actually changes
- Consider debouncing reorders to avoid excessive DOM churn

## Success Metrics

- List syncs within 30s of worktree add/remove
- No full page refreshes needed
- No visible flicker during sync
- New cards fully functional (polling, timestamps, etc.)
- CPU/network overhead minimal (only changes transmitted)
