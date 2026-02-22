# Phase 2: Remove flat worktree list from root page

## Goals

Remove the `WorktreeListView` rendering from the dashboard root page (`/`), so the root page displays only the header and project summary cards. This makes the root page a clean projects overview, with individual worktree cards accessible only via the project details page (`/projects/:projectName`).

Also remove the per-worktree data fetching in `DashboardService.renderDashboard`, which exists solely to supply data to `WorktreeListView` and becomes dead code once the view is removed.

## Scope

### In Scope
- Remove `WorktreeListView.render(...)` call from `DashboardService.renderDashboard` body content
- Remove `worktreesWithData` computation block (lines 58-68) from `renderDashboard`
- Update `DashboardServiceTest` tests that assert worktree card content is present on root page
- Verify project details page continues to render worktree cards independently

### Out of Scope
- Simplifying `renderDashboard` function signature (Phase 3) -- the parameters `issueCache`, `progressCache`, `prCache` will become unused but are NOT removed in this phase
- Removing `WorktreeListView` object from codebase -- it stays because `WorktreeListSync` references the `#worktree-list` container and card patterns
- Modifying the `/api/worktrees/changes` endpoint in `CaskServer` -- it stays for now (the root page simply stops polling it)
- CSS changes to `.worktree-list` or `.worktree-card` styles -- still needed for project details page
- Any changes to `ProjectDetailsView` or project details page behavior

## Dependencies

### Prior Work (Already Done)
- Phase 1 added `ProjectSummary` view model and worktree count/attention badges to project cards
- `MainProjectsView.render(summaries)` already renders self-sufficient project overview cards
- `ProjectDetailsView.render` independently renders worktree cards (does NOT use `WorktreeListView`)
- `ProjectDetailsView` has its own HTMX polling via `hx-get="/api/projects/$projectName/worktrees/changes"`

### Required By This Phase
- Phase 3 depends on this phase to simplify the `renderDashboard` function signature

## Approach

### 1. Understand current `renderDashboard` structure

The function at `.iw/core/dashboard/DashboardService.scala` (lines 29-111) currently does:

```
1. Sort worktrees by issue ID (line 42)
2. Derive main projects (lines 44-48)
3. Compute project summaries (lines 51-55)       <-- STAYS
4. Fetch per-worktree data: issue, progress,      <-- REMOVE (lines 58-68)
   git status, PR, review state
5. Build body content:
   a. Dashboard header (lines 73-98)              <-- STAYS
   b. MainProjectsView.render (line 100)          <-- STAYS
   c. WorktreeListView.render (line 101)           <-- REMOVE
   d. Modal container (line 103)                   <-- STAYS
6. Render page via PageLayout (lines 107-111)     <-- STAYS
```

### 2. Remove per-worktree data fetching

Delete the `worktreesWithData` block (DashboardService.scala lines 57-68):

```scala
    // Fetch data for each worktree (read-only from cache)
    val worktreesWithData = sortedWorktrees.map { wt =>
      val issueData = fetchIssueForWorktreeCachedOnly(wt, issueCache, now)
      val progress = fetchProgressForWorktree(wt, progressCache)
      val gitStatus = fetchGitStatusForWorktree(wt)
      val prData = fetchPRForWorktreeCachedOnly(wt, prCache, now)

      // Just read review state from cache (no filesystem reads)
      val reviewStateResult = reviewStateCache.get(wt.issueId).map(cached => Right(cached.state))

      (wt, issueData, progress, gitStatus, prData, reviewStateResult)
    }
```

This block calls `fetchIssueForWorktreeCachedOnly`, `fetchProgressForWorktree`, `fetchGitStatusForWorktree`, and `fetchPRForWorktreeCachedOnly` for every worktree. The results feed only into `WorktreeListView.render`. After removal, none of these calls happen during root page rendering.

Note: The `val now = Instant.now()` on line 39 was used by both `worktreesWithData` and will become unused after this removal. However, it is harmless to keep (no side effects). Phase 3 will clean it up along with the signature simplification.

