# Implementation Log: Support Mermaid diagrams in Markdown renderer

Issue: IW-67

This log tracks the evolution of implementation across phases.

---

## Phase 1: Render Mermaid flowchart diagram (2025-12-29)

**What was built:**
- Infrastructure: `.iw/core/MarkdownRenderer.scala` - Added `transformMermaidBlocks()` to post-process HTML, converting `<pre><code class="language-mermaid">` to `<div class="mermaid">`
- Infrastructure: `.iw/core/MarkdownRenderer.scala` - Added `decodeHtmlEntities()` to decode HTML entities (e.g., `--&gt;` → `-->`)
- Presentation: `.iw/core/presentation/views/ArtifactView.scala` - Added Mermaid.js v10.9.4 script from jsDelivr CDN
- Presentation: `.iw/core/presentation/views/ArtifactView.scala` - Added Mermaid initialization with `neutral` theme

**Decisions made:**
- Server-side transformation chosen over client-side JavaScript manipulation (more reliable, testable, no flash of unstyled content)
- Regex with DOTALL flag for matching multiline code blocks
- Simple string replacement for HTML entity decoding (handles 5 common entities)
- jsDelivr CDN with exact version pin (v10.9.4) for reproducibility

**Patterns applied:**
- Post-processing pipeline: markdown → flexmark HTML → Mermaid transformation
- Separation of concerns: MarkdownRenderer handles content transformation, ArtifactView handles page structure

**Testing:**
- Unit tests: 12 tests added
  - 6 tests for MarkdownRenderer Mermaid transformation
  - 6 tests for ArtifactView script inclusion
- Integration tests: Existing suite passes (no regressions)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20251229.md
- Major findings: No critical issues. Minor style suggestions (test granularity, function composition).

**For next phases:**
- Available utilities: `transformMermaidBlocks()` and `decodeHtmlEntities()` in MarkdownRenderer
- Extension points: Mermaid initialization config can be extended for error handling (Phase 2)
- Notes: E2E manual browser verification pending

**Files changed:**
```
M  .iw/core/MarkdownRenderer.scala
M  .iw/core/presentation/views/ArtifactView.scala
M  .iw/core/test/ArtifactViewTest.scala
M  .iw/core/test/MarkdownRendererTest.scala
```

---

## Phase 2: Handle invalid Mermaid syntax gracefully (2025-12-29)

**What was built:**
- Presentation: `.iw/core/presentation/views/ArtifactView.scala` - Added `securityLevel: 'loose'` to Mermaid config for detailed error messages
- Presentation: `.iw/core/presentation/views/ArtifactView.scala` - Added CSS styling for Mermaid diagrams and error messages

**Decisions made:**
- Used `securityLevel: 'loose'` to enable full error message display (Mermaid default is 'strict' which limits output)
- Applied Material Design error color (#d32f2f) for consistent error styling
- Used `:has()` CSS pseudo-selector for error container styling (acceptable for local dev tool targeting modern browsers)

**Patterns applied:**
- Graceful degradation: Invalid diagrams show errors while valid content renders normally
- CSS error indicators: Visual feedback (red border, pink background) for error states

**Testing:**
- Unit tests: 2 tests added
  - Test for securityLevel configuration
  - Test for error CSS styling
- E2E: Test markdown file created with invalid syntax (`test-mermaid-errors.md`)
- Integration tests: All existing tests pass (no regressions)

**Code review:**
- Iterations: 1
- Major findings: No critical issues. `:has()` selector noted as modern CSS (acceptable for target audience).

**For next phases:**
- Available utilities: Error styling is in place for any Mermaid errors
- Extension points: Can add custom error handlers via Mermaid callbacks if needed
- Notes: Manual browser verification pending

**Files changed:**
```
M  .iw/core/presentation/views/ArtifactView.scala
M  .iw/core/test/ArtifactViewTest.scala
A  project-management/issues/IW-67/test-mermaid-errors.md
```

---
