# Phase 4 Context: Incremental card updates via HTMX

**Issue:** IW-92
**Phase:** 4 of 5
**Story:** Story 3 - Incremental card updates via HTMX

## User Story

```gherkin
Feature: Progressive card updates
  As a user viewing the dashboard
  I want each worktree card to update independently
  So that I see fresh data appear progressively

Scenario: Cards update one by one as data becomes available
  Given the dashboard has loaded with 5 cached worktree cards
  When fresh issue data arrives for worktree "IW-92"
  Then only the "IW-92" card re-renders with fresh data
  And other cards remain unchanged
  And the update is smooth without page flicker
  And the card shows "Updated just now"
```

## Goals

This phase polishes the HTMX integration from Phase 3 with smooth transitions and better UX:

1. CSS transitions for smooth card updates (no visual jump or flicker)
2. Tab visibility refresh (instant update when user returns to dashboard)
3. Mobile-friendly styling and interaction
4. Prevent scroll position disruption during updates

## Scope

### In Scope

- CSS transition classes for card updates (`htmx-swapping`, `htmx-settling`)
- Tab visibility event handler (`visibilitychange` to trigger refresh on tab focus)
- Card height stabilization (prevent layout shift during updates)
- Mobile-responsive card styling improvements
- Smooth opacity/fade transitions during swap

### Out of Scope (Later Phases)

- Priority-based refresh ordering (Phase 5)
- Server-Sent Events (keeping simple with polling)
- Complex loading animations

## Dependencies

### From Previous Phases

**Phase 1:**
- `getCachedOnly()` methods for non-blocking cache access
- Stale indicators in cards
- Skeleton card rendering

**Phase 2:**
- `CacheConfig` for TTLs
- Stale cache preservation on API failure
- Error handling returning stale data

**Phase 3:**
- HTMX attributes on cards (`hx-get`, `hx-trigger`, `hx-swap`)
- `/worktrees/:issueId/card` endpoint for per-card HTML
- `/api/worktrees/:issueId/refresh` endpoint for JSON status
- `RefreshThrottle` for 30-second rate limiting
- `TimestampFormatter` for "Updated X ago" display

### External Dependencies

- HTMX library (already included, v1.9.10)
- Modern CSS transitions (standard browser support)

## Technical Approach

### Current Behavior (After Phase 3)

```
Cards have HTMX attributes for polling
Cards update but may cause visual jump
No special handling for tab visibility
No CSS transitions during swap
```

### Target Behavior (Phase 4)

```
Cards fade out/in smoothly during update
Card height maintained to prevent layout shift
Tab switch triggers immediate refresh
Mobile-friendly touch targets and layout
Scroll position preserved during updates
```

### Key Components

1. **CSS Transitions**
   - Add `.htmx-swapping` styles (fade out)
   - Add `.htmx-settling` styles (fade in)
   - Use `transition: opacity 200ms ease-in-out`
   - Ensure card dimensions don't change during swap

2. **Tab Visibility Handler**
   ```javascript
   document.addEventListener('visibilitychange', () => {
     if (document.visibilityState === 'visible') {
       htmx.trigger(document.body, 'refresh');
     }
   });
   ```
   - Add `hx-trigger="refresh from:body"` to cards

3. **Layout Stability**
   - Set `min-height` on card containers
   - Use `hx-swap="outerHTML transition:true"` for smooth transitions
   - Consider `hx-preserve` for scroll position

4. **Mobile Improvements**
   - Larger touch targets (minimum 44px)
   - Responsive card width
   - Prevent zoom on double-tap

## Files to Modify

### Presentation Changes

| File | Change |
|------|--------|
| `.iw/core/DashboardService.scala` | Add CSS transition styles |
| `.iw/core/WorktreeListView.scala` | Add visibility trigger, mobile styles |

### Test Files

| File | Purpose |
|------|---------|
| `.iw/core/test/WorktreeListViewTest.scala` | Test CSS classes and HTMX attributes |

## Testing Strategy

### Unit Tests

1. **CSS transition classes**
   - Verify `.htmx-swapping` and `.htmx-settling` styles present
   - Verify card has `transition` CSS property

2. **Tab visibility**
   - Verify `visibilitychange` handler script present
   - Verify cards have `refresh from:body` trigger

3. **Mobile styles**
   - Verify responsive breakpoints in CSS
   - Verify minimum touch target sizes

### Manual Verification

1. **Smooth updates**
   - Load dashboard, watch cards update
   - Verify no visual jump or flicker
   - Verify scroll position maintained

2. **Tab visibility**
   - Open dashboard, switch to another tab
   - Wait 30+ seconds, switch back
   - Verify cards refresh immediately

3. **Mobile**
   - Test on mobile viewport
   - Verify cards are touch-friendly
   - Verify layout doesn't break

## Acceptance Criteria

- [ ] Each card updates independently via HTMX swap
- [ ] Updates are smooth and don't cause page jump
- [ ] User can interact with other cards while one updates
- [ ] Cards show "Updated X seconds ago" timestamp (from Phase 3)
- [ ] Tab switch triggers refresh for visible cards
- [ ] Mobile layout is usable and touch-friendly

## Notes

- This phase is primarily CSS and JavaScript polish
- No new Scala business logic needed
- Build on Phase 3's HTMX foundation
- Focus on perceived performance (smooth transitions feel faster)
- Key insight: stable card height prevents layout shift

## Success Metrics

- Cards update with smooth opacity transition
- No visible layout shift during updates
- Tab focus triggers refresh within 500ms
- Dashboard usable on mobile devices

