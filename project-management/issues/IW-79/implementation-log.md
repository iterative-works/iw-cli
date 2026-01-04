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

## Human Review: Phase 4 - UX Gap Identified (2026-01-03)

**Reviewer:** Michal

**Context:** Reviewing the Create Worktree modal functionality after Phase 4 completion.

**Issue identified:**
The "Create Worktree" button in the dashboard header uses the server's current working directory config (`os.pwd/.iw/config.conf`). This doesn't make sense in a multi-project dashboard context - the dashboard can show worktrees from multiple projects, but the create button only works for one project.

**Root cause:**
The implementation assumed single-project context, but the dashboard manages worktrees across multiple projects. Each registered worktree has its own `trackerType` and `team`, but the modal ignores this and uses hardcoded server CWD config.

**Decision made:**
Add Phase 5 to fix this by:
1. Deriving "main projects" from registered worktree paths
2. Showing a main projects section with per-project create buttons
3. Scoping the modal and search to the selected project
4. Removing the global create button from header

**Impact on stories:**
- Story 4 added: "Show main projects with create buttons"
- Phases 1-4 remain valid but Phase 5 will modify the modal to be project-scoped

**Action items:**
- [x] Add Story 4 with Gherkin scenarios to analysis.md
- [x] Add Phase 5 to tasks.md
- [ ] Generate phase-05-context.md and implement

---

## Phase 4: Concurrent Creation Protection (2026-01-03)

**What was built:**
- Domain: `CreationLock.scala` - Case class for tracking in-progress creation with issueId and startedAt timestamp
- Infrastructure: `CreationLockRegistry.scala` - Thread-safe lock registry using ConcurrentHashMap with tryAcquire, release, isLocked, and cleanupExpired methods
- Domain: Added `CreationInProgress(issueId)` case to WorktreeCreationError enum
- Domain: Added error mapping for CreationInProgress → UserFriendlyError with retry capability
- Application: Added `createWithLock()` method to WorktreeCreationService wrapping create with lock acquisition/release
- Infrastructure: Updated CaskServer to use createWithLock and map CreationInProgress to HTTP 423 Locked
- Presentation: Added HTMX hx-on::before-request/after-request attributes to SearchResultsView for UI disabling
- Presentation: Added `.disabled` CSS class to DashboardService with pointer-events:none and opacity:0.5

**Decisions made:**
- ConcurrentHashMap for thread safety: Uses putIfAbsent for atomic lock acquisition
- try-finally for guaranteed release: Lock always released even on creation failure
- HTTP 423 Locked status: Standard HTTP status code for "resource temporarily locked"
- HTMX event handlers for UI: No custom JavaScript needed, CSS class toggle on request lifecycle
- Global registry object: Acceptable for single-server local tool, clear() method for test isolation

**Patterns applied:**
- Thread-safe concurrent collection: ConcurrentHashMap.putIfAbsent for atomic check-and-set
- Resource management with try-finally: Ensures lock release regardless of success/failure
- HTMX progressive enhancement: Server-side rendering with client-side state management
- Functional Core, Imperative Shell: Pure domain models, effects contained in registry/server

**Testing:**
- Unit tests: 26 tests added
  - CreationLockTest: 4 tests (construction, field access, equality)
  - CreationLockRegistryTest: 11 tests (acquire, release, isLocked, cleanupExpired, isolation)
  - WorktreeCreationServiceTest: 4 tests (createWithLock lock/release semantics)
  - WorktreeCreationErrorMappingTest: 5 tests (CreationInProgress mapping)
  - SearchResultsViewTest: 2 tests (HTMX UI state attributes)
- All tests passing

**Code review:**
- Iterations: 1
- Review file: review-phase-04-20260103.md
- Findings: 0 critical, 5 warnings, 9 suggestions
- Warnings: Global mutable state (acceptable), direct infrastructure import (acceptable), no automatic cleanup trigger (noted), no concurrent stress test (noted), test isolation via clear() (acceptable)
- Verdict: APPROVED

**For next phases:**
- Available utilities: `CreationLockRegistry` for any operation needing mutual exclusion, `createWithLock()` for protected creation
- Extension points: Lock cleanup scheduling, lock timeout configuration
- Notes: Feature complete - all 4 phases implemented for IW-79

**Files changed:**
```
A .iw/core/domain/CreationLock.scala
A .iw/core/infrastructure/CreationLockRegistry.scala
A .iw/core/test/CreationLockTest.scala
A .iw/core/test/CreationLockRegistryTest.scala
M .iw/core/domain/WorktreeCreationError.scala
M .iw/core/application/WorktreeCreationService.scala
M .iw/core/CaskServer.scala
M .iw/core/presentation/views/SearchResultsView.scala
M .iw/core/DashboardService.scala
M .iw/core/test/WorktreeCreationErrorTest.scala
M .iw/core/test/WorktreeCreationErrorMappingTest.scala
M .iw/core/test/WorktreeCreationServiceTest.scala
M .iw/core/test/SearchResultsViewTest.scala
```

---

## Phase 5: Main Projects Listing (2026-01-04)

**What was built:**
- Domain: `MainProject.scala` - Case class with path, projectName, trackerType, team; path derivation logic
- Application: `MainProjectService.scala` - deriveFromWorktrees for extracting unique projects from worktrees, loadConfig for arbitrary paths
- Presentation: `MainProjectsView.scala` - Renders main projects section with create buttons per project
- Modified: `CreateWorktreeModal.scala` - Accepts optional project path parameter
- Modified: `SearchResultsView.scala` - Includes project path in HTMX hx-vals for creation context
- Modified: `DashboardService.scala` - Removed global create button, added main projects section with CSS
- Modified: `CaskServer.scala` - Added project parameter to modal and search endpoints

**Decisions made:**
- Path derivation via regex: `-([A-Z]+-\d+|\d+)$` matches IW-79, IWLE-123, and numeric 123 formats
- Project-scoped modal flow: Each main project has its own create button that opens modal with project context
- URL encoding for project path: Project paths are URL-encoded in query parameters
- Config loading on-demand: No caching, reload config from project path each time

**Patterns applied:**
- Functional Core, Imperative Shell: Pure deriveMainProjectPath function, effects in CaskServer
- Dependency Injection via function params: loadConfig function injected for testability
- ScalaTags for HTML: Type-safe main projects section and cards

**Testing:**
- Unit tests: 25 tests added (12 domain, 6 service, 7 view)
- Integration tests: 0 (endpoint manually testable)
- All tests passing

**Code review:**
- Iterations: 1
- Review file: review-phase-05-20260103.md
- Findings: 0 critical, 2 warnings (testing edge cases), 5 suggestions
- Verdict: APPROVED

**For next phases:**
- Available utilities: `MainProjectService.deriveFromWorktrees()` for project extraction, `MainProject.deriveMainProjectPath()` for path parsing
- Extension points: Config caching could be added if performance becomes an issue
- Notes: Phase 5 completes IW-79 - dashboard now supports multi-project worktree creation

**Files changed:**
```
A .iw/core/domain/MainProject.scala
A .iw/core/application/MainProjectService.scala
A .iw/core/presentation/views/MainProjectsView.scala
A .iw/core/test/MainProjectTest.scala
A .iw/core/test/MainProjectServiceTest.scala
A .iw/core/test/MainProjectsViewTest.scala
M .iw/core/CaskServer.scala
M .iw/core/DashboardService.scala
M .iw/core/presentation/views/CreateWorktreeModal.scala
M .iw/core/presentation/views/SearchResultsView.scala
```

---
