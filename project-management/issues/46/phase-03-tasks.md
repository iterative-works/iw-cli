# Phase 3 Tasks: View Artifact Content

**Issue:** #46  
**Phase:** 3 of 6  
**Story:** Click artifact to view rendered markdown content  
**Estimated Effort:** 8-12 hours

---

## Setup Tasks

- [ ] Add flexmark-all dependency to `.iw/core/project.scala`
- [ ] Verify scala-cli compiles with new dependency: `cd .iw && scala-cli compile core`

---

## Component 1: MarkdownRenderer (Infrastructure Layer)

### Tests First

- [ ] Create `.iw/core/test/MarkdownRendererTest.scala` with test suite structure
- [ ] Write test: renders basic markdown (headers h1-h4, paragraphs)
- [ ] Write test: renders lists (ordered, unordered, nested)
- [ ] Write test: renders inline code and code blocks with language tags
- [ ] Write test: renders tables (GFM style)
- [ ] Write test: renders links and autolinks
- [ ] Write test: renders blockquotes
- [ ] Write test: handles empty input (returns empty string)
- [ ] Write test: handles special characters / HTML escaping
- [ ] Run tests to confirm all fail: `cd .iw && scala-cli test core`

### Implementation

- [ ] Create `.iw/core/infrastructure/MarkdownRenderer.scala` package structure
- [ ] Implement `toHtml(markdown: String): String` with flexmark configuration
- [ ] Configure flexmark extensions (tables, strikethrough, autolink, anchorlink)
- [ ] Run tests to verify all pass: `cd .iw && scala-cli test core`

---

## Component 2: ArtifactService (Application Layer)

### Tests First

- [ ] Create `.iw/core/test/ArtifactServiceTest.scala` with test suite structure
- [ ] Write test: loadArtifact succeeds with valid issueId and path
- [ ] Write test: loadArtifact returns Left when worktree not found
- [ ] Write test: loadArtifact returns Left when PathValidator rejects path
- [ ] Write test: loadArtifact returns Left when file read fails
- [ ] Write test: loadArtifact integrates with MarkdownRenderer
- [ ] Write test: extractLabel returns filename from various Path inputs
- [ ] Write test: extractLabel handles paths with multiple segments
- [ ] Run tests to confirm all fail: `cd .iw && scala-cli test core`

### Implementation

- [ ] Create `.iw/core/ArtifactService.scala` in application package
- [ ] Implement `loadArtifact(issueId, artifactPath, state, readFile)` signature
- [ ] Add worktree resolution from ServerState
- [ ] Integrate PathValidator.validateArtifactPath (from Phase 2)
- [ ] Add file reading via injected readFile function
- [ ] Add markdown rendering via MarkdownRenderer.toHtml
- [ ] Implement `extractLabel(path: Path): String` helper
- [ ] Run tests to verify all pass: `cd .iw && scala-cli test core`

---

## Component 3: ArtifactView (Presentation Layer)

### Tests First

- [ ] Create `.iw/core/test/ArtifactViewTest.scala` with test suite structure
- [ ] Write test: render() produces valid HTML5 document structure
- [ ] Write test: render() includes artifact label in title and h1
- [ ] Write test: render() includes issueId in back link and subtitle
- [ ] Write test: render() back link points to "/" (dashboard)
- [ ] Write test: render() includes rendered HTML in content div
- [ ] Write test: renderError() produces error page with message
- [ ] Write test: renderError() includes back link to dashboard
- [ ] Run tests to confirm all fail: `cd .iw && scala-cli test core`

### Implementation

- [ ] Create `.iw/core/presentation/views/ArtifactView.scala`
- [ ] Implement `render(artifactLabel, renderedHtml, issueId): String`
- [ ] Add page structure (header with back link, title, content div)
- [ ] Add CSS styles for container, header, markdown content
- [ ] Add markdown-specific CSS (code blocks, tables, blockquotes)
- [ ] Implement `renderError(issueId, errorMessage): String`
- [ ] Add error page structure with back link
- [ ] Run tests to verify all pass: `cd .iw && scala-cli test core`

---

## Component 4: CaskServer Route (Infrastructure Layer)

### Tests First

- [ ] Create `.iw/core/test/ArtifactEndpointTest.scala` with test suite structure
- [ ] Create test fixture: temporary worktree with review-state.json and markdown file
- [ ] Write test: GET /worktrees/:issueId/artifacts?path=... returns 200 with HTML
- [ ] Write test: missing path query parameter returns 400
- [ ] Write test: invalid artifact path (traversal) returns 404
- [ ] Write test: worktree not found returns 404
- [ ] Write test: file read error returns 404
- [ ] Write test: markdown rendering works with real file content
- [ ] Write test: response has correct Content-Type header (text/html; charset=UTF-8)
- [ ] Run tests to confirm all fail: `cd .iw && scala-cli test core`

### Implementation

- [ ] Open `.iw/core/CaskServer.scala` for editing
- [ ] Add `@cask.get("/worktrees/:issueId/artifacts")` route signature
- [ ] Implement query param extraction for "path" parameter
- [ ] Add 400 response for missing path parameter
- [ ] Load ServerState using ServerStateService.load
- [ ] Create file I/O wrapper function (scala.io.Source pattern)
- [ ] Call ArtifactService.loadArtifact with dependencies
- [ ] Handle success: render ArtifactView.render and return 200
- [ ] Handle error: log to stderr, render ArtifactView.renderError, return 404
- [ ] Run tests to verify all pass: `cd .iw && scala-cli test core`

