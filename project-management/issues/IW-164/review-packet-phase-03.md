# Review Packet: Phase 3 - PR links persist across card refresh

**Issue:** IW-164
**Phase:** 3
**Branch:** IW-164-phase-03
**Date:** 2026-01-26

## Goals

Fix PR cache so links remain visible after HTMX card refresh by returning cached PR data in `CardRenderResult.fetchedPR`.

## Scenarios

- [x] PR links visible after HTMX card refresh
- [x] CardRenderResult.fetchedPR populated when PR is cached
- [x] No fetchedPR when PR cache is empty
- [x] All existing tests pass

## Entry Points

Start review from `WorktreeCardService.scala` lines 113-116 where the PR cache lookup was modified.

## Changes Summary

### WorktreeCardService.scala

**Before:** PR data was read from cache but only the inner `PullRequestData` was used for rendering. The `CachedPR` wrapper was discarded.

```scala
val prData = prCache.get(issueId).map(_.pr)
// ...
CardRenderResult(html, fetchedCachedIssue, progressCacheUpdate, None, reviewStateCacheUpdate)
```

**After:** The `CachedPR` wrapper is preserved and returned in `CardRenderResult.fetchedPR` so the server can update its cache.

```scala
val (prData, prCacheUpdate) = prCache.get(issueId) match {
  case Some(cached) => (Some(cached.pr), Some(cached))
  case None => (None, None)
}
// ...
CardRenderResult(html, fetchedCachedIssue, progressCacheUpdate, prCacheUpdate, reviewStateCacheUpdate)
```

### WorktreeCardServiceTest.scala

Added 2 unit tests:
1. `renderCard returns fetchedPR when PR cache has data` - Verifies PR data is returned
2. `renderCard returns None for fetchedPR when no PR cached` - Verifies None when empty cache

## Test Summary

- **Unit Tests:** 2 added, all pass
- **Total Test Suite:** All existing tests pass

## Files Changed

| File | Type | Lines |
|------|------|-------|
| `.iw/core/dashboard/WorktreeCardService.scala` | Modified | +6/-4 |
| `.iw/core/test/WorktreeCardServiceTest.scala` | Modified | +64 |
| `project-management/issues/IW-164/phase-03-tasks.md` | Modified | Task checkboxes |

## Architecture Notes

This follows the same pattern established in Phases 1-2:
1. Service reads from cache (if available)
2. Service returns cached wrapper in `CardRenderResult.fetchedXXX`
3. Server updates its cache from the returned value

Unlike progress (mtime-based), PR uses TTL-based caching, so no filesystem reads needed here - just returning the cached data.

## Risks

- **Low Risk:** Simple change returning existing cached data
- **No new dependencies:** Uses existing CachedPR model
- **No I/O added:** Pure in-memory operation
