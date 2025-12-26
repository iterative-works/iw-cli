# Phase 3 Context: View Artifact Content

**Issue:** #46  
**Phase:** 3 of 6  
**Story:** Story 2 - Click artifact to view rendered markdown content  
**Estimated Effort:** 8-12 hours  
**Dependencies:** Phase 1 (ReviewState, dashboard display), Phase 2 (PathValidator)

---

## Goals

This phase enables users to click on review artifacts in the dashboard and view their markdown content rendered as HTML in a readable format. The implementation focuses on:

1. **HTTP endpoint** to serve artifact content with path validation
2. **Server-side markdown rendering** to convert markdown files to HTML
3. **Server-rendered artifact page** with navigation back to dashboard
4. **Security integration** using PathValidator from Phase 2
5. **Clean separation** between artifact viewing and list display

**What success looks like:**
- User clicks "Analysis" artifact → sees rendered analysis.md as HTML
- User clicks "Back to Dashboard" → returns to worktree list
- Invalid paths → clear error message, no filesystem leaks
- Large markdown files (>100KB) → render without issues

---

## Scope

### In Scope

1. **New HTTP endpoint** `GET /worktrees/:issueId/artifacts` with query param `?path=...`
2. **ArtifactService** application service with:
   - Path validation using PathValidator
   - File reading with error handling
   - Markdown rendering delegation
3. **MarkdownRenderer** utility for markdown-to-HTML conversion
4. **ArtifactView** presentation component for rendered page
5. **Update WorktreeListView** to make artifact labels clickable links
6. **Error handling** for missing files, invalid paths, read errors
7. **Unit tests** for ArtifactService and MarkdownRenderer
8. **Integration tests** for HTTP endpoint with real files

### Out of Scope

- Modal/JavaScript overlay (using simple page navigation instead)
- Artifact list navigation (next/previous buttons)
- Markdown editing or updating
- Artifact content caching (read fresh on each request)
- Review state status/phase display (Phase 4)
- Non-markdown files (PDFs, images, etc.)

### Why Separate Page Over Modal?

**Decision:** Server-rendered page at `/worktrees/:issueId/artifacts?path=...`

**Rationale:**
- **Simpler:** No JavaScript, no client-side rendering complexity
- **Testable:** Standard HTTP testing, no browser automation needed
- **Accessible:** Works without JavaScript, better for screen readers
- **Maintainable:** Follows existing Cask patterns (dashboard route)
- **Future-friendly:** Can add modal later if needed without breaking page

---

## Dependencies

### From Phase 1

**Available utilities:**
- `ReviewState` domain model (status, phase, message, artifacts list)
- `ReviewArtifact` domain model (label, path)
- `ReviewStateService.fetchReviewState()` - loads and parses review-state.json
- `WorktreeListView.renderWorktreeCard()` - displays artifact list (needs update for links)
- Dashboard route at `GET /` - entry point for users

**Current artifact display (Phase 1):**
```scala
// WorktreeListView.scala lines 128-139
reviewState.filter(_.artifacts.nonEmpty).map { state =>
  div(
    cls := "review-artifacts",
    h4("Review Artifacts"),
    ul(
      cls := "artifact-list",
      state.artifacts.map { artifact =>
        li(artifact.label)  // Currently just text, need to make links
      }
    )
  )
}
```

### From Phase 2

**Available security utilities:**
- `PathValidator.validateArtifactPath(worktreePath, artifactPath)` - validates paths
- Returns `Either[String, Path]` - Left for errors, Right for validated path
- Handles: empty paths, absolute paths, directory traversal, symlinks, boundary checks
- Secure error messages: "Artifact not found" (no filesystem leaks)

**Security contract:**
```scala
// MUST call before any file read
PathValidator.validateArtifactPath(
  worktreePath = Paths.get("/path/to/worktree"),
  artifactPath = "project-management/issues/46/analysis.md"
) match {
  case Right(validatedPath) => 
    // Safe to read from validatedPath
  case Left(errorMessage) => 
    // Return error to user, don't read file
}
```

### External Dependencies

**Markdown rendering library:**

Need to add to `.iw/core/project.scala`:
```scala
//> using dep com.vladsch.flexmark:flexmark-all:0.64.8
```

