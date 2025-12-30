# Story-Driven Analysis: Support Mermaid diagrams in Markdown renderer

**Issue:** IW-67
**Created:** 2025-12-29
**Status:** Draft
**Classification:** Simple

## Problem Statement

Users need to create visual diagrams (flowcharts, sequence diagrams, class diagrams, etc.) in markdown documentation. Currently, the markdown renderer only supports text, code blocks, and tables, requiring users to create diagrams externally and embed them as images.

Adding Mermaid.js support enables users to define diagrams as text directly in markdown using a well-established syntax. This improves documentation by making diagrams version-controllable, easier to update, and eliminates the need for external diagram tools.

## Research Findings

### Alternatives Evaluated

| Tool | Rendering | Verdict |
|------|-----------|---------|
| **Mermaid.js** | Client-side | **Best choice** - Zero-config, most popular, GitHub/VS Code native support |
| **D2** | Client/server | Better aesthetics but harder to integrate, newer/less mature |
| **PlantUML** | Server-side (Java) | Most diagram types but requires Java server - overkill for our use |
| **Kroki** | Server-side | Aggregates 50+ tools but needs server/container |
| **Graphviz** | Server-side | Powerful but dated visuals, harder setup |

**Decision:** Mermaid.js is the clear choice for client-side dashboard rendering.

### Flexmark Integration Research

- **No official flexmark extension for Mermaid exists** (checked flexmark-java 0.64.8 docs)
- Standard approach used by other projects:
  1. Parse markdown normally (flexmark preserves code blocks with language info)
  2. Post-process HTML to convert `<pre><code class="language-mermaid">` to `<div class="mermaid">`
  3. Include Mermaid.js and initialize with `mermaid.initialize({startOnLoad:true})`
- Alternative: Use JavaScript to detect and transform mermaid blocks on page load

### Mermaid.js Version & CDN Research

- **Latest stable:** v10.9.4 (v10.x branch with security backports, production-ready)
- v11.x exists but is more experimental
- **Recommended CDN:** jsDelivr (fastest, most reliable)
- **Best practice:** Pin to exact version in production (e.g., `mermaid@10.9.4`)
- **Script approach:** Traditional `<script>` tag is simpler for our case (no build system)
- ESM modules available but unnecessary for this use case

## User Stories

### Story 1: Render Mermaid flowchart diagram

```gherkin
Feature: Mermaid diagram rendering
  As a documentation author
  I want to include Mermaid diagrams in markdown files
  So that I can create visual diagrams without external tools

Scenario: View flowchart diagram in artifact viewer
  Given I have a markdown file with a Mermaid flowchart:
    """
    ```mermaid
    graph TD
      A[Start] --> B[Process]
      B --> C[End]
    ```
    """
  When I view the markdown file in the artifact viewer
  Then I see a rendered flowchart diagram
  And the diagram shows boxes for Start, Process, and End
  And the diagram shows arrows connecting the boxes
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward because:
- Mermaid.js is a well-documented client-side library with CDN availability
- No changes needed to markdown parsing (code blocks already work)
- Only requires adding JavaScript library to HTML template
- No server-side rendering or complex integration needed

**Acceptance:**
- Mermaid code blocks render as visual diagrams (not plain text)
- Diagrams display correctly in the artifact viewer
- Non-Mermaid code blocks still render as code (no regression)

---

### Story 2: Handle invalid Mermaid syntax gracefully

```gherkin
Feature: Mermaid error handling
  As a documentation author
  I want to see clear errors when my Mermaid syntax is invalid
  So that I can fix diagram definitions easily

