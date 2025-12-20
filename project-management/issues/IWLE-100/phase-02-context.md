# Phase 2 Context: Automatic worktree registration from CLI commands

**Issue:** IWLE-100
**Phase:** 2 of 7
**Story:** Story 2 - Automatic worktree registration from CLI commands
**Estimated Effort:** 6-8 hours
**Created:** 2025-12-20

---

## Goals

This phase makes the dashboard feature usable in normal workflow by implementing automatic worktree registration. The primary objectives are:

1. **Server registration endpoint**: Add `PUT /api/worktrees/{issueId}` to register/update worktrees
2. **HTTP client for CLI commands**: Create `ServerClient` that CLI commands can use to communicate with the server
3. **Auto-registration in `iw start`**: Register new worktrees after successful creation
4. **Auto-registration in `iw open`**: Register/update existing worktrees when opened
5. **Update timestamp in `iw issue`**: Refresh `lastSeenAt` timestamp for current worktree
6. **Best-effort registration**: Failures must not break CLI command functionality
7. **Lazy server start**: If server isn't running, trigger auto-start before registration

After this phase, developers will have worktrees automatically appear on the dashboard without manual API calls, enabling the intended workflow.

---

## Scope

### In Scope

**Registration Endpoint:**
- `PUT /api/worktrees/{issueId}` endpoint in CaskServer
- Request body: `{ "path": "...", "trackerType": "...", "team": "..." }`
- Creates new registration or updates existing (upsert semantics)
- Updates `lastSeenAt` timestamp on every request
- Returns 200 with registration JSON on success

**ServerClient HTTP Client:**
- `registerWorktree(issueId, path, trackerType, team)` method
- `updateLastSeen(issueId)` method  
- Uses sttp for HTTP calls (consistent with LinearClient pattern)
- Best-effort approach: returns `Either[String, Unit]`, failures logged but don't throw
- Reads port from hardcoded 9876 (config file reading comes in Phase 3)

**CLI Command Modifications:**
- **start.scala**: Call `registerWorktree` after successful worktree creation, before tmux session
- **open.scala**: Call `updateLastSeen` when opening worktree (at entry point)
- **issue.scala**: Call `updateLastSeen` at successful command completion

**Integration Pattern:**
- Detect if server is running via health check (`GET /health`)
- If not running, attempt lazy start using existing dashboard.scala pattern
- If lazy start fails, log warning but continue (non-blocking)
- Registration calls happen synchronously (no background threads in Phase 2)

### Out of Scope

**Not in Phase 2 (deferred to later phases):**
- Port configuration from file (Phase 3 - Server lifecycle management)
- `iw server start/stop/status` commands (Phase 3)
- PID file management (Phase 3)
- Background server daemon mode (Phase 3)
- Issue data fetching from tracker API (Phase 4)
- Phase/task progress parsing (Phase 5)
- Git status detection (Phase 6)
- PR link detection (Phase 6)
- Unregister endpoint for `iw rm` (Phase 7)
- Auto-pruning non-existent worktrees (Phase 7)

**Technical Scope Boundaries:**
- Server lifecycle: For Phase 2, we use the existing `dashboard.scala` server start pattern (foreground thread). Full lifecycle management comes in Phase 3.
- Error handling: Registration failures are logged to stderr but don't affect command exit codes. CLI commands must succeed even if server is unavailable.
- Performance: Synchronous HTTP calls are acceptable (fast local requests). Async can be added later if needed.

---

## Dependencies

### Prerequisites from Phase 1

**Must exist and work correctly:**
- `CaskServer` with `/` and `/health` endpoints on port 9876
- `WorktreeRegistration` domain model with validation
- `ServerState` with worktree map and `listByActivity` method
- `StateRepository` for JSON persistence at `~/.local/share/iw/server/state.json`
- `ServerStateService` for state load/save operations
- `dashboard.scala` command that starts server and opens browser
- Unit tests for domain models and state repository

**Available for reuse:**
- sttp HTTP client library (already used by LinearClient)
- upickle JSON serialization (already used by StateRepository)
- Existing command structure and error handling patterns
- Configuration loading from `.iw/config.conf` (ConfigFileRepository)