**Why flexmark-all:**
- **Pure JVM:** No native dependencies, works with scala-cli
- **Full-featured:** Tables, code blocks, GFM extensions
- **Well-maintained:** Active development, good docs
- **Battle-tested:** Used in JetBrains IDEs
- **flexmark-all:** Includes all extensions (simpler than cherry-picking)

**Alternative considered:** commonmark-java (simpler but fewer features)

---

## Technical Approach

### Architecture Overview

```
User clicks artifact link in dashboard
    ↓
GET /worktrees/:issueId/artifacts?path=analysis.md
    ↓
CaskServer.artifactPage() route
    ↓
ArtifactService.loadArtifact(issueId, artifactPath, state)
    ├─ Resolve worktreePath from ServerState
    ├─ PathValidator.validateArtifactPath() ← Phase 2
    ├─ Read file content (injected I/O)
    └─ MarkdownRenderer.toHtml(content)
    ↓
ArtifactView.render(artifact, html, issueId)
    ↓
HTML page with rendered markdown + back link
```

### Component Design

#### 1. ArtifactService (Application Layer)

**Purpose:** Pure business logic for artifact loading with I/O injection (FCIS pattern)

**Location:** `.iw/core/ArtifactService.scala`

```scala
package iw.core.application

import iw.core.PathValidator
import iw.core.domain.ServerState
import java.nio.file.{Path, Paths}

object ArtifactService:
  /** Load and render artifact content.
    *
    * @param issueId Issue identifier
    * @param artifactPath Relative path from worktree root
    * @param state Current server state (for worktree lookup)
    * @param readFile Function to read file content (injected I/O)
    * @return Either error message or (artifact label, rendered HTML, worktree path)
    */
  def loadArtifact(
    issueId: String,
    artifactPath: String,
    state: ServerState,
    readFile: Path => Either[String, String]
  ): Either[String, (String, String, String)] =
    // 1. Resolve worktree path from state
    state.worktrees.get(issueId) match
      case None => Left("Worktree not found")
      case Some(worktree) =>
        val worktreePath = Paths.get(worktree.path)
        
        // 2. Validate artifact path (security)
        PathValidator.validateArtifactPath(worktreePath, artifactPath) match
          case Left(error) => Left(error)
          case Right(validatedPath) =>
            // 3. Read file content
            readFile(validatedPath).flatMap { content =>
              // 4. Render markdown to HTML
              val html = MarkdownRenderer.toHtml(content)
              
              // 5. Extract artifact label from filename
              val label = extractLabel(validatedPath)
              
              Right((label, html, worktree.path))
            }

  /** Extract human-readable label from file path.
    *
    * Examples:
    *   analysis.md → "analysis.md"
    *   phase-03-context.md → "phase-03-context.md"
    *   project-management/issues/46/review.md → "review.md"
    */
  private def extractLabel(path: Path): String =
    path.getFileName.toString
```

**Testing approach:**
- Unit tests with mock I/O functions
- Test worktree not found
- Test invalid artifact path (use PathValidator tests as reference)
- Test file read errors
- Test markdown rendering integration
- Test label extraction

#### 2. MarkdownRenderer (Infrastructure Layer)

**Purpose:** Convert markdown content to HTML using flexmark

**Location:** `.iw/core/MarkdownRenderer.scala`

```scala
package iw.core.infrastructure

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownRenderer:
  /** Convert markdown content to HTML.
    *
    * Uses flexmark with common extensions enabled:
    * - Tables (GFM style)
    * - Fenced code blocks with syntax highlighting classes
    * - Strikethrough
    * - Autolinks
    *
    * @param markdown Raw markdown content
    * @return Rendered HTML (fragment, not full page)
    */
  def toHtml(markdown: String): String =
    // Configure flexmark with common extensions
    val options = MutableDataSet()
    
    // Enable GitHub Flavored Markdown features
    options.set(Parser.EXTENSIONS, java.util.Arrays.asList(
      com.vladsch.flexmark.ext.tables.TablesExtension.create(),
      com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
      com.vladsch.flexmark.ext.autolink.AutolinkExtension.create(),
      com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension.create()
    ))
    
    // Create parser and renderer
    val parser = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()
    
    // Parse and render
    val document = parser.parse(markdown)
    renderer.render(document)
```

**Testing approach:**
- Unit tests for basic markdown (headers, paragraphs, lists)
- Test code blocks with language tags
- Test tables
- Test special characters / escaping
- Test empty input
- Test very large input (performance check)

