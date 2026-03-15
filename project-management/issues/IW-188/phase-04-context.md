# Phase 4: Artifact links to artifact detail view

## Goals

Verify artifact link functionality in the worktree detail page and improve artifact navigation. The `renderReviewArtifacts` rendering is already integrated into `WorktreeDetailView` (reused from `WorktreeCardRenderer`), and the `/worktrees/:issueId/artifacts` endpoint exists. This phase focuses on:

1. Improving back-navigation from `ArtifactView` to link to the worktree detail page (`/worktrees/:issueId`) instead of the dashboard root (`/`)
2. Adding test coverage for artifact links in the worktree detail page context
3. Verifying the full click-through flow: detail page → artifact link → artifact view → back to detail page

## Current State (after Phases 1-3)

### Artifact rendering in detail view (`WorktreeDetailView.scala`, line 223)

The detail view delegates directly to `WorktreeCardRenderer.renderReviewArtifacts`:
```scala
WorktreeCardRenderer.renderReviewArtifacts(worktree.issueId, reviewStateResult)
```

This generates `<a href="/worktrees/:issueId/artifacts?path=...">` links for each artifact.

### Artifact endpoint (`CaskServer.scala`, lines 196-229)

`GET /worktrees/:issueId/artifacts?path=<path>` loads artifact content via `ArtifactService.loadArtifact()` and renders via `ArtifactView.render()`.

### ArtifactView back link (`ArtifactView.scala`, lines 32-35)

Currently links to `/` (dashboard root):
```scala
a(cls := "back-link", href := "/", "← Back to Dashboard")
```

This breaks the hierarchical navigation flow. When navigating Projects > Project > Worktree > Artifact, clicking "back" should return to the worktree detail page, not the dashboard root.

### Existing test coverage

- Unit tests verify review artifacts section renders in the detail view (Phase 1)
- No unit test verifying artifact link href patterns specifically
- No test for artifact back-navigation from `ArtifactView`
- No integration test for the `/worktrees/:issueId/artifacts` → back link flow

## Gap Analysis

### Covered by existing tests
1. Review artifacts section renders when review state has artifacts — **covered** (unit test in Phase 1)
2. Artifacts endpoint returns rendered HTML — **covered** (existing CaskServerTest)

### NOT covered
1. **No test for artifact link href pattern in detail view**: Unit tests verify the section renders but don't assert the link URL pattern `/worktrees/:issueId/artifacts?path=...`
2. **No test for multiple artifacts rendering**: No test with 2+ artifacts verifying all are rendered as individual links
3. **No test for zero artifacts**: What happens when review state has empty artifact list
4. **ArtifactView back link points to wrong URL**: Goes to `/` instead of `/worktrees/:issueId`
5. **No test for back link URL in ArtifactView**: No test verifies where the back link goes

## Scope

### Must do
- Fix `ArtifactView` back link to navigate to `/worktrees/:issueId` instead of `/`
- Update `ArtifactView.renderError` back link similarly
- Add unit test verifying artifact link href pattern in detail view output
- Add unit test for ArtifactView back link URL
- Add unit test for multiple artifacts rendering
- Add unit test for empty artifact list (verify no artifact section rendered)

### Should do
- Add integration test verifying artifact page back link points to worktree detail page
- Add E2E test verifying artifact link from detail page loads artifact content

### Out of scope
- Changing artifact list layout/styling (currently uses compact card style, acceptable for now)
- Adding artifact content preview on the detail page
- Breadcrumb navigation in ArtifactView (separate concern, would be a future improvement)

## Dependencies

- Phase 1 (complete): `WorktreeDetailView.render`, `CaskServer` routes, `WorktreeCardRenderer.renderReviewArtifacts`
- Phase 3 (complete): Not-found page test coverage

## Approach

### 1. Fix ArtifactView back link (`ArtifactView.scala`)

Change `render` signature to use `issueId` for the back link:
```scala
a(cls := "back-link", href := s"/worktrees/$issueId", "← Back to Worktree")
```

