# Phase 7 Context: Load recent issues on modal open

**Issue:** IW-88
**Phase:** 7 of 7
**Story:** Load recent issues on modal open
**Est:** 2-4h

## Goals

When users open the Create Worktree modal, they should see the 5 most recent issues immediately - before typing anything. This reduces friction by removing the need to know issue IDs upfront.

## Scope

### In Scope
- Add HTMX trigger to load recent issues when modal opens
- Display recent issues in search results container on modal load
- Clear search input returns to showing recent issues

### Out of Scope
- Any tracker-specific changes (all already implemented in Phases 1, 3, 5)
- Search functionality changes (already working)
- Modal styling changes

## Dependencies from Previous Phases

**All infrastructure is already in place:**

- Phase 1: `GitHubClient.listRecentIssues()` - fetches recent GitHub issues
- Phase 3: `LinearClient.listRecentIssues()` - fetches recent Linear issues
- Phase 5: `YouTrackClient.listRecentIssues()` - fetches recent YouTrack issues
- All Phases: `IssueSearchService.fetchRecent()` - application service
- All Phases: `/api/issues/recent` endpoint - returns HTML fragment
- All Phases: `buildFetchRecentFunction()` in CaskServer - routes by tracker type

## Technical Approach

**Option A: HTMX load trigger (Recommended)**

Add `hx-trigger="load"` to a container in the modal that calls `/api/issues/recent`:

```html
<div id="search-results"
     hx-get="/api/issues/recent?project={projectPath}"
     hx-trigger="load"
     hx-swap="innerHTML">
  <!-- Loading indicator or empty while fetching -->
</div>
```

Pros:
- Clean separation - reuses existing `/api/issues/recent` endpoint
- No duplication of fetch logic
- HTMX handles loading automatically

Cons:
- Slight delay (one HTTP roundtrip after modal appears)

**Option B: Pre-load in modal endpoint**

Modify `createWorktreeModal` to pre-fetch recent issues and include them in the initial HTML:

```scala
@cask.get("/api/modal/create-worktree")
def createWorktreeModal(project: Option[String]) = {
  val recentIssues = fetchRecentIssues(project)
  CreateWorktreeModal.render(project, recentIssues)
}
```

Pros:
- Single HTTP request, instant display

Cons:
- Couples modal endpoint to issue fetching
- More complex error handling

**Decision: Use Option A (HTMX load trigger)**

This follows the existing HTMX patterns in the codebase and keeps the modal rendering simple. The slight delay is acceptable since it's a single fast API call.

## Files to Modify

1. **`.iw/core/presentation/views/CreateWorktreeModal.scala`**
   - Add `hx-trigger="load"` to `#search-results` container
   - Add `hx-get="/api/issues/recent"` with project parameter
   - Keep search input behavior unchanged

2. **`.iw/core/CaskServer.scala`** (Optional)
   - Consider making `/api/issues/search?q=` (empty query) return recent issues
   - This makes "clearing the search box" show recent issues again

3. **`.iw/core/test/CreateWorktreeModalTest.scala`**
   - Test that rendered modal includes HTMX load trigger
   - Test that trigger targets correct endpoint

## Testing Strategy

### Unit Tests
- `CreateWorktreeModalTest`: Verify rendered HTML includes:
  - `hx-trigger="load"` attribute
  - `hx-get="/api/issues/recent"` or similar
  - Correct target and swap attributes

### E2E Tests (Manual or BATS)
- Open Create Worktree modal → recent issues appear within 1 second
- Type search query → results replace recent issues
- Clear search query → recent issues appear again

## Acceptance Criteria

1. ✅ Modal shows 5 recent issues immediately on open (no user action required)
2. ✅ Clearing search input returns to showing recent issues
3. ✅ Existing search and worktree creation flow unchanged
4. ✅ Works for all three tracker types (GitHub, Linear, YouTrack)
5. ✅ Tests pass for modal rendering and load trigger