#### 3. ArtifactView (Presentation Layer)

**Purpose:** Render full HTML page for artifact viewing

**Location:** `.iw/core/presentation/views/ArtifactView.scala`

```scala
package iw.core.presentation.views

import scalatags.Text.all.*

object ArtifactView:
  /** Render artifact viewing page.
    *
    * @param artifactLabel Filename of the artifact
    * @param renderedHtml Markdown rendered as HTML
    * @param issueId Issue ID for back link
    * @return Full HTML page
    */
  def render(artifactLabel: String, renderedHtml: String, issueId: String): String =
    val page = html(
      head(
        meta(charset := "UTF-8"),
        tag("title")(s"$artifactLabel - $issueId"),
        tag("style")(raw(styles))
      ),
      body(
        div(
          cls := "container",
          div(
            cls := "header",
            a(
              cls := "back-link",
              href := "/",
              "← Back to Dashboard"
            ),
            h1(artifactLabel),
            p(cls := "issue-id", issueId)
          ),
          div(
            cls := "content",
            // Raw HTML from markdown renderer
            // flexmark output is safe (escapes user content)
            raw(renderedHtml)
          )
        )
      )
    )
    
    "<!DOCTYPE html>\n" + page.render

  private val styles = """
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      margin: 0;
      padding: 20px;
      background-color: #f5f5f5;
      line-height: 1.6;
    }

    .container {
      max-width: 900px;
      margin: 0 auto;
      background: white;
      padding: 40px;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .header {
      border-bottom: 2px solid #e9ecef;
      padding-bottom: 20px;
      margin-bottom: 30px;
    }

    .back-link {
      display: inline-block;
      color: #0066cc;
      text-decoration: none;
      margin-bottom: 10px;
      font-size: 14px;
    }

    .back-link:hover {
      text-decoration: underline;
    }

    h1 {
      margin: 10px 0;
      color: #333;
    }

    .issue-id {
      color: #666;
      font-size: 14px;
      margin: 5px 0 0 0;
    }

    .content {
      color: #333;
    }

    /* Markdown content styling */
    .content h1, .content h2, .content h3, .content h4, .content h5, .content h6 {
      margin-top: 24px;
      margin-bottom: 16px;
      font-weight: 600;
      line-height: 1.25;
    }

    .content h1 { font-size: 2em; border-bottom: 1px solid #eee; padding-bottom: 0.3em; }
    .content h2 { font-size: 1.5em; border-bottom: 1px solid #eee; padding-bottom: 0.3em; }
    .content h3 { font-size: 1.25em; }
    .content h4 { font-size: 1em; }

    .content p {
      margin-bottom: 16px;
    }

    .content ul, .content ol {
      margin-bottom: 16px;
      padding-left: 2em;
    }

    .content li {
      margin-bottom: 4px;
    }

    .content code {
      background: #f6f8fa;
      padding: 0.2em 0.4em;
      border-radius: 3px;
      font-family: 'Courier New', monospace;
      font-size: 0.9em;
    }

    .content pre {
      background: #f6f8fa;
      padding: 16px;
      border-radius: 6px;
      overflow-x: auto;
      margin-bottom: 16px;
    }

    .content pre code {
      background: none;
      padding: 0;
      font-size: 0.9em;
    }

    .content table {
      border-collapse: collapse;
      width: 100%;
      margin-bottom: 16px;
    }

    .content table th,
    .content table td {
      border: 1px solid #ddd;
      padding: 8px 12px;
      text-align: left;
    }

    .content table th {
      background: #f6f8fa;
      font-weight: 600;
    }

    .content blockquote {
      border-left: 4px solid #ddd;
      margin: 0 0 16px 0;
      padding-left: 16px;
      color: #666;
    }

    .content a {
      color: #0066cc;
      text-decoration: none;
    }

    .content a:hover {
      text-decoration: underline;
    }

    .content hr {
      border: none;
      border-top: 1px solid #ddd;
      margin: 24px 0;
    }
  """
```

**Testing approach:**
- Unit tests for HTML structure
- Verify back link includes correct issue ID
- Verify title includes artifact label
- Test with sample rendered HTML

#### 4. CaskServer Route (Infrastructure Layer)

**Purpose:** HTTP endpoint for artifact viewing

