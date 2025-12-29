# Phase 3 Context: Support common Mermaid diagram types

**Issue:** IW-67
**Phase:** 3 - Support common Mermaid diagram types
**Generated:** 2025-12-30

---

## Goals

This phase validates that the Mermaid.js integration from Phase 1 correctly handles multiple diagram types. Since Mermaid.js supports all diagram types with the same JavaScript library, this phase is primarily verification and documentation.

**Primary objectives:**
1. Verify sequence diagrams render correctly
2. Verify class diagrams render correctly
3. Verify other common diagram types (gantt, pie, state) work
4. Create test fixtures demonstrating each diagram type
5. Update documentation to list supported diagram types

---

## Scope

### In Scope
- Creating test markdown files with different diagram types
- Verifying each diagram type renders in browser (E2E)
- Documenting supported diagram types
- Adding any additional CSS if certain diagram types need styling adjustments

### Out of Scope
- Adding new Mermaid features or configuration beyond Phase 1/2
- Custom theme development for different diagram types
- Automated E2E tests (manual verification is sufficient for this simple validation)
- Supporting experimental/deprecated Mermaid diagram types

---

## Dependencies

### From Previous Phases
- **Phase 1:** Mermaid.js integration with CDN script loading, `transformMermaidBlocks()` function, and `neutral` theme
- **Phase 2:** Error handling via `securityLevel: 'loose'` and error CSS styling

### Available Utilities
From `.iw/core/MarkdownRenderer.scala`:
- `transformMermaidBlocks()` - Converts `<pre><code class="language-mermaid">` to `<div class="mermaid">`
- `decodeHtmlEntities()` - Decodes HTML entities in Mermaid content

From `.iw/core/presentation/views/ArtifactView.scala`:
- Mermaid.js v10.9.4 loaded from jsDelivr CDN
- Mermaid initialization with `{ startOnLoad: true, theme: 'neutral', securityLevel: 'loose' }`
- Error styling CSS

---

## Technical Approach

This phase is primarily validation. No code changes should be needed since Mermaid.js handles all diagram types automatically.

### Testing Strategy

1. **Create test fixtures** - Markdown files with each diagram type in `.iw/core/test/fixtures/`
2. **Unit tests** - Verify MarkdownRenderer correctly transforms each diagram type's code blocks
3. **Manual E2E verification** - View each diagram type in browser via artifact viewer

### Diagram Types to Validate

Based on Mermaid.js v10.9.4 documentation, these are the common diagram types:

| Type | Syntax Keyword | Example Use |
|------|----------------|-------------|
| Flowchart | `graph TD` / `graph LR` / `flowchart TD` | Process flows, decision trees |
| Sequence | `sequenceDiagram` | API call sequences, user flows |
| Class | `classDiagram` | OOP class relationships |
| State | `stateDiagram-v2` | State machines, lifecycle |
| ER Diagram | `erDiagram` | Database schema |
| Gantt | `gantt` | Project timelines |
| Pie | `pie` | Data distribution |
| Git Graph | `gitGraph` | Branch visualization |

---

## Files to Modify

### Files to Create
- `.iw/core/test/fixtures/mermaid-diagram-types.md` - Test fixtures for all diagram types
- `.iw/core/test/MermaidDiagramTypesTest.scala` - Unit tests for diagram type rendering

### Files to Modify (if needed)
- `.iw/core/presentation/views/ArtifactView.scala` - Only if diagram-specific CSS is needed
- `.iw/core/test/ArtifactViewTest.scala` - Add tests for any new CSS

---

## Testing Strategy

### Unit Tests
- Test that each diagram type's code block is correctly transformed to `<div class="mermaid">`
- Verify HTML entity decoding works for each diagram type's syntax

### Manual E2E Verification
Create `test-mermaid-diagrams.md` in the issue folder with examples of:
- Flowchart (already verified in Phase 1)
- Sequence diagram
- Class diagram
- State diagram
- ER diagram (if syntax is simple enough)
- Pie chart (simple data visualization)

Run the artifact viewer and verify each diagram renders correctly.

---

## Acceptance Criteria

1. **Flowchart diagrams** - Already verified in Phase 1 (regression test)
2. **Sequence diagrams** - Render correctly with participants and message arrows
3. **Class diagrams** - Render correctly with boxes, relationships, and labels
4. **State diagrams** - Render correctly with states and transitions
5. **Pie charts** - Render correctly with labeled segments
6. **Other diagrams** - At minimum: ER diagrams or Git graphs work

### Exit Criteria
- All unit tests pass
- Manual browser verification shows each diagram type renders
- No regressions to Phase 1/2 functionality
- Documentation lists supported diagram types

---

## Notes

- This phase is estimated at 1 hour due to the validation-only nature
- If any diagram type fails to render, investigate whether it's a Mermaid.js limitation or our integration issue
- Consider creating a "mermaid examples" artifact that users can reference for syntax
