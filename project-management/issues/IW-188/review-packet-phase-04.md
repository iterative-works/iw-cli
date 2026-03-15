---
generated_from: 4dce1da4b2fd7c186a4f19e0d562151755e94d3b
generated_at: 2026-03-15T08:01:08Z
branch: IW-188-phase-04
issue_id: IW-188
phase: 4
files_analyzed:
  - .iw/core/dashboard/presentation/views/ArtifactView.scala
  - .iw/core/test/ArtifactViewTest.scala
  - .iw/core/test/CaskServerTest.scala
  - .iw/core/test/WorktreeDetailViewTest.scala
  - .iw/test/dashboard-dev-mode.bats
---

# Review Packet: Phase 4 - Artifact Links to Artifact Detail View

## Goals

This phase closes the navigation loop for the artifact detail view by fixing the back link in `ArtifactView` and adding test coverage that confirms the full click-through flow works end to end.

Prior phases built the worktree detail page (`/worktrees/:issueId`) and wired artifact links into it via the existing `WorktreeCardRenderer.renderReviewArtifacts` helper. The artifact viewer endpoint (`GET /worktrees/:issueId/artifacts?path=...`) was already in place, but its "back" link pointed to the dashboard root (`/`) instead of the worktree detail page that the user navigated from. This phase:

1. Fixes `ArtifactView.render` and `ArtifactView.renderError` to link back to `/worktrees/:issueId`.
2. Updates existing `ArtifactViewTest` assertions that were testing the old `/` behavior.
3. Adds unit tests for artifact link URL patterns and edge cases (multiple artifacts, empty artifact list) in `WorktreeDetailViewTest`.
4. Adds an integration test in `CaskServerTest` verifying the back link in a real server response.
5. Adds an E2E BATS test verifying the full flow: register worktree, hit artifact endpoint, confirm content and back link.

## Scenarios

- [ ] Clicking an artifact link on the worktree detail page loads the artifact content page
- [ ] The artifact content page back link reads "Back to Worktree" and navigates to `/worktrees/:issueId`
- [ ] The artifact error page back link also navigates to `/worktrees/:issueId`
- [ ] The artifact error page inline return link also navigates to `/worktrees/:issueId`
- [ ] Artifact links in the detail page follow the `/worktrees/:issueId/artifacts?path=...` pattern
- [ ] When review state contains multiple artifacts, all are rendered as individual links
- [ ] When review state has an empty artifact list, no artifact section is rendered

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/dashboard/presentation/views/ArtifactView.scala` | `render()` / `renderError()` | The two methods that were changed — both back links now point to `/worktrees/$issueId` |
| `.iw/core/test/ArtifactViewTest.scala` | `"render back link points to worktree detail page"` | Three tests updated from asserting `/` to asserting `/worktrees/TEST-123` |
| `.iw/core/test/WorktreeDetailViewTest.scala` | `"render shows artifact links with correct href pattern"` | Three new unit tests for artifact link URL, multiple artifacts, and empty list |
| `.iw/core/test/CaskServerTest.scala` | `"GET /worktrees/:issueId/artifacts returns page with back link to worktree detail"` | New integration test — registers worktree, hits artifact endpoint, checks back link |
| `.iw/test/dashboard-dev-mode.bats` | `"artifact link from worktree detail page loads artifact content"` | New E2E BATS test covering the full round-trip |

## Diagrams

### Navigation Flow (before and after)

```
Before Phase 4:

  Detail page (/worktrees/IW-188)
       |
       | click artifact link
       v
  Artifact view (/worktrees/IW-188/artifacts?path=...)
       |
       | click "Back to Dashboard"
       v
  Dashboard root (/)   <-- WRONG: breaks hierarchical flow

After Phase 4:

  Detail page (/worktrees/IW-188)
       |
       | click artifact link
       v
  Artifact view (/worktrees/IW-188/artifacts?path=...)
       |
       | click "Back to Worktree"
       v
  Detail page (/worktrees/IW-188)  <-- CORRECT: stays in hierarchy
