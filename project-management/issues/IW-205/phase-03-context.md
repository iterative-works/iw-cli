# Phase 3: Simplify DashboardService after worktree list removal

## Goals

Simplify the `DashboardService.renderDashboard` function signature by removing parameters that became unused after Phase 2. Phase 2 removed the per-worktree data fetching and `WorktreeListView` call, leaving `issueCache`, `progressCache`, `prCache`, and `config` as dead parameters. This phase removes them, along with the unused `val now = Instant.now()` line, updates the Scaladoc, and updates all callers and tests.

## Scope

### In Scope
- Remove unused parameters from `renderDashboard`: `issueCache`, `progressCache`, `prCache`, `config`
- Remove unused `val now = Instant.now()` from `renderDashboard` body (line 39)
- Update Scaladoc to reflect the simplified signature
- Update the caller in `CaskServer.scala` `dashboard()` method (lines 50-59) to stop passing removed params
- Remove the now-unnecessary `config` loading in `CaskServer.dashboard()` (lines 46-47) since it was only used to pass to `renderDashboard`
- Update all 27 call sites in `DashboardServiceTest.scala` to use the new signature

### Out of Scope
- Removing any private helper methods from `DashboardService` (`fetchIssueForWorktreeCachedOnly`, `fetchProgressForWorktree`, `fetchGitStatusForWorktree`, `fetchPRForWorktreeCachedOnly`, etc.) -- these are still used by per-card refresh paths in `CaskServer` (e.g., `projectDetails`, `worktreeCard`)
- Removing any imports from `DashboardService.scala` -- `CachedIssue`, `CachedProgress`, `CachedPR`, `ProjectConfiguration`, and `java.time.Instant` are all still used by other methods in the same object
- Moving `ProjectSummary.computeSummaries` to the application layer (noted in Phase 1 review as a future consideration)
- Any changes to `CaskServer.projectDetails()` or other endpoints

## Dependencies

### Prior Work (Already Done)
- Phase 1 added `ProjectSummary` view model and worktree count/attention badges to project cards
- Phase 2 removed `WorktreeListView.render` call and `worktreesWithData` computation from `renderDashboard`, making `issueCache`, `progressCache`, `prCache`, `config`, and `val now` unused

### Required By This Phase
- No downstream phases depend on this -- Phase 3 is the final phase of IW-205

## Approach

### 1. Verify unused parameters

Current `renderDashboard` signature (`.iw/core/dashboard/DashboardService.scala`, lines 29-38):

```scala
def renderDashboard(
    worktrees: List[WorktreeRegistration],
    issueCache: Map[String, CachedIssue],        // UNUSED -- remove
    progressCache: Map[String, CachedProgress],   // UNUSED -- remove
    prCache: Map[String, CachedPR],               // UNUSED -- remove
    reviewStateCache: Map[String, CachedReviewState],
    config: Option[ProjectConfiguration],         // UNUSED -- remove
    sshHost: String,
    devMode: Boolean = false
): String =
    val now = Instant.now()  // UNUSED -- remove
```

Function body uses only: `worktrees`, `reviewStateCache`, `sshHost`, `devMode`.

Confirmed by tracing through the body:
- Line 42: `worktrees.sortBy(_.issueId)` -- uses `worktrees`
- Lines 45-48: `MainProjectService.deriveFromWorktrees(sortedWorktrees, MainProjectService.loadConfig)` -- uses `sortedWorktrees` (derived from `worktrees`), NOT `config`
- Lines 51-55: `ProjectSummary.computeSummaries(sortedWorktrees, mainProjects, reviewStateCache)` -- uses `reviewStateCache`
- Lines 58-90: body content -- uses `sshHost`
- Lines 93-97: `PageLayout.render(...)` -- uses `devMode`

### 2. Simplify `renderDashboard` signature

New signature:

```scala
def renderDashboard(
    worktrees: List[WorktreeRegistration],
    reviewStateCache: Map[String, CachedReviewState],
    sshHost: String,
    devMode: Boolean = false
): String =
```

### 3. Update Scaladoc

Remove `@param` entries for `issueCache`, `progressCache`, `prCache`, `config`. Remove the note about "cached data only" since the function now only receives the data it actually uses. Update the description to reflect that the function renders the projects overview page.

### 4. Remove `val now = Instant.now()` from body

Line 39 becomes dead code after Phase 2. Remove it. No other line in the function body references `now`.

### 5. Update `CaskServer.dashboard()` caller

Current code (`.iw/core/dashboard/CaskServer.scala`, lines 46-59):

```scala
    // Load project configuration
    val configPath = os.pwd / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
    val config = ConfigFileRepository.read(configPath)

    // Render dashboard with cached data only (read-only, no writes)
    val html = DashboardService.renderDashboard(
        worktrees,
        state.issueCache,
        state.progressCache,
        state.prCache,
        state.reviewStateCache,
        config,
        sshHost = effectiveSshHost,
        devMode = devMode
    )
```

After update:

```scala
    // Render projects overview page
    val html = DashboardService.renderDashboard(
        worktrees,
        state.reviewStateCache,
        sshHost = effectiveSshHost,
        devMode = devMode
    )
```

The `configPath` and `config` lines (46-47) can be removed entirely from the `dashboard()` method since `config` is only used to pass to `renderDashboard`.

### 6. Update all 27 test call sites in `DashboardServiceTest.scala`

Every call to `DashboardService.renderDashboard(...)` in the test file currently passes `issueCache`, `progressCache`, `prCache`, and `config`. All 27 call sites need to be updated to remove these four arguments.

Current pattern in tests:
```scala
val html = DashboardService.renderDashboard(
    worktrees = List(worktree),
    issueCache = Map.empty,       // remove
    progressCache = Map.empty,    // remove
    prCache = Map.empty,          // remove
    reviewStateCache = Map.empty,
    config = None,                // remove
    sshHost = "localhost"
)
```

