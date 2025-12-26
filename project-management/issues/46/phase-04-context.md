# Phase 4 Context: Review Status and Phase Display

**Issue:** #46  
**Phase:** 4 of 6  
**Story:** Story 3 - Review state indicates phase and status  
**Estimated Effort:** 3-4 hours  
**Dependencies:** Phase 1 (ReviewState model with status/phase/message fields)

---

## Goals

This phase extends the dashboard's review artifacts section to display status, phase number, and message from review-state.json. The implementation focuses on:

1. **Status badge rendering** with visual indicators (color-coded by status)
2. **Phase number display** when present in review state
3. **Review message display** when present
4. **Graceful degradation** for missing optional fields
5. **Consistent styling** with existing dashboard components

**What success looks like:**
- Status "awaiting_review" → green badge with clear label
- Status "in_progress" → yellow badge indicating work in progress
- Status "completed" → gray badge showing completion
- Phase number shows as "Phase 8" when present
- Message displays below status/phase when present
- Missing fields (status/phase/message) don't break rendering

---

## Scope

### In Scope

1. **Extend WorktreeListView** to render status badge when status field exists
2. **Add phase number display** in review section header
3. **Add message display** below status badge
4. **CSS styling** for status badges with color coding
5. **Unit tests** for status rendering logic
6. **Integration tests** for visual appearance with various status values
7. **Graceful handling** of missing optional fields

### Out of Scope

- Status filtering/sorting in dashboard (future enhancement)
- Editable status (read-only display)
- Status change notifications
- Phase navigation (next/previous)
- Status history tracking
- Custom status values beyond the defined set

### Status Values

**Defined in analysis:**
- `awaiting_review` - Work complete, needs human review
- `in_progress` - Work currently ongoing
- `completed` - Review done, approved

**Implementation note:** These are the primary values. System should handle any string value gracefully (display as-is if not recognized).

---

## Dependencies

### From Phase 1

**Available domain model:**
```scala
// ReviewState.scala lines 16-31
case class ReviewState(
  status: Option[String],    // ← Already available
  phase: Option[Int],        // ← Already available
  message: Option[String],   // ← Already available
  artifacts: List[ReviewArtifact]
)
```

**Current review section rendering:**
```scala
// WorktreeListView.scala lines 128-144
reviewState.filter(_.artifacts.nonEmpty).map { state =>
  div(
    cls := "review-artifacts",
    h4("Review Artifacts"),  // ← Need to add status/phase here
    ul(
      cls := "artifact-list",
      state.artifacts.map { artifact =>
        li(
          a(
            href := s"/worktrees/${worktree.issueId}/artifacts?path=${artifact.path}",
            artifact.label
          )
        )
      }
    )
    // ← Need to add message display here
  )
}
```

### From Phases 2-3

**No direct dependencies**, but:
- ArtifactView could be extended to show status/phase in artifact pages (future enhancement)
- Status badge styling should match artifact link styling

### External Dependencies

**None** - uses existing Scalatags and CSS

---

## Technical Approach

### Architecture Overview

```
ReviewState model (Phase 1)
    ↓
    Contains: status: Option[String]
              phase: Option[Int]
              message: Option[String]
    ↓
WorktreeListView.renderWorktreeCard()
    ↓
    Conditional rendering:
    - If status.isDefined → render status badge
    - If phase.isDefined → render "Phase N" text
    - If message.isDefined → render message paragraph
    ↓
CSS classes map status to colors:
    - .review-status-awaiting-review → green
    - .review-status-in-progress → yellow
    - .review-status-completed → gray
    - .review-status-default → blue (unknown values)
```

### Component Design

#### 1. Extend WorktreeListView (Presentation Layer)

**Purpose:** Add status/phase/message rendering to existing review section

**Location:** `.iw/core/WorktreeListView.scala` (modify existing code)

**Current structure (lines 128-144):**
```scala
reviewState.filter(_.artifacts.nonEmpty).map { state =>
  div(
    cls := "review-artifacts",
    h4("Review Artifacts"),
    ul(
      cls := "artifact-list",
      state.artifacts.map { artifact =>
        li(
          a(
            href := s"/worktrees/${worktree.issueId}/artifacts?path=${artifact.path}",
            artifact.label
          )
        )
      }
    )
  )
}
```