---

## Component 5: Update WorktreeListView (Presentation Layer)

### Tests First

- [ ] Open `.iw/core/test/WorktreeListViewTest.scala` for editing
- [ ] Write test: artifact list items contain anchor tags with href
- [ ] Write test: artifact href points to `/worktrees/{issueId}/artifacts?path={path}`
- [ ] Write test: artifact link text is artifact label
- [ ] Write test: multiple artifacts generate multiple links with correct paths
- [ ] Run tests to confirm they fail: `cd .iw && scala-cli test core`

### Implementation

- [ ] Open `.iw/core/WorktreeListView.scala` for editing
- [ ] Find artifact rendering code (around line 128-139)
- [ ] Change `li(artifact.label)` to `li(a(href := ..., artifact.label))`
- [ ] Construct href: `/worktrees/${worktree.issueId}/artifacts?path=${artifact.path}`
- [ ] Run tests to verify all pass: `cd .iw && scala-cli test core`

---

## Component 6: Add CSS Styles for Artifact Links

### Implementation

- [ ] Open `.iw/core/DashboardService.scala` for editing
- [ ] Find styles section in renderDashboard method
- [ ] Add `.review-artifacts` styles (margin, padding, background, border-radius)
- [ ] Add `.review-artifacts h4` styles (margin, font-size, font-weight, color)
- [ ] Add `.artifact-list` styles (list-style: none, padding, margin)
- [ ] Add `.artifact-list li` styles (margin)
- [ ] Add `.artifact-list a` styles (color, text-decoration, font-size)
- [ ] Add `.artifact-list a:hover` styles (text-decoration: underline)

---

## Integration Testing

### E2E Test Setup

- [ ] Create test worktree structure in /tmp with review-state.json
- [ ] Create sample markdown file with headers, lists, code blocks, tables
- [ ] Register test worktree in ServerState
- [ ] Run server: `./iw server` in background

### Manual E2E Testing

- [ ] Open dashboard: http://localhost:9876
- [ ] Verify artifact link appears in review section
- [ ] Click artifact link → verify navigation to artifact page
- [ ] Verify markdown renders with proper formatting
- [ ] Verify "Back to Dashboard" link → returns to dashboard
- [ ] Test invalid URL (/worktrees/TEST/artifacts?path=../../../etc/passwd) → verify 404 error
- [ ] Test missing path param (/worktrees/TEST/artifacts) → verify 400 error
- [ ] Stop server

---

## Error Handling Polish

- [ ] Test with large markdown file (>100KB) → verify renders without hanging
- [ ] Test with artifact path containing spaces → verify URL encoding works
- [ ] Test with artifact path containing Unicode characters → verify handling
- [ ] Test with broken symlink in artifact path → verify error handling
- [ ] Verify all error messages are user-friendly (no stack traces)
- [ ] Verify error logs contain detailed info for debugging

---

## Code Review and Refinement

- [ ] Run all tests: `cd .iw && scala-cli test core`
- [ ] Check for code duplication across components
- [ ] Verify FCIS pattern: I/O only in CaskServer route
- [ ] Review error messages for clarity and security
- [ ] Verify PathValidator called before all file reads
- [ ] Verify no filesystem paths leaked in user-facing errors
- [ ] Check CSS consistency with existing dashboard styles
- [ ] Verify import organization follows project conventions

---

## Acceptance Criteria Verification

- [ ] Artifact links are clickable in dashboard ✓
- [ ] Clicking link navigates to `/worktrees/:issueId/artifacts?path=...` ✓
- [ ] Markdown is rendered with proper formatting (headers, lists, code blocks, tables) ✓
- [ ] Page includes "Back to Dashboard" link ✓
- [ ] Page shows artifact filename and issue ID in header ✓
- [ ] Can view first artifact, return to dashboard, view second artifact ✓
- [ ] Each artifact shows its own content (no caching issues) ✓
- [ ] Back button works from any artifact view ✓
- [ ] Missing file shows "Artifact not found" error page ✓
- [ ] Invalid path (traversal) shows "Artifact not found" error page ✓
- [ ] Worktree not found shows clear error ✓
- [ ] Missing path param shows 400 error ✓
- [ ] Errors don't leak filesystem structure ✓

---

## Definition of Done

**Code complete:**
- [ ] All 7 new files created with tests
- [ ] All 3 modified files updated (CaskServer, WorktreeListView, DashboardService)
- [ ] No compilation errors
- [ ] No test failures
- [ ] All tests pass: `cd .iw && scala-cli test core`

**Functionality complete:**
- [ ] Can click artifact link in dashboard
- [ ] Can view rendered markdown
- [ ] Can return to dashboard
- [ ] Invalid paths show error
- [ ] Large files (>100KB) render correctly

**Quality gates:**
- [ ] Unit test coverage verified for MarkdownRenderer, ArtifactService, ArtifactView
- [ ] Integration tests cover happy path + 3 error cases
- [ ] Manual E2E testing completed successfully
- [ ] Security review: PathValidator used correctly in ArtifactService
- [ ] No code duplication between components
- [ ] Follows FCIS pattern (I/O injection in ArtifactService)

---

**Total Tasks:** 91 (setup: 2, tests: 40, implementation: 35, integration: 8, review: 6)

**Next Phase:** Phase 4 - Review status and phase display
