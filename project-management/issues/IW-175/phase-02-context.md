# Phase 2: New worktrees appear at predictable location

**Issue:** IW-175
**Story:** 2 - New worktrees appear at predictable location
**Estimated Effort:** 2-3 hours

## Goals

Ensure that when a new worktree is created, its card appears at the correct sorted position in the dashboard (based on Issue ID) rather than simply appending to the end. This makes the dashboard behavior predictable: users can anticipate where a new card will appear based on its Issue ID.

**Primary Outcome:** New worktree cards insert at the position dictated by alphabetical Issue ID sorting, not at the end of the list.

## Scope

### In Scope

1. **Update `WorktreeListSync.generateAdditionOob` to support positional insertion**
   - Accept target position or adjacent card ID for insertion
   - Generate correct HTMX OOB swap attribute (`afterbegin`, `afterend:#card-XXX`, etc.)

2. **Update `WorktreeListSync.generateChangesResponse` to calculate correct position**
   - For each new card, determine where it belongs in the sorted list
   - Pass position information to `generateAdditionOob`

3. **Add tests for positional insertion**
   - Unit tests for correct OOB attribute generation
   - Unit tests for position calculation logic

4. **Verify existing card content remains unchanged**
   - Same card rendering, same HTMX polling behavior
   - Only the OOB insertion mechanism changes

### Out of Scope

- Story 1 (Stable card positions) - already completed in Phase 1
- Story 3 (Removed worktrees predictable) - handled in Phase 3
- Story 4 (User-selectable sort order) - deferred to future issue
- Full page refresh behavior (already works correctly via Phase 1)
- Any changes to card appearance or content

## Dependencies

### Prerequisites (must exist from Phase 1)

- `ServerState.listByIssueId` - provides stable Issue ID ordering (implemented in Phase 1)
- `WorktreeListSync.detectChanges` - identifies additions (exists)
- `WorktreeListSync.generateAdditionOob` - generates OOB HTML (exists, needs modification)
- Cards have stable IDs (`id="card-{issueId}"`) for targeting

### Phase 1 Deliverables Used

From the implementation log, Phase 1 delivered:
- `ServerState.listByIssueId` - sorts worktrees alphabetically by Issue ID
- Both dashboard endpoints (`/` and `/api/worktrees/changes`) now use Issue ID sorting
- Simple alphabetical string sorting (IW-1 < IW-10 < IW-2)

This phase builds on that foundation by ensuring OOB insertions respect the same sort order.

## Technical Approach

### Current Behavior (Problem)

`WorktreeListSync.generateAdditionOob` currently always appends to the end:

```scala
div(
  attr("hx-swap-oob") := "beforeend:#worktree-list",
  cardHtml
).render
```

This means a new card for `IW-150` would appear at the end of the list, even if cards for `IW-100` and `IW-200` exist. The card only moves to its correct position on full page refresh.

### Target Behavior (Solution)

New cards should insert at their sorted position:
- If adding `IW-150` to a list with `IW-100, IW-200`, the card should appear AFTER `IW-100` and BEFORE `IW-200`

### Implementation Strategy

**Option A: Insert after specific card using `afterend`**

HTMX supports inserting after a specific element:
```html
<div hx-swap-oob="afterend:#card-IW-100">...new card...</div>
```

This inserts the new card immediately after `#card-IW-100`.

**Option B: Delete and re-render affected range**

More complex, involves removing multiple cards and re-inserting them in order.

**Recommendation:** Option A is simpler and more efficient. We just need to find the card that should precede the new one.

### Algorithm for Finding Insert Position

Given a new card with Issue ID `newId` and existing sorted list `[IW-100, IW-200, IW-300]`:

1. Find the largest existing ID that is less than `newId` (alphabetically)
2. If found, insert `afterend` that card
3. If no such ID exists (new card should be first), insert `afterbegin:#worktree-list`

Example:
- Adding `IW-150` to `[IW-100, IW-200, IW-300]`:
  - `IW-100` < `IW-150` < `IW-200`
  - Insert `afterend:#card-IW-100`

- Adding `IW-050` to `[IW-100, IW-200, IW-300]`:
  - `IW-050` < `IW-100` (no predecessor)
  - Insert `afterbegin:#worktree-list`

### Code Changes

**1. Update `generateAdditionOob` signature**

```scala
def generateAdditionOob(
  registration: WorktreeRegistration,
  issueData: Option[CachedIssue],
  progress: Option[CachedProgress],
  prData: Option[CachedPR],
  reviewState: Option[CachedReviewState],
  now: Instant,
  sshHost: String,
  predecessorId: Option[String]  // NEW: ID of card to insert after, or None for first position
): String
```

**2. Update OOB swap attribute generation**

```scala
val oobTarget = predecessorId match
  case Some(predId) => s"afterend:#card-$predId"
  case None => "afterbegin:#worktree-list"

div(
  attr("hx-swap-oob") := oobTarget,
  cardHtml
).render
```

**3. Update `generateChangesResponse` to calculate predecessor**

```scala
// For each addition, find the predecessor in the current sorted list
val additionsHtml = changes.additions.flatMap { issueId =>
  registrations.get(issueId).map { reg =>
    // Find predecessor: largest existing ID less than this one
    val predecessorId = findPredecessor(issueId, currentIds)
    generateAdditionOob(
      reg,
      issueCache.get(issueId),
      progressCache.get(issueId),
      prCache.get(issueId),
      reviewStateCache.get(issueId),
      now,
      sshHost,
      predecessorId
    )
  }
}
```