**Configuration data available:**
- Tracker type: `config.trackerType` (Linear or YouTrack)
- Team: Embedded in issueId (e.g., "IWLE" from "IWLE-123")
- Project name: `config.projectName`
- Worktree path: Computed by `WorktreePath.resolve()`

### External Dependencies

**No new dependencies required:**
- sttp.client4 already in project.scala
- upickle already in project.scala
- Cask already in project.scala

**System dependencies (already required):**
- Git (for worktree operations)
- Tmux (for session management)
- Curl or sttp for HTTP client (sttp used)

---

## Technical Approach

### Architecture Overview

```
CLI Commands (start.scala, open.scala, issue.scala)
       ↓
   ServerClient (HTTP client)
       ↓ PUT /api/worktrees/{id}
   CaskServer
       ↓
   WorktreeRegistrationService (Application Layer)
       ↓
   StateRepository (Infrastructure Layer)
       ↓
   state.json (Filesystem)
```

**Layer responsibilities:**
- **Presentation (CLI commands)**: Trigger registration at appropriate lifecycle points
- **Infrastructure (ServerClient)**: HTTP communication, best-effort error handling
- **Application (WorktreeRegistrationService)**: Business logic for register/update operations
- **Infrastructure (StateRepository)**: Persistence (reused from Phase 1)

### Key Components

#### 1. ServerClient (Infrastructure Layer)

**File:** `.iw/core/ServerClient.scala`

**Purpose:** HTTP client for CLI commands to communicate with CaskServer.

**Interface:**
```scala
object ServerClient:
  def registerWorktree(
    issueId: IssueId,
    path: String,
    trackerType: String,
    team: String,
    port: Int = 9876
  ): Either[String, Unit]
  
  def updateLastSeen(
    issueId: IssueId, 
    port: Int = 9876
  ): Either[String, Unit]
  
  private def ensureServerRunning(port: Int): Either[String, Unit]
  private def isHealthy(port: Int): Boolean
```

**Implementation notes:**
- Use sttp synchronous client (consistent with LinearClient pattern)
- JSON body serialization via upickle
- Best-effort: catch exceptions, return Left(error message)
- `ensureServerRunning` checks health endpoint, triggers lazy start if needed
- Lazy start reuses pattern from `dashboard.scala` (background thread + health check wait)

#### 2. WorktreeRegistrationService (Application Layer)

**File:** `.iw/core/WorktreeRegistrationService.scala`

**Purpose:** Pure business logic for registration operations.

**Interface:**
```scala
object WorktreeRegistrationService:
  def register(
    issueId: String,
    path: String,
    trackerType: String,
    team: String,
    state: ServerState
  ): Either[String, ServerState]
  
  def updateLastSeen(
    issueId: String,
    state: ServerState
  ): Either[String, ServerState]
```

**Implementation notes:**
- `register`: Creates new `WorktreeRegistration` or updates existing, always sets `lastSeenAt = now`
- `updateLastSeen`: Updates only `lastSeenAt` timestamp, fails if worktree not found
- Returns new `ServerState` (immutable)
- Pure functions - no I/O

#### 3. CaskServer Registration Endpoint

**File:** `.iw/core/CaskServer.scala` (modify existing)

**New route:**
```scala
@cask.put("/api/worktrees/:issueId")
def registerWorktree(issueId: String): cask.Response[ujson.Value]
```

**Request body format:**
```json
{
  "path": "/home/user/projects/myproject/../myproject-IWLE-123",
  "trackerType": "Linear",
  "team": "IWLE"
}
```

**Response formats:**
```json
// Success (200)
{
  "status": "registered",
  "issueId": "IWLE-123",
  "lastSeenAt": "2025-12-20T10:30:45Z"
}

// Error (400)
{
  "error": "Invalid issue ID format"
}

// Error (500)
{
  "error": "Failed to save state: ..."
}
```

**Implementation logic:**
1. Parse request body (upickle)
2. Call `WorktreeRegistrationService.register()`
3. Persist new state via `StateRepository.write()`
4. Return success or error response

