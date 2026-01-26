# Implementation Log: Dashboard caches (progressCache, prCache) never populated

Issue: IW-164

This log tracks the evolution of implementation across phases.

---

## Phase 1: Review state regression test (2026-01-26)

**What was built:**
- Regression tests: `.iw/core/test/WorktreeCardServiceTest.scala` - 3 new tests documenting working pattern
- Bug fix: `.iw/core/test/YouTrackClientCreateIssueTest.scala` - Added missing import (pre-existing issue)

**Decisions made:**
- Document the working pattern in test comments so future developers understand why review state works
- Use real filesystem (temp directories) rather than mocks for integration-style testing
- Test 3 scenarios: file exists, file missing, cache hit with mtime validation

**Patterns documented:**
- Review state caching pattern: Always read from filesystem, use mtime for cache validation, return fetched data in CardRenderResult
- This pattern should be followed for progress (Story 1) and PR (Story 2)

**Testing:**
- Unit tests: 3 tests added (regression tests for review state)
- All 163+ existing tests continue to pass

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260126.md
- Major findings: None (0 critical, 0 warnings, 1 suggestion)

**For next phases:**
- The documented pattern in WorktreeCardServiceTest.scala explains how progress and PR should work
- Key insight: `WorktreeCardService.renderCard` must return fetched data in `CardRenderResult` for server to update cache

**Files changed:**
```
M  .iw/core/test/WorktreeCardServiceTest.scala
M  .iw/core/test/YouTrackClientCreateIssueTest.scala
```

---

## Phase 2: Progress bars persist across card refresh (2026-01-26)

**What was built:**
- Service: `.iw/core/dashboard/WorkflowProgressService.scala` - New `fetchProgressCached` method returning `CachedProgress`
- Service: `.iw/core/dashboard/WorktreeCardService.scala` - New `fetchProgressForWorktree` method, modified `renderCard`
- Tests: `.iw/core/test/WorktreeCardServiceTest.scala` - 3 new tests for progress persistence

**Decisions made:**
- Follow the exact review state pattern (documented in Phase 1)
- Preserve backward compatibility: keep original `fetchProgress` method, add new `fetchProgressCached`
- Extract internal implementation to `fetchProgressInternal` for code reuse

**Patterns applied:**
- FCIS (Functional Core, Imperative Shell): I/O functions (readFile, getMtime) injected into pure service
- Mtime-based caching: Return cached data when file mtimes unchanged
- Cache update pattern: Return `CachedProgress` in `CardRenderResult.fetchedProgress` for server to update cache

**Testing:**
- Unit tests: 3 tests added (progress persistence)
- All 166+ existing tests continue to pass

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20260126.md
- Major findings: None (0 critical, 0 warnings, 0 suggestions)

**For next phases:**
- Same pattern can be applied to PR caching (Phase 3)
- PR has TTL-based caching rather than mtime, but same return pattern applies

**Files changed:**
```
M  .iw/core/dashboard/WorkflowProgressService.scala
M  .iw/core/dashboard/WorktreeCardService.scala
M  .iw/core/test/WorktreeCardServiceTest.scala
```

---

## Phase 3: PR links persist across card refresh (2026-01-26)

**What was built:**
- Service: `.iw/core/dashboard/WorktreeCardService.scala` - Modified PR data handling to return `CachedPR` in `CardRenderResult`
- Tests: `.iw/core/test/WorktreeCardServiceTest.scala` - 2 new tests for PR persistence

**Decisions made:**
- Follow the same return pattern as review state and progress
- Simpler than progress: PR uses TTL-based caching, no filesystem reads needed
- Just return the cached `CachedPR` wrapper (not just inner `PullRequestData`) so server can update cache

**Patterns applied:**
- Cache update pattern: Return `CachedPR` in `CardRenderResult.fetchedPR` for server to update cache
- TTL-based caching: Unlike mtime-based progress/review state, PR cache validity is time-based (already implemented in `CachedPR.isValid`)

**Testing:**
- Unit tests: 2 tests added (PR persistence)
- All 168+ existing tests continue to pass

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20260126.md
- Major findings: None (0 critical, 0 warnings, 3 suggestions - all minor style)

**Summary:**
All three cache types now follow the same pattern:
1. Service reads from cache (or filesystem for mtime-based caches)
2. Service returns cached wrapper in `CardRenderResult.fetchedXXX`
3. Server updates its cache from the returned value

**Files changed:**
```
M  .iw/core/dashboard/WorktreeCardService.scala
M  .iw/core/test/WorktreeCardServiceTest.scala
```

---
