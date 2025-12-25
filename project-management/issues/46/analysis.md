# Story-Driven Analysis: Dashboard: Display workflow review artifacts from state file

**Issue:** #46
**Created:** 2025-12-25
**Status:** Draft
**Classification:** Feature

## Problem Statement

When a workflow (like ag-implement) reaches a review checkpoint, humans need to review several artifacts (analysis documents, phase context, review packets, etc.). Currently, reviewers must manually navigate to files in the worktree's `project-management/issues/` directory to find these documents.

This creates friction in the review process:
- Reviewers don't know which artifacts are relevant
- Manual navigation is error-prone and time-consuming
- No clear indication that a review is needed
- No unified view of review materials

The dashboard already displays worktree cards with issue metadata and workflow progress. Adding review artifact display would enable reviewers to access all relevant materials directly from the dashboard, improving the agile workflow's review loop.

## User Stories

### Story 1: Dashboard displays review artifacts when state file exists

```gherkin
Feature: Display review artifacts in dashboard
  As a developer reviewing workflow output
  I want to see available review artifacts in the dashboard
  So that I can quickly access relevant review materials

Scenario: Worktree with review state file shows artifacts
  Given a worktree is registered for issue "IWLE-72"
  And the worktree has a review-state.yaml file
  And the state file lists 3 artifacts
  When I load the dashboard
  Then I see the worktree card for "IWLE-72"
  And I see a "Review Artifacts" section on the card
  And I see 3 artifact links listed
  And each artifact shows its label
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
This story requires parsing YAML files from the filesystem and extending the existing dashboard rendering. The WorkflowProgressService already demonstrates the pattern for reading files from worktree directories with injected I/O. The main complexity is defining the review-state.yaml schema and integrating it into the existing dashboard data flow.

**Key Technical Challenges:**
- Define YAML schema that workflows can write easily
- Parse YAML in Scala (upickle/ujson or dedicated YAML library)
- Extend ServerState to cache review state similar to progress cache
- Handle missing/malformed state files gracefully

**Acceptance:**
- Dashboard loads and parses review-state.yaml from each worktree
- Worktree cards display artifact list when state file exists
- Missing or invalid state files don't break dashboard rendering
- Artifact labels are shown clearly

---

### Story 2: Click artifact to view rendered markdown content

```gherkin
Feature: View artifact content
  As a developer reviewing workflow output
  I want to click an artifact link to see its content
  So that I can read review materials without leaving the dashboard

Scenario: Click artifact shows markdown content
  Given a worktree has a review-state.yaml with artifact "analysis.md"
  And the artifact path is "project-management/issues/IWLE-72/analysis.md"
  When I load the dashboard
  And I click the "analysis.md" artifact link
  Then I see the markdown content rendered as HTML
  And the content is displayed in a readable format

Scenario: View multiple artifacts sequentially
  Given a worktree has 3 artifacts in review state
  When I view the first artifact
  And I close the artifact view
  And I view the second artifact
  Then I see the second artifact's content
  And I can navigate between artifacts easily
```

**Estimated Effort:** 8-12h
**Complexity:** Complex

**Technical Feasibility:**
This requires adding new HTTP endpoints and client-side rendering. Two main approaches: (1) modal overlay with JavaScript, or (2) separate page per artifact. Modal is more complex but provides better UX. Markdown rendering can use existing markdown libraries or render server-side.

**Key Technical Challenges:**
- Choose rendering approach (modal vs separate page)
- Markdown to HTML conversion (client-side vs server-side)
- Handle large markdown files efficiently
- Navigation between multiple artifacts
- Security: validate artifact paths to prevent directory traversal

**Acceptance:**
- Clicking artifact opens content view (modal or page)
- Markdown is rendered as formatted HTML
- Users can close and view other artifacts
- Invalid paths are handled gracefully
- Large files (>100KB) render without freezing UI

---

### Story 3: Review state indicates phase and status

```gherkin
Feature: Display review status
  As a developer monitoring workflow progress
  I want to see review status and current phase
  So that I know what stage the workflow is in