#### 4. CLI Command Integration

**Modification points:**

**start.scala (after worktree creation, before tmux):**
```scala
// After: Output.success(s"Worktree created at ${targetPath}")
// Add:
val trackerType = config.trackerType.toString
val team = issueId.team
ServerClient.registerWorktree(issueId, targetPath.toString, trackerType, team) match
  case Left(error) =>
    Output.warn(s"Failed to register worktree with dashboard: $error")
  case Right(_) =>
    () // Silent success
```

**open.scala (at entry point, after config load):**
```scala
// After: case Some(config) =>
// Add:
ServerClient.updateLastSeen(issueId) match
  case Left(error) =>
    Output.warn(s"Failed to update dashboard: $error")
  case Right(_) =>
    () // Silent success
```

**issue.scala (at successful completion):**
```scala
// After: println(formatted)
// Add:
for {
  issueId <- getIssueId(args)
  _ <- ServerClient.updateLastSeen(issueId)
} yield ()
// Ignore errors (already at exit)
```

**Error handling philosophy:**
- Registration is a "nice to have" feature, not critical path
- Failures are logged with `Output.warn()` but don't change exit code
- Users can still work normally if dashboard is unavailable

### Server Lifecycle Pattern

**For Phase 2, reuse dashboard.scala pattern:**

```scala
private def ensureServerRunning(port: Int): Either[String, Unit] =
  if isHealthy(port) then
    Right(())
  else
    // Start server in background thread
    val serverThread = new Thread(() => {
      CaskServer.start(statePath, port)
    })
    serverThread.setDaemon(true)
    serverThread.start()
    
    // Wait for health check
    if waitForServer(port, timeoutSeconds = 5) then
      Right(())
    else
      Left("Server failed to start")
```

**Notes:**
- Daemon thread so it doesn't block CLI exit
- 5-second timeout for health check (same as dashboard.scala)
- If start fails, return Left (CLI command logs warning and continues)
- Phase 3 will improve this with proper PID management and `iw server` commands

---

## Files to Modify/Create

### New Files

**1. `.iw/core/ServerClient.scala`**
- HTTP client for CLI → Server communication
- Lazy server start logic
- Best-effort error handling

**2. `.iw/core/WorktreeRegistrationService.scala`**
- Pure business logic for register/updateLastSeen
- Returns new ServerState (immutable)

**3. `.iw/core/test/ServerClientTest.scala`**
- Unit tests for ServerClient (mock server responses)
- Tests for error handling paths

**4. `.iw/core/test/WorktreeRegistrationServiceTest.scala`**
- Unit tests for registration logic
- Tests for timestamp updates
- Tests for validation errors

### Modified Files

**1. `.iw/core/CaskServer.scala`**
- Add `PUT /api/worktrees/:issueId` route
- Parse request body, call WorktreeRegistrationService
- Handle errors and return appropriate HTTP status

**2. `.iw/commands/start.scala`**
- Add ServerClient.registerWorktree call after worktree creation
- Add Output.warn for registration failures

**3. `.iw/commands/open.scala`**
- Add ServerClient.updateLastSeen call at entry point
- Add Output.warn for update failures

**4. `.iw/commands/issue.scala`**
- Add ServerClient.updateLastSeen call at successful completion
- Ignore errors (best-effort)

### Test Files

**Unit tests (munit):**
- `ServerClientTest.scala` - HTTP client behavior, error handling
- `WorktreeRegistrationServiceTest.scala` - Registration logic, timestamp updates

**Integration tests (munit):**
- `CaskServerTest.scala` (extend existing) - Test new PUT endpoint with real StateRepository

**E2E tests (deferred to Phase 3):**
- After server lifecycle management is solid, add BATS tests for full workflow

---

## Testing Strategy

### Unit Tests

