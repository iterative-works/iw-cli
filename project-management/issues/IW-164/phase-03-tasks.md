# Phase 3 Tasks: PR links persist across card refresh

**Issue:** IW-164
**Phase:** 3 - PR links persist across card refresh

## Overview

Fix PR cache so links remain visible after HTMX card refresh. Unlike progress, PR uses TTL-based caching - we just need to return the cached data in CardRenderResult.

## Tasks

### Implementation

- [x] [impl] Modify WorktreeCardService.renderCard to return cached PR in fetchedPR
- [x] [impl] Update CardRenderResult construction to include PR cache update

### Tests

- [x] [test] WorktreeCardService.renderCard returns fetchedPR when PR cache has data
- [x] [test] WorktreeCardService.renderCard returns None for fetchedPR when no PR cached

### Verification

- [x] [impl] Run all tests to ensure no regressions

## Acceptance Criteria

- [x] PR links visible after HTMX card refresh
- [x] CardRenderResult.fetchedPR populated when PR is cached
- [x] All existing tests pass

**Phase Status:** Complete
