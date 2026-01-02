# Story-Driven Analysis: Spawn worktrees from dashboard

**Issue:** IW-79
**Created:** 2026-01-02
**Status:** Draft
**Classification:** Feature

## Problem Statement

Currently, users must use the CLI command `./iw start <issue-id>` to create worktrees for issues they want to work on. This requires:
1. Knowing the issue ID beforehand
2. Switching to terminal
3. Running the CLI command
4. Waiting for worktree creation and tmux session setup

The dashboard currently displays worktrees that are already registered - it manages work-in-progress. Issue discovery and management belongs in the issue tracker (GitHub/Linear/YouTrack), not the dashboard.

By adding a "Create Worktree" button with issue search, users can:
- Stay in the dashboard when starting new work
- Search for issues by ID or title without leaving the browser
- Create worktrees with minimal friction
- Keep the dashboard focused on work-in-progress management

This provides a streamlined workflow that reduces context switching while respecting the separation of concerns: issue trackers manage issues, dashboard manages worktrees.

## User Stories

### Story 1: Create worktree via modal search

```gherkin
Feature: Worktree creation via modal search
  As a developer
  I want to search for an issue and create a worktree from the dashboard
  So that I can start working without switching to the CLI

Scenario: Open create worktree modal
  Given the dashboard is open in my browser
  When I click the "Create Worktree" button in the header
  Then a modal dialog opens
  And I see a search input field with placeholder "Search by issue ID or title..."
  And I see a list of recent/suggested issues (top 5)

Scenario: Search for issue by ID
  Given the create worktree modal is open
  When I type "IW-79" in the search field
  Then I see issue "IW-79" in the results
  And the result shows issue title and status

Scenario: Search for issue by title
  Given the create worktree modal is open
  When I type "dashboard" in the search field
  Then I see issues containing "dashboard" in their title
  And results are sorted by relevance

Scenario: Create worktree from search result
  Given I see issue "IW-79" in the search results
  When I click on the issue
  Then the modal shows "Creating worktree..." with a loading indicator
  And after creation completes, I see a success message
  And I see instructions: "Run: tmux attach -t iw-cli-IW-79"
  And a "Copy" button copies the command to clipboard
  And the modal can be dismissed
  And the worktree appears in the dashboard list
```

**Estimated Effort:** 8-10h
**Complexity:** Moderate

**Technical Feasibility:**
This is moderately complex because it requires:
- New UI component (modal with search)
- Issue search API endpoint (calls tracker API)
- Worktree creation API endpoint (reuses existing `start` logic)
- HTMX for modal interactions and dynamic search results
- Debounced search to avoid excessive API calls

Key considerations:
- Search should work across all tracker types (GitHub/Linear/YouTrack)
- Debounce search input (300ms) to avoid API spam
- Show loading states during search and creation
- Handle case where issue already has a worktree

**Acceptance:**
- "Create Worktree" button visible in dashboard header
- Modal opens on click with search input
- Search works by issue ID (exact match) and title (partial match)
- Results show within 500ms of typing pause
- Clicking result triggers worktree creation
- Success shows tmux attach command with copy button
- Error states handled gracefully (see Story 2)
- Modal dismissible at any point

---

### Story 2: Handle worktree creation errors gracefully

```gherkin
Feature: Error handling for worktree creation
  As a developer
  I want to see clear error messages when worktree creation fails
  So that I know what went wrong and how to fix it

Scenario: Worktree directory already exists
  Given I search for issue "IW-79" in the create modal
  And directory "../iw-cli-IW-79/" already exists on disk
  When I click on issue "IW-79" to create worktree
  Then I see error message "Directory iw-cli-IW-79 already exists"
  And I see suggestion "Remove the directory or register existing worktree with './iw open IW-79'"
  And I can try again or dismiss the modal

Scenario: Issue already has a worktree
  Given issue "IW-79" already has a registered worktree
  When I search for "IW-79" in the create modal
  Then the issue shows "Already has worktree" badge
  And clicking it shows "Open existing worktree?" option
  And I can copy the tmux attach command directly

Scenario: Issue not found
  Given I type "INVALID-999" in the search field
  When the search completes
  Then I see "No issues found" message
  And I can modify my search

Scenario: API error during search
  Given the issue tracker API is unavailable
  When I type in the search field
  Then I see "Could not search issues. Check your connection." message
  And I can retry the search
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward - mostly mapping existing error handling from `start.scala` CLI command to user-friendly UI messages.

**Acceptance:**
- All error scenarios display user-friendly messages
- Errors include remediation suggestions where applicable
- Modal remains usable after errors (can retry or dismiss)
- Existing worktrees detected and handled gracefully

---

### Story 3: Handle concurrent worktree creation attempts

```gherkin
Feature: Concurrent creation protection
  As a developer
  I want to be prevented from creating duplicate worktrees
  So that I don't create conflicts

Scenario: UI disabled during creation
  Given I click on issue "IW-79" to create worktree
  Then the modal shows "Creating..." state
  And I cannot click other issues during creation
  And I cannot close the modal until creation completes or fails

Scenario: Server rejects duplicate creation
  Given worktree creation for "IW-79" is in progress
  When another request tries to create worktree for "IW-79"
  Then the second request fails with "Creation already in progress"
  And the first creation continues normally
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward:
- Client-side: Disable interactions during creation
- Server-side: In-memory lock per issue ID during creation
- Lock released on success, failure, or timeout (30s)

**Acceptance:**
- UI prevents interaction during worktree creation
- Server prevents concurrent creation for same issue
- Lock cleaned up on completion or timeout

---

## Architectural Sketch

### For Story 1: Create worktree via modal search

