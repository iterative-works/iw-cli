# Phase 3: Removed worktrees shift remaining cards predictably

**Issue:** IW-175
**Story:** 3 - Removed worktrees shift remaining cards predictably
**Estimated Effort:** 1-2 hours

## Goals

Validate that when a worktree is removed (via auto-pruning), the card disappears and remaining cards shift predictably to fill the gap. The sort order remains stable (alphabetical by Issue ID) after removal.

**Primary Outcome:** Confirm that the existing deletion behavior works correctly with the stable Issue ID sorting implemented in Phases 1-2.

## Scope

### In Scope

1. **Verify `generateDeletionOob` produces correct HTMX delete swap**
   - Already exists and generates `hx-swap-oob="delete"`
   - Card removed by ID: `#card-{issueId}`

2. **Verify deletion in `generateChangesResponse` works correctly**
   - Deletions are detected by `detectChanges`
   - Deletion OOB is generated for each removed card
   - Remaining cards maintain their relative order

3. **Add tests for deletion behavior with stable sorting**
   - Test that deletion doesn't cause reorders
   - Test that multiple deletions work correctly
   - Test edge cases (delete first, delete last, delete middle)

4. **Verify existing auto-pruning integrates correctly**
   - `CaskServer` pruning mechanism unchanged
   - `detectChanges` correctly identifies deletions

### Out of Scope

- Changes to `generateDeletionOob` (already correct)
- Changes to auto-pruning logic (not needed)
- Any reordering logic for deletions (remaining cards shift naturally)
- User-initiated deletion UI (separate issue if needed)

## Dependencies

### Prerequisites (from Phase 1-2)

- `ServerState.listByIssueId` - provides stable Issue ID ordering (Phase 1)
- `WorktreeListSync.detectChanges` - identifies deletions (exists)
- `WorktreeListSync.generateDeletionOob` - generates OOB delete HTML (exists)
- `WorktreeListSync.generateChangesResponse` - combines all changes (Phase 2)

### Phase 2 Deliverables Used

From the implementation log, Phase 2 delivered:
- `findPredecessor` for positional insertion (not needed for deletions)
- Updated `generateAdditionOob` with `predecessorId` parameter (not needed for deletions)
- Updated `generateChangesResponse` with `currentIds` parameter

Phase 3 reuses the existing deletion logic - no changes expected.

## Technical Approach

### Current Behavior (Already Correct)

`WorktreeListSync.generateDeletionOob` generates:

```html
<div id="card-IW-150" hx-swap-oob="delete"></div>
```

This tells HTMX to remove the element with `id="card-IW-150"` from the DOM. When the card is removed, remaining cards naturally shift to fill the gap (standard DOM behavior, no explicit repositioning needed).

### What Needs Verification

1. **Deletion doesn't trigger reorders**: When a card is deleted, the `detectChanges` algorithm should NOT mark remaining cards as "reordered" - they just shift positions.

2. **Stable sort maintained**: After deletion, remaining cards stay in alphabetical Issue ID order.

3. **Multiple deletions work**: If multiple worktrees are removed simultaneously, all cards are deleted correctly.

### Algorithm Analysis

Looking at `detectChanges`:

```scala
def detectChanges(oldIds: List[String], newIds: List[String]): ListChanges =
  val deletions = oldIds.filterNot(newSet.contains)
  val reorders = if commonIds != commonInNewOrder then ...
```

Key insight: If we delete `IW-150` from `[IW-100, IW-150, IW-200]`:
- `oldIds = [IW-100, IW-150, IW-200]`
- `newIds = [IW-100, IW-200]`
- `commonIds = [IW-100, IW-200]` (IW-150 removed)
- `commonInNewOrder = [IW-100, IW-200]` (same order)
- `deletions = [IW-150]`
- `reorders = []` (common items have same relative order)

This is correct - deletion does NOT cause reorders.

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `.iw/core/test/WorktreeListSyncTest.scala` | Modify | Add tests for deletion behavior |

### No Implementation Changes Required

All deletion logic already exists and works correctly. Phase 3 is primarily verification and testing.

## Testing Strategy

### Unit Tests

