---
generated_from: 907913c9a419068a91a4cb1497a167697d4204af
generated_at: 2026-02-22T13:36:25Z
branch: IW-205-phase-02
issue_id: IW-205
phase: 2
files_analyzed:
  - .iw/core/dashboard/DashboardService.scala
  - .iw/core/test/DashboardServiceTest.scala
---

# Review Packet: Phase 2 - Remove flat worktree list from root page

## Goals

This phase removes the flat worktree list from the dashboard root page, transforming it into a clean projects overview. The root page now displays only:
- Dashboard header with SSH host configuration
- Project summary cards (from Phase 1)
- Modal container (for Create Worktree functionality)

Individual worktree cards are now accessible only via the project details page (`/projects/:projectName`).

Additionally, this phase removes the per-worktree data fetching logic that existed solely to supply data to `WorktreeListView`. This eliminates unnecessary cache reads and API calls during root page rendering.

Key objectives:
- Remove `WorktreeListView.render()` call from root page
- Remove `worktreesWithData` computation block (13 lines of dead code)
- Update tests to assert worktree cards are NOT present on root page
- Verify project details page remains unaffected

## Scenarios

- [ ] Root page (`/`) does NOT render `WorktreeListView`
- [ ] Root page HTML does NOT contain `worktree-list` div
- [ ] Root page HTML does NOT contain `worktree-card` elements
- [ ] Root page does NOT poll `/api/worktrees/changes`
- [ ] Root page still renders header, project summary cards, and modal container
- [ ] `DashboardService.renderDashboard` no longer fetches per-worktree data
- [ ] Project details page continues to show worktree cards (unaffected)
- [ ] All tests pass with updated assertions

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/dashboard/DashboardService.scala` | `renderDashboard()` | Main service method - removed worktree list rendering and data fetching |
| `.iw/core/test/DashboardServiceTest.scala` | Test suite | Comprehensive test updates showing behavioral changes |

## Diagrams

### Before Phase 2: Root Page Rendering Flow

```
renderDashboard()
├─ Sort worktrees
├─ Derive main projects
├─ Compute project summaries
├─ Fetch per-worktree data ───┐ (REMOVED)
│  ├─ fetchIssueForWorktreeCachedOnly()
│  ├─ fetchProgressForWorktree()
│  ├─ fetchGitStatusForWorktree()
│  └─ fetchPRForWorktreeCachedOnly()
│
└─ Render body content
   ├─ Dashboard header
   ├─ MainProjectsView (project cards)
   ├─ WorktreeListView ────────┘ (REMOVED)
   └─ Modal container
```

### After Phase 2: Root Page Rendering Flow

```
renderDashboard()
├─ Sort worktrees
├─ Derive main projects
├─ Compute project summaries
│
└─ Render body content
   ├─ Dashboard header
   ├─ MainProjectsView (project cards)
   └─ Modal container
```

### Architecture: Separation of Concerns

```
Root Page (/)
└─ Shows: Project overview
   ├─ Header + SSH config
   ├─ Project cards with counts
   └─ Modal for creating worktrees

Project Details (/projects/:name)
└─ Shows: Project-specific worktrees
   ├─ Header + back navigation
   ├─ Create button
   └─ Filtered worktree cards
```

## Test Summary

### Unit Tests: DashboardServiceTest

**New tests (3 additions):**
- `renderDashboard root page does NOT contain worktree-list div` - Negative assertion
- `renderDashboard root page does NOT contain worktree-card HTML` - Negative assertion  
- `renderDashboard root page does NOT poll /api/worktrees/changes` - Negative assertion

**Updated tests (9 modifications):**
- `renderDashboard includes review state when present in cache` - Removed `assert(html.contains("IWLE-123"))`
- `renderDashboard handles missing review state gracefully` - Removed `assert(html.contains("IWLE-456"))`
- `renderDashboard with multiple worktrees and mixed review state availability` - Changed to assert project section exists
- `renderDashboard review state cache is keyed by issue ID` - Removed `assert(html.contains("IWLE-789"))`
- `fetchReviewStateForWorktree with missing state file doesn't crash dashboard` - Removed `assert(html.contains("IWLE-MISSING"))`
- `fetchReviewStateForWorktree returns Some(Left) when JSON invalid` - Changed to assert HTML structure only
- `fetchReviewStateForWorktree with fake paths renders dashboard` - Removed `assert(html.contains("IWLE-VALID"))`
- `renderDashboard does not crash with invalid review state` - Removed assertions for specific issue IDs
- `renderDashboard SSH host is configurable per deployment` - Removed `assert(html.contains("IWLE-SSH-1"))`

