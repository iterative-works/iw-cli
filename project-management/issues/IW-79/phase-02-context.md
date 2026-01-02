# Phase 2 Context: Worktree Creation from Modal

**Issue:** IW-79
**Phase:** 2 of 4
**Estimated Effort:** 4-5 hours
**Status:** Ready for Implementation

## Goals

This phase adds the actual worktree creation functionality to the modal built in Phase 1:

1. **Click handler on search results** - Results are now clickable to trigger creation
2. **Creation API endpoint** (`POST /api/worktrees/create`) - Orchestrates worktree creation
3. **Progress states** - Show "Creating..." while worktree is being created
4. **Success state** - Display tmux attach command with copy button
5. **Reuse existing start logic** - Leverage `start.scala` worktree creation code
6. **Tmux session creation** - Create detached session user can attach to

At the end of this phase, users can:
- Click on a search result to create a worktree
- See progress indicator during creation
- Get tmux attach command upon success
- Copy the command to clipboard
- Have the worktree appear in the dashboard list

**What's NOT in scope:**
- Detailed error messages (Phase 3)
- "Already has worktree" detection (Phase 3)
- Server-side locking for concurrent requests (Phase 4)
- UI disabling during creation (Phase 4)

## Scope

### In Scope

**UI Changes:**
- Make search result items clickable
- Add HTMX attributes for POST on click
- Add loading state ("Creating worktree...") in modal
- Add success state with tmux command and copy button
- Auto-refresh worktree list after creation

**API Endpoints:**
- `POST /api/worktrees/create` - Create worktree for given issue ID
- Request body: `{ "issueId": "IW-79" }`
- Returns HTML fragment with success message and tmux command

**Creation Logic:**
- Parse issue ID from request
- Load project configuration
- Fetch issue details from tracker
- Create git worktree (reuse existing logic)
- Create tmux session (detached)
- Register worktree in state file
- Return success response

**HTMX Integration:**
- `hx-post` on result items for creation trigger
- `hx-target` to replace modal content with status
- Loading indicator using HTMX classes
- Dashboard list refresh after success

### Out of Scope

- Directory-exists error handling (Phase 3)
- "Already has worktree" detection (Phase 3)
- API error retry logic (Phase 3)
- Server-side creation lock (Phase 4)
- UI disabling during creation (Phase 4)
- Double-click prevention (Phase 4)

## Dependencies

**From Phase 1:**
- `IssueSearchService` - Already implemented for search
- `IssueSearchResult` - Value object for search results
- `CreateWorktreeModal.scala` - Modal component
- `SearchResultsView.scala` - Results rendering
- HTMX integration in dashboard
- Modal CSS styles

**Existing Code to Reuse:**
- `.iw/commands/start.scala` - Worktree creation logic
- `GitWorktreeAdapter.createWorktree()` - Git worktree creation
- `TmuxAdapter.createSession()` - Tmux session creation
- `WorktreeRegistrationService.register()` - State file update
- `DashboardService.forceRefresh()` - Dashboard refresh trigger

**External Dependencies:**
- Git CLI for worktree operations
- Tmux for session creation
- Tracker APIs for issue details

## Technical Approach

### Architecture Overview

```
User clicks search result
    ↓
HTMX POSTs to /api/worktrees/create
    ↓
Server loads config and validates issue
    ↓
Server creates git worktree
    ↓
Server creates tmux session (detached)
    ↓
Server registers worktree in state
    ↓
Server returns success HTML
    ↓
HTMX swaps success into modal
    ↓
User sees tmux command with copy button
```

### Search Result Click Handler

Update `SearchResultsView.scala` to make items clickable:

```scala
def renderResultItem(result: IssueSearchResult): Frag =
  div(
    cls := "search-result-item",
    attr("hx-post") := "/api/worktrees/create",
    attr("hx-vals") := s"""{"issueId": "${result.id}"}""",
    attr("hx-target") := "#modal-body-content",
    attr("hx-swap") := "innerHTML",
    attr("hx-indicator") := "#creation-spinner",
    // ... existing content
  )
```

