---
generated_from: 8bb8076ddb605ac8842591a3d8ec01f8a3e0575a
generated_at: 2026-02-20T18:43:38+01:00
branch: IW-206
issue_id: IW-206
phase: 5
files_analyzed:
  - .iw/core/dashboard/presentation/views/MainProjectsView.scala
  - .iw/core/test/MainProjectsViewTest.scala
---

# Review Packet: Phase 5 - Project Cards Link to Project Details

## Goals

This phase adds navigation from the overview page to project detail pages by making the project name on each project card a clickable link.

Key objectives:
- Make project names on overview cards link to `/projects/:projectName`
- Preserve the independent functionality of the Create button
- Ensure comprehensive unit test coverage for the link behavior

## Scenarios

- [ ] Project name on overview card links to project details page at `/projects/:projectName`
- [ ] Link wraps the project heading (h3 element)
- [ ] Create button continues to work independently alongside the new link
- [ ] Link uses the correct project name in the URL path

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/dashboard/presentation/views/MainProjectsView.scala` | `renderProjectCard()` | Modified to wrap project name in an anchor tag linking to project details |
| `.iw/core/test/MainProjectsViewTest.scala` | New test cases (lines 139-174) | Three new unit tests verify link presence, structure, and coexistence with Create button |

## Diagrams

### Component Structure

```
MainProjectsView
├── render(projects: List[MainProject])
│   └── renderProjectCard(project: MainProject)
│       ├── a(href="/projects/{projectName}")  ← NEW
│       │   └── h3(projectName)
│       ├── div.project-info (tracker & team)
│       └── button.create-worktree-button
```

### Before/After View Structure

**Before Phase 5:**
```html
<div class="main-project-card">
  <h3>iw-cli</h3>
  <div class="project-info">...</div>
  <button class="create-worktree-button">...</button>
</div>
```

**After Phase 5:**
```html
<div class="main-project-card">
  <a href="/projects/iw-cli">
    <h3>iw-cli</h3>
  </a>
  <div class="project-info">...</div>
  <button class="create-worktree-button">...</button>
</div>
```

## Test Summary

### Unit Tests (3 new tests)

All tests pass successfully:

| Test | Type | Status | Purpose |
|------|------|--------|---------|
| `project name links to project details page` | Unit | ✓ | Verifies href attribute points to `/projects/iw-cli` |
| `project name link wraps the project heading` | Unit | ✓ | Confirms link structure wraps the h3 element |
| `create button still present alongside project link` | Unit | ✓ | Ensures Create button functionality is preserved |

**Test Coverage:**
- Link presence and href correctness
- HTML structure (link wrapping heading)
- Non-regression (Create button still works)

### E2E Tests

No E2E tests for this phase (view-only change, no new behavior requiring browser testing).

## Files Changed

<details>
<summary>2 files changed: 1 source file, 1 test file</summary>

### Modified Files

**`.iw/core/dashboard/presentation/views/MainProjectsView.scala`** (+4, -1 lines)
- Wrapped project name h3 in anchor tag
- Added href pointing to `/projects/:projectName`
- Updated comment to reflect linking behavior

**`.iw/core/test/MainProjectsViewTest.scala`** (+37 lines)
- Added test: "project name links to project details page"
- Added test: "project name link wraps the project heading"
- Added test: "create button still present alongside project link"

</details>

## Implementation Notes

### Design Decisions

1. **Minimal change approach**: Only the project name heading was wrapped in a link, preserving all other card functionality
2. **URL structure**: Uses `/projects/:projectName` to match the existing routing pattern
3. **No styling changes**: Relies on existing CSS for link styling (if any)

### Code Quality

- Change follows functional programming principles (pure rendering function)
- Consistent with existing ScalaTags patterns in the codebase
- URL construction uses simple string interpolation (project names are safe identifiers)
- No new dependencies introduced

### Test Quality

- Tests verify both positive behavior (link exists, correct href) and non-regression (Create button preserved)
- Tests use HTML string matching which is simple but effective for view testing
- Each test follows the existing pattern: create test data, render, assert HTML content

## Verification

To verify this phase manually:

1. Start the dashboard server: `./iw dash`
2. Navigate to the overview page (root `/`)
3. Observe project cards in the "Main Projects" section
4. Click on a project name
5. Verify navigation to `/projects/:projectName`
6. Go back and click the "+ Create" button
7. Verify the Create worktree modal still opens correctly

**Expected behavior:**
- Project name is visually clickable (browser default link styling or custom CSS)
- Clicking project name navigates to project details
- Create button remains functional and independent