**Domain Layer:**
- `IssueSearchQuery` value object (search text, tracker type)
- `IssueSearchResult` value object (issue ID, title, status, hasWorktree)
- `WorktreeCreationRequest` value object (issue ID, tracker type)
- `WorktreeCreationResult` sum type (Success | AlreadyExists | Error)

**Application Layer:**
- `IssueSearchService.search(query)` - search issues in tracker
- `WorktreeCreationService.create(request)` - orchestrate worktree creation
- Reuse existing `WorktreeRegistrationService.register`

**Infrastructure Layer:**
- `GitHubClient.searchIssues` - search GitHub issues
- `LinearClient.searchIssues` - search Linear issues
- `YouTrackClient.searchIssues` - search YouTrack issues
- Reuse `GitWorktreeAdapter.createWorktree`
- Reuse `TmuxAdapter` for session creation

**Presentation Layer:**
- `CreateWorktreeButton.render` - button in dashboard header
- `CreateWorktreeModal.render` - modal with search and results
- `IssueSearchResult.render` - individual search result item
- API endpoint `GET /api/issues/search?q=...` - search issues
- API endpoint `POST /api/worktrees/create` - create worktree
- HTMX for modal interactions

---

### For Story 2: Error handling

**Domain Layer:**
- `WorktreeCreationError` enum (DirectoryExists | AlreadyHasWorktree | GitError | etc.)
- `UserFriendlyError` value object (message, suggestion)

**Application Layer:**
- `WorktreeCreationService.mapError` - translate errors to user-friendly format

**Presentation Layer:**
- Error display in modal
- "Already has worktree" badge on search results

---

### For Story 3: Concurrent protection

**Domain Layer:**
- `CreationLock` value object (issueId, startedAt)

**Application Layer:**
- `WorktreeCreationService.acquireLock` / `releaseLock`

**Infrastructure Layer:**
- In-memory lock registry (Map[IssueId, CreationLock])

**Presentation Layer:**
- Disabled state in modal during creation

---

## Technical Decisions (Resolved)

### Decision 1: Tmux session creation from web context

**Decision:** Create tmux session but don't attach

The dashboard will create both the worktree AND the tmux session (detached). User attaches manually with `tmux attach -t <session>` or `./iw open <issue-id>`.

**Rationale:** Full parity with CLI workflow. The server can run `tmux new-session -d` (detached mode) without issues.

---

### Decision 2: Issue search approach

**Decision:** On-demand modal search (not upfront listing)

User clicks "Create Worktree" button, modal opens with search box. Issues are fetched only when user searches. Shows top 5 recent issues as initial suggestions.

**Rationale:**
- Dashboard manages work-in-progress, not issue discovery
- Issue management belongs in issue tracker
- On-demand search is simpler and avoids upfront API calls
- Search-first UX is faster for users who know what they want

---

### Decision 3: Dashboard architecture

**Decision:** Hybrid with HTMX

Server renders HTML via ScalaTags. HTMX handles modal and search interactions:
- `hx-get` for search results
- `hx-post` for worktree creation
- `hx-trigger="keyup changed delay:300ms"` for debounced search

**Rationale:** HTMX is perfect for this use case - minimal JavaScript, server-rendered HTML.

---

### Decision 4: Worktree creation timing

**Decision:** Synchronous with 30s timeout

POST `/api/worktrees/create` blocks until worktree is created or timeout.

**Rationale:** Worktree creation typically takes 5-10 seconds. Synchronous is simpler.

---

### Decision 5: Authentication

**Decision:** Use environment variables (existing approach)

Dashboard uses same credentials as CLI: `LINEAR_API_TOKEN`, `YOUTRACK_API_TOKEN`, `gh` CLI auth.

**Rationale:** Local development tool, not multi-user web app.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Modal search + creation): 8-10 hours
- Story 2 (Error handling): 2-3 hours
- Story 3 (Concurrent protection): 2-3 hours

**Total Range:** 12 - 16 hours

**Confidence:** Medium-High

**Reasoning:**
- Simpler scope than original analysis (no issue listing, no auto-refresh)
- Reuses existing worktree creation logic from CLI
- HTMX simplifies client-side interactions
- Main complexity is search API integration across 3 tracker types

---

## Testing Approach

**Story 1: Modal search + creation**
- Unit: Test search query parsing, result mapping
- Integration: Test search endpoints with mock tracker responses
- Integration: Test worktree creation endpoint
- E2E: Open modal, search, create worktree, verify success
- Manual: Test with real GitHub/Linear/YouTrack

**Story 2: Error handling**
- Unit: Test error mapping
- Integration: Test error responses from API
- E2E: Trigger each error scenario, verify messages

**Story 3: Concurrent protection**
- Unit: Test lock acquisition/release
- Integration: Test concurrent API requests
- E2E: Double-click prevention

---

## Implementation Sequence

**Recommended Phase Order:**

1. **Phase 1: Modal UI + Search** (4-5h)
   - Add "Create Worktree" button to dashboard header
   - Create modal component with search input
   - Implement search API endpoint
   - Wire up HTMX for search interactions

2. **Phase 2: Worktree Creation** (4-5h)
   - Implement creation API endpoint
   - Reuse existing `start` command logic
   - Add success/error states to modal
   - Show tmux attach instructions

3. **Phase 3: Error Handling** (2-3h)
   - Map all error scenarios to user-friendly messages
   - Handle "already has worktree" case
   - Add retry capability

4. **Phase 4: Concurrent Protection** (2-3h)
   - Add server-side locking
   - Disable UI during creation
   - Handle timeout/cleanup

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. Run `/iterative-works:ag-create-tasks IW-79` to generate phase-based tasks
2. Run `/iterative-works:ag-implement IW-79` for iterative implementation
