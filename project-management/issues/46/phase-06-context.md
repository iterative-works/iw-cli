# Phase 6 Context: Graceful Error Handling

**Issue:** #46
**Phase:** 6 of 6
**Story:** Story 5 - Missing or invalid state files handled gracefully
**Estimated Effort:** 3-4 hours

---

## Goals

This phase focuses on **audit and enhancement of error handling** across the review artifacts feature. Much error handling already exists from prior phases, but this phase ensures:

1. **Error messages are user-friendly** and don't leak implementation details
2. **Partial failures are handled gracefully** (some artifacts work, others don't)
3. **Logging provides debugging context** without breaking the dashboard
4. **UI clearly distinguishes** between "no state" vs "invalid state" vs "state OK but artifact missing"
5. **Comprehensive testing** validates all error scenarios

The goal is to ensure a robust, production-ready feature where errors are informative but don't break the user experience.

---

## Current State Analysis

### What Already Exists (Error Handling from Phases 1-5)

✅ **Phase 1 - ReviewStateService:**
- `parseReviewStateJson()` returns `Either[String, ReviewState]` with error messages
- `fetchReviewState()` returns `Either[String, CachedReviewState]`
- Missing file error: `Left("File not found")` from getMtime
- Invalid JSON error: `Left("Failed to parse review state JSON: ...")`
- Missing artifacts field: `Left("Failed to parse... Missing required field: artifacts")`

✅ **Phase 2 - PathValidator:**
- Secure error messages that don't leak paths: `"Artifact not found"`, `"Invalid artifact path"`
- Validates paths before file access (prevents directory traversal)
- Handles missing files, symlink errors, boundary violations

✅ **Phase 3 - ArtifactService:**
- `loadArtifact()` returns `Either[String, (String, String, String)]`
- Worktree lookup failure: `Left("Worktree not found")`
- Path validation failures propagated from PathValidator
- File read failures propagated from injected readFile function

✅ **Phase 4 - WorktreeListView:**
- Conditional rendering: Review section only shown when `reviewState.filter(_.artifacts.nonEmpty)`
- Missing status/phase/message: Uses `Option.map` for graceful omission
- No error state displayed (assumes valid ReviewState if present)

✅ **Phase 5 - DashboardService:**
- `fetchReviewStateForWorktree()` returns `Option[CachedReviewState]`
- Converts `Either[String, CachedReviewState]` to `Option` (discards Left silently)
- Missing/invalid state results in `None` → no review section displayed

### What's Missing (Phase 6 Gaps)

❌ **Error Visibility:**
- Invalid JSON errors are silently swallowed (converted to None)
- Users don't know if state file is missing vs malformed vs parse error
- No visual indicator when review-state.json exists but is invalid

❌ **Logging:**
- Errors are discarded without logging when converted to Option
- No debugging context when review state fails to load
- Missing artifact clicks don't log the failure

❌ **UI Error Display:**
- No "Review state unavailable" message for malformed JSON (analysis requirement)
- No distinction between "no state file" (clean) vs "invalid state file" (needs fixing)
- Artifact view errors shown in separate page (from Phase 3), but not tested for UX clarity

❌ **Testing:**
- No E2E tests for error scenarios (missing file, invalid JSON, missing artifact)
- No tests for error message content/format
- No tests for partial success scenarios (some artifacts valid, others missing)

---

## Scope

### In Scope

**1. Error Logging (Without Breaking Dashboard)**

Add logging to DashboardService when review state fetch fails:
- Log missing file (INFO level - normal case)
- Log invalid JSON (WARN level - needs investigation)
- Log parse errors (WARN level with details)
- Ensure logging doesn't throw exceptions or break JSON serialization

**2. Enhanced UI Error Display**

Extend WorktreeListView to show error states:
- Missing state file: No review section (existing behavior - keep)
- Invalid state file: Show "Review state unavailable" message with error icon
- Valid state, but empty artifacts: No review section (existing behavior - keep)
- Invalid artifact path: Error page already exists (Phase 3) - verify message clarity

**3. Distinguish Error Types**

Change `fetchReviewStateForWorktree()` to return `Option[Either[String, CachedReviewState]]`:
- `None` = no state file (clean, no error)
- `Some(Left(error))` = state file exists but invalid (show error)
- `Some(Right(cached))` = valid state

This enables UI to distinguish "no state" from "invalid state".

**4. Comprehensive Error Testing**

Add unit tests:
- Error message formatting
- Logging behavior (verify no exceptions)
- UI rendering for each error state

Add integration/E2E tests:
- Dashboard with missing review-state.json
- Dashboard with malformed JSON
- Dashboard with valid state but missing artifact file
- Artifact click with missing file (verify error page)

**5. Documentation**

Update comments/docstrings with error scenarios and handling strategy.

### Out of Scope

❌ **Retry Logic:** No automatic retries for failed file reads (YAGNI)
❌ **Error Recovery:** No automatic file repair/fix (user must fix JSON)
❌ **Metrics/Monitoring:** No Prometheus metrics for errors (future enhancement)
❌ **User Notifications:** No active alerts/emails on errors (dashboard display is enough)
❌ **Partial Artifact Loading:** If one artifact in list is invalid, whole list fails (keep simple)

---

## Dependencies

### Required from Previous Phases

✅ **Phase 1:** ReviewStateService with Either-based error handling
✅ **Phase 2:** PathValidator with secure error messages
✅ **Phase 3:** ArtifactService with error propagation, ArtifactView error page
✅ **Phase 4:** WorktreeListView with conditional rendering
✅ **Phase 5:** DashboardService cache accumulation pattern

### External Dependencies

- **Logging:** Scala's built-in logging or simple println (decide based on existing patterns)
- Check existing codebase for logging strategy (grep for "log", "println", "System.err")

---

## Technical Approach

### 1. Change Return Type for Error Visibility

**Current:**
```scala
private def fetchReviewStateForWorktree(
  wt: WorktreeRegistration,
  cache: Map[String, CachedReviewState]
): Option[CachedReviewState] =
  ReviewStateService.fetchReviewState(
    wt.issueId,
    wt.path,
    cache,
    readFileFn,
    getMtimeFn
  ).toOption  // Silently discards errors
```

**New:**
```scala
private def fetchReviewStateForWorktree(
  wt: WorktreeRegistration,
  cache: Map[String, CachedReviewState]
): Option[Either[String, CachedReviewState]] =
  ReviewStateService.fetchReviewState(
    wt.issueId,
    wt.path,
    cache,
    readFileFn,
    getMtimeFn
  ) match {
    case Left(err) if err.contains("File not found") =>
      None  // Clean case - no state file
    case Left(err) =>
      Some(Left(err))  // Invalid state - show error
    case Right(cached) =>
      Some(Right(cached))  // Valid state
  }
```

**Rationale:** This preserves the distinction between "no file" (None) and "invalid file" (Some(Left(error))) without breaking existing patterns.

### 2. Update DashboardService to Log Errors

Add logging before converting errors:

```scala
ReviewStateService.fetchReviewState(...) match {
  case Left(err) if err.contains("File not found") =>
    // Normal case - no logging needed
    None
  case Left(err) =>
    // Log warning for invalid state
    System.err.println(s"[WARN] Failed to load review state for ${wt.issueId}: $err")
    Some(Left(err))
  case Right(cached) =>
    Some(Right(cached))
}
```

**Rationale:** Simple stderr logging (matches existing Scala CLI patterns). No external logger dependency needed.

### 3. Update WorktreeListView for Error Display

Extend review section rendering to handle `Option[Either[String, ReviewState]]`:

**Current:**
```scala
reviewState.filter(_.artifacts.nonEmpty).map { state => ... }
```

**New:**
```scala
reviewStateResult match {
  case None =>
    // No state file - don't show anything
    ()
  case Some(Left(error)) =>
    // Invalid state file - show error message
    div(
      cls := "review-artifacts review-error",
      h4("Review Artifacts"),
      p(cls := "review-error-message", "⚠ Review state unavailable"),
      p(cls := "review-error-detail", "The review state file exists but could not be loaded. Check for JSON syntax errors.")
    )
  case Some(Right(state)) if state.artifacts.nonEmpty =>
    // Valid state with artifacts - existing rendering
    div(cls := "review-artifacts", ...)
  case Some(Right(state)) =>
    // Valid state but no artifacts - don't show anything
    ()
}
```

**Rationale:** Clear visual distinction between states. User knows to check JSON syntax.

### 4. Update DashboardService Accumulator

Change accumulator type to handle new signature:

**Current:**
```scala
(List.empty[(..., Option[ReviewState])], reviewStateCache)
```

**New:**
```scala
(List.empty[(..., Option[Either[String, ReviewState]])], reviewStateCache)
```

Extract ReviewState differently:

**New:**
```scala
val (reviewStateResult, newCache) = cachedReviewState match {
  case None =>
    (None, cache)
  case Some(Left(error)) =>
    (Some(Left(error)), cache)
  case Some(Right(cached)) =>
    (Some(Right(cached.state)), cache + (wt.issueId -> cached))
}
```

**Rationale:** Only update cache for valid states. Invalid states don't pollute cache.

### 5. Add CSS for Error State

Add styles to DashboardService.styles:

```css
.review-error {
  background-color: #fff3cd;
  border-left: 4px solid #ffc107;
  padding: 12px;
  margin-top: 12px;
}

.review-error-message {
  font-weight: bold;
  color: #856404;
  margin: 0 0 8px 0;
}

.review-error-detail {
  color: #856404;
  font-size: 0.9em;
  margin: 0;
}
```

**Rationale:** Yellow warning color (not red error) since it's a data issue, not system failure.

---

## Files to Modify

### Application Layer

**1. `.iw/core/DashboardService.scala`**
- Change `fetchReviewStateForWorktree()` return type to `Option[Either[String, CachedReviewState]]`
- Add error logging with System.err.println
- Update accumulator to handle new type
- Add CSS for error display

**2. `.iw/core/WorktreeListView.scala`**
- Change `render()` signature: `Option[ReviewState]` → `Option[Either[String, ReviewState]]`
- Update `renderWorktreeCard()` signature similarly
- Add error state rendering (yellow warning box)

### Testing

**3. `.iw/core/test/DashboardServiceTest.scala`**
- Test: Missing review-state.json returns None
- Test: Invalid JSON returns Some(Left(error))
- Test: Valid state returns Some(Right(cached))
- Test: Error logging doesn't throw exceptions

**4. `.iw/core/test/WorktreeListViewTest.scala`**
- Test: None renders no review section
- Test: Some(Left(error)) renders error message
- Test: Some(Right(state)) with artifacts renders artifact list
- Test: Some(Right(state)) with empty artifacts renders nothing
- Test: Error message content and CSS classes

**5. `.iw/core/test/ArtifactServiceTest.scala` (verify existing)**
- Verify error messages are user-friendly
- Verify missing artifact returns clear error
- Add test for error message content if missing

**Optional: E2E Tests (if time permits)**

**6. `.iw/test/dashboard-review-errors.bats`** (new file)
- Setup worktree with missing review-state.json → verify no error shown
- Setup worktree with invalid JSON → verify "unavailable" message
- Setup worktree with valid state → verify artifacts shown
- Click missing artifact → verify error page

---

## Testing Strategy

### Unit Tests (Primary Focus)

**DashboardService Tests:**
1. `fetchReviewStateForWorktree returns None for missing file`
2. `fetchReviewStateForWorktree returns Some(Left) for invalid JSON`
3. `fetchReviewStateForWorktree returns Some(Right) for valid state`
4. `renderDashboard logs errors without crashing`
5. `Cache not updated for invalid states`

**WorktreeListView Tests:**
1. `render with None shows no review section`
2. `render with Some(Left(error)) shows error message`
3. `render error message contains warning icon and helpful text`
4. `render with Some(Right(valid state)) shows artifacts`
5. `render with Some(Right(empty artifacts)) shows nothing`
6. `Error CSS classes applied correctly`

**ArtifactService Tests (Verification):**
1. Verify existing test: `loadArtifact returns Left for missing file`
2. Verify error message: `"Artifact not found"` (secure, user-friendly)
3. Add test if missing: `Error messages don't contain filesystem paths`

### Integration Tests (Secondary)

**Dashboard Integration:**
1. Create temp worktree with malformed review-state.json
2. Call DashboardService.renderDashboard
3. Verify HTML contains error message
4. Verify cache not updated

**ArtifactView Integration:**
1. Request artifact with invalid path
2. Verify error page rendered
3. Verify error message user-friendly

### E2E Tests (Optional - Time Permitting)

**BATS Tests:**
- `dashboard shows no error for missing review-state.json`
- `dashboard shows warning for malformed JSON`
- `dashboard shows artifacts for valid state`

**Manual Testing Checklist:**
- [ ] Missing review-state.json: No review section, no error
- [ ] review-state.json with syntax error: Yellow warning box
- [ ] Valid state with 3 artifacts: All artifacts shown
- [ ] Click valid artifact: Markdown rendered
- [ ] Click missing artifact: Error page with clear message
- [ ] Server logs show warnings for invalid state (check stderr)

---

## Acceptance Criteria

From analysis Story 5:

✅ **Missing state files don't break dashboard**
- No review section shown when file missing
- Other worktree data displays normally
- No errors logged (normal case)

✅ **Invalid JSON shows error, doesn't crash server**
- Dashboard loads successfully
- Review section shows "Review state unavailable" message
- Error logged to stderr for debugging
- Other worktrees unaffected

✅ **Missing artifact files show clear error message**
- Click triggers navigation to error page (existing from Phase 3)
- Error message: "Artifact not found: [filename]" (verify Phase 3 implementation)
- No filesystem paths leaked in error

✅ **Other artifacts remain accessible despite individual failures**
- If one artifact link is broken, others still work (isolated failures)
- Dashboard continues to function
- No cascade failures

✅ **Errors logged for debugging**
- Invalid JSON: WARN level to stderr
- Parse errors: WARN level with error details
- No exceptions thrown during logging
- Logs don't break JSON serialization

---

## Implementation Checklist

### Step 1: Change Return Types (30 min)

- [ ] Update `DashboardService.fetchReviewStateForWorktree()` return type
- [ ] Update `WorktreeListView.render()` parameter type
- [ ] Update `WorktreeListView.renderWorktreeCard()` parameter type
- [ ] Update accumulator in `DashboardService.renderDashboard()`
- [ ] Fix compilation errors

### Step 2: Add Error Logging (15 min)

- [ ] Add System.err.println for invalid JSON
- [ ] Add System.err.println for parse errors
- [ ] Test logging doesn't throw exceptions
- [ ] Verify log format is helpful (includes issueId, error)

### Step 3: UI Error Display (45 min)

- [ ] Add error state rendering in WorktreeListView
- [ ] Add CSS styles for .review-error
- [ ] Test error message content
- [ ] Verify no review section for None
- [ ] Verify artifacts shown for Some(Right(state))

### Step 4: Testing (90 min)

- [ ] Add 5 DashboardService unit tests (error scenarios)
- [ ] Add 6 WorktreeListView unit tests (UI rendering)
- [ ] Verify ArtifactService error tests (existing from Phase 3)
- [ ] Run all tests and fix failures
- [ ] Manual testing of error scenarios

### Step 5: Documentation (15 min)

- [ ] Update DashboardService docstring with error handling
- [ ] Update WorktreeListView docstring with error states
- [ ] Add comments explaining error type distinction

### Total: ~3 hours (within 3-4h estimate)

---

## Risk Assessment

**Low Risk:**
- Changes are isolated to error handling paths (happy path unchanged)
- Type changes will catch all call sites at compile time
- Existing tests verify happy path still works
- UI changes are additive (new error message, doesn't remove existing)

**Medium Risk:**
- Accumulator type change requires careful testing
- Cache update logic must only happen for valid states
- Logging must not throw exceptions

**Mitigations:**
- Comprehensive unit tests for each error scenario
- Manual testing before merge
- Verify existing tests still pass

---

## Success Metrics

**Functional:**
- All 11 unit tests pass (5 DashboardService + 6 WorktreeListView)
- Manual testing: All 5 scenarios work correctly
- No regressions in existing functionality

**Code Quality:**
- Error messages are user-friendly (no filesystem paths, implementation details)
- Logging is helpful for debugging (includes context)
- Type changes make impossible states unrepresentable (None vs Some(Left))

**UX:**
- User can distinguish "no state" from "invalid state"
- User knows how to fix invalid state (check JSON syntax)
- Errors don't cascade (one broken state doesn't break dashboard)

---

## Notes

### Existing Patterns to Follow

**Error Handling:**
- ReviewStateService: `Either[String, T]` for fallible operations
- PathValidator: Secure error messages without path leaks
- DashboardService: `Option[T]` for optional data

**Logging Strategy:**
- Check existing codebase for logging (need to grep for patterns)
- If no logger exists, use System.err.println (simple, no dependencies)

**Testing:**
- munit test framework (existing)
- Mock I/O via function injection (FCIS pattern)
- Test fixtures in separate files or inline JSON strings

### Questions for Implementation

1. **Logging Library?** 
   - Grep codebase for existing logging
   - If none: use System.err.println (simplest)
   - If exists: follow existing pattern

2. **Error Message Consistency?**
   - Review all error messages for user-friendliness
   - Ensure no implementation details leaked
   - Standard format: "[Component] [Action] failed: [Reason]"

3. **E2E Test Priority?**
   - Start with unit tests (most value)
   - Add E2E if time permits (3-4h budget)
   - Manual testing is acceptable fallback

---

## Related Files

**Implementation:**
- `/home/mph/Devel/projects/iw-cli-46/.iw/core/DashboardService.scala`
- `/home/mph/Devel/projects/iw-cli-46/.iw/core/WorktreeListView.scala`

**Testing:**
- `/home/mph/Devel/projects/iw-cli-46/.iw/core/test/DashboardServiceTest.scala`
- `/home/mph/Devel/projects/iw-cli-46/.iw/core/test/WorktreeListViewTest.scala`
- `/home/mph/Devel/projects/iw-cli-46/.iw/core/test/ArtifactServiceTest.scala`

**Reference (Prior Phases):**
- `/home/mph/Devel/projects/iw-cli-46/.iw/core/ReviewStateService.scala` (Phase 1)
- `/home/mph/Devel/projects/iw-cli-46/.iw/core/PathValidator.scala` (Phase 2)
- `/home/mph/Devel/projects/iw-cli-46/.iw/core/ArtifactService.scala` (Phase 3)

**Analysis:**
- `/home/mph/Devel/projects/iw-cli-46/project-management/issues/46/analysis.md`
- `/home/mph/Devel/projects/iw-cli-46/project-management/issues/46/implementation-log.md`

---

**Phase Status:** Ready for Implementation

**Next Steps:**
1. Run `/iterative-works:ag-implement 46` to start Phase 6
2. Follow implementation checklist above
3. Focus on unit tests first, E2E if time permits
4. Manual testing before marking complete
