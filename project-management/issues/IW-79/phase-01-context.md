# Phase 1 Context: Modal UI + Issue Search

**Issue:** IW-79
**Phase:** 1 of 4
**Estimated Effort:** 4-5 hours
**Status:** Ready for Implementation

## Goals

This phase establishes the foundation for dashboard-based worktree creation by adding:

1. **"Create Worktree" button** in the dashboard header
2. **Modal dialog component** that opens on button click
3. **Search input** with debounced live search (300ms delay)
4. **Issue search API endpoint** (`GET /api/issues/search?q=...`)
5. **Search results display** showing issue ID, title, and status
6. **Cross-tracker search support** (GitHub, Linear, YouTrack)

At the end of this phase, users can:
- Click "Create Worktree" button to open a modal
- Type in the search field to search for issues
- See search results appear dynamically (debounced)
- View issue details (ID, title, status) in results

**What's NOT in scope:**
- Actually creating worktrees (Phase 2)
- Error handling beyond basic API errors (Phase 3)
- Concurrent creation protection (Phase 4)
- Initial suggestions/"recent issues" list (deferred)

## Scope

### In Scope

**UI Components:**
- "Create Worktree" button in dashboard header (next to h1)
- Modal overlay with backdrop
- Modal content container with close button
- Search input field with placeholder "Search by issue ID or title..."
- Search results list container
- Individual result item rendering (issue card)

**API Endpoints:**
- `GET /api/issues/search?q=<query>` - Search issues across configured tracker
- Returns JSON array of search results

**Search Functionality:**
- Debounced search (300ms after last keystroke)
- Search by issue ID (exact match priority)
- Search by title (partial match, case-insensitive)
- Maximum 10 results returned
- Works with GitHub, Linear, and YouTrack trackers

**HTMX Integration:**
- Modal open/close interactions
- Debounced search with `hx-trigger="keyup changed delay:300ms"`
- Dynamic result rendering via `hx-get`
- Proper HTMX targeting and swapping

### Out of Scope

- Worktree creation logic (Phase 2)
- Advanced error states and retry (Phase 3)
- Concurrent creation protection (Phase 4)
- "Recent issues" initial suggestions
- Keyboard navigation (arrows, enter to select)
- Loading skeleton states
- Empty state illustrations

## Dependencies

**No prior phase dependencies** - This is Phase 1.

**External Dependencies:**
- HTMX library (will be added to page head)
- Existing tracker clients: `LinearClient`, `GitHubClient`, `YouTrackClient`
- Project configuration for tracker type detection

**Code Dependencies:**
- `.iw/core/CaskServer.scala` - Will add new route
- `.iw/core/DashboardService.scala` - Will update to include HTMX and button
- `.iw/core/presentation/views/` - Will add new modal view components

## Technical Approach

### Architecture Overview

```
User clicks button
    ↓
Modal opens (HTMX target swap)
    ↓
User types in search input
    ↓
HTMX triggers debounced GET /api/issues/search?q=...
    ↓
Server calls appropriate tracker client search method
    ↓
Server renders search results as HTML
    ↓
HTMX swaps results into modal
```

### Modal Component Design

**Modal Structure (ScalaTags):**
```scala
div(
  id := "create-worktree-modal",
  cls := "modal hidden",  // hidden by default
  div(cls := "modal-backdrop"),
  div(
    cls := "modal-content",
    div(
      cls := "modal-header",
      h2("Create Worktree"),
      button(
        cls := "modal-close",
        attr("hx-get") := "/",
        attr("hx-target") := "#create-worktree-modal",
        attr("hx-swap") := "outerHTML",
        "×"
      )
    ),
    div(
      cls := "modal-body",
      input(
        id := "issue-search-input",
        `type` := "text",
        placeholder := "Search by issue ID or title...",
        attr("hx-get") := "/api/issues/search",
        attr("hx-trigger") := "keyup changed delay:300ms",
        attr("hx-target") := "#search-results",
        attr("hx-include") := "[name='q']",
        name := "q"
      ),
      div(id := "search-results", cls := "search-results")
    )
  )
)
```