### Creation Endpoint Implementation

```scala
@cask.post("/api/worktrees/create")
def createWorktree(request: cask.Request): cask.Response[String] =
  val body = ujson.read(request.text())
  val issueId = body("issueId").str

  // Load configuration
  val configPath = os.pwd / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
  ConfigFileRepository.read(configPath) match
    case None =>
      cask.Response(
        renderError("Project not configured"),
        statusCode = 500
      )
    case Some(config) =>
      // Create worktree using existing logic
      createWorktreeForIssue(issueId, config) match
        case Right(result) =>
          cask.Response(
            renderSuccess(result),
            statusCode = 200
          )
        case Left(error) =>
          cask.Response(
            renderError(error),
            statusCode = 500
          )
```

### Worktree Creation Service

Create `WorktreeCreationService.scala` in application layer:

```scala
case class WorktreeCreationResult(
  issueId: String,
  worktreePath: String,
  tmuxSessionName: String,
  tmuxAttachCommand: String
)

object WorktreeCreationService:
  def create(
    issueId: String,
    config: ProjectConfiguration,
    fetchIssue: String => Either[String, IssueData],
    createWorktree: (String, String) => Either[String, Unit],
    createTmuxSession: String => Either[String, Unit],
    registerWorktree: WorktreeInfo => Either[String, Unit]
  ): Either[String, WorktreeCreationResult] =
    for
      issue <- fetchIssue(issueId)
      branchName = generateBranchName(issueId, issue.title, config)
      worktreePath = generateWorktreePath(branchName)
      _ <- createWorktree(worktreePath, branchName)
      sessionName = generateSessionName(branchName)
      _ <- createTmuxSession(sessionName)
      _ <- registerWorktree(WorktreeInfo(...))
    yield WorktreeCreationResult(
      issueId = issueId,
      worktreePath = worktreePath,
      tmuxSessionName = sessionName,
      tmuxAttachCommand = s"tmux attach -t $sessionName"
    )
```

### Success View Component

Create `CreationSuccessView.scala`:

```scala
object CreationSuccessView:
  def render(result: WorktreeCreationResult): Frag =
    div(
      cls := "creation-success",
      div(
        cls := "success-icon",
        raw("&#x2714;")  // Checkmark
      ),
      h3("Worktree Created!"),
      p(s"Created worktree for ${result.issueId}"),
      div(
        cls := "tmux-command-container",
        label("To attach to the session:"),
        div(
          cls := "command-box",
          code(result.tmuxAttachCommand),
          button(
            cls := "copy-btn",
            attr("onclick") := s"navigator.clipboard.writeText('${result.tmuxAttachCommand}')",
            "Copy"
          )
        )
      ),
      p(
        cls := "path-info",
        s"Worktree path: ${result.worktreePath}"
      ),
      button(
        cls := "btn-secondary close-modal-btn",
        attr("hx-get") := "/",
        attr("hx-target") := "#modal-container",
        attr("hx-swap") := "innerHTML",
        "Close"
      )
    )
```

### Loading State

Add loading indicator to modal:

```scala
// In CreateWorktreeModal
div(
  id := "creation-spinner",
  cls := "htmx-indicator",
  div(cls := "spinner"),
  span("Creating worktree...")
)
```

HTMX will automatically show this when request is in progress.

### Dashboard Refresh

After successful creation, trigger dashboard refresh:

```scala
// Add header to response
headers = Seq("HX-Trigger" -> "worktree-created")

// In dashboard, listen for event
div(
  id := "worktree-list",
  attr("hx-get") := "/api/dashboard/worktrees",
  attr("hx-trigger") := "worktree-created from:body"
)
```

Or simpler: use `hx-trigger="load"` on success view to refresh list.

## Files to Create/Modify

### Files to Create

