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