**Modal Trigger Button:**
```scala
button(
  cls := "btn-primary create-worktree-btn",
  attr("hx-get") := "/api/modal/create-worktree",
  attr("hx-target") := "#modal-container",
  attr("hx-swap") := "innerHTML",
  "Create Worktree"
)
```

### Search API Implementation

**Route in CaskServer:**
```scala
@cask.get("/api/issues/search")
def searchIssues(q: String): cask.Response[ujson.Value] =
  // Load project config to determine tracker type
  val configPath = os.pwd / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
  val config = ConfigFileRepository.read(configPath)
  
  config match
    case None =>
      cask.Response(
        ujson.Arr(),
        statusCode = 500
      )
    case Some(cfg) =>
      val searchService = IssueSearchService(cfg)
      searchService.search(q) match
        case Left(error) =>
          cask.Response(
            ujson.Arr(),
            statusCode = 500
          )
        case Right(results) =>
          cask.Response(
            ujson.Arr(results.map(_.toJson): _*),
            statusCode = 200
          )
```

**New Service: IssueSearchService**

Create new file: `.iw/core/application/IssueSearchService.scala`

```scala
package iw.core.application

import iw.core.{ProjectConfiguration, IssueId, LinearClient, GitHubClient, YouTrackClient, ApiToken, Constants}

case class IssueSearchResult(
  id: String,
  title: String,
  status: String,
  url: String
)

object IssueSearchService:
  def search(query: String, config: ProjectConfiguration): Either[String, List[IssueSearchResult]] =
    config.trackerType.toString.toLowerCase match
      case "linear" => searchLinear(query, config)
      case "github" => searchGitHub(query, config)
      case "youtrack" => searchYouTrack(query, config)
      case _ => Left("Unknown tracker type")
  
  private def searchLinear(query: String, config: ProjectConfiguration): Either[String, List[IssueSearchResult]] =
    // Linear GraphQL search query
    // For Phase 1: Simple implementation returning empty list
    // Will be implemented with actual Linear search API
    Right(List.empty)
  
  private def searchGitHub(query: String, config: ProjectConfiguration): Either[String, List[IssueSearchResult]] =
    // GitHub search via gh CLI or API
    // For Phase 1: Simple implementation returning empty list
    // Will be implemented with gh issue list --search
    Right(List.empty)
  
  private def searchYouTrack(query: String, config: ProjectConfiguration): Either[String, List[IssueSearchResult]] =
    // YouTrack search API
    // For Phase 1: Simple implementation returning empty list
    // Will be implemented with YouTrack search endpoint
    Right(List.empty)
```

**Note:** For Phase 1, we'll implement a **minimal working search** that:
- For exact ID match: Uses existing `fetchIssue` to validate the ID exists
- For title search: Returns empty list (stub for now)
- This gets the UI working without needing full search API integration

Full search implementation can be added incrementally:
1. Phase 1: ID-only search (exact match via fetchIssue)
2. Follow-up: Title search for each tracker

### Tracker Search Implementation Strategy

**Phase 1 Minimal Approach:**

```scala
def search(query: String, config: ProjectConfiguration): Either[String, List[IssueSearchResult]] =
  // Try to parse as issue ID
  IssueId.parse(query, config.teamPrefix) match
    case Right(issueId) =>
      // Attempt to fetch this specific issue
      fetchIssueById(issueId, config) match
        case Right(issue) =>
          Right(List(IssueSearchResult(
            issue.id,
            issue.title,
            issue.status,
            buildIssueUrl(issue.id, config)
          )))
        case Left(_) =>
          // Not found or error - return empty
          Right(List.empty)
    case Left(_) =>
      // Not a valid ID - title search (stub for Phase 1)
      Right(List.empty)
```

This gets the modal and search UI working immediately. Users can search by exact issue ID and see results.

**Future Enhancement (post-Phase 1):**
- Implement actual search APIs for each tracker
- Linear: GraphQL search query with `issuesSearch` field
- GitHub: `gh issue list --search "<query>"` command
- YouTrack: REST API `/api/issues?query=...` endpoint

