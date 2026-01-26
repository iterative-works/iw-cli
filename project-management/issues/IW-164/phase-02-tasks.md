# Phase 2 Tasks: Progress bars persist across card refresh

**Issue:** IW-164
**Phase:** 2 - Progress bars persist across card refresh

## Overview

Fix progress cache so bars remain visible after HTMX card refresh by following the review state pattern.

## Tasks

### Analysis
- [ ] [impl] Understand current progress fetching flow in WorktreeCardService.renderCard

### Implementation

- [ ] [impl] Add fetchProgressForWorktree method to WorktreeCardService
- [ ] [impl] Modify WorkflowProgressService.fetchProgress to return CachedProgress
- [ ] [impl] Call fetchProgressForWorktree in renderCard method
- [ ] [impl] Return fetched progress in CardRenderResult.fetchedProgress

### Tests

- [ ] [test] WorktreeCardService.renderCard returns fetchedProgress when task files exist
- [ ] [test] WorktreeCardService.renderCard returns None for fetchedProgress when no task files
- [ ] [test] Mtime-based caching works (cache hit when files unchanged)

### Verification

- [ ] [impl] Run all tests to ensure no regressions

## Acceptance Criteria

- [ ] Progress bars visible after HTMX card refresh
- [ ] CardRenderResult.fetchedProgress populated when task files exist
- [ ] All existing tests pass

**Phase Status:** Not Started