**WorktreeRegistrationServiceTest:**
```scala
test("register creates new worktree") {
  val state = ServerState(Map.empty)
  val result = WorktreeRegistrationService.register(
    "IWLE-123", "/path/to/worktree", "Linear", "IWLE", state
  )
  assert(result.isRight)
  assert(result.map(_.worktrees.contains("IWLE-123")).getOrElse(false))
}

test("register updates existing worktree lastSeenAt") {
  val now = Instant.now()
  val earlier = now.minusSeconds(3600)
  val existingReg = WorktreeRegistration(
    "IWLE-123", "/path", "Linear", "IWLE", earlier, earlier
  )
  val state = ServerState(Map("IWLE-123" -> existingReg))
  
  Thread.sleep(1) // Ensure time difference
  val result = WorktreeRegistrationService.register(
    "IWLE-123", "/path", "Linear", "IWLE", state
  )
  
  assert(result.isRight)
  val newReg = result.toOption.flatMap(_.worktrees.get("IWLE-123"))
  assert(newReg.map(_.lastSeenAt.isAfter(earlier)).getOrElse(false))
}

test("updateLastSeen updates timestamp") {
  // Similar to above, test timestamp update without changing other fields
}

test("updateLastSeen fails for non-existent worktree") {
  val state = ServerState(Map.empty)
  val result = WorktreeRegistrationService.updateLastSeen("IWLE-999", state)
  assert(result.isLeft)
}
```

**ServerClientTest:**
```scala
// Mock HTTP server responses
test("registerWorktree sends PUT request with correct body") {
  // Test HTTP request format
}

test("registerWorktree handles 200 success") {
  // Test successful registration
}

test("registerWorktree handles 500 server error") {
  // Test error response handling
}

test("updateLastSeen sends correct issueId") {
  // Test update request
}

test("ensureServerRunning detects running server") {
  // Test health check
}

test("ensureServerRunning starts server if not running") {
  // Test lazy start
}
```

### Integration Tests

**CaskServerTest (extend existing):**
```scala
test("PUT /api/worktrees/{id} registers new worktree") {
  // Real HTTP request to test server
  // Verify state.json is updated
}

test("PUT /api/worktrees/{id} updates existing worktree") {
  // Test upsert behavior
}

test("PUT /api/worktrees/{id} returns 400 for invalid body") {
  // Test error handling
}
```

### E2E Scenario Tests (Manual for Phase 2)

**Manual test scenarios:**

1. **Scenario: Creating worktree registers it**
   - Clean state: `rm ~/.local/share/iw/server/state.json`
   - Run: `./iw start IWLE-123`
   - Verify: state.json contains IWLE-123
   - Verify: Dashboard shows IWLE-123

2. **Scenario: Opening worktree updates timestamp**
   - Prerequisite: IWLE-123 registered with old timestamp
   - Wait 5 seconds
   - Run: `./iw open IWLE-123`
   - Verify: state.json has newer lastSeenAt
   - Verify: Dashboard shows updated time

3. **Scenario: Issue command updates timestamp**
   - Prerequisite: IWLE-123 registered
   - Run: `./iw issue` (inside IWLE-123 worktree)
   - Verify: lastSeenAt updated

4. **Scenario: Registration fails gracefully when server unavailable**
   - Stop server (if running)
   - Run: `./iw start IWLE-456` (server will auto-start)
   - Verify: Worktree created successfully
   - Verify: Registration succeeded (or warning shown but command succeeded)

**Automated E2E tests (BATS) - deferred to Phase 3 after lifecycle management**

---

## Acceptance Criteria

Phase 2 is complete when:

### Functional Requirements

- [ ] `PUT /api/worktrees/{issueId}` endpoint implemented and working
  - [ ] Accepts JSON body with path, trackerType, team
  - [ ] Creates new registration if not exists
  - [ ] Updates lastSeenAt if already exists
  - [ ] Returns 200 with registration data on success
  - [ ] Returns 400 for invalid request body
  - [ ] Returns 500 on state persistence errors

- [ ] ServerClient HTTP client implemented
  - [ ] `registerWorktree()` sends PUT request
  - [ ] `updateLastSeen()` sends PUT request (minimal body)
  - [ ] Best-effort error handling (returns Either)
  - [ ] Lazy server start if health check fails

