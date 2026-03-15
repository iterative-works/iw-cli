---
generated_from: 18f284dba1a585d0c2fdc5694ea53fcfb4f6f1da
generated_at: 2026-03-15T17:31:33Z
branch: IW-188-phase-06
issue_id: IW-188
phase: 6
files_analyzed:
  - .iw/core/dashboard/presentation/views/WorktreeCardRenderer.scala
  - .iw/core/test/WorktreeCardRendererTest.scala
  - .iw/core/test/CaskServerTest.scala
  - .iw/test/dashboard-dev-mode.bats
---

# Review Packet: Phase 6 - Worktree Cards Link to Detail Page

## Goals

This phase completes the drill-down navigation flow (Projects > Project > Worktree) by making the card title (`h3`) in every worktree card a clickable link to the worktree detail page at `/worktrees/:issueId`. The issue ID badge continues to link to the external tracker as before.

Key objectives:

- Wrap the `h3` title in `renderCard` with `<a href="/worktrees/:issueId">` so clicking the card title navigates to the detail page
- Apply the same link to the `h3` in `renderSkeletonCard` so cards in loading state also provide navigation
- Preserve the existing issue ID badge link to the external tracker (`p.issue-id > a` pointing to `data.url`) — no behavior change there
- Provide test coverage at all three levels: unit, integration, and E2E

## Scenarios

- [ ] Clicking the card title (`h3`) navigates to `/worktrees/:issueId` (detail page)
- [ ] The issue ID badge (`p.issue-id > a`) still links to the external tracker URL
- [ ] Skeleton cards also link their title to `/worktrees/:issueId`
- [ ] PR links, Zed editor links, and artifact links on the card are unchanged
- [ ] Change applies consistently across all card display contexts (project page, root dashboard, card refresh endpoint, OOB additions)
- [ ] All existing tests pass (regression — shared renderer affects all card contexts)

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/dashboard/presentation/views/WorktreeCardRenderer.scala` | `renderCard()` | Core change: `h3` title now wraps in `<a href="/worktrees/:issueId">` |
| `.iw/core/dashboard/presentation/views/WorktreeCardRenderer.scala` | `renderSkeletonCard()` | Secondary change: skeleton `h3` receives the same link |
| `.iw/core/test/WorktreeCardRendererTest.scala` | `WorktreeCardRendererTest` | New unit test file validating both links in the rendered HTML |
| `.iw/core/test/CaskServerTest.scala` | `"GET /worktrees/:issueId/card response contains detail page link"` | Integration test verifying the card endpoint returns a card with the detail link |
| `.iw/test/dashboard-dev-mode.bats` | `"worktree card contains detail page link"` | E2E test verifying the card endpoint returns the correct link against a live server |

## Diagrams

### Card HTML Structure (before and after)

```
Before Phase 6                         After Phase 6
─────────────────────────────────      ─────────────────────────────────
div.worktree-card                      div.worktree-card
  h3                                     h3
    "My Issue Title"          →            a[href="/worktrees/IW-188"]
  p.issue-id                                 "My Issue Title"
    a[href="https://tracker…"]           p.issue-id
      "IW-188"                             a[href="https://tracker…"]
  …                                          "IW-188"
                                         …
```

### Navigation Flow Completed

```
[Projects Page /]
       │
       ▼
[Project Page /projects/:name]
       │
       ▼ (card title click — added in Phase 6)
[Worktree Detail Page /worktrees/:issueId]
       │
       ▼ (artifact link — Phase 4)