### HTMX Integration Details

**Add HTMX to Dashboard:**

In `DashboardService.scala`, update the `<head>` section:

```scala
head(
  meta(charset := "UTF-8"),
  tag("title")("iw Dashboard"),
  // Add HTMX
  tag("script")(
    src := "https://unpkg.com/htmx.org@1.9.10",
    integrity := "sha384-D1Kt99CQMDuVetoL1lrYwg5t+9QdHe7NLX/SoJYkXDFfX37iInKRy5xLSi8nO7UC",
    attr("crossorigin") := "anonymous"
  ),
  tag("style")(raw(styles))
)
```

**HTMX Patterns Used:**

1. **Modal Open:** Button with `hx-get` to fetch modal HTML
2. **Modal Close:** Close button swaps modal container to empty
3. **Debounced Search:** Input with `hx-trigger="keyup changed delay:300ms"`
4. **Result Swap:** Search results target `#search-results` div

### CSS Styles for Modal

Add to `DashboardService.scala` styles:

```css
/* Modal overlay */
.modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal.hidden {
  display: none;
}

.modal-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
}

.modal-content {
  position: relative;
  background: white;
  border-radius: 8px;
  padding: 0;
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 1001;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e9ecef;
}

.modal-header h2 {
  margin: 0;
  font-size: 20px;
  color: #333;
}

.modal-close {
  background: none;
  border: none;
  font-size: 28px;
  color: #999;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

.modal-close:hover {
  background: #f5f5f5;
  color: #333;
}

.modal-body {
  padding: 20px;
  max-height: calc(80vh - 80px);
  overflow-y: auto;
}

/* Search input */
#issue-search-input {
  width: 100%;
  padding: 12px 16px;
  font-size: 16px;
  border: 2px solid #e9ecef;
  border-radius: 6px;
  outline: none;
  transition: border-color 0.2s;
}

#issue-search-input:focus {
  border-color: #228be6;
}

/* Search results */
.search-results {
  margin-top: 16px;
}

.search-result-item {
  padding: 12px 16px;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.search-result-item:hover {
  border-color: #228be6;
  background: #f8f9fa;
}

.search-result-id {
  font-size: 14px;
  color: #228be6;
  font-weight: 600;
  margin-bottom: 4px;
}

.search-result-title {
  font-size: 16px;
  color: #333;
  margin-bottom: 4px;
}

.search-result-status {
  font-size: 13px;
  color: #666;
}

/* Create worktree button */
.create-worktree-btn {
  padding: 10px 20px;
  background: #228be6;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.create-worktree-btn:hover {
  background: #1c7ed6;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.dashboard-header h1 {
  margin: 0;
}
```

## Files to Create/Modify

### Files to Create

1. **`.iw/core/application/IssueSearchService.scala`**
   - Purpose: Search issues across different trackers
   - Exports: `IssueSearchService.search(query, config)`
   - Phase 1: Implements ID-only search using existing fetchIssue

2. **`.iw/core/presentation/views/CreateWorktreeModal.scala`**
   - Purpose: Render modal component
   - Exports: `CreateWorktreeModal.render()`
   - Uses ScalaTags for HTML generation

3. **`.iw/core/presentation/views/SearchResultsView.scala`**
   - Purpose: Render search results list
   - Exports: `SearchResultsView.render(results: List[IssueSearchResult])`
   - Individual result item rendering

### Files to Modify

1. **`.iw/core/infrastructure/CaskServer.scala`**
   - Add route: `@cask.get("/api/issues/search")`
   - Add route: `@cask.get("/api/modal/create-worktree")` (returns modal HTML)
   - Uses `IssueSearchService` for search logic

2. **`.iw/core/application/DashboardService.scala`**
   - Add HTMX script tag to `<head>`
   - Update header section to include "Create Worktree" button
   - Add modal container div to body
   - Update CSS styles to include modal styles

3. **`.iw/core/domain/IssueSearchResult.scala`** (new domain model)
   - Value object for search results
   - Fields: id, title, status, url

### File Organization