- [ ] CLI command integration complete
  - [ ] `iw start` registers worktree after creation
  - [ ] `iw open` updates lastSeenAt when opening
  - [ ] `iw issue` updates lastSeenAt when run
  - [ ] Registration failures don't break commands
  - [ ] Warnings shown for registration failures

### Non-Functional Requirements

- [ ] All unit tests passing (WorktreeRegistrationService, ServerClient)
- [ ] All integration tests passing (CaskServer PUT endpoint)
- [ ] Manual E2E scenarios verified (see Testing Strategy above)
- [ ] Code follows existing patterns (sttp usage, Either error handling)
- [ ] No new compilation warnings
- [ ] Git commits follow TDD: test → implementation → refactor

### Quality Checks

- [ ] Code review self-check: Does ServerClient follow LinearClient patterns?
- [ ] Code review self-check: Are all error paths handled gracefully?
- [ ] Code review self-check: Is registration truly best-effort (non-blocking)?
- [ ] Documentation: Update implementation-log.md with Phase 2 summary
- [ ] Documentation: Comment complex parts (lazy start logic, error handling)

---

## Implementation Sequence

**Recommended order (TDD):**

### Step 1: Pure Domain Logic (1-2h)

1. Write `WorktreeRegistrationServiceTest.scala` with registration scenarios
2. Implement `WorktreeRegistrationService.scala` to pass tests
3. Verify all unit tests pass
4. Commit: "feat(IWLE-100): Add WorktreeRegistrationService with tests"

### Step 2: HTTP Endpoint (1-2h)

5. Write integration test for `PUT /api/worktrees/{id}` in `CaskServerTest.scala`
6. Implement route in `CaskServer.scala`
7. Wire up WorktreeRegistrationService and StateRepository
8. Verify integration tests pass
9. Manual test with curl: `curl -X PUT http://localhost:9876/api/worktrees/IWLE-123 -d '{"path":"...","trackerType":"Linear","team":"IWLE"}'`
10. Commit: "feat(IWLE-100): Add worktree registration endpoint"

### Step 3: HTTP Client (1-2h)

11. Write `ServerClientTest.scala` with mock server scenarios
12. Implement `ServerClient.scala` with registerWorktree and updateLastSeen
13. Implement lazy server start logic (reuse dashboard.scala pattern)
14. Verify unit tests pass
15. Manual test: Call ServerClient.registerWorktree from scala REPL
16. Commit: "feat(IWLE-100): Add ServerClient for CLI-to-server communication"

### Step 4: CLI Integration (2-3h)

17. Modify `start.scala`: Add registration call after worktree creation
18. Test manually: `./iw start IWLE-TEST-123`, verify state.json
19. Commit: "feat(IWLE-100): Auto-register worktrees in iw start"

20. Modify `open.scala`: Add updateLastSeen call at entry
21. Test manually: `./iw open IWLE-TEST-123`, verify timestamp updated
22. Commit: "feat(IWLE-100): Update lastSeen in iw open"

23. Modify `issue.scala`: Add updateLastSeen call at completion
24. Test manually: `./iw issue`, verify timestamp updated
25. Commit: "feat(IWLE-100): Update lastSeen in iw issue"

### Step 5: E2E Verification (0.5-1h)

26. Run all manual E2E scenarios (see Testing Strategy)
27. Fix any issues found
28. Verify dashboard shows worktrees with correct timestamps
29. Commit fixes if needed

### Step 6: Documentation (0.5h)

30. Update `implementation-log.md` with Phase 2 summary
31. Document key decisions and patterns used
32. Commit: "docs(IWLE-100): Document Phase 2 implementation"

**Total estimated time: 6-8 hours**

---

## Risks and Mitigation

### Risk 1: Server start race conditions

**Risk:** CLI command might try to register before server is fully ready.

**Mitigation:**
- Reuse proven health check pattern from dashboard.scala
- 5-second timeout with 200ms polling
- If timeout, treat as server unavailable (log warning, continue)

### Risk 2: Concurrent state file writes

