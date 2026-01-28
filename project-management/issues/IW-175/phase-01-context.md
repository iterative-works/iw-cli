# Phase 1: Stable card positions during auto-refresh

**Issue:** IW-175
**Story:** 1 - Stable card positions during auto-refresh
**Estimated Effort:** 4-6 hours

## Goals

Replace the dynamic activity-based sorting with stable Issue ID sorting so that dashboard cards maintain their positions during auto-refresh cycles. This eliminates the misclick problem caused by cards reordering unpredictably.

**Primary Outcome:** Cards stay in the same position during 30-second auto-refresh when no worktrees are added or removed.

## Scope

### In Scope

1. **Replace sorting logic in `DashboardService.renderDashboard`**
   - Change from `WorktreePriority.priorityScore(wt, now)` to Issue ID-based sorting
   - Sort alphabetically by Issue ID (ascending)

2. **Replace/update `ServerState.listByActivity`**
   - Either rename to `listByIssueId` or change implementation to sort by Issue ID
   - Update or deprecate `lastSeenAt`-based sorting

3. **Update `CaskServer` endpoints that use `listByActivity`**
   - `/` (dashboard) route at line 43
   - `/api/worktrees/changes` route at line 187

4. **Create pure sorting utility for Issue IDs**
   - Handle alphabetical sorting correctly (IW-1, IW-2, IW-10, IW-100)
   - Support mixed prefixes (IW-100, GH-50, LINEAR-25)
   - Consider numeric-aware natural sorting for readability

5. **Update tests**
   - Modify `ServerStateTest` to reflect new sorting behavior
   - Add unit tests for Issue ID sorting logic

### Out of Scope

- Story 2 (predictable new worktree insertion) - handled in Phase 2
- Story 3 (predictable removal behavior) - handled in Phase 3
- Story 4 (user-selectable sort order) - deferred to future issue
- Any UI changes (no visible changes to card rendering)
- Activity-based sorting as an option (removed entirely per analysis decision)

## Dependencies

### Prerequisites (must exist)

- `WorktreeRegistration` domain model with `issueId` field (exists)
- `ServerState` domain model (exists)
- `DashboardService.renderDashboard` method (exists)
- `CaskServer` HTTP routes (exist)

### No dependencies on previous phases

This is Phase 1 - no prior phases required.

## Technical Approach

### 1. Create Issue ID Sorting Utility

Create a pure function for sorting Issue IDs that handles natural ordering:

**Location:** `.iw/core/model/WorktreeSorter.scala` (new file)

```scala
object WorktreeSorter:
  /** Sort worktrees by Issue ID using natural alphabetical ordering.
    *
    * Ordering rules:
    * - Alphabetical by full Issue ID string
    * - Natural numeric comparison within same prefix (IW-2 < IW-10)
    * - Mixed prefixes sorted alphabetically (GH-50 < IW-100)
    */
  def sortByIssueId(worktrees: Iterable[WorktreeRegistration]): List[WorktreeRegistration]
```

**Decision:** Use simple alphabetical string sorting first. Natural numeric sorting (IW-2 before IW-10) can be added if needed, but pure alphabetical is predictable and sufficient.

### 2. Update ServerState

**Option A (Recommended):** Replace `listByActivity` implementation

```scala
// Before
def listByActivity: List[WorktreeRegistration] =
  worktrees.values.toList.sortBy(_.lastSeenAt.getEpochSecond)(Ordering[Long].reverse)

// After
def listByIssueId: List[WorktreeRegistration] =
  worktrees.values.toList.sortBy(_.issueId)
```

**Option B:** Add new method and deprecate old

Keep `listByActivity` for backward compatibility but add `listByIssueId` and use it everywhere.

**Recommendation:** Option A - clean replacement since `listByActivity` is only used internally and the activity sorting is being removed entirely.

### 3. Update DashboardService

**Location:** `.iw/core/dashboard/DashboardService.scala`, line 42

```scala
// Before
val sortedWorktrees = worktrees.sortBy(wt => WorktreePriority.priorityScore(wt, now))(Ordering[Long].reverse)

// After
val sortedWorktrees = worktrees.sortBy(_.issueId)
```

Note: The `worktrees` parameter is already a `List[WorktreeRegistration]`, so we can sort directly without going through `ServerState`.

### 4. Update CaskServer

**Location:** `.iw/core/dashboard/CaskServer.scala`

Two places use `state.listByActivity`:
- Line 43: `val worktrees = state.listByActivity`
- Line 187: `val currentWorktrees = state.listByActivity`