**Updated structure:**
```scala
reviewState.filter(_.artifacts.nonEmpty).map { state =>
  div(
    cls := "review-artifacts",
    // Header with phase number (if available)
    h4(
      "Review Artifacts",
      state.phase.map { phaseNum =>
        span(cls := "review-phase", s" (Phase $phaseNum)")
      }
    ),
    // Status badge (if available)
    state.status.map { statusValue =>
      div(
        cls := s"review-status ${statusBadgeClass(statusValue)}",
        span(cls := "review-status-label", formatStatusLabel(statusValue))
      )
    },
    // Message (if available)
    state.message.map { msg =>
      p(cls := "review-message", msg)
    },
    // Artifacts list (existing)
    ul(
      cls := "artifact-list",
      state.artifacts.map { artifact =>
        li(
          a(
            href := s"/worktrees/${worktree.issueId}/artifacts?path=${artifact.path}",
            artifact.label
          )
        )
      }
    )
  )
}
```

**Helper functions to add:**

```scala
/** Map status value to CSS class for badge styling.
  *
  * @param status Status string from review-state.json
  * @return CSS class name (e.g., "review-status-awaiting-review")
  */
private def statusBadgeClass(status: String): String =
  status.toLowerCase.replace(" ", "-") match
    case "awaiting_review" | "awaiting-review" => "review-status-awaiting-review"
    case "in_progress" | "in-progress" => "review-status-in-progress"
    case "completed" | "complete" => "review-status-completed"
    case other => "review-status-default"

/** Format status value as human-readable label.
  *
  * @param status Status string from review-state.json
  * @return Formatted label (e.g., "Awaiting Review")
  */
private def formatStatusLabel(status: String): String =
  status.toLowerCase.replace("_", " ").split(" ").map(_.capitalize).mkString(" ")
```

**Design decisions:**
- **Phase in header:** Shows alongside "Review Artifacts" to establish context
- **Status badge:** Visually prominent, color-coded for quick scanning
- **Message below status:** Additional context without cluttering header
- **Optional rendering:** Each element (status/phase/message) only shows if defined
- **Normalize status:** Handle variations (underscores, hyphens, spaces) gracefully

#### 2. Add CSS Styling (Presentation Layer)

**Purpose:** Visual styling for status badges and review metadata

**Location:** `.iw/core/DashboardService.scala` (add to existing styles)

**New CSS to add:**

```css
/* Review phase number */
.review-phase {
  font-size: 0.85em;
  color: #666;
  font-weight: normal;
  margin-left: 8px;
}

/* Review status badge */
.review-status {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 0.85em;
  font-weight: 600;
  margin: 8px 0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.review-status-label {
  color: white;
}

/* Status-specific colors */
.review-status-awaiting-review {
  background-color: #28a745;  /* Green - ready for review */
}

.review-status-in-progress {
  background-color: #ffc107;  /* Yellow - work ongoing */
}

.review-status-in-progress .review-status-label {
  color: #333;  /* Dark text on yellow background */
}

.review-status-completed {
  background-color: #6c757d;  /* Gray - finished */
}

.review-status-default {
  background-color: #007bff;  /* Blue - unknown status */
}

/* Review message */
.review-message {
  margin: 8px 0;
  padding: 8px 12px;
  background: #f8f9fa;
  border-left: 3px solid #007bff;
  font-size: 0.9em;
  color: #495057;
  border-radius: 4px;
}
```

**Design decisions:**
- **Badge style:** Pill-shaped, uppercase text for visual prominence
- **Color scheme:**
  - Green (awaiting_review): Positive, actionable
  - Yellow (in_progress): Caution, ongoing work
  - Gray (completed): Neutral, finished state
  - Blue (default): Information, unknown status
- **Accessibility:** High contrast ratios, clear labels
- **Consistency:** Matches existing dashboard styling (same font, spacing)

---

## Files to Modify

### Modified Files (2 files)

1. **`.iw/core/WorktreeListView.scala`**
   - Extend review artifacts section (lines 128-144)
   - Add helper functions: `statusBadgeClass()`, `formatStatusLabel()`
   - Add conditional rendering for status/phase/message
   - Estimated changes: ~30 lines

2. **`.iw/core/DashboardService.scala`**
   - Add CSS styles for review status badges and message
   - Estimated changes: ~40 lines of CSS

### Test Files to Update/Create