Scenario: Invalid Mermaid syntax shows error message
  Given I have a markdown file with invalid Mermaid syntax:
    """
    ```mermaid
    graph TD
      A[Start] -> INVALID SYNTAX HERE
    ```
    """
  When I view the markdown file in the artifact viewer
  Then I see an error message indicating the Mermaid syntax is invalid
  And I see the line or location of the syntax error
  And the error does not break the entire page
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because Mermaid.js has built-in error handling:
- Library provides error callbacks and visual error messages
- Errors are isolated per diagram (don't break page)
- Default error rendering is usually sufficient

**Acceptance:**
- Invalid Mermaid syntax displays an error message
- Error message indicates the problem location
- Page remains functional with other content visible
- Other diagrams on the same page still render

---

### Story 3: Support common Mermaid diagram types

```gherkin
Feature: Multiple Mermaid diagram types
  As a documentation author
  I want to use different Mermaid diagram types (flowchart, sequence, class, etc.)
  So that I can visualize different aspects of the system

Scenario: View sequence diagram
  Given I have a markdown file with a Mermaid sequence diagram:
    """
    ```mermaid
    sequenceDiagram
      User->>API: Request
      API->>Database: Query
      Database-->>API: Result
      API-->>User: Response
    ```
    """
  When I view the markdown file in the artifact viewer
  Then I see a rendered sequence diagram
  And the diagram shows participants User, API, and Database
  And the diagram shows message flow with arrows

Scenario: View class diagram
  Given I have a markdown file with a Mermaid class diagram
  When I view the markdown file in the artifact viewer
  Then I see a rendered class diagram with boxes and relationships
```

**Estimated Effort:** 1h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- All diagram types are supported by the same Mermaid.js library
- No additional configuration or libraries needed
- Implementation is the same regardless of diagram type

**Acceptance:**
- Flowchart diagrams render correctly
- Sequence diagrams render correctly
- Class diagrams render correctly
- Other diagram types (gantt, pie, etc.) render correctly

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Render Mermaid flowchart diagram

**Infrastructure Layer:**
- MarkdownRenderer.toHtml (existing - no changes needed, code blocks already work)
- Mermaid.js library (external JavaScript via CDN)

**Presentation Layer:**
- ArtifactView.render (modified to include Mermaid.js script)
- ArtifactView.styles (modified to include Mermaid diagram styling if needed)
- JavaScript initialization code (initialize Mermaid after page load)

**Application Layer:**
- ArtifactService.loadArtifact (existing - no changes needed)

---

### For Story 2: Handle invalid Mermaid syntax gracefully

**Presentation Layer:**
- Mermaid error handling configuration in ArtifactView
- Error display styling in ArtifactView.styles
- JavaScript error callback configuration

---

### For Story 3: Support common Mermaid diagram types

**Presentation Layer:**
- Same components as Story 1 (no additional changes needed)
- Mermaid.js supports all diagram types by default

---

## Technical Decisions (Research-Informed)

### RESOLVED: Mermaid.js version and CDN source

**Research findings:** v10.9.4 is the latest stable with security backports. jsDelivr is the recommended CDN.

**Recommendation:** Pin to v10.9.4 from jsDelivr CDN
```html
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.4/dist/mermaid.min.js"></script>
```

**Rationale:**
- Exact version pin ensures reproducibility
- v10.9.4 has critical security fixes (XSS vulnerabilities patched)
- jsDelivr is fast and reliable
- Traditional script tag is simpler than ESM for our no-build-system setup

**CLARIFY:** Is offline support needed? If yes, we should self-host instead of CDN.

---

### RESOLVED: Content Security Policy considerations

**Current state:** The dashboard is a local development tool running on localhost. No CSP is currently configured.

**Recommendation:** No CSP needed for initial implementation
- Local dashboard doesn't face external threats
- Can add SRI hash later if security requirements change

**CLARIFY:** Do you want SRI hashes for defense-in-depth, or keep it simple?

---

### RESOLVED: Mermaid theme and styling integration

**Recommendation:** Use Mermaid's `neutral` theme
```javascript
mermaid.initialize({ startOnLoad: true, theme: 'neutral' });
```

**Rationale:**
- `neutral` theme uses grayscale/minimal colors that work with any background
- Better visual consistency with artifact viewer's clean aesthetic
- Single line of configuration, no custom CSS needed
- Dark mode not needed (dashboard is light theme only)

---

### RESOLVED: Markdown code block processing

**Research findings:** No flexmark extension exists. Standard approach is client-side JavaScript transformation.

**Recommendation:** Option B - Post-process HTML in MarkdownRenderer

**Approach:**
1. Flexmark renders ` ```mermaid ` as `<pre><code class="language-mermaid">...</code></pre>`
2. Add post-processing in `MarkdownRenderer.toHtml()` to convert these to `<div class="mermaid">...</div>`
3. Mermaid.js auto-renders elements with class `mermaid` on page load

**Rationale:**
- Server-side transformation is more reliable than client-side JavaScript manipulation
- Keeps Mermaid initialization simple (`startOnLoad: true`)
- Testable at the unit level (can verify HTML output)
- Follows existing flexmark extension patterns

**Alternative considered:** Client-side JavaScript to transform code blocks
- Pros: No changes to MarkdownRenderer
- Cons: Harder to test, potential flash of unstyled content, more fragile

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Render Mermaid flowchart diagram): 3-4 hours
- Story 2 (Handle invalid Mermaid syntax gracefully): 1-2 hours
- Story 3 (Support common Mermaid diagram types): 1 hour

**Total Range:** 5-7 hours

**Confidence:** High

**Reasoning:**
- Well-understood technology with extensive documentation
- Minimal changes to existing codebase (mostly additive)
- Existing test patterns are clear and comprehensive
- Main uncertainty is integration approach (CLARIFY markers above)
- Once CLARIFY items resolved, implementation is straightforward

---

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Verify Mermaid code blocks are preserved in HTML
2. **Integration Tests**: Verify HTML page includes Mermaid.js correctly
3. **E2E Scenario Tests**: Manual verification in browser (or automated with headless browser)

**Story-Specific Testing Notes:**

**Story 1:**
- Unit: Test that MarkdownRenderer preserves ` ```mermaid ` code blocks without modification
- Unit: Test that ArtifactView.render includes `<script>` tag for Mermaid.js
- Unit: Test that rendered HTML contains expected structure for Mermaid diagrams
- Integration: Test complete flow from markdown file to HTML page
- E2E: Manual browser test viewing actual Mermaid diagram in artifact viewer

**Story 2:**
- Unit: Test that invalid Mermaid syntax is handled (if we add error handling config)
- E2E: Manual browser test with intentionally broken Mermaid syntax
- E2E: Verify error message is displayed and page doesn't crash

**Story 3:**
- Unit: Test markdown files with different diagram types (flowchart, sequence, class)
- E2E: Manual browser tests viewing each diagram type in artifact viewer
- E2E: Verify all diagram types render correctly

**Test Data Strategy:**
- Create test markdown files with various Mermaid diagram examples
- Include both valid and invalid Mermaid syntax in test cases
- Use simple diagrams for tests (avoid overly complex examples)
- Store test fixtures in `.iw/core/test/fixtures/` directory

**Regression Coverage:**
- Verify existing markdown rendering still works (tables, code blocks, headers, etc.)
- Run existing MarkdownRendererTest suite to ensure no regressions
- Test that non-Mermaid code blocks (scala, javascript, etc.) still render as code

---

## Deployment Considerations

### Database Changes
None - this is purely a presentation layer change.

### Configuration Changes
None required, though we may want to document Mermaid support in user documentation.

### Rollout Strategy
- Feature can be deployed immediately once implemented
- No feature flag needed (backward compatible - existing markdown unchanged)
- New Mermaid diagrams will render for all users
- Existing markdown without Mermaid diagrams unaffected

### Rollback Plan
If Mermaid.js causes issues in production:
1. Remove `<script>` tag from ArtifactView template
2. Mermaid code blocks will revert to displaying as regular code blocks
3. No data loss or corruption possible (markdown files unchanged)

---

## Dependencies

### Prerequisites
- None - existing markdown rendering infrastructure is sufficient
- Mermaid.js loaded from CDN (no local installation needed)

### Story Dependencies
- Story 2 depends on Story 1 being complete (need Mermaid.js loaded to test error handling)
- Story 3 has no additional dependencies (uses same integration as Story 1)
- Stories can be implemented sequentially in order

### External Blockers
- CDN availability (jsDelivr or chosen CDN must be accessible)
- None if self-hosting is chosen (see CLARIFY marker)

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Render Mermaid flowchart diagram** - Establishes core integration, highest value
2. **Story 2: Handle invalid Mermaid syntax gracefully** - Builds on Story 1, improves user experience
3. **Story 3: Support common Mermaid diagram types** - Validation/testing only, minimal effort

**Iteration Plan:**

- **Iteration 1** (Story 1): Core Mermaid integration, basic diagram rendering (3-4h)
- **Iteration 2** (Story 2): Error handling and robustness (1-2h)
- **Iteration 3** (Story 3): Validation of all diagram types (1h)

## Documentation Requirements

- [ ] Update README or documentation to mention Mermaid diagram support
- [ ] Add example markdown file showing Mermaid usage (optional but helpful)
- [ ] Update any user guides if they exist for the artifact viewer
- [ ] Document which Mermaid.js version is used (for future maintenance)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Review remaining CLARIFY items (offline support? SRI hashes?)
2. Run `/iterative-works:ag-create-tasks IW-67` to map stories to implementation phases
3. Run `/iterative-works:ag-implement IW-67` for iterative story-by-story implementation

---

## Analysis Summary

This is a **Simple** enhancement (5-7 hours total) that adds significant value to documentation capabilities. The implementation is primarily additive (adding Mermaid.js library to HTML template) with minimal risk to existing functionality.

**Research confirmed:**
- Mermaid.js is the right choice (client-side, most popular, zero-config)
- No flexmark extension exists - use HTML post-processing approach
- v10.9.4 from jsDelivr CDN is recommended

**Remaining decisions:**
1. **Offline support?** - If needed, self-host instead of CDN
2. **SRI hashes?** - For defense-in-depth or keep simple?

Once these minor decisions are confirmed, implementation is straightforward following established patterns in the codebase.