**Location:** `.iw/core/CaskServer.scala` (add new route)

```scala
// Add to existing CaskServer class

@cask.get("/worktrees/:issueId/artifacts")
def artifactPage(issueId: String, request: cask.Request): cask.Response[String] =
  // Get artifact path from query param
  val artifactPathOpt = request.queryParams.get("path").flatMap(_.headOption)
  
  artifactPathOpt match
    case None =>
      cask.Response(
        data = "Missing required parameter: path",
        statusCode = 400
      )
    
    case Some(artifactPath) =>
      // Load current server state
      val stateResult = ServerStateService.load(repository)
      
      stateResult match
        case Left(error) =>
          System.err.println(s"Failed to load state: $error")
          cask.Response(
            data = "Internal server error",
            statusCode = 500
          )
        
        case Right(state) =>
          // File I/O wrapper: read file content
          val readFile = (path: java.nio.file.Path) => scala.util.Try {
            val source = scala.io.Source.fromFile(path.toFile)
            try source.mkString
            finally source.close()
          }.toEither.left.map(_.getMessage)
          
          // Load and render artifact
          ArtifactService.loadArtifact(issueId, artifactPath, state, readFile) match
            case Right((label, html, worktreePath)) =>
              val page = ArtifactView.render(label, html, issueId)
              cask.Response(
                data = page,
                headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
              )
            
            case Left(error) =>
              // Log error for debugging (may contain filesystem info)
              System.err.println(s"Artifact error for $issueId/$artifactPath: $error")
              
              // Return generic error to user (secure)
              cask.Response(
                data = errorPage(issueId, error),
                statusCode = 404,
                headers = Seq("Content-Type" -> "text/html; charset=UTF-8")
              )

// Helper for error page
private def errorPage(issueId: String, error: String): String =
  ArtifactView.renderError(issueId, error)
```

**Add to ArtifactView:**
```scala
/** Render error page for artifact loading failures. */
def renderError(issueId: String, errorMessage: String): String =
  val page = html(
    head(
      meta(charset := "UTF-8"),
      tag("title")("Artifact Error"),
      tag("style")(raw(styles))
    ),
    body(
      div(
        cls := "container",
        div(
          cls := "header",
          a(cls := "back-link", href := "/", "← Back to Dashboard"),
          h1("Artifact Not Found")
        ),
        div(
          cls := "content",
          p(s"Unable to load artifact: $errorMessage"),
          p(a(href := "/", "Return to dashboard"))
        )
      )
    )
  )
  "<!DOCTYPE html>\n" + page.render
```

**Testing approach:**
- Integration tests with test worktrees
- Test valid artifact path
- Test missing path parameter
- Test invalid artifact path (directory traversal)
- Test worktree not found
- Test file read error
- Test markdown rendering

#### 5. Update WorktreeListView for Clickable Links

**Purpose:** Make artifact labels clickable links to artifact page

**Location:** `.iw/core/WorktreeListView.scala` (modify existing code)

**Current code (lines 128-139):**
```scala
reviewState.filter(_.artifacts.nonEmpty).map { state =>
  div(
    cls := "review-artifacts",
    h4("Review Artifacts"),
    ul(
      cls := "artifact-list",
      state.artifacts.map { artifact =>
        li(artifact.label)  // ← Change this
      }
    )
  )
}
```

**Updated code:**
```scala
reviewState.filter(_.artifacts.nonEmpty).map { state =>
  div(
    cls := "review-artifacts",
    h4("Review Artifacts"),
    ul(
      cls := "artifact-list",
      state.artifacts.map { artifact =>
        li(
          a(
            href := s"/worktrees/${worktree.issueId}/artifacts?path=${artifact.path}",
            artifact.label
          )
        )
      }
    )
  )
}
```

**Add CSS to DashboardService styles:**
```css
.review-artifacts {
  margin: 15px 0;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 6px;
}

.review-artifacts h4 {
  margin: 0 0 10px 0;
  font-size: 0.95em;
  font-weight: 600;
  color: #495057;
}

.artifact-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.artifact-list li {
  margin: 4px 0;
}

.artifact-list a {
  color: #0066cc;
  text-decoration: none;
  font-size: 0.9em;
}

.artifact-list a:hover {
  text-decoration: underline;
}
```

**Testing approach:**
- Update WorktreeListViewTest with artifact link verification
- Test URL encoding for artifact paths with special characters