### 3. Remove `WorktreeListView.render` call

In the `bodyContent` fragment (lines 70-104), remove line 101:

```scala
      WorktreeListView.render(worktreesWithData, now, sshHost),
```

This is the line that renders the `#worktree-list` div containing all worktree cards with HTMX polling attributes (`hx-get="/api/worktrees/changes"`, `hx-trigger="every 30s"`).

After this change, the body content becomes:
```scala
    val bodyContent = frag(
      div(cls := "dashboard-header", ...),
      MainProjectsView.render(projectSummaries),
      div(id := "modal-container")
    )
```

### 4. Remove unused import

The `WorktreeListView` import is no longer needed in `DashboardService.scala`. However, check if other symbols from the same package are used before removing.

Current import at line 4-11 includes `WorktreeListView` implicitly through the `iw.core.dashboard` package. Since `WorktreeListView` is in the same package as `DashboardService`, there may be no explicit import to remove. Verify at implementation time.

### 5. Update `DashboardServiceTest` tests

Many tests in `.iw/core/test/DashboardServiceTest.scala` assert that the root page HTML contains individual issue IDs (e.g., `assert(html.contains("IWLE-123"))`). These pass because `WorktreeListView` renders skeleton cards with `id="card-IWLE-123"`. After removal, those issue IDs will NOT appear in root page HTML.

**Tests that need updating:**

| Test | Current assertion | Required change |
|------|-------------------|-----------------|
| `renderDashboard includes review state when present in cache` (line 45) | `assert(html.contains("IWLE-123"))` | Change to assert NOT present, or assert project cards are present instead |
| `renderDashboard handles missing review state gracefully` (line 80) | `assert(html.contains("IWLE-456"))` | Same -- issue ID no longer in root page HTML |
| `renderDashboard with multiple worktrees and mixed review state availability` (line 112) | `assert(html.contains("IWLE-100"))` etc. | Same |
| `renderDashboard review state cache is keyed by issue ID` (line 149) | `assert(html.contains("IWLE-789"))` | Same |
| `fetchReviewStateForWorktree with missing state file doesn't crash dashboard` (line 184) | `assert(html.contains("IWLE-MISSING"))` | Same |
| `fetchReviewStateForWorktree returns Some(Left) when JSON invalid` (line 206) | `assert(html.contains("IWLE-INVALID"))` | Same |
| `fetchReviewStateForWorktree with fake paths renders dashboard` (line 231) | `assert(html.contains("IWLE-VALID"))` | Same |
| `renderDashboard does not crash with invalid review state` (line 251) | `assert(html.contains("IWLE-OK"))` etc. | Same |
| `renderDashboard with empty worktree list` (line 97) | `assert(html.contains("iw Dashboard"))` | May still pass (header stays), but verify empty state text |
| `renderDashboard sorts worktrees by priority` (line 419) | Asserts order of IW-1, IW-2, IW-3 by HTML index | **Remove or rewrite** -- sorting only matters for worktree cards, which are gone from root page |
| `renderDashboard sort is stable for equal priorities` (line 469) | Asserts order of IW-A, IW-B, IW-C | **Remove or rewrite** -- same reason |
| `renderDashboard includes Zed button with configured SSH host` (line 342) | `assert(html.contains("zed-button"))` | **Remove or rewrite** -- Zed buttons are in worktree cards only |
| `renderDashboard Zed button uses correct SSH host for multiple worktrees` (line 360) | `assert(html.contains("zed://ssh/..."))` | **Remove or rewrite** -- same |

**Strategy for test updates:**

- Tests verifying "dashboard renders without crashing" (review state, empty state, SSH host form): Keep but update assertions to check for `<!DOCTYPE html>`, `iw Dashboard` header, and optionally `main-projects-section` -- NOT individual issue IDs
- Tests verifying worktree card ordering: Remove entirely -- ordering tests belong in `ProjectDetailsView` tests, not root page tests
- Tests verifying Zed buttons: Remove entirely -- Zed button tests belong in worktree card rendering tests
- Add a new test: `renderDashboard root page does NOT contain worktree-list div`
- Add a new test: `renderDashboard root page does NOT contain worktree card HTML`

