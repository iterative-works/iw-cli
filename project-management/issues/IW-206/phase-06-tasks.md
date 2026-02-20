# Phase 06 Tasks: Handle unknown project name gracefully

**Issue:** IW-206
**Phase:** 06
**Goal:** Replace plain 404 with styled error page for unknown projects

---

## Tests (RED)

- [ ] [impl] [ ] [reviewed] [test] Add test for renderNotFound includes project name
- [ ] [impl] [ ] [reviewed] [test] Add test for renderNotFound includes link back to overview (href="/")
- [ ] [impl] [ ] [reviewed] [test] Add test for renderNotFound includes breadcrumb

---

## Implementation (GREEN)

- [ ] [impl] [ ] [reviewed] [impl] Add renderNotFound(projectName) method to ProjectDetailsView
- [ ] [impl] [ ] [reviewed] [impl] Update CaskServer 404 case to use PageLayout.render with renderNotFound

---

## Integration

- [ ] [impl] [ ] [reviewed] [integration] Verify all tests pass

---

**Phase Status:** Not Started
