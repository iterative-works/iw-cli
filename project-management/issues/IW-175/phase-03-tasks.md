# Phase 3 Tasks: Removed worktrees shift remaining cards predictably

**Issue:** IW-175
**Phase:** 3 - Removed worktrees shift remaining cards predictably
**Estimated Time:** 1-2 hours

## Overview

This phase verifies that existing deletion behavior works correctly with stable Issue ID sorting. The key verification points are:

1. Deletions do NOT trigger reorders in `detectChanges`
2. Cards shift naturally when deleted (no repositioning needed)
3. Remaining cards maintain alphabetical Issue ID order

---

## Setup

- [x] Read the existing `WorktreeListSync.scala` implementation
- [x] Read the existing `WorktreeListSyncTest.scala` tests
- [x] Review existing deletion tests

---

## Tests - detectChanges deletion behavior

- [x] **Test: Deletion does not cause reorders**
  - Input: oldIds = ["IW-100", "IW-150", "IW-200"], newIds = ["IW-100", "IW-200"]
  - Expected: deletions = ["IW-150"], reorders = []

- [x] **Test: Delete first card does not reorder remaining**
  - Input: oldIds = ["IW-100", "IW-150", "IW-200"], newIds = ["IW-150", "IW-200"]
  - Expected: deletions = ["IW-100"], reorders = []

- [x] **Test: Delete last card does not reorder remaining**
  - Input: oldIds = ["IW-100", "IW-150", "IW-200"], newIds = ["IW-100", "IW-150"]
  - Expected: deletions = ["IW-200"], reorders = []

- [x] **Test: Multiple deletions do not cause reorders**
  - Input: oldIds = ["IW-100", "IW-150", "IW-200", "IW-250"], newIds = ["IW-100", "IW-250"]
  - Expected: deletions = ["IW-150", "IW-200"], reorders = []

---

## Tests - generateChangesResponse with deletions

- [x] **Test: generateChangesResponse produces deletion OOB for removed cards**
  - Verify deletions generate `hx-swap-oob="delete"` HTML
  - Verify correct card ID targeting (`id="card-{issueId}"`)

- [x] **Test: generateChangesResponse handles mixed additions and deletions**
  - Input: add IW-175, delete IW-150
  - Verify both addition and deletion OOB swaps present
  - Verify no reorders

---

## Verification - Existing deletion logic

- [x] **Review `generateDeletionOob` function**
  - Verify it produces correct HTML: `<div id="card-{issueId}" hx-swap-oob="delete"></div>`
  - No changes needed (already correct)

- [x] **Review existing deletion test**
  - Verify `generateDeletionOob includes hx-swap-oob delete attribute` test exists
  - No changes needed if comprehensive

---

## Integration

- [x] **Run all unit tests to verify no regressions**
  - `./iw test unit`
  - Ensure all existing tests still pass

- [x] **Verify addition behavior unchanged**
  - Run Phase 2 tests - should all pass
  - No changes to addition logic

- [x] **Verify reorder behavior unchanged**
  - Review `generateReorderOob` - should not need changes

---

## Verification

- [ ] **Manual test: Delete worktree that should cause cards to shift up**
  - Start dashboard with worktrees IW-100, IW-150, IW-200
  - Delete worktree directory for IW-150 from filesystem
  - Wait for auto-prune (next refresh)
  - Verify card IW-150 disappears
  - Verify IW-100 stays at position 1, IW-200 shifts to position 2

NOTE: Manual verification should be performed by the user after Phase 3 implementation is complete.

---

## Summary

| Category | Tasks |
|----------|-------|
| Setup | 3 |
| Tests - detectChanges | 4 |
| Tests - generateChangesResponse | 2 |
| Verification - Existing | 2 |
| Integration | 3 |
| Manual Verification | 1 |
| **Total** | **15** |

**Phase Status:** Complete
