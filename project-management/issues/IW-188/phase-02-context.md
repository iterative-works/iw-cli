# Phase 2: Breadcrumb navigation with project context

## Goals

Verify and strengthen the breadcrumb implementation that was already built in Phase 1. The breadcrumb rendering and project name derivation are fully implemented in `WorktreeDetailView` and wired up in `CaskServer`. This phase ensures the implementation meets Story 2 acceptance criteria through targeted test improvements and any gap fixes found during verification.

## Current State (after Phase 1)

Phase 1 implemented breadcrumbs as part of the core detail page. The following already exists:

### Rendering (`WorktreeDetailView.scala`)
- `renderBreadcrumb(issueId, projectName)` private method (lines 72-89)
- Three-level breadcrumb: `Projects > {projectName} > {issueId}` when `projectName = Some(name)`
  - "Projects" links to `/`
  - `{projectName}` links to `/projects/$name`
  - `{issueId}` is plain text (current page)
- Two-level breadcrumb: `Projects > {issueId}` when `projectName = None`
  - "Projects" links to `/`
  - `{issueId}` is plain text
- `renderNotFound` also includes a two-level breadcrumb: `Projects > {issueId}`
- All breadcrumbs use `<nav class="breadcrumb">` matching `ProjectDetailsView` pattern

### Route wiring (`CaskServer.scala`)
- `worktreeDetail` route (line 143) derives `projectName` via:
  ```scala
  val projectName = MainProject.deriveMainProjectPath(worktree.path)
    .map(path => os.Path(path).last)
  ```
- Passes `projectName` to `WorktreeDetailView.render()`

### CSS (`dashboard.css`)
- `.breadcrumb` styles already exist (lines 735-752), shared with `ProjectDetailsView`

### Existing tests (`WorktreeDetailViewTest.scala`)
- "render shows breadcrumb with project name when derivable" — checks presence of project name, issue ID, "Projects", and root link
- "render shows breadcrumb without project name when not derivable" — checks "Projects", issue ID, root link
- "renderNotFound includes breadcrumb" — checks "Projects" text and `breadcrumb` class

### Test gaps identified
1. **No assertion on project link URL**: The "with project name" test checks that "iw-cli" appears in the HTML but does not verify `href="/projects/iw-cli"` — the link could be missing or malformed and the test would pass
2. **No assertion on three-level structure**: The test doesn't verify that the breadcrumb has three segments (Projects > projectName > issueId) in the correct order
3. **No assertion that issueId is NOT a link**: Story 2 specifies `{issueId}` should be unlinked text (current page), but no test verifies this
4. **Integration test doesn't check breadcrumb**: `CaskServerTest` worktree detail test checks for `worktree-detail` class and skeleton state but never asserts breadcrumb presence or content
5. **No E2E test**: No BATS test for the worktree detail page at all (the E2E test strategy from the analysis calls for one)

## Scope

### Must do
- Add unit test assertions for the project link URL (`href="/projects/iw-cli"`)
- Add unit test assertion that `{issueId}` in breadcrumb is NOT wrapped in an `<a>` tag (it's the current page)
- Add integration test assertion in `CaskServerTest` that the worktree detail response contains breadcrumb markup
- Verify the existing implementation matches Story 2 acceptance criteria end-to-end

### Should do
- Add a BATS E2E test that registers a worktree, hits `/worktrees/{issueId}`, and checks breadcrumb presence in the HTML response

### Out of scope
- Changing the breadcrumb visual style (already matches `ProjectDetailsView`)
- Adding HTMX behavior to breadcrumbs (not specified)
- Changing the `renderBreadcrumb` implementation (already correct per Story 2 spec)

## Dependencies

- Phase 1 (complete): `WorktreeDetailView` with `renderBreadcrumb`, `CaskServer` route with project name derivation

## Approach

### 1. Strengthen unit tests in `WorktreeDetailViewTest.scala`

**Test: "render shows breadcrumb with project name when derivable"** — add:
```scala
assert(html.contains("href=\"/projects/iw-cli\""), "Should link project name to project page")
```

**New test: "breadcrumb issueId is not a link (current page)"** — verify:
```scala
// The issue ID in the breadcrumb should be a <span>, not an <a>
// The breadcrumb nav should NOT contain a link with href="/worktrees/IW-188"
assert(!html.contains("href=\"/worktrees/IW-188\""), "Issue ID in breadcrumb should not be a link")
```

**Test: "renderNotFound includes breadcrumb"** — add:
```scala
assert(html.contains("href=\"/\""), "Not-found breadcrumb should link to root")
```

### 2. Strengthen integration test in `CaskServerTest.scala`

In `"GET /worktrees/:issueId returns 200 with HTML for known worktree"` — add:
```scala
assert(html.contains("breadcrumb"), "Response should contain breadcrumb navigation")
assert(html.contains("Projects"), "Breadcrumb should contain Projects link")
```

### 3. Add BATS E2E test

Create a test in the E2E suite that:
1. Starts the dashboard server
2. Registers a worktree via `PUT /api/v1/worktrees/{issueId}`
3. Hits `GET /worktrees/{issueId}`
4. Asserts response contains `breadcrumb` class and "Projects" text

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/test/WorktreeDetailViewTest.scala` | Add/strengthen breadcrumb assertions (project link URL, issueId not a link) |
| `.iw/core/test/CaskServerTest.scala` | Add breadcrumb assertions to existing worktree detail integration test |
| `.iw/test/dashboard-dev-mode.bats` (or new BATS file) | Add E2E test for worktree detail breadcrumb (if feasible within existing test infrastructure) |

## Testing Strategy

### Unit tests (strengthen existing)
- Verify `href="/projects/iw-cli"` appears in three-level breadcrumb
- Verify issue ID is rendered as `<span>`, not `<a>`, in breadcrumb
- Verify not-found breadcrumb links to root

### Integration tests (strengthen existing)
- Verify breadcrumb navigation is present in the full HTTP response from `GET /worktrees/:issueId`

### E2E tests (new)
- BATS test hitting `/worktrees/{issueId}` and checking for breadcrumb content in the response

## Acceptance Criteria

From Story 2:
- [x] Breadcrumb shows "Projects > {projectName} > {issueId}" when project is derivable from worktree path — **implemented in Phase 1**
- [x] Breadcrumb shows "Projects > {issueId}" when project cannot be derived — **implemented in Phase 1**
- [x] All links in breadcrumb navigate correctly — **implemented in Phase 1, needs stronger test coverage**
- [x] Styled consistently with `ProjectDetailsView` breadcrumb — **shares same CSS class and HTML structure**

**Remaining work:** Strengthen test coverage to prove the acceptance criteria are met, rather than relying on manual verification.