New pattern:
```scala
val html = DashboardService.renderDashboard(
    worktrees = List(worktree),
    reviewStateCache = Map.empty,
    sshHost = "localhost"
)
```

Also check the test import line (line 7) -- `CachedIssue`, `CachedPR`, `CachedProgress` may become unused imports in the test file if no other test references them. Scan the test file for any remaining usage of these types before removing imports.

### 7. Verify no imports become unused in DashboardService.scala

After the changes, these symbols in the import on line 6 are still used elsewhere in the file:
- `CachedIssue` -- used by `fetchIssueForWorktreeCachedOnly` (line 111) and `fetchIssueForWorktree` (line 133)
- `CachedProgress` -- used by `fetchProgressForWorktree` (line 268)
- `CachedPR` -- used by `fetchPRForWorktreeCachedOnly` (line 324) and `fetchPRForWorktree` (line 341)
- `ProjectConfiguration` -- used by `buildFetchFunction` (line 165) and `buildUrlBuilder` (line 239)
- `java.time.Instant` -- used by `fetchIssueForWorktreeCachedOnly` (line 112), `fetchIssueForWorktree` (line 134), `fetchPRForWorktreeCachedOnly` (line 325), `fetchPRForWorktree` (line 342)

No imports need to be removed from `DashboardService.scala`.

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/dashboard/DashboardService.scala` | Remove `issueCache`, `progressCache`, `prCache`, `config` params from `renderDashboard` signature; remove `val now = Instant.now()`; update Scaladoc |
| `.iw/core/dashboard/CaskServer.scala` | Remove `state.issueCache`, `state.progressCache`, `state.prCache`, `config` args from `renderDashboard` call; remove `configPath`/`config` loading lines (46-47) |
| `.iw/core/test/DashboardServiceTest.scala` | Update all 27 `renderDashboard` call sites to remove `issueCache`, `progressCache`, `prCache`, `config` args; remove unused test imports if any |

## Testing Strategy

### Unit Tests (DashboardServiceTest - updated call sites)

All 27 existing test call sites are updated to use the new 4-parameter signature. No new tests are needed -- this is a pure refactoring that removes dead parameters. The tests themselves continue to validate the same behavior.

**Tests to update (all `renderDashboard` calls):**
1. `renderDashboard includes review state when present in cache` (line 65)
2. `renderDashboard handles missing review state gracefully` (line 82)
3. `renderDashboard with empty worktree list` (line 96)
4. `renderDashboard with multiple worktrees and mixed review state availability` (line 132)
5. `renderDashboard review state cache is keyed by issue ID` (line 165)
6. `fetchReviewStateForWorktree with missing state file doesn't crash dashboard` (line 186)
7. `fetchReviewStateForWorktree returns Some(Left) when JSON invalid` (line 212)
8. `fetchReviewStateForWorktree with fake paths renders dashboard` (line 231)
9. `renderDashboard does not crash with invalid review state` (line 251)
10. `Cache not updated when state is invalid` (line 271)
11. `renderDashboard accepts sshHost parameter` (line 286)
12. `renderDashboard includes SSH host input field in HTML` (line 301)
13. `renderDashboard SSH host form submits to current URL` (line 317)
14. `Dashboard HTML links to external CSS file for styles` (line 339)
15. `Dashboard HTML links to external JS file for visibilitychange handler` (line 353)
16. `renderDashboard with devMode=true renders DEV MODE banner` (line 369)
17. `renderDashboard with devMode=false does NOT render DEV MODE banner` (line 386)
18. `renderDashboard with devMode=false by default does NOT render banner` (line 403)
19. `renderDashboard links to external CSS file containing dev-mode-banner styles` (line 420)
20. `renderDashboard output contains CSS link to /static/dashboard.css` (line 437)
21. `renderDashboard output contains JS script for /static/dashboard.js` (line 450)
22. `renderDashboard output does NOT contain inline style tag` (line 463)
23. `renderDashboard output does NOT contain inline visibilitychange script` (line 477)
24. `renderDashboard computes and passes summaries to MainProjectsView` (line 503)
25. `renderDashboard root page does NOT contain worktree-list div` (line 521)
26. `renderDashboard root page does NOT contain worktree-card HTML` (line 538)
27. `renderDashboard root page does NOT poll /api/worktrees/changes` (line 555)

### Existing tests (no changes needed)
- All other test files are unaffected -- `renderDashboard` is only called from `CaskServer` and `DashboardServiceTest`
- E2E BATS tests are unaffected -- they test through HTTP, not the Scala API

### Verification
- Run `./iw test unit` to confirm all unit tests pass with updated signatures
- Run `./iw test e2e` to confirm E2E tests still pass
- Verify the compiler produces no warnings about unused parameters or imports

## Acceptance Criteria

- [ ] `renderDashboard` signature has exactly 4 parameters: `worktrees`, `reviewStateCache`, `sshHost`, `devMode`
- [ ] `renderDashboard` body does not contain `val now = Instant.now()`
- [ ] `renderDashboard` Scaladoc documents only the remaining parameters
- [ ] `CaskServer.dashboard()` calls `renderDashboard` with the new 4-parameter signature
- [ ] `CaskServer.dashboard()` no longer loads `config` (the `configPath`/`config` lines are removed)
- [ ] All 27 test call sites in `DashboardServiceTest.scala` use the new signature
- [ ] No unused imports in `DashboardServiceTest.scala`
- [ ] No unused imports in `DashboardService.scala` (verified: all imports still used by other methods)
- [ ] All unit tests pass (`./iw test unit`)
- [ ] All E2E tests pass (`./iw test e2e`)
- [ ] No compiler warnings
