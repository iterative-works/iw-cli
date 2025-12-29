# Story-Driven Analysis: Support Mermaid diagrams in Markdown renderer

**Issue:** IW-67
**Created:** 2025-12-29
**Status:** Draft
**Classification:** Simple

## Problem Statement

Users need to create visual diagrams (flowcharts, sequence diagrams, class diagrams, etc.) in markdown documentation. Currently, the markdown renderer only supports text, code blocks, and tables, requiring users to create diagrams externally and embed them as images.

Adding Mermaid.js support enables users to define diagrams as text directly in markdown using a well-established syntax. This improves documentation by making diagrams version-controllable, easier to update, and eliminates the need for external diagram tools.

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

## Technical Risks & Uncertainties

### CLARIFY: Mermaid.js version and CDN source

**Questions to answer:**
1. Which Mermaid.js version should we use? (Latest stable vs specific version)
2. Which CDN should we use? (jsDelivr, unpkg, cdnjs, or self-hosted?)
3. Should we pin to a specific version or use a version range (e.g., `@10` vs `@10.6.1`)?

**Options:**
- **Option A**: Use latest version from jsDelivr CDN with major version pin (`mermaid@11/dist/mermaid.min.js`)
  - Pros: Automatic minor/patch updates, widely used CDN
  - Cons: Potential breaking changes in minor versions (rare but possible)

- **Option B**: Pin to specific version from jsDelivr CDN (`mermaid@11.4.1/dist/mermaid.min.js`)
  - Pros: Fully reproducible, no unexpected changes
  - Cons: Manual updates needed for bug fixes/features

- **Option C**: Self-host Mermaid.js in the project
  - Pros: No external dependencies, works offline
  - Cons: Adds to repository size, manual updates needed

**Impact:** Affects reliability, maintenance burden, and offline functionality. Story 1 implementation depends on this decision.

---

### CLARIFY: Content Security Policy considerations

**Questions to answer:**
1. Does the project have or need a Content Security Policy (CSP)?
2. If yes, do we need to whitelist the CDN for script sources?
3. Should we use Subresource Integrity (SRI) hashes for CDN scripts?

**Options:**
- **Option A**: No CSP, include script directly
  - Pros: Simple, works immediately
  - Cons: Potential security concern if CSP is added later

- **Option B**: Add SRI hash for CDN script
  - Pros: Verifies script integrity, security best practice
  - Cons: Hash must be updated when Mermaid version changes

- **Option C**: Self-host to avoid CSP issues
  - Pros: Full control, no external dependencies
  - Cons: Maintenance overhead

**Impact:** Affects security posture and Story 1 implementation approach.

---

### CLARIFY: Mermaid theme and styling integration

**Questions to answer:**
1. Should Mermaid diagrams use default theme or custom theme?
2. Should diagram styling match the existing artifact viewer styles?
3. Do we need to support dark mode for diagrams?

**Options:**
- **Option A**: Use Mermaid default theme
  - Pros: Zero configuration, works immediately
  - Cons: May not match artifact viewer aesthetics

- **Option B**: Configure Mermaid to use neutral/minimal theme
  - Pros: Better integration with existing styles
  - Cons: Requires additional configuration in JavaScript

- **Option C**: Create custom Mermaid theme CSS
  - Pros: Perfect aesthetic match with artifact viewer
  - Cons: Significant additional effort, maintenance burden

**Impact:** Affects visual consistency and potentially adds to Story 1 complexity.

---

### CLARIFY: Markdown code block processing

**Questions to answer:**
1. Should we transform ` ```mermaid ` blocks before passing to Mermaid.js?
2. Should we use `<pre class="mermaid">` or `<div class="mermaid">`?
3. Does flexmark need configuration changes to preserve mermaid code blocks?

**Options:**
- **Option A**: Keep markdown code blocks as-is, let Mermaid.js find and render them
  - Pros: Simple, minimal changes
  - Cons: May not work if flexmark wraps code blocks in complex HTML

- **Option B**: Post-process HTML to transform mermaid code blocks to `<div class="mermaid">`
  - Pros: Guaranteed to work, explicit control
  - Cons: Additional HTML processing step, slightly more complex

- **Option C**: Configure flexmark to handle mermaid blocks specially
  - Pros: Clean separation of concerns
  - Cons: May require flexmark extension or custom processor

**Impact:** This is critical for Story 1 - we need to know HOW the HTML will be structured before implementing.

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
1. Resolve CLARIFY markers with Michal (especially code block processing approach and CDN choice)
2. Run `/iterative-works:ag-create-tasks IW-67` to map stories to implementation phases
3. Run `/iterative-works:ag-implement IW-67` for iterative story-by-story implementation

---

## Analysis Summary

This is a **Simple** enhancement (5-7 hours total) that adds significant value to documentation capabilities. The implementation is primarily additive (adding Mermaid.js library to HTML template) with minimal risk to existing functionality. The main technical decisions (CLARIFY markers) are around:

1. **Integration approach**: How to process mermaid code blocks (critical for implementation)
2. **CDN vs self-hosting**: Affects reliability and maintenance
3. **Theming**: Affects visual integration quality

Once these decisions are made, implementation is straightforward following established patterns in the codebase.