Scenario: Review state shows phase number and message
  Given a review-state.yaml file contains:
    """
    status: awaiting_review
    phase: 8
    message: "Phase 8 complete - Ready for review"
    """
  When I load the dashboard
  Then I see "Phase 8" in the review section
  And I see status "awaiting_review"
  And I see the message "Phase 8 complete - Ready for review"

Scenario: Different status values display appropriately
  Given a review state with status "in_progress"
  When I load the dashboard
  Then I see a visual indicator for "in_progress" status
  And the status is distinguishable from "awaiting_review"
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
This extends Story 1 with minimal additional complexity. The review-state.yaml already needs parsing for artifacts; adding status/phase/message fields is straightforward. CSS styling for different statuses follows existing patterns in the dashboard.

**Key Technical Challenges:**
- Define finite set of status values
- Map status to visual indicators (colors, badges)
- Coordinate with workflow progress display (avoid redundancy)

**Acceptance:**
- Status, phase, and message are displayed when present
- Visual indicators differentiate statuses
- Missing fields degrade gracefully (optional fields)
- Status badges use consistent styling with existing dashboard

---

### Story 4: Review state cache prevents redundant file reads

```gherkin
Feature: Cache review state
  As a system administrator
  I want review state cached efficiently
  So that the dashboard loads quickly with many worktrees

Scenario: Review state cached with file mtime validation
  Given a worktree has review-state.yaml with mtime 1000
  And the review state is cached
  When the dashboard loads
  And the review-state.yaml mtime is still 1000
  Then the cached review state is used
  And the file is not re-read

Scenario: Cache invalidated when file changes
  Given cached review state with mtime 1000
  When review-state.yaml is modified (mtime 2000)
  And the dashboard loads
  Then the file is re-read
  And the cache is updated with new content
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
Follows the exact pattern established by CachedProgress and CachedIssue. The mtime-based invalidation strategy is proven. Main work is creating domain types and cache service.

**Key Technical Challenges:**
- Define ReviewState domain type and CachedReviewState
- Integrate cache into ServerState
- Handle cache serialization/deserialization
- Coordinate cache updates with file I/O

**Acceptance:**
- Review state cached in ServerState with mtime
- Cache validated on each dashboard load
- Modified files trigger re-read
- Cache persists across server restarts

---

### Story 5: Missing or invalid state files handled gracefully

```gherkin
Feature: Graceful degradation
  As a developer
  I want the dashboard to work even with invalid state files
  So that one broken file doesn't break the entire dashboard

Scenario: Missing review-state.yaml shows no review section
  Given a worktree has no review-state.yaml file
  When I load the dashboard
  Then I see the worktree card
  And I do not see a review artifacts section
  And other worktree data displays normally

Scenario: Malformed YAML shows error indicator
  Given a review-state.yaml file with invalid YAML syntax
  When I load the dashboard
  Then I see the worktree card
  And I see "Review state unavailable" message
  And the dashboard continues to function

Scenario: Artifact file missing shows clear error
  Given review-state.yaml lists artifact "missing.md"
  And the file does not exist
  When I click the artifact link
  Then I see "Artifact not found: missing.md" error
  And I can still view other valid artifacts
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Error handling follows functional patterns already in use (Either/Option types). The dashboard already handles missing issue data and progress gracefully. This extends the same approach to review state.

**Key Technical Challenges:**
- Distinguish between "no state file" vs "invalid state file"
- User-friendly error messages
- Partial success (some artifacts load, others fail)
- Log errors without breaking JSON serialization

**Acceptance:**
- Missing state files don't break dashboard
- Invalid YAML shows error, doesn't crash server
- Missing artifact files show clear error message
- Other artifacts remain accessible despite individual failures
- Errors logged for debugging

---

### Story 6: Artifact paths validated to prevent directory traversal