---

## Files to Modify/Create

### New Files (7 files)

1. **`.iw/core/ArtifactService.scala`** - Application service for artifact loading
   - `loadArtifact(issueId, artifactPath, state, readFile)` - main entry point
   - `extractLabel(path)` - filename extraction
   - Pure logic with I/O injection

2. **`.iw/core/infrastructure/MarkdownRenderer.scala`** - Markdown to HTML conversion
   - `toHtml(markdown)` - flexmark integration
   - Configure GFM extensions
   - Pure function (no I/O)

3. **`.iw/core/presentation/views/ArtifactView.scala`** - Artifact page rendering
   - `render(label, html, issueId)` - full page
   - `renderError(issueId, error)` - error page
   - CSS styles for markdown content

4. **`.iw/core/test/ArtifactServiceTest.scala`** - Unit tests
   - Test worktree resolution
   - Test path validation integration
   - Test file reading
   - Test error cases

5. **`.iw/core/test/MarkdownRendererTest.scala`** - Unit tests
   - Test basic markdown features
   - Test code blocks, tables
   - Test edge cases

6. **`.iw/core/test/ArtifactViewTest.scala`** - Unit tests
   - Test HTML structure
   - Test error page rendering

7. **`.iw/core/test/ArtifactEndpointTest.scala`** - Integration tests
   - Test full HTTP flow
   - Test with real markdown files
   - Test error responses

### Modified Files (3 files)

1. **`.iw/core/project.scala`** - Add flexmark dependency
   - Add `//> using dep com.vladsch.flexmark:flexmark-all:0.64.8`

2. **`.iw/core/CaskServer.scala`** - Add artifact endpoint
   - Add `@cask.get("/worktrees/:issueId/artifacts")` route
   - Add `artifactPage()` handler
   - Add `errorPage()` helper

3. **`.iw/core/WorktreeListView.scala`** - Make artifacts clickable
   - Change `li(artifact.label)` to `li(a(href := ..., artifact.label))`
   - Update test: `.iw/core/test/WorktreeListViewTest.scala`

4. **`.iw/core/DashboardService.scala`** - Add artifact CSS styles
   - Add `.review-artifacts`, `.artifact-list` styles

---

## Testing Strategy

### Unit Tests

**ArtifactServiceTest.scala:**
- ✓ Load artifact with valid path
- ✓ Worktree not found
- ✓ Invalid artifact path (absolute, traversal, etc.)
- ✓ File read error (mock readFile to return Left)
- ✓ Label extraction from various paths
- ✓ Markdown rendering integration

**MarkdownRendererTest.scala:**
- ✓ Basic markdown (headers, paragraphs, lists)
- ✓ Code blocks with syntax highlighting
- ✓ Tables (GFM style)
- ✓ Links and autolinks
- ✓ Blockquotes
- ✓ Empty input
- ✓ Special characters / escaping

**ArtifactViewTest.scala:**
- ✓ Render artifact page structure
- ✓ Back link includes correct issue ID
- ✓ Error page structure
- ✓ HTML escaping (use sample with special chars)

### Integration Tests

**ArtifactEndpointTest.scala:**
- ✓ GET /worktrees/:issueId/artifacts?path=... returns 200 with HTML
- ✓ Missing path param returns 400
- ✓ Invalid artifact path returns 404
- ✓ Worktree not found returns 404
- ✓ File read error returns 404
- ✓ Markdown rendering with real file

**Test fixtures:**
- Create test worktree structure:
  ```
  /tmp/test-worktree-artifact/
    project-management/
      issues/
        TEST-123/
          analysis.md        ← Sample markdown
          review-state.json  ← Points to analysis.md
  ```

### E2E Testing (Manual)

1. Start server: `./iw server`
2. Register test worktree with review-state.json
3. Open dashboard: http://localhost:9876
4. Click artifact link → verify rendering
5. Click "Back to Dashboard" → verify navigation
6. Try invalid URL → verify error handling

---

## Acceptance Criteria (from Story 2)

**Gherkin Scenario 1: Click artifact shows markdown content**
```gherkin
Scenario: Click artifact shows markdown content
  Given a worktree has a review-state.json with artifact "analysis.md"
  And the artifact path is "project-management/issues/IWLE-72/analysis.md"
  When I load the dashboard
  And I click the "analysis.md" artifact link
  Then I see the markdown content rendered as HTML
  And the content is displayed in a readable format
```