1. **`.iw/core/application/WorktreeCreationService.scala`**
   - Pure function for worktree creation orchestration
   - Composes existing adapters
   - Returns `Either[String, WorktreeCreationResult]`

2. **`.iw/core/domain/WorktreeCreationResult.scala`**
   - Value object for creation result
   - Fields: issueId, worktreePath, tmuxSessionName, tmuxAttachCommand

3. **`.iw/core/presentation/views/CreationSuccessView.scala`**
   - Renders success state with tmux command
   - Includes copy button
   - Includes close button

4. **`.iw/core/presentation/views/CreationLoadingView.scala`**
   - Renders loading spinner
   - Shows "Creating worktree..." message

### Files to Modify

1. **`.iw/core/presentation/views/SearchResultsView.scala`**
   - Add HTMX click handlers to result items
   - Add `hx-post`, `hx-vals`, `hx-target` attributes

2. **`.iw/core/CaskServer.scala`**
   - Add `POST /api/worktrees/create` endpoint
   - Wire up WorktreeCreationService
   - Return HTML responses

3. **`.iw/core/DashboardService.scala`**
   - Add CSS for success state
   - Add CSS for loading spinner
   - Add CSS for copy button

4. **`.iw/core/presentation/views/CreateWorktreeModal.scala`**
   - Add loading indicator element
   - Update modal body structure for content swap

## Testing Strategy

### Unit Tests

**WorktreeCreationService Tests:**
```scala
test("create with valid issue succeeds"):
  // Mock all dependencies to succeed
  // Assert Right with correct result fields

test("create with invalid issue fails"):
  // Mock fetchIssue to fail
  // Assert Left with error message

test("create generates correct branch name"):
  // Test branch name generation logic
  // Assert matches pattern: {prefix}-{issueId}-{slug}

test("create generates correct tmux session name"):
  // Test session name from branch
  // Assert valid tmux session name
```

**CreationSuccessView Tests:**
```scala
test("renders success message"):
  // Render with result
  // Assert contains "Worktree Created!"

test("renders tmux command"):
  // Render with result
  // Assert contains tmux attach command

test("renders copy button"):
  // Render with result
  // Assert contains copy button with onclick

test("renders close button"):
  // Render with result
  // Assert contains close button with hx-get
```

### Integration Tests

**Creation Endpoint Tests:**
```scala
test("POST /api/worktrees/create returns 200 on success"):
  // POST with valid issue ID
  // Assert 200 status
  // Assert HTML contains success message

test("POST /api/worktrees/create returns 500 on invalid issue"):
  // POST with invalid issue ID
  // Assert 500 status
  // Assert HTML contains error message

test("POST /api/worktrees/create actually creates worktree"):
  // POST with valid issue ID
  // Assert worktree directory exists
  // Assert tmux session exists
  // Assert state file updated
```

### Manual Testing Checklist

**Creation Flow:**
- [ ] Click on search result triggers creation
- [ ] Loading spinner appears during creation
- [ ] Success message appears after creation
- [ ] Tmux command is correct
- [ ] Copy button copies command to clipboard
- [ ] Close button closes modal
- [ ] Dashboard list shows new worktree

**Worktree Verification:**
- [ ] Git worktree created in correct location
- [ ] Branch named correctly (prefix-issueId-slug)
- [ ] Tmux session created (detached)
- [ ] State file updated with worktree info
- [ ] Worktree appears in `git worktree list`

**Cross-Tracker Testing:**
- [ ] Works with Linear tracker
- [ ] Works with GitHub tracker
- [ ] Works with YouTrack tracker

## Acceptance Criteria

**Functional Requirements:**

1. **Clickable Results:**
   - [ ] Search results are clickable
   - [ ] Click triggers worktree creation
   - [ ] Visual feedback on hover (cursor pointer)

2. **Loading State:**
   - [ ] Spinner shown during creation
   - [ ] "Creating worktree..." message visible
   - [ ] User cannot click other results (optional, Phase 4)

