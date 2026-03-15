# Phase 4 Tasks: Artifact links to artifact detail view

## Setup

- [x] [setup] Read `ArtifactView.scala` and confirm back link on line 34 (`href := "/"`) and `renderError` back links on lines 103 and 109
- [x] [setup] Read `ArtifactViewTest.scala` and identify the three tests that assert old `/` behavior (lines 51, 96, 114)
- [x] [setup] Read `WorktreeDetailViewTest.scala` and confirm `sampleReviewState` fixture has artifacts (line 58-67)
- [x] [setup] Read `WorktreeCardRenderer.renderReviewArtifacts` to understand artifact link href generation pattern

## Tests

### Unit Tests (ArtifactViewTest.scala)

- [x] [test] Update test "render back link points to dashboard" (line 51) to assert `href="/worktrees/TEST-123"` and text "Back to Worktree" instead of `href="/"` and "Back to Dashboard"
- [x] [test] Update test "renderError includes back link to dashboard" (line 96) to assert `href="/worktrees/TEST-123"` and text "Back to Worktree" instead of `href="/"` and "Back to Dashboard"
- [x] [test] Update test "renderError includes return link" (line 114) to assert "Return to worktree" instead of "Return to dashboard"
- [x] [test] Run updated `ArtifactViewTest` and confirm all three updated tests fail (TDD red phase)

### Unit Tests (WorktreeDetailViewTest.scala)

- [x] [test] Add test "render shows artifact links with correct href pattern" - assert output contains `href="/worktrees/IW-188/artifacts?path=project-management/issues/IW-188/analysis.md"`
- [x] [test] Add test "render shows multiple artifacts as individual links" - pass review state with 2+ artifacts, assert both artifact labels and links present
- [x] [test] Add test "render does not show artifact section when artifact list is empty" - pass review state with empty artifacts list, assert no `artifact-list` class in output
- [x] [test] Run new `WorktreeDetailViewTest` tests and confirm they pass (these test existing rendering, should be green)

## Implementation

- [x] [impl] Fix `ArtifactView.render` back link (line 34): change `href := "/"` to `href := s"/worktrees/$issueId"` and text to "Back to Worktree"
- [x] [impl] Fix `ArtifactView.renderError` header back link (line 103): change `href := "/"` to `href := s"/worktrees/$issueId"` and text to "Back to Worktree"
- [x] [impl] Fix `ArtifactView.renderError` content return link (line 109): change `href := "/"` to `href := s"/worktrees/$issueId"` and text to "Return to worktree"
- [x] [impl] Run `ArtifactViewTest` and confirm all tests pass (TDD green phase)
- [x] [impl] Run full unit test suite to verify no regressions

## Integration

- [x] [integration] Add integration test in `CaskServerTest.scala`: "GET /worktrees/:issueId/artifacts returns page with back link to worktree detail" - register worktree, create artifact file, hit artifact endpoint, assert response contains `href="/worktrees/:issueId"`
- [x] [integration] Add E2E test in `dashboard-dev-mode.bats`: "artifact link from worktree detail page loads artifact content" - register worktree with review state, hit detail page, extract artifact link, hit artifact link, verify content loads, verify back link points to `/worktrees/:issueId`
- [x] [integration] Run full test suite (`./iw test`) to confirm all tests pass
**Phase Status:** Complete
