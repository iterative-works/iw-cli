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

The dashboard currently only displays worktrees that are already registered - it has no visibility into available issues that could be worked on. This creates a disconnect between issue discovery and worktree creation.

By adding the ability to spawn worktrees directly from the dashboard, users can:
- Browse available issues visually in the dashboard
- See which issues are available to work on vs already in-progress
- Create worktrees with a single click
- Streamline the workflow from issue discovery to development start

This provides a more integrated, visual workflow that reduces context switching and makes it easier to start work on new issues.

## User Stories

### Story 1: Display main repository with available issues on dashboard

```gherkin
Feature: Main repository issue listing
  As a developer
  I want to see available issues from my main repository in the dashboard
  So that I can discover what work is available

Scenario: Dashboard shows main repository section with issues
  Given I am in the main repository directory
  And there are 5 open issues in my GitHub repository
  And the dashboard server is running
  When I open the dashboard in my browser
  Then I see a "Main Repository" section at the top of the page
  And I see 5 issue cards displayed with title, status, and assignee
  And each issue card shows issue ID as a clickable link
  And issues without worktrees show a "Start Worktree" button
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
This is moderately complex because it requires:
- Fetching issues from GitHub/Linear/YouTrack APIs (existing clients available)
- Determining which issues already have worktrees (requires cross-referencing with registered worktrees)
- Rendering a new UI section in the dashboard
- Handling API failures gracefully (rate limits, auth errors)

Key challenges:
- GitHub API pagination (if repository has many issues)
- Caching strategy for issue list (how often to refresh?)
- Filtering logic (which issues to show: all open? assigned to me? specific labels?)

**Acceptance:**
- Dashboard displays main repository section distinct from worktree cards
- Issue list includes at minimum: issue ID, title, status, assignee (if any)
- Issues that already have active worktrees are visually distinguished
- Error states handled gracefully (API failures show friendly message)
- Issue data cached appropriately to avoid excessive API calls

---

### Story 2: Spawn worktree via dashboard button click

```gherkin
Feature: Worktree creation from dashboard
  As a developer
  I want to click a button to create a worktree for an issue
  So that I can start working without using the CLI

Scenario: Successfully create worktree from dashboard
  Given I see an issue "IW-79" without an existing worktree
  And the issue shows a "Start Worktree" button
  When I click the "Start Worktree" button
  Then a worktree is created at "../iw-cli-IW-79/"
  And a git branch "IW-79" is created
  And the worktree is registered with the dashboard
  And I see a success notification
  And the issue card updates to show "Open Worktree" instead of "Start Worktree"
```

**Estimated Effort:** 8-12h
**Complexity:** Complex

**Technical Feasibility:**
This is complex because it requires:
- New POST API endpoint to trigger worktree creation
- Server-side execution of git commands (same as CLI `start` command)
- Handling long-running operations (worktree creation can take seconds)
- Tmux session creation from server context (may need different approach)
- JavaScript in dashboard for button interaction and progress feedback
- Error handling for various failure scenarios (directory exists, git errors, etc.)

Key technical challenges:
1. **Async operation handling**: Worktree creation isn't instant - need to handle UI feedback
2. **Tmux session**: Current `start` command creates tmux session - how to handle from web context?
3. **Error scenarios**: Directory collision, branch already exists, git errors, etc.
4. **User notification**: How to inform user about success/failure and next steps?

**Acceptance:**
- Clicking button triggers worktree creation with same behavior as `./iw start`
- UI provides immediate feedback (loading state, progress indicator)
- Success case shows clear next steps (e.g., "Worktree created, attach with: tmux attach -t iw-cli-IW-79")
- Error cases display helpful error messages
- Page updates to reflect new worktree status without manual refresh

---

### Story 3: Filter and search available issues

```gherkin
Feature: Issue filtering and search
  As a developer
  I want to filter the issue list by status, assignee, and labels
  So that I can quickly find issues I want to work on

Scenario: Filter issues by assignee
  Given the main repository has 20 open issues
  And 5 issues are assigned to me
  And I see all 20 issues in the dashboard
  When I select "Assigned to me" from the filter dropdown
  Then I see only the 5 issues assigned to me
  And the filter selection persists across page refreshes