3. **Success State:**
   - [ ] Success message with checkmark
   - [ ] Issue ID shown in message
   - [ ] Tmux attach command displayed
   - [ ] Copy button functional
   - [ ] Worktree path displayed
   - [ ] Close button dismisses modal

4. **Worktree Creation:**
   - [ ] Git worktree created successfully
   - [ ] Branch created from main/master
   - [ ] Branch name follows convention
   - [ ] Worktree in sibling directory

5. **Tmux Session:**
   - [ ] Session created detached
   - [ ] Session name matches branch
   - [ ] User can attach with provided command

6. **Dashboard Integration:**
   - [ ] Worktree registered in state
   - [ ] Dashboard list refreshes
   - [ ] New worktree appears in list

**Technical Requirements:**

1. **Code Reuse:**
   - [ ] Uses existing GitWorktreeAdapter
   - [ ] Uses existing TmuxAdapter
   - [ ] Uses existing WorktreeRegistrationService
   - [ ] No duplication of start.scala logic

2. **Pure Functions:**
   - [ ] WorktreeCreationService is pure
   - [ ] IO operations injected as functions
   - [ ] Easy to test in isolation

3. **Error Handling:**
   - [ ] Basic errors returned to client
   - [ ] Server doesn't crash on errors
   - [ ] Error HTML rendered properly

## Implementation Notes

### Order of Implementation

**Step 1: Domain Model (15 min)**
1. Create `WorktreeCreationResult.scala`
2. Unit tests for value object

**Step 2: Creation Service (1 hour)**
1. Create `WorktreeCreationService.scala`
2. Implement create method with function injection
3. Unit tests with mocked dependencies
4. Extract logic from `start.scala` if needed

**Step 3: View Components (45 min)**
1. Create `CreationSuccessView.scala`
2. Create `CreationLoadingView.scala`
3. Unit tests for rendering
4. Add CSS styles

**Step 4: Update Search Results (30 min)**
1. Add HTMX attributes to result items
2. Update modal to support content swap
3. Test click handling

**Step 5: API Endpoint (1 hour)**
1. Add POST route to CaskServer
2. Wire up WorktreeCreationService
3. Implement actual I/O operations
4. Integration tests

**Step 6: Dashboard Refresh (30 min)**
1. Add HX-Trigger header or auto-refresh
2. Test worktree appears in list
3. Verify state file updated

**Step 7: Manual Testing (45 min)**
1. Test complete flow
2. Test with each tracker
3. Fix any issues

### Key Patterns

**From start.scala to reuse:**
- Branch name generation: `generateBranchName()`
- Worktree path generation: `generateWorktreePath()`
- Tmux session naming: Same as worktree name
- Git worktree creation command
- State file registration

**HTMX Patterns:**
- `hx-post` for creation
- `hx-vals` for JSON body
- `hx-indicator` for loading state
- `HX-Trigger` header for events

### Edge Cases

1. **Issue title with special characters:**
   - Slugify title for branch name
   - Remove non-alphanumeric chars

2. **Very long issue title:**
   - Truncate branch name to reasonable length

3. **Worktree directory already exists:**
   - Phase 3 will handle this properly
   - Phase 2: Just return error

4. **Tmux session already exists:**
   - Try to create, handle error
   - Or reuse existing session

5. **Network timeout during issue fetch:**
   - Return error to user
   - Phase 3: Add retry

## References

**Existing Code:**
- `.iw/commands/start.scala` - Reference for creation logic
- `.iw/core/GitWorktreeAdapter.scala` - Git operations
- `.iw/core/TmuxAdapter.scala` - Tmux operations
- `.iw/core/WorktreeRegistrationService.scala` - State management

**HTMX:**
- POST requests: https://htmx.org/attributes/hx-post/
- Loading indicators: https://htmx.org/docs/#indicators
- Triggering events: https://htmx.org/docs/#response-headers

---

**Ready to implement!** Start with Step 1 (Domain Model) and work through sequentially.