Change both to use the new sorting method (either `listByIssueId` or inline `sortBy(_.issueId)`).

### 5. Consider WorktreePriority

`WorktreePriority.priorityScore` is no longer needed for dashboard sorting. Options:
- Keep it for potential future use (e.g., staggered loading priority)
- Remove it entirely

**Recommendation:** Keep for now - it's used in staggered loading and may be useful for future features. Just don't use it for card ordering.

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `.iw/core/model/ServerState.scala` | Modify | Replace `listByActivity` with `listByIssueId` |
| `.iw/core/dashboard/DashboardService.scala` | Modify | Change line 42 to sort by Issue ID |
| `.iw/core/dashboard/CaskServer.scala` | Modify | Update lines 43 and 187 to use new sort |
| `.iw/core/test/ServerStateTest.scala` | Modify | Update tests for new sorting behavior |

### Optional New Files

| File | Purpose |
|------|---------|
| `.iw/core/model/WorktreeSorter.scala` | Pure sorting utilities (if extracted) |
| `.iw/core/test/WorktreeSorterTest.scala` | Tests for sorting utilities |

## Testing Strategy

### Unit Tests

1. **ServerState sorting tests** (modify existing)
   - Test `listByIssueId` returns worktrees sorted alphabetically
   - Test empty state returns empty list
   - Test single worktree works correctly

2. **Issue ID ordering tests** (new)
   - Simple alphabetical: "GH-50" < "IW-100" < "LINEAR-25"
   - Same prefix ordering: "IW-1" < "IW-10" < "IW-100" < "IW-2" (if pure alphabetical)
   - Or natural ordering: "IW-1" < "IW-2" < "IW-10" < "IW-100" (if numeric-aware)
   - Mixed case handling (if any)

### Integration Tests

1. **DashboardService sorting**
   - Create multiple worktrees with varying Issue IDs and activity timestamps
   - Verify rendered HTML has cards in Issue ID order (not activity order)

### E2E Tests (if time permits)

1. **Dashboard stability test**
   - Load dashboard with 5+ worktrees
   - Note card positions
   - Wait for auto-refresh (30+ seconds)
   - Verify positions unchanged

## Acceptance Criteria

1. **Cards maintain positions during auto-refresh**
   - When no worktrees are added or removed, card positions stay the same after refresh
   - Verified by E2E test or manual testing

2. **Card order is predictable**
   - Cards are sorted alphabetically by Issue ID
   - Order is deterministic and reproducible

3. **No layout shift during normal operation**
   - Activity changes (opening worktree, editing files) do not cause cards to reorder
   - Only adding/removing worktrees changes the layout

4. **All existing tests pass**
   - Unit tests updated to reflect new behavior
   - No regressions in card rendering, data fetching, or HTMX behavior

5. **Staggered loading still works**
   - Skeleton cards still load progressively
   - `WorktreePriority.priorityScore` still used for refresh priority (if applicable)

## Implementation Notes

### Alphabetical vs Natural Sorting Decision

**Simple alphabetical** (recommended for Phase 1):
- "IW-1" < "IW-10" < "IW-100" < "IW-2"
- Simpler to implement
- Predictable for users who understand string sorting

**Natural numeric** (can add later):
- "IW-1" < "IW-2" < "IW-10" < "IW-100"
- More intuitive for humans
- Slightly more complex implementation

Start with simple alphabetical. If users find it confusing, natural sorting can be added as a small follow-up.

### Backward Compatibility

- No external API changes
- No JSON schema changes
- No configuration changes
- Internal-only sorting change

### Rollback Plan

If issues arise, revert to activity-based sorting by:
1. Restore `listByActivity` implementation in `ServerState.scala`
2. Restore `WorktreePriority.priorityScore` usage in `DashboardService.scala`
3. Restore `listByActivity` calls in `CaskServer.scala`

No data migration needed.

---

## Task Checklist

- [ ] Create `WorktreeSorter` object with `sortByIssueId` function (or inline)
- [ ] Write unit tests for Issue ID sorting
- [ ] Update `ServerState.listByActivity` to `listByIssueId`
- [ ] Update `ServerStateTest` for new sorting behavior
- [ ] Update `DashboardService.renderDashboard` sorting logic
- [ ] Update `CaskServer` to use new sorting method (2 locations)
- [ ] Run all tests to verify no regressions
- [ ] Manual verification: load dashboard, wait for refresh, verify stable positions