```
.iw/core/
├── application/
│   ├── DashboardService.scala        [MODIFY]
│   └── IssueSearchService.scala      [CREATE]
├── domain/
│   └── IssueSearchResult.scala       [CREATE]
├── infrastructure/
│   └── CaskServer.scala              [MODIFY]
└── presentation/
    └── views/
        ├── CreateWorktreeModal.scala [CREATE]
        └── SearchResultsView.scala   [CREATE]
```

## Testing Strategy

### Unit Tests

**IssueSearchService Tests:**

File: `.iw/core/test/IssueSearchServiceTest.scala`

```scala
class IssueSearchServiceTest extends munit.FunSuite:
  test("search by valid issue ID returns result"):
    // Mock config with Linear tracker
    // Mock LinearClient.fetchIssue to return issue
    // Assert single result returned
    
  test("search by invalid issue ID returns empty"):
    // Mock config with Linear tracker
    // Mock LinearClient.fetchIssue to return error
    // Assert empty results
    
  test("search by title returns empty (Phase 1 stub)"):
    // Non-ID query
    // Assert empty results (stub behavior)
```

**SearchResultsView Tests:**

File: `.iw/core/test/SearchResultsViewTest.scala`

```scala
class SearchResultsViewTest extends munit.FunSuite:
  test("renders empty state when no results"):
    // Render with empty list
    // Assert contains "No issues found" message
    
  test("renders results list"):
    // Render with 2 results
    // Assert 2 result items present
    // Assert each has ID, title, status
```

### Integration Tests

**Search API Endpoint Tests:**

File: `.iw/core/test/CaskServerTest.scala` (extend existing)

```scala
test("GET /api/issues/search returns 200"):
  // Start server
  // GET /api/issues/search?q=IW-79
  // Assert 200 status
  // Assert JSON array response

test("GET /api/issues/search with invalid query returns empty array"):
  // GET /api/issues/search?q=INVALID-999
  // Assert 200 status
  // Assert empty array
```

**Modal Endpoint Tests:**

```scala
test("GET /api/modal/create-worktree returns modal HTML"):
  // GET /api/modal/create-worktree
  // Assert 200 status
  // Assert HTML contains modal structure
  // Assert contains search input
```

### Manual Testing Checklist

**UI Flow:**
- [ ] "Create Worktree" button visible in dashboard header
- [ ] Clicking button opens modal
- [ ] Modal backdrop visible
- [ ] Search input has focus when modal opens
- [ ] Typing in search input triggers debounced search
- [ ] Search results appear in modal
- [ ] Clicking modal close button closes modal
- [ ] Clicking backdrop closes modal

**Search Behavior:**
- [ ] Searching valid issue ID (e.g., "IW-79") shows result
- [ ] Search shows issue title, status
- [ ] Searching invalid ID shows "No issues found"
- [ ] Title search shows "No issues found" (stub)
- [ ] Search has 300ms debounce (no immediate API calls)
- [ ] Results update as query changes

**Cross-Tracker Testing:**
- [ ] Linear tracker: Search by Linear ID (e.g., "IWLE-100")
- [ ] GitHub tracker: Search by issue number (e.g., "79", "IW-79")
- [ ] YouTrack tracker: Search by YouTrack ID (e.g., "PROJ-123")

**Styling:**
- [ ] Modal centered on screen
- [ ] Modal has shadow and rounded corners
- [ ] Search input has focus styling
- [ ] Result items have hover effect
- [ ] Button has hover effect
- [ ] Modal scrolls if many results

## Acceptance Criteria

**Functional Requirements:**

1. **Button Presence:**
   - [ ] "Create Worktree" button visible in dashboard header
   - [ ] Button positioned next to page title
   - [ ] Button has primary styling (blue background)

2. **Modal Opening:**
   - [ ] Clicking button opens modal overlay
   - [ ] Modal appears centered on screen
   - [ ] Backdrop dims background
   - [ ] Page scroll disabled when modal open

3. **Search Input:**
   - [ ] Search input field visible in modal
   - [ ] Placeholder text: "Search by issue ID or title..."
   - [ ] Input receives focus when modal opens
   - [ ] Input has border highlight on focus

