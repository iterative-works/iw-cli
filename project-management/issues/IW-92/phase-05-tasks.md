# Phase 5 Tasks: Visible-items-first optimization

**Issue:** IW-92
**Phase:** 5 of 5
**Story:** Story 5 - Visible-items-first optimization (stretch goal)
**Goal:** Priority-based refresh ordering for many worktrees

## Task Groups

### Setup

- [x] [impl] [ ] [reviewed] Review `WorktreeRegistration` for activity timestamp field
- [x] [impl] [ ] [reviewed] Review `DashboardService.renderDashboard()` for worktree ordering
- [x] [impl] [ ] [reviewed] Review `WorktreeListView` for HTMX trigger attributes

### Tests - Priority Score

- [x] [impl] [ ] [reviewed] Test recent activity gets higher priority score
- [x] [impl] [ ] [reviewed] Test older activity gets lower priority score
- [x] [impl] [ ] [reviewed] Test priority score is deterministic (same input = same output)
- [x] [impl] [ ] [reviewed] Test priority handles missing activity timestamp gracefully

### Implementation - Priority Score

- [x] [impl] [ ] [reviewed] Create `WorktreePriority.scala` with pure priority calculation
- [x] [impl] [ ] [reviewed] Implement `priorityScore(registration, now)` function
- [x] [impl] [ ] [reviewed] Score based on time since last activity (more recent = higher)

### Tests - Sorted Rendering

- [x] [impl] [ ] [reviewed] Test dashboard sorts worktrees by priority (highest first)
- [x] [impl] [ ] [reviewed] Test sort is stable for equal priorities (preserves registration order)

### Implementation - Sorted Rendering

- [x] [impl] [ ] [reviewed] Modify `DashboardService.renderDashboard()` to sort by priority
- [x] [impl] [ ] [reviewed] Pass current time (`Instant.now()`) for priority calculation
- [x] [impl] [ ] [reviewed] Ensure sort doesn't affect worktree registration data

### Tests - Staggered Polling

- [x] [impl] [ ] [reviewed] Test first 3 cards have `delay:500ms` in hx-trigger
- [x] [impl] [ ] [reviewed] Test cards 4-8 have `delay:2s` in hx-trigger
- [x] [impl] [ ] [reviewed] Test remaining cards have `delay:5s` in hx-trigger

### Implementation - Staggered Polling

- [x] [impl] [ ] [reviewed] Modify `WorktreeListView.renderCard()` to accept position parameter
- [x] [impl] [ ] [reviewed] Calculate delay based on card position (500ms/2s/5s tiers)
- [x] [impl] [ ] [reviewed] Update HTMX `hx-trigger` attribute with position-based delay

### Integration

- [ ] [integration] Manual test: Dashboard with 10 worktrees shows priority ordering
- [ ] [integration] Manual test: First 3 cards refresh within 1 second
- [ ] [integration] Manual test: Network requests are staggered (no burst)
- [ ] [integration] Manual test: Dashboard feels faster with many worktrees

## Acceptance Criteria Checklist

- [ ] Worktrees sorted by last activity (most recent first)
- [ ] Top 3 worktrees refresh within 1 second of page load
- [ ] Remaining worktrees refresh within 5 seconds
- [ ] User sees useful data quickly even with many worktrees
- [ ] No race conditions or ordering bugs
- [ ] Sorting is stable and deterministic

## Notes

- This phase is an optimization - dashboard works without it
- Priority based on activity timestamp, not user preference
- Staggered delays are approximate - HTMX handles exact timing
- Position-based delay is a proxy for "above the fold" visibility
- Key insight: spreading requests improves perceived and actual performance

**Phase Status:** Not Started
