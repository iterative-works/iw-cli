# Review Packet: Phase 3 - Removed worktrees shift remaining cards predictably

**Issue:** IW-175
**Phase:** 3
**Branch:** IW-175-phase-03

## Goals

Verify that existing deletion behavior works correctly with stable Issue ID sorting implemented in Phases 1-2. This is primarily a verification phase.

**Acceptance Criteria:**
- Deletion does NOT trigger reorders in `detectChanges`
- Cards shift naturally when deleted (no repositioning needed)
- Remaining cards maintain alphabetical Issue ID order

## Scenarios Verified

- [x] Delete middle card - no reorders, remaining cards shift naturally
- [x] Delete first card - no reorders, remaining cards shift up
- [x] Delete last card - no reorders
- [x] Delete multiple cards - no reorders
- [x] Mixed additions and deletions - both work correctly together

## Entry Points

**This phase adds only tests - no implementation changes.**

Start reviewing at:
- `.iw/core/test/WorktreeListSyncTest.scala` - 6 new tests added at end of file (lines 416-512)

## Test Summary

**New tests added (6):**
1. `detectChanges: delete first card does not reorder remaining`
2. `detectChanges: delete last card does not reorder remaining`
3. `detectChanges: multiple deletions do not cause reorders`
4. `detectChanges: delete all cards except one`
5. `generateChangesResponse: deletion generates correct OOB swap`
6. `generateChangesResponse: mixed additions and deletions`

**Existing tests unchanged:**
- All Phase 1 and Phase 2 tests pass
- Existing deletion test (`detectChanges identifies removed worktrees as deletions`) still passes

## Architecture Notes

No implementation changes required. The existing deletion logic in `WorktreeListSync`:
- `generateDeletionOob` generates correct `hx-swap-oob="delete"` HTML
- `detectChanges` correctly identifies deletions without triggering reorders
- HTMX handles DOM removal automatically, remaining cards shift naturally

## Files Changed

| File | Type | Description |
|------|------|-------------|
| `.iw/core/test/WorktreeListSyncTest.scala` | Test | Added 6 tests for deletion behavior |

## Summary

Phase 3 is a verification phase that confirms the existing deletion logic works correctly with stable Issue ID sorting. No implementation changes were needed - the tests verify that:

1. Deletions are detected correctly
2. Deletions do NOT trigger reorders
3. OOB delete swap is generated correctly
4. Mixed additions and deletions work together

The existing implementation was already correct, so this phase primarily adds test coverage to document and verify the expected behavior.
