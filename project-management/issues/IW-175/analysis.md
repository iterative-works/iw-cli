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

### Story 4: User can choose sort order preference

```gherkin
Feature: User-selectable sort order for worktree cards
  As a developer with different workflows
  I want to choose how cards are sorted
  So that I can organize my worktrees based on my current needs

Scenario: User switches to "most recent activity" sorting
  Given I have worktrees sorted alphabetically by issue ID
  And worktree "IW-200" was accessed most recently
  And worktree "IW-100" was accessed least recently
  When I select "Most Recent Activity" from the sort dropdown
  Then cards reorder with "IW-200" at the top
  And "IW-100" at the bottom
  And the sort preference persists across page reloads

Scenario: User switches back to alphabetical sorting
  Given I have worktrees sorted by most recent activity
  When I select "Alphabetical (A-Z)" from the sort dropdown
  Then cards reorder alphabetically by issue ID
  And the sort preference persists across page reloads
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity. Requires:
- UI component for sort selector (dropdown or toggle buttons)
- Client-side preference storage (localStorage or cookie)
- Server-side sort implementation for multiple keys
- HTMX integration to re-render list when sort changes

### CLARIFY: User preference storage mechanism

**Questions to answer:**
1. Should sort preference be per-project (stored in `.iw/config.yaml`) or per-user globally?
2. Should we store preference client-side (localStorage) or server-side (config file)?
3. Should preference be synced across multiple browser tabs/windows?

**Options:**
- **Option A**: Client-side localStorage (browser-specific, simple)
  - Pros: No server changes, fast, works immediately
  - Cons: Doesn't sync across browsers/devices, lost on browser clear
- **Option B**: Server-side in `~/.local/share/iw/server/config.json` (user-global)
  - Pros: Syncs across browsers, persists reliably
  - Cons: Requires server state changes, more complex
- **Option C**: Per-project in `.iw/config.yaml` (project-specific)
  - Pros: Team-shareable default, project-appropriate sorting
  - Cons: Doesn't respect individual user preference, requires git commits

**Impact:** Affects implementation complexity and user expectations for preference persistence. Story 4 blocked until this is decided.

**Acceptance:**
- User can select from multiple sort options (alphabetical, recent activity, creation date)
- Dashboard reorders cards immediately when sort changes
- Sort preference persists across dashboard reloads
- Currently selected sort option is visually indicated

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

### For Story 4: User can choose sort order preference

**Domain Layer:**
- `SortPreference` value object (selected sort key + direction)
- Validation logic for sort preference values

**Application Layer:**
- `PreferenceService` (load/save user sort preference)
- Update `DashboardService.renderDashboard` to accept sort preference parameter
- Update `ServerState.listBy` to support multiple sort keys

**Infrastructure Layer:**
- `PreferenceRepository` (read/write sort preference to storage)
- Storage adapter (localStorage via JavaScript or config file)

**Presentation Layer:**
- Sort selector UI component (dropdown or button group)
- HTMX endpoint to change sort: `POST /api/preferences/sort`
- JavaScript to persist preference to localStorage (if client-side)
- Response re-renders entire worktree list with new order

---

## Technical Risks & Uncertainties

### CLARIFY: Which stable sort key should be the default?

**Questions to answer:**
1. Which attribute provides the most intuitive default ordering?
2. Should we default to alphabetical issue ID or chronological creation order?
3. Do users expect oldest-first or newest-first for creation timestamp?

**Options:**
- **Option A**: Alphabetical by issue ID (ascending: IW-100, IW-150, IW-175, IW-200)
  - Pros: Easy to predict, easy to find specific issue, matches file system conventions
  - Cons: Doesn't reflect work priority or recency
- **Option B**: Chronological by registration timestamp (newest first)
  - Pros: Most recently created worktrees at top (likely current work)
  - Cons: Less predictable, harder to find specific issue
- **Option C**: Alphabetical by path (ascending)
  - Pros: Matches filesystem listing
  - Cons: Paths might be complex, issue ID is clearer identifier

**Impact:** Affects initial user experience and whether "most users" need to change sort preference. Blocking Story 1.

---

### CLARIFY: Should "most recent activity" sorting be available at all?

The current behavior (sorting by `lastSeenAt`) causes the misclick problem. If we add "most recent activity" as an optional sort mode (Story 4), should we:

**Questions to answer:**
1. Is "most recent activity" valuable enough to keep as an option?
2. If yes, should it animate transitions to reduce misclick frustration?
3. Should we throttle activity updates to reduce reorder frequency?

**Options:**
- **Option A**: Remove activity-based sorting entirely
  - Pros: Simplifies implementation, eliminates root cause of misclicks
  - Cons: Loses potentially useful "recently worked on" signal
- **Option B**: Keep as optional sort mode with animated transitions
  - Pros: Preserves flexibility, animations make reorders visible
  - Cons: Still causes layout shifts (just more visible), complex to implement
- **Option C**: Keep as optional sort mode with throttled updates (e.g., update activity rank every 5 minutes)
  - Pros: Reduces reorder frequency, preserves some recency signal
  - Cons: Activity ranking becomes stale, still shifts occasionally

**Impact:** Affects scope of Story 4. If we choose Option A, Story 4 becomes simpler (just choosing between stable keys). Story 4 blocked until decided.

---

### CLARIFY: How should HTMX handle reordering when sort changes?

When user changes sort preference (Story 4), we need to reorder existing cards.

**Questions to answer:**
1. Should we swap entire list or use HTMX morphing/transitions?
2. Do we need CSS animations to show cards moving to new positions?
3. Should we use HTMX extensions (e.g., `hx-swap="morph"` with idiomorph)?

**Options:**
- **Option A**: Full list swap (replace entire `#worktree-list`)
  - Pros: Simple, reliable, already supported
  - Cons: Jarring, cards disappear and reappear, no motion indication
