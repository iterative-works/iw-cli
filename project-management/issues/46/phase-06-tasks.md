# Phase 6 Tasks: Graceful Error Handling

**Issue:** #46
**Phase:** 6 of 6
**Story:** Story 5 - Missing or invalid state files handled gracefully
**Estimated Effort:** 3-4 hours

---

## Task Overview

This phase enhances error handling across the review artifacts feature to ensure:
- User-friendly error messages without implementation details
- Clear distinction between "no state" vs "invalid state" vs "valid state"
- Errors are logged for debugging without breaking the dashboard
- Comprehensive testing of all error scenarios

---

## Setup Tasks (15 min)

- [ ] Review existing error handling in ReviewStateService (Phase 1)
- [ ] Review existing error handling in PathValidator (Phase 2)
- [ ] Review existing error handling in ArtifactService (Phase 3)
- [ ] Identify logging strategy used in codebase (check for existing patterns)

---

## Test Tasks - Unit Tests (60 min)

### DashboardService Tests (30 min)

- [ ] Write test: `fetchReviewStateForWorktree returns None when state file missing`
  - Setup: Mock getMtime to return Left("File not found")
  - Assert: Result is None
  - Assert: No error logged

- [ ] Write test: `fetchReviewStateForWorktree returns Some(Left) when JSON invalid`
  - Setup: Mock readFile to return invalid JSON string
  - Assert: Result is Some(Left(error))
  - Assert: Error message mentions "Failed to parse review state JSON"

- [ ] Write test: `fetchReviewStateForWorktree returns Some(Right) for valid state`
  - Setup: Mock I/O to return valid review-state.json
  - Assert: Result is Some(Right(CachedReviewState))
  - Assert: CachedReviewState contains correct ReviewState

- [ ] Write test: `renderDashboard logs invalid JSON errors without crashing`
  - Setup: Worktree with invalid JSON
  - Capture stderr output
  - Assert: Dashboard HTML generated successfully
  - Assert: Error logged to stderr with issueId

- [ ] Write test: `Cache not updated when state is invalid`
  - Setup: Start with empty cache, fetch invalid state
  - Assert: Returned cache is still empty
  - Assert: Invalid state not added to cache

### WorktreeListView Tests (30 min)

- [ ] Write test: `render with None shows no review section`
  - Setup: Pass reviewState = None
  - Assert: Generated HTML does not contain "review-artifacts" class

- [ ] Write test: `render with Some(Left(error)) shows error message`
  - Setup: Pass reviewState = Some(Left("Failed to parse JSON"))
  - Assert: HTML contains "review-error" class
  - Assert: HTML contains "Review state unavailable" text
  - Assert: HTML contains helpful error detail

- [ ] Write test: `render error message has correct CSS classes`
  - Setup: Pass reviewState = Some(Left("error"))
  - Assert: HTML contains "review-error" class
  - Assert: HTML contains "review-error-message" class
  - Assert: HTML contains "review-error-detail" class

- [ ] Write test: `render with Some(Right(state)) and artifacts shows artifact list`
  - Setup: Pass valid ReviewState with 2 artifacts
  - Assert: HTML contains "review-artifacts" class
  - Assert: Both artifacts rendered as links

- [ ] Write test: `render with Some(Right(state)) and empty artifacts shows nothing`
  - Setup: Pass ReviewState with empty artifacts list
  - Assert: HTML does not contain "review-artifacts" class

- [ ] Write test: `Error message does not leak filesystem paths`
  - Setup: Pass reviewState with error containing path
  - Assert: Generated HTML does not contain absolute file paths

---

## Implementation Tasks (75 min)

### Update Return Types (30 min)

- [ ] Change `DashboardService.fetchReviewStateForWorktree` return type
  - From: `Option[CachedReviewState]`
  - To: `Option[Either[String, CachedReviewState]]`

- [ ] Update `fetchReviewStateForWorktree` implementation
  - Pattern match on ReviewStateService.fetchReviewState result
  - Return None for "File not found" errors
  - Return Some(Left(error)) for invalid JSON/parse errors
  - Return Some(Right(cached)) for valid states

- [ ] Update `WorktreeListView.render` parameter type
  - Change tuple from: `..., Option[ReviewState])`
  - To: `..., Option[Either[String, ReviewState]])`

- [ ] Update `WorktreeListView.renderWorktreeCard` parameter type
  - Change from: `reviewState: Option[ReviewState]`
  - To: `reviewState: Option[Either[String, ReviewState]]`

- [ ] Fix compilation errors in DashboardService accumulator
  - Update tuple type in foldLeft accumulator
  - Update pattern matching to handle Option[Either[...]]
  - Extract ReviewState correctly for cache update

### Add Error Logging (15 min)

