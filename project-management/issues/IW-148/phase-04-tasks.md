# Phase 4: Presentation Layer — Tasks

## Tests First (TDD)

### MainProjectsView
- [x] Test: project with worktreeCount = 0 renders "0 worktrees" text (already passing)
- [x] Test: empty state text mentions './iw register'

### ProjectDetailsView
- [x] Test: renderNotFound mentions registration and project name
- [x] Test: render with empty worktrees includes "Create Worktree" button

## Implementation

- [x] Update `ProjectDetailsView.renderNotFound()` text
- [x] Update `MainProjectsView` empty state text

## Verification

- [x] Run `./iw test unit` — all tests pass
- [x] No compilation warnings
- [x] Existing tests show no regression

**Phase Status:** Complete
