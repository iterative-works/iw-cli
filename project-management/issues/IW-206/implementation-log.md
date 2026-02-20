# Implementation Log: Dashboard - Add project details page with worktree list

Issue: IW-206

This log tracks the evolution of implementation across phases.

---

## Phase 1: Extract CSS/JS to static resources and create shared layout (2026-02-20)

**What was built:**
- Component: `.iw/core/dashboard/presentation/views/PageLayout.scala` - Shared HTML page shell (DOCTYPE, head with HTMX CDN, CSS/JS links, body wrapper)
- Static: `.iw/core/dashboard/resources/static/dashboard.css` - Extracted 734-line CSS from DashboardService
- Static: `.iw/core/dashboard/resources/static/dashboard.js` - Extracted visibility-change event listener
- Route: `/static/:filename` in CaskServer - Serves static files with correct Content-Type

**Decisions made:**
- Serve static files from filesystem (not classpath) since the project uses scala-cli, not packaged JARs
- Use `os.pwd`-relative paths for static file directory (`.iw/core/dashboard/resources/static/`)
- Keep HTMX CDN URLs in PageLayout (not extracted to config) since they rarely change
- PageLayout takes `devMode` parameter to conditionally render dev mode banner

**Patterns applied:**
- Shared layout component: PageLayout renders the complete HTML shell, accepting body content as a Scalatags `Frag` parameter
- Refactoring by extraction: Inline CSS/JS moved to external files without any content changes

**Testing:**
- Unit tests: 13 PageLayout tests + 4 new DashboardService refactoring tests
- Integration tests: 5 StaticFilesTest tests (HTTP serving with real server)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260220.md
- Major findings: No critical issues. Warnings about test thread cleanup and hardcoded paths (acceptable for this scope).

**For next phases:**
- Available utilities: `PageLayout.render(title, bodyContent, devMode)` for any new page
- Extension points: Add new static files to `resources/static/` and they'll be served automatically
- Notes: Future pages (project details) should use PageLayout for HTML shell consistency

**Files changed:**
```
A .iw/core/dashboard/presentation/views/PageLayout.scala
A .iw/core/dashboard/resources/static/dashboard.css
A .iw/core/dashboard/resources/static/dashboard.js
A .iw/core/test/PageLayoutTest.scala
A .iw/core/test/StaticFilesTest.scala
M .iw/core/dashboard/CaskServer.scala
M .iw/core/dashboard/DashboardService.scala
M .iw/core/test/DashboardServiceTest.scala
```

---

## Phase 2: Project details page with filtered worktree cards (2026-02-20)

**What was built:**
- View: `.iw/core/dashboard/presentation/views/ProjectDetailsView.scala` - Project page with breadcrumb, metadata header, and filtered worktree cards
- Function: `MainProjectService.filterByProjectName` - Pure function to filter worktrees by derived project name
- Route: `GET /projects/:projectName` in CaskServer - Project details page endpoint
- Reuses `WorktreeCardRenderer` for card HTML and `PageLayout` for page shell

**Decisions made:**
- Filter by last path component of derived main project path (exact match, case-sensitive)
- Reuse existing `WorktreeCardRenderer` with `HtmxCardConfig.dashboard` for consistent card behavior
- Changed 4 DashboardService cache-fetching methods from `private` to `private[dashboard]` to avoid duplicating data fetching logic in CaskServer
- Render 404 response when no worktrees match project name (project not found)

**Patterns applied:**
- View component: ProjectDetailsView.render returns Scalatags Frag, composed with PageLayout.render
- Pure filtering: filterByProjectName is a pure function using existing deriveMainProjectPath heuristic
- Reuse: WorktreeCardRenderer shared between root dashboard and project details page

**Testing:**
- Unit tests: 5 filtering tests + 8 view tests + 6 service tests = 19 total
- Integration tests: Route integration tests deferred (tracked in tasks)

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20260220.md
- Major findings: No critical issues. Warnings about pre-existing FCIS violation in loadConfig, private[dashboard] visibility trade-off, and missing route integration tests (deferred).

**For next phases:**
- Available utilities: `ProjectDetailsView.render(...)` for project page, `MainProjectService.filterByProjectName(...)` for filtering
- Extension points: Add project-scoped actions (create worktree button, sync) to ProjectDetailsView
- Notes: Breadcrumb navigation from Phase 3 can extend the existing nav element in ProjectDetailsView

**Files changed:**
```
A .iw/core/dashboard/presentation/views/ProjectDetailsView.scala
A .iw/core/test/ProjectDetailsViewTest.scala
A .iw/core/test/ProjectFilteringTest.scala
M .iw/core/dashboard/CaskServer.scala
M .iw/core/dashboard/DashboardService.scala
M .iw/core/dashboard/application/MainProjectService.scala
```

---

## Phase 3: Breadcrumb navigation from project page back to overview (2026-02-20)

**What was built:**
- CSS: Added styles for `.breadcrumb`, `.project-details`, `.project-header`, `.project-metadata`, `.tracker-type-badge`, and `.team-info` to `dashboard.css`

