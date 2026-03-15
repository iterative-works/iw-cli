# Phase 1: Worktree detail page with complete context

## Goals

Create a dedicated worktree detail page at `/worktrees/:issueId` that shows the complete context for a single worktree. This page replaces scanning through card grids by providing a full-page view with all available data sections: issue title/status/assignee, git status, workflow progress, PR link/state, review artifacts, and Zed editor link.

## Scope

### In Scope
- New `WorktreeDetailView` object in `presentation/views/` rendering a full-page layout for a single worktree
- New `GET /worktrees/:issueId` route in `CaskServer` returning the detail page
- Rendering all data sections present on cards but in a spacious, full-page layout
- Graceful rendering when optional data is missing (no PR, no review state, no issue data loaded yet)
- Breadcrumb navigation: `Projects > {projectName} > {issueId}` (with fallback `Projects > {issueId}` when project can't be derived)
- Not-found page for unknown worktree IDs

### Out of Scope
- HTMX auto-refresh (Phase 5)
- Artifact links as clickable navigation (Phase 4 — artifacts will render but without link interactivity beyond existing card behavior)
- Card title linking to detail page (Phase 6)
- Workflow action buttons (deferred to #47)

## Dependencies

### Prerequisites (all exist)
- `PageLayout.render(title, bodyContent, devMode)` — full HTML page shell
- `ProjectDetailsView` — reference pattern for breadcrumb, not-found, route structure
- `WorktreeCardRenderer` — reference for data section rendering (reuse logic or extract shared helpers)
- `DashboardService.fetchIssueForWorktreeCachedOnly`, `fetchProgressForWorktree`, `fetchGitStatusForWorktree`, `fetchPRForWorktreeCachedOnly` — cached data fetching
- `ServerStateService.getState` — thread-safe state access
- `MainProject.deriveMainProjectPath(worktreePath)` — project name derivation from worktree path
- All domain models: `WorktreeRegistration`, `IssueData`, `WorkflowProgress`, `GitStatus`, `PullRequestData`, `ReviewState`
- `SampleData` test fixtures for all domain types
- Scalatags DSL for HTML generation

### No prior phases required
This is Phase 1 — no dependencies on other phases.

## Approach

### 1. Create `WorktreeDetailView` object

Location: `.iw/core/dashboard/presentation/views/WorktreeDetailView.scala`

Pattern: Follow `ProjectDetailsView` structure — a Scala `object` with pure `render` and `renderNotFound` methods returning `Frag`.

**Key methods:**

```scala
object WorktreeDetailView:
  def render(
    worktree: WorktreeRegistration,
    issueData: Option[(IssueData, Boolean, Boolean)],
    progress: Option[WorkflowProgress],
    gitStatus: Option[GitStatus],
    prData: Option[PullRequestData],
    reviewStateResult: Option[Either[String, ReviewState]],
    projectName: Option[String],
    now: Instant,
    sshHost: String
  ): Frag

  def renderNotFound(issueId: String): Frag
```

**Layout structure:**
- Breadcrumb: `Projects > {projectName} > {issueId}` (or `Projects > {issueId}`)
- Header: Issue title (h1), issue ID with tracker link, status badge, assignee
- Sections (each rendered only when data is present):
  - Git status: branch name, clean/dirty indicator
  - Workflow progress: current phase info, progress bar, task counts
  - Pull request: PR number link, state badge
  - Zed editor: link button
  - Review artifacts: display badge, artifacts list (reuse `WorktreeCardRenderer.renderReviewArtifacts` logic)
- Skeleton state: when issue data isn't loaded yet, show issue ID and available data (git status, etc.)

### 2. Add route in `CaskServer`

Add `GET /worktrees/:issueId` route following the same pattern as `/projects/:projectName`:
1. Get state via `stateService.getState`
2. Look up worktree by `state.worktrees.get(issueId)`
3. If not found → render `WorktreeDetailView.renderNotFound(issueId)` with 404 status
4. If found → fetch cached data, derive project name, render `WorktreeDetailView.render(...)`
5. Wrap in `PageLayout.render(...)` and return HTML response

**Data fetching:** Use `DashboardService.fetchIssueForWorktreeCachedOnly`, `fetchProgressForWorktree`, `fetchGitStatusForWorktree`, `fetchPRForWorktreeCachedOnly` — same pattern as `/projects/:projectName` route.

**Project name derivation:** Use `MainProject.deriveMainProjectPath(worktree.path)` to extract project name from worktree directory path. The result is `Option[String]` — `None` means the project name couldn't be derived.

### 3. Make `renderReviewArtifacts` accessible

The review artifacts rendering logic in `WorktreeCardRenderer` is currently `private`. Either:
- Make `renderReviewArtifacts` package-private or public, or
- Extract the logic into the detail view

Decision: Make `renderReviewArtifacts` accessible (simplest, avoids duplication).

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/dashboard/presentation/views/WorktreeDetailView.scala` | **NEW** — Detail page view with `render` and `renderNotFound` |
| `.iw/core/dashboard/CaskServer.scala` | Add `GET /worktrees/:issueId` route, add `WorktreeDetailView` import |
| `.iw/core/dashboard/presentation/views/WorktreeCardRenderer.scala` | Make `renderReviewArtifacts` accessible (remove `private`) |
| `.iw/core/test/WorktreeDetailViewTest.scala` | **NEW** — Unit tests for detail view rendering |

## Testing Strategy

### Unit Tests (`WorktreeDetailViewTest.scala`)
Following `ProjectDetailsViewTest` pattern — call `render(...).render` and assert HTML content:

1. **Full data rendering:** Issue title, status, assignee, git branch, PR link, review artifacts all present
2. **Missing optional data:** No PR → PR section absent. No review state → artifacts section absent. No assignee → assignee not shown.
3. **Skeleton state:** When `issueData` is `None`, show issue ID and whatever data is available
4. **Breadcrumb with project name:** Shows `Projects > projectName > issueId` with correct links
5. **Breadcrumb without project name:** Shows `Projects > issueId` when `projectName` is `None`
6. **Not found page:** Contains issue ID, back link to `/`, styled consistently
7. **Zed editor link:** Contains correct `zed://ssh/...` URL
8. **Workflow progress:** Phase info and progress bar rendered correctly

### Integration Tests (in `CaskServerTest.scala`)
- `GET /worktrees/:issueId` returns 200 with HTML for known worktree
- `GET /worktrees/NONEXISTENT` returns 404 with friendly error page

### E2E Tests (BATS)
- Register a worktree, hit `/worktrees/{issueId}`, verify page contains issue ID

## Acceptance Criteria

- [ ] `GET /worktrees/:issueId` returns a full HTML page with all available worktree data
- [ ] All data sections render correctly: issue title/status/assignee, git status, workflow progress, PR link/state, review artifacts, Zed editor link
- [ ] Page renders gracefully when data sections are missing (skeleton states, absent sections)
- [ ] Breadcrumb shows `Projects > {projectName} > {issueId}` when project is derivable
- [ ] Breadcrumb shows `Projects > {issueId}` when project cannot be derived
- [ ] Unknown issue IDs display a user-friendly "not found" page with 404 status
- [ ] URL is stable and bookmarkable
- [ ] Page is wrapped in `PageLayout` with consistent styling
