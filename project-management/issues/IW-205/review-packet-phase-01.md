---
generated_from: 1f5013c494abe479e7882955faa34892abb694e2
generated_at: 2026-02-21T20:52:57Z
branch: IW-205-phase-01
issue_id: IW-205
phase: 1
files_analyzed:
  - .iw/core/dashboard/presentation/views/ProjectSummary.scala
  - .iw/core/dashboard/presentation/views/MainProjectsView.scala
  - .iw/core/dashboard/DashboardService.scala
  - .iw/core/test/ProjectSummaryTest.scala
  - .iw/core/test/MainProjectsViewTest.scala
  - .iw/core/test/DashboardServiceTest.scala
---

# Review Packet: Phase 1 - Project cards show worktree count and summary status

## Goals

This phase enhances project cards on the dashboard root page to display worktree count and attention indicators. The implementation makes the root page useful as a projects overview, showing at a glance:

- How many worktrees each project has
- Whether any worktrees need attention (based on `ReviewState.needsAttention`)

Key objectives:
- Create a pure, functional view model (`ProjectSummary`) that pairs projects with computed statistics
- Implement pure summary computation using existing domain data
- Update the presentation layer to display the new summary information
- Maintain test coverage with comprehensive unit and integration tests

## Scenarios

- [x] Project card displays worktree count (e.g., "3 worktrees")
- [x] Project card displays "0 worktrees" when a project has no worktrees
- [x] Project card displays "1 worktree" (singular) when exactly one worktree exists
- [x] Project card displays attention indicator (e.g., "1 needs attention") when worktrees have `needsAttention == Some(true)`
- [x] Project card does NOT display attention indicator when `attentionCount == 0`
- [x] Worktrees with no review state cache entry are NOT counted as needing attention
- [x] Worktrees with `needsAttention == Some(false)` are NOT counted as needing attention
- [x] Summary computation correctly groups worktrees by derived main project path
- [x] Multiple projects display correct per-project counts independently

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/dashboard/presentation/views/ProjectSummary.scala` | `ProjectSummary.computeSummaries()` | Pure function that computes all summary statistics from worktrees and review state cache |
| `.iw/core/dashboard/presentation/views/MainProjectsView.scala` | `render(summaries: List[ProjectSummary])` | Presentation layer entry point - signature changed to accept summaries instead of raw projects |
| `.iw/core/dashboard/DashboardService.scala` | `renderDashboard()` lines 50-55 | Integration point where summaries are computed and passed to view |
| `.iw/core/test/ProjectSummaryTest.scala` | All 6 test cases | Comprehensive unit tests for summary computation logic |

## Diagrams

### Component Flow

```
DashboardService.renderDashboard()
    |
    +-- Derives MainProjects from worktrees (existing)
    |
    +-- ProjectSummary.computeSummaries()
    |       |
    |       +-- Groups worktrees by MainProject.deriveMainProjectPath()
    |       +-- Counts total worktrees per project
    |       +-- Counts worktrees with needsAttention == Some(true)
    |       |
    |       +-- Returns List[ProjectSummary]
    |
    +-- MainProjectsView.render(summaries)
            |
            +-- For each summary:
                    |
                    +-- Renders project card with:
                            - Project name (links to /projects/:name)
                            - Tracker info
                            - Worktree count text
                            - Attention indicator (if > 0)
                            - Create worktree button
```

### Data Flow

```
┌─────────────────────────────────────┐
│ DashboardService.renderDashboard()  │
│                                     │
│ Inputs:                             │
│ - worktrees: List[WorktreeReg]      │
│ - reviewStateCache: Map[String,     │
│                    CachedReviewState]│
└──────────────┬──────────────────────┘
               │
               v
┌──────────────────────────────────────┐
│ ProjectSummary.computeSummaries()    │
│                                      │
│ 1. Group worktrees by project path   │
│ 2. For each project:                 │
│    - Count worktrees                 │
│    - Count needing attention         │
└──────────────┬───────────────────────┘
               │
               v
        List[ProjectSummary]
        ┌───────────────┐
        │ project       │
        │ worktreeCount │
        │ attentionCount│
        └───────┬───────┘
                │
                v
