# Phase 3: Handle unknown worktree gracefully

## Goals

Verify and strengthen the not-found handling that was already built in Phase 1. The `renderNotFound` view and 404 route logic are fully implemented. This phase ensures the implementation meets Story 5 acceptance criteria through gap analysis, targeted test additions, and edge case coverage (invalid characters in issue ID, XSS safety, empty/long IDs).

## Current State (after Phase 1 and Phase 2)

Phase 1 implemented the core not-found handling. Phase 2 added breadcrumb test coverage. The following already exists:

### Rendering (`WorktreeDetailView.scala`, lines 52-70)
- `renderNotFound(issueId: String): Frag` public method
- Renders breadcrumb: `Projects > {issueId}` (two-level, "Projects" links to `/`)
- Renders "Worktree Not Found" heading
- Renders explanation: "Worktree '{issueId}' is not registered."
- Renders registration hint: "Run './iw register' from the worktree directory to register it."
- Renders "Back to Projects Overview" link (`href="/"`)
- Wrapped in `div(cls := "worktree-detail")` for consistent styling
- Uses Scalatags which auto-escapes string content (XSS-safe by default)

### Route wiring (`CaskServer.scala`, lines 143-160)
- `GET /worktrees/:issueId` route handles `None` case from `state.worktrees.get(issueId)`
- Returns `renderNotFound(issueId)` wrapped in `PageLayout.render(...)` with `statusCode = 404`
- Content-Type is `text/html; charset=UTF-8`
- The `issueId` comes from Cask's URL path parameter extraction (`:issueId`)

### Existing unit tests (`WorktreeDetailViewTest.scala`, lines 218-240)
- "renderNotFound includes the issue ID" -- checks `IW-999` appears in output
- "renderNotFound includes link back to overview" -- checks `href="/"`
- "renderNotFound includes breadcrumb" -- checks "Projects", `breadcrumb` class, `href="/"`
- "renderNotFound shows not found heading and explanation" -- checks "Worktree Not Found" and "not registered"

### Existing integration tests (`CaskServerTest.scala`, lines 1083-1113)
- "GET /worktrees/NONEXISTENT returns 404 with error page" -- checks:
  - Status code 404
  - Content-Type text/html
  - Body contains "NONEXISTENT-999"
  - Body contains "not registered" or "Not Found"
  - Body contains "worktree-detail" class (styled page, not raw error)
  - Body contains "breadcrumb" class
  - Body contains "Projects" link text

### Existing E2E test (`dashboard-dev-mode.bats`, lines 128-177)
- "GET /worktrees/:issueId returns breadcrumb navigation" -- tests a *known* worktree only
- No E2E test for the 404/not-found case

## Gap Analysis

### Covered by existing tests
1. Unknown issue ID returns 404 with user-friendly page -- **covered** (integration test)
2. Page includes "Back to Projects Overview" link -- **covered** (unit test checks `href="/"`)
3. Page is wrapped in `PageLayout` -- **covered** (integration test verifies styled page via `worktree-detail` class)
4. Breadcrumb present on not-found page -- **covered** (unit + integration tests)

### NOT covered - test gaps
1. **No E2E test for 404 page**: The BATS E2E suite only tests a known worktree. There is no E2E test hitting `/worktrees/NONEXISTENT-999` and verifying the not-found response.
2. **No test for special characters in issue ID** (Scenario 2 from Gherkin): What happens when the URL contains characters like `<script>`, `../`, `%00`, spaces, or Unicode? Scalatags escapes string output, but we should verify this with a test.
3. **No test for very long issue ID**: What happens with an extremely long string in the URL path?
4. **No unit test verifying "Back to Projects Overview" link text**: The existing test only checks `href="/"` but not the anchor text itself.
5. **No test that not-found page does NOT contain worktree data sections**: Should verify that sections like `git-status`, `pr-link`, `progress-bar` are absent from the not-found page.

### NOT covered - potential implementation gaps
1. **No input validation on issue ID in the route**: The route accepts any string. Cask's `:issueId` path parameter will match anything between slashes. This is fine for the lookup (it just won't find a match), but we should verify the behavior with edge-case inputs.
2. **No explicit handling of URL-encoded special characters**: Cask likely decodes `%2F` etc. before passing to the handler. Worth testing.

## Scope

### Must do
- Add E2E test (BATS) for 404 page: hit `/worktrees/NONEXISTENT-999`, verify 404 status, verify "Not Found" content
- Add unit test for special characters in issue ID passed to `renderNotFound` (verify Scalatags escaping works, no XSS)
- Add unit test verifying the "Back to Projects Overview" link text (not just `href`)
- Add unit test verifying not-found page does NOT contain worktree data sections