**Risk:** Multiple CLI commands registering simultaneously could corrupt state.json.

**Mitigation:**
- StateRepository already uses atomic write (tmp + rename)
- OS-level atomic move guarantees last-write-wins
- Acceptable for Phase 2 (low concurrency in normal usage)
- Phase 3 will add proper locking if needed

### Risk 3: Breaking existing commands

**Risk:** Adding registration calls could introduce bugs in start/open/issue.

**Mitigation:**
- Best-effort approach: failures don't change exit code
- Extensive testing of error paths
- Registration happens after critical command logic (worktree creation, etc.)
- Easy rollback: remove registration calls

### Risk 4: Port conflicts

**Risk:** Port 9876 might be in use, preventing server start.

**Mitigation:**
- Phase 2 uses hardcoded port (same as Phase 1)
- If port busy, lazy start fails, registration logs warning
- Commands still work (just dashboard unavailable)
- Phase 3 will add port configuration

---

## Notes and Decisions

### Design Decisions

**1. Synchronous HTTP calls**
- Decision: Use synchronous sttp client (not async)
- Rationale: Local HTTP calls are fast (<50ms), blocking is acceptable
- Can change to async later if performance issue

**2. Best-effort registration**
- Decision: Registration failures don't affect CLI command exit codes
- Rationale: Dashboard is auxiliary feature, core workflow must be reliable
- Alternative considered: Make registration required (rejected: too fragile)

**3. Lazy server start in CLI commands**
- Decision: CLI commands auto-start server if needed
- Rationale: Better UX, users don't need to remember to start server
- Alternative considered: Require explicit `iw server start` (rejected: too manual, comes in Phase 3)

**4. Daemon thread for background server**
- Decision: Use `serverThread.setDaemon(true)` for lazy start
- Rationale: CLI command can exit without waiting for server shutdown
- Alternative considered: Detached process (rejected: too complex for Phase 2, comes in Phase 3)

**5. UpdateLastSeen vs full re-registration**
- Decision: Separate `updateLastSeen()` method that only updates timestamp
- Rationale: More efficient, clearer intent
- Alternative considered: Always call registerWorktree (rejected: wasteful, path might not be available in `iw issue`)

### Open Questions (for later phases)

- **Q:** Should we add rate limiting to prevent registration spam?
  - **A:** Defer to Phase 3. Not needed for MVP.

- **Q:** Should updateLastSeen fail silently if worktree not registered?
  - **A:** For Phase 2, yes (just log warning). Phase 3 may add auto-registration fallback.

- **Q:** What if issue ID is invalid in PUT request?
  - **A:** Return 400. IssueId validation will catch this.

---

## Links to Related Documents

- **Analysis:** `project-management/issues/IWLE-100/analysis.md` (Story 2, lines 61-104)
- **Phase 1 Context:** `project-management/issues/IWLE-100/phase-01-context.md`
- **Implementation Log:** `project-management/issues/IWLE-100/implementation-log.md`
- **Task Index:** `project-management/issues/IWLE-100/tasks.md`

---

## Gherkin Scenarios (from Analysis)

```gherkin
Feature: CLI commands register worktrees automatically
  As a developer using iw commands
  I want worktrees to register automatically with the dashboard
  So that I don't need manual registration steps

Scenario: Creating a worktree registers it automatically
  Given the dashboard server is running
  When I run "iw start IWLE-123"
  And the worktree is created successfully
  Then the worktree IWLE-123 is registered with the server
  And the registration includes path, tracker type, and team
  And I see the new worktree on the dashboard immediately

Scenario: Using any command in a worktree updates its last-seen timestamp
  Given worktree IWLE-123 exists and is registered
  When I cd into the worktree directory
  And I run "iw issue"
  Then the last-seen timestamp for IWLE-123 is updated
  And the worktree appears at the top of the dashboard (most recent)
```

**Test Automation:**
- Phase 2: Manual testing of scenarios
- Phase 3: Automated BATS tests after server lifecycle solid

---

**Status:** Ready for Implementation
**Next Command:** `/iterative-works:ag-implement IWLE-100 --phase 2`