**Acceptance checks:**
- [ ] Artifact links are clickable in dashboard
- [ ] Clicking link navigates to `/worktrees/:issueId/artifacts?path=...`
- [ ] Markdown is rendered with proper formatting (headers, lists, code blocks, tables)
- [ ] Page includes "Back to Dashboard" link
- [ ] Page shows artifact filename and issue ID in header

**Gherkin Scenario 2: View multiple artifacts sequentially**
```gherkin
Scenario: View multiple artifacts sequentially
  Given a worktree has 3 artifacts in review state
  When I view the first artifact
  And I close the artifact view
  And I view the second artifact
  Then I see the second artifact's content
  And I can navigate between artifacts easily
```

**Acceptance checks:**
- [ ] Can view first artifact, return to dashboard, view second artifact
- [ ] Each artifact shows its own content (no caching issues)
- [ ] Back button works from any artifact view

**Error handling checks:**
- [ ] Missing file shows "Artifact not found" error page
- [ ] Invalid path (traversal) shows "Artifact not found" error page
- [ ] Worktree not found shows clear error
- [ ] Missing path param shows 400 error
- [ ] Errors don't leak filesystem structure

---

## Implementation Sequence

**Recommended order:**

1. **Add flexmark dependency** (1 min)
   - Modify `.iw/core/project.scala`
   - Test: `cd .iw && scala-cli compile core` succeeds

2. **Implement MarkdownRenderer** (30 min)
   - Create `.iw/core/infrastructure/MarkdownRenderer.scala`
   - Write unit tests
   - Test: All MarkdownRendererTest tests pass

3. **Implement ArtifactService** (1-2h)
   - Create `.iw/core/ArtifactService.scala`
   - Integrate PathValidator from Phase 2
   - Write unit tests
   - Test: All ArtifactServiceTest tests pass

4. **Implement ArtifactView** (1h)
   - Create `.iw/core/presentation/views/ArtifactView.scala`
   - Add CSS styles for markdown content
   - Write unit tests
   - Test: All ArtifactViewTest tests pass

5. **Add CaskServer route** (1h)
   - Modify `.iw/core/CaskServer.scala`
   - Add artifact endpoint
   - Manual test: Can access endpoint (even if returns error)

6. **Update WorktreeListView** (30 min)
   - Modify `.iw/core/WorktreeListView.scala` to add links
   - Update `.iw/core/DashboardService.scala` with CSS
   - Update `.iw/core/test/WorktreeListViewTest.scala`
   - Test: WorktreeListViewTest tests pass

7. **Integration testing** (2-3h)
   - Create test fixtures
   - Write ArtifactEndpointTest
   - Test: All integration tests pass
   - Manual E2E: Click through full workflow

8. **Error handling polish** (1h)
   - Verify all error cases
   - Test edge cases (large files, special characters in paths)
   - Security review: Confirm no path leaks

9. **Code review and refinement** (1-2h)
   - Run all tests: `./iw test`
   - Check for code duplication
   - Verify FCIS pattern followed
   - Review error messages

**Total estimated time:** 8-12 hours

---

## Risk Assessment

### Medium Risks

1. **Flexmark configuration complexity**
   - Risk: Extensions not configured correctly → missing features
   - Mitigation: Test with sample markdown files covering all GFM features
   - Fallback: Use simpler commonmark-java if flexmark issues arise

2. **Large file performance**
   - Risk: 100KB+ markdown files render slowly → UI freeze
   - Mitigation: Test with large files during integration testing
   - Fallback: Add file size check, return error for files >1MB

3. **URL encoding issues**
   - Risk: Artifact paths with special characters break links
   - Mitigation: Test with paths containing spaces, Unicode
   - Fallback: Use proper URL encoding in WorktreeListView links

### Low Risks

1. **CSS styling inconsistencies**
   - Risk: Markdown content doesn't match dashboard styling
   - Mitigation: Use same font family, color palette as dashboard
   - Easy fix: Adjust CSS after visual review

2. **Back navigation**
   - Risk: Users expect browser back button, not link
   - Mitigation: Both work (browser back and "Back to Dashboard" link)
   - Enhancement: Can add navigation breadcrumbs later

---