### Should do
- Add integration test for issue ID with special characters (e.g., `<script>alert(1)</script>`)
- Add unit test for empty string issue ID passed to `renderNotFound`
- Verify all acceptance criteria from Story 5 are met

### Out of scope
- Input validation/rejection of malformed issue IDs at the route level (the dashboard is an internal tool; any string that doesn't match a registered worktree simply gets the not-found page)
- Rate limiting or abuse prevention on the 404 page
- Changing the not-found page visual design

## Dependencies

- Phase 1 (complete): `WorktreeDetailView.renderNotFound`, `CaskServer` 404 route handling
- Phase 2 (complete): Breadcrumb test coverage (strengthened assertions)

## Approach

### 1. Add unit tests in `WorktreeDetailViewTest.scala`

**New test: "renderNotFound escapes special characters in issue ID"**
```scala
val html = WorktreeDetailView.renderNotFound("<script>alert(1)</script>").render
assert(!html.contains("<script>"), "Should HTML-escape special characters in issue ID")
assert(html.contains("&lt;script&gt;"), "Should contain escaped version of the issue ID")
```

**New test: "renderNotFound with empty issue ID"**
```scala
val html = WorktreeDetailView.renderNotFound("").render
assert(html.contains("Worktree Not Found"), "Should still show not-found heading for empty ID")
assert(html.contains("href=\"/\""), "Should still link back to overview")
```

**New test: "renderNotFound includes 'Back to Projects Overview' link text"**
```scala
val html = WorktreeDetailView.renderNotFound("IW-999").render
assert(html.contains("Back to Projects Overview"), "Should include link text for overview navigation")
```

**New test: "renderNotFound does not contain worktree data sections"**
```scala
val html = WorktreeDetailView.renderNotFound("IW-999").render
assert(!html.contains("git-status"), "Not-found page should not have git status section")
assert(!html.contains("pr-link"), "Not-found page should not have PR section")
assert(!html.contains("progress-bar"), "Not-found page should not have progress bar")
assert(!html.contains("phase-info"), "Not-found page should not have phase info")
assert(!html.contains("zed-link"), "Not-found page should not have Zed editor link")
```

### 2. Add integration test in `CaskServerTest.scala`

**New test: "GET /worktrees/ with special characters returns 404 with escaped content"**
```scala
// URL-encode a string with HTML special characters and verify the response
// escapes them properly (no raw HTML injection in the 404 page)
```

### 3. Add E2E test in `dashboard-dev-mode.bats`

**New test: "GET /worktrees/NONEXISTENT-999 returns not-found page"**
```bash
# Start dev server, hit /worktrees/NONEXISTENT-999
# Verify HTTP status is 404
# Verify response contains "Not Found" or "not registered"
# Verify response contains link back to overview
```

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/test/WorktreeDetailViewTest.scala` | Add unit tests for special characters, empty ID, link text, absent data sections |
| `.iw/core/test/CaskServerTest.scala` | Add integration test for special characters in issue ID |
| `.iw/test/dashboard-dev-mode.bats` | Add E2E test for 404 not-found page |

## Testing Strategy

### Unit tests (new)
- Verify Scalatags HTML-escapes special characters in the issue ID (XSS prevention)
- Verify empty issue ID renders a valid not-found page
- Verify "Back to Projects Overview" link text is present
- Verify worktree data sections (git-status, pr-link, progress-bar, phase-info, zed-link) are absent from not-found page

### Integration tests (new)
- Verify special characters in URL path produce a 404 with properly escaped HTML

### E2E tests (new)
- Verify hitting `/worktrees/NONEXISTENT-999` on a running server returns a not-found page with correct content

## Acceptance Criteria

From Story 5:
- [x] Unknown issue IDs display a user-friendly "not found" page -- **implemented in Phase 1, covered by unit + integration tests**
- [x] The page includes a link back to the projects overview -- **implemented in Phase 1, needs test for link text**
- [x] Page is wrapped in `PageLayout` with consistent styling -- **implemented in Phase 1, covered by integration test**
- [ ] Special characters in issue ID are handled safely (no XSS) -- **needs unit + integration test**
- [ ] E2E test proves the 404 flow works end-to-end -- **needs BATS test**
- [ ] Not-found page does not accidentally render worktree data sections -- **needs unit test**

**Remaining work:** Add targeted tests to close the gaps identified above. The implementation itself is complete from Phase 1; this phase is purely about proving correctness through test coverage.