[Artifact View /worktrees/:issueId/artifacts?path=…]
```

### Renderer Usage Contexts

`WorktreeCardRenderer` is shared across all card display surfaces. Phase 6 affects all of them uniformly:

```
GET /                         → dashboard cards     → renderCard / renderSkeletonCard
GET /projects/:name           → project page cards  → renderCard / renderSkeletonCard
GET /worktrees/:issueId/card  → card refresh        → renderCard / renderSkeletonCard
POST (OOB addition)           → new worktree cards  → renderCard / renderSkeletonCard
```

## Test Summary

### Unit Tests (`WorktreeCardRendererTest.scala` — new file)

| Test | Type | What It Checks |
|------|------|----------------|
| `renderCard h3 title links to worktree detail page` | Unit | `href="/worktrees/TEST-123"` is present in rendered card HTML |
| `renderCard issue ID badge still links to external tracker` | Unit | `href="https://tracker.example.com/TEST-123"` is still present |
| `renderCard has both detail page link and tracker link` | Unit | Both links coexist in the same card |
| `renderCard title link wraps title text inside h3` | Unit | Exact HTML structure: `<h3><a href="/worktrees/TEST-123">My Issue</a></h3>` |
| `renderSkeletonCard h3 title links to worktree detail page` | Unit | Skeleton card also contains `href="/worktrees/TEST-123"` |
| `renderSkeletonCard issue ID is not a link` | Unit | Skeleton `p.issue-id` contains a `span`, not an `a` element |

### Integration Tests (`CaskServerTest.scala` — additions)

| Test | Type | What It Checks |
|------|------|----------------|
| `GET /worktrees/:issueId/card response contains detail page link` | Integration | Card endpoint HTML contains `href="/worktrees/TEST-CARD"` |
| `GET /worktrees/:issueId/card response contains both detail page and tracker links` | Integration | Card HTML contains both the detail link and the `issue-id` section |

### E2E Tests (`dashboard-dev-mode.bats` — additions)

| Test | Type | What It Checks |
|------|------|----------------|
| `worktree card contains detail page link` | E2E | Live server card endpoint returns HTML with `href="/worktrees/TEST-LINK"` |
| `worktree card still contains external tracker link section` | E2E | Live server card HTML contains both the detail link and `class="issue-id"` |
| `card endpoint response contains detail page link` | E2E | Separate worktree registration and card fetch confirms the link |

## Files Changed

| File | Change Type | Summary |
|------|-------------|---------|
| `.iw/core/dashboard/presentation/views/WorktreeCardRenderer.scala` | Modified | `renderCard`: `h3(data.title)` becomes `h3(a(href := s"/worktrees/${worktree.issueId}", data.title))`. `renderSkeletonCard`: `h3(cls := "skeleton-title", "Loading...")` becomes `h3(cls := "skeleton-title", a(href := s"/worktrees/${worktree.issueId}", "Loading..."))`. The `p.issue-id > a` tracker link is unchanged. |
| `.iw/core/test/WorktreeCardRendererTest.scala` | New | Six unit tests directly instantiating `WorktreeCardRenderer` and asserting on rendered HTML string. Covers `renderCard` dual-link structure and `renderSkeletonCard` link/no-link behavior. |
| `.iw/core/test/CaskServerTest.scala` | Modified | Two new integration tests: one verifying `href="/worktrees/TEST-CARD"` in the card endpoint response, one verifying both the detail link and the tracker `issue-id` section coexist. |
| `.iw/test/dashboard-dev-mode.bats` | Modified | Three new E2E tests verifying card title links against a live `--dev` server: presence of detail page link, preservation of `issue-id` section, and a standalone card endpoint fetch. |

<details>
<summary>Key code change in WorktreeCardRenderer.scala</summary>

```scala
// renderCard — was:
h3(data.title),

// renderCard — now:
h3(a(href := s"/worktrees/${worktree.issueId}", data.title)),

// renderSkeletonCard — was:
h3(cls := "skeleton-title", "Loading..."),

// renderSkeletonCard — now:
h3(cls := "skeleton-title", a(href := s"/worktrees/${worktree.issueId}", "Loading...")),
```

The `p.issue-id` block is untouched:

```scala
p(
  cls := "issue-id",
  a(href := data.url, worktree.issueId)
)
```

</details>
