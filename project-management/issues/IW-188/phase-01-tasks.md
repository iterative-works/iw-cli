# Phase 1 Tasks: Worktree detail page with complete context

## Setup

- [x] [setup] Make `WorktreeCardRenderer.renderReviewArtifacts` accessible (remove `private` modifier)

## Tests

- [x] [test] Create `WorktreeDetailViewTest.scala` with test for rendering issue title and status in full data scenario
- [x] [test] Test rendering with missing optional data (no PR, no review state, no assignee)
- [x] [test] Test skeleton state rendering when `issueData` is `None`
- [x] [test] Test breadcrumb with project name: `Projects > projectName > issueId`
- [x] [test] Test breadcrumb without project name: `Projects > issueId`
- [x] [test] Test `renderNotFound` produces error page with issue ID and back link
- [x] [test] Test Zed editor link contains correct `zed://ssh/...` URL
- [x] [test] Test workflow progress section renders phase info and progress bar
- [x] [test] Test git status section renders branch name and clean/dirty indicator
- [x] [test] Test PR section renders PR number link and state badge

## Implementation

- [x] [impl] Create `WorktreeDetailView.scala` with `render` method for full data display
- [x] [impl] Add skeleton rendering for when issue data is not yet loaded
- [x] [impl] Add breadcrumb rendering with project name derivation fallback
- [x] [impl] Add `renderNotFound` method for unknown worktree IDs
- [x] [impl] Add `GET /worktrees/:issueId` route in `CaskServer` with data fetching and view rendering
- [x] [impl] Add import for `WorktreeDetailView` in `CaskServer`

## Integration

- [x] [integ] Add integration test: `GET /worktrees/:issueId` returns 200 with HTML for known worktree
- [x] [integ] Add integration test: `GET /worktrees/NONEXISTENT` returns 404 with error page
- [x] [integ] Run full test suite to verify no regressions