3. **`.iw/core/test/WorktreeListViewTest.scala`**
   - Add tests for status badge rendering
   - Add tests for phase number display
   - Add tests for message display
   - Add tests for missing fields graceful degradation
   - Estimated changes: ~50 lines (5-7 new tests)

---

## Testing Strategy

### Unit Tests

**WorktreeListViewTest.scala extensions:**

**Test 1: Status badge rendering**
```scala
test("renderWorktreeCard includes status badge when status is defined"):
  val state = ReviewState(
    status = Some("awaiting_review"),
    phase = None,
    message = None,
    artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
  )
  val html = WorktreeListView.renderWorktreeCard(
    worktree = testWorktree,
    reviewState = Some(state),
    /* ... other params */
  ).render
  
  assert(html.contains("review-status"))
  assert(html.contains("review-status-awaiting-review"))
  assert(html.contains("Awaiting Review"))
```

**Test 2: Different status values**
```scala
test("renderWorktreeCard maps status values to correct CSS classes"):
  val statuses = List(
    ("awaiting_review", "review-status-awaiting-review"),
    ("in_progress", "review-status-in-progress"),
    ("completed", "review-status-completed"),
    ("unknown_status", "review-status-default")
  )
  
  statuses.foreach { case (status, expectedClass) =>
    val state = ReviewState(
      status = Some(status),
      phase = None,
      message = None,
      artifacts = List(ReviewArtifact("Test", "test.md"))
    )
    val html = WorktreeListView.renderWorktreeCard(
      worktree = testWorktree,
      reviewState = Some(state),
      /* ... */
    ).render
    
    assert(html.contains(expectedClass), s"Status $status should have class $expectedClass")
  }
```

**Test 3: Phase number display**
```scala
test("renderWorktreeCard includes phase number when defined"):
  val state = ReviewState(
    status = None,
    phase = Some(8),
    message = None,
    artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
  )
  val html = WorktreeListView.renderWorktreeCard(
    worktree = testWorktree,
    reviewState = Some(state),
    /* ... */
  ).render
  
  assert(html.contains("review-phase"))
  assert(html.contains("Phase 8"))
```

**Test 4: Message display**
```scala
test("renderWorktreeCard includes message when defined"):
  val state = ReviewState(
    status = None,
    phase = None,
    message = Some("Phase 8 complete - Ready for review"),
    artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
  )
  val html = WorktreeListView.renderWorktreeCard(
    worktree = testWorktree,
    reviewState = Some(state),
    /* ... */
  ).render
  
  assert(html.contains("review-message"))
  assert(html.contains("Phase 8 complete - Ready for review"))
```

**Test 5: All fields present**
```scala
test("renderWorktreeCard displays status, phase, and message together"):
  val state = ReviewState(
    status = Some("awaiting_review"),
    phase = Some(8),
    message = Some("Ready for review"),
    artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
  )
  val html = WorktreeListView.renderWorktreeCard(
    worktree = testWorktree,
    reviewState = Some(state),
    /* ... */
  ).render
  
  assert(html.contains("review-status-awaiting-review"))
  assert(html.contains("Phase 8"))
  assert(html.contains("Ready for review"))
```

**Test 6: Missing optional fields**
```scala
test("renderWorktreeCard handles missing status, phase, and message"):
  val state = ReviewState(
    status = None,
    phase = None,
    message = None,
    artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
  )
  val html = WorktreeListView.renderWorktreeCard(
    worktree = testWorktree,
    reviewState = Some(state),
    /* ... */
  ).render
  
  // Should still render artifacts section
  assert(html.contains("Review Artifacts"))
  assert(html.contains("Analysis"))
  
  // Should NOT render status/phase/message elements
  assert(!html.contains("review-status"))
  assert(!html.contains("review-phase"))
  assert(!html.contains("review-message"))
```

**Test 7: Status label formatting**
```scala
test("formatStatusLabel converts status values to readable labels"):
  assertEquals(
    WorktreeListView.formatStatusLabel("awaiting_review"),
    "Awaiting Review"
  )
  assertEquals(
    WorktreeListView.formatStatusLabel("in_progress"),
    "In Progress"
  )
  assertEquals(
    WorktreeListView.formatStatusLabel("completed"),
    "Completed"
  )
```

### Integration Tests

**Manual testing scenarios:**

