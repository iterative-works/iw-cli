# Implementation Log: Spawn worktrees from dashboard

**Issue:** IW-79

This log tracks the evolution of implementation across phases.

---

## Phase 1: Modal UI + Issue Search (2026-01-02)

**What was built:**
- Domain: `IssueSearchResult.scala` - Value object for search results with id, title, status, url
- Application: `IssueSearchService.scala` - Search service with ID-based search and tracker URL building
- Presentation: `CreateWorktreeModal.scala` - Modal component with HTMX integration
- Presentation: `SearchResultsView.scala` - Results list rendering with empty state
- Infrastructure: Added 2 API endpoints to CaskServer (`/api/issues/search`, `/api/modal/create-worktree`)
- Infrastructure: Updated DashboardService with HTMX, button, modal container, CSS styles

**Decisions made:**
- ID-only search for Phase 1: Title/text search deferred to keep scope manageable
- HTMX for interactivity: No custom JavaScript needed, all interactions via HTMX attributes
- Return HTML from search API: Designed for HTMX swap, not JSON API
- 300ms debounce: Balances responsiveness with API call reduction

**Patterns applied:**
- Functional Core, Imperative Shell: Pure functions in IssueSearchService, effects in CaskServer
- ScalaTags for HTML: Type-safe HTML generation with composable fragments
- Higher-order functions for DI: fetchIssue function injected into search service for testability

**Testing:**
- Unit tests: 30 tests added (4 domain, 8 service, 8 modal, 10 results view)
- Integration tests: 0 (endpoints manually tested)
- All tests passing

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260102.md
- Findings: 0 critical, 3 warnings (code duplication acknowledged), 4 suggestions
- Verdict: APPROVED

**For next phases:**
- Available utilities: `IssueSearchService.search()` for issue lookup, `SearchResultsView.render()` for result display
- Extension points: Search result items are clickable (Phase 2 will add click handlers)
- Notes: Phase 2 will add `POST /api/worktrees/create` endpoint and result item click handling

**Files changed:**
```
A .iw/core/IssueSearchResult.scala
A .iw/core/IssueSearchService.scala
A .iw/core/presentation/views/CreateWorktreeModal.scala
A .iw/core/presentation/views/SearchResultsView.scala
A .iw/core/test/IssueSearchResultTest.scala
A .iw/core/test/IssueSearchServiceTest.scala
A .iw/core/test/CreateWorktreeModalTest.scala
A .iw/core/test/SearchResultsViewTest.scala
M .iw/core/CaskServer.scala
M .iw/core/DashboardService.scala
```

---

## Phase 2: Worktree Creation from Modal (2026-01-03)

**What was built:**
- Domain: `WorktreeCreationResult.scala` - Value object for creation result (issueId, worktreePath, tmuxSessionName, tmuxAttachCommand)
- Application: `WorktreeCreationService.scala` - Pure function orchestrating worktree creation with injected I/O dependencies
- Presentation: `CreationSuccessView.scala` - Success state with tmux command and copy button
- Presentation: `CreationLoadingView.scala` - Loading spinner with HTMX indicator
- Infrastructure: Added `POST /api/worktrees/create` endpoint to CaskServer
- Modified: `SearchResultsView.scala` - Added HTMX click handlers (hx-post, hx-vals, hx-target, hx-indicator)
- Modified: `CreateWorktreeModal.scala` - Added loading indicator and content swap target

**Decisions made:**
- Pure function with DI: WorktreeCreationService accepts all I/O operations as function parameters for testability
- Reuse existing domain: Leverages IssueId and WorktreePath opaque types from Phase 1
- HTMX content swap: Success/loading states replace modal body content via hx-target
- Sibling directory worktrees: Path uses "../project-IW-79" relative to main project

**Patterns applied:**
- Functional Core, Imperative Shell: Pure WorktreeCreationService, effects in CaskServer endpoint
- Either monad for errors: For-comprehension chains all creation steps with error short-circuiting
- ScalaTags HTML: Type-safe success/loading views
- HTMX indicators: Automatic show/hide via htmx-indicator class

**Testing:**
- Unit tests: 36 tests added
  - WorktreeCreationResult: 3 tests
  - WorktreeCreationService: 9 tests (happy path + all error paths)
  - CreationSuccessView: 12 tests
  - CreationLoadingView: 5 tests
  - SearchResultsView HTMX: 5 tests
  - CreateWorktreeModal updates: 3 tests
- Integration tests: 0 (endpoint manually testable)
- All 903 tests passing

**Code review:**
- Iterations: 1
- Review file: review-packet-phase-02.md
- Findings: 0 critical, 5 warnings, 10 suggestions
- Warnings: Primitive obsession (acceptable), CaskServer mixing concerns (acceptable), missing endpoint tests (future phase)
- Verdict: APPROVED

