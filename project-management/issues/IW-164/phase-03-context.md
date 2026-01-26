# Phase 3 Context: PR links persist across card refresh

**Issue:** IW-164
**Phase:** 3 - PR links persist across card refresh
**Story:** PR links persist across card refresh

## Goals

Fix the PR cache so that PR links remain visible after HTMX card refresh. PR links were vanishing because `WorktreeCardService.renderCard` reads from cache only (line 102) but never populates it.

## Scope

**In Scope:**
- Modify `WorktreeCardService.renderCard` to return fetched PR in `CardRenderResult.fetchedPR`
- The PR fetching logic already exists via `PullRequestCacheService`
- Server already has cache update code (CaskServer.scala lines 153-155)
- Add tests for the new behavior

**Out of Scope:**
- Changing TTL-based caching (already implemented in `CachedPR.isValid`)
- Adding new CLI calls (PR data already fetched elsewhere)
- Initial dashboard render changes

## Dependencies

**From Previous Phases:**
- Phase 1 documented the working pattern
- Phase 2 applied pattern to progress (same approach applies)

**Prerequisites:**
- `PullRequestCacheService` exists with `fetchPR` and `getCachedOnly` methods
- `CachedPR` model exists with TTL validation
- `CardRenderResult.fetchedPR` field exists (currently always None)

## Technical Approach

### The Problem

Looking at `WorktreeCardService.renderCard` (line 102):
```scala
val prData = prCache.get(issueId).map(_.pr)
```

This only reads from cache. If cache is empty, PR is None. The cache never gets populated from card refresh because:
- `CardRenderResult.fetchedPR` is always `None` (line 127)

### The Fix

Unlike progress (mtime-based), PR uses TTL-based caching because CLI calls are expensive. The fix is simpler:

1. **Return cached PR data** if it exists (no filesystem read needed)
2. **Return the CachedPR wrapper** in `CardRenderResult.fetchedPR` so server can update cache

**Key insight:** We don't need to fetch fresh PR data on every refresh. We just need to return the `CachedPR` object (not just the inner `PullRequestData`) so the server can store it.

**Note:** The actual PR fetching happens elsewhere (likely initial dashboard render or a background process). This phase just ensures the cached data is returned properly.

### Key Code Changes

**WorktreeCardService.scala:**

Simply return the cached PR data in `CardRenderResult.fetchedPR`:
```scala
val (prData, prCacheUpdate) = prCache.get(issueId) match {
  case Some(cached) => (Some(cached.pr), Some(cached))
  case None => (None, None)
}
// ...
CardRenderResult(html, fetchedCachedIssue, progressCacheUpdate, prCacheUpdate, reviewStateCacheUpdate)
```

## Files to Modify

**Production Code:**
- `.iw/core/dashboard/WorktreeCardService.scala` - Return cached PR in result

**Test Code:**
- `.iw/core/test/WorktreeCardServiceTest.scala` - Add tests for PR persistence

**Files to Read (reference):**
- `.iw/core/model/CachedPR.scala` - Existing cache model
- `.iw/core/dashboard/PullRequestCacheService.scala` - Existing service

## Testing Strategy

### Unit Tests
1. Test `WorktreeCardService.renderCard` returns `fetchedPR` when PR cache has data
2. Test `WorktreeCardService.renderCard` returns `None` for `fetchedPR` when no PR cached

Note: We don't need mtime tests - PR uses TTL caching which is already tested in `CachedPRTest.scala`

## Acceptance Criteria

- [ ] PR links visible after HTMX card refresh
- [ ] CardRenderResult.fetchedPR populated when PR is cached
- [ ] No new CLI calls (PR data from existing cache)
- [ ] All existing tests pass