1. **Create test review-state.json with all fields:**
   ```json
   {
     "status": "awaiting_review",
     "phase": 8,
     "message": "Phase 8 complete - Ready for review",
     "artifacts": [
       {"label": "Analysis", "path": "project-management/issues/46/analysis.md"}
     ]
   }
   ```
   - Start server: `./iw server`
   - Open dashboard: http://localhost:9876
   - Verify: Green "Awaiting Review" badge, "Phase 8" in header, message displays

2. **Test different status values:**
   - Change status to `"in_progress"` → yellow badge
   - Change status to `"completed"` → gray badge
   - Change status to `"unknown"` → blue badge

3. **Test missing fields:**
   - Remove `status` → no badge shows
   - Remove `phase` → no phase number shows
   - Remove `message` → no message paragraph shows
   - Artifacts list still displays correctly

4. **Test edge cases:**
   - Empty message: `"message": ""` → should not render message element
   - Phase 0: `"phase": 0` → should show "Phase 0"
   - Long message: verify wrapping and readability

### Visual Regression

**Compare with existing dashboard:**
- Status badges should match issue status badge styling (if any)
- Spacing should be consistent with other card sections
- Colors should match dashboard color palette
- Font sizes should maintain hierarchy

---

## Acceptance Criteria (from Story 3)

**Gherkin Scenario 1: Review state shows phase number and message**
```gherkin
Scenario: Review state shows phase number and message
  Given a review-state.json file contains:
    """
    status: awaiting_review
    phase: 8
    message: "Phase 8 complete - Ready for review"
    """
  When I load the dashboard
  Then I see "Phase 8" in the review section
  And I see status "awaiting_review"
  And I see the message "Phase 8 complete - Ready for review"
```

**Acceptance checks:**
- [ ] Phase number displays as "Phase 8" in review section header
- [ ] Status "awaiting_review" shows as green badge with "Awaiting Review" label
- [ ] Message "Phase 8 complete - Ready for review" displays below status badge
- [ ] All three elements (status, phase, message) render correctly together

**Gherkin Scenario 2: Different status values display appropriately**
```gherkin
Scenario: Different status values display appropriately
  Given a review state with status "in_progress"
  When I load the dashboard
  Then I see a visual indicator for "in_progress" status
  And the status is distinguishable from "awaiting_review"
```

**Acceptance checks:**
- [ ] "in_progress" status shows yellow badge (distinct from green "awaiting_review")
- [ ] "completed" status shows gray badge
- [ ] Unknown status values show blue badge with formatted label
- [ ] Visual indicators are clearly distinguishable (color + label)

