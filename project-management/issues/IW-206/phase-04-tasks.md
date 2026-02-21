# Phase 04 Tasks: Project-scoped Create Worktree button

**Issue:** IW-206
**Phase:** 04
**Goal:** Add Create Worktree button to project details page, scoped to the current project

---

## Setup

- [x] [impl] [x] [reviewed] [setup] Read ProjectDetailsView.scala current structure
- [x] [impl] [x] [reviewed] [setup] Read MainProjectsView.scala create button pattern

---

## Tests (RED)

- [x] [impl] [x] [reviewed] [test] Create ProjectDetailsCreateButtonTest with test for create button presence
- [x] [impl] [x] [reviewed] [test] Add test for create button hx-get URL with encoded project path
- [x] [impl] [x] [reviewed] [test] Add test for create button hx-target and hx-swap attributes
- [x] [impl] [x] [reviewed] [test] Add test for modal-container div presence

---

## Implementation (GREEN)

- [x] [impl] [x] [reviewed] [impl] Add URLEncoder import to ProjectDetailsView.scala
- [x] [impl] [x] [reviewed] [impl] Add create button in project-header section of ProjectDetailsView.render
- [x] [impl] [x] [reviewed] [impl] Add modal-container div at end of ProjectDetailsView.render output

---

## Integration

- [x] [impl] [x] [reviewed] [integration] Verify all existing tests pass
- [x] [impl] [x] [reviewed] [integration] Verify new tests pass (GREEN)

---

## Phase Success Criteria

- Create Worktree button visible on project details page
- Button has correct HTMX attributes (hx-get with encoded project path, hx-target, hx-swap)
- Modal container div present for HTMX to render into
- All tests pass (existing + new)
- Root dashboard unaffected

**Phase Status:** Complete