**Removed tests (4 deletions):**
- `renderDashboard includes Zed button with configured SSH host` - Zed buttons are worktree card concerns
- `renderDashboard Zed button uses correct SSH host for multiple worktrees` - Same reason
- `renderDashboard sorts worktrees by priority` - Sorting is no longer a root page concern
- `renderDashboard sort is stable for equal priorities` - Same reason

**Test Coverage:**
- Unit: 15 total tests (3 new + 9 updated + 3 retained)
- Integration: Covered by unit tests (rendering is pure)
- E2E: No BATS changes needed (none currently assert worktree cards on root page)

## Files Changed

### Modified Files (2)

<details>
<summary><code>.iw/core/dashboard/DashboardService.scala</code></summary>

**Changes:**
- Updated PURPOSE comment to reflect "project cards" instead of "worktree list"
- Removed `worktreesWithData` computation block (lines 57-68 deleted)
  - Eliminated calls to `fetchIssueForWorktreeCachedOnly`
  - Eliminated calls to `fetchProgressForWorktree`
  - Eliminated calls to `fetchGitStatusForWorktree`
  - Eliminated calls to `fetchPRForWorktreeCachedOnly`
- Removed `WorktreeListView.render(worktreesWithData, now, sshHost)` call
- Updated comment from "Main projects section (above worktree list)" to "Main projects section"

**Lines changed:** 13 deletions, 1 modification

**Impact:**
- Root page no longer fetches per-worktree cache data
- Root page no longer renders individual worktree cards
- Simplifies rendering logic and reduces cache reads

</details>

<details>
<summary><code>.iw/core/test/DashboardServiceTest.scala</code></summary>

**Changes:**
- Removed 12 assertions checking for specific issue IDs in root page HTML
- Removed 4 complete test methods (Zed button tests, worktree sorting tests)
- Added 3 new negative assertion tests
- Updated 1 test to check for project section instead of individual worktrees

**Lines changed:** 154 deletions, 47 additions

**Test breakdown:**
- Tests removed: 4 (worktree-specific rendering and ordering)
- Tests updated: 9 (changed assertions from positive to neutral/negative)
- Tests added: 3 (negative assertions for removed functionality)

**Impact:**
- Test suite now reflects root page as projects overview
- Tests no longer assert worktree cards on root page
- Tests verify absence of worktree list and polling

</details>

## Summary

Phase 2 successfully simplifies the dashboard root page by removing the flat worktree list and associated data fetching. The implementation:

1. **Removes 13 lines of per-worktree data fetching** that called 4 different cache/service functions
2. **Removes 1 line calling `WorktreeListView.render()`** from the body content
3. **Updates 9 tests** to remove assertions about individual issue IDs appearing on root page
4. **Removes 4 tests** that tested worktree-specific concerns (sorting, Zed buttons)
5. **Adds 3 tests** that explicitly verify worktree list absence

The root page is now a clean projects overview that loads faster (no per-worktree cache reads) and provides better navigation hierarchy. Individual worktree details remain accessible via project details pages.

**Phase 3 preview:** The next phase will further simplify `renderDashboard` by removing unused parameters (`issueCache`, `progressCache`, `prCache`) from the function signature, since these caches are no longer accessed during root page rendering.
