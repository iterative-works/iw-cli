# Phase 2 Context: Progress bars persist across card refresh

**Issue:** IW-164
**Phase:** 2 - Progress bars persist across card refresh
**Story:** Progress bars persist across card refresh

## Goals

Fix the progress cache so that progress bars remain visible after HTMX card refresh. The progress bar shows task completion status and vanishes because `WorktreeCardService.renderCard` reads from cache only but never populates it.

## Scope

**In Scope:**
- Modify `WorktreeCardService.renderCard` to fetch progress from filesystem
- Return fetched progress in `CardRenderResult.fetchedProgress`
- Use mtime-based caching like review state does
- Add tests for the new behavior

**Out of Scope:**
- PR link persistence (Phase 3)
- Initial dashboard render changes (only card refresh)
- Performance optimization beyond mtime caching

## Dependencies

**From Previous Phases:**
- Phase 1 documented the working pattern in `WorktreeCardServiceTest.scala`
- Pattern: fetch from filesystem → return in CardRenderResult → server updates cache

**Prerequisites:**
- `WorkflowProgressService.fetchProgress` exists and reads from filesystem
- `CachedProgress` model exists with mtime validation
- `CardRenderResult.fetchedProgress` field exists (currently always None)

## Technical Approach

### The Problem

Looking at `WorktreeCardService.renderCard` (line 100):
```scala
val progress = progressCache.get(issueId).map(_.progress)
```

This only reads from cache. If cache is empty, progress is None. The cache never gets populated because:
- Initial render doesn't populate it (design decision from analysis)
- Card refresh returns `None` for `fetchedProgress` (line 127)

### The Fix

Follow the review state pattern:

1. **Read from filesystem** on every card refresh using `WorkflowProgressService.fetchProgress`
2. **Use mtime-based caching** to avoid re-parsing unchanged files
3. **Return fetched data** in `CardRenderResult.fetchedProgress`
4. Server already has code to update cache from this field (CaskServer.scala lines 150-152)

### Key Code Changes

**WorktreeCardService.scala:**

Add method similar to `fetchReviewStateForWorktree`:
```scala
private def fetchProgressForWorktree(
  wt: WorktreeRegistration,
  cache: Map[String, CachedProgress]
): Option[CachedProgress]
```

Call it from `renderCard` and return the result in `CardRenderResult.fetchedProgress`.

## Files to Modify

**Production Code:**
- `.iw/core/dashboard/WorktreeCardService.scala` - Add progress fetching logic
- `.iw/core/dashboard/WorkflowProgressService.scala` - May need to add/adjust fetchProgress with cache support

**Test Code:**
- `.iw/core/test/WorktreeCardServiceTest.scala` - Add tests for progress persistence

**Files to Read (reference):**
- `.iw/core/dashboard/ReviewStateService.scala` - Pattern to follow
- `.iw/core/model/CachedProgress.scala` - Existing cache model

## Testing Strategy

### Unit Tests
1. Test `WorktreeCardService.renderCard` returns `fetchedProgress` when task files exist
2. Test `WorktreeCardService.renderCard` returns `None` for `fetchedProgress` when no task files
3. Test mtime-based caching works (cache hit when files unchanged)

### Test Data
Create temp directory with sample task files:
```
project-management/issues/TEST-123/
├── phase-01-tasks.md (with checkboxes)
└── tasks.md
```

## Acceptance Criteria

- [ ] Progress bars visible on initial render AND after card refresh
- [ ] Progress data correctly reflects task file state
- [ ] Mtime-based caching prevents unnecessary re-parsing
- [ ] No regressions in existing tests
- [ ] CardRenderResult.fetchedProgress is populated when files exist