Scenario: Search issues by text
  Given the main repository has 20 open issues
  When I type "dashboard" in the search box
  Then I see only issues with "dashboard" in title or description
  And the search is case-insensitive
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward because:
- Client-side filtering (JavaScript) - no server changes needed
- UI components are simple (dropdown, search input)
- Filter state can be stored in URL params or localStorage

Main consideration: Should filtering happen client-side or server-side? Client-side is simpler for MVP but may not scale well if repository has hundreds of issues.

**Acceptance:**
- Filter dropdown with options: All, Assigned to me, Unassigned, Specific labels
- Search box filters issues by text match in title/description
- Filters can be combined (e.g., assigned to me AND contains "bug")
- Filter state persists across page refreshes
- Filtered count displayed (e.g., "Showing 5 of 20 issues")

---

### Story 4: Handle worktree creation errors gracefully

```gherkin
Feature: Error handling for worktree creation
  As a developer
  I want to see clear error messages when worktree creation fails
  So that I know what went wrong and how to fix it

Scenario: Worktree directory already exists
  Given issue "IW-79" has no registered worktree
  But directory "../iw-cli-IW-79/" already exists on disk
  When I click "Start Worktree" for issue "IW-79"
  Then I see error message "Directory iw-cli-IW-79 already exists"
  And I see suggestion "Remove the directory or use './iw open IW-79' if it's a valid worktree"
  And the button remains clickable (user can retry after fixing)

Scenario: Git branch already exists
  Given issue "IW-79" has no registered worktree
  But git branch "IW-79" already exists
  When I click "Start Worktree" for issue "IW-79"
  Then a worktree is created using the existing branch
  And I see info message "Using existing branch 'IW-79'"
  And the worktree is registered successfully
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward - mostly reusing existing error handling from `start.scala` CLI command. Main work is:
- Mapping error messages to user-friendly format
- UI design for error display (inline vs modal vs toast)
- Ensuring all error paths return structured error responses

**Acceptance:**
- All error scenarios from CLI `start` command are handled in web flow
- Error messages are user-friendly (not raw git errors)
- Errors suggest remediation steps where applicable
- UI returns to ready state after error (button re-enabled, etc.)
- Errors are logged server-side for debugging

---

### Story 5: Auto-refresh issue list when worktrees change

```gherkin
Feature: Live dashboard updates
  As a developer
  I want the issue list to update automatically when worktrees are created
  So that I don't have to manually refresh the page

Scenario: Issue status updates after worktree creation
  Given I have the dashboard open in my browser
  And I see issue "IW-79" with "Start Worktree" button
  When I click "Start Worktree" and creation succeeds
  Then within 2 seconds the issue card updates to show "Open Worktree" button
  And the new worktree appears in the worktree list section
  And I did not manually refresh the page

Scenario: Dashboard updates when worktree created via CLI
  Given I have the dashboard open in my browser
  And I see issue "IW-79" with "Start Worktree" button
  When another user (or I in terminal) runs "./iw start IW-79"
  Then within 10 seconds the dashboard reflects the new worktree
  And issue "IW-79" now shows "Open Worktree" button
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity requiring:
- Server-Sent Events (SSE) or WebSocket for live updates
- OR polling mechanism (simpler but less efficient)
- Client-side state management for issue list and worktree list
- Partial page updates without full refresh

For MVP, polling every 5-10 seconds is simpler than SSE/WebSocket and sufficient for this use case.

