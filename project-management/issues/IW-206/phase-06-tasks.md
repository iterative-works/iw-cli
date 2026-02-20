# Phase 06 Tasks: Handle unknown project name gracefully

**Issue:** IW-206
**Phase:** 06
**Goal:** Replace plain 404 with styled error page for unknown projects

---

## Tests (RED)

- [x] [impl] [x] [reviewed] [test] Add test for renderNotFound includes project name
- [x] [impl] [x] [reviewed] [test] Add test for renderNotFound includes link back to overview (href="/")
- [x] [impl] [x] [reviewed] [test] Add test for renderNotFound includes breadcrumb

---

## Implementation (GREEN)

- [x] [impl] [x] [reviewed] [impl] Add renderNotFound(projectName) method to ProjectDetailsView
- [x] [impl] [x] [reviewed] [impl] Update CaskServer 404 case to use PageLayout.render with renderNotFound

---

## Integration

- [x] [impl] [x] [reviewed] [integration] Verify all tests pass

---

**Phase Status:** Complete