1. **`detectChanges` deletion tests**
   - Delete single card - no reorders
   - Delete first card - remaining cards not reordered
   - Delete last card - remaining cards not reordered
   - Delete multiple cards - only deletions, no reorders

2. **`generateDeletionOob` tests** (may already exist)
   - Verify correct HTML output
   - Verify card ID targeting

3. **`generateChangesResponse` with deletions**
   - Verify deletions generate correct OOB swap
   - Verify remaining cards not affected

### Test Cases

```scala
test("detectChanges: deletion does not cause reorders"):
  val oldIds = List("IW-100", "IW-150", "IW-200")
  val newIds = List("IW-100", "IW-200")  // IW-150 deleted

  val changes = WorktreeListSync.detectChanges(oldIds, newIds)

  assertEquals(changes.additions, List.empty)
  assertEquals(changes.deletions, List("IW-150"))
  assertEquals(changes.reorders, List.empty)

test("detectChanges: delete first card does not reorder remaining"):
  val oldIds = List("IW-100", "IW-150", "IW-200")
  val newIds = List("IW-150", "IW-200")  // IW-100 deleted

  val changes = WorktreeListSync.detectChanges(oldIds, newIds)

  assertEquals(changes.deletions, List("IW-100"))
  assertEquals(changes.reorders, List.empty)

test("detectChanges: delete last card does not reorder remaining"):
  val oldIds = List("IW-100", "IW-150", "IW-200")
  val newIds = List("IW-100", "IW-150")  // IW-200 deleted

  val changes = WorktreeListSync.detectChanges(oldIds, newIds)

  assertEquals(changes.deletions, List("IW-200"))
  assertEquals(changes.reorders, List.empty)

test("detectChanges: multiple deletions do not cause reorders"):
  val oldIds = List("IW-100", "IW-150", "IW-200", "IW-250")
  val newIds = List("IW-100", "IW-250")  // IW-150 and IW-200 deleted

  val changes = WorktreeListSync.detectChanges(oldIds, newIds)

  assertEquals(changes.deletions.toSet, Set("IW-150", "IW-200"))
  assertEquals(changes.reorders, List.empty)
```

### Manual Verification

1. Start dashboard with worktrees IW-100, IW-150, IW-200
2. Delete worktree directory for IW-150 from filesystem
3. Wait for auto-prune (next refresh)
4. Verify card IW-150 disappears
5. Verify IW-100 stays at position 1, IW-200 shifts to position 2

## Acceptance Criteria

1. **Deleted worktree cards disappear correctly**
   - Card removed from DOM via OOB delete
   - No visual artifacts or errors

2. **Remaining cards maintain sort order**
   - Cards stay in alphabetical Issue ID order
   - No unexpected reordering after deletion

3. **No reorders triggered by deletion**
   - `detectChanges` returns empty `reorders` list when only deletions occur

4. **Multiple deletions work correctly**
   - All deleted cards removed
   - Remaining cards maintain relative order

5. **All existing tests pass**
   - No regressions in addition or reorder behavior
   - Deletion tests verify expected behavior

## Implementation Notes

### HTMX OOB Delete Reference

HTMX `hx-swap-oob="delete"` removes the element from the DOM entirely. The element must have an `id` attribute matching the card being deleted.

```html
<!-- This removes the element with id="card-IW-150" -->
<div id="card-IW-150" hx-swap-oob="delete"></div>
```

### No Repositioning Needed

Unlike additions (which need positional insertion), deletions don't require repositioning:
- Card is removed by ID
- Remaining cards naturally shift up in the DOM
- Browser handles the visual shift automatically

This is simpler than additions because we don't need to calculate where to insert.

### Edge Cases

1. **Delete only card**: List becomes empty - `#worktree-list` shows empty state
2. **Delete all cards**: Multiple OOB deletes, list becomes empty
3. **Delete during addition**: Both operations should work independently

### Backward Compatibility

- No API changes
- No JSON schema changes
- Same OOB delete mechanism (already working)

---

## Task Checklist

- [ ] Review existing deletion tests in `WorktreeListSyncTest.scala`
- [ ] Add tests verifying deletion doesn't trigger reorders
- [ ] Add edge case tests (delete first, last, multiple)
- [ ] Run all tests to verify no regressions
- [ ] Manual verification: delete worktree and observe dashboard behavior
