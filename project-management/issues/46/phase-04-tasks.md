# Phase 4 Tasks: Review Status and Phase Display

**Issue:** #46
**Phase:** 4 of 6
**Story:** Story 3 - Review state indicates phase and status
**Estimated Effort:** 3-4 hours

## Task Breakdown

### Setup

- [ ] Review existing WorktreeListView review section code (lines 128-144)
- [ ] Review existing DashboardService CSS structure for styling patterns
- [ ] Identify test data fixtures needed for WorktreeListViewTest

### Tests - Helper Functions

- [ ] Write test for `formatStatusLabel()` with "awaiting_review" → "Awaiting Review"
- [ ] Write test for `formatStatusLabel()` with "in_progress" → "In Progress"
- [ ] Write test for `formatStatusLabel()` with "completed" → "Completed"
- [ ] Write test for `statusBadgeClass()` with "awaiting_review" → "review-status-awaiting-review"
- [ ] Write test for `statusBadgeClass()` with "in_progress" → "review-status-in-progress"
- [ ] Write test for `statusBadgeClass()` with "completed" → "review-status-completed"
- [ ] Write test for `statusBadgeClass()` with unknown status → "review-status-default"

### Implementation - Helper Functions

- [ ] Implement `formatStatusLabel(status: String): String` in WorktreeListView
- [ ] Implement `statusBadgeClass(status: String): String` in WorktreeListView
- [ ] Run tests and verify helper functions pass

### Tests - Status Badge Rendering

- [ ] Write test: status badge appears when ReviewState.status is defined
- [ ] Write test: status badge has correct CSS class for "awaiting_review"
- [ ] Write test: status badge has correct CSS class for "in_progress"
- [ ] Write test: status badge has correct CSS class for "completed"
- [ ] Write test: no status badge when ReviewState.status is None

### Tests - Phase Number Display

- [ ] Write test: phase number "Phase 8" appears when ReviewState.phase = Some(8)
- [ ] Write test: phase number has CSS class "review-phase"
- [ ] Write test: no phase number when ReviewState.phase is None
- [ ] Write test: phase 0 displays as "Phase 0"

### Tests - Message Display

- [ ] Write test: message appears when ReviewState.message is defined
- [ ] Write test: message has CSS class "review-message"
- [ ] Write test: no message when ReviewState.message is None
- [ ] Write test: empty string message does not render

### Tests - Combined Rendering

- [ ] Write test: all fields (status, phase, message) render together correctly
- [ ] Write test: missing all optional fields still renders artifacts section
- [ ] Write test: partial fields (only status) renders correctly

### Implementation - Review Section Extension

- [ ] Extend review section header to include phase number (lines 128-144)
- [ ] Add status badge rendering after header
- [ ] Add message rendering after status badge
- [ ] Verify compilation succeeds
- [ ] Run unit tests and verify all pass

### Implementation - CSS Styling

- [ ] Add CSS for `.review-phase` (gray text, smaller font)
- [ ] Add CSS for `.review-status` (badge base styling)
- [ ] Add CSS for `.review-status-label` (white text)
- [ ] Add CSS for `.review-status-awaiting-review` (green background)
- [ ] Add CSS for `.review-status-in-progress` (yellow background, dark text)
- [ ] Add CSS for `.review-status-completed` (gray background)
- [ ] Add CSS for `.review-status-default` (blue background)
- [ ] Add CSS for `.review-message` (bordered info box)

### Integration Testing

- [ ] Create test review-state.json with all fields (status, phase, message, artifacts)
- [ ] Start server with `./iw server` and verify dashboard loads
- [ ] Verify status badge displays with correct color (green for "awaiting_review")
- [ ] Verify phase number displays as "Phase 8"
- [ ] Verify message displays below status badge
- [ ] Test with status = "in_progress" → verify yellow badge
- [ ] Test with status = "completed" → verify gray badge
- [ ] Test with missing status field → verify no badge, no error
- [ ] Test with missing phase field → verify no phase number, no error
- [ ] Test with missing message field → verify no message, no error

### Code Review and Refinement

- [ ] Review for code duplication (none expected)
- [ ] Verify helper functions are pure and testable
- [ ] Verify CSS follows existing dashboard conventions
- [ ] Run all tests: `./iw test`
- [ ] Verify no compilation warnings
- [ ] Check that existing dashboard tests still pass

## Success Criteria

**Helper Functions:**
- [x] `formatStatusLabel()` converts underscore-separated values to Title Case
- [x] `statusBadgeClass()` maps status values to CSS classes
- [x] Unknown status values map to "review-status-default"

**Rendering:**
- [x] Status badge displays when status field present
- [x] Phase number displays when phase field present
- [x] Message displays when message field present
- [x] Missing fields degrade gracefully (no errors, no empty elements)
- [x] All three fields can be displayed together

**Visual Design:**
- [x] "awaiting_review" → green badge
- [x] "in_progress" → yellow badge
- [x] "completed" → gray badge
- [x] Unknown status → blue badge
- [x] Phase number in header, smaller font
- [x] Message in bordered info box

**Quality:**
- [x] All unit tests pass
- [x] No compilation errors or warnings
- [x] Visual appearance verified in browser
- [x] CSS consistent with existing dashboard styles

## Notes

**Scalatags Conditional Rendering Pattern:**
```scala
// Render only if Option is defined
state.status.map { statusValue =>
  div(cls := "review-status", statusValue)
}
// Returns Option[Frag] - Scalatags handles automatically
```

**Status Normalization:**
```scala
// Handle variations: "awaiting_review", "awaiting-review"
status.toLowerCase.replace(" ", "-") match
  case "awaiting_review" | "awaiting-review" => "review-status-awaiting-review"
```

**Test Data Location:**
Use existing ReviewState from Phase 1:
```scala
val state = ReviewState(
  status = Some("awaiting_review"),
  phase = Some(8),
  message = Some("Ready for review"),
  artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
)
```