### 6. Verify `ProjectDetailsView` independence

Confirm `ProjectDetailsView` at `.iw/core/dashboard/presentation/views/ProjectDetailsView.scala` does NOT use `WorktreeListView`. Verified: it renders worktree cards directly using `WorktreeCardRenderer.renderSkeletonCard` and `WorktreeCardRenderer.renderCard`, and has its own `#worktree-list` div with project-scoped HTMX polling (`hx-get="/api/projects/$projectName/worktrees/changes"`). No changes needed.

### 7. Verify `WorktreeListView` is still used elsewhere

`WorktreeListView` helper functions (`calculateDelay`, `statusClass`, `formatCacheAge`, `formatRelativeTime`, `displayTypeClass`) are referenced by `WorktreeCardRenderer` and potentially other components. The `WorktreeListView.render` method is referenced only from `DashboardService.renderDashboard` (the call we're removing). The object stays in the codebase.

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/dashboard/DashboardService.scala` | Remove `worktreesWithData` computation (lines 57-68), remove `WorktreeListView.render(...)` call (line 101), remove unused `val now` if no other references |
| `.iw/core/test/DashboardServiceTest.scala` | Update ~14 tests that assert individual issue IDs in root page HTML; remove worktree ordering and Zed button tests; add negative assertions for worktree-list absence |

## Testing Strategy

### Unit Tests (DashboardServiceTest - updated)

**New tests:**
- `renderDashboard root page does NOT contain worktree-list div` -- assert `!html.contains("worktree-list")`
- `renderDashboard root page does NOT contain worktree card HTML` -- assert `!html.contains("worktree-card")`
- `renderDashboard root page does NOT poll /api/worktrees/changes` -- assert `!html.contains("/api/worktrees/changes")`

**Updated tests (assertions changed from positive to negative):**
- All tests currently asserting `html.contains("<issue-id>")` should instead verify the dashboard renders (check `<!DOCTYPE html>`, `iw Dashboard`, `main-projects-section` or `No main projects found`) without asserting individual issue IDs

**Removed tests:**
- `renderDashboard sorts worktrees by priority` -- ordering is no longer a root page concern
- `renderDashboard sort is stable for equal priorities` -- same
- `renderDashboard includes Zed button with configured SSH host` -- Zed buttons are worktree card concerns
- `renderDashboard Zed button uses correct SSH host for multiple worktrees` -- same

### Existing tests (no changes needed)
- `WorktreeListViewTest` -- tests `WorktreeListView.render` in isolation, unaffected
- `ProjectDetailsViewTest` -- tests project details page, unaffected
- `WorktreeCardRendererTest` -- tests card rendering in isolation, unaffected
- E2E BATS tests -- no BATS tests currently assert worktree cards on root page

## Acceptance Criteria

- [ ] Root page (`/`) does NOT render `WorktreeListView`
- [ ] Root page HTML does NOT contain `worktree-list` div
- [ ] Root page HTML does NOT contain `worktree-card` elements
- [ ] Root page does NOT poll `/api/worktrees/changes` (no HTMX attribute for this endpoint)
- [ ] Root page still renders header, project summary cards, and modal container
- [ ] Project details page (`/projects/:projectName`) continues to show worktree cards (unaffected)
- [ ] `DashboardService.renderDashboard` no longer fetches per-worktree data (no calls to `fetchIssueForWorktreeCachedOnly`, `fetchProgressForWorktree`, `fetchGitStatusForWorktree`, `fetchPRForWorktreeCachedOnly`)
- [ ] All tests pass (with updates described in Testing Strategy)
- [ ] `renderDashboard` function signature is unchanged (signature simplification is Phase 3)