- [ ] Add error logging in `fetchReviewStateForWorktree`
  - Use System.err.println for invalid JSON errors
  - Include issueId and error message in log
  - Format: `[WARN] Failed to load review state for {issueId}: {error}`
  - Ensure logging doesn't throw exceptions

- [ ] Add error logging for parse failures
  - Log when review-state.json exists but can't be parsed
  - Include context for debugging (issueId, error type)

### Update UI Error Display (30 min)

- [ ] Update WorktreeListView review section rendering
  - Replace `reviewState.filter(_.artifacts.nonEmpty).map` with pattern match
  - Handle None case: render nothing (existing behavior)
  - Handle Some(Left(error)) case: render error message
  - Handle Some(Right(state)) case: render artifacts if non-empty

- [ ] Add error state rendering for Some(Left(error))
  - Create div with "review-error" class
  - Add h4 "Review Artifacts" header
  - Add warning paragraph: "⚠ Review state unavailable"
  - Add detail paragraph: "Check for JSON syntax errors"

- [ ] Add CSS styles for error display to DashboardService.styles
  - `.review-error`: yellow warning background (#fff3cd), warning border
  - `.review-error-message`: bold, warning color (#856404)
  - `.review-error-detail`: smaller font, warning color
  - Use padding and margins for readability

---

## Integration & Testing Tasks (30 min)

### Run Tests

- [ ] Run all DashboardService unit tests
  - Fix any failures
  - Ensure new tests pass
  - Verify existing tests still pass

- [ ] Run all WorktreeListView unit tests
  - Fix any failures
  - Ensure error rendering tests pass
  - Verify artifact display tests still pass

- [ ] Run all existing ReviewStateService tests (Phase 1)
  - Verify no regressions from type changes
  - Ensure Either-based errors still work correctly

- [ ] Run full test suite (unit + integration + E2E)
  - Verify no regressions across all phases
  - Ensure dashboard still loads with no review states

### Manual Testing

- [ ] Test missing review-state.json scenario
  - Create worktree without review-state.json
  - Load dashboard
  - Verify: No review section shown, no errors in UI or logs

- [ ] Test invalid JSON scenario
  - Create worktree with malformed review-state.json (syntax error)
  - Load dashboard
  - Verify: Yellow warning box shown
  - Verify: Error logged to stderr with issueId
  - Verify: Other worktrees unaffected

- [ ] Test valid state scenario
  - Create worktree with valid review-state.json and 3 artifacts
  - Load dashboard
  - Verify: All 3 artifacts shown as links
  - Verify: No errors in UI or logs

- [ ] Test missing artifact file scenario
  - Create valid review-state.json with artifact path that doesn't exist
  - Click artifact link
  - Verify: Error page shown (from Phase 3)
  - Verify: Error message is user-friendly
  - Verify: Other artifacts still clickable

---

## Documentation Tasks (15 min)

- [ ] Update DashboardService docstring
  - Document error handling strategy
  - Explain return type Option[Either[...]]
  - Note logging behavior for invalid states

- [ ] Update WorktreeListView docstring
  - Document three rendering cases: None, Some(Left), Some(Right)
  - Explain error display UI

- [ ] Add inline comments for error type distinction
  - Explain why None vs Some(Left) matters
  - Document cache update strategy (only valid states)

- [ ] Update phase-06-context.md with completion notes
  - Document any deviations from plan
  - Note any additional improvements made
  - Record manual testing results

---

## Verification Checklist

Before marking phase complete, verify:

- [ ] All 11 unit tests pass (5 DashboardService + 6 WorktreeListView)
- [ ] Manual testing: All 4 scenarios work correctly
- [ ] No regressions: Existing tests still pass
- [ ] Error messages are user-friendly (no filesystem paths)
- [ ] Logging includes context (issueId, error type)
- [ ] Type changes compile without warnings
- [ ] Cache only updated for valid states
- [ ] UI clearly distinguishes error states
- [ ] Documentation updated and accurate

---

## Success Criteria

**Functional:**
- Missing state files: No review section, no errors
- Invalid JSON: Warning message shown, error logged
- Valid state: Artifacts displayed correctly
- Missing artifact: Clear error on click, others work

**Code Quality:**
- Error messages don't leak implementation details
- Logging provides debugging context
- Type system prevents invalid states (Option[Either[...]])
- Code follows existing patterns (FCIS, Either for errors)

**UX:**
- User can distinguish "no state" from "invalid state"
- User knows how to fix invalid state (check JSON)
- Errors don't cascade (one broken state doesn't break dashboard)

---

**Total Tasks:** 37
**Estimated Time:** 3-4 hours
**Next Step:** Run `/iterative-works:ag-implement 46` to start Phase 6
