# Story-Driven Analysis: Dashboard - Restructure root page as projects overview

**Issue:** IW-205
**Created:** 2026-02-21
**Status:** Ready for Implementation
**Classification:** Feature

## Problem Statement

The dashboard root page (`/`) currently renders two flat sections: a "Main Projects" row of project cards and a "Worktree List" grid of all worktree cards across all projects. This layout has two problems:

1. **All worktrees are mixed together.** A developer with worktrees across 3 projects sees them in one undifferentiated grid, making it hard to quickly find worktrees for a specific project.

2. **Redundant navigation path.** The project details page (`/projects/:projectName`) already exists (IW-206) and shows filtered worktree cards per project. The flat worktree list on the root page duplicates this information in a less organized way.

The root page should be restructured as a **projects overview** -- the top level of the navigation hierarchy (Projects -> Project -> Worktree -> Artifact). Each project card should summarize its worktree state (count, attention needed) and link to the project details page where the full worktree list lives.

## User Stories

### Story 1: Project cards show worktree count and summary status

```gherkin
Funkce: Projektové karty zobrazují počet worktree a souhrn stavu
  Jako vývojář
  Chci na přehledové stránce vidět kolik worktree má každý projekt
  Aby jsem rychle pochopil stav práce na každém projektu

Scénář: Karta projektu s worktree
  Pokud existuje projekt "iw-cli" se třemi registrovanými worktree
  Když otevřu přehledovou stránku dashboardu
  Pak karta projektu "iw-cli" zobrazuje "3 worktrees"

Scénář: Karta projektu bez worktree
  Pokud existuje projekt "iw-cli" bez registrovaných worktree
  Když otevřu přehledovou stránku dashboardu
  Pak karta projektu "iw-cli" zobrazuje "0 worktrees"

Scénář: Karta projektu s worktree vyžadující pozornost
  Pokud existuje projekt "iw-cli" se třemi worktree
  A jedna worktree má needsAttention=true v review state
  Když otevřu přehledovou stránku dashboardu
  Pak karta projektu "iw-cli" zobrazuje "3 worktrees, 1 needs attention"
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
The data needed to compute worktree counts per project is already available in `DashboardService.renderDashboard`. It receives `worktrees: List[WorktreeRegistration]` and `reviewStateCache: Map[String, CachedReviewState]`. We can group worktrees by derived project using `MainProject.deriveMainProjectPath`, count them, and cross-reference with `reviewStateCache` to count those with `needsAttention == Some(true)`.

The main design decision is how to pass this summary data to `MainProjectsView.render`. Options:
- Create a `ProjectSummary` view model (or similar) that pairs `MainProject` with counts
- Extend the `MainProjectsView.render` signature to accept a `Map[String, ProjectSummary]` alongside projects
- Compute summaries inside `DashboardService` and pass them through

A clean approach is a presentation-layer view model that bundles `MainProject` with its summary data.

**CLARIFY: What counts as "needs attention"?** The `ReviewState.needsAttention` field is the only indicator currently available. Should we also consider stale issue data, dirty git status, or other signals? For now, we use only `needsAttention` from review state, which is the most explicit signal. Other indicators can be added later.

**Acceptance:**
- Project cards display worktree count (e.g., "3 worktrees")
- Project cards display attention count when > 0 (e.g., "1 needs attention")
- Counts are computed from actual registered worktree data and review state cache
- Zero-worktree projects show "0 worktrees" (for independently tracked projects per #148, when implemented)

---

### Story 2: Remove flat worktree list from root page

```gherkin
Funkce: Odebrání plochého seznamu worktree z přehledové stránky
  Jako vývojář
  Chci aby přehledová stránka zobrazovala pouze projektové karty
  Aby byla přehledná a jednoduchá

Scénář: Přehledová stránka bez seznamu worktree
  Pokud existují projekty s registrovanými worktree
  Když otevřu přehledovou stránku dashboardu
  Pak vidím pouze projektové karty
  A nevidím individuální karty worktree
  A nevidím sekci "worktree-list"

Scénář: Navigace k worktree přes projekt
  Pokud existuje projekt "iw-cli" se třemi worktree
  Když otevřu přehledovou stránku dashboardu
  A kliknu na kartu projektu "iw-cli"
  Pak jsem přesměrován na /projects/iw-cli
  A tam vidím karty worktree patřící k projektu "iw-cli"
```

**Estimated Effort:** 2-3h
**Complexity:** Simple

**Technical Feasibility:**
This is a removal task. In `DashboardService.renderDashboard`, we remove the call to `WorktreeListView.render(...)` from the `bodyContent` fragment. The `WorktreeListView` object itself stays in the codebase since `ProjectDetailsView` still uses the card rendering patterns (though it renders cards directly, not via `WorktreeListView`).

The main concern is that existing tests that assert worktree cards are present in the root dashboard HTML will need updating. These tests should be modified to assert the cards are NOT present on the root page.

Additionally, `DashboardService.renderDashboard` currently fetches cached data for all worktrees (lines 51-61) purely to pass to `WorktreeListView`. Once we remove the worktree list from the root page, this data fetching becomes unnecessary and can be removed, simplifying the function.

**Acceptance:**
- Root page (`/`) does NOT render `WorktreeListView`
- Root page does NOT contain worktree card HTML
- Root page does NOT poll `/api/worktrees/changes`
- Project details page (`/projects/:projectName`) continues to show worktree cards
- `DashboardService.renderDashboard` no longer fetches per-worktree data

---

### Story 3: Simplify DashboardService after worktree list removal

```gherkin
Funkce: Zjednodušení DashboardService po odebrání worktree listu
  Jako vývojář pracující na kódu
  Chci aby DashboardService nedělal zbytečnou práci
  Aby byl kód čistší a rychlejší

