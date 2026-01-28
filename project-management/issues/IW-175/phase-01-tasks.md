# Phase 1 Tasks: Stable card positions during auto-refresh

**Issue:** IW-175
**Phase:** 1 - Stable card positions during auto-refresh
**Estimated Time:** 4-6 hours

## Overview

Replace activity-based sorting (`lastSeenAt`) with Issue ID-based sorting to eliminate the misclick problem caused by cards reordering unpredictably during auto-refresh.

## Key Changes

1. Replace `listByActivity` with `listByIssueId` in `ServerState`
2. Update `DashboardService.renderDashboard` to sort by Issue ID
3. Update `CaskServer` to use new sorting (2 locations: lines 43 and 187)
4. Update tests to verify new behavior

---

## Setup

- [ ] Read current implementation of `ServerState.listByActivity` to understand sorting
- [ ] Read current implementation of `DashboardService.renderDashboard` line 42

---

## Tests - ServerState

- [ ] **Modify test:** Change `ServerStateTest` test name from "listByActivity returns worktrees sorted by lastSeenAt descending" to "listByIssueId returns worktrees sorted by issueId ascending"
- [ ] **Modify test:** Update test to use `listByIssueId` method name and verify alphabetical ordering by issueId
- [ ] **Modify test:** Update expected order in test (IWLE-1, IWLE-2, IWLE-3 alphabetically)
- [ ] **Modify test:** Update empty state test to use `listByIssueId`
- [ ] **Modify test:** Update single worktree test to use `listByIssueId`

---

## Tests - Issue ID Sorting Edge Cases

- [ ] **Add test:** Test alphabetical ordering with different prefixes (GH-50 < IW-100 < LINEAR-25)
- [ ] **Add test:** Test pure string sorting behavior (IW-1 < IW-10 < IW-100 < IW-2 for alphabetical)

---

## Implementation - ServerState

- [ ] **Rename method:** Change `listByActivity` to `listByIssueId` in `ServerState.scala`
- [ ] **Update implementation:** Change `sortBy(_.lastSeenAt.getEpochSecond)(Ordering[Long].reverse)` to `sortBy(_.issueId)`
- [ ] **Update PURPOSE comment:** Change line 2 from "sorted by activity" to "sorted by issue ID"

---

## Implementation - DashboardService

- [ ] **Update sorting:** Change line 42 from `sortBy(wt => WorktreePriority.priorityScore(wt, now))(Ordering[Long].reverse)` to `sortBy(_.issueId)`
- [ ] **Remove unused import:** Remove `WorktreePriority` from imports if no longer used elsewhere

---

## Implementation - CaskServer

- [ ] **Update dashboard route:** Change line 43 from `state.listByActivity` to `state.listByIssueId`
- [ ] **Update changes endpoint:** Change line 187 from `state.listByActivity` to `state.listByIssueId`

---

## Verification

- [ ] **Run unit tests:** Execute `./iw test unit` and verify all tests pass
- [ ] **Run E2E tests:** Execute `./iw test e2e` and verify no regressions
- [ ] **Manual test:** Start dashboard, observe card order is alphabetical by Issue ID
- [ ] **Manual test:** Wait for auto-refresh (30s), verify cards do not reorder

---

## Files Modified

| File | Line(s) | Change |
|------|---------|--------|
| `.iw/core/model/ServerState.scala` | 2, 13-14 | Rename method, change sort key |
| `.iw/core/dashboard/DashboardService.scala` | 42 | Change sort to issueId |
| `.iw/core/dashboard/CaskServer.scala` | 43, 187 | Use `listByIssueId` |
| `.iw/core/test/ServerStateTest.scala` | 25-72, 85 | Update tests for new method |

---

## Notes

- **Simple alphabetical sorting:** We're using pure string sorting (`sortBy(_.issueId)`), which means "IW-10" comes before "IW-2". This is acceptable for Phase 1 and can be improved to natural numeric sorting in a follow-up if users find it confusing.
- **WorktreePriority:** Keep `WorktreePriority` for potential future use (staggered loading priority). Just don't use it for card ordering.
- **No backward compatibility needed:** `listByActivity` is only used internally.
