# Story-Driven Analysis: Dashboard - Add project details page with worktree list

**Issue:** IW-206
**Created:** 2026-02-20
**Status:** Draft
**Classification:** Feature

## Problem Statement

The dashboard currently shows a flat list of all worktrees across all projects on the root page. When a user has worktrees spread across multiple projects (e.g., iw-cli, kanon, iw-support), they see everything in one undifferentiated grid. There is no way to view just the worktrees for a single project, and no stable URL to bookmark or share a project view.

The hierarchical navigation redesign introduces a Projects -> Project -> Worktree -> Artifact drill-down. This issue adds the **Project** level: a dedicated page at `/projects/:projectName` that shows project metadata and only the worktrees belonging to that project. This is the bridge between the projects overview (root page) and the individual worktree detail pages (#188).

## User Stories

### Story 1: Project details page with filtered worktree cards

```gherkin
Funkce: Stránka detailu projektu se seznamem worktree
  Jako vývojář
  Chci vidět dedikovanou stránku pro konkrétní projekt
  Aby jsem se mohl soustředit na worktree jednoho projektu

Scénář: Zobrazení stránky projektu s worktree kartami
  Pokud existuje projekt "iw-cli" se třemi registrovanými worktree
  Když otevřu /projects/iw-cli
  Pak vidím název projektu, typ trackeru a tým
  A vidím pouze worktree karty patřící k projektu "iw-cli"
  A nevidím worktree karty patřící k jiným projektům

Scénář: Projekt nemá žádné worktree
  Pokud existuje projekt "iw-cli" bez registrovaných worktree
  Když otevřu /projects/iw-cli
  Pak vidím metadata projektu
  A vidím prázdný stav s informací že nejsou žádné worktree
```

**Estimated Effort:** 8-12h
**Complexity:** Moderate

**Technical Feasibility:**
The main challenge is filtering worktrees by project. `MainProject.deriveMainProjectPath` already maps worktree paths to main project paths, and `MainProjectService.deriveFromWorktrees` groups worktrees by project. We need to reverse this: given a project name, find all worktrees whose derived main project path ends with that name. The existing `WorktreeCardRenderer` and `WorktreeListView` can be reused for card rendering. The page structure (HTML shell, CSS, HTMX scripts) mirrors the existing root dashboard in `DashboardService.renderDashboard`. A new route in `CaskServer` and a new view component are needed.

**Acceptance:**
- `GET /projects/:projectName` returns a full HTML page with project metadata and filtered worktree cards
- Worktree cards are filtered by matching the derived main project path
- Each card has the same HTMX polling behavior as the root dashboard
- Empty state shown when project has no worktrees

---

### Story 2: Breadcrumb navigation from project page back to overview

```gherkin
Funkce: Drobečková navigace na stránce projektu
  Jako vývojář
  Chci se snadno vrátit na přehled projektů
  Aby jsem mohl přepínat mezi projekty

Scénář: Zobrazení drobečkové navigace
  Pokud jsem na stránce projektu "iw-cli"
  Když se stránka načte
  Pak vidím drobečkovou navigaci "Projects > iw-cli"
  A odkaz "Projects" vede na kořenovou stránku "/"

Scénář: Kliknutí na odkaz Projects
  Pokud jsem na stránce projektu "iw-cli"
  Když kliknu na odkaz "Projects" v drobečkové navigaci
  Pak jsem přesměrován na kořenovou stránku dashboardu
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Standard HTML breadcrumb component. No HTMX needed -- simple `<a href="/">` link. The breadcrumb is part of the project page layout, so it is rendered in the same view component as Story 1. This story is intentionally small to deliver the navigation piece independently.

**Acceptance:**
- Breadcrumb shows "Projects > {projectName}" on the project details page
- "Projects" link navigates to the root dashboard
- Styled consistently with the rest of the dashboard

---

### Story 3: Project-scoped "Create Worktree" button

```gherkin
Funkce: Tlačítko vytvoření worktree v kontextu projektu
  Jako vývojář
  Chci vytvořit worktree přímo ze stránky projektu
  Aby nová worktree byla automaticky přiřazena k tomuto projektu

Scénář: Otevření modálu pro vytvoření worktree
  Pokud jsem na stránce projektu "iw-cli" s cestou "/home/user/projects/iw-cli"
  Když kliknu na tlačítko "Create Worktree"
  Pak se otevře existující modální okno pro vytvoření worktree
  A modál je předvyplněn s cestou projektu
  A titulek modálu ukazuje název projektu

Scénář: Vytvoření worktree ze stránky projektu
  Pokud jsem na stránce projektu "iw-cli"
  A modál pro vytvoření worktree je otevřen
  Když vyberu issue a potvrdím vytvoření
  Pak se worktree vytvoří v kontextu projektu "iw-cli"
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
The existing `CreateWorktreeModal` already accepts an optional `projectPath` parameter and passes it to the search and create endpoints. The existing `MainProjectsView.renderProjectCard` already renders "Create" buttons with `hx-get="/api/modal/create-worktree?project={encodedPath}"`. We just need to reuse this same button pattern in the project details page. No new modal logic needed.

**Acceptance:**
- "Create Worktree" button on the project details page opens the create modal scoped to the project
- The modal shows the project name in its title
- Issue search and creation are scoped to the project's configuration

---

### Story 4: HTMX auto-refresh for project worktree list

```gherkin
Funkce: Automatická aktualizace seznamu worktree na stránce projektu
  Jako vývojář
  Chci aby se seznam worktree na stránce projektu automaticky aktualizoval
  Aby jsem viděl aktuální stav bez nutnosti obnovovat stránku

Scénář: Automatická aktualizace jednotlivých karet
  Pokud jsem na stránce projektu "iw-cli"
  A projekt má tři worktree
  Když uplyne 30 sekund
  Pak se každá karta worktree individuálně obnoví přes HTMX polling

Scénář: Detekce přidání nové worktree
  Pokud jsem na stránce projektu "iw-cli"
  A jiný proces zaregistruje novou worktree pro projekt "iw-cli"
  Když uplyne 30 sekund polling interval
  Pak se nová worktree karta automaticky objeví v seznamu

Scénář: Detekce odebrání worktree
  Pokud jsem na stránce projektu "iw-cli"
  A jiný proces odregistruje worktree z projektu "iw-cli"
  Když uplyne 30 sekund polling interval
  Pak se odstraněná worktree karta automaticky zmizí ze seznamu
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Individual card HTMX polling (`GET /worktrees/:issueId/card`) works automatically because each card has its own `hx-get` and `hx-trigger` attributes. This is the same mechanism used on the root dashboard and requires no changes.

The list-level sync (adding/removing cards when worktrees are registered/unregistered) is more complex. The root dashboard uses `GET /api/worktrees/changes` with a `have` parameter to detect additions and deletions. This endpoint currently returns ALL worktrees. For the project page, we need a **project-scoped** variant that only considers worktrees belonging to the given project. This is the main technical challenge: either add a `project` query parameter to the existing endpoint, or create a new endpoint.

**Acceptance:**
- Per-card HTMX polling works on the project details page (same as root dashboard)
- List-level sync detects additions and removals of worktrees within the project scope
- Cards added to other projects do not appear on this project page

---

### Story 5: Project cards on overview link to project details

```gherkin
Funkce: Propojení projektových karet na přehledové stránce s detailem projektu
  Jako vývojář
  Chci kliknout na projekt na přehledové stránce
  Aby jsem se dostal na stránku detailu projektu

Scénář: Kliknutí na projektovou kartu
  Pokud jsem na přehledové stránce dashboardu
  A existuje projekt "iw-cli"
  Když kliknu na kartu projektu "iw-cli"
  Pak jsem přesměrován na /projects/iw-cli

Scénář: Projektová karta stále zobrazuje tlačítko Create
  Pokud jsem na přehledové stránce dashboardu
  Když se stránka načte
  Pak projektová karta "iw-cli" má odkaz na detail projektu
  A projektová karta "iw-cli" stále obsahuje tlačítko "Create"
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
The `MainProjectsView.renderProjectCard` currently renders project name as an `h3` with no link. We need to wrap it (or the card itself) in an `<a href="/projects/{projectName}">` link. The "Create" button should remain and not be part of the link target (to avoid navigation when clicking Create). This is a small change to the existing `MainProjectsView`.

**Acceptance:**
- Clicking a project card (not the Create button) navigates to `/projects/:projectName`
- The Create button still works independently (opens the create modal without navigation)
- Project name or card area is clickable

---

### Story 6: Handle unknown project name gracefully

```gherkin
Funkce: Zpracování neexistujícího projektu
  Jako vývojář
  Chci vidět srozumitelnou chybovou zprávu
  Aby jsem věděl že projekt neexistuje nebo nemá žádné worktree

Scénář: Neznámý projekt
  Pokud neexistuje žádný registrovaný worktree pro projekt "neexistujici-projekt"
  Když otevřu /projects/neexistujici-projekt
  Pak vidím stránku s informací že projekt nebyl nalezen
  A vidím odkaz zpět na přehled projektů

Scénář: Projekt s URL-enkódovaným názvem
  Pokud existuje projekt s názvem obsahujícím speciální znaky
  Když otevřu /projects/ s enkódovaným názvem
  Pak se název správně dekóduje a zobrazí
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
The route handler in CaskServer needs to check whether any worktrees exist for the given project name. If none match, return a 404-style page with a message and link back to the overview. URL encoding/decoding is handled by the Cask framework automatically for path parameters. The `projectName` in the URL is the last path component of the project directory (e.g., "iw-cli"), which should not typically contain special characters, but we should handle it gracefully.

**Acceptance:**
- Unknown project names display a user-friendly "not found" page
- The page includes a link back to the projects overview
- URL-encoded project names are handled correctly

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Project details page with filtered worktree cards

**Domain Layer:**
- Reuse `MainProject` (already exists)
- Reuse `MainProject.deriveMainProjectPath` for filtering worktrees by project

**Application Layer:**
- Method to filter worktrees by project name (given all worktrees, return those whose derived main project path name matches)
- Reuse `MainProjectService.deriveFromWorktrees` for project metadata lookup

**Infrastructure Layer:**
- No new adapters needed

**Presentation Layer:**
- New route: `GET /projects/:projectName` in `CaskServer`
- New view: `ProjectDetailsView` -- renders the full HTML page with project metadata header, worktree card list, and breadcrumb
- Reuses `WorktreeCardRenderer` for individual cards
- Reuses `WorktreeListView.render` pattern for the card grid (or a project-scoped variant)
- Shares CSS from `DashboardService.styles` (or extracts shared styles)

---

### For Story 2: Breadcrumb navigation

**Presentation Layer:**
- Breadcrumb HTML fragment within `ProjectDetailsView`
- CSS for breadcrumb styling (add to shared styles)

---

### For Story 3: Project-scoped "Create Worktree" button

**Presentation Layer:**
- Reuse existing `CreateWorktreeModal` (already supports `projectPath` parameter)
- "Create Worktree" button in `ProjectDetailsView` with `hx-get="/api/modal/create-worktree?project={encodedPath}"`
- Modal container div in `ProjectDetailsView`

---

### For Story 4: HTMX auto-refresh for project worktree list

**Application Layer:**
- Project-scoped worktree changes endpoint (filter `WorktreeListSync.detectChanges` to project scope)

**Infrastructure Layer:**
- No new adapters

**Presentation Layer:**
- Either new route `GET /api/projects/:projectName/worktrees/changes` or extend existing `GET /api/worktrees/changes` with a `project` query parameter
- Worktree list div with `hx-get` pointing to the project-scoped changes endpoint
- HTMX `hx-vals` with current card IDs (same pattern as root dashboard)

---

### For Story 5: Project cards link to project details

**Presentation Layer:**
- Modify `MainProjectsView.renderProjectCard` to wrap project name/card in an `<a>` element linking to `/projects/:projectName`
- Ensure Create button does not trigger navigation (event propagation)

---

### For Story 6: Handle unknown project name

**Presentation Layer:**
- 404 handling in `CaskServer` project route
- Error/not-found page fragment in `ProjectDetailsView`

---

## Technical Risks & Uncertainties

### CLARIFY: Worktree-to-project filtering strategy

The current `MainProject.deriveMainProjectPath` strips the issue ID suffix from worktree paths (e.g., `/home/user/projects/iw-cli-IW-79` -> `/home/user/projects/iw-cli`). To filter worktrees for a project on the details page, we need to reverse this mapping: given a project name like "iw-cli", find all worktrees whose derived main project path ends with "iw-cli".

**Questions to answer:**
1. Is matching by project name (last path component) sufficient, or could two projects have the same name in different parent directories?
2. Should we match by full project path (more precise) or by name (simpler URL)?

**Options:**
- **Option A: Match by project name (last path component)**: Simpler URLs (`/projects/iw-cli`), but breaks if two projects share a name. In practice this is unlikely since projects are on the same machine.
- **Option B: Match by encoded full path**: URL like `/projects/%2Fhome%2Fuser%2Fprojects%2Fiw-cli` -- precise but ugly and fragile (path changes break bookmarks).
- **Option C: Match by name with disambiguation**: Use name by default, and full path only when ambiguous. Adds complexity for an edge case.

**Recommended:** Option A. Project names are derived from directory names and are unique in practice on a single developer machine. If a collision occurs, we can add disambiguation later.

**Impact:** Affects URL scheme, route design, and filtering logic.

---

### CLARIFY: Shared styles vs duplicated page shell

`DashboardService.renderDashboard` contains inline CSS (the `styles` val) and the full HTML page shell (head, body, HTMX scripts). The project details page needs the same CSS and scripts.

**Questions to answer:**
1. Should we extract the shared CSS and page shell into a reusable layout component?
2. Or duplicate the page structure for now and extract later?

**Options:**
- **Option A: Extract shared layout now**: Cleaner, avoids drift, but larger initial change (refactoring `DashboardService`).
- **Option B: Duplicate for now, extract later**: Faster delivery for Story 1, but risks style drift. Mark with a TODO.

**Impact:** Affects how much of `DashboardService` needs to change in Story 1.

---

### CLARIFY: Project-scoped worktree list sync endpoint

The root dashboard uses `GET /api/worktrees/changes?have=ID1,ID2,...` to detect additions/removals. The project details page needs the same functionality but scoped to one project.

**Questions to answer:**
1. Should we add a `project` query parameter to the existing `/api/worktrees/changes` endpoint?
2. Or create a separate `/api/projects/:projectName/worktrees/changes` endpoint?

**Options:**
- **Option A: Add `project` parameter to existing endpoint**: Less code duplication, one endpoint to maintain. But the filtering logic gets conditional.
- **Option B: New endpoint per project**: Cleaner separation, more explicit. But duplicates the changes-detection logic or requires extracting it into a shared service (which `WorktreeListSync` already is).

**Recommended:** Option A. The filtering logic is one extra `.filter()` call on the worktree list. `WorktreeListSync.detectChanges` is already a pure function that works on ID lists regardless of source.

**Impact:** Affects route design in `CaskServer` and HTMX attributes in `ProjectDetailsView`.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Project details page with filtered worktree cards): 8-12 hours
- Story 2 (Breadcrumb navigation): 2-3 hours
- Story 3 (Project-scoped Create Worktree button): 3-4 hours
- Story 4 (HTMX auto-refresh for project worktree list): 6-8 hours
- Story 5 (Project cards link to project details): 2-3 hours
- Story 6 (Handle unknown project name): 2-3 hours

**Total Range:** 23 - 33 hours

**Confidence:** Medium

**Reasoning:**
- Story 1 is the largest because it introduces a new page with a new route, view, and filtering logic. The estimate includes extracting or reusing the page shell/CSS.
- Stories 2, 3, 5, 6 are straightforward presentation-layer changes that reuse existing components.
- Story 4 has moderate complexity due to the project-scoped list sync, but the core logic (`WorktreeListSync.detectChanges`) is already pure and tested.
- Confidence is medium because the shared styles/layout extraction (CLARIFY #2) could increase Story 1 scope, and the HTMX behavior on the new page may reveal integration quirks.

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**
2. **Integration Tests**
3. **E2E Scenario Tests**

**Story-Specific Testing Notes:**

**Story 1:**
- Unit: Test worktree filtering logic -- given a list of worktrees and a project name, the correct subset is returned. Test with worktrees from multiple projects, test edge cases (no matches, all matches).
- Integration: Test the new `CaskServer` route returns correct HTML. Verify project metadata is present in the response. Verify only project-scoped worktree cards are in the response.
- E2E: BATS test that registers worktrees for two projects, hits `/projects/{name}`, and verifies only correct cards are returned.

**Story 2:**
- Unit: Test breadcrumb rendering produces correct HTML with link to root.
- Integration: Covered by Story 1 integration test (breadcrumb is part of the page).
- E2E: Covered by Story 1 E2E test.

**Story 3:**
- Unit: Test that the Create button renders with the correct `hx-get` URL containing the encoded project path.
- Integration: Covered by Story 1 integration test.
- E2E: BATS test that opens the create modal from the project page (verify modal HTML is returned).

**Story 4:**
- Unit: Test `WorktreeListSync.detectChanges` with project-filtered lists (already tested for general case, add project-filtered case).
- Integration: Test the changes endpoint with `project` parameter returns only project-scoped changes.
- E2E: BATS test that registers a new worktree while on the project page, polls the changes endpoint, and verifies the new card appears.

**Story 5:**
- Unit: Test `MainProjectsView.renderProjectCard` output contains a link to `/projects/{name}`.
- Integration: Test root dashboard page contains clickable project links.
- E2E: Covered by existing root dashboard E2E tests (verify link presence).

**Story 6:**
- Unit: Test that the route handler returns a not-found page for unknown project names.
- Integration: Test `GET /projects/nonexistent` returns appropriate response (404 or friendly error page).
- E2E: BATS test that hits `/projects/nonexistent` and verifies error page content.

**Test Data Strategy:**
- Use `WorktreeRegistration` fixtures with varied project paths to test filtering
- Reuse existing `TestFixtures.scala` patterns for worktree and state construction
- For E2E tests, register worktrees via the API before testing the project page

**Regression Coverage:**
- Root dashboard (`GET /`) must continue to show all worktrees from all projects
- Per-card refresh (`GET /worktrees/:issueId/card`) must continue to work
- Existing worktree list sync (`GET /api/worktrees/changes`) must continue to work for the root dashboard
- Project creation modal must continue to work from both root dashboard and project page

## Deployment Considerations

### Database Changes
None -- iw-cli uses file-based state. No state format changes needed.

### Configuration Changes
None -- no new environment variables or config file changes.

### Rollout Strategy
- New route (`/projects/:projectName`) does not affect existing routes
- Modification to project cards on root page (Story 5) is a small UI change -- project cards become clickable links
- Backward compatible -- existing bookmarks to root dashboard still work

### Rollback Plan
- Revert the commits -- no persistent state changes
- The only user-visible change is the link on project cards and the new route

## Dependencies

### Prerequisites
- Existing `MainProject` and `MainProjectService` (already exist)
- Existing `WorktreeCardRenderer` and `WorktreeListView` (already exist)
- Existing HTMX card refresh infrastructure (already exists)
- Existing `CreateWorktreeModal` with project scoping (already exists)

### Story Dependencies
- Story 2 depends on Story 1 (breadcrumb is part of the page layout)
- Story 3 depends on Story 1 (Create button is on the page)
- Story 4 depends on Story 1 (list sync targets the project page's worktree list)
- Story 5 is independent (modifies root page, not project page)
- Story 6 depends on Story 1 (error handling for the route)

### External Blockers
- None -- all dependencies are internal

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Project details page** -- Core page with route, view, and filtering. Everything else builds on this.
2. **Story 2: Breadcrumb navigation** -- Small addition to the page from Story 1. Can be implemented as part of Story 1 or immediately after.
3. **Story 3: Create Worktree button** -- Reuses existing modal, small addition to the page.
4. **Story 5: Project cards link to details** -- Independent of Stories 2-4, but makes sense after the target page exists. Connects the navigation flow.
5. **Story 6: Unknown project handling** -- Error case, important for robustness.
6. **Story 4: HTMX auto-refresh** -- Most complex remaining story, benefits from having the static page working first.

**Iteration Plan:**

- **Iteration 1** (Stories 1-3): Core project page with metadata, filtered cards, breadcrumb, and create button. Delivers the primary user value -- a dedicated, navigable project view.
- **Iteration 2** (Stories 5-6): Navigation integration (project cards link to details) and error handling. Completes the navigation flow.
- **Iteration 3** (Story 4): Live updates. Adds polish with auto-refresh for additions/removals within the project scope.

## Documentation Requirements

- [ ] Gherkin scenarios serve as living documentation
- [ ] API documentation for new route (`GET /projects/:projectName`)
- [ ] Update dashboard architecture docs if they exist (note new route)
- [ ] No user-facing docs needed (dashboard is self-explanatory)
- [ ] No migration guide needed (no breaking changes)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with stakeholders
2. Run **ag-create-tasks** with the issue ID
3. Run **ag-implement** for iterative story implementation
