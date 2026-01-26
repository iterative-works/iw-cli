# Phase 2 Tasks: Progress bars persist across card refresh

**Issue:** IW-164
**Phase:** 2 - Progress bars persist across card refresh

## Overview

Fix progress cache so bars remain visible after HTMX card refresh by following the review state pattern.

## Tasks

### Analysis
- [x] [impl] Understand current progress fetching flow in WorktreeCardService.renderCard

### Implementation

- [x] [impl] Add fetchProgressForWorktree method to WorktreeCardService
- [x] [impl] Modify WorkflowProgressService.fetchProgress to return CachedProgress
- [x] [impl] Call fetchProgressForWorktree in renderCard method
- [x] [impl] Return fetched progress in CardRenderResult.fetchedProgress

### Tests

- [x] [test] WorktreeCardService.renderCard returns fetchedProgress when task files exist
- [x] [test] WorktreeCardService.renderCard returns None for fetchedProgress when no task files
- [x] [test] Mtime-based caching works (cache hit when files unchanged)

### Verification

- [x] [impl] Run all tests to ensure no regressions

## Acceptance Criteria

- [x] Progress bars visible after HTMX card refresh
- [x] CardRenderResult.fetchedProgress populated when task files exist
- [x] All existing tests pass

**Phase Status:** Complete
