# Implementation Log: Add server dashboard for worktree monitoring

Issue: IWLE-100

This log tracks the evolution of implementation across phases.

---

## Phase 1: View basic dashboard with registered worktrees (2025-12-20)

**What was built:**
- Domain: `WorktreeRegistration.scala` - Value object with validation for worktree metadata (issue ID, path, tracker type, team, timestamps)
- Domain: `ServerState.scala` - State model with activity-based sorting (`listByActivity`)
- Infrastructure: `StateRepository.scala` - JSON persistence with atomic writes (tmp + rename) using upickle
- Infrastructure: `CaskServer.scala` - HTTP server with `/` and `/health` endpoints on port 9876
- Application: `ServerStateService.scala` - State management coordination (load, save, list)
- Application: `DashboardService.scala` - Full HTML page generation with inline CSS
- Presentation: `WorktreeListView.scala` - Scalatags worktree cards with relative timestamps
- Command: `dashboard.scala` - CLI entry point with health checks and platform-agnostic browser opening

**Decisions made:**
- Port 9876 hardcoded for Phase 1 (will be configurable in Phase 3)
- Issue titles show placeholder "Issue title not yet loaded" (Phase 4 will fetch from tracker)
- State file at `~/.local/share/iw/server/state.json`
- Platform detection for browser opening: macOS (`open`), Linux (`xdg-open`), Windows (`cmd /c start`)
- Flat directory structure kept for simplicity (packages declare logical layers)

**Patterns applied:**
- Functional Core / Imperative Shell: Domain and Application layers are pure, Infrastructure handles I/O
- Value Objects with validation: `WorktreeRegistration.create()` returns `Either[String, WorktreeRegistration]`
- Atomic writes: State persistence uses tmp file + rename to prevent corruption

**Testing:**
- Unit tests: 9 tests added (WorktreeRegistration, ServerState, StateRepository)
- Integration tests: 4 tests in StateRepositoryTest (filesystem-based)
- E2E tests: Deferred to after core implementation stabilizes

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20251220.md
- Major findings: Architectural improvements recommended (DI patterns, package structure) - acceptable for Phase 1

**For next phases:**
- Available utilities: `StateRepository` for JSON persistence, `CaskServer` for HTTP routes
- Extension points: Add new routes to CaskServer, extend ServerState with new fields
- Notes: Server lifecycle management (Phase 3) will add `iw server start/stop/status` commands

**Files changed:**
```
M .iw/core/project.scala
A .iw/commands/dashboard.scala
A .iw/core/CaskServer.scala
A .iw/core/DashboardService.scala
A .iw/core/ServerState.scala
A .iw/core/ServerStateService.scala
A .iw/core/StateRepository.scala
A .iw/core/WorktreeListView.scala
A .iw/core/WorktreeRegistration.scala
A .iw/core/test/ServerStateTest.scala
A .iw/core/test/StateRepositoryTest.scala
A .iw/core/test/WorktreeRegistrationTest.scala
```

---

## Phase 2: Automatic worktree registration from CLI commands (2025-12-20)

**What was built:**
- Application: `WorktreeRegistrationService.scala` - Pure business logic for registration with upsert semantics and timestamp injection
- Infrastructure: `ServerClient.scala` - HTTP client for CLI-to-server communication with lazy server start
- Infrastructure: `CaskServer.scala` - Added `PUT /api/v1/worktrees/:issueId` endpoint with structured error responses
- CLI Integration: Modified `start.scala`, `open.scala`, `issue.scala` to auto-register/update worktrees

**Decisions made:**
- Best-effort registration: CLI commands succeed even if server unavailable (warnings only)
- Upsert semantics: PUT creates new registration or updates existing, preserving `registeredAt`
- Lazy server start: ServerClient auto-starts server if health check fails
- API versioning: Added `/api/v1/` prefix for future compatibility
- Timestamp injection: Pure functions receive `Instant` from callers (FCIS compliance)