**Decisions made:**
- CSS-only phase: breadcrumb HTML and behavior were already implemented in Phase 2
- Used existing dashboard color palette and spacing patterns for consistency
- Tracker type badge uses pill/rounded style matching status badges
- Team info link uses primary blue color (#228be6) consistent with other links

**Patterns applied:**
- Consistent spacing: 12px gap in metadata, 16px breadcrumb margin
- Flex layout for metadata badge + team display

**Testing:**
- All existing unit tests pass (CSS-only changes don't affect HTML structure)

**Code review:**
- Skipped (CSS-only, no logic changes)

**For next phases:**
- Project details page is now visually complete with proper styling
- Extension points: Phase 4 can add "Create Worktree" button to project-header area

**Files changed:**
```
M .iw/core/dashboard/resources/static/dashboard.css
```

---

## Phase 4: Project-scoped Create Worktree button (2026-02-20)

**What was built:**
- Button: "Create Worktree" button in `ProjectDetailsView` project-header section with HTMX attributes
- Container: `#modal-container` div added to project details page for HTMX modal rendering
- Tests: 5 unit tests in `ProjectDetailsCreateButtonTest.scala`

**Decisions made:**
- Reuse existing `CreateWorktreeModal` with `projectPath` parameter (no new modal needed)
- Replicate button HTMX pattern from `MainProjectsView.renderProjectCard`
- URL-encode `mainProject.path` for the `hx-get` query parameter
- Place button inside `project-header` div, after the metadata section

**Patterns applied:**
- HTMX button pattern: `hx-get` → `hx-target` → `hx-swap` for modal loading
- Reuse existing endpoint: `/api/modal/create-worktree?project=...`

**Testing:**
- Unit tests: 5 tests (button presence, hx-get URL, hx-target, hx-swap, modal container)

**Code review:**
- Skipped (minimal changes: 2 imports, 1 button, 1 div)

**For next phases:**
- Project details page now has a working create button that scopes to the project
- Phase 5 can add clickable project cards on the overview page
- Phase 6 can improve 404 handling for unknown projects

**Files changed:**
```
M .iw/core/dashboard/presentation/views/ProjectDetailsView.scala
A .iw/core/test/ProjectDetailsCreateButtonTest.scala
```

---

## Phase 5: Project cards on overview link to project details (2026-02-20)

**What was built:**
- Link: Project name in `MainProjectsView.renderProjectCard` now wraps `h3` in an `<a href="/projects/{projectName}">` element
- Tests: 3 unit tests added to existing `MainProjectsViewTest.scala`

**Decisions made:**
- Wrap `h3` inside `<a>` rather than making the entire card clickable, to keep the Create button independent
- Use `projectName` (not encoded path) in the URL since it's a clean URL path segment

**Patterns applied:**
- Standard HTML link wrapping heading element

**Testing:**
- Unit tests: 3 tests (link presence, href correctness, Create button still present)

**Code review:**
- Skipped (single line change: wrap h3 in anchor tag)

**For next phases:**
- Navigation flow is now complete: overview → project details → worktrees
- Phase 6 can add graceful 404 handling for unknown project names

**Files changed:**
```
M .iw/core/dashboard/presentation/views/MainProjectsView.scala
M .iw/core/test/MainProjectsViewTest.scala
```

---

## Phase 6: Handle unknown project name gracefully (2026-02-20)

**What was built:**
- Method: `ProjectDetailsView.renderNotFound(projectName)` - styled not-found page with breadcrumb, message, and link back to overview
- CaskServer: 404 response now uses `PageLayout.render` with `renderNotFound` instead of plain text

**Decisions made:**
- Render a full styled page (using PageLayout) instead of plain text, for consistent user experience
- Include breadcrumb navigation even on 404 page for orientation
- Keep 404 HTTP status code (not redirect to overview)

**Testing:**
- Unit tests: 3 tests (project name in output, link to overview, breadcrumb)

**Code review:**
- Skipped (small change: new method + CaskServer 404 case update)

**For next phases:**
- Phase 7 (HTMX auto-refresh) is the final and most complex phase

**Files changed:**
```
M .iw/core/dashboard/presentation/views/ProjectDetailsView.scala
M .iw/core/dashboard/CaskServer.scala
M .iw/core/test/ProjectDetailsViewTest.scala
```

---

## Phase 7: HTMX auto-refresh for project worktree list (2026-02-20)

**What was built:**
- HTMX attributes: Added `hx-get`, `hx-trigger`, `hx-swap`, `hx-vals` to `#worktree-list` div in `ProjectDetailsView`
- Route: `GET /api/projects/:projectName/worktrees/changes` in CaskServer - project-scoped list sync endpoint

**Decisions made:**
- New dedicated endpoint rather than query parameter on existing `/api/worktrees/changes`, for clean REST URL hierarchy mirroring `/projects/:projectName`
- Reuse `WorktreeListSync.detectChanges` and `generateChangesResponse` unchanged - only the input list is filtered by project
- Same 30s polling interval as root dashboard

**Patterns applied:**
- HTMX OOB swap pattern: `hx-swap="none"` with server returning OOB-attributed fragments
- Project-scoped filtering: `MainProjectService.filterByProjectName` applied before change detection
- Same `hx-vals` JS expression as root dashboard to collect current card IDs from DOM

**Testing:**
- Unit tests: 4 tests added (hx-get, hx-trigger, hx-swap, hx-vals)

**Code review:**
- Skipped (minimal changes: 4 HTMX attributes + 1 route mirroring existing pattern)

**For next phases:**
- All 7 phases complete. Project details page is fully functional with auto-refresh.

**Files changed:**
```
M .iw/core/dashboard/presentation/views/ProjectDetailsView.scala
M .iw/core/dashboard/CaskServer.scala
M .iw/core/test/ProjectDetailsViewTest.scala
```

---