**Additional acceptance criteria:**
- [ ] Missing status field → no badge renders, no error
- [ ] Missing phase field → no phase number renders, no error
- [ ] Missing message field → no message paragraph renders, no error
- [ ] Empty string values handled gracefully (don't render empty elements)
- [ ] Status badges use consistent styling with existing dashboard
- [ ] CSS classes follow naming convention (`.review-status-*`)

---

## Implementation Sequence

**Recommended order:**

1. **Add helper functions to WorktreeListView** (30 min)
   - Add `statusBadgeClass(status: String): String`
   - Add `formatStatusLabel(status: String): String`
   - Test: Helper functions work with various inputs

2. **Extend review section rendering** (45 min)
   - Modify review artifacts section in `renderWorktreeCard()`
   - Add phase number to header
   - Add status badge rendering
   - Add message rendering
   - Test: Manual verification that code compiles

3. **Add CSS styling** (30 min)
   - Add styles to DashboardService
   - Include all status color variants
   - Add review-phase and review-message styles
   - Test: Server starts successfully

4. **Write unit tests** (1.5-2h)
   - Create WorktreeListViewTest extensions
   - Test all scenarios (status variants, phase, message, missing fields)
   - Test helper functions directly
   - Test: All unit tests pass

5. **Manual integration testing** (30-45 min)
   - Create test review-state.json with various combinations
   - Start dashboard server
   - Verify visual appearance
   - Test all status variants
   - Test missing fields
   - Test: Visual appearance matches design

6. **Code review and refinement** (30 min)
   - Review for code duplication
   - Verify graceful degradation
   - Check CSS consistency with existing styles
   - Run all tests: `./iw test`
   - Test: All tests pass, no regressions

**Total estimated time:** 3-4 hours

---

## Risk Assessment

### Low Risks

1. **CSS color scheme**
   - Risk: Status colors don't match dashboard aesthetics
   - Mitigation: Use existing dashboard color palette
   - Fallback: Easy to adjust CSS after visual review

2. **Status value variations**
   - Risk: Workflows might use different status formats (underscores vs hyphens)
   - Mitigation: Normalize status in `statusBadgeClass()` function
   - Handled: Convert underscores/hyphens/spaces consistently

3. **Long messages**
   - Risk: Very long messages break layout
   - Mitigation: CSS word-wrap and max-width
   - Easy fix: Add text truncation if needed

### Minimal Risks

1. **Missing fields**
   - Risk: None - Option types handle this naturally
   - Mitigation: Conditional rendering with `.map()`

2. **Unknown status values**
   - Risk: None - default to blue badge
   - Mitigation: `review-status-default` fallback class

---

## Performance Considerations

**Rendering overhead:**
- Minimal: 3 additional Option.map() calls per worktree card
- No additional I/O (data already loaded in Phase 1)
- CSS adds ~40 lines, negligible impact

**Memory usage:**
- Negligible: status/phase/message already in ReviewState from Phase 1

**No performance concerns for this phase.**

---

## Security Considerations

**None specific to this phase:**
- No user input handling (read-only display)
- No file I/O (data from Phase 1)
- No URL parameters

**XSS prevention:**
- Scalatags escapes all text content automatically
- Message content from review-state.json (trusted source)

---

## Documentation Requirements

- [ ] Update CLAUDE.md if new patterns emerge (unlikely for this phase)
- [ ] Add inline comments for status badge styling logic
- [ ] Document status value mappings in code comments

---

## Definition of Done

**Code complete:**
- [ ] WorktreeListView.scala modified with status/phase/message rendering
- [ ] Helper functions added: `statusBadgeClass()`, `formatStatusLabel()`
- [ ] DashboardService.scala CSS extended with status badge styles
- [ ] WorktreeListViewTest.scala updated with 7 new tests
- [ ] No compilation errors
- [ ] No test failures

**Functionality complete:**
- [ ] Status badge displays when status field present
- [ ] Phase number displays when phase field present
- [ ] Message displays when message field present
- [ ] Missing fields degrade gracefully (no errors, no empty elements)
- [ ] All three status variants (awaiting_review, in_progress, completed) visually distinct
- [ ] Unknown status values show default blue badge

**Quality gates:**
- [ ] Unit test coverage for all rendering paths
- [ ] Manual testing of all status variants completed
- [ ] Visual appearance verified against design
- [ ] CSS consistent with existing dashboard styles
- [ ] No code duplication

**Ready for Phase 5:**
- [ ] Story 3 acceptance criteria met
- [ ] Review status display working end-to-end
- [ ] No blocking issues or regressions
- [ ] Foundation ready for Phase 5 caching optimizations

---

## Notes for Implementation

**Scalatags conditional rendering pattern:**
```scala
// Render only if Option is defined
state.status.map { statusValue =>
  div(cls := "review-status", statusValue)
}

// Returns: Option[Frag]
// Scalatags handles Option[Frag] automatically (renders if Some, skips if None)
```

**CSS naming convention (from existing codebase):**
```css
/* Existing patterns */
.issue-status { ... }
.workflow-progress { ... }

/* New patterns (consistent) */
.review-status { ... }
.review-phase { ... }
.review-message { ... }
```

**Status normalization pattern:**
```scala
// Handle variations: "awaiting_review", "awaiting-review", "Awaiting Review"
status.toLowerCase.replace(" ", "-") match
  case "awaiting_review" | "awaiting-review" => ...
```

**Testing pattern for conditional rendering:**
```scala
// Verify element NOT present when field missing
val state = ReviewState(status = None, ...)
val html = renderWorktreeCard(..., reviewState = Some(state), ...).render
assert(!html.contains("review-status"))
```

---

## Success Metrics

**Functionality:**
- User can see review status at a glance (color-coded badge)
- Phase number provides context for multi-phase workflows
- Message gives additional information when needed
- Missing fields don't break the UI

**Code quality:**
- All tests pass
- No code duplication
- Helper functions are pure and testable
- CSS follows existing conventions

**User experience:**
- Status is immediately visible (prominent badge)
- Colors convey meaning (green=ready, yellow=in-progress, gray=done)
- Phase number doesn't clutter when not needed (optional)
- Message provides context without overwhelming

---

**Ready to implement!** This phase is straightforward, building on existing ReviewState infrastructure from Phase 1.