Scénář: Renderování dashboardu bez načítání worktree dat
  Pokud existují registrované worktree
  Když se renderuje přehledová stránka
  Pak DashboardService nenačítá issue data, progress, git status ani PR data
  A renderuje pouze hlavičku a projektové karty
```

**Estimated Effort:** 2-3h
**Complexity:** Simple

**Technical Feasibility:**
After Story 2, `DashboardService.renderDashboard` will have dead code: the `worktreesWithData` computation (lines 51-61) fetches issue data, progress, git status, and PR data for every worktree. None of this is needed if we only render project cards. The function signature can be simplified -- it may not even need the individual cache maps anymore, only worktree registrations (for counting) and review state cache (for attention counts).

This is a refactoring story that follows naturally from Story 2. It reduces the `renderDashboard` surface area and makes the separation of concerns clearer: root page = project overview, project page = worktree details.

**Acceptance:**
- `DashboardService.renderDashboard` does not call `fetchIssueForWorktreeCachedOnly`, `fetchProgressForWorktree`, `fetchGitStatusForWorktree`, or `fetchPRForWorktreeCachedOnly`
- Function signature is simplified (remove unused cache parameters or mark them optional)
- All existing tests pass (updated for new behavior)
- No performance regression (should be faster since we skip per-worktree data fetching)

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Project cards show worktree count and summary status

**Domain Layer:**
- Reuse `MainProject` (already exists)
- Reuse `ReviewState.needsAttention` for attention signal

**Application Layer / Presentation Layer:**
- View model to pair `MainProject` with summary counts (worktree count, attention count)
- Pure function to compute summaries: given worktrees and review state cache, group by project and count
- New in `MainProjectService` or a new pure function: `computeProjectSummaries`

**Presentation Layer:**
- Modify `MainProjectsView.render` to accept summary data alongside projects
- Render worktree count badge on each card
- Render attention indicator when attention count > 0

**Infrastructure Layer:**
- No changes needed

---

### For Story 2: Remove flat worktree list from root page

**Presentation Layer:**
- Remove `WorktreeListView.render(...)` call from `DashboardService.renderDashboard`
- Remove `import` of `WorktreeListView` from `DashboardService` (if it becomes unused)
- Remove modal container div from root page (it moves to project details only -- but it is already there)

**Testing:**
- Update `DashboardServiceTest` to assert worktree cards are NOT present
- Ensure `WorktreeListViewTest` still passes (the object is still used by project details)

---

### For Story 3: Simplify DashboardService after worktree list removal

**Application Layer:**
- Simplify `DashboardService.renderDashboard` signature
- Remove per-worktree data fetching logic from dashboard rendering path

**Testing:**
- Update tests that pass cache data to `renderDashboard`
- Verify render is faster / simpler

---

## Technical Risks & Uncertainties

### CLARIFY: Definition of "needs attention"

The issue mentions "1 needs attention" in the summary status. The only current mechanism is `ReviewState.needsAttention: Option[Boolean]`. This is set by external tooling (Claude Code agent workflow) and is not universally present.

**Proposed resolution:** Use `ReviewState.needsAttention == Some(true)` as the sole signal for now. If no review state cache entry exists for a worktree, it does not count as needing attention. This keeps the logic simple and avoids false positives.

---

### CLARIFY: What happens to the root page HTMX polling?

The root page currently has HTMX polling via `WorktreeListView` (`hx-get="/api/worktrees/changes"` with `hx-trigger="every 30s"`). Once we remove the worktree list, this polling is no longer needed on the root page. However, the `/api/worktrees/changes` endpoint itself must remain since it is still used by project details pages (indirectly, through the project-scoped variant).

**Proposed resolution:** Simply remove the polling from the root page HTML output. The API endpoint stays.

---

### CLARIFY: Should "Create Worktree" remain on root page project cards?

The issue says: "Keep the + Create Worktree action accessible (either on project cards or in project details)". Currently, `MainProjectsView` renders a "+ Create" button on each project card, and `ProjectDetailsView` also has a "+ Create Worktree" button.

**Proposed resolution:** Keep the "+ Create" button on project cards on the root page. It already works and provides a shortcut without navigating to the project details page. The modal container div must remain on the root page for this to work.

---

### Risk: Breaking existing root page bookmarks/behavior

**Likelihood:** Low
**Impact:** Low
**Mitigation:** The root page URL (`/`) does not change. The page content changes (no more worktree cards), but this is the intended redesign. Users who want worktree cards navigate to `/projects/:projectName`.

---

### Risk: Tests relying on worktree cards in root page HTML

**Likelihood:** High
**Impact:** Medium
**Mitigation:** Several `DashboardServiceTest` tests assert that issue IDs appear in the root page HTML (e.g., `assert(html.contains("IWLE-123"))`). These assertions currently pass because worktree cards contain the issue IDs. After removing worktree cards from the root page, these tests will fail. They need to be updated -- either removed (if they were testing worktree card rendering) or adapted (if they were testing something else).

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Project cards show worktree count and summary status): 6-8 hours
- Story 2 (Remove flat worktree list from root page): 2-3 hours
- Story 3 (Simplify DashboardService after worktree list removal): 2-3 hours

**Total Range:** 10 - 14 hours

**Confidence:** High

**Reasoning:**
- Story 1 is the most involved because it introduces a view model, pure computation logic, and view changes. But all the data is already available -- no new API calls or infrastructure.
- Story 2 is a removal task with well-defined scope. The main effort is updating tests.
- Story 3 is a straightforward refactoring that follows mechanically from Story 2.
- The project details page (IW-206) is already implemented, so we know the navigation target exists and works.
- No new routes, no new endpoints, no new infrastructure. This is primarily a presentation-layer restructuring.

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**
2. **Integration Tests** (via test assertions on rendered HTML)
3. **E2E Scenario Tests** (BATS)

**Story-Specific Testing Notes:**

**Story 1:**
- Unit: Test pure function that computes project summaries (given worktrees + review state cache, verify correct counts per project). Test edge cases: no worktrees, all worktrees needing attention, no review state cache entries.
- Unit: Test `MainProjectsView.render` with summary data -- verify count badge HTML, attention indicator HTML.
- Integration: Test `DashboardService.renderDashboard` output contains count badges.
- E2E: BATS test that registers worktrees with review state and verifies counts on root page.

**Story 2:**
- Unit: Test `DashboardService.renderDashboard` output does NOT contain `worktree-list` div or worktree card HTML.
- Integration: Covered by unit tests (the rendering is pure).
- E2E: BATS test that verifies root page does not show worktree cards. Verify project details page still shows them.

**Story 3:**
- Unit: Test simplified `renderDashboard` signature works with fewer parameters.
- Integration: Verify existing functionality (project cards, header, SSH form) still works after simplification.

**Regression Coverage:**
- Root dashboard (`GET /`) must show project cards with summary data
- Root dashboard must NOT show individual worktree cards
- Project details page (`GET /projects/:projectName`) must continue to show filtered worktree cards
- Per-card refresh (`GET /worktrees/:issueId/card`) must continue to work
- Project-scoped worktree sync (`GET /api/projects/:projectName/worktrees/changes`) must continue to work
- Create worktree modal must continue to work from both root dashboard and project page

## Deployment Considerations

### Database Changes
None -- iw-cli uses file-based state. No state format changes needed.

### Configuration Changes
None -- no new environment variables or config file changes.

### Rollout Strategy
- Root page content changes (worktree cards removed, project cards enhanced) but URL stays the same
- Project details pages are unaffected
- Backward compatible -- no API changes, no state format changes

### Rollback Plan
- Revert the commits -- no persistent state changes
- The only user-visible change is the root page layout

## Dependencies

### Prerequisites
- IW-206 (project details page) -- DONE. The navigation target exists and works.
- `MainProject.deriveMainProjectPath` and `MainProjectService.deriveFromWorktrees` -- already exist
- `ReviewState.needsAttention` field -- already exists in domain model

### Story Dependencies
- Story 2 depends on Story 1 being complete (or can be done in parallel if tests are coordinated)
- Story 3 depends on Story 2 (removes code that Story 2 makes dead)

### External Blockers
- None -- all dependencies are internal and already implemented

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Project cards show worktree count and summary status** -- Core value delivery. Enhanced project cards make the root page useful as a projects overview.
2. **Story 2: Remove flat worktree list from root page** -- Removes the redundant section. With Story 1 done, the root page is self-sufficient as a projects overview.
3. **Story 3: Simplify DashboardService** -- Cleanup. Remove dead code left over from Story 2.

**Iteration Plan:**

- **Phase 1** (Story 1): Add view model, compute summaries, enhance project cards. Root page shows both enhanced project cards AND the old worktree list during this phase -- users get a preview of the new layout while the old one is still there.
- **Phase 2** (Stories 2-3): Remove worktree list and simplify DashboardService. This is the "flip the switch" moment where the root page becomes a pure projects overview.

## Documentation Requirements

- [ ] Code documentation (inline comments for summary computation logic)
- [ ] Update any existing dashboard architecture docs (note root page restructuring)
- [ ] No API documentation changes (no new endpoints)
- [ ] No user-facing docs needed (dashboard is self-explanatory)
- [ ] No migration guide needed (no breaking changes)

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. **Generate tasks:** `/iterative-works:ag-create-tasks IW-205`
2. **Begin implementation:** `/iterative-works:ag-implement IW-205`
