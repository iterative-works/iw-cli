# Phase 1 Tasks: Render Mermaid flowchart diagram

**Issue:** IW-67
**Phase:** 1 of 3
**Estimated Effort:** 3-4 hours

---

## Setup Tasks

- [ ] Verify existing test files can be modified (read current test structure)
- [ ] Verify MarkdownRenderer.scala and ArtifactView.scala locations

---

## Test Tasks (TDD - Write tests FIRST)

### Unit Tests for MarkdownRenderer (HTML transformation)

- [ ] [test] Write test: basic Mermaid code block transforms to `<div class="mermaid">`
- [ ] [test] Write test: Mermaid div contains unescaped diagram syntax (arrows `-->` not `--&gt;`)
- [ ] [test] Write test: multiple Mermaid blocks are all transformed
- [ ] [test] Write test: non-Mermaid code blocks (scala, javascript) remain unchanged
- [ ] [test] Write test: empty Mermaid block is handled correctly
- [ ] [test] Write test: Mermaid block with special characters preserves content

### Unit Tests for ArtifactView (Script inclusion)

- [ ] [test] Write test: rendered HTML includes Mermaid.js script tag from CDN
- [ ] [test] Write test: script tag uses correct version (v10.9.4)
- [ ] [test] Write test: rendered HTML includes Mermaid initialization script
- [ ] [test] Write test: initialization script configures `neutral` theme
- [ ] [test] Write test: initialization script sets `startOnLoad: true`
- [ ] [test] Write test: Mermaid scripts are in `<head>` section before `</head>`

---

## Implementation Tasks (Make tests pass)

### MarkdownRenderer Implementation

- [ ] [impl] Add HTML post-processing function to MarkdownRenderer.toHtml()
- [ ] [impl] Implement regex/parser to find `<pre><code class="language-mermaid">` blocks
- [ ] [impl] Extract and decode HTML entities from code block content
- [ ] [impl] Replace matched blocks with `<div class="mermaid">` containing decoded content
- [ ] [impl] Handle multiple Mermaid blocks in same document
- [ ] [impl] Verify all MarkdownRenderer tests pass (run test suite)

### ArtifactView Implementation

- [ ] [impl] Add Mermaid.js CDN script tag to ArtifactView.render() `<head>` section
- [ ] [impl] Add Mermaid initialization script with config object
- [ ] [impl] Configure Mermaid with `startOnLoad: true` and `theme: 'neutral'`
- [ ] [impl] Use scalatags `raw()` for script content to avoid escaping
- [ ] [impl] Verify all ArtifactView tests pass (run test suite)

---

## Integration Tasks

- [ ] Run full unit test suite: `./iw test unit`
- [ ] Verify no regressions in existing markdown rendering tests
- [ ] Verify no regressions in existing ArtifactView rendering tests

---

## E2E Verification Tasks (Manual)

- [ ] Create test markdown file in project-management/issues/IW-67/test-mermaid.md with:
  - Mermaid flowchart with decision diamond
  - Regular Scala code block (regression check)
  - Multiple Mermaid diagrams
- [ ] Start dashboard server: `./iw server start`
- [ ] Open artifact viewer in browser
- [ ] Navigate to test-mermaid.md
- [ ] Verify flowchart renders as visual diagram (not code text)
- [ ] Verify decision node renders as diamond shape
- [ ] Verify arrows and labels display correctly
- [ ] Verify Scala code block still renders as code (not transformed)
- [ ] Verify all Mermaid diagrams on page render correctly

---

## Task Completion Notes

**TDD Workflow:**
1. Write failing test
2. Run test to confirm it fails
3. Implement minimal code to make test pass
4. Run test to confirm success
5. Refactor if needed

**Test Commands:**
- Unit tests only: `./iw test unit`
- All tests: `./iw test`

**Success Criteria:**
- All unit tests pass
- No regressions in existing tests
- Manual browser verification confirms visual diagram rendering
- Non-Mermaid code blocks unchanged

---

**Phase Status:** Ready for Implementation

**Total Tasks:** 35 tasks across Setup (2), Tests (12), Implementation (11), Integration (3), E2E (7)