```gherkin
Feature: Secure artifact access
  As a system administrator
  I want artifact paths validated
  So that users cannot access arbitrary files on the system

Scenario: Relative paths normalized safely
  Given review-state.yaml lists artifact "../../../etc/passwd"
  When I request the artifact
  Then I receive a "Invalid artifact path" error
  And the file is not served

Scenario: Absolute paths rejected
  Given review-state.yaml lists artifact "/etc/passwd"
  When I request the artifact
  Then I receive a "Invalid artifact path" error

Scenario: Valid paths within worktree succeed
  Given review-state.yaml lists "project-management/issues/IWLE-72/analysis.md"
  And the path is within the worktree directory
  When I request the artifact
  Then the file content is returned
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Path validation is a well-understood security pattern. Use canonical path resolution to ensure paths stay within worktree boundaries. This is critical security validation that should be thoroughly tested.

**Key Technical Challenges:**
- Canonical path resolution (handle symlinks)
- Define allowed base paths (worktree root only)
- Clear error messages without leaking path information
- Handle edge cases (Windows paths, etc.)

**Acceptance:**
- Paths with ".." components are rejected
- Absolute paths are rejected
- Paths outside worktree are rejected
- Valid relative paths within worktree succeed
- Error messages don't leak filesystem structure

---

## Architectural Sketch

### Story 1: Dashboard displays review artifacts when state file exists

**Domain Layer:**
- `ReviewState` case class (status, phase, message, artifacts list)
- `ReviewArtifact` case class (label, path)
- `CachedReviewState` case class (state, mtime, fetchedAt)

**Application Layer:**
- `ReviewStateService.fetchReviewState(issueId, worktreePath, cache, readFile, getMtime)`
- `ReviewStateService.parseReviewStateFile(yamlContent)` - parse YAML to ReviewState

**Infrastructure Layer:**
- YAML parsing (choose library: circe-yaml, snakeyaml-engine, or upickle)
- File I/O via injected functions (follows existing pattern)

**Presentation Layer:**
- Extend `WorktreeListView.renderWorktreeCard` to show review section
- CSS styles for review artifacts section

**Data Flow:**
- DashboardService fetches review state per worktree (like progress)
- ReviewStateService reads review-state.yaml from worktree
- ReviewState passed to WorktreeListView for rendering

---

### Story 2: Click artifact to view rendered markdown content

**Domain Layer:**
- `ArtifactContent` case class (markdown text, metadata)
- Path validation functions (pure)

**Application Layer:**
- `ArtifactService.loadArtifact(worktreePath, artifactPath, readFile)`
- `ArtifactService.validateArtifactPath(worktreePath, artifactPath)` - security check
- `MarkdownRenderer.toHtml(markdownText)` - convert markdown to HTML

**Infrastructure Layer:**
- HTTP endpoint: `GET /api/v1/worktrees/:issueId/artifacts?path=...`
- Markdown parsing library (commonmark-java or flexmark)
- File reading with path validation

**Presentation Layer:**
- JavaScript modal component for artifact display
- OR: Server-rendered artifact page at `/worktrees/:issueId/artifacts/:filename`
- Styling for rendered markdown content

**Data Flow:**
1. User clicks artifact link in dashboard
2. Request to `/api/v1/worktrees/:issueId/artifacts?path=analysis.md`
3. Server validates path, reads file, renders markdown
4. Returns HTML or JSON with content
5. Client displays in modal or navigates to page

---

### Story 3: Review state indicates phase and status

**Domain Layer:**
- Extend `ReviewState` with status/phase/message fields (already planned)
- Status enum or validation function

**Application Layer:**
- No new services - extend existing ReviewStateService

**Infrastructure Layer:**
- No changes needed

**Presentation Layer:**
- Extend review section template with status badge
- CSS classes for status visual indicators (`.review-status-awaiting`, `.review-status-in-progress`)

---

### Story 4: Review state cache prevents redundant file reads

**Domain Layer:**
- `CachedReviewState` (already planned in Story 1)
- Cache validation logic similar to CachedProgress

**Application Layer:**
- Extend `ReviewStateService` with cache check logic
- Add cache to `ServerState.reviewStateCache: Map[String, CachedReviewState]`

**Infrastructure Layer:**
- Extend `StateRepository` to serialize reviewStateCache
- Extend `ServerStateService` to manage review state cache

**Presentation Layer:**
- No changes (cache is transparent to UI)

---

### Story 5: Missing or invalid state files handled gracefully

**Domain Layer:**
- Error types for review state failures (no domain model changes)

**Application Layer:**
- ReviewStateService returns `Either[String, ReviewState]`
- Error messages for different failure modes

**Infrastructure Layer:**
- YAML parser error handling
- File read error handling

**Presentation Layer:**
- Conditional rendering: show review section only if state exists
- Error message display for invalid states

---

### Story 6: Artifact paths validated to prevent directory traversal

**Domain Layer:**
- `PathValidator.validateArtifactPath(basePath, relativePath): Either[String, Path]`

**Application Layer:**
- Use PathValidator in ArtifactService before file access

**Infrastructure Layer:**
- Canonical path resolution (java.nio.file.Path)

**Presentation Layer:**
- Error display for invalid paths

---

## Technical Risks & Uncertainties

### CLARIFY: YAML parsing library choice

The existing codebase uses uJson for JSON parsing. For YAML support, we need to choose a library.

**Questions to answer:**
1. Should we use a Scala-native YAML library or Java interop?
2. What's the parsing performance requirement (files are small, <10KB typically)?
3. Do we need YAML generation, or just parsing?

**Options:**

**Option A: circe-yaml** (Scala, popular)
- Pros: Pure Scala, integrates with circe ecosystem, good error messages
- Cons: Adds circe dependency (heavy if not already using), more dependencies
- Effort: ~1h to integrate, straightforward API

**Option B: snakeyaml-engine** (Java, lightweight)
- Pros: Lightweight, widely used, no Scala dependencies
- Cons: Java API (more verbose), less type-safe
- Effort: ~1-2h to integrate, write Scala wrapper

**Option C: Manual parsing with upickle/ujson** (if YAML subset is simple)
- Pros: No new dependency, consistent with existing code
- Cons: YAML is complex, manual parsing error-prone
- Effort: ~4-6h to implement robust parser, not recommended

**Impact:** Affects Story 1 and potentially build configuration. Recommend **Option B** for minimal dependencies, or **Option A** if type safety is priority.

---

### CLARIFY: Artifact rendering approach (modal vs separate page)

When users click an artifact, how should content be displayed?

**Questions to answer:**
1. Should users stay on the dashboard or navigate away?
2. Do we need JavaScript for modal, or prefer server-rendered simplicity?
3. Should users be able to compare multiple artifacts side-by-side?

**Options:**

**Option A: JavaScript modal overlay**
- Pros: No page navigation, smooth UX, can view multiple artifacts without losing context
- Cons: Requires JavaScript, more complex frontend, harder to test
- Effort: 6-8h (modal component, event handling, styling)

**Option B: Separate page per artifact**
- Pros: Simple server-side rendering, works without JavaScript, easier to test
- Cons: Loses dashboard context, requires navigation, less smooth UX
- Effort: 4-5h (new route, template, back navigation)

**Option C: Inline expansion on dashboard card**
- Pros: No modal or navigation, progressive disclosure
- Cons: Large artifacts make cards unwieldy, scrolling issues
- Effort: 3-4h (expandable section, scroll handling)

**Impact:** Affects Story 2 implementation approach and user experience. Recommend **Option B** for simplicity in MVP, can upgrade to Option A later if needed.

---

### CLARIFY: Markdown rendering location (server vs client)

Should markdown conversion happen server-side or client-side?

**Questions to answer:**
1. Do we want to support rich markdown (tables, code blocks, etc.)?
2. Should rendered content be cached?
3. Is XSS a concern (do we trust markdown sources)?

**Options:**

**Option A: Server-side rendering with commonmark-java**
- Pros: Consistent styling, no client dependencies, easier XSS prevention
- Cons: Server does more work, adds JVM library
- Effort: 2-3h (integrate library, sanitization)

**Option B: Client-side rendering with marked.js or similar**
- Pros: Offloads work to client, reduces server load
- Cons: Requires JavaScript, client must download library, XSS risk
- Effort: 3-4h (integrate library, sanitization, testing)

**Option C: Pre-render on workflow side, serve static HTML**
- Pros: Zero rendering cost, very fast
- Cons: Workflows must render, less flexible, storage overhead
- Effort: Depends on workflow changes

**Impact:** Affects Story 2 performance and dependencies. Recommend **Option A** for security and simplicity, markdown files are small (<100KB typically).

---

### CLARIFY: Review state file location and naming

Where should workflows write the review-state.yaml file?

**Questions to answer:**
1. Should it live in project-management/issues/{issueId}/ with other artifacts?
2. Should it be at worktree root for easy discovery?
3. Should name be configurable or fixed?

**Options:**

**Option A: project-management/issues/{issueId}/review-state.yaml**
- Pros: Co-located with artifacts, consistent with workflow file organization
- Cons: Requires constructing path from issueId
- Discovery path: `{worktreePath}/project-management/issues/{issueId}/review-state.yaml`

**Option B: .iw/review-state.yaml (worktree root)**
- Pros: Easy to find, consistent location across worktrees
- Cons: Separated from artifacts, multiple issues in one worktree could conflict
- Discovery path: `{worktreePath}/.iw/review-state.yaml`

**Option C: Both: symlink or duplicate**
- Pros: Accessible from both locations
- Cons: Complexity, sync issues
- Not recommended

**Impact:** Affects file discovery logic in Story 1 and workflow writing logic. Recommend **Option A** for consistency with existing workflow patterns.

---

### CLARIFY: Artifact path resolution strategy

How should artifact paths in review-state.yaml be specified?

**Questions to answer:**
1. Relative to review-state.yaml location, or absolute from worktree root?
2. Should we support glob patterns (e.g., "phase-*-context.md")?
3. What if artifact doesn't exist when state file is written?

**Options:**

**Option A: Relative to worktree root**
- Pros: Clear, explicit, easy to validate
- Cons: Workflows must know full path
- Example: `project-management/issues/IWLE-72/analysis.md`

**Option B: Relative to review-state.yaml location**
- Pros: Shorter paths, portable if state file moves
- Cons: More complex resolution, ambiguity
- Example: `./analysis.md` or `analysis.md`

**Option C: Absolute filesystem paths**
- Pros: Unambiguous
- Cons: Not portable, security risk, wrong abstraction
- Not recommended

**Impact:** Affects path validation in Story 6 and workflow writing convenience. Recommend **Option A** for security and clarity.

---

### CLARIFY: Review state schema evolution

How should we handle future changes to review-state.yaml schema?

**Questions to answer:**
1. Should we version the schema explicitly?
2. Should new fields be strictly optional for backward compatibility?
3. How do we handle unknown fields (ignore or error)?

**Options:**

**Option A: Strict schema with version field**
- Pros: Clear compatibility story, explicit migrations
- Cons: More ceremony, workflows must specify version
- Example: `version: 1` in YAML

**Option B: Lenient parsing, optional fields, ignore unknowns**
- Pros: Backward compatible by default, easy evolution
- Cons: Harder to detect errors, undefined behavior on schema drift
- Example: All fields optional except `artifacts`

**Option C: No versioning, break when needed**
- Pros: Simplest, YAGNI
- Cons: Breaks workflows on upgrades
- Not recommended for multi-team environment

**Impact:** Affects Story 1 parsing logic and long-term maintainability. Recommend **Option B** for MVP (lenient), can add versioning later if schema changes frequently.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Display artifacts from state file): 6-8 hours
- Story 2 (View artifact content): 8-12 hours
- Story 3 (Review status and phase): 3-4 hours
- Story 4 (Review state caching): 4-6 hours
- Story 5 (Graceful error handling): 3-4 hours
- Story 6 (Path validation security): 2-3 hours

**Total Range:** 26-37 hours

**Confidence:** Medium

**Reasoning:**
- Stories 1, 4, 5, 6 are moderate confidence - patterns exist in codebase (progress caching, error handling)
- Story 2 is lower confidence - depends on CLARIFY decisions (modal vs page, markdown rendering)
- Story 3 is high confidence - straightforward extension of Story 1
- Main unknowns: YAML library choice, rendering approach, edge cases in markdown parsing
- Risk buffer included for integration testing across stories

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure domain logic, value objects, business rules
2. **Integration Tests**: File I/O, YAML parsing, HTTP endpoints
3. **E2E Tests**: Automated browser tests or BATS tests for dashboard rendering

---

**Story 1: Display artifacts from state file**

**Unit:**
- ReviewState parsing from YAML content
- Cache validity logic (mtime comparison)
- Error handling for malformed YAML

**Integration:**
- Read review-state.yaml from filesystem
- Parse and return ReviewState
- Cache invalidation on mtime change

**E2E:**
- Dashboard shows review section when state file exists
- Artifacts list displays correctly
- No review section when file missing

**Test Data:**
```yaml
# test-fixtures/review-state-simple.yaml
status: awaiting_review
phase: 8
message: "Ready for review"
artifacts:
  - label: "Analysis"
    path: "project-management/issues/IWLE-72/analysis.md"
  - label: "Phase Context"
    path: "project-management/issues/IWLE-72/phase-08-context.md"
