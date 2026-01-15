# Phase 5 Context: Visible-items-first optimization

**Issue:** IW-92
**Phase:** 5 of 5
**Story:** Story 5 - Visible-items-first optimization (stretch goal)

## User Story

```gherkin
Feature: Prioritize visible worktrees
  As a user viewing the dashboard on mobile
  I want the first few cards to load fastest
  So that I see useful data immediately

Scenario: First 3 worktrees refresh before others
  Given I have 10 registered worktrees
  When the dashboard loads
  Then the first 3 cards refresh data first
  And remaining 7 cards refresh after the first 3
  And I see "Loading..." on cards below the fold
  And priority is based on last activity (most recent first)
```

## Goals

This phase optimizes refresh ordering so users with many worktrees see useful data faster:

1. Sort worktrees by last activity timestamp (most recent first)
2. Implement priority-based refresh (first N worktrees refresh before rest)
3. Optimize for "above the fold" experience (visible cards load first)
4. Scale gracefully with 10+ worktrees

## Scope

### In Scope

- Priority score calculation for worktrees (based on last activity)
- Sorting worktrees by priority before rendering
- Staggered HTMX polling (high-priority cards poll sooner)
- "Recent" badge or indicator for active worktrees

### Out of Scope (Would Be Future Work)

- Viewport detection (JavaScript to detect which cards are visible)
- Infinite scroll / lazy loading
- Configurable priority thresholds

## Dependencies

### From Previous Phases

**Phase 1:**
- `getCachedOnly()` methods for non-blocking cache access
- Stale indicators in cards

**Phase 2:**
- `CacheConfig` for TTLs
- Stale cache preservation on API failure

**Phase 3:**
- HTMX polling per card (`hx-get`, `hx-trigger`, `hx-swap`)
- `/worktrees/:issueId/card` endpoint
- `RefreshThrottle` for 30-second rate limiting
- `TimestampFormatter` for "Updated X ago" display

**Phase 4:**
- CSS transitions for smooth updates
- Tab visibility refresh
- Mobile-friendly styling

### External Dependencies

- HTMX library (already included, v1.9.10)
- Existing worktree registration with activity timestamps

## Technical Approach

### Current Behavior (After Phase 4)

```
Worktrees rendered in registration order
All cards poll at same interval (every 30s)
No priority differentiation
Works but suboptimal for many worktrees
```

### Target Behavior (Phase 5)

```
Worktrees sorted by last activity (most recent first)
High-priority cards poll with shorter initial delay
Low-priority cards poll later (staggered start)
User sees most relevant worktrees refresh first
```

### Key Components

1. **Priority Score Calculation**
   - Pure function: `priorityScore(worktree: WorktreeRegistration, now: Instant): Int`
   - Based on last activity timestamp (git commit, file change, etc.)
   - Higher score = higher priority

2. **Sorted Rendering**
   - `DashboardService.renderDashboard()` sorts worktrees before rendering
   - Most recently active worktrees appear first in list
   - Affects both initial render order and visual position

3. **Staggered Polling Delays**
   - Top 3 worktrees: `hx-trigger="load delay:500ms, every 30s"`
   - Next 5 worktrees: `hx-trigger="load delay:2s, every 30s"`
   - Remaining: `hx-trigger="load delay:5s, every 30s"`
   - Reduces initial API load, prioritizes visible cards

4. **Priority Indicator (Optional)**
   - Visual badge showing "Recent activity" or similar
   - Helps user understand why worktrees are ordered this way

## Files to Modify

### Domain Changes

| File | Change |
|------|--------|
| `.iw/core/WorktreeRegistration.scala` | Add `lastActivityTimestamp` field (if not present) |
| `.iw/core/WorktreePriority.scala` (new) | Pure priority score calculation |

### Application Changes

| File | Change |
|------|--------|
| `.iw/core/DashboardService.scala` | Sort worktrees by priority before rendering |

### Presentation Changes

| File | Change |
|------|--------|
| `.iw/core/WorktreeListView.scala` | Staggered `hx-trigger` delays based on position |

### Test Files

| File | Purpose |
|------|---------|
| `.iw/core/test/WorktreePriorityTest.scala` (new) | Test priority score calculation |
| `.iw/core/test/DashboardServiceTest.scala` | Test sorted rendering |
| `.iw/core/test/WorktreeListViewTest.scala` | Test staggered polling delays |

## Testing Strategy

### Unit Tests

1. **Priority score calculation**
   - Verify recent activity gets higher score
   - Verify older activity gets lower score
   - Verify ties are handled deterministically

2. **Sorted rendering**
   - Verify worktrees sorted by priority score
   - Verify sort is stable (preserves registration order for ties)

3. **Staggered polling**
   - Verify top 3 cards have shortest delay
   - Verify next 5 cards have medium delay
   - Verify remaining cards have longest delay

### Manual Verification

1. **Priority ordering**
   - Register 10 worktrees with different activity levels
   - Load dashboard, verify most active appear first
   - Verify refresh happens in priority order

2. **Performance**
   - With 10+ worktrees, verify first cards refresh quickly
   - Verify API calls are spread out over time
   - Verify no performance degradation

## Acceptance Criteria

- [ ] Worktrees sorted by last activity (most recent first)
- [ ] Top 3 worktrees refresh within 1 second of page load
- [ ] Remaining worktrees refresh within 5 seconds
- [ ] User sees useful data quickly even with many worktrees
- [ ] No race conditions or ordering bugs
- [ ] Sorting is stable and deterministic

## Notes

- This is a stretch goal optimization - core functionality works without it
- Priority is based on activity, not user preference (simpler to implement)
- Viewport detection (JavaScript-based) is out of scope - use position as proxy
- Keep staggered delays reasonable (don't make low-priority cards wait too long)
- Key insight: position in list correlates with visibility (top = likely visible)

## Success Metrics

- First 3 cards show fresh data within 1 second
- Dashboard with 10 worktrees feels as fast as dashboard with 3
- API rate limiting still effective (no burst of concurrent requests)
- User perceives dashboard as fast even with many worktrees