**Patterns applied:**
- Functional Core / Imperative Shell: WorktreeRegistrationService is pure, CaskServer handles I/O and passes timestamps
- Best-effort side effects: Registration uses Either with warn-only error handling
- Daemon thread pattern: Server started in daemon thread for lazy start
- Structured error responses: `{"code": "ERROR_CODE", "message": "..."}` format

**Testing:**
- Unit tests: 15 tests added (WorktreeRegistrationService: 12, ServerClient: 3)
- Integration tests: 5 tests added (CaskServer PUT endpoint)
- E2E tests: Deferred to Phase 3 with server lifecycle

**Code review:**
- Iterations: 2
- Review file: review-phase-02-20251220.md
- Issues fixed: Timestamp injection (FCIS), API versioning, structured errors, package naming

**For next phases:**
- Available utilities: `ServerClient.registerWorktree()` for any command needing registration
- Extension points: Add more endpoints to CaskServer, extend registration data
- Notes: Phase 3 will add `iw server start/stop/status` and port configuration

**Files changed:**
```
M .iw/commands/issue.scala
M .iw/commands/open.scala
M .iw/commands/start.scala
M .iw/core/CaskServer.scala
A .iw/core/ServerClient.scala
A .iw/core/WorktreeRegistrationService.scala
A .iw/core/test/CaskServerTest.scala
A .iw/core/test/ServerClientTest.scala
A .iw/core/test/WorktreeRegistrationServiceTest.scala
```

---

## Phase 3: Server lifecycle management (2025-12-20)

**What was built:**
- Domain: `ServerConfig.scala` - Port configuration with validation (1024-65535 range)
- Domain: `ServerStatus.scala` - Server runtime status model (running, port, worktreeCount, startedAt, pid)
- Application: `ServerLifecycleService.scala` - Pure business logic for uptime formatting and status creation
- Infrastructure: `ServerConfigRepository.scala` - JSON config file persistence with default creation
- Infrastructure: `ProcessManager.scala` - Background process spawning, PID file management, process lifecycle control
- Infrastructure: `CaskServer.scala` - Added `GET /api/status` endpoint with runtime information
- Presentation: `server.scala` - CLI with `start`, `stop`, `status` subcommands
- Presentation: `server-daemon.scala` - Background server process entry point

**Decisions made:**
- Port configuration stored in `~/.local/share/iw/server/config.json` (single source of truth)
- PID file at `~/.local/share/iw/server/server.pid` for process tracking
- SIGTERM for graceful shutdown via `ProcessHandle.destroy()`
- Health check with 50 retries x 100ms = 5 second timeout before declaring server ready
- Default port 9876 maintained for backward compatibility
- UNIX-only process management (Windows support deferred)

**Patterns applied:**
- Functional Core / Imperative Shell: ServerConfig, ServerStatus are pure domain; ProcessManager handles effects
- Value Objects with validation: `ServerConfig.validate(port)` returns `Either[String, Int]`
- Process lifecycle: PID file creation on start, removal on stop, stale PID detection
- Health check integration: Server start waits for `/health` response before success

**Testing:**
- Unit tests: 21 tests added (ServerConfig: 7, ServerStatus: 3, ServerLifecycleService: 8, ProcessManager: 3)
- Integration tests: 14 tests added (ServerConfigRepository: 8, ProcessManager: 6, CaskServer status: 3)
- E2E tests: Deferred to Phase 4

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20251220.md
- Verdict: PASS with 0 critical, 3 warnings, 12 suggestions
- Warnings: ServerClient lazy start coupling, test package organization, ProcessManager mutable state

**For next phases:**
- Available utilities: `ServerConfigRepository` for port config, `ProcessManager` for process control
- Extension points: Add more status fields, extend lifecycle commands
- Notes: Phase 4 will add issue data fetching from Linear/YouTrack