4. **Search Functionality:**
   - [ ] Typing triggers search after 300ms pause
   - [ ] Search works by issue ID (exact match)
   - [ ] Valid ID returns issue with title and status
   - [ ] Invalid ID shows "No issues found"
   - [ ] Empty query shows no results

5. **Search Results Display:**
   - [ ] Results appear below search input
   - [ ] Each result shows: Issue ID, Title, Status
   - [ ] Results are clickable (Phase 2 will handle click)
   - [ ] Maximum 10 results shown
   - [ ] Results update as query changes

6. **Modal Closing:**
   - [ ] Close button (×) in modal header
   - [ ] Clicking close button closes modal
   - [ ] Clicking backdrop closes modal
   - [ ] Modal removal clears search results

7. **Cross-Tracker Support:**
   - [ ] Search works with Linear tracker
   - [ ] Search works with GitHub tracker
   - [ ] Search works with YouTrack tracker
   - [ ] Correct tracker client called based on config

**Technical Requirements:**

1. **HTMX Integration:**
   - [ ] HTMX library loaded in page head
   - [ ] Debounced search uses `hx-trigger="keyup changed delay:300ms"`
   - [ ] Search endpoint uses `hx-get="/api/issues/search"`
   - [ ] Results swap into `#search-results` div

2. **API Endpoints:**
   - [ ] `GET /api/issues/search?q=<query>` returns JSON
   - [ ] `GET /api/modal/create-worktree` returns modal HTML
   - [ ] Search endpoint handles missing query param
   - [ ] Search endpoint handles tracker errors gracefully

3. **Code Quality:**
   - [ ] All new code follows functional programming principles
   - [ ] Pure functions for rendering and parsing
   - [ ] Effects isolated to API boundaries
   - [ ] ScalaTags used for all HTML generation
   - [ ] No inline JavaScript in HTML

4. **Testing:**
   - [ ] Unit tests for IssueSearchService
   - [ ] Unit tests for view components
   - [ ] Integration tests for API endpoints
   - [ ] Manual testing checklist completed

**Non-Functional Requirements:**

1. **Performance:**
   - [ ] Search responds within 500ms for local network
   - [ ] Debounce prevents excessive API calls
   - [ ] Modal opens/closes without lag

2. **Usability:**
   - [ ] Modal is keyboard accessible (Tab, Escape)
   - [ ] Search input accepts paste events
   - [ ] Visual feedback for loading state (Phase 2)

3. **Maintainability:**
   - [ ] Code organized by layer (domain, application, presentation, infrastructure)
   - [ ] Each component has single responsibility
   - [ ] File purposes documented in header comments

## Implementation Notes

### Order of Implementation

**Step 1: Domain Model (30 min)**
1. Create `IssueSearchResult.scala` value object
2. Unit tests for model

**Step 2: Search Service (1 hour)**
1. Create `IssueSearchService.scala`
2. Implement ID-only search using existing fetchIssue
3. Unit tests for search service
4. Test with Linear, GitHub, YouTrack configs

**Step 3: View Components (1 hour)**
1. Create `CreateWorktreeModal.scala`
2. Create `SearchResultsView.scala`
3. Unit tests for rendering
4. Test HTML output structure

**Step 4: API Routes (1 hour)**
1. Add HTMX script to DashboardService
2. Add modal endpoint to CaskServer
3. Add search endpoint to CaskServer
4. Integration tests for endpoints

**Step 5: Dashboard Integration (45 min)**
1. Update dashboard header with button
2. Add modal container div
3. Add CSS styles for modal
4. Test complete flow

**Step 6: Manual Testing (45 min)**
1. Test with each tracker type
2. Test search variations
3. Test modal interactions
4. Fix any UI issues

### Key Patterns to Follow

**Existing Patterns from Codebase:**

1. **ScalaTags for HTML:**
   - Use `scalatags.Text.all.*`
   - Use `tag("tagname")` for HTML5 tags
   - Use `raw()` only for trusted content

2. **Cask Routes:**
   - Define routes as methods with `@cask.get` annotation
   - Return `cask.Response[T]` with status codes
   - Use pattern matching for error handling

