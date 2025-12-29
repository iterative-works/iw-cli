# Phase 2 Tasks: Handle invalid Mermaid syntax gracefully

**Issue:** IW-67
**Phase:** 2 of 3
**Context:** phase-02-context.md

---

## Tasks

### Setup

- [ ] [test] Read existing ArtifactViewTest to understand test patterns

### Tests First (TDD)

- [ ] [test] Write test: Mermaid initialization includes suppressErrors config
- [ ] [test] Write test: CSS includes error styling for mermaid errors
- [ ] [test] Run tests to verify they fail (TDD red phase)

### Implementation

- [ ] [impl] Update Mermaid initialization in ArtifactView with suppressErrors: false
- [ ] [impl] Add CSS styling for Mermaid error messages to ArtifactView.styles
- [ ] [impl] Run tests to verify they pass (TDD green phase)

### Integration & Verification

- [ ] [verify] Run full unit test suite to verify no regressions
- [ ] [verify] Create test markdown file with invalid Mermaid syntax
- [ ] [verify] Manual browser verification of error display

---

## Notes

- Phase 2 is a minimal change - just configure Mermaid error handling and add CSS
- Mermaid v10.9.4 has good built-in error handling that we're leveraging
- CSS selectors may need adjustment based on actual Mermaid error output