**For next phases:**
- Available utilities: `WorktreeCreationService.create()` for worktree creation
- Extension points: Error handling can be enhanced (Phase 3), concurrent protection (Phase 4)
- Notes: Basic errors returned; detailed error messages and "already has worktree" detection deferred to Phase 3

**Files changed:**
```
A .iw/core/application/WorktreeCreationService.scala
A .iw/core/domain/WorktreeCreationResult.scala
A .iw/core/presentation/views/CreationSuccessView.scala
A .iw/core/presentation/views/CreationLoadingView.scala
A .iw/core/test/WorktreeCreationServiceTest.scala
A .iw/core/test/WorktreeCreationResultTest.scala
A .iw/core/test/CreationSuccessViewTest.scala
A .iw/core/test/CreationLoadingViewTest.scala
M .iw/core/CaskServer.scala
M .iw/core/presentation/views/SearchResultsView.scala
M .iw/core/presentation/views/CreateWorktreeModal.scala
M .iw/core/test/SearchResultsViewTest.scala
M .iw/core/test/CreateWorktreeModalTest.scala
```

---

## Phase 3: Error Handling (2026-01-03)

**What was built:**
- Domain: `WorktreeCreationError.scala` - Scala 3 enum with 6 error cases (DirectoryExists, AlreadyHasWorktree, GitError, TmuxError, IssueNotFound, ApiError)
- Domain: `UserFriendlyError.scala` - Case class for user-facing errors with title, message, suggestion, canRetry flag, and issueId
- Domain: `WorktreeCreationError.toUserFriendly()` - Mapping function from domain errors to user-friendly errors with generic messages (no info disclosure)
- Presentation: `CreationErrorView.scala` - Error state view with retry/dismiss buttons, HTMX integration, issueId in hx-vals for retry
- Modified: `WorktreeCreationService.scala` - Returns `Either[WorktreeCreationError, WorktreeCreationResult]` with precondition checks
- Modified: `SearchResultsView.scala` - Added hasWorktree badge for existing worktrees
- Modified: `CaskServer.scala` - Error handling with domain error to HTTP status mapping (422, 409, 500, 404, 502)

**Decisions made:**
- Scala 3 enum for errors: Modern ADT pattern instead of sealed trait + case objects
- Generic error messages: Internal details (paths, stack traces) never exposed to users; logged server-side only
- Precondition checks before I/O: Directory existence and existing worktree checks happen before git/tmux operations
- HTTP status mapping: 422 for DirectoryExists/AlreadyHasWorktree, 500 for Git/Tmux errors, 404 for IssueNotFound, 502 for ApiError
- issueId in retry button: Enables proper retry without client-side state

**Patterns applied:**
- Scala 3 enum with parameters: Clean ADT definition with exhaustive pattern matching
- Error mapping to presentation: Domain errors translated to user-friendly messages at boundary
- Defense in depth: No information disclosure in user-facing errors, proper escaping of issueId in JSON

**Testing:**
- Unit tests: 55 tests added
  - WorktreeCreationErrorTest: 7 tests (enum construction + pattern matching)
  - UserFriendlyErrorTest: 4 tests (case class construction)
  - WorktreeCreationErrorMappingTest: 24 tests (mapping all error cases, verifying no info disclosure)
  - CreationErrorViewTest: 16 tests (rendering with/without retry, issueId in hx-vals)
  - WorktreeCreationServiceTest updates: 4 tests (error scenarios)
- All 230+ tests passing

**Code review:**
- Iterations: 2
- Review file: review-phase-03-20260103.md
- Iteration 1: 3 critical issues found (Scala 2 pattern instead of enum, missing issueId in retry, info disclosure)
- Iteration 2: All critical issues fixed, 0 critical, 10 warnings, 21 suggestions
- Verdict: APPROVED

**For next phases:**
- Available utilities: `WorktreeCreationError.toUserFriendly()` for error mapping, `CreationErrorView.render()` for error display
- Extension points: Rate limiting, CSRF protection, security headers
- Notes: Phase 4 could add concurrency protection (semaphore for worktree creation)

**Files changed:**
```
A .iw/core/domain/WorktreeCreationError.scala
A .iw/core/domain/UserFriendlyError.scala
A .iw/core/presentation/views/CreationErrorView.scala
A .iw/core/test/WorktreeCreationErrorTest.scala
A .iw/core/test/UserFriendlyErrorTest.scala
A .iw/core/test/WorktreeCreationErrorMappingTest.scala
A .iw/core/test/CreationErrorViewTest.scala
M .iw/core/CaskServer.scala
M .iw/core/IssueSearchResult.scala
M .iw/core/IssueSearchService.scala
M .iw/core/application/WorktreeCreationService.scala
M .iw/core/presentation/views/SearchResultsView.scala
M .iw/core/test/SearchResultsViewTest.scala
M .iw/core/test/WorktreeCreationServiceTest.scala
```

---
