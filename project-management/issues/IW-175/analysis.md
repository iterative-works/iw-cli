# Story-Driven Analysis: Dashboard cards jump around during refresh causing misclicks

**Issue:** IW-175
**Created:** 2026-01-27
**Status:** Draft
**Classification:** Feature

## Problem Statement

Users experience frustrating misclicks when the dashboard auto-refreshes every 30 seconds. The worktree cards reorder unpredictably based on the `lastSeenAt` timestamp, which changes as users work in worktrees. This creates a classic "layout shift" UX problem:

1. User scans the dashboard looking for a specific issue card
2. User moves cursor to click on the desired card
3. Dashboard refreshes (30-second interval), cards reorder based on changed activity timestamps
4. User clicks on the wrong card because positions shifted

This makes the dashboard feel unreliable and frustrating to use, especially when users are trying to quickly navigate between multiple worktrees.

**Current Technical Behavior:**
- Cards are sorted by `WorktreePriority.priorityScore(wt, now)` which calculates `-secondsSinceActivity`
- The `lastSeenAt` field changes when worktree is accessed, causing priority scores to change
- Both initial render (`DashboardService.renderDashboard`) and incremental updates (`/api/worktrees/changes`) use `state.listByActivity` which sorts by `lastSeenAt`
- HTMX refreshes every 30s, re-rendering cards in the new order

## User Stories

### Story 1: Stable card positions during auto-refresh

```gherkin
Feature: Dashboard cards maintain stable positions
  As a developer using multiple worktrees
  I want cards to stay in the same position during auto-refresh
  So that I can click on the card I intended without misclicks

Scenario: Cards remain in place when no worktrees are added or removed
  Given I have 5 registered worktrees on the dashboard
  And the cards are displayed in alphabetical order by issue ID
  And I note the position of card "IW-175" as position 3
  When the dashboard auto-refreshes after 30 seconds
  And no worktrees were added or removed
  Then card "IW-175" is still at position 3
  And all other cards remain in their original positions
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward to implement. Requires changing the default sort key from `lastSeenAt` (dynamic) to `issueId` (stable). The challenge is choosing the right stable sort key that provides predictable ordering.

Key decisions:
- Which stable attribute to sort by (issue ID, registration timestamp, path)
- Whether to support sorting direction (ascending/descending)

**Acceptance:**
- Cards maintain positions during auto-refresh when set is unchanged
- Card order is predictable and consistent across refreshes
- No layout shift during normal operation

---

### Story 2: New worktrees appear at predictable location

```gherkin
Feature: New worktrees have predictable placement
  As a developer creating new worktrees
  I want new cards to appear at a predictable location
  So that I know where to look for newly created worktrees

Scenario: New worktree card appears based on alphabetical ordering
  Given I have worktrees "IW-100", "IW-150", "IW-200" on the dashboard
  And cards are sorted alphabetically by issue ID
  When I create a new worktree for issue "IW-175"
  And the dashboard adds the new card
  Then the new card "IW-175" appears between "IW-150" and "IW-200"
  And existing cards "IW-100", "IW-150", "IW-200" shift predictably to accommodate
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Already handled by HTMX OOB (out-of-band) swap mechanism in `WorktreeListSync`. Once stable sort is implemented in Story 1, new cards will naturally insert at the correct position based on the sort key.

Needs verification that OOB insertion respects the stable sort order.

**Acceptance:**
- New worktree cards appear at position dictated by stable sort key
- User can predict where new card will appear based on its issue ID
- Existing cards shift smoothly to accommodate new card

---

### Story 3: Removed worktrees shift remaining cards predictably

