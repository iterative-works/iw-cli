# Phase 6: Worktree cards on project page link to detail page

## Goals

Make worktree cards clickable entries into the detail page by linking the card title (`h3`) to `/worktrees/:issueId`. The issue ID badge keeps its existing external tracker link. This completes the drill-down navigation flow: Projects > Project > Worktree (card click) > Worktree Detail Page.

1. Modify `WorktreeCardRenderer.renderCard` so the `h3` title wraps in `<a href="/worktrees/:issueId">`
2. Modify `WorktreeCardRenderer.renderSkeletonCard` so the `h3` title wraps in `<a href="/worktrees/:issueId">`
3. Keep the issue ID paragraph (`p.issue-id > a`) still linking to `data.url` (external tracker)
4. Add test coverage at all levels (unit, integration, E2E)

## Current State (after Phases 1-5)

### WorktreeCardRenderer (`WorktreeCardRenderer.scala`)

`renderCard` currently renders:
```scala
// Issue title — plain text, no link
h3(data.title),
// Issue ID as clickable link to external tracker
p(cls := "issue-id", a(href := data.url, worktree.issueId))
```

`renderSkeletonCard` currently renders:
```scala
h3(cls := "skeleton-title", "Loading..."),
p(cls := "issue-id", span(worktree.issueId))
```

There is no link from the card to the worktree detail page (`/worktrees/:issueId`). Users can only reach the detail page by typing the URL directly.

### WorktreeDetailView (Phase 1)

The detail page at `GET /worktrees/:issueId` is fully implemented with breadcrumb navigation, auto-refresh, and all data sections. It is ready to receive traffic from card links.

### Existing tests

There are no dedicated `WorktreeCardRendererTest` unit tests. Card rendering is tested indirectly through `WorktreeCardServiceTest` (which tests the service layer) and integration tests in `CaskServerTest`. E2E tests in `dashboard-dev-mode.bats` exercise cards through the full server.

## Scope

### Must do
- Wrap `h3(data.title)` in `renderCard` with `a(href := s"/worktrees/${worktree.issueId}", data.title)` so the card title links to the detail page
- Wrap `h3` in `renderSkeletonCard` with the same link pattern
- Keep `p.issue-id > a` linking to `data.url` (external tracker) — no change needed
- Create `WorktreeCardRendererTest.scala` with unit tests for the new link behavior and for verifying the existing tracker link is preserved
- Add integration test verifying card HTML in project page response contains `/worktrees/:issueId` link
- Add E2E test verifying card title links to detail page

### Out of scope
- Making the entire card clickable (only the title becomes a link)
- Changing the issue ID badge behavior (it stays linked to the external tracker)
- Changing PR links, Zed editor links, or artifact links on the card
- Visual/CSS styling changes for the title link (can be done separately)
- Card renderer behavior in non-card contexts (detail page uses its own view)

## Dependencies

- Phase 1 (complete): `WorktreeDetailView` and `GET /worktrees/:issueId` route exist and are ready to receive navigation
- Phases 2-5 (complete): Breadcrumb, error handling, artifact links, auto-refresh all work on the detail page

## Approach

### 1. Write failing unit tests (`WorktreeCardRendererTest.scala`)

Create a new test file for `WorktreeCardRenderer` directly:
- Test that `renderCard` output contains `<a href="/worktrees/TEST-123">` wrapping the title text
- Test that `renderCard` output still contains `<a href="https://tracker.example.com/TEST-123">` for the issue ID badge
- Test that `renderSkeletonCard` output contains `<a href="/worktrees/TEST-123">` wrapping the title
- Test that the title link and tracker link are distinct elements (title in `h3`, tracker in `p.issue-id`)

### 2. Modify `WorktreeCardRenderer.renderCard`

Change the `h3` from plain text to a link:
```scala
// Before:
h3(data.title),

// After:
h3(a(href := s"/worktrees/${worktree.issueId}", data.title)),
```

### 3. Modify `WorktreeCardRenderer.renderSkeletonCard`

Change the skeleton `h3` from plain text to a link:
```scala
// Before:
h3(cls := "skeleton-title", "Loading..."),

// After:
h3(cls := "skeleton-title", a(href := s"/worktrees/${worktree.issueId}", "Loading...")),
```

### 4. Write integration test

Add a test to `CaskServerTest.scala` verifying that the project details page (or dashboard) contains cards with title links to `/worktrees/:issueId`.

### 5. Write E2E test

Add a BATS test to `dashboard-dev-mode.bats` that registers a worktree, loads the dashboard or project page, and verifies the card HTML contains a link to `/worktrees/:issueId`.

### 6. Run full test suite

Verify all existing tests still pass (regression check — card rendering changes affect all card display contexts).

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/dashboard/presentation/views/WorktreeCardRenderer.scala` | Wrap `h3` title in `a(href := "/worktrees/:issueId")` in both `renderCard` and `renderSkeletonCard` |
| `.iw/core/test/WorktreeCardRendererTest.scala` | **New file.** Unit tests for title link to detail page and preserved tracker link on issue ID badge |
| `.iw/core/test/CaskServerTest.scala` | Integration test: card in project page response contains `/worktrees/:issueId` link |
| `.iw/test/dashboard-dev-mode.bats` | E2E test: card title on dashboard links to `/worktrees/:issueId` |

## Testing Strategy

### Unit tests (`WorktreeCardRendererTest.scala` — new file)
- `renderCard` output contains `href="/worktrees/TEST-123"` in `h3` element
- `renderCard` output still contains issue ID link to external tracker URL (`data.url`)
- `renderCard` title link and tracker link are both present (dual-link card)
- `renderSkeletonCard` output contains `href="/worktrees/TEST-123"` in `h3` element
- `renderSkeletonCard` issue ID is not a link (remains `span`, not `a`)

### Integration tests (`CaskServerTest.scala`)
- Card HTML returned by project details page or card endpoint contains `/worktrees/:issueId` in an anchor href
- External tracker link is still present in card HTML

### E2E tests (`dashboard-dev-mode.bats`)
- Register a worktree, fetch dashboard, verify response contains `href="/worktrees/` pattern in card HTML
- Verify the external tracker link is still present alongside the detail page link

## Acceptance Criteria

From Story 6 and analysis decisions:

- [ ] Clicking the card title (`h3`) navigates to `/worktrees/:issueId` (detail page)
- [ ] The issue ID badge (`p.issue-id > a`) still links to the external tracker URL
- [ ] Skeleton cards also link their title to `/worktrees/:issueId`
- [ ] PR links, Zed editor links, and artifact links on the card are unchanged
- [ ] Change applies consistently across all card display contexts (project page, root dashboard, card refresh endpoint, OOB additions)
- [ ] All existing tests pass (regression — shared renderer affects all contexts)