```

---

**Story 2: View artifact content**

**Unit:**
- Markdown to HTML conversion
- Path validation logic (see Story 6)

**Integration:**
- HTTP endpoint returns artifact content
- File read with error handling
- Markdown rendering with various markdown features

**E2E:**
- Click artifact link opens content view
- Markdown displays as formatted HTML
- Multiple artifacts can be viewed sequentially

**Test Data:**
- Sample markdown files with headers, lists, code blocks
- Edge cases: empty file, very large file, malformed markdown

---

**Story 3: Review status and phase**

**Unit:**
- Status value validation
- CSS class mapping for status

**Integration:**
- Parse status/phase/message from YAML

**E2E:**
- Status badge displays correctly
- Different statuses have distinct visual indicators
- Missing fields don't break rendering

**Test Data:**
```yaml
# Each status value tested
status: awaiting_review
status: in_progress
status: completed
```

---

**Story 4: Review state caching**

**Unit:**
- CachedReviewState validation logic
- Cache hit/miss determination

**Integration:**
- Cache persists in ServerState
- Cache invalidated on file modification
- Cache survives server restart (StateRepository)

**E2E:**
- Dashboard loads use cached state when valid
- File changes trigger re-read

**Test Data:**
- Sequence: write state -> cache -> modify file -> verify re-read

---

**Story 5: Graceful error handling**

**Unit:**
- Error message generation
- Either types for failure cases

**Integration:**
- Missing file returns Left with message
- Invalid YAML returns Left with parse error
- Partial success (some artifacts load)

**E2E:**
- Missing state file: no review section, no crash
- Invalid YAML: error message shown
- Missing artifact: error on click, other artifacts work

**Test Data:**
- Invalid YAML syntax
- Non-existent files
- Empty files

---

**Story 6: Path validation**

**Unit:**
- Path normalization
- Directory traversal detection
- Absolute path rejection

**Integration:**
- Valid paths succeed
- Invalid paths return error
- Canonical path resolution

**E2E:**
- Attempt to access /etc/passwd: rejected
- Attempt to access ../../../sensitive: rejected
- Access valid artifact: succeeds

**Test Data:**
```yaml
# Malicious path attempts
artifacts:
  - label: "Evil"
    path: "../../../etc/passwd"
  - label: "Sneaky"
    path: "/etc/passwd"
  - label: "Tricky"
    path: "project-management/../../etc/passwd"