```gherkin
Feature: Removing worktrees causes predictable card shifts
  As a developer cleaning up old worktrees
  I want remaining cards to shift predictably when a worktree is removed
  So that I can anticipate the new layout

Scenario: Removing a worktree causes cards below to shift up
  Given I have worktrees "IW-100", "IW-150", "IW-175", "IW-200" on the dashboard
  And cards are sorted alphabetically by issue ID
  When worktree "IW-150" is removed from the filesystem
  And the dashboard auto-prunes the removed worktree
  Then card "IW-150" disappears from the dashboard
  And cards "IW-175" and "IW-200" shift up to fill the gap
  And card "IW-100" remains at the top
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
Auto-pruning already exists in `CaskServer.initialize()`. With stable sort from Story 1, removal will naturally cause remaining cards to shift predictably. This story primarily validates existing behavior works correctly with stable sorting.

**Acceptance:**
- Removed worktree cards disappear smoothly
- Remaining cards shift to fill gap predictably
- No unexpected reordering of remaining cards

---

### Story 4: User can choose sort order preference (DEFERRED)

> **Status:** Deferred to future issue. Focus on Stories 1-3 first.

This story would add a sort selector UI allowing users to choose between:
- Issue ID (A-Z / Z-A)
- Registration date (newest/oldest first)
- Path (A-Z)

Deferred because:
- Stories 1-3 solve the core misclick problem
- Activity sorting removed (current `lastSeenAt` doesn't reflect actual user activity)
- Can add sort selector later if users request it

---

## Architectural Sketch

### For Story 1: Stable card positions during auto-refresh

**Domain Layer:**
- `WorktreeSortKey` enum/ADT (value object representing sort options: ByIssueId, ByRegisteredAt, ByPath)
- `WorktreeSorter` object with pure sorting functions for different keys

**Application Layer:**
- `DashboardService.renderDashboard` - replace `WorktreePriority.priorityScore` with stable sort key
- `ServerState.listByActivity` - rename/generalize to accept sort key parameter

**Infrastructure Layer:**
- No changes required for Story 1 (uses existing data)

**Presentation Layer:**
- No UI changes for Story 1 (just changes internal ordering)

---

### For Story 2: New worktrees appear at predictable location

**Domain Layer:**
- Same `WorktreeSorter` from Story 1 ensures correct insertion position

**Application Layer:**
- Verify `WorktreeListSync.detectChanges` correctly handles insertion order
- Ensure `WorktreeListSync.generateChangesResponse` places new cards at right position

**Infrastructure Layer:**
- No changes (reuses existing OOB swap mechanism)

**Presentation Layer:**
- No changes (HTMX OOB already handles insertion)

---

### For Story 3: Removed worktrees shift remaining cards predictably

**Domain Layer:**
- No domain changes (removal logic exists)

**Application Layer:**
- Verify `CaskServer.pruneWorktrees` works correctly with stable sort
- Ensure `WorktreeListSync` handles removals predictably

**Infrastructure Layer:**
- No changes (reuses existing pruning)

**Presentation Layer:**
- No changes (HTMX swap handles removal)

---

### For Story 4: User can choose sort order preference (DEFERRED)

Story 4 is deferred to a future issue. No architectural work needed now.

---

## Technical Risks & Uncertainties

### RESOLVED: Default stable sort key

**Decision:** Alphabetical by Issue ID (ascending)

Chosen because it's easy to predict and easy to find specific issues. Users can scan alphabetically to locate a card.

---

### RESOLVED: Activity sorting availability

**Decision:** Remove activity-based sorting entirely (for now)

The current `lastSeenAt` timestamp doesn't accurately reflect user activity - it may just reflect when the dashboard polled the worktree. Removing it simplifies implementation and eliminates the root cause of misclicks.

Can revisit in a future issue if we implement proper activity tracking.

---

### Low Risk: HTMX OOB behavior with stable sorting

With stable Issue ID sorting, the HTMX OOB (out-of-band) swap mechanism should work correctly:
- New cards insert at the position dictated by their issue ID
- Removed cards leave a gap that remaining cards fill

**Mitigation:** Integration tests will verify OOB behavior works as expected.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Stable card positions): 4-6 hours
- Story 2 (New worktrees predictable): 2-3 hours
- Story 3 (Removed worktrees predictable): 1-2 hours
- ~~Story 4 (User-selectable sort order): DEFERRED~~

**Total Range:** 7-11 hours

**Confidence:** High

**Reasoning:**
- Stories 1-3 are straightforward changes to existing sort logic
- No external API dependencies (all changes are internal)
- Existing HTMX patterns are well-established in codebase
- Risk: HTMX OOB behavior with reordering might have edge cases (low probability)

---

## Testing Approach

### Per Story Testing

**Story 1: Stable card positions**
- **Unit**: `WorktreeSorter` pure functions (sort by issue ID, by timestamp, by path)
  - Test alphabetical ordering (IW-1, IW-10, IW-100, IW-2 should order correctly)
  - Test numeric ordering within same prefix (IW-1, IW-2, IW-10, IW-100)
  - Test mixed prefixes (IW-100, GH-50, LINEAR-25)
- **Integration**: `ServerState.listByActivity` replacement uses correct sort key
- **E2E**:
  - Load dashboard with 5 worktrees, note card positions
  - Wait 30+ seconds for auto-refresh
  - Verify card positions unchanged

**Story 2: New worktrees predictable**
- **Unit**: `WorktreeListSync.detectChanges` correctly identifies insertions with sort order
- **Integration**: New worktree inserted at correct position in sorted list
- **E2E**:
  - Open dashboard with existing worktrees
  - Create new worktree via modal
  - Verify new card appears at expected position (based on issue ID)
  - Verify OOB swap places it correctly without full page reload

**Story 3: Removed worktrees predictable**
- **Unit**: `ServerState.removeWorktree` maintains sort invariant
- **Integration**: Auto-pruning removes worktree and preserves order
- **E2E**:
  - Open dashboard with 5 worktrees
  - Delete one worktree directory from filesystem
  - Wait for auto-prune (next refresh)
  - Verify card removed and remaining cards in correct positions

**Story 4: DEFERRED** - No testing needed for this issue.

### Test Data Strategy

**Fixtures:**
- Create test worktrees with known issue IDs (IW-50, IW-100, IW-150, IW-200, IW-250)
- Use fixed timestamps for `registeredAt` and `lastSeenAt` to ensure deterministic sorting
- Mock current time (`Instant.now()`) to control relative time calculations

**Factories:**
- `TestWorktree.create(issueId, registeredAt, lastSeenAt)` helper
- `TestServerState.withWorktrees(List[WorktreeRegistration])` helper

### Regression Coverage

**Potentially affected features:**
- Card refresh endpoint (`/worktrees/:issueId/card`) - should still work with new sort
- HTMX OOB additions - should respect new sort order
- Skeleton card loading - should work with stable positions
- Auto-pruning - should maintain sort invariant

**Regression tests:**
- Existing card refresh tests in `WorktreeCardServiceTest.scala`
- Verify no change in card content/structure (only order changes)
- Verify HTMX attributes remain correct
- Verify caches still populate correctly

---

## Deployment Considerations

### Database Changes
No database changes (uses existing in-memory `ServerState` and JSON file persistence).

### Configuration Changes
None required for Stories 1-3.

### Rollout Strategy

Single deployment:
- Deploy stable Issue ID sorting as default
- Users immediately benefit from stable positions
- Low risk (only changes internal sort key)
- No feature flag needed

### Rollback Plan

**If stable sorting causes issues:**
1. Revert `DashboardService.scala` sort logic to use `WorktreePriority.priorityScore`
2. No data migration needed (no persistent state changes)

---

## Dependencies

### Prerequisites
- Existing HTMX integration (already present)
- Existing OOB swap mechanism (`WorktreeListSync`)
- Existing server state management (`ServerStateService`)

### Story Dependencies
- **Story 2 depends on Story 1**: Need stable sort before testing insertion positions
- **Story 3 depends on Story 1**: Need stable sort before testing removal behavior

**Parallelization:**
- Stories 1-3 must be sequential (each builds on the previous)

### External Blockers
- None (all changes are internal to dashboard module)

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Stable card positions** - Establishes foundation, fixes core misclick issue immediately
2. **Story 2: New worktrees predictable** - Validates stable sort works for additions
3. **Story 3: Removed worktrees predictable** - Validates stable sort works for removals

**Iteration Plan:**

- **Iteration 1** (Stories 1-2): Core stable sorting + new worktree insertion (6-9h)
  - Delivers immediate value: fixes misclick problem
  - Users can start benefiting right away

- **Iteration 2** (Story 3): Validate removal behavior (1-2h)
  - Low-risk validation of existing pruning logic
  - Ensures no regressions from Story 1 changes

---

## Documentation Requirements

- [x] Gherkin scenarios serve as living documentation
- [ ] Update dashboard documentation (if exists) to mention sort behavior
- [ ] No API documentation changes (internal implementation detail)
- [ ] No migration guide needed (backward compatible)

---

**Analysis Status:** Ready for Implementation

**CLARIFY Resolutions:**
- **Default sort key:** Alphabetical by Issue ID (ascending) - DECIDED
- **Activity sorting:** Remove entirely for now (current `lastSeenAt` doesn't accurately reflect user activity) - DECIDED
- **Story 4 (sort selector UI):** Deferred to future issue - DECIDED

**Simplified Scope:**
Stories 1-3 only. Total estimate: 7-11 hours.

**Next Steps:**
1. Run `/iterative-works:ag-create-tasks IW-175` to map stories to implementation phases
2. Run `/iterative-works:ag-implement IW-175` for iterative story-by-story implementation

---

**Key Decisions Summary:**

| Decision Point | Decision | Rationale |
|---------------|----------|-----------|
| Default sort key | Alphabetical by Issue ID (ascending) | Easy to predict, easy to find specific issues |
| Activity sorting | Removed (not available as option) | Current `lastSeenAt` doesn't reflect actual work; simplifies implementation |
| Story 4 (sort selector) | Deferred | Focus on fixing the core problem first; add flexibility later if needed |
| Stagger loading | Keep current behavior | Works fine with stable sorting |
