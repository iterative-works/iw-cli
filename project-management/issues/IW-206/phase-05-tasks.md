# Phase 05 Tasks: Project cards on overview link to project details

**Issue:** IW-206
**Phase:** 05
**Goal:** Make project cards on overview page link to project details

---

## Tests (RED)

- [x] [impl] [x] [reviewed] [test] Create MainProjectsViewLinkTest with test for project name link presence
- [x] [impl] [x] [reviewed] [test] Add test for link href pointing to /projects/:projectName
- [x] [impl] [x] [reviewed] [test] Add test for Create button still present alongside link

---

## Implementation (GREEN)

- [x] [impl] [x] [reviewed] [impl] Wrap h3(project.projectName) in an `a` element with href="/projects/{projectName}" in MainProjectsView.renderProjectCard

---

## Integration

- [x] [impl] [x] [reviewed] [integration] Verify all existing tests pass
- [x] [impl] [x] [reviewed] [integration] Verify new tests pass

---

**Phase Status:** Complete
