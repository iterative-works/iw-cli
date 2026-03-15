# Phase 6 Tasks: Worktree cards on project page link to detail page

## Setup

- [x] [setup] Read `WorktreeCardRenderer.scala` lines 56-135 to confirm current `renderCard` structure: note that `h3(data.title)` on line 95 is plain text and `p(cls := "issue-id", a(href := data.url, worktree.issueId))` on lines 97-103 links to the external tracker
- [x] [setup] Read `WorktreeCardRenderer.scala` lines 218-254 to confirm `renderSkeletonCard` structure: note `h3(cls := "skeleton-title", "Loading...")` on line 233 and `p(cls := "issue-id", span(worktree.issueId))` on lines 234-237
- [x] [setup] Read `WorktreeCardServiceTest.scala` lines 1-54 to understand the fixture pattern (`WorktreeRegistration`, `IssueData`, `CachedIssue`) used in existing card tests — `WorktreeCardRendererTest` will follow the same fixture style
- [x] [setup] Read `CaskServerTest.scala` lines 1-80 to understand the server start helper pattern and how integration tests register worktrees before asserting on page content

## Tests

### Unit Tests — WorktreeCardRendererTest.scala (TDD red phase, new file)

- [x] [test] Create `WorktreeCardRendererTest.scala` in `.iw/core/test/` with package `iw.tests` and the standard two-line PURPOSE header; extend `munit.FunSuite`
- [x] [test] Add test "renderCard h3 title links to worktree detail page": construct `WorktreeRegistration` (issueId = "TEST-123") and `IssueData` (title = "My Issue", url = "https://tracker.example.com/TEST-123"), call `WorktreeCardRenderer.renderCard(...)`, assert output string contains `href="/worktrees/TEST-123"` inside an `h3` element
- [x] [test] Add test "renderCard issue ID badge still links to external tracker": use same fixtures, call `renderCard`, assert output string contains `href="https://tracker.example.com/TEST-123"` inside a `p` with class `issue-id`
- [x] [test] Add test "renderCard has both detail page link and tracker link": use same fixtures, assert output contains `href="/worktrees/TEST-123"` AND `href="https://tracker.example.com/TEST-123"` (dual-link card)
- [x] [test] Add test "renderCard title link wraps title text inside h3": assert output contains `<h3` followed by `href="/worktrees/TEST-123"` followed by `My Issue` and then `</h3>` (title text inside the anchor inside h3)
- [x] [test] Add test "renderSkeletonCard h3 title links to worktree detail page": construct `WorktreeRegistration` (issueId = "TEST-123"), call `WorktreeCardRenderer.renderSkeletonCard(...)`, assert output contains `href="/worktrees/TEST-123"` inside an element with class `skeleton-title`
- [x] [test] Add test "renderSkeletonCard issue ID is not a link": call `renderSkeletonCard`, assert output does NOT contain `<a` inside the `issue-id` paragraph (issue ID remains a `span`, not an anchor)
- [x] [test] Run `WorktreeCardRendererTest` and confirm the new tests fail (TDD red phase — title links do not exist yet)

### Integration Tests — CaskServerTest.scala (TDD red phase)

- [x] [test] Add test "GET /projects/:projectName card HTML contains worktree detail page link": start test server, register a worktree (issueId = "TEST-123"), fetch a page that renders cards (project page or card endpoint), assert response body contains `href="/worktrees/TEST-123"`
- [x] [test] Add test "GET /worktrees/:issueId/card response contains detail page link": start test server, register a worktree (issueId = "TEST-123"), fetch `/worktrees/TEST-123/card`, assert response body contains `href="/worktrees/TEST-123"` and also contains the external tracker URL
- [x] [test] Run `CaskServerTest` and confirm the new tests fail (TDD red phase — card does not contain detail page link yet)

## Implementation

### Modify `renderCard` (WorktreeCardRenderer.scala)

- [x] [impl] In `WorktreeCardRenderer.renderCard`, change `h3(data.title)` (line 95) to `h3(a(href := s"/worktrees/${worktree.issueId}", data.title))` — wraps the title text in an anchor pointing to the detail page; leave all other card elements unchanged
- [x] [impl] Run `WorktreeCardRendererTest` and confirm the `renderCard` title-link tests now pass; confirm the `renderSkeletonCard` tests still fail

### Modify `renderSkeletonCard` (WorktreeCardRenderer.scala)

- [x] [impl] In `WorktreeCardRenderer.renderSkeletonCard`, change `h3(cls := "skeleton-title", "Loading...")` (line 233) to `h3(cls := "skeleton-title", a(href := s"/worktrees/${worktree.issueId}", "Loading..."))` — wraps the skeleton title text in an anchor pointing to the detail page
- [x] [impl] Run `WorktreeCardRendererTest` and confirm all unit tests now pass (TDD green phase)
- [x] [impl] Run `CaskServerTest` and confirm the new integration tests now pass (TDD green phase)
- [x] [impl] Run full unit test suite (`./iw test unit`) to verify no regressions in any existing card rendering tests

## Integration

- [x] [integration] Add E2E test in `dashboard-dev-mode.bats`: "worktree card contains detail page link" — start dev server, register a worktree via API, fetch card endpoint, assert response contains `href="/worktrees/` pattern in a card anchor
- [x] [integration] Add E2E test: "worktree card still contains external tracker link section" — start dev server, register a worktree, fetch card endpoint, assert response contains both `href="/worktrees/` (detail link) and `class="issue-id"` (tracker link section still present)
- [x] [integration] Add E2E test: "card endpoint response contains detail page link" — start dev server, register a worktree via API, fetch `/worktrees/TEST-1/card`, assert response contains `href="/worktrees/TEST-1"`
- [x] [integration] Run full test suite (`./iw test`) to confirm all tests pass
**Phase Status:** Complete
