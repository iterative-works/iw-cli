# Phase 3 Context: Server lifecycle management

**Issue:** IWLE-100
**Phase:** 3 of 7
**Story:** Story 6 - Server lifecycle management
**Estimated Effort:** 6-8 hours
**Created:** 2025-12-20

---

## Goals

This phase solidifies the infrastructure by adding explicit control over the dashboard server lifecycle. The primary objectives are:

1. **Explicit server control**: Add `iw server start`, `iw server stop`, and `iw server status` commands
2. **Background daemon mode**: Server runs as a background process with PID file tracking
3. **Process management**: Spawn background process, manage PID file, detect running state
4. **Health check integration**: Verify server is responding before declaring success
5. **Graceful shutdown**: Clean shutdown that removes PID file and terminates process
6. **Port configuration**: Move from hardcoded port to config file at `~/.local/share/iw/server/config.json`
7. **Status reporting**: Show server state, port, worktree count, uptime

After this phase, developers will have full control over when the server runs, can check its status, and can stop it when not needed.

---

## Scope

### In Scope

**Port Configuration:**
- Config file at `~/.local/share/iw/server/config.json`
- Format: `{"port": 9876}`
- Create with default port 9876 if missing
- All components (CaskServer, ServerClient, dashboard.scala) read from this single source of truth
- If port in use by another process, error with clear message "Port 9876 is already in use"

**Process Management:**
- PID file at `~/.local/share/iw/server/server.pid`
- Background process spawning (daemon mode)
- PID file creation on start, removal on stop
- Process detection from PID file (check if process exists)
- Handle stale PID files (process dead but file remains)

**Server Start Command:**
- `iw server start` command
- Starts server in background (detached process)
- Writes PID to `~/.local/share/iw/server/server.pid`
- Waits for health check before declaring success
- Output: "Server started on http://localhost:9876" (or configured port)
- Error if already running: "Server is already running on port 9876 (PID: 12345)"

**Server Stop Command:**
- `iw server stop` command
- Reads PID from file, sends termination signal (SIGTERM)
- Waits for process to exit (with timeout)
- Removes PID file after successful shutdown
- Output: "Server stopped"
- Handles cases: no PID file, stale PID, process already dead

**Server Status Command:**
- `iw server status` command
- Shows: running state, port, worktree count, uptime
- Example output when running:
  ```
  Server running on port 9876
  Tracking 5 worktrees
  Uptime: 2h 34m
  PID: 12345
  ```
- Example output when stopped:
  ```
  Server is not running
  ```

**Status Endpoint:**
- `GET /api/status` endpoint on CaskServer
- Returns JSON: `{"status": "running", "port": 9876, "worktreeCount": 5, "startedAt": "2025-12-20T10:00:00Z"}`
- Used by `iw server status` to get runtime information

**Integration Changes:**
- Modify `dashboard.scala` to use config file for port
- Modify `ServerClient` to read port from config file
- Modify `CaskServer.start()` to accept port from caller (caller reads config)
- Auto-start in ServerClient continues to work (uses config file port)

### Out of Scope

**Not in Phase 3 (deferred to later phases):**
- Issue data fetching from tracker API (Phase 4)
- Phase/task progress parsing (Phase 5)
- Git status and PR detection (Phase 6)
- Unregister endpoint and auto-pruning (Phase 7)
- systemd service file template (future consideration)
- Multiple port support or dynamic port allocation (YAGNI)
- Server log file rotation (stderr output is sufficient for now)
- Server restart command (user can stop + start manually)

**Technical Scope Boundaries:**
- Process management: UNIX signals only (SIGTERM for graceful shutdown). No Windows-specific process management in Phase 3.
- PID file locking: Simple existence check, no file locking to prevent multiple servers (port conflict is sufficient detection)
- Uptime tracking: Server tracks own start time in memory, not persisted to state file
- Health check: Reuse existing `/health` endpoint, no new health metrics

---

## Dependencies

### Prerequisites from Phase 1 & 2

**Must exist and work correctly:**
- `CaskServer` with `/`, `/health`, and `PUT /api/v1/worktrees/:issueId` endpoints
- `WorktreeRegistration` domain model with validation
- `ServerState` with worktree map and `listByActivity` method
- `StateRepository` for JSON persistence at `~/.local/share/iw/server/state.json`
- `ServerStateService` for state load/save operations
- `ServerClient` with `registerWorktree()` and `updateLastSeen()` methods
- `ServerClient.isHealthy()` health check method
- `dashboard.scala` command that starts server and opens browser
- Auto-registration in `start.scala`, `open.scala`, `issue.scala`