```

---

**Test Data Strategy:**
- Create fixtures directory: `.iw/core/test/fixtures/review-states/`
- Sample YAML files for each scenario
- Sample markdown files with various content
- Use temporary worktrees for E2E tests

**Regression Coverage:**
- Ensure existing dashboard tests still pass
- Verify WorkflowProgress display unchanged
- Confirm issue/PR caching unaffected
- Test dashboard with no review states (backward compatibility)

---

## Deployment Considerations

### Database Changes
No database - all state in filesystem YAML files.

**Story 1 schema:**
```yaml
# project-management/issues/{issueId}/review-state.yaml
status: awaiting_review | in_progress | completed
phase: 8
message: "Phase 8 complete - Ready for review"
artifacts:
  - label: "Analysis Document"
    path: "project-management/issues/IWLE-72/analysis.md"
  - label: "Phase 8 Context"
    path: "project-management/issues/IWLE-72/phase-08-context.md"
  - label: "Review Packet"
    path: "project-management/issues/IWLE-72/review-packet-phase-08.md"
```

### Configuration Changes
No environment variables needed.

Possible future: `.iw/config.conf` option to disable review artifact feature.

### Rollout Strategy
- Deploy per story (if possible)
- Story 1 can deploy independently: reads state, shows artifacts (no click action yet)
- Story 2 requires Story 1 to be deployed first
- Stories 3-6 can deploy with Story 1 as they extend it

**Feature Flag Option:**
- Add `reviewArtifactsEnabled = false` to ServerConfig
- Enable only when ready for production

### Rollback Plan
- If review state breaks dashboard: feature flag or remove review section from template
- Cache corruption: delete ServerState file, cache rebuilds on next load
- Bad artifacts displayed: workflows can update review-state.yaml, dashboard re-reads on mtime change

---

## Dependencies

### Prerequisites
- YAML parsing library (resolved via CLARIFY)
- Markdown rendering library (resolved via CLARIFY)
- Both likely available as JVM libraries via Maven Central

### Story Dependencies
**Sequential:**
- Story 2 depends on Story 1 (needs artifact list to display content)
- Story 4 depends on Story 1 (needs ReviewState type)
- Story 5 extends Story 1 (error handling)
- Story 6 required before Story 2 production (security)

**Can Parallelize:**
- Story 3 can be built with Story 1 (just additional fields)
- Story 5 and Story 6 could be developed in parallel if both start after Story 1

### External Blockers
None - all work contained in iw-cli codebase.

Coordination needed: Workflows (ag-implement) must write review-state.yaml files for feature to be useful, but dashboard can be built first.

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Display artifacts from state file** - Establishes foundation, defines schema, proves YAML parsing works
2. **Story 6: Path validation security** - Critical security before allowing file access
3. **Story 2: View artifact content** - Delivers core user value, depends on 1 and 6
4. **Story 3: Review status and phase** - Quick enhancement, builds on Story 1
5. **Story 4: Review state caching** - Performance optimization, low risk
6. **Story 5: Graceful error handling** - Polish, ensures robustness

**Iteration Plan:**

**Iteration 1 (Stories 1, 6):** Foundation and Security (8-11 hours)
- Core parsing and display
- Path validation prevents security issues
- Deliverable: Dashboard shows artifact list, no click action yet

**Iteration 2 (Stories 2, 3):** Core User Value (11-16 hours)
- Artifact viewing works end-to-end
- Status/phase information enriches context
- Deliverable: Full review workflow supported

**Iteration 3 (Stories 4, 5):** Performance and Polish (7-10 hours)
- Caching improves dashboard load time
- Error handling ensures reliability
- Deliverable: Production-ready feature

---

## Documentation Requirements

- [ ] YAML schema documented (review-state.yaml format)
- [ ] API documentation for artifact endpoint (Story 2)
- [ ] Workflow integration guide (how to write review-state.yaml)
- [ ] Security considerations documented (path validation)
- [ ] Dashboard user guide updated (how to use review artifacts)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers:
   - YAML library choice (recommend snakeyaml-engine)
   - Rendering approach (recommend separate page for MVP)
   - Markdown rendering location (recommend server-side)
   - State file location (recommend project-management/issues/{issueId}/)
   - Path resolution strategy (recommend worktree-root-relative)
   - Schema evolution (recommend lenient parsing)

2. After CLARIFY resolution, begin implementation with Story 1 + Story 6 (foundation + security)

3. Coordinate with workflow developers on review-state.yaml writing (can happen in parallel)

---

**Key Design Decisions Summary:**

- **Vertical slicing:** Each story delivers end-user value (except Story 6 which is security prerequisite)
- **Functional core:** ReviewStateService uses I/O injection like WorkflowProgressService
- **Caching pattern:** Follows existing CachedProgress/CachedIssue patterns
- **Security first:** Story 6 blocks Story 2 to prevent directory traversal
- **Graceful degradation:** Missing/invalid states don't break dashboard (Story 5)
- **Iterative delivery:** Can ship after Iteration 1 (read-only), Iteration 2 (full feature), Iteration 3 (optimized)