┌───────────────────────────────────┐
│ MainProjectsView.render()         │
│                                   │
│ For each summary:                 │
│ - Render worktree count text      │
│ - Conditionally render attention  │
│   indicator                       │
└───────────────────────────────────┘
```

## Test Summary

### Unit Tests

#### ProjectSummaryTest (6 tests)
- ✓ `computeSummaries with empty inputs returns empty list`
  - Verifies edge case handling
  
- ✓ `computeSummaries with single project and multiple worktrees returns correct count`
  - Core functionality: counting worktrees for one project
  
- ✓ `computeSummaries with multiple projects returns correct per-project counts`
  - Verifies grouping logic works correctly across projects
  
- ✓ `computeSummaries worktree with needsAttention == Some(true) increments attention count`
  - Positive case for attention counting
  
- ✓ `computeSummaries worktree with no review state does NOT count as needing attention`
  - Ensures missing cache entries don't create false positives
  
- ✓ `computeSummaries worktree with needsAttention == Some(false) does NOT count`
  - Explicit false values should not increment count

#### MainProjectsViewTest (4 new tests + 10 updated)
New tests for summary display:
- ✓ `render with summary showing worktree count text`
- ✓ `render with zero worktrees shows 0 worktrees`
- ✓ `render with attention count > 0 shows attention indicator`
- ✓ `render with attention count == 0 does not show attention indicator`

Updated tests: All existing tests updated to use `ProjectSummary` wrapper instead of raw `MainProject`.

### Integration Tests

#### DashboardServiceTest (1 new test)
- ✓ `renderDashboard computes and passes summaries to MainProjectsView`
  - Verifies end-to-end integration from service through view rendering

### Test Coverage Summary

| Component | Unit Tests | Integration Tests | Total |
|-----------|------------|-------------------|-------|
| ProjectSummary | 6 | - | 6 |
| MainProjectsView | 14 | - | 14 |
| DashboardService | - | 1 | 1 |
| **Total** | **20** | **1** | **21** |

## Files Changed

### New Files (2)

1. **`.iw/core/dashboard/presentation/views/ProjectSummary.scala`** (56 lines)
   - View model case class pairing `MainProject` with counts
   - Pure `computeSummaries()` function
   - Zero dependencies on I/O or external state

2. **`.iw/core/test/ProjectSummaryTest.scala`** (166 lines)
   - Comprehensive unit test suite
   - 6 test cases covering all computation scenarios

### Modified Files (4)

3. **`.iw/core/dashboard/presentation/views/MainProjectsView.scala`**
   - **Changed**: `render()` signature from `List[MainProject]` to `List[ProjectSummary]`
   - **Added**: Worktree count display in `renderProjectCard()`
   - **Added**: Conditional attention indicator rendering
   - **Lines changed**: ~20 (primarily in `renderProjectCard()` method)

4. **`.iw/core/dashboard/DashboardService.scala`**
   - **Added**: Import for `ProjectSummary`
   - **Added**: Computation of summaries (lines 50-55)
   - **Changed**: `MainProjectsView.render()` call to pass summaries
   - **Lines changed**: ~10

5. **`.iw/core/test/MainProjectsViewTest.scala`**
   - **Updated**: All existing tests to use `ProjectSummary` wrapper
   - **Added**: 4 new tests for summary display
   - **Lines changed**: ~60

6. **`.iw/core/test/DashboardServiceTest.scala`**
   - **Added**: 1 integration test for summary computation
   - **Lines changed**: ~25

<details>
<summary>Detailed Changes Summary</summary>

```
New files:
  .iw/core/dashboard/presentation/views/ProjectSummary.scala  | 56 ++
  .iw/core/test/ProjectSummaryTest.scala                      | 166 ++

Modified files:
  .iw/core/dashboard/presentation/views/MainProjectsView.scala | ~20 lines
  .iw/core/dashboard/DashboardService.scala                    | ~10 lines
  .iw/core/test/MainProjectsViewTest.scala                     | ~60 lines
  .iw/core/test/DashboardServiceTest.scala                     | ~25 lines

Total: 2 new files, 4 modified files
```

</details>

## Implementation Highlights

### Pure Functional Design

The `ProjectSummary.computeSummaries()` function is completely pure:
- No I/O operations
- No side effects
- Deterministic output from inputs
- Easily testable in isolation

This follows the FCIS (Functional Core, Imperative Shell) architecture documented in `.iw/core/CLAUDE.md`.

### View Model Pattern

`ProjectSummary` serves as a presentation-layer view model:
- Encapsulates display logic data
- Separates computation from rendering
- Makes the view layer simpler and more focused

### Attention Logic

The attention counting uses the existing `ReviewState.needsAttention` field:
```scala
val attentionCount = projectWorktrees.count { wt =>
  reviewStateCache.get(wt.issueId).exists(_.state.needsAttention == Some(true))
}
```

This is clean because:
- Only explicit `Some(true)` counts as needing attention
- Missing cache entries → no attention needed
- Explicit `Some(false)` → no attention needed
- No heuristic signals (avoids false positives)

### Worktree Grouping

Uses existing `MainProject.deriveMainProjectPath()` to group worktrees:
```scala
val worktreesByProject = worktrees.groupBy { wt =>
  MainProject.deriveMainProjectPath(wt.path)
}
```

This ensures consistency with how projects are derived elsewhere in the codebase.

## Next Steps

Phase 1 is complete and ready for review. The next phases are:

- **Phase 2**: Remove flat worktree list from root page
- **Phase 3**: Simplify DashboardService after worktree list removal

Phase 1 can be deployed independently. The enhanced project cards work alongside the existing worktree list, providing a smooth transition path.
