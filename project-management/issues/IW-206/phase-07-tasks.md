# Phase 07 Tasks: HTMX auto-refresh for project worktree list

**Issue:** IW-206
**Phase:** 07
**Goal:** Add HTMX list-level auto-refresh to project details page

---

## Tests (RED)

- [x] [impl] [x] [reviewed] [test] Add test: worktree-list div has hx-get attribute pointing to project-scoped changes endpoint
- [x] [impl] [x] [reviewed] [test] Add test: worktree-list div has hx-trigger with polling interval
- [x] [impl] [x] [reviewed] [test] Add test: worktree-list div has hx-swap="none"
- [x] [impl] [x] [reviewed] [test] Add test: worktree-list div has hx-vals with JS expression for card IDs

---

## Implementation (GREEN)

- [x] [impl] [x] [reviewed] [impl] Add HTMX polling attributes to worktree-list div in ProjectDetailsView.render
- [x] [impl] [x] [reviewed] [impl] Add GET /api/projects/:projectName/worktrees/changes route to CaskServer

---

## Integration

- [x] [impl] [x] [reviewed] [integration] Verify all tests pass

---

**Phase Status:** Complete