```

### Component Relationships

```
CaskServer
  GET /worktrees/:issueId/artifacts
       |
       | loads artifact via ArtifactService.loadArtifact()
       | renders via ArtifactView.render(issueId = issueId, ...)
       v
  ArtifactView.render()
    back link: href = /worktrees/$issueId   (fixed in this phase)

  ArtifactView.renderError()
    header back link: href = /worktrees/$issueId   (fixed in this phase)
    content return link: href = /worktrees/$issueId (fixed in this phase)

WorktreeDetailView
  delegates to WorktreeCardRenderer.renderReviewArtifacts(issueId, reviewState)
    artifact links: href = /worktrees/$issueId/artifacts?path=...
                           (pre-existing; tested for the first time in this phase)
```

## Test Summary

### Unit Tests

| Test | File | Type | Status |
|------|------|------|--------|
| `render back link points to worktree detail page` | `ArtifactViewTest.scala` | Unit | Updated (was asserting `/`) |
| `renderError includes back link to worktree detail page` | `ArtifactViewTest.scala` | Unit | Updated (was asserting `/`) |
| `renderError includes return link` | `ArtifactViewTest.scala` | Unit | Updated ("Return to worktree") |
| `render shows artifact links with correct href pattern` | `WorktreeDetailViewTest.scala` | Unit | New |
| `render shows multiple artifacts as individual links` | `WorktreeDetailViewTest.scala` | Unit | New |
| `render does not show artifact section when artifact list is empty` | `WorktreeDetailViewTest.scala` | Unit | New |

### Integration Tests

| Test | File | Type | Status |
|------|------|------|--------|
| `GET /worktrees/:issueId/artifacts returns page with back link to worktree detail` | `CaskServerTest.scala` | Integration | New |

### E2E Tests

| Test | File | Type | Status |
|------|------|------|--------|
| `artifact link from worktree detail page loads artifact content` | `dashboard-dev-mode.bats` | E2E | New |

## Files Changed

| File | Change Type | Summary |
|------|-------------|---------|
| `.iw/core/dashboard/presentation/views/ArtifactView.scala` | Modified | Three back link `href` values changed from `"/"` to `s"/worktrees/$issueId"`; link and return text updated to "Back to Worktree" / "Return to worktree" |
| `.iw/core/test/ArtifactViewTest.scala` | Modified | Three test assertions updated to match new back link target and text |
| `.iw/core/test/WorktreeDetailViewTest.scala` | Modified | Three new tests appended: artifact href pattern, multiple artifacts, empty artifact list |
| `.iw/core/test/CaskServerTest.scala` | Modified | One new integration test appended; starts a real server, registers a worktree, hits the artifact endpoint, asserts back link |
| `.iw/test/dashboard-dev-mode.bats` | Modified | One new BATS test appended; starts server in dev mode, registers worktree, hits artifact endpoint, asserts content, back link href, and link text |
| `project-management/issues/IW-188/phase-04-tasks.md` | Modified | All tasks marked complete |
| `project-management/issues/IW-188/review-state.json` | Modified | Phase 4 artifacts and status added (workflow bookkeeping) |

<details>
<summary>ArtifactView.scala — exact changes</summary>

`render()` — line 34:
```scala
// Before
href := "/",
"← Back to Dashboard"

// After
href := s"/worktrees/$issueId",
"← Back to Worktree"
```

`renderError()` — header back link (line 103):
```scala
// Before
a(cls := "back-link", href := "/", "← Back to Dashboard"),

// After
a(cls := "back-link", href := s"/worktrees/$issueId", "← Back to Worktree"),
```

`renderError()` — content return link (line 109):
```scala
// Before
p(a(href := "/", "Return to dashboard"))

// After
p(a(href := s"/worktrees/$issueId", "Return to worktree"))
```

Note: `issueId` was already a parameter of both `render` and `renderError` — no signature change required.

</details>