**Files changed:**
```
M .iw/commands/dashboard.scala
M .iw/core/CaskServer.scala
M .iw/core/ServerClient.scala
M .iw/core/test/CaskServerTest.scala
A .iw/commands/server.scala
A .iw/commands/server-daemon.scala
A .iw/core/ServerConfig.scala
A .iw/core/ServerConfigRepository.scala
A .iw/core/ServerStatus.scala
A .iw/core/ServerLifecycleService.scala
A .iw/core/ProcessManager.scala
A .iw/core/test/ServerConfigTest.scala
A .iw/core/test/ServerConfigRepositoryTest.scala
A .iw/core/test/ServerStatusTest.scala
A .iw/core/test/ServerLifecycleServiceTest.scala
A .iw/core/test/ProcessManagerTest.scala
```

---

## Phase 4: Show issue details and status from tracker (2025-12-20)

**What was built:**
- Domain: `IssueData.scala` - Extended issue model with URL and fetchedAt timestamp, factory method `fromIssue()`
- Domain: `CachedIssue.scala` - Cache wrapper with TTL validation (5 minute default), pure `isValid()` and `age()` functions
- Application: `IssueCacheService.scala` - Pure business logic for cache-aware issue fetching with stale fallback on API failure
- Infrastructure: `StateRepository.scala` - Extended with upickle ReadWriters for IssueData, CachedIssue, and Instant
- Application: `DashboardService.scala` - Integrated issue fetching via IssueCacheService with Linear/YouTrack API dispatch
- Presentation: `WorktreeListView.scala` - Enhanced cards with issue title, status badge, assignee, cache indicator, clickable links

**Decisions made:**
- TTL of 5 minutes balances freshness with API rate limits
- Stale cache fallback: prefer showing outdated data over "unavailable" on API failure (better UX)
- Timestamps injected via `now: Instant` parameter for FCIS purity
- Issue cache embedded in ServerState (no separate cache file) for atomic persistence
- Status badge color mapping: in-progress=yellow, done=green, blocked=red, default=gray
- Cache age formatting: "just now", "Xm ago", "Xh ago", "Xd ago"

**Patterns applied:**
- Functional Core / Imperative Shell: IssueData, CachedIssue, IssueCacheService are pure; DashboardService orchestrates at the edge
- Pure functions with timestamp injection: `isValid(cached, now)`, `fetchWithCache(..., now, ...)`
- Graceful degradation: Valid cache → Fresh fetch → Stale fallback → Error message
- Factory method pattern: `IssueData.fromIssue(issue, url, fetchedAt)`

**Testing:**
- Unit tests: 18 tests added (IssueData: 3, CachedIssue: 6, IssueCacheService: 9)
- Integration tests: 5 tests added (StateRepository cache serialization)
- E2E tests: Deferred to Phase 8

**Code review:**
- Iterations: 1
- Review file: review-phase-04-20251220.md
- Verdict: PASS with 0 critical, 2 warnings, 5 suggestions
- Warnings: Case sensitivity in tracker type matching, view layer contains status classification logic

**For next phases:**
- Available utilities: `IssueCacheService.fetchWithCache()` for cache-aware issue fetching
- Extension points: Add more issue fields, customize TTL per project, add cache pruning
- Notes: Cache grows with worktrees (~2KB per issue); pruning deferred to Phase 7 if needed

**Files changed:**
```
M .iw/core/CaskServer.scala
M .iw/core/DashboardService.scala
M .iw/core/ServerState.scala
M .iw/core/StateRepository.scala
M .iw/core/WorktreeListView.scala
M .iw/core/test/StateRepositoryTest.scala
A .iw/core/IssueData.scala
A .iw/core/CachedIssue.scala
A .iw/core/IssueCacheService.scala
A .iw/core/test/IssueDataTest.scala
A .iw/core/test/CachedIssueTest.scala
A .iw/core/test/IssueCacheServiceTest.scala
```