## Security Considerations

**Path validation (Phase 2 integration):**
- MUST call PathValidator.validateArtifactPath before any file read
- MUST use returned Path directly (don't re-resolve)
- MUST NOT leak filesystem paths in error messages
- MUST NOT log artifact paths in user-facing errors

**XSS prevention:**
- Flexmark escapes HTML in markdown content by default
- Scalatags escapes all text content
- Only use `raw()` for flexmark output (trusted after escaping)

**File access:**
- MUST only serve files within worktree boundary
- MUST reject symlinks pointing outside worktree
- MUST reject absolute paths

**Error handling:**
- User-facing errors: Generic "Artifact not found"
- Server logs: Can include detailed path info for debugging
- MUST NOT return stack traces to users

---

## Performance Considerations

**File reading:**
- Read on every request (no caching in Phase 3)
- Acceptable for typical markdown files (<100KB)
- Future optimization: Add content caching with mtime validation

**Markdown rendering:**
- Flexmark is fast for typical files
- May be slow for very large files (>1MB)
- Consider: Add file size check, reject files >1MB

**Memory usage:**
- Load entire file into memory for rendering
- Acceptable for markdown files
- Large files: Risk of OOM with many concurrent requests

**Future optimizations (out of scope):**
- Cache rendered HTML with mtime validation
- Stream large files instead of loading fully
- Add Content-Length header for better browser handling

---

## Documentation Requirements

- [ ] Update CLAUDE.md if new patterns emerge
- [ ] Add inline comments for flexmark configuration
- [ ] Document ArtifactService I/O injection pattern
- [ ] Add example artifact paths to tests

---

## Definition of Done

**Code complete:**
- [ ] All 7 new files created with tests
- [ ] All 4 modified files updated
- [ ] No compilation errors
- [ ] No test failures
- [ ] Code review complete (self-review using CLAUDE.md guidelines)

**Functionality complete:**
- [ ] Can click artifact link in dashboard
- [ ] Can view rendered markdown
- [ ] Can return to dashboard
- [ ] Invalid paths show error
- [ ] Large files (>100KB) render correctly

**Quality gates:**
- [ ] Unit test coverage >90% for new code
- [ ] Integration tests cover happy path + 3 error cases
- [ ] Manual E2E testing completed
- [ ] Security review: PathValidator used correctly
- [ ] No code duplication
- [ ] Follows FCIS pattern (I/O at edges)

**Ready for Phase 4:**
- [ ] Story 2 acceptance criteria met
- [ ] ArtifactView can be extended for status/phase display (Phase 4)
- [ ] No blocking issues or regressions

---

## Notes for Implementation

**Import organization:**
```scala
// Domain layer
import iw.core.domain.{ServerState, ReviewState, ReviewArtifact}

// Application layer
import iw.core.application.ArtifactService

// Infrastructure layer
import iw.core.infrastructure.MarkdownRenderer
import iw.core.PathValidator

// Presentation layer
import iw.core.presentation.views.ArtifactView
```

**File I/O wrapper pattern (from DashboardService):**
```scala
val readFile = (path: java.nio.file.Path) => scala.util.Try {
  val source = scala.io.Source.fromFile(path.toFile)
  try source.mkString
  finally source.close()
}.toEither.left.map(_.getMessage)
```

**Error logging pattern (from CaskServer):**
```scala
System.err.println(s"Artifact error for $issueId/$artifactPath: $error")
```

**Scalatags raw() usage:**
```scala
// ONLY use raw() for trusted content (after markdown rendering)
raw(renderedHtml)  // ✓ Safe: flexmark escapes user content

// NEVER use raw() for user input
raw(userInput)     // ✗ UNSAFE: XSS vulnerability
```

---

## Success Metrics

**Functionality:**
- User can view any artifact in review-state.json
- Markdown renders with proper formatting
- Navigation works smoothly
- Error cases handled gracefully

**Code quality:**
- All tests pass
- No code duplication
- Follows existing patterns (FCIS, I/O injection)
- Security: No path traversal vulnerabilities

**User experience:**
- Artifact viewing feels fast (<500ms for typical files)
- Markdown is readable (proper spacing, fonts, colors)
- Error messages are clear (no technical jargon)
- Back navigation is intuitive

---

**Ready to implement!** Start with step 1 (add flexmark dependency) and work through the sequence.
