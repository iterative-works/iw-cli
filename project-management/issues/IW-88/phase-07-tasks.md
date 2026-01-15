# Phase 7 Tasks: Load recent issues on modal open

**Issue:** IW-88
**Phase:** 7 of 7
**Context:** phase-07-context.md

## Implementation Tasks

### Setup

- [x] [test] Write test: search results container has hx-trigger="load" for auto-load
- [x] [test] Write test: search results container has hx-get pointing to /api/issues/recent
- [x] [test] Write test: hx-get includes project parameter when projectPath is provided

### Implementation

- [x] [impl] Add hx-get attribute to #search-results div pointing to /api/issues/recent endpoint
- [x] [impl] Add hx-trigger="load" attribute to #search-results div
- [x] [impl] Add hx-swap="innerHTML" attribute for proper content replacement
- [x] [impl] Handle project parameter in URL (same pattern as search input)

### Verification

- [x] [test] Run all unit tests and verify they pass
- [ ] [verify] Manual test: Open modal → recent issues appear
- [ ] [verify] Manual test: Type search → results replace recent issues
- [ ] [verify] Manual test: Clear search → recent issues appear again

## TDD Flow

1. Write test for hx-trigger="load" attribute presence
2. Run test → RED (attribute doesn't exist)
3. Add hx-trigger="load" to search-results div
4. Run test → GREEN
5. Write test for hx-get="/api/issues/recent" attribute
6. Run test → RED
7. Add hx-get attribute to search-results div
8. Run test → GREEN
9. Write test for project parameter in URL
10. Run test → RED
11. Update hx-get to include project parameter
12. Run test → GREEN
13. Run all tests → all GREEN
14. Manual verification

## Notes

- Minimal changes: only modify CreateWorktreeModal.scala and its test
- The `/api/issues/recent` endpoint already exists and works
- HTMX will automatically call the endpoint when the modal loads
- The search-results div already has the correct ID and class
