# Phase 4 Tasks: Incremental card updates via HTMX

**Issue:** IW-92
**Phase:** 4 of 5
**Story:** Story 3 - Incremental card updates via HTMX
**Goal:** Smooth per-card updates with CSS transitions and tab visibility refresh

## Task Groups

### Setup

- [x] [impl] [ ] [reviewed] Review existing HTMX attributes in `WorktreeListView.scala`
- [x] [impl] [ ] [reviewed] Review existing CSS styles in `DashboardService.scala`
- [ ] [impl] [ ] [reviewed] Test current card update behavior in browser

### Tests - CSS Transitions

- [x] [impl] [ ] [reviewed] Test dashboard CSS includes `.htmx-swapping` styles
- [x] [impl] [ ] [reviewed] Test dashboard CSS includes `.htmx-settling` styles
- [x] [impl] [ ] [reviewed] Test card has `transition` property in CSS

### Implementation - CSS Transitions

- [x] [impl] [ ] [reviewed] Add `.htmx-swapping { opacity: 0; }` CSS rule
- [x] [impl] [ ] [reviewed] Add `.htmx-settling { opacity: 1; }` CSS rule
- [x] [impl] [ ] [reviewed] Add `transition: opacity 200ms ease-in-out` to card styles
- [x] [impl] [ ] [reviewed] Add `min-height` to card container to prevent layout shift

### Tests - Tab Visibility

- [x] [impl] [ ] [reviewed] Test dashboard HTML includes `visibilitychange` script
- [x] [impl] [ ] [reviewed] Test cards have `refresh from:body` in `hx-trigger`

### Implementation - Tab Visibility

- [x] [impl] [ ] [reviewed] Add `visibilitychange` event listener script to dashboard
- [x] [impl] [ ] [reviewed] Add `refresh from:body` to card `hx-trigger` attribute
- [x] [impl] [ ] [reviewed] Trigger `htmx.trigger(document.body, 'refresh')` on tab focus

### Tests - Mobile Styling

- [x] [impl] [ ] [reviewed] Test card CSS includes mobile breakpoint styles
- [x] [impl] [ ] [reviewed] Test buttons/links have minimum touch target size (44px)

### Implementation - Mobile Styling

- [x] [impl] [ ] [reviewed] Add responsive breakpoint for cards (`@media (max-width: 768px)`)
- [x] [impl] [ ] [reviewed] Ensure buttons have `min-height: 44px` for touch targets
- [x] [impl] [ ] [reviewed] Add `touch-action: manipulation` to prevent zoom on double-tap

### Tests - Layout Stability

- [x] [impl] [ ] [reviewed] Test card container has stable dimensions during swap
- [x] [impl] [ ] [reviewed] Test `hx-swap` includes transition modifier

### Implementation - Layout Stability

- [x] [impl] [ ] [reviewed] Update `hx-swap` to use `outerHTML transition:true`
- [ ] [impl] [ ] [reviewed] Add `scroll:false` to prevent scroll jumping if needed

### Integration

- [ ] [integration] Manual test: Card updates with smooth fade transition
- [ ] [integration] Manual test: Tab switch triggers immediate refresh
- [ ] [integration] Manual test: Dashboard usable on mobile viewport
- [ ] [integration] Manual test: Scroll position preserved during updates

## Acceptance Criteria Checklist

- [ ] Each card updates independently via HTMX swap
- [ ] Updates are smooth and don't cause page jump
- [ ] User can interact with other cards while one updates
- [ ] Cards show "Updated X seconds ago" timestamp
- [ ] Tab switch triggers refresh for visible cards
- [ ] Mobile layout is usable and touch-friendly

## Notes

- This phase is CSS/JavaScript polish, minimal Scala logic changes
- HTMX classes `htmx-swapping` and `htmx-settling` are automatically added during swap
- `transition:true` in hx-swap enables view transitions API
- Keep transitions short (200ms) for perceived performance
- Key insight: visual stability is more important than animation flashiness

**Phase Status:** Not Started

