# Phase 1 Context: Review state regression test

**Issue:** IW-164
**Phase:** 1 - Review state regression test
**Story:** Verify review state continues working correctly

## Goals

Establish a working reference pattern by creating regression tests for review state, which already works correctly. This documents the correct approach for the other stories and ensures we don't break what's working.

## Scope

**In Scope:**
- Create regression tests for review state persistence across HTMX card refresh
- Document the working filesystem-read pattern used by review state
- Verify that `CardRenderResult.fetchedReviewState` is populated correctly

**Out of Scope:**
- Fixing progress or PR caching (Stories 1-2)
- Changing review state implementation (it works)
- E2E browser testing (BATS/Playwright) - focus on unit/integration tests

## Dependencies

**From Previous Phases:** None (this is the first phase)

**Prerequisites:**
- Dashboard server code exists at `works/iterative/dashboard/`
- `WorktreeCardService.scala` contains the working pattern
- `ReviewStateService.scala` implements mtime-based caching
- Test framework (munit) is configured

## Technical Approach

### Step 1: Understand the Working Pattern

Review how `WorktreeCardService.renderCard` handles review state:
1. Always reads from filesystem via `reviewStateService.fetchReviewState`
2. Uses mtime-based caching to avoid re-parsing unchanged files
3. Returns fetched data in `CardRenderResult.reviewStateCacheUpdate`
4. Server updates cache from this returned value

### Step 2: Create Unit Tests

Test the key behaviors:
- `CachedReviewState.isValid` - validates mtime comparison
- `ReviewStateService.fetchReviewState` - returns data from filesystem
- `CardRenderResult` construction - includes review state update

### Step 3: Create Integration Tests

Test the full render path:
- `WorktreeCardService.renderCard` with real filesystem
- Verify `CardRenderResult.reviewStateCacheUpdate` is populated
- Verify data survives simulated "refresh" (second render call)

## Files to Modify

**New Files:**
- `works/iterative/dashboard/src/test/scala/works/iterative/dashboard/services/ReviewStateRegressionSpec.scala`

**Files to Read (reference only):**
- `works/iterative/dashboard/src/main/scala/works/iterative/dashboard/services/WorktreeCardService.scala`
- `works/iterative/dashboard/src/main/scala/works/iterative/dashboard/services/ReviewStateService.scala`
- `works/iterative/dashboard/src/main/scala/works/iterative/dashboard/model/CardRenderResult.scala`

## Testing Strategy

### Unit Tests
1. Test `CachedReviewState.isValid(fileMtime)` returns true when mtimes match
2. Test `CachedReviewState.isValid(fileMtime)` returns false when file changed
3. Test `ReviewStateService.fetchReviewState` reads from filesystem correctly

### Integration Tests
1. Create temp directory with `review-state.json`
2. Call `WorktreeCardService.renderCard`
3. Verify `CardRenderResult.reviewStateCacheUpdate` contains expected data
4. Call render again (simulating refresh)
5. Verify data still present in second result

### Test Data Fixtures
```json
{
  "version": 1,
  "issue_id": "TEST-123",
  "status": "implementing",
  "phase": 2,
  "step": "implementation",
  "branch": "TEST-123-feature",
  "pr_url": null,
  "git_sha": "abc123",
  "last_updated": "2026-01-26T10:00:00Z",
  "message": "Test review state",
  "artifacts": [
    {"label": "Analysis", "path": "analysis.md"}
  ]
}
```

## Acceptance Criteria

- [ ] Unit tests pass for `CachedReviewState.isValid`
- [ ] Unit tests pass for `ReviewStateService.fetchReviewState`
- [ ] Integration test verifies `CardRenderResult` contains review state data
- [ ] Integration test verifies data persists across multiple renders
- [ ] Tests document the working pattern for Stories 1-2 to follow
- [ ] No changes to production code (these are regression tests)