**Acceptance:**
- Dashboard polls server every 5-10 seconds for updates
- When server state changes, UI updates without page refresh
- Updates are incremental (don't cause visual "flash")
- Polling can be paused/resumed (e.g., pause when tab not visible)
- Manual refresh button available for immediate update

---

### Story 6: Open existing worktree from dashboard

```gherkin
Feature: Open existing worktree
  As a developer
  I want to open the tmux session for an existing worktree
  So that I can resume work on an issue

Scenario: Issue shows "Open Worktree" when worktree exists
  Given issue "IW-79" has an active registered worktree
  And tmux session "iw-cli-IW-79" exists
  When I view the dashboard
  Then issue "IW-79" shows "Open Worktree" button instead of "Start Worktree"
  And the button shows the worktree path as tooltip

Scenario: Click "Open Worktree" provides instructions
  Given issue "IW-79" has an active worktree with tmux session
  When I click "Open Worktree" for issue "IW-79"
  Then I see instructions: "In your terminal, run: tmux attach -t iw-cli-IW-79"
  And the tmux command is copyable with one click
  And I see alternative: "Or run: ./iw open IW-79"
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- No server-side action needed (can't attach to tmux from web browser)
- UI just displays instructions with tmux/CLI command
- Copy-to-clipboard is standard web API
- Logic to determine if worktree exists already implemented

The web browser cannot directly attach to a tmux session, so this story is about providing clear instructions to the user.

**Acceptance:**
- Issues with existing worktrees show "Open Worktree" button
- Button click displays modal with clear instructions
- Tmux attach command is pre-filled and copyable
- Alternative CLI command (`./iw open`) also shown
- Modal dismissible and doesn't block other interactions

---

### Story 7: Handle concurrent worktree creation attempts

```gherkin
Feature: Concurrent creation protection
  As a developer
  I want to be prevented from creating duplicate worktrees
  So that I don't create conflicts

Scenario: Button disabled during worktree creation
  Given I see issue "IW-79" with "Start Worktree" button
  When I click "Start Worktree"
  Then the button immediately changes to "Creating..." with loading spinner
  And the button is disabled (not clickable)
  And other "Start Worktree" buttons remain enabled

Scenario: Server rejects duplicate creation attempt
  Given I click "Start Worktree" for issue "IW-79" in one browser tab
  And creation is in progress (not yet complete)
  When I click "Start Worktree" for issue "IW-79" in another browser tab
  Then the second request fails with error "Worktree creation already in progress"
  And the first creation continues normally
```

**Estimated Effort:** 4-5h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity:
- Client-side: Disable button immediately on click
- Server-side: Track in-progress creations (in-memory map or file lock)
- Handle cleanup if creation fails mid-way
- Race condition handling between multiple server requests

**Acceptance:**
- UI prevents double-clicking same button
- Server prevents duplicate creation requests for same issue
- In-progress state cleaned up on success, failure, or timeout
- Multiple users can create worktrees for different issues concurrently
- Error messages distinguish between "already exists" vs "creation in progress"

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Display main repository with available issues

**Domain Layer:**
- `MainRepository` value object (repository name, tracker type, base URL)
- `AvailableIssue` entity (issue ID, title, status, assignee, hasWorktree flag)
- `IssueList` aggregate (collection of available issues with filtering)

**Application Layer:**
- `IssueListService.fetchAvailableIssues` - fetch issues from tracker API
- `IssueListService.enrichWithWorktreeStatus` - mark which issues have worktrees
- `IssueCacheService` - cache issue list with TTL (extend existing)

**Infrastructure Layer:**
- `GitHubClient.listIssues` - fetch open issues for repository
- `LinearClient.listIssues` - fetch issues from Linear workspace
- `YouTrackClient.listIssues` - fetch issues from YouTrack project
- Extend `StateRepository` to store issue list cache

**Presentation Layer:**
- `MainRepositoryView.render` - ScalaTags component for main repo section
- `AvailableIssueCard.render` - ScalaTags component for individual issue card
- CSS styles for main repository section
- API endpoint `GET /api/main-repository/issues` (optional - or render server-side)

---

### For Story 2: Spawn worktree via dashboard button click

**Domain Layer:**
- `WorktreeCreationRequest` value object (issue ID, repository path, tracker type)
- `WorktreeCreationResult` sum type (Success | AlreadyExists | GitError | etc.)
- `WorktreeCreationStatus` aggregate (tracks in-progress creations)

**Application Layer:**
- `WorktreeCreationService.createWorktree` - orchestrate worktree creation flow
- `WorktreeCreationService.getCreationStatus` - check if creation in progress
- Reuse existing `WorktreeRegistrationService.register`

**Infrastructure Layer:**
- Reuse `GitWorktreeAdapter.createWorktree`
- Reuse `GitWorktreeAdapter.createWorktreeForBranch`
- Adapt `TmuxAdapter` for server context (or skip tmux creation from web)
- Reuse `ServerClient` for registration (but called server-side)

**Presentation Layer:**
- API endpoint `POST /api/worktrees/:issueId/create`
- JavaScript for button click handling
- JavaScript for AJAX request to creation endpoint
- UI components for loading state, success notification, error display
- Update existing `AvailableIssueCard` to include "Start Worktree" button

---

### For Story 3: Filter and search available issues

**Domain Layer:**
- `IssueFilter` value object (filterType, filterValue)
- `IssueSearchQuery` value object (searchText)

**Application Layer:**
- `IssueListService.filterIssues` - apply filters to issue list
- `IssueListService.searchIssues` - text search across issues

**Infrastructure Layer:**
- No new infrastructure needed (client-side filtering for MVP)

**Presentation Layer:**
- `IssueFilterControls.render` - filter dropdown and search box components
- JavaScript for client-side filtering logic
- CSS for filter controls
- localStorage for persisting filter preferences

---

### For Story 4: Handle worktree creation errors gracefully

**Domain Layer:**
- `WorktreeCreationError` enum (DirectoryExists | BranchConflict | GitError | etc.)
- `ErrorMessage` value object (userMessage, technicalDetails, suggestedAction)

**Application Layer:**
- `WorktreeCreationService.mapErrorToUserMessage` - translate errors
- Extend `WorktreeCreationService.createWorktree` to return structured errors

**Infrastructure Layer:**
- No new components (reuse existing error handling)

**Presentation Layer:**
- `ErrorNotification.render` - UI component for displaying errors
- Update `POST /api/worktrees/:issueId/create` to return structured error JSON
- JavaScript for error display and retry logic

---

### For Story 5: Auto-refresh issue list when worktrees change

**Domain Layer:**
- `DashboardState` aggregate (issue list + worktree list + last updated timestamp)
- `StateChangeEvent` value object (what changed, when)

**Application Layer:**
- `DashboardStateService.getCurrentState` - snapshot of full dashboard state
- `DashboardStateService.hasStateChanged` - compare state versions

**Infrastructure Layer:**
- No new infrastructure (reuse existing state loading)

**Presentation Layer:**
- API endpoint `GET /api/dashboard/state` (or extend existing `/api/status`)
- JavaScript polling mechanism (setInterval)
- JavaScript state diffing and partial DOM updates
- Manual refresh button

---

### For Story 6: Open existing worktree from dashboard

**Domain Layer:**
- `WorktreeOpenCommand` value object (tmux attach command, iw open command)

**Application Layer:**
- `WorktreeOpenService.getOpenCommands` - generate commands for issue

**Infrastructure Layer:**
- No new infrastructure (read-only operation)

**Presentation Layer:**
- Update `AvailableIssueCard.render` to show "Open Worktree" when applicable
- `OpenWorktreeModal.render` - modal with instructions and copy buttons
- JavaScript for copy-to-clipboard functionality
- CSS for modal styling

---

### For Story 7: Handle concurrent worktree creation attempts

**Domain Layer:**
- `CreationLock` value object (issueId, startedAt, expiresAt)
- `CreationLockRegistry` aggregate (active locks with TTL)

**Application Layer:**
- `WorktreeCreationService.acquireLock` - attempt to acquire creation lock
- `WorktreeCreationService.releaseLock` - release lock after creation
- `WorktreeCreationService.cleanupExpiredLocks` - remove stale locks

**Infrastructure Layer:**
- Extend `StateRepository` to store creation locks
- OR in-memory lock registry (simpler but doesn't survive server restart)

**Presentation Layer:**
- Update button click handler to disable immediately
- Update `POST /api/worktrees/:issueId/create` to check lock before proceeding
- JavaScript for button state management (disabled, loading, error, success)

---

## Technical Risks & Uncertainties

### CLARIFY: Tmux session creation from web context

The existing `./iw start` CLI command creates a tmux session and attaches to it. When creating a worktree from the dashboard (web UI), we cannot directly create or attach to a tmux session.

**Questions to answer:**
1. Should the dashboard skip tmux session creation entirely?
2. Should we create the tmux session but not attach (user attaches manually)?
3. Should we provide a separate "open" workflow that instructs user to attach?
4. Should we create a daemon/background process that handles tmux creation?

**Options:**
- **Option A: Skip tmux creation from dashboard** - Dashboard only creates worktree and registers it. User manually creates tmux session with `./iw open <issue-id>` or `tmux new-session -s <name> -c <path>`.
  - Pros: Simple, no complexity around tmux from web context
  - Cons: Inconsistent with CLI workflow, user needs extra step

- **Option B: Create tmux session but don't attach** - Dashboard creates worktree AND tmux session, but user must attach manually.
  - Pros: Full parity with CLI, tmux session ready
  - Cons: Creates tmux session user may not use, requires server to run tmux commands

- **Option C: Defer to "open" command** - Dashboard creates worktree, shows instructions to run `./iw open <issue-id>` which handles tmux.
  - Pros: Reuses existing `open` command, clear user journey
  - Cons: User needs to switch to terminal anyway

**Impact:** Affects Story 2 (worktree creation) and Story 6 (opening worktrees). If we choose Option A or C, the web workflow is simpler but requires terminal interaction. Option B provides full automation but adds server-side tmux complexity.

**Recommendation:** Start with Option C for MVP - dashboard creates worktree and shows clear instructions to run `./iw open <issue-id>`. This keeps the web implementation simple while providing full functionality.

---

### CLARIFY: Issue list scope and filtering

The dashboard needs to fetch and display issues from the issue tracker. We need to decide what subset of issues to show.

**Questions to answer:**
1. Which issues should be shown by default (all open? assigned to me? specific labels?)?
2. How many issues to fetch (10? 50? 100? all open issues?)?
3. Should we paginate if there are many issues?
4. Which issue trackers should support this (GitHub? Linear? YouTrack? all?)?
5. How do we handle private issues or permission restrictions?

**Options:**
- **Option A: All open issues** - Fetch all open issues from repository
  - Pros: Complete view, no filtering complexity
  - Cons: May be too many issues (100+), slow API calls, overwhelming UI

- **Option B: Recent open issues (limit 50)** - Fetch most recently updated 50 open issues
  - Pros: Reasonable default, faster, manageable UI
  - Cons: May miss older issues, "most recent" depends on tracker API

- **Option C: Filtered by assignee/label** - Only show issues assigned to current user or with specific labels
  - Pros: Focused, relevant issues only
  - Cons: Requires user configuration, may hide available work, needs auth to determine "me"

**Impact:** Affects Story 1 (issue display) and Story 3 (filtering). Choice impacts API performance, caching strategy, and UI complexity.

**Recommendation:** Start with Option B for MVP - fetch most recent 50 open issues, with client-side filtering (Story 3) to narrow down. Add configuration for custom filters in future iteration.

---

### CLARIFY: Dashboard architecture - server-side vs client-side rendering

Currently the dashboard uses server-side rendering (ScalaTags generates HTML). Adding interactive features (buttons, filtering, auto-refresh) requires client-side JavaScript.

**Questions to answer:**
1. Should we keep server-side rendering and add progressive enhancement with JavaScript?
2. Should we move to client-side rendering (SPA) with API endpoints?
3. How much JavaScript complexity are we willing to add?
4. Should we use a JavaScript framework (React, Vue, Alpine.js) or vanilla JS?

**Options:**
- **Option A: Progressive enhancement** - Keep ScalaTags server rendering, add vanilla JavaScript for interactivity
  - Pros: Minimal change, no build tooling, works without JS
  - Cons: State management gets messy, mixing rendering paradigms

- **Option B: Hybrid approach** - Server renders initial page, client-side JS handles updates via API
  - Pros: Fast initial load, clean separation, good for incremental updates
  - Cons: Duplicate rendering logic, more complex state sync

- **Option C: Full client-side SPA** - Migrate dashboard to React/Vue SPA, server only provides JSON APIs
  - Pros: Rich interactivity, modern patterns, better UX
  - Cons: Major rewrite, build tooling, breaks without JS

**Impact:** Affects all stories. Choice determines development approach, testing strategy, and user experience.

**Recommendation:** Start with Option B (hybrid) for MVP - server-side rendering for initial page, JavaScript + JSON APIs for interactive features. Keep vanilla JS for MVP (no framework complexity).

---

### CLARIFY: Worktree creation as async operation

Worktree creation can take several seconds (git commands, file I/O). Web requests should ideally return quickly.

**Questions to answer:**
1. Should worktree creation be synchronous (block HTTP request until done)?
2. Should we use async/background job processing?
3. How do we handle timeouts?
4. How does the UI show progress?

**Options:**
- **Option A: Synchronous with timeout** - POST /api/worktrees/:issueId/create blocks until worktree created or timeout (30s)
  - Pros: Simple implementation, immediate result
  - Cons: Long HTTP requests, browser timeout risk, blocks server thread

- **Option B: Async with polling** - POST returns immediately with job ID, client polls GET /api/worktrees/:issueId/status for completion
  - Pros: Responsive, handles long operations, can show progress
  - Cons: More complex, requires job tracking, client polling logic

- **Option C: Async with WebSocket** - POST starts creation, WebSocket pushes status updates to client
  - Pros: Real-time updates, no polling overhead
  - Cons: Adds WebSocket complexity, may be overkill for this use case

**Impact:** Affects Story 2 (worktree creation) and Story 7 (concurrent creation). Choice impacts API design, client-side implementation, and perceived performance.

**Recommendation:** Start with Option A (synchronous with 30s timeout) for MVP - worktree creation is usually fast (5-10s). Add Option B (async with polling) if we find timeout issues in practice.

---

### CLARIFY: Authentication and authorization

Dashboard is currently unauthenticated (runs on localhost). If we're calling issue tracker APIs on behalf of the user, we need to consider auth.

**Questions to answer:**
1. Whose credentials should be used for issue tracker API calls?
2. Should dashboard require user login?
3. How do we handle multiple users on same machine?
4. Should we use personal access tokens from environment variables?

**Options:**
- **Option A: Use environment variables (current approach)** - Read LINEAR_API_TOKEN, YOUTRACK_API_TOKEN, use gh CLI for GitHub
  - Pros: Consistent with current CLI approach, no auth flow needed
  - Cons: Single user per machine, can't distinguish between users

- **Option B: User-specific tokens** - Dashboard asks for access token on first use, stores in browser localStorage
  - Pros: Multiple users can use same dashboard, user controls their token
  - Cons: Requires auth UI, token management, security concerns (XSS)

- **Option C: OAuth flow** - Dashboard redirects to GitHub/Linear/YouTrack for OAuth authentication
  - Pros: Proper auth, scoped permissions, secure
  - Cons: Complex implementation, requires callback URL, token refresh

**Impact:** Affects Story 1 (fetching issues) and all stories that interact with issue tracker. Choice impacts security model and multi-user support.

**Recommendation:** Keep Option A (environment variables) for MVP - this matches current iw-cli design (single user, local tool). Document that dashboard inherits the user's configured tokens. Defer multi-user support to future iteration.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Display main repository issues): 6-8 hours
- Story 2 (Spawn worktree via button): 8-12 hours
- Story 3 (Filter and search issues): 4-6 hours
- Story 4 (Error handling): 3-4 hours
- Story 5 (Auto-refresh): 6-8 hours
- Story 6 (Open existing worktree): 3-4 hours
- Story 7 (Concurrent creation protection): 4-5 hours

**Total Range:** 34 - 47 hours

**Confidence:** Medium

**Reasoning:**
- **Story 2 is the most complex** (8-12h) - involves server-side orchestration, error handling, async behavior, and UI state management. Uncertainty around tmux handling and async approach affects this estimate.
- **Story 1 is moderately complex** (6-8h) - requires integrating with 3 different tracker APIs (GitHub/Linear/YouTrack), implementing caching, and designing new UI components.
- **Story 5 has moderate uncertainty** (6-8h) - depends on whether we implement polling (simpler) vs WebSocket/SSE (more complex) for live updates.
- **Stories 3, 4, 6, 7 are more straightforward** (3-6h each) - mostly UI work or extending existing patterns.
- **CLARIFY decisions significantly impact estimates** - Async vs sync for Story 2 could change estimate by 50%. Choice of rendering approach (SSR vs client-side) affects all stories.
- **First-time JavaScript work** - Current codebase is Scala-heavy. Adding substantial JavaScript for interactivity may take longer than estimated if team is less familiar with client-side patterns.
- **Testing overhead not included** - Estimates assume unit tests for services, but E2E tests for browser interactions could add 25-50% more time.

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure domain logic, value objects, business rules
2. **Integration Tests**: API endpoints, issue tracker clients, state persistence
3. **E2E Scenario Tests**: Automated browser tests verifying the Gherkin scenario

**Story-Specific Testing Notes:**

**Story 1: Display main repository issues**
- Unit: Test issue list filtering logic, worktree status enrichment
- Integration: Test GitHub/Linear/YouTrack API clients with mock responses
- E2E: Browser test loads dashboard, verifies main repository section renders with correct issue count
- Manual: Verify with real GitHub repository to check styling and data accuracy

**Story 2: Spawn worktree via button**
- Unit: Test `WorktreeCreationService.createWorktree` pure logic
- Integration: Test POST /api/worktrees/:issueId/create endpoint, verify worktree created on disk
- E2E: Browser test clicks "Start Worktree" button, polls until completion, verifies success notification
- Manual: Test error scenarios (directory exists, no disk space, etc.)

**Story 3: Filter and search issues**
- Unit: Test filtering algorithms (assignee match, text search)
- Integration: Not applicable (client-side only)
- E2E: Browser test applies filter, verifies correct issues shown/hidden
- Manual: Verify filter persistence across page refresh

**Story 4: Error handling**
- Unit: Test error message mapping
- Integration: Test API returns proper error codes and messages
- E2E: Browser test triggers each error scenario, verifies error display
- Manual: Verify error messages are user-friendly and actionable

**Story 5: Auto-refresh**
- Unit: Test state diffing logic
- Integration: Test /api/dashboard/state endpoint returns current state
- E2E: Browser test verifies dashboard updates within 10s when worktree created externally
- Manual: Test with dashboard open while running CLI commands

**Story 6: Open existing worktree**
- Unit: Test command generation logic
- Integration: Not applicable (no server changes)
- E2E: Browser test clicks "Open Worktree", verifies modal with correct commands
- Manual: Verify copy-to-clipboard works, commands are correct

**Story 7: Concurrent creation protection**
- Unit: Test lock acquisition/release logic
- Integration: Test concurrent POST requests to same endpoint
- E2E: Browser test simulates double-click, verifies only one creation proceeds
- Manual: Test with multiple browser tabs

**Test Data Strategy:**
- Use test GitHub repository with known issues for E2E tests
- Mock issue tracker API responses for integration tests (faster, no API rate limits)
- Create test fixtures for different issue states (open, closed, assigned, etc.)
- Use temporary directories for worktree creation tests (cleanup after each test)

**Regression Coverage:**
- Existing worktree cards must still work (Story 1 shouldn't break current dashboard)
- CLI `./iw start` must still work (Story 2 doesn't replace CLI, supplements it)
- Dashboard should handle missing issue tracker credentials gracefully
- Dashboard should work with all three tracker types (GitHub, Linear, YouTrack)

## Deployment Considerations

### Database Changes
No traditional database - state stored in JSON file (StateRepository). Changes needed per story:

**Story 1 migrations:**
- Add `issueListCache: Map[String, CachedIssueList]` to ServerState
- CachedIssueList includes: issues array, fetchedAt timestamp, trackerType

**Story 2 migrations:**
- No schema changes (reuses existing worktree registration)

**Story 5 migrations:**
- Add `stateVersion: Long` or `lastModified: Instant` to ServerState for change detection

**Story 7 migrations:**
- Add `creationLocks: Map[String, CreationLock]` to ServerState

All changes are backward-compatible (new fields with defaults). Existing state files will work, new fields will be empty/default initially.

### Configuration Changes
- No new environment variables required (reuses existing LINEAR_API_TOKEN, YOUTRACK_API_TOKEN, gh auth)
- No new config file settings required
- May add optional configuration later for issue list limit, cache TTL, etc.

### Rollout Strategy
Feature can be deployed incrementally story-by-story:

1. **Story 1 (issue display)**: Deploy to add main repository section - non-breaking, purely additive
2. **Story 2 (worktree creation)**: Deploy to add button functionality - no impact on users who don't click button
3. **Story 3 (filtering)**: Deploy to add filter controls - optional feature, doesn't affect existing functionality
4. **Story 4 (error handling)**: Deploy to improve error messages - enhancement only
5. **Story 5 (auto-refresh)**: Deploy to add polling - minimal impact (adds HTTP requests every 10s)
6. **Story 6 (open worktree)**: Deploy to add modal - no impact on existing flows
7. **Story 7 (concurrent protection)**: Deploy to add locking - enhancement only

Each story is independently deployable. No feature flags needed (features are additive).

### Rollback Plan
If critical bug found:
1. Stop dashboard server (`pkill -f dashboard`)
2. Checkout previous git commit
3. Restart dashboard

State file changes are backward-compatible, so rollback won't corrupt state. Worst case: delete `.iw/server-state.json` to reset (worktrees will re-register on next CLI use).

## Dependencies

### Prerequisites
Before starting Story 1:
- Dashboard server infrastructure (CaskServer) - **EXISTS**
- Issue tracker API clients (GitHub, Linear, YouTrack) - **EXISTS** (fetch single issue, need to add list issues)
- ScalaTags view rendering - **EXISTS**
- Server state management (StateRepository) - **EXISTS**

### Story Dependencies
Sequential dependencies:
- **Story 2 depends on Story 1** - Need issue cards in UI before adding "Start Worktree" button
- **Story 4 depends on Story 2** - Error handling extends worktree creation
- **Story 6 depends on Story 1** - "Open Worktree" button shown on issue cards
- **Story 7 depends on Story 2** - Concurrent protection wraps worktree creation

Can be parallelized:
- **Story 3 (filtering) is independent** - Can implement anytime after Story 1
- **Story 5 (auto-refresh) is independent** - Can implement anytime, doesn't depend on specific features

### External Blockers
None identified. All dependencies are internal to iw-cli codebase.

Potential external risks:
- GitHub/Linear/YouTrack API rate limits (mitigated by caching)
- API authentication failures (user's token expired/invalid)
- Breaking API changes from tracker providers (low risk, stable APIs)

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Display main repository issues** - Foundation for everything else. Establishes UI structure and issue data flow.
2. **Story 2: Spawn worktree via button** - Core value delivery. Enables the primary use case.
3. **Story 4: Error handling** - Immediately follow Story 2 to ensure robustness before adding more features.
4. **Story 6: Open existing worktree** - Natural follow-up to worktree creation, completes the worktree lifecycle.
5. **Story 3: Filter and search** - Polish for issue discovery, improves usability with larger issue lists.
6. **Story 5: Auto-refresh** - UX enhancement, makes dashboard feel more "live".
7. **Story 7: Concurrent creation protection** - Edge case protection, nice-to-have for multi-user scenarios.

**Iteration Plan:**

- **Iteration 1 (Stories 1-2)**: 14-20 hours
  - Deliverable: Dashboard shows available issues, users can create worktrees with button click
  - Value: Core feature complete - integrated issue discovery and worktree creation
  - Risk: Highest complexity stories, may need CLARIFY decisions resolved first

- **Iteration 2 (Stories 4, 6)**: 6-8 hours
  - Deliverable: Error handling robust, users can open existing worktrees
  - Value: Feature is production-ready, handles edge cases gracefully
  - Risk: Low, mostly extending existing functionality

- **Iteration 3 (Stories 3, 5, 7)**: 14-19 hours
  - Deliverable: Filtering, auto-refresh, concurrent protection
  - Value: Polish and UX improvements, handles scale and edge cases
  - Risk: Medium, some uncertainty around auto-refresh implementation

## Documentation Requirements

- [x] Gherkin scenarios serve as living documentation (included in this analysis)
- [ ] API documentation for new endpoints:
  - `GET /api/main-repository/issues` - List available issues
  - `POST /api/worktrees/:issueId/create` - Create worktree
  - `GET /api/dashboard/state` - Get current dashboard state (for polling)
- [ ] Architecture documentation:
  - Document hybrid rendering approach (server + client)
  - Document state synchronization strategy (polling vs WebSocket)
  - Document error handling patterns
- [ ] User-facing docs:
  - Add dashboard usage section to README
  - Document difference between CLI and web workflows
  - Document tmux session handling from web context
  - Add troubleshooting section for common errors

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. **CRITICAL**: Resolve CLARIFY markers before proceeding:
   - Decide on tmux creation strategy (Story 2)
   - Define issue list scope and filtering defaults (Story 1)
   - Choose rendering architecture (server vs client)
   - Decide on sync vs async worktree creation (Story 2)
   - Confirm authentication approach (environment variables)
2. Review estimates and story scope with Michal
3. Run `/iterative-works:ag-create-tasks IW-79` to map stories to implementation phases
4. Run `/iterative-works:ag-implement IW-79` for iterative story-by-story implementation