3. **Service Layer:**
   - Pure functions for business logic
   - Inject I/O functions as parameters
   - Return `Either[String, T]` for errors

4. **View Layer:**
   - Views are pure functions returning `Frag`
   - No state in view components
   - Pass all data as parameters

**Example from Existing Code:**

See `WorktreeListView.scala` for ScalaTags patterns:
- Using `cls :=` for CSS classes
- Using `attr()` for custom attributes
- Using `raw()` for CSS/JS strings
- Building complex HTML structures functionally

See `CaskServer.scala` for API route patterns:
- Pattern matching on `Either` results
- Returning appropriate status codes
- JSON serialization with ujson

### Edge Cases to Handle

1. **Empty Search Query:**
   - Return empty results (don't search)

2. **Very Long Query:**
   - Limit query to 100 characters

3. **Special Characters in Query:**
   - URL encode query parameters
   - Handle quotes, ampersands safely

4. **No Network Connection:**
   - Return error to client
   - Show "Search unavailable" message

5. **Tracker API Rate Limiting:**
   - Phase 1: Accept failure
   - Future: Add retry logic or caching

### HTMX Debugging Tips

**Common Issues:**

1. **HTMX not triggering:**
   - Check browser console for HTMX errors
   - Verify HTMX script loaded (check Network tab)
   - Check `hx-trigger` syntax

2. **Results not appearing:**
   - Check `hx-target` selector exists
   - Check `hx-swap` strategy (innerHTML vs outerHTML)
   - Verify server returns HTML (not JSON)

3. **Debounce not working:**
   - Verify `delay:300ms` in `hx-trigger`
   - Check for JavaScript errors blocking HTMX

**HTMX DevTools:**
- Install HTMX browser extension for debugging
- Use `htmx.logAll()` in console for verbose logging

### Security Considerations

**Phase 1 Security:**

1. **Input Validation:**
   - Sanitize search query before passing to API
   - Prevent SQL/NoSQL injection in tracker clients
   - Limit query length to prevent DoS

2. **XSS Prevention:**
   - Use ScalaTags (auto-escapes by default)
   - Never use `raw()` with user input
   - Sanitize issue titles/descriptions from tracker APIs

3. **CSRF Protection:**
   - Phase 1: Read-only operations (search)
   - Phase 2: Will add CSRF tokens for worktree creation

4. **Authentication:**
   - Use existing environment variable approach
   - API tokens from env vars, not embedded in HTML

## Next Steps After Phase 1

**Phase 2: Worktree Creation from Modal**
- Add click handler to search results
- Implement `POST /api/worktrees/create` endpoint
- Reuse `start.scala` worktree creation logic
- Show success state with tmux attach instructions

**Phase 3: Error Handling**
- Detect existing worktrees in search results
- Show user-friendly error messages
- Add retry capability

**Phase 4: Concurrent Protection**
- Add server-side locking
- Disable UI during creation
- Handle timeout cleanup

## References

**Existing Code to Study:**

1. **Modal Patterns:**
   - No existing modals - use HTMX examples

2. **ScalaTags Patterns:**
   - `WorktreeListView.scala` - Complex HTML rendering
   - `ArtifactView.scala` - Page structure with styles

3. **Cask Routes:**
   - `CaskServer.scala` - Route definitions
   - Status code handling patterns

4. **Search Implementation:**
   - `LinearClient.scala` - API client pattern
   - `GitHubClient.scala` - CLI client pattern
   - `YouTrackClient.scala` - REST API pattern

**External References:**

1. **HTMX Documentation:**
   - https://htmx.org/docs/
   - Attributes: `hx-get`, `hx-trigger`, `hx-target`, `hx-swap`
   - Examples: https://htmx.org/examples/

2. **ScalaTags:**
   - https://www.lihaoyi.com/scalatags/
   - Text backend (server-side rendering)

3. **Cask Framework:**
   - https://com-lihaoyi.github.io/cask/
   - Routes and responses

---

**Ready to implement!** Start with Step 1 (Domain Model) and work through sequentially.