Same for `renderError` — note that `renderError` has TWO back links:
- Line 103: `a(cls := "back-link", href := "/", "← Back to Dashboard")` (header)
- Line 109: `p(a(href := "/", "Return to dashboard"))` (content)

Both should link to `/worktrees/$issueId`.

No signature change needed — `issueId` is already a parameter of both methods.

### 2. Add unit tests for artifact links in detail view (`WorktreeDetailViewTest.scala`)

**Test: "render shows artifact links with correct href pattern"**
- Pass review state with artifacts to `render`
- Assert output contains `href="/worktrees/IW-188/artifacts?path=project-management/issues/IW-188/analysis.md"`
- Assert artifact label text is present

**Test: "render shows multiple artifacts as individual links"**
- Pass review state with 2+ artifacts
- Assert both artifact labels and links are present

**Test: "render does not show artifact section when artifact list is empty"**
- Pass review state with empty artifacts list
- Assert no `artifact-list` class in output

### 3. Update existing ArtifactView tests (`ArtifactViewTest.scala`)

Existing tests assert the old `/` back link behavior and will break after the fix:
- "render back link points to dashboard" (line 51): asserts `href="/"` and "Back to Dashboard"
- "renderError includes back link to dashboard" (line 96): asserts `href="/"` and "Back to Dashboard"
- "renderError includes return link" (line 114): asserts "Return to dashboard"

These must be updated to assert the new `/worktrees/:issueId` pattern:

**Update: "render back link points to worktree detail page"**
- Assert `href="/worktrees/TEST-123"` (using the test's issueId)
- Assert "Back to Worktree" text

**Update: "renderError includes back link to worktree detail page"**
- Assert `href="/worktrees/TEST-123"`
- Assert "Back to Worktree" text

**Update: "renderError includes return link"**
- Assert "Return to worktree" text

### 4. Add integration test (`CaskServerTest.scala`)

**Test: "GET /worktrees/:issueId/artifacts returns page with back link to worktree detail"**
- Register worktree, create artifact file, hit artifact endpoint
- Assert response contains `href="/worktrees/:issueId"`

### 5. Add E2E test (`dashboard-dev-mode.bats`)

**Test: "artifact link from worktree detail page loads artifact content"**
- Register worktree with review state containing artifacts
- Hit detail page, extract artifact link
- Hit artifact link, verify artifact content loads
- Verify back link points to worktree detail page

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/dashboard/presentation/views/ArtifactView.scala` | Change back link from `/` to `/worktrees/:issueId` in both `render` and `renderError` |
| `.iw/core/test/WorktreeDetailViewTest.scala` | Add tests for artifact link patterns, multiple artifacts, empty artifacts |
| `.iw/core/test/ArtifactViewTest.scala` | Update existing back link tests to assert `/worktrees/:issueId` pattern |
| `.iw/core/test/CaskServerTest.scala` | Add integration test for artifact back link |
| `.iw/test/dashboard-dev-mode.bats` | Add E2E test for artifact link flow |

## Testing Strategy

### Unit tests
- Verify artifact links in detail view output match `/worktrees/:issueId/artifacts?path=...` pattern
- Verify multiple artifacts render as individual links
- Verify empty artifact list produces no artifact section
- Verify ArtifactView back link points to `/worktrees/:issueId`

### Integration tests
- Verify artifact page response contains back link to worktree detail page

### E2E tests
- Verify full flow: detail page → artifact link → artifact content → back to detail

## Acceptance Criteria

From Story 4:
- [x] Each artifact in review state is rendered as a clickable link — **implemented in Phase 1 via `renderReviewArtifacts`**
- [x] Links follow existing pattern: `/worktrees/:issueId/artifacts?path=...` — **implemented in Phase 1**
- [ ] Artifact detail view loads correctly when clicking a link — **needs test coverage**
- [ ] Back navigation from artifact view returns to worktree detail page — **needs implementation fix**