- **Option B**: CSS transitions with staggered swap
  - Pros: Smoother, shows movement, less jarring
  - Cons: Complex timing, might still feel abrupt
- **Option C**: HTMX morphing with idiomorph extension
  - Pros: Intelligently morphs DOM, smooth transitions, preserves state
  - Cons: Requires additional library, more complex to debug

**Impact:** Affects UX quality of sort changes. Doesn't block Story 4 implementation (can start with Option A and upgrade later).

---

### CLARIFY: Interaction with existing staggered loading

Currently, skeleton cards use staggered delays (500ms for top 3, 2s for 4-8, 5s for 9+) based on **position**.

**Questions to answer:**
1. After reordering, do stagger delays re-calculate based on new positions?
2. Should skeleton cards always load in stable order regardless of final sort?
3. Does staggering still make sense with stable sorting?

**Options:**
- **Option A**: Keep staggered loading based on stable sort position
  - Pros: Top cards (by stable sort) load first, consistent
  - Cons: Might not match user's current priority
- **Option B**: Remove staggered loading entirely
  - Pros: Simpler, all cards treated equally
  - Cons: Loses performance optimization for high-priority cards
- **Option C**: Make stagger strategy configurable per sort mode
  - Pros: Most flexible, can optimize per use case
  - Cons: Very complex, probably over-engineered

**Impact:** Affects loading performance and UX. Doesn't block Stories 1-3 (can keep current behavior). Story 4 might need adjustment.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Stable card positions): 4-6 hours
- Story 2 (New worktrees predictable): 2-3 hours
- Story 3 (Removed worktrees predictable): 1-2 hours
- Story 4 (User-selectable sort order): 6-8 hours

**Total Range:** 13-19 hours

**Confidence:** Medium

**Reasoning:**
- Stories 1-3 have high confidence (straightforward changes to existing sort logic)
- Story 4 has medium confidence (depends on CLARIFY decisions around preference storage and UI complexity)
- No external API dependencies (all changes are internal)
- Existing HTMX patterns are well-established in codebase
- Risk: HTMX OOB behavior with reordering might have edge cases we haven't encountered

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

**Story 4: User sort preference**
- **Unit**:
  - `SortPreference` validation (valid enum values)
  - `PreferenceService` load/save logic
- **Integration**:
  - Dashboard loads with user's saved preference
  - Changing preference re-sorts list correctly
- **E2E**:
  - Select "Alphabetical" sort, verify cards alphabetical
  - Reload page, verify still alphabetical
  - Select "Newest First" sort, verify cards reorder
  - Reload page, verify still newest-first
  - Open in new tab, verify preference respected (if server-side storage)

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

**Story 4 migrations (if server-side preference storage chosen):**
- Add `sortPreference` field to `~/.local/share/iw/server/config.json` schema
- Default to "issueId" if not present (backward compatible)

### Configuration Changes

