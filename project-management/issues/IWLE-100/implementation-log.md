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