**4. Add helper function for finding predecessor**

```scala
/** Find the ID that should precede the given ID in sorted order.
  *
  * @param newId ID being inserted
  * @param existingIds Current list of IDs (must be sorted)
  * @return ID that should precede newId, or None if newId should be first
  */
def findPredecessor(newId: String, existingIds: List[String]): Option[String] =
  existingIds.filter(_ < newId).lastOption
```

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `.iw/core/dashboard/WorktreeListSync.scala` | Modify | Add `predecessorId` parameter to `generateAdditionOob`, update OOB attribute generation, add `findPredecessor` helper, update `generateChangesResponse` |
| `.iw/core/test/WorktreeListSyncTest.scala` | Modify | Add tests for positional insertion |

### No New Files Required

All changes fit within existing files.

## Testing Strategy

### Unit Tests

1. **`findPredecessor` tests**
   - Empty list returns `None`
   - New ID is smallest returns `None`
   - New ID in middle returns correct predecessor
   - New ID is largest returns last existing ID

2. **`generateAdditionOob` tests**
   - With `predecessorId = None` generates `afterbegin:#worktree-list`
   - With `predecessorId = Some("IW-100")` generates `afterend:#card-IW-100`

3. **`generateChangesResponse` integration**
   - Adding single card at beginning
   - Adding single card in middle
   - Adding single card at end
   - Adding multiple cards maintains sorted order

### Test Cases

```scala
test("findPredecessor returns None for empty list"):
  assertEquals(findPredecessor("IW-100", List.empty), None)

test("findPredecessor returns None when new ID should be first"):
  assertEquals(findPredecessor("IW-050", List("IW-100", "IW-200")), None)

test("findPredecessor returns correct predecessor for middle insertion"):
  assertEquals(findPredecessor("IW-150", List("IW-100", "IW-200")), Some("IW-100"))

test("findPredecessor returns last ID when new ID should be last"):
  assertEquals(findPredecessor("IW-300", List("IW-100", "IW-200")), Some("IW-200"))

test("generateAdditionOob with no predecessor uses afterbegin"):
  val html = generateAdditionOob(..., predecessorId = None)
  assert(html.contains("hx-swap-oob=\"afterbegin:#worktree-list\""))

test("generateAdditionOob with predecessor uses afterend"):
  val html = generateAdditionOob(..., predecessorId = Some("IW-100"))
  assert(html.contains("hx-swap-oob=\"afterend:#card-IW-100\""))
```

### Manual Verification

1. Start dashboard with worktrees `IW-100`, `IW-200`, `IW-300`
2. Create new worktree for `IW-150`
3. Verify card appears between `IW-100` and `IW-200` (without full page refresh)
4. Create new worktree for `IW-050`
5. Verify card appears at the top of the list

### Regression Coverage

- Existing card refresh behavior unchanged
- Deletion OOB behavior unchanged
- Reorder OOB behavior unchanged
- Full page refresh still shows correct order

## Acceptance Criteria

1. **New worktree cards appear at correct sorted position**
   - Card for `IW-150` appears between `IW-100` and `IW-200`
   - Card for `IW-050` appears at the top (before `IW-100`)
   - Card for `IW-350` appears at the bottom

2. **No full page refresh required**
   - Cards insert via HTMX OOB swap
   - Existing cards shift to accommodate new card

3. **Predictable insertion**
   - User can predict where new card will appear based on Issue ID
   - Insertion respects alphabetical Issue ID ordering (same as Phase 1)

4. **All existing tests pass**
   - No regressions in deletion or reorder behavior
   - Card content and polling behavior unchanged

5. **New tests cover insertion logic**
   - `findPredecessor` helper tested
   - OOB attribute generation tested for all positions

## Implementation Notes

### HTMX OOB Swap Reference

HTMX supports several OOB swap strategies:
- `hx-swap-oob="true"` - swap by matching ID
- `hx-swap-oob="afterbegin:#target"` - insert at beginning of target
- `hx-swap-oob="beforeend:#target"` - insert at end of target
- `hx-swap-oob="afterend:#target"` - insert after target element
- `hx-swap-oob="beforebegin:#target"` - insert before target element

For positional insertion, `afterend:#card-{predecessorId}` is the most natural choice.

### Edge Cases

1. **First card in empty list**: Use `afterbegin:#worktree-list`
2. **Card should be first**: Use `afterbegin:#worktree-list`
3. **Card should be last**: Use `afterend:#card-{lastId}` (same as middle case)
4. **Multiple additions in same response**: Each gets its own predecessor calculation

### Alphabetical Sorting Reminder

Phase 1 uses simple alphabetical sorting, so:
- `IW-1` < `IW-10` < `IW-100` < `IW-2`

This is intentional per Phase 1 decision. Natural numeric sorting can be added later if needed.

### Backward Compatibility

- No external API changes
- No JSON schema changes
- Same card rendering
- Only OOB swap targeting changes

### Rollback Plan

If issues arise, revert `generateAdditionOob` to use `beforeend:#worktree-list` unconditionally.

---

## Task Checklist

- [ ] Add `findPredecessor` helper function to `WorktreeListSync`
- [ ] Write unit tests for `findPredecessor`
- [ ] Update `generateAdditionOob` to accept `predecessorId` parameter
- [ ] Update `generateAdditionOob` to generate correct OOB swap attribute
- [ ] Update `generateChangesResponse` to calculate predecessor for each addition
- [ ] Add tests for OOB attribute generation
- [ ] Run all tests to verify no regressions
- [ ] Manual verification: create worktrees and verify correct insertion positions