**Story 4 configuration (if server-side storage):**
- Update server config schema to include `defaultSortKey` and `defaultSortDirection`
- Example:
  ```json
  {
    "host": "0.0.0.0",
    "port": 8080,
    "sortPreference": {
      "key": "issueId",
      "direction": "ascending"
    }
  }
  ```

### Rollout Strategy

**Incremental deployment:**
1. **Phase 1 (Stories 1-3)**: Deploy stable sorting as default, no user controls
   - Users immediately benefit from stable positions
   - Low risk (only changes internal sort key)
   - Can deploy without feature flag
2. **Phase 2 (Story 4)**: Add sort selector UI after gathering feedback on default
   - Deploy behind feature flag if desired
   - Can A/B test default sort key before exposing controls

**Feature flags:**
- Not strictly necessary for Stories 1-3 (low-risk change)
- Consider for Story 4: `enableSortSelector` flag in server config

### Rollback Plan

**If stable sorting causes issues:**
1. Revert `DashboardService.scala` sort logic to use `WorktreePriority.priorityScore`
2. Revert `ServerState.listByActivity` to original implementation
3. No data migration needed (no persistent state changes)

**If sort preference UI causes issues (Story 4):**
1. Remove sort selector from UI (comment out in template)
2. Server continues using default stable sort
3. Clear any corrupt preference values from config/localStorage

---

## Dependencies

### Prerequisites
- Existing HTMX integration (already present)
- Existing OOB swap mechanism (`WorktreeListSync`)
- Existing server state management (`ServerStateService`)

### Story Dependencies
- **Story 2 depends on Story 1**: Need stable sort before testing insertion positions
- **Story 3 depends on Story 1**: Need stable sort before testing removal behavior
- **Story 4 depends on Stories 1-3**: Need stable sorting implemented before adding sort selector

**Parallelization:**
- Stories 1-3 must be sequential
- Story 4 can be developed in parallel after Story 1 merges (as long as we use Story 1's sorting infrastructure)

### External Blockers
- None (all changes are internal to dashboard module)

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Stable card positions** - Establishes foundation, fixes core misclick issue immediately
2. **Story 2: New worktrees predictable** - Validates stable sort works for additions
3. **Story 3: Removed worktrees predictable** - Validates stable sort works for removals
4. **Story 4: User sort preference** - Adds flexibility after stable default is proven

**Iteration Plan:**

- **Iteration 1** (Stories 1-2): Core stable sorting + new worktree insertion (6-9h)
  - Delivers immediate value: fixes misclick problem
  - Users can start benefiting right away
  - Can gather feedback on default sort choice

- **Iteration 2** (Story 3): Validate removal behavior (1-2h)
  - Low-risk validation of existing pruning logic
  - Ensures no regressions from Story 1 changes

- **Iteration 3** (Story 4): Add sort selector (6-8h)
  - Blocked on CLARIFY decisions
  - Can be deprioritized if default stable sort satisfies users
  - Nice-to-have after core issue resolved

---

## Documentation Requirements

- [x] Gherkin scenarios serve as living documentation
- [ ] Update dashboard documentation (if exists) to mention sort behavior
- [ ] If Story 4 implemented: Document sort preference options and storage location
- [ ] If Story 4 implemented: Document how to set default sort in config
- [ ] No API documentation changes (internal implementation detail)
- [ ] No migration guide needed (backward compatible)

---

**Analysis Status:** Ready for Review (pending CLARIFY resolution)

**Next Steps:**
1. **CLARIFY decisions needed before task generation:**
   - Default stable sort key (Issue ID vs Registration Timestamp vs Path)
   - Whether to keep "most recent activity" sorting as option in Story 4
   - User preference storage mechanism (localStorage vs server config vs project config)

2. **After CLARIFY resolution:**
   - Run `/iterative-works:ag-create-tasks IW-175` to map stories to implementation phases

3. **Implementation:**
   - Run `/iterative-works:ag-implement IW-175` for iterative story-by-story implementation
   - Start with Stories 1-2 to deliver immediate value
   - Gather user feedback on default sort before investing in Story 4

---

**Key Decisions Summary:**

| Decision Point | Recommendation | Confidence |
|---------------|---------------|------------|
| Default sort key | Alphabetical by Issue ID (ascending) | Medium - needs user validation |
| Keep activity sorting | Yes, as optional mode in Story 4 | Low - needs discussion |
| Preference storage | Client-side localStorage | Medium - simple MVP |
| HTMX reorder strategy | Full list swap (Option A) | High - simplest, upgrade later |
| Stagger loading | Keep current behavior | High - don't change what works |
