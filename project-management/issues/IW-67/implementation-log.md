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