---

## Phase 5: Display phase and task progress (2025-12-20)

**What was built:**
- Domain: `PhaseInfo.scala` - Phase metadata with task counts and computed properties (isComplete, isInProgress, notStarted, progressPercentage)
- Domain: `WorkflowProgress.scala` - Complete workflow state with current phase detection and overall progress calculation
- Domain: `CachedProgress.scala` - Cache wrapper with file mtime validation for cache invalidation
- Application: `MarkdownTaskParser.scala` - Pure functions for parsing checkbox tasks (`- [ ]`, `- [x]`) and extracting phase names from headers
- Application: `WorkflowProgressService.scala` - Pure business logic for progress computation with file I/O injection pattern
- Infrastructure: `ServerState.scala` - Extended with progressCache field for workflow progress persistence
- Infrastructure: `StateRepository.scala` - Added upickle ReadWriters for PhaseInfo, WorkflowProgress, CachedProgress
- Application: `DashboardService.scala` - Integrated progress fetching with file I/O wrappers (readFile, getMtime)
- Presentation: `WorktreeListView.scala` - Enhanced cards with phase label, progress bar, and task count display
- Styling: Added CSS for phase-info, progress-container, progress-bar with gradient fill and overlay text

**Decisions made:**
- mtime-based cache invalidation (no TTL, no content hashing) - simpler and fast enough for manual file edits
- Strict checkbox parsing: only `- [ ]` and `- [x]` recognized (matches agile workflow format)
- Phase name extraction: try header first (`# Phase N: Name`), fallback to filename (`phase-02-tasks.md` → "Phase 2")
- Current phase logic: first with incomplete tasks, or first not-started, or last phase if all complete
- Progress display: show current phase progress (not overall) for actionable focus
- File discovery: check phase-01 through phase-20 by attempting to get mtime
- Error handling: graceful fallback - missing files show no progress (no error message)
- Progress bar: linear gradient green (#51cf66 → #37b24d), text overlay for task count

**Patterns applied:**
- Functional Core / Imperative Shell: PhaseInfo, WorkflowProgress, MarkdownTaskParser, WorkflowProgressService are pure; file I/O injected from DashboardService
- File I/O injection: `readFile: String => Either[String, Seq[String]]` and `getMtime: String => Either[String, Long]` passed to WorkflowProgressService
- Best-effort parsing: count valid checkboxes, ignore malformed lines (don't crash on unexpected markdown)
- Cache validation: compare file mtimes, re-parse if any changed or new files added
- Graceful degradation: missing task files → no progress shown, read errors → toOption for silent failure

**Testing:**
- Unit tests: 48 tests added (PhaseInfo: 5, WorkflowProgress: 4, CachedProgress: 4, MarkdownTaskParser: 16, WorkflowProgressService: 12, IssueCacheService extended: 7)
- Integration tests: 4 tests added (StateRepository progress cache serialization)
- E2E tests: Manual testing deferred to Parts 8-9 (empty files, malformed markdown, missing directories)

**Code review:**
- Iterations: 1
- Review file: review-phase-05-20251220.md
- Skills applied: architecture, scala3, testing
- Verdict: PASS with 0 critical, 5 warnings, 8 suggestions
- Warnings: Domain model contains file path (PhaseInfo), application service mixes concerns (DashboardService), opaque types for type safety, extension methods for TaskCount, missing error boundary tests

**For next phases:**
- Available utilities: `WorkflowProgressService.fetchProgress()` for progress tracking, `MarkdownTaskParser` for checkbox counting
- Extension points: Add nested task support, custom task formats, real-time file watching
- Notes: Progress cache grows with worktrees (~1KB per issue); no pruning needed (mtime-based invalidation keeps it current)

**Files changed:**
```
M .iw/core/CaskServer.scala
M .iw/core/DashboardService.scala
M .iw/core/ServerState.scala
M .iw/core/StateRepository.scala
M .iw/core/WorktreeListView.scala
M .iw/core/test/StateRepositoryTest.scala
A .iw/core/PhaseInfo.scala
A .iw/core/WorkflowProgress.scala
A .iw/core/CachedProgress.scala
A .iw/core/MarkdownTaskParser.scala
A .iw/core/WorkflowProgressService.scala
A .iw/core/test/PhaseInfoTest.scala
A .iw/core/test/WorkflowProgressTest.scala
A .iw/core/test/CachedProgressTest.scala
A .iw/core/test/MarkdownTaskParserTest.scala
A .iw/core/test/WorkflowProgressServiceTest.scala
```

---

## Phase 6: Show git status and PR links (2025-12-20)

**What was built:**
- Domain: `GitStatus.scala` - Git repository state model with branch name, clean/dirty indicator, and computed CSS classes
- Domain: `PullRequestData.scala` - PR information model with PRState enum (Open/Merged/Closed) and badge styling
- Domain: `CachedPR.scala` - Cache wrapper with 2-minute TTL validation for PR data
- Application: `GitStatusService.scala` - Pure business logic for git status detection with injected command execution
- Application: `PullRequestCacheService.scala` - Pure business logic for PR fetching with GitHub/GitLab CLI support and caching
- Infrastructure: `CommandRunner.scala` - Shell command execution utilities (execute, isCommandAvailable)
- Infrastructure: `ServerState.scala` - Extended with prCache field for PR data persistence
- Infrastructure: `StateRepository.scala` - Added upickle ReadWriters for GitStatus, PRState, PullRequestData, CachedPR
- Application: `DashboardService.scala` - Integrated git status and PR fetching with command execution wrappers
- Presentation: `WorktreeListView.scala` - Enhanced cards with git status section and PR link button with state badges
- Styling: Added CSS for git-status, git-clean (green), git-dirty (yellow), pr-button, pr-badge (open=blue, merged=purple, closed=gray)

**Decisions made:**
- 2-minute TTL for PR cache (shorter than issue cache, PRs change more frequently)
- Command execution injected as functions to keep services pure (FCIS pattern)
- Tool detection: prefer `gh` over `glab` if both available
- Graceful degradation: no git status shown if not a git repo, no PR link if tools unavailable
- Git commands use `-C` flag for worktree path (no chdir needed)
- PR detection via CLI tools (gh/glab) leverages existing user authentication
- Clean/dirty logic: empty `git status --porcelain` output = clean

**Patterns applied:**
- Functional Core / Imperative Shell: GitStatus, PullRequestData, CachedPR, GitStatusService, PullRequestCacheService are pure; CommandRunner handles I/O
- Function injection: `execCommand: (String, Array[String]) => Either[String, String]` passed to services
- Tool detection injection: `detectTool: String => Boolean` for testable tool availability checking
- Graceful degradation: Either.toOption for silent failure, dashboard renders without git/PR data on errors
- Cache-first pattern: check PR cache validity before executing CLI commands

**Testing:**
- Unit tests: 57 tests added (GitStatus: 6, PullRequestData: 8, CachedPR: 8, CommandRunner: 10, GitStatusService: 14, PullRequestCacheService: 15)
- Integration tests: 4 tests added (StateRepository PR cache serialization)
- E2E tests: Manual testing scenarios documented for clean/dirty, PR links, cache TTL

**Code review:**
- Iterations: 1
- Review file: review-phase-06-20251220.md
- Skills applied: architecture, scala3, testing, composition
- Verdict: PASS with 0 critical, 6 warnings, 6 suggestions
- Warnings: Opaque type for IssueId, extension methods for presentation logic, edge case test coverage

**For next phases:**
- Available utilities: `CommandRunner` for shell execution, `GitStatusService` for git status, `PullRequestCacheService` for PR fetching
- Extension points: Add more git info (ahead/behind), support more PR tools, configure TTL per project
- Notes: PR cache grows with worktrees (~0.5KB per issue); pruning via 2-minute TTL keeps it fresh

**Files changed:**
```
M .iw/core/CaskServer.scala
M .iw/core/DashboardService.scala
M .iw/core/ServerState.scala
M .iw/core/StateRepository.scala
M .iw/core/WorktreeListView.scala
M .iw/core/test/StateRepositoryTest.scala
A .iw/core/CachedPR.scala
A .iw/core/CommandRunner.scala
A .iw/core/GitStatus.scala
A .iw/core/GitStatusService.scala
A .iw/core/PullRequestCacheService.scala
A .iw/core/PullRequestData.scala
A .iw/core/test/CachedPRTest.scala
A .iw/core/test/CommandRunnerTest.scala
A .iw/core/test/GitStatusServiceTest.scala
A .iw/core/test/GitStatusTest.scala
A .iw/core/test/PullRequestCacheServiceTest.scala
A .iw/core/test/PullRequestDataTest.scala
```

---

## Phase 7: Unregister worktrees when removed (2025-12-20)

**What was built:**
- Domain: `ServerState.removeWorktree()` - Method to atomically remove worktree and all associated caches (issue, progress, PR)
- Application: `WorktreeUnregistrationService.scala` - Pure functions for explicit unregistration and auto-pruning of non-existent paths
- Infrastructure: `CaskServer.scala` - Added `DELETE /api/v1/worktrees/:issueId` endpoint with 200/404 responses, integrated auto-pruning into dashboard route
- Infrastructure: `ServerClient.scala` - Added `unregisterWorktree()` method for CLI-to-server communication
- CLI Integration: `rm.scala` - Integrated best-effort unregistration after successful worktree removal

**Decisions made:**
- Best-effort unregistration: `iw rm` succeeds even if server unavailable (warnings only)
- Idempotent DELETE: Returns 200 if worktree removed, 404 if already absent (no error on re-delete)
- Auto-pruning on dashboard load: Checks path existence and removes stale worktrees
- Atomic cache cleanup: Removing worktree also removes all associated caches (issue, progress, PR)
- Auto-prune saves state: Only persists if changes detected (no unnecessary writes)

**Patterns applied:**
- Functional Core / Imperative Shell: `WorktreeUnregistrationService` is pure, `CaskServer` handles filesystem checks
- Pure functions with path predicate injection: `pruneNonExistent(state, pathExists: String => Boolean)`
- Best-effort side effects: Unregistration uses Either with warn-only error handling (consistent with Phase 2)
- Idempotent operations: Safe to call multiple times (DELETE, prune)

**Testing:**
- Unit tests: 13 tests added (WorktreeUnregistrationService: 10, ServerState.removeWorktree: 3)
- Integration tests: 3 tests added (CaskServer DELETE endpoint)
- E2E tests: Manual verification scenarios for rm command and auto-pruning

**Code review:**
- Iterations: 1
- Review file: review-phase-07-20251220.md
- Skills applied: architecture, scala3, testing, composition
- Verdict: PASS with 0 critical, 1 warning, 4 suggestions
- Warning: Inconsistent error output style in rm.scala (System.err vs Output.warning)

**For next phases:**
- Available utilities: `WorktreeUnregistrationService.pruneNonExistent()` for cleanup, `ServerClient.unregisterWorktree()` for explicit removal
- Extension points: Add batch unregistration, archiving instead of deletion
- Notes: This is the final phase - dashboard feature complete!

**Files changed:**
```
M .iw/commands/rm.scala
M .iw/core/CaskServer.scala
M .iw/core/ServerClient.scala
M .iw/core/ServerState.scala
M .iw/core/test/CaskServerTest.scala
M .iw/core/test/ServerStateTest.scala
A .iw/core/WorktreeUnregistrationService.scala
A .iw/core/test/WorktreeUnregistrationServiceTest.scala
```

---
