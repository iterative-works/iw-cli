# Phase 2 Tasks: New worktrees appear at predictable location

**Issue:** IW-175
**Phase:** 2 - New worktrees appear at predictable location
**Estimated Time:** 2-3 hours

## Overview

This phase ensures that when a new worktree is created, its card appears at the correct sorted position (by Issue ID) rather than appending to the end. The key changes are:

1. Add `findPredecessor` helper function
2. Update `generateAdditionOob` to accept `predecessorId` parameter
3. Update `generateChangesResponse` to calculate predecessor for each addition

---

## Setup

- [ ] Read the existing `WorktreeListSync.scala` implementation
- [ ] Read the existing `WorktreeListSyncTest.scala` tests

---

## Tests - findPredecessor helper

- [ ] **Test: `findPredecessor` returns `None` for empty list**
  - Input: newId = "IW-100", existingIds = List.empty
  - Expected: None

- [ ] **Test: `findPredecessor` returns `None` when new ID should be first**
  - Input: newId = "IW-050", existingIds = List("IW-100", "IW-200")
  - Expected: None (IW-050 < IW-100, no predecessor)

- [ ] **Test: `findPredecessor` returns correct predecessor for middle insertion**
  - Input: newId = "IW-150", existingIds = List("IW-100", "IW-200", "IW-300")
  - Expected: Some("IW-100")

- [ ] **Test: `findPredecessor` returns last ID when new ID should be last**
  - Input: newId = "IW-400", existingIds = List("IW-100", "IW-200", "IW-300")
  - Expected: Some("IW-300")

---

## Implementation - findPredecessor helper

- [ ] **Implement `findPredecessor` function in `WorktreeListSync`**
  - Pure function: `def findPredecessor(newId: String, existingIds: List[String]): Option[String]`
  - Returns the largest existing ID that is less than newId (alphabetically)
  - Returns None if newId should be first

---

## Tests - generateAdditionOob with predecessorId

- [ ] **Test: `generateAdditionOob` with `predecessorId = None` uses `afterbegin`**
  - Verify HTML contains `hx-swap-oob="afterbegin:#worktree-list"`

- [ ] **Test: `generateAdditionOob` with `predecessorId = Some("IW-100")` uses `afterend`**
  - Verify HTML contains `hx-swap-oob="afterend:#card-IW-100"`

---

## Implementation - generateAdditionOob signature change

- [ ] **Update `generateAdditionOob` to accept `predecessorId` parameter**
  - Add parameter: `predecessorId: Option[String]`
  - Change OOB swap attribute from hardcoded `beforeend:#worktree-list` to:
    - `afterbegin:#worktree-list` when predecessorId is None
    - `afterend:#card-{predecessorId}` when predecessorId is Some

- [ ] **Update existing test for `generateAdditionOob`**
  - The existing test expects `beforeend:#worktree-list`, update it to pass `None` for predecessorId and expect `afterbegin:#worktree-list`

---

## Tests - generateChangesResponse with predecessor calculation

- [ ] **Test: Addition inserts at beginning when ID is smallest**
  - existingIds: ["IW-100", "IW-200"]
  - addition: "IW-050"
  - Verify OOB swap uses `afterbegin:#worktree-list`

- [ ] **Test: Addition inserts in middle at correct position**
  - existingIds: ["IW-100", "IW-200", "IW-300"]
  - addition: "IW-150"
  - Verify OOB swap uses `afterend:#card-IW-100`

- [ ] **Test: Addition inserts at end when ID is largest**
  - existingIds: ["IW-100", "IW-200"]
  - addition: "IW-300"
  - Verify OOB swap uses `afterend:#card-IW-200`

- [ ] **Test: Multiple additions each get correct predecessor**
  - existingIds: ["IW-100", "IW-300"]
  - additions: ["IW-050", "IW-200"]
  - Verify IW-050 uses `afterbegin:#worktree-list`
  - Verify IW-200 uses `afterend:#card-IW-100`

---

## Implementation - generateChangesResponse predecessor calculation

- [ ] **Update `generateChangesResponse` to calculate predecessor for each addition**
  - For each addition, determine the predecessor from the current sorted list
  - Pass predecessorId to `generateAdditionOob`
  - Note: Need to pass currentIds (sorted list of existing IDs) to this function or compute it internally

- [ ] **Add `currentIds` parameter to `generateChangesResponse`**
  - Need the sorted list of current IDs to calculate predecessors
  - This is the newIds list from `detectChanges`

---

## Integration

- [ ] **Run all unit tests to verify no regressions**
  - `./iw test unit`
  - Ensure all existing tests still pass

- [ ] **Verify existing deletion OOB behavior unchanged**
  - Review `generateDeletionOob` - should not need changes

- [ ] **Verify existing reorder OOB behavior unchanged**
  - Review `generateReorderOob` - should not need changes

---

## Verification

- [ ] **Manual test: Create worktree that should appear at beginning**
  - Start dashboard with worktrees IW-100, IW-200
  - Create worktree for IW-050
  - Verify card appears at top (position 1)

- [ ] **Manual test: Create worktree that should appear in middle**
  - Start dashboard with worktrees IW-100, IW-200, IW-300
  - Create worktree for IW-150
  - Verify card appears between IW-100 and IW-200

- [ ] **Manual test: Create worktree that should appear at end**
  - Start dashboard with worktrees IW-100, IW-200
  - Create worktree for IW-300
  - Verify card appears at bottom

---

## Summary

| Category | Tasks |
|----------|-------|
| Setup | 2 |
| Tests - findPredecessor | 4 |
| Implementation - findPredecessor | 1 |
| Tests - generateAdditionOob | 2 |
| Implementation - generateAdditionOob | 2 |
| Tests - generateChangesResponse | 4 |
| Implementation - generateChangesResponse | 2 |
| Integration | 3 |
| Verification | 3 |
| **Total** | **23** |