**Available for reuse:**
- `ServerClient.isHealthy(port)` for health checks
- `ServerClient.ensureServerRunning()` pattern (will need modification)
- Platform detection code from `dashboard.scala` (for OS-specific process management if needed)
- JSON serialization with upickle
- Error handling patterns from existing commands

**Directory structure:**
- `~/.local/share/iw/server/` directory exists (created in Phase 1)
- State file: `~/.local/share/iw/server/state.json`
- Config file (new): `~/.local/share/iw/server/config.json`
- PID file (new): `~/.local/share/iw/server/server.pid`

### External Dependencies

**New command-line requirements:**
- `ps` command for process existence checking (standard UNIX tool)
- `kill` command for sending SIGTERM (standard UNIX tool)

**Scala/JVM capabilities:**
- `ProcessBuilder` for spawning background processes
- `java.lang.ProcessHandle` for process management (Java 9+)
- File I/O for PID file and config file management

---

## Technical Approach

### High-Level Strategy

Phase 3 follows the **Functional Core / Imperative Shell** pattern:

**Domain Layer (Pure):**
- `ServerStatus` case class (running: Boolean, port: Int, worktreeCount: Int, startedAt: Option[Instant], pid: Option[Int])
- `ServerConfig` case class (port: Int)
- Port validation logic

**Application Layer (Pure):**
- `ServerLifecycleService` with pure functions:
  - `parseConfig(json: String): Either[String, ServerConfig]`
  - `createStatus(state: ServerState, startedAt: Instant, pid: Int): ServerStatus`
  - `formatUptime(startedAt: Instant, now: Instant): String`

**Infrastructure Layer (Effects):**
- `ConfigRepository` - Read/write config.json with port
- `ProcessManager` - Spawn background process, manage PID file, check if process exists
- `CaskServer` - Add `GET /api/status` endpoint that reads server start time from instance variable

**Presentation Layer:**
- `server.scala` command with subcommands: start, stop, status
- Output formatting for user-facing messages

### Implementation Flow

**For `iw server start`:**
1. Read config file (or create with default port 9876)
2. Check if server already running (PID file exists and process alive)
3. If running, error with message
4. Spawn background process running CaskServer on configured port
5. Write PID to file
6. Wait for health check with timeout (5 seconds)
7. Output success message with port

**For `iw server stop`:**
1. Read PID file
2. If no file or empty, output "Server is not running"
3. Send SIGTERM to process
4. Wait for process to exit (timeout: 10 seconds)
5. Remove PID file
6. Output "Server stopped"

**For `iw server status`:**
1. Read PID file
2. Check if process exists
3. If not running, output "Server is not running"
4. If running, call `GET /api/status` endpoint
5. Format and display status information

**Background Process Spawning:**
```scala
// Conceptual approach (not final implementation)
val processBuilder = new ProcessBuilder(
  "scala-cli", "run", ".iw/core/CaskServer.scala",
  "--", statePath, port.toString
)
processBuilder.redirectOutput(/* log file or /dev/null */)
processBuilder.redirectError(/* log file or stderr */)
val process = processBuilder.start()
val pid = process.pid()
writePidFile(pid)
```

**PID File Management:**
- Write: `Files.write(Paths.get(pidPath), pid.toString.getBytes)`
- Read: `Files.readString(Paths.get(pidPath)).trim.toInt`
- Check alive: `ProcessHandle.of(pid).isPresent && ProcessHandle.of(pid).get().isAlive()`
- Remove: `Files.deleteIfExists(Paths.get(pidPath))`

### Config File Format

**`~/.local/share/iw/server/config.json`:**
```json
{
  "port": 9876
}
```

**Default creation logic:**
- If file doesn't exist, create parent directory and write default
- Validate port is in range 1024-65535
- If invalid, error with message and suggest correction

### Status Endpoint Design

**Request:** `GET /api/status`

**Response:**
```json
{
  "status": "running",
  "port": 9876,
  "worktreeCount": 5,
  "startedAt": "2025-12-20T10:00:00Z"
}
```

**Implementation notes:**
- CaskServer stores `startedAt: Instant` as instance variable set in constructor
- Worktree count from `ServerState.worktrees.size`
- Port from constructor parameter
- Status always "running" (if endpoint responds, server is running)

---

## Files to Modify

### New Files

**Domain Layer:**
- `.iw/core/ServerStatus.scala` - Server status value object
- `.iw/core/ServerConfig.scala` - Configuration value object with validation

**Application Layer:**
- `.iw/core/ServerLifecycleService.scala` - Pure business logic for lifecycle operations

**Infrastructure Layer:**
- `.iw/core/ConfigRepository.scala` - Read/write port config file
- `.iw/core/ProcessManager.scala` - Background process spawning, PID file management, process detection

