# Phase 1 Tasks: Worktree detail page with complete context

## Setup

- [ ] [setup] Make `WorktreeCardRenderer.renderReviewArtifacts` accessible (remove `private` modifier)

## Tests

- [ ] [test] Create `WorktreeDetailViewTest.scala` with test for rendering issue title and status in full data scenario
- [ ] [test] Test rendering with missing optional data (no PR, no review state, no assignee)
- [ ] [test] Test skeleton state rendering when `issueData` is `None`
- [ ] [test] Test breadcrumb with project name: `Projects > projectName > issueId`
- [ ] [test] Test breadcrumb without project name: `Projects > issueId`
- [ ] [test] Test `renderNotFound` produces error page with issue ID and back link
- [ ] [test] Test Zed editor link contains correct `zed://ssh/...` URL
- [ ] [test] Test workflow progress section renders phase info and progress bar
- [ ] [test] Test git status section renders branch name and clean/dirty indicator
- [ ] [test] Test PR section renders PR number link and state badge

## Implementation

- [ ] [impl] Create `WorktreeDetailView.scala` with `render` method for full data display
- [ ] [impl] Add skeleton rendering for when issue data is not yet loaded
- [ ] [impl] Add breadcrumb rendering with project name derivation fallback
- [ ] [impl] Add `renderNotFound` method for unknown worktree IDs
- [ ] [impl] Add `GET /worktrees/:issueId` route in `CaskServer` with data fetching and view rendering
- [ ] [impl] Add import for `WorktreeDetailView` in `CaskServer`

## Integration

- [ ] [integ] Add integration test: `GET /worktrees/:issueId` returns 200 with HTML for known worktree
- [ ] [integ] Add integration test: `GET /worktrees/NONEXISTENT` returns 404 with error page
- [ ] [integ] Run full test suite to verify no regressions
