# Story-Driven Analysis: Dashboard - Add dedicated worktree detail page with auto-refresh

**Issue:** IW-188
**Created:** 2026-03-14
**Status:** Draft
**Classification:** Feature

## Problem Statement

When working on a specific issue, finding relevant information requires scanning through the project's worktree list. The card grid layout compresses information into small cards, making it hard to see full details at a glance. There is no dedicated, stable URL for a single worktree that can be bookmarked, shared, or used as a reference while working.

The hierarchical navigation redesign introduces a Projects > Project > Worktree > Artifact drill-down. The project details page (#206) is already implemented. This issue adds the **Worktree** level: a dedicated page at `/worktrees/:issueId` that shows complete context for a single worktree with auto-refresh.

## User Stories

### Story 1: Worktree detail page with complete context

```gherkin
Funkce: Detailní stránka worktree se vším kontextem
  Jako vývojář
  Chci vidět dedikovanou stránku pro konkrétní worktree
  Aby jsem měl veškerý kontext na jednom místě bez nutnosti hledat v seznamu

Scénář: Zobrazení kompletního kontextu worktree
  Pokud existuje worktree pro issue "IW-188"
  A issue má název "Dashboard: Add worktree detail page"
  A issue má stav "In Progress" a přiřazeného "Michal"
  A worktree má branch "IW-188" se stavem "clean"
  A worktree má workflow ve fázi 2/3 se 4/7 úkoly
  A worktree má otevřený PR #42
  A worktree má review stav s artefakty
  Když otevřu /worktrees/IW-188
  Pak vidím název issue "Dashboard: Add worktree detail page"
  A vidím stav "In Progress" a přiřazeného "Michal"
  A vidím git branch "IW-188" se stavem "clean"
  A vidím průběh workflow "Phase 2/3: 4/7 tasks"
  A vidím odkaz na PR #42 se stavem "Open"
  A vidím sekci review artefaktů
  A vidím odkaz pro otevření v editoru Zed

Scénář: Worktree bez některých dat
  Pokud existuje worktree pro issue "IW-200"
  A issue data ještě nebyla načtena z API
  A worktree nemá PR
  A worktree nemá review stav
  Když otevřu /worktrees/IW-200
  Pak vidím skeleton stav pro issue data
  A sekce PR se nezobrazuje
  A sekce review artefaktů se nezobrazuje
```

**Estimated Effort:** 8-12h
**Complexity:** Moderate

**Technical Feasibility:**
All data needed for the detail page is already fetched and rendered by the existing card infrastructure (`WorktreeCardService`, `WorktreeCardRenderer`). The detail page uses the same data sources but with a full-page layout instead of a compact card. The main work is creating a new view component (`WorktreeDetailView`) and a new route in `CaskServer`. The data fetching follows the exact same pattern as `/projects/:projectName` -- read from caches in `ServerStateService`, with the card-level HTMX endpoint handling fresh fetches.

**Acceptance:**
- `GET /worktrees/:issueId` returns a full HTML page with all available worktree data
- All data sections render correctly: issue title/status/assignee, git status, workflow progress, PR link/state, review artifacts, Zed editor link
- Page renders gracefully when data sections are missing (skeleton states, absent sections)
- URL is stable and bookmarkable

---

### Story 2: Breadcrumb navigation with project context

```gherkin
Funkce: Drobečková navigace s kontextem projektu
  Jako vývojář
  Chci se snadno vrátit na stránku projektu nebo přehled
  Aby jsem mohl přepínat mezi úrovněmi navigace

Scénář: Zobrazení drobečkové navigace
  Pokud jsem na detailní stránce worktree "IW-188"
  A worktree patří k projektu "iw-cli"
  Když se stránka načte
  Pak vidím drobečkovou navigaci "Projects > iw-cli > IW-188"
  A odkaz "Projects" vede na "/"
  A odkaz "iw-cli" vede na "/projects/iw-cli"
  A "IW-188" je neodsazený text (aktuální stránka)

Scénář: Worktree bez rozpoznaného projektu
  Pokud jsem na detailní stránce worktree "IW-188"
  A worktree cestu nelze přiřadit žádnému projektu
  Když se stránka načte
  Pak vidím drobečkovou navigaci "Projects > IW-188"
  A odkaz "Projects" vede na "/"
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
The project name can be derived from the worktree path using `MainProject.deriveMainProjectPath`, which already extracts the main project directory name. If the path matches a known project, the breadcrumb links to `/projects/:projectName`. If not, the breadcrumb falls back to just `Projects > issueId`. This is the same approach the card renderer uses -- the project name derivation is a pure function already tested.

**Acceptance:**
- Breadcrumb shows "Projects > {projectName} > {issueId}" when project is derivable
- Breadcrumb shows "Projects > {issueId}" when project cannot be derived
- All links in breadcrumb navigate correctly
- Styled consistently with `ProjectDetailsView` breadcrumb

---

### Story 3: HTMX auto-refresh for worktree detail content

```gherkin
Funkce: Automatická aktualizace detailu worktree
  Jako vývojář
  Chci aby se informace na detailní stránce worktree automaticky aktualizovaly
  Aby jsem viděl aktuální stav bez nutnosti obnovovat stránku

Scénář: Automatická aktualizace obsahu
  Pokud jsem na detailní stránce worktree "IW-188"
  Když uplyne 30 sekund
  Pak se obsahová sekce stránky obnoví přes HTMX polling
  A zobrazí aktuální data (stav issue, git status, workflow průběh)

Scénář: Aktualizace po změně stavu
  Pokud jsem na detailní stránce worktree "IW-188"
  A stav issue se změní z "In Progress" na "In Review"
  Když uplyne 30 sekund polling interval
  Pak vidím aktualizovaný stav "In Review"
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
The existing card-level HTMX refresh pattern (`GET /worktrees/:issueId/card` with `hx-trigger="every 30s"`) already handles all data fetching and HTML regeneration. For the detail page, the simplest approach is to have the content section poll a dedicated endpoint (e.g., `GET /worktrees/:issueId/detail-content`) that returns just the content fragment (not the full page shell). Alternatively, the existing `/worktrees/:issueId/card` endpoint could be reused if the detail view content section uses a card-compatible structure. The key decision is whether to create a separate content refresh endpoint or reuse the existing card endpoint.

**Acceptance:**
- Detail page content refreshes automatically every 30 seconds
- Fresh data from API is fetched and displayed (via existing `WorktreeCardService` throttling)
- Page does not flicker or lose scroll position during refresh
- HTMX `hx-swap` replaces only the content area, not the full page

---

### Story 4: Artifact links to artifact detail view

```gherkin
Funkce: Odkazy na artefakty v detailu worktree
  Jako vývojář
  Chci kliknout na artefakt v review stavu
  Aby jsem mohl přímo zobrazit obsah artefaktu

Scénář: Kliknutí na artefakt
  Pokud jsem na detailní stránce worktree "IW-188"
  A review stav obsahuje artefakt "Analysis" s cestou "project-management/issues/IW-188/analysis.md"
  Když kliknu na odkaz "Analysis"
  Pak jsem přesměrován na /worktrees/IW-188/artifacts?path=project-management/issues/IW-188/analysis.md

Scénář: Více artefaktů
  Pokud jsem na detailní stránce worktree "IW-188"
  A review stav obsahuje artefakty "Analysis" a "Phase Context"
  Když se stránka načte
  Pak vidím seznam artefaktů s odkazy na oba artefakty
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
The `WorktreeCardRenderer.renderReviewArtifacts` already renders artifact links pointing to `/worktrees/:issueId/artifacts?path=...`. The artifact viewer endpoint already exists and works. For the detail page, we can reuse this exact rendering logic, or extract it to render the artifact list in a more spacious layout. The existing `ArtifactView` handles the artifact display. No new endpoints needed.

**Acceptance:**
- Each artifact in review state is rendered as a clickable link
- Links follow existing pattern: `/worktrees/:issueId/artifacts?path=...`
- Artifact detail view loads correctly when clicking a link

---

### Story 5: Handle unknown worktree gracefully

```gherkin
Funkce: Zpracování neexistující worktree
  Jako vývojář
  Chci vidět srozumitelnou chybovou zprávu
  Aby jsem věděl že worktree neexistuje

Scénář: Neznámá worktree
  Pokud neexistuje registrovaná worktree pro issue "NONEXISTENT-999"
  Když otevřu /worktrees/NONEXISTENT-999
  Pak vidím stránku s informací že worktree nebyla nalezena
  A vidím odkaz zpět na přehled projektů

Scénář: Worktree s neplatnými znaky v ID
  Pokud otevřu /worktrees/ s neplatným issue ID
  Když se stránka načte
  Pak vidím srozumitelnou chybovou stránku
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
The route handler checks `state.worktrees.get(issueId)`. If `None`, return a styled not-found page (same pattern as `ProjectDetailsView.renderNotFound`). The Cask framework handles URL encoding/decoding for path parameters. Issue IDs typically follow the pattern `PREFIX-NUMBER` (e.g., IW-188, IWLE-123) and should not contain special characters that need escaping.

**Acceptance:**
- Unknown issue IDs display a user-friendly "not found" page
- The page includes a link back to the projects overview
- Page is wrapped in `PageLayout` with consistent styling

---

### Story 6: Worktree cards on project page link to detail page

```gherkin
Funkce: Propojení worktree karet s detailní stránkou
  Jako vývojář
  Chci kliknout na worktree kartu na stránce projektu
  Aby jsem se dostal na detailní stránku worktree

Scénář: Kliknutí na issue ID na kartě
  Pokud jsem na stránce projektu "iw-cli"
  A existuje worktree karta pro "IW-188"
  Když kliknu na issue ID "IW-188" na kartě
  Pak jsem přesměrován na /worktrees/IW-188

Scénář: Existující odkazy na kartě zůstávají funkční
  Pokud jsem na stránce projektu "iw-cli"
  Když se stránka načte
  Pak odkaz na issue ID na kartě vede na /worktrees/IW-188
  A odkaz na PR na kartě stále vede na externí PR URL
  A odkaz na Zed editor stále otevírá Zed
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Currently `WorktreeCardRenderer.renderCard` renders the issue ID as a link to the external tracker URL (`data.url`). To link to the detail page, the issue ID link target needs to change from `data.url` to `/worktrees/${worktree.issueId}`. The external tracker link can be moved elsewhere on the card (e.g., a small icon or the status badge). This is a behavioral change to the shared card renderer, so it affects cards everywhere (project page, root dashboard cards). The card title (`h3`) could also become a link to the detail page.

**Acceptance:**
- Clicking the issue title or ID on a worktree card navigates to `/worktrees/:issueId`
- External tracker URL remains accessible (moved to a different element)
- PR links, Zed editor links continue working as before
- Change applies consistently across all card contexts (project page, root dashboard)

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Worktree detail page with complete context

**Domain Layer:**
- Reuse `WorktreeRegistration`, `IssueData`, `WorkflowProgress`, `GitStatus`, `PullRequestData`, `ReviewState` (all exist)
- Reuse `MainProject.deriveMainProjectPath` for project context

**Application Layer:**
- Reuse `DashboardService.fetchIssueForWorktreeCachedOnly`, `fetchProgressForWorktree`, `fetchGitStatusForWorktree`, `fetchPRForWorktreeCachedOnly` (all exist)
- Reuse `ServerStateService.getState` for reading cached data

**Infrastructure Layer:**
- No new adapters needed

**Presentation Layer:**
- New route: `GET /worktrees/:issueId` in `CaskServer`
- New view: `WorktreeDetailView` in `presentation/views/` -- renders full-page layout for a single worktree
- Reuses `PageLayout.render` for HTML page shell
- Reuses data section rendering from `WorktreeCardRenderer` (or extracts shared helper methods)

---

### For Story 2: Breadcrumb navigation with project context

**Domain Layer:**
- Reuse `MainProject.deriveMainProjectPath` for project name derivation

**Presentation Layer:**
- Breadcrumb HTML fragment within `WorktreeDetailView`
- Three-level breadcrumb: Projects > {projectName} > {issueId}
- Fallback two-level breadcrumb: Projects > {issueId}

---

### For Story 3: HTMX auto-refresh for worktree detail content

**Application Layer:**
- Content refresh endpoint returning HTML fragment (detail content only, no page shell)

**Presentation Layer:**
- New route: `GET /worktrees/:issueId/detail-content` in `CaskServer` (returns HTML fragment)
- HTMX attributes on content section: `hx-get`, `hx-trigger="every 30s"`, `hx-swap="innerHTML"`
- `WorktreeDetailView` method to render just the content fragment (without breadcrumb/page shell)

---

### For Story 4: Artifact links to artifact detail view

**Presentation Layer:**
- Artifact list rendering in `WorktreeDetailView` (reuse `WorktreeCardRenderer.renderReviewArtifacts` logic or call it directly)
- Links follow existing `/worktrees/:issueId/artifacts?path=...` pattern

---

### For Story 5: Handle unknown worktree gracefully

**Presentation Layer:**
- `WorktreeDetailView.renderNotFound(issueId)` method (mirrors `ProjectDetailsView.renderNotFound`)
- 404 handling in `CaskServer` worktree detail route

---

### For Story 6: Worktree cards link to detail page

**Presentation Layer:**
- Modify `WorktreeCardRenderer.renderCard` to link issue title/ID to `/worktrees/:issueId`
- Move external tracker URL to a secondary element (icon or separate link)

---

## Technical Risks & Uncertainties

### CLARIFY: Auto-refresh approach for detail page

The detail page needs auto-refresh. There are two approaches:

**Questions to answer:**
1. Should the detail page content section poll a dedicated fragment endpoint, or can we reuse the existing card endpoint?
2. If a dedicated endpoint, should it return the same HTML structure as the full page content, or a simplified version?

**Options:**
- **Option A: Dedicated fragment endpoint** (`GET /worktrees/:issueId/detail-content`). The content section of the detail page has `hx-get` pointing to this endpoint, which returns just the inner content (no breadcrumb, no page shell). Pros: clean separation, can optimize the fragment independently. Cons: new endpoint to maintain, duplicated rendering logic.
- **Option B: Reuse card endpoint** (`GET /worktrees/:issueId/card`). The detail page embeds the card HTML inside a wrapper and lets the existing card refresh mechanism work. Pros: no new endpoint, reuses existing data fetching. Cons: card layout may not match detail page layout; would need the detail page to use the card's HTML structure.
- **Option C: Full page reload via HTMX** (`hx-get="/worktrees/:issueId"` with `hx-select` to extract content). The page polls itself and HTMX selects only the content div. Pros: simplest, no new endpoint. Cons: full page render on each poll is wasteful; may cause flicker.

**Impact:** Determines whether we need a new endpoint in CaskServer and a separate render method in WorktreeDetailView.

---

### CLARIFY: Workflow actions display (#47)

The review-state.json schema includes an `available_actions` field with `(id, label, skill)` tuples. Issue #47 proposes workflow-defined actions on the dashboard. The worktree detail page is the natural home for these action buttons.

**Questions to answer:**
1. Should we include action button rendering in this issue, or defer to #47?
2. If included, what happens when an action button is clicked? (There's no execution infrastructure yet.)

**Options:**
- **Option A: Defer entirely to #47**. The detail page does not render `available_actions`. When #47 is implemented, it adds action buttons to the detail page. Pros: clean scope, no dead UI. Cons: misses the natural integration point.
- **Option B: Render action buttons as disabled/informational**. Show what actions are available but don't make them functional. Pros: shows the data is there, useful for workflow visibility. Cons: confusing UX with non-functional buttons.
- **Option C: Render action labels as read-only text**. Show available actions as a list of labels (no buttons). Pros: informational without implying interactivity. Cons: still needs removal/update when #47 lands.

**Impact:** Affects whether `ReviewState` Scala model needs to parse `available_actions`, and whether `WorktreeDetailView` renders an actions section.

---

### CLARIFY: Card renderer changes for detail page links (Story 6)

Currently the issue ID on cards links to the external tracker URL. Changing it to link to the detail page changes existing behavior.

**Questions to answer:**
1. Should the card title link to the worktree detail page?
2. Where should the external tracker link move to?
3. Should this change apply to all card contexts, or only project-page cards?

**Options:**
- **Option A: Card title links to detail page, issue ID badge links to tracker**. The `h3` title becomes `<a href="/worktrees/:issueId">` and the issue ID below keeps its tracker link. Pros: natural -- clicking the card goes deeper in navigation, issue ID still goes to tracker. Cons: two different link behaviors on the same card.
- **Option B: Entire card header area links to detail page, add separate tracker icon**. Pros: clear primary action. Cons: more layout changes.
- **Option C: Defer card linking to a separate issue**. Keep cards as-is for now. Users navigate to detail page via URL only. Pros: no changes to shared renderer. Cons: no discoverability of the detail page.

**Impact:** Affects `WorktreeCardRenderer` which is shared across all card display contexts.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Worktree detail page with complete context): 8-12 hours
- Story 2 (Breadcrumb navigation with project context): 2-3 hours
- Story 3 (HTMX auto-refresh for detail content): 4-6 hours
- Story 4 (Artifact links to artifact detail view): 2-3 hours
- Story 5 (Handle unknown worktree gracefully): 2-3 hours
- Story 6 (Worktree cards link to detail page): 3-4 hours

**Total Range:** 21 - 31 hours

**Confidence:** Medium

**Reasoning:**
- Story 1 is the largest because it introduces a new page with a new route, view, and full-page layout. However, all data fetching infrastructure exists -- the work is primarily in presentation layout.
- Story 3 has moderate uncertainty depending on the auto-refresh approach (CLARIFY #1). A dedicated fragment endpoint adds more work than reusing the card endpoint.
- Story 6 depends on the card linking decision (CLARIFY #3) which could range from trivial (add a link wrapper) to moderate (restructure card header).
- All data models and fetching services are already implemented, reducing integration risk.
- The `PageLayout` shared component is already extracted (from IW-206), so no prerequisite extraction work is needed.

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**
2. **Integration Tests**
3. **E2E Scenario Tests**

**Story-Specific Testing Notes:**

**Story 1:**
- Unit: Test `WorktreeDetailView.render` produces correct HTML with all data sections. Test with full data, partial data (missing PR, missing review state), and no issue data (skeleton).
- Integration: Test `CaskServer` route `GET /worktrees/:issueId` returns HTML with correct content-type and status code. Verify all expected sections are present in response.
- E2E: BATS test that registers a worktree, hits `/worktrees/{issueId}`, and verifies page contains issue ID, worktree-specific content.

**Story 2:**
- Unit: Test breadcrumb rendering with known project name and with unknown project (fallback). Test that links point to correct URLs.
- Integration: Covered by Story 1 integration test (breadcrumb is part of the page).
- E2E: Covered by Story 1 E2E test.

**Story 3:**
- Unit: Test that the content fragment endpoint renders the same data sections as the full page (minus page shell).
- Integration: Test fragment endpoint returns HTML fragment (not full page). Verify HTMX attributes are present on the content section in the full page.
- E2E: BATS test that hits the fragment endpoint and verifies it returns a valid HTML fragment.

**Story 4:**
- Unit: Test artifact links render with correct href pattern. Test with zero, one, and multiple artifacts.
- Integration: Covered by Story 1 integration test.
- E2E: BATS test that verifies artifact links are present in detail page when review state has artifacts.

**Story 5:**
- Unit: Test `WorktreeDetailView.renderNotFound` produces correct HTML with back link.
- Integration: Test `GET /worktrees/NONEXISTENT` returns 404 with friendly error page.
- E2E: BATS test that hits `/worktrees/NONEXISTENT` and verifies error page content and 404 status.

**Story 6:**
- Unit: Test `WorktreeCardRenderer.renderCard` output contains link to `/worktrees/:issueId`. Test that tracker URL is still accessible.
- Integration: Test project details page cards contain worktree detail links.
- E2E: Covered by existing card rendering E2E tests (verify link changes).

**Test Data Strategy:**
- Use existing `TestFixtures.scala` patterns for constructing `WorktreeRegistration`, `IssueData`, `WorkflowProgress`, `GitStatus`, `PullRequestData`, `ReviewState`
- For E2E tests, register worktrees via the server API before testing the detail page
- Test with varying combinations of present/absent data to verify graceful degradation

**Regression Coverage:**
- Root dashboard (`GET /`) must continue to work correctly
- Project details page (`GET /projects/:projectName`) must continue to show cards
- Per-card refresh (`GET /worktrees/:issueId/card`) must continue to work
- Artifact viewer (`GET /worktrees/:issueId/artifacts?path=...`) must continue to work
- If Story 6 modifies the card renderer, all card display contexts must be retested

## Deployment Considerations

### Database Changes
None -- iw-cli uses file-based state. No state format changes needed.

### Configuration Changes
None -- no new environment variables or config file changes.

### Rollout Strategy
- New route (`/worktrees/:issueId`) does not affect existing routes
- If Story 6 is implemented, the card link behavior changes (issue ID now links to detail page instead of tracker)
- Backward compatible -- existing bookmarks to root dashboard and project pages still work

### Rollback Plan
- Revert the commits -- no persistent state changes
- If Story 6 card changes cause issues, they can be reverted independently

## Dependencies

### Prerequisites
- `PageLayout` shared component (already exists from IW-206)
- `ProjectDetailsView` as reference implementation (already exists from IW-206)
- All data models and services (already exist)
- Static CSS/JS resources (already extracted from IW-206)

### Story Dependencies
- Story 2 depends on Story 1 (breadcrumb is part of the page layout)
- Story 3 depends on Story 1 (auto-refresh targets the content area of the page)
- Story 4 depends on Story 1 (artifact links are within the page content)
- Story 5 depends on Story 1 (error handling for the route)
- Story 6 is independent (modifies the shared card renderer, not the detail page)

### External Blockers
- None -- all dependencies are internal
- #47 (workflow actions) is a stretch goal, not a blocker

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Worktree detail page** -- Core page with route, view, and data rendering. Everything else builds on this.
2. **Story 2: Breadcrumb navigation** -- Small addition to the page from Story 1. Can be implemented as part of Story 1 or immediately after.
3. **Story 5: Handle unknown worktree** -- Error case for the route. Important for robustness, small and quick.
4. **Story 4: Artifact links** -- Ensures review artifacts are clickable. Reuses existing rendering. Small.
5. **Story 3: HTMX auto-refresh** -- Adds live updates. Requires the CLARIFY decision on refresh approach.
6. **Story 6: Card links to detail page** -- Connects the navigation flow. Depends on CLARIFY decision about card behavior. Can be done last since users can still navigate via URL.

**Iteration Plan:**

- **Iteration 1** (Stories 1-2, 5): Core detail page with breadcrumb navigation and error handling. Delivers the primary user value -- a dedicated, bookmarkable worktree view with complete context.
- **Iteration 2** (Stories 4, 3): Artifact links and auto-refresh. Adds interactivity and live updates to the detail page.
- **Iteration 3** (Story 6): Navigation integration. Cards link to the detail page, completing the drill-down flow.

## Documentation Requirements

- [ ] Gherkin scenarios serve as living documentation
- [ ] API documentation for new route (`GET /worktrees/:issueId`)
- [ ] API documentation for content refresh endpoint (if new endpoint created per CLARIFY #1)
- [ ] No user-facing docs needed (dashboard is self-explanatory)
- [ ] No migration guide needed (no breaking changes)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with stakeholders
2. Run **ag-create-tasks** with the issue ID
3. Run **ag-implement** for iterative story implementation