**Presentation Layer:**
- `.iw/commands/server.scala` - CLI entry point with start/stop/status subcommands

**Tests:**
- `.iw/core/test/ServerStatusTest.scala` - Unit tests for ServerStatus model
- `.iw/core/test/ServerConfigTest.scala` - Unit tests for ServerConfig validation
- `.iw/core/test/ServerLifecycleServiceTest.scala` - Unit tests for lifecycle service
- `.iw/core/test/ConfigRepositoryTest.scala` - Integration tests for config file I/O
- `.iw/core/test/ProcessManagerTest.scala` - Integration tests for process management

### Modified Files

**Infrastructure Layer:**
- `.iw/core/CaskServer.scala`:
  - Add `startedAt: Instant` instance variable
  - Add `GET /api/status` endpoint
  - Modify `CaskServer.start()` signature to accept port explicitly (caller reads config)

**Presentation Layer:**
- `.iw/commands/dashboard.scala`:
  - Read port from config file instead of hardcoded 9876
  - Handle missing config by creating default

**Infrastructure Layer (Client):**
- `.iw/core/ServerClient.scala`:
  - Read port from config file instead of hardcoded default
  - Update `ensureServerRunning()` to use config port
  - Update all method signatures that default to port 9876

---

## Testing Strategy

### Unit Tests

**ServerStatus:**
- Valid status creation
- Uptime calculation (difference between now and startedAt)
- Format uptime as human-readable string (2h 34m, 5m 12s, etc.)

**ServerConfig:**
- Valid port numbers (9876, 8080, etc.)
- Invalid ports (0, 70000, negative)
- JSON parsing success and failure cases

**ServerLifecycleService:**
- Parse valid config JSON
- Parse invalid config JSON
- Create status from state and runtime info
- Format uptime (various durations)

### Integration Tests

**ConfigRepository:**
- Write config file and read back
- Create default config if missing
- Handle invalid JSON in config file
- Handle missing parent directory (create it)

**ProcessManager:**
- Spawn background process and verify PID
- Write PID file and read back
- Check if process is alive (mock or real process)
- Stop process with SIGTERM
- Handle stale PID file (process dead)
- Clean up PID file after stop

**CaskServer `/api/status` endpoint:**
- GET returns 200 with valid JSON
- JSON contains correct port, worktreeCount, startedAt
- startedAt is close to server start time (within 1 second tolerance)

### End-to-End Tests

**E2E test scenarios (using BATS or similar):**

1. **Start server explicitly:**
   - Run `iw server start`
   - Verify output contains "Server started on http://localhost:9876"
   - Verify PID file exists and contains valid PID
   - Verify process is running (check with `ps`)
   - Verify health endpoint responds with 200

2. **Server already running error:**
   - Start server with `iw server start`
   - Try to start again
   - Verify error message "Server is already running"
   - Verify exit code is non-zero

3. **Stop server gracefully:**
   - Start server
   - Run `iw server stop`
   - Verify output "Server stopped"
   - Verify PID file is removed
   - Verify process is no longer running

4. **Status when running:**
   - Start server
   - Register 2 worktrees (via ServerClient)
   - Run `iw server status`
   - Verify output shows "Server running on port 9876"
   - Verify output shows "Tracking 2 worktrees"
   - Verify uptime is shown

5. **Status when stopped:**
   - Ensure server is not running
   - Run `iw server status`
   - Verify output "Server is not running"

6. **Auto-start still works:**
   - Ensure server is not running
   - Run `iw dashboard`
   - Verify server starts automatically
   - Verify browser opens

7. **Config file port override:**
   - Create config with port 8080
   - Start server
   - Verify server runs on port 8080
   - Verify `iw dashboard` opens http://localhost:8080

**Test data cleanup:**
- Each test should clean up: stop server, remove PID file, remove config file
- Use temporary directories for state/config/PID files in tests
- Ensure no orphaned processes after test suite

### Regression Tests

**Ensure Phase 1 & 2 functionality still works:**
- Dashboard still displays worktrees
- Auto-registration in `start.scala`, `open.scala`, `issue.scala` still works
- Server health check endpoint still responds
- State persistence still works correctly

---

## Acceptance Criteria

This phase is complete when:

- [ ] `iw server start` launches background server and outputs success message
- [ ] PID file is created at `~/.local/share/iw/server/server.pid` with correct PID
- [ ] Health check passes before declaring start success
- [ ] Starting already-running server produces clear error message
- [ ] `iw server stop` gracefully shuts down server and removes PID file
- [ ] Stopping non-running server outputs "Server is not running" (not error)
- [ ] `iw server status` shows correct state (running vs stopped)
- [ ] Status output includes port, worktree count, uptime when running
- [ ] `GET /api/status` endpoint returns valid JSON with server info
- [ ] Config file at `~/.local/share/iw/server/config.json` is created with default port if missing
- [ ] All components read port from config file (no hardcoded 9876 except as default)
- [ ] Port conflict detected and reported with clear error message
- [ ] Auto-start in `iw dashboard` still works with configured port
- [ ] Auto-start in ServerClient (used by CLI commands) still works with configured port
- [ ] Stale PID files are handled gracefully (don't prevent new start)
- [ ] Unit tests pass for all domain and application layer components
- [ ] Integration tests pass for ProcessManager and ConfigRepository
- [ ] E2E tests pass for all lifecycle commands
- [ ] Phase 1 & 2 functionality is not broken (regression tests pass)

---

## Risk Assessment

### Risk: Background process fails to start

**Likelihood:** Medium
**Impact:** High (server won't run)

**Mitigation:**
- Comprehensive error handling in process spawning
- Check for common failures (permission denied, command not found)
- Clear error messages directing user to manual start if needed
- Health check with timeout ensures we detect failures quickly

### Risk: PID file becomes stale (process dies but file remains)

**Likelihood:** Medium
**Impact:** Medium (prevents new server start)

**Mitigation:**
- Check process existence using `ProcessHandle.of(pid).isPresent && .isAlive()`
- If PID in file is dead, treat as "server not running"
- Allow start to proceed if PID is stale (overwrite file)
- Warn user if stale PID detected

### Risk: Port configuration breaks existing setups

**Likelihood:** Low
**Impact:** Medium (existing workflows stop working)

**Mitigation:**
- Default to port 9876 if config missing (backward compatible)
- Create config automatically on first use
- Clear error messages if port is invalid or in use
- Test migration path (hardcoded 9876 → config file)

### Risk: Process shutdown hangs or fails

**Likelihood:** Low
**Impact:** Medium (orphaned processes)

**Mitigation:**
- Use SIGTERM for graceful shutdown (not SIGKILL immediately)
- Implement timeout (10 seconds) before reporting failure
- If timeout, warn user and suggest manual kill
- Provide PID in error message for manual intervention

### Risk: Cross-platform process management issues

**Likelihood:** Medium (Windows)
**Impact:** High (feature broken on Windows)

**Mitigation:**
- Phase 3 targets UNIX/Linux/macOS only (explicit scope)
- Document Windows limitation in README
- Defer Windows support to future phase if needed
- Use Java ProcessHandle API for better cross-platform compatibility

---

## Open Questions

None. All technical decisions were resolved during analysis review.

The following were clarified:
- Port configuration: Fixed port in config file, no environment variable override
- Process management: UNIX signals (SIGTERM), no Windows support in Phase 3
- PID file handling: Simple existence check, no file locking
- Server start mode: Background daemon for `iw server start`, foreground for `iw dashboard` (for Ctrl+C control)

---

## Implementation Notes

### Recommended Implementation Order

1. **Config file infrastructure** (ConfigRepository, ServerConfig)
   - Easiest, no process management complexity
   - Enables testing of port override in later steps

2. **Status endpoint** (`GET /api/status` in CaskServer)
   - Simple addition, tests rest of infrastructure
   - Can be tested manually before full lifecycle commands

3. **Process management** (ProcessManager with PID file)
   - Core complexity, needs careful testing
   - Split into: spawn, check alive, stop

4. **Server lifecycle service** (ServerLifecycleService)
   - Pure business logic, can be tested without processes
   - Coordinates config, process, health checks

5. **CLI commands** (server.scala with start/stop/status)
   - Wiring layer, uses components built above
   - Tests should mostly pass if components are correct

6. **Integration updates** (dashboard.scala, ServerClient)
   - Update to use config file
   - Verify auto-start still works

### Code Review Checklist

Before completing Phase 3, verify:
- [ ] All hardcoded port 9876 references removed (except as default in config creation)
- [ ] PID file cleanup happens in all shutdown paths (graceful stop, error cases)
- [ ] Health checks use configured port, not hardcoded
- [ ] Error messages are user-friendly and actionable
- [ ] Process spawning redirects stdout/stderr appropriately (not lost)
- [ ] Stale PID detection works correctly (doesn't prevent start)
- [ ] FCIS pattern maintained (pure domain/application, effects in infrastructure)
- [ ] Unit tests cover edge cases (invalid port, missing config, stale PID)
- [ ] Integration tests clean up resources (no orphaned processes or files)

---

**Phase Status:** Ready for Implementation

**Next Steps:**
1. Generate implementation tasks: `/iterative-works:ag-create-tasks IWLE-100 --phase=3`
2. Begin implementation following task breakdown
3. Run tests continuously during development (TDD)
4. Submit for code review after all tests pass
