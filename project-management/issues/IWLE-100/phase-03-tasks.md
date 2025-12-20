# Phase 3: Server lifecycle management

**Issue:** IWLE-100
**Estimated Effort:** 6-8 hours
**Prerequisites:** Phases 1 and 2 complete

## Overview

This phase adds explicit control over the dashboard server lifecycle with background daemon mode, PID file management, and health check integration. The implementation follows TDD principles and builds incrementally from config infrastructure to full lifecycle commands.

---

## Setup

- [ ] Create directory `~/.local/share/iw/server/` if not exists (should already exist from Phase 1)
- [ ] Ensure test environment has temporary directories for state/config/PID files

---

## 1. Config File Infrastructure (60-90 min)

### RED - Write Failing Tests

- [x] [impl] [ ] [reviewed] Create test file: `.iw/core/test/ServerConfigTest.scala`
- [x] [impl] [ ] [reviewed] Write test: Valid port numbers (9876, 8080, 3000) parse correctly
- [x] [impl] [ ] [reviewed] Write test: Invalid ports (0, -1, 70000, 99999) fail validation
- [x] [impl] [ ] [reviewed] Write test: Port validation rejects values outside 1024-65535 range
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests fail (no ServerConfig yet)

- [x] [impl] [ ] [reviewed] Create test file: `.iw/core/test/ServerConfigRepositoryTest.scala`
- [x] [impl] [ ] [reviewed] Write test: Write config file and read back returns same port
- [x] [impl] [ ] [reviewed] Write test: Create default config if file missing (port 9876)
- [x] [impl] [ ] [reviewed] Write test: Handle invalid JSON in config file (returns error)
- [x] [impl] [ ] [reviewed] Write test: Handle missing parent directory (creates it)
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests fail

### GREEN - Implement Minimal Code

- [x] [impl] [ ] [reviewed] Create domain model: `.iw/core/ServerConfig.scala`
- [x] [impl] [ ] [reviewed] Implement `ServerConfig` case class with port field
- [x] [impl] [ ] [reviewed] Implement port validation (1024-65535 range)
- [x] [impl] [ ] [reviewed] Implement JSON serialization/deserialization with upickle
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify ServerConfig tests pass

- [x] [impl] [ ] [reviewed] Create infrastructure: `.iw/core/ServerConfigRepository.scala`
- [x] [impl] [ ] [reviewed] Implement `read(path: String): Either[String, ServerConfig]`
- [x] [impl] [ ] [reviewed] Implement `write(config: ServerConfig, path: String): Either[String, Unit]`
- [x] [impl] [ ] [reviewed] Implement `getOrCreateDefault(path: String): Either[String, ServerConfig]`
- [x] [impl] [ ] [reviewed] Handle directory creation if parent missing
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify all config tests pass

### REFACTOR - Improve Quality

- [x] [impl] [ ] [reviewed] Review error messages - ensure they are user-friendly
- [x] [impl] [ ] [reviewed] Extract constants (default port 9876, port range)
- [x] [impl] [ ] [reviewed] Ensure immutability (all vals, no vars)
- [x] [impl] [ ] [reviewed] Add scaladoc comments to public methods
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify all tests still pass

**Success Criteria:**
- ServerConfig validates port range correctly
- ConfigRepository reads/writes config.json reliably
- Default config created with port 9876 if file missing
- Clear error messages for invalid config

---

## 2. Status Endpoint in CaskServer (30-45 min)

### RED - Write Failing Tests

- [x] [impl] [ ] [reviewed] Add tests to CaskServerTest.scala for status endpoint
- [x] [impl] [ ] [reviewed] Write test: `GET /api/status` returns 200 OK
- [x] [impl] [ ] [reviewed] Write test: Response contains valid JSON with port, worktreeCount, startedAt
- [x] [impl] [ ] [reviewed] Write test: startedAt timestamp is recent (within 1 second of server start)
- [x] [impl] [ ] [reviewed] Write test: worktreeCount matches ServerState.worktrees.size
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify tests fail

### GREEN - Implement Status Endpoint

- [x] [impl] [ ] [reviewed] Modify `.iw/core/CaskServer.scala`:
  - Add `startedAt: Instant` as instance variable (set in constructor)
  - Add `port: Int` parameter to constructor
- [x] [impl] [ ] [reviewed] Implement `@cask.get("/api/status")` endpoint
- [x] [impl] [ ] [reviewed] Return JSON with: status="running", port, worktreeCount, startedAt
- [x] [impl] [ ] [reviewed] Ensure startedAt uses ISO-8601 format (toString)
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - verify status endpoint tests pass

### REFACTOR - Clean Up

- [x] [impl] [ ] [reviewed] Ensure JSON field names are consistent with design
- [x] [impl] [ ] [reviewed] Verify all existing tests still pass (regression check)
- [x] [impl] [ ] [reviewed] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- `/api/status` endpoint returns correct JSON structure
- startedAt reflects actual server start time
- worktreeCount matches current state
- No regression in existing endpoints

---

## 3. Process Management Infrastructure (90-120 min)

### RED - Write Failing Tests

- [ ] Create test file: `.iw/core/test/ProcessManagerTest.scala`
- [ ] Write test: Write PID file and read back returns same PID
- [ ] Write test: Check if process is alive (mock with current JVM PID)
- [ ] Write test: Detect stale PID file (process dead)
- [ ] Write test: Remove PID file after successful stop
- [ ] Write test: Handle missing PID file gracefully (return None)
- [ ] Run test: `./iw test unit` - verify tests fail

### GREEN - Implement ProcessManager

- [ ] Create infrastructure: `.iw/core/ProcessManager.scala`
- [ ] Implement `writePidFile(pid: Long, path: String): Either[String, Unit]`
- [ ] Implement `readPidFile(path: String): Either[String, Option[Long]]`
- [ ] Implement `isProcessAlive(pid: Long): Boolean` using ProcessHandle
- [ ] Implement `stopProcess(pid: Long, timeoutSeconds: Int): Either[String, Unit]` (SIGTERM)
- [ ] Implement `removePidFile(path: String): Either[String, Unit]`
- [ ] Run test: `./iw test unit` - verify ProcessManager tests pass

### RED - Test Background Process Spawning

- [ ] Write test: Spawn background process and verify PID file created
- [ ] Write test: Background process is detached (daemon mode)
- [ ] Write test: Process stdout/stderr redirected appropriately
- [ ] Run test: `./iw test unit` - verify tests fail

### GREEN - Implement Process Spawning

- [ ] Implement `spawnServerProcess(statePath: String, port: Int, pidPath: String): Either[String, Long]`
- [ ] Use `ProcessBuilder` to spawn `scala-cli run .iw/commands/dashboard.scala`
- [ ] Redirect stdout/stderr to `/dev/null` or log file
- [ ] Write PID to file after successful spawn
- [ ] Return PID on success
- [ ] Run test: `./iw test unit` - verify spawn tests pass

### REFACTOR - Improve Robustness

- [ ] Add timeout handling for process stop (default 10 seconds)
- [ ] Ensure PID file cleanup happens in all paths (success + error)
- [ ] Extract magic numbers to constants (timeouts, signal codes)
- [ ] Add error context to Left(error) messages
- [ ] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- PID file read/write works reliably
- Process alive detection uses ProcessHandle correctly
- Stale PID files detected and handled
- Background process spawning works with proper redirection
- SIGTERM sent correctly to stop process

---

## 4. Server Lifecycle Service (60-90 min)

### RED - Write Failing Tests

- [ ] Create test file: `.iw/core/test/ServerLifecycleServiceTest.scala`
- [ ] Write test: Parse valid config JSON
- [ ] Write test: Parse invalid config JSON (returns error)
- [ ] Write test: Create status from state and runtime info
- [ ] Write test: Format uptime as human-readable string (2h 34m, 5m 12s)
- [ ] Write test: Handle various uptime durations (seconds, minutes, hours)
- [ ] Run test: `./iw test unit` - verify tests fail

### GREEN - Implement ServerLifecycleService

- [ ] Create application service: `.iw/core/ServerLifecycleService.scala`
- [ ] Implement `parseConfig(json: String): Either[String, ServerConfig]`
- [ ] Implement `createStatus(state: ServerState, startedAt: Instant, pid: Long, port: Int): ServerStatus`
- [ ] Implement `formatUptime(startedAt: Instant, now: Instant): String`
- [ ] Handle edge cases: zero duration, very long uptime
- [ ] Run test: `./iw test unit` - verify ServerLifecycleService tests pass

### REFACTOR - Pure Functions

- [ ] Ensure all methods are pure (no side effects)
- [ ] Extract uptime formatting logic to separate method
- [ ] Use immutable data structures throughout
- [ ] Add scaladoc to public API
- [ ] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- Config parsing handles valid and invalid JSON
- Status creation computes fields correctly
- Uptime formatting is human-readable
- All logic is pure (testable without I/O)

---

## 5. Server Status Domain Model (30-45 min)

### RED - Write Failing Tests

- [ ] Create test file: `.iw/core/test/ServerStatusTest.scala`
- [ ] Write test: Create ServerStatus with all fields
- [ ] Write test: Uptime calculation (difference between now and startedAt)
- [ ] Write test: ServerStatus JSON serialization
- [ ] Run test: `./iw test unit` - verify tests fail

### GREEN - Implement ServerStatus

- [ ] Create domain model: `.iw/core/ServerStatus.scala`
- [ ] Implement `ServerStatus` case class with fields:
  - `running: Boolean`
  - `port: Int`
  - `worktreeCount: Int`
  - `startedAt: Option[Instant]`
  - `pid: Option[Long]`
- [ ] Add upickle ReadWriter for JSON serialization
- [ ] Run test: `./iw test unit` - verify ServerStatus tests pass

### REFACTOR - Domain Model

- [ ] Ensure immutability (all vals)
- [ ] Add factory methods if needed (apply/from)
- [ ] Verify field names match API specification
- [ ] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- ServerStatus model has all required fields
- JSON serialization works correctly
- Model is immutable and type-safe

---

## 6. CLI Commands - Server Start (60-90 min)

### RED - Write Failing E2E Test

- [ ] Create test file: `tests/e2e/server-lifecycle.bats`
- [ ] Write E2E test: `iw server start` launches background server
- [ ] Write E2E test: PID file created at correct path
- [ ] Write E2E test: Server responds to health check
- [ ] Write E2E test: Starting already-running server shows error
- [ ] Run test: `./iw test e2e` - verify tests fail

### GREEN - Implement Server Start Command

- [ ] Create command file: `.iw/commands/server.scala`
- [ ] Implement `iw server start` subcommand
- [ ] Flow:
  1. Read config file (or create default)
  2. Check if server already running (PID file + process check)
  3. If running, error with message
  4. Spawn background process via ProcessManager
  5. Write PID file
  6. Wait for health check (5 second timeout)
  7. Output success message with port
- [ ] Run test: `./iw test e2e` - verify server start tests pass

### REFACTOR - Error Handling

- [ ] Ensure clear error messages for common failures
- [ ] Handle port already in use (detect via bind error)
- [ ] Add logging for troubleshooting
- [ ] Extract helper functions (readConfig, checkRunning)
- [ ] Run test: `./iw test e2e` - all tests pass

**Success Criteria:**
- `iw server start` successfully launches server in background
- PID file created and contains correct PID
- Health check passes before declaring success
- Already-running detection works correctly
- Error messages are user-friendly

---

## 7. CLI Commands - Server Stop (45-60 min)

### RED - Write Failing E2E Test

- [ ] Write E2E test: `iw server stop` stops running server
- [ ] Write E2E test: PID file removed after stop
- [ ] Write E2E test: Process no longer running after stop
- [ ] Write E2E test: Stopping non-running server shows message (not error)
- [ ] Run test: `./iw test e2e` - verify tests fail

### GREEN - Implement Server Stop Command

- [ ] Implement `iw server stop` subcommand in `.iw/commands/server.scala`
- [ ] Flow:
  1. Read PID file
  2. If no file or empty, output "Server is not running"
  3. Check if process exists (handle stale PID)
  4. Send SIGTERM via ProcessManager
  5. Wait for process exit (10 second timeout)
  6. Remove PID file
  7. Output "Server stopped"
- [ ] Run test: `./iw test e2e` - verify server stop tests pass

### REFACTOR - Graceful Shutdown

- [ ] Handle timeout gracefully (warn user, suggest manual kill)
- [ ] Ensure PID file cleanup even on error
- [ ] Provide PID in error messages for manual intervention
- [ ] Run test: `./iw test e2e` - all tests pass

**Success Criteria:**
- `iw server stop` terminates server gracefully
- PID file removed after successful stop
- Stale PID files handled without error
- Non-running server produces friendly message

---

## 8. CLI Commands - Server Status (45-60 min)

### RED - Write Failing E2E Test

- [ ] Write E2E test: `iw server status` when running shows details
- [ ] Write E2E test: Status output includes port, worktree count, uptime
- [ ] Write E2E test: Status when stopped shows "Server is not running"
- [ ] Run test: `./iw test e2e` - verify tests fail

### GREEN - Implement Server Status Command

- [ ] Implement `iw server status` subcommand in `.iw/commands/server.scala`
- [ ] Flow when running:
  1. Read PID file
  2. Check if process exists
  3. If not running, output "Server is not running"
  4. Call `GET /api/status` endpoint
  5. Format output:
     ```
     Server running on port 9876
     Tracking 5 worktrees
     Uptime: 2h 34m
     PID: 12345
     ```
- [ ] Run test: `./iw test e2e` - verify status tests pass

### REFACTOR - Output Formatting

- [ ] Extract output formatting to helper function
- [ ] Ensure uptime calculation matches server's uptime
- [ ] Handle API call failures gracefully
- [ ] Run test: `./iw test e2e` - all tests pass

**Success Criteria:**
- `iw server status` shows correct state (running vs stopped)
- Runtime info fetched from `/api/status` endpoint
- Output is clear and well-formatted
- Handles both running and stopped states

---

## 9. Integration - Update CaskServer.start() (30-45 min)

### RED - Write Failing Tests

- [ ] Write test: CaskServer.start() accepts port parameter
- [ ] Write test: Server binds to configured port (not hardcoded 9876)
- [ ] Run test: `./iw test unit` - verify tests fail

### GREEN - Update CaskServer

- [ ] Modify `.iw/core/CaskServer.scala`:
  - Make `port` a required parameter in `start()` method
  - Pass `port` to CaskServer constructor
  - Update Undertow listener to use configured port
- [ ] Update all callers to pass port explicitly:
  - `dashboard.scala` (reads from config)
  - `ServerClient.ensureServerRunning()` (reads from config)
- [ ] Run test: `./iw test unit` - verify tests pass

### REFACTOR - Remove Hardcoded Port

- [ ] Search codebase for hardcoded `9876` references
- [ ] Replace with config file reads or constants
- [ ] Verify default config creation uses constant
- [ ] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- No hardcoded port 9876 except as default in config creation
- CaskServer binds to configured port
- All components read from config file

---

## 10. Integration - Update dashboard.scala (30-45 min)

### RED - Write Failing E2E Test

- [ ] Write E2E test: `iw dashboard` uses port from config file
- [ ] Write E2E test: Dashboard creates default config if missing
- [ ] Write E2E test: Custom port in config is respected
- [ ] Run test: `./iw test e2e` - verify tests fail

### GREEN - Update Dashboard Command

- [ ] Modify `.iw/commands/dashboard.scala`:
  - Read config file at start (use ConfigRepository)
  - If missing, create default config (port 9876)
  - Use configured port for server start
  - Use configured port in URL opened in browser
- [ ] Run test: `./iw test e2e` - verify dashboard tests pass

### REFACTOR - Config Handling

- [ ] Extract config path to constant
- [ ] Ensure error handling for config read failures
- [ ] Verify browser opens correct URL with config port
- [ ] Run test: `./iw test e2e` - all tests pass

**Success Criteria:**
- `iw dashboard` reads port from config file
- Default config created if file missing
- Browser opens to correct port
- Backward compatible (uses 9876 by default)

---

## 11. Integration - Update ServerClient (30-45 min)

### RED - Write Failing Tests

- [ ] Write test: ServerClient reads port from config file
- [ ] Write test: Auto-start uses configured port
- [ ] Write test: Health check uses configured port
- [ ] Run test: `./iw test unit` - verify tests fail

### GREEN - Update ServerClient

- [ ] Modify `.iw/core/ServerClient.scala`:
  - Update method signatures to remove default port 9876
  - Read config file in `ensureServerRunning()`
  - Pass configured port to all internal methods
  - Update `registerWorktree()` and `updateLastSeen()` signatures
- [ ] Update callers in `start.scala`, `open.scala`, `issue.scala`:
  - Remove port parameter (use config)
  - Pass statePath only
- [ ] Run test: `./iw test unit` - verify tests pass

### REFACTOR - Single Source of Truth

- [ ] Ensure config file is read once per operation
- [ ] Cache config reading if performance issue
- [ ] Verify all CLI commands use same config path
- [ ] Run test: `./iw test unit` - all tests pass

**Success Criteria:**
- ServerClient reads port from config file
- Auto-registration uses configured port
- All CLI commands respect config file
- No hardcoded port references remain

---

## 12. End-to-End Validation (60-90 min)

### E2E Test Scenarios

- [ ] **Test 1 - Explicit server start:**
  - Run `iw server start`
  - Verify output contains "Server started on http://localhost:9876"
  - Verify PID file exists at `~/.local/share/iw/server/server.pid`
  - Verify process is running (`ps -p <PID>`)
  - Verify health endpoint responds (`curl http://localhost:9876/health`)

- [ ] **Test 2 - Server already running error:**
  - Start server with `iw server start`
  - Try to start again
  - Verify error message "Server is already running on port 9876 (PID: <PID>)"
  - Verify exit code is non-zero

- [ ] **Test 3 - Stop server gracefully:**
  - Start server
  - Run `iw server stop`
  - Verify output "Server stopped"
  - Verify PID file is removed
  - Verify process is no longer running

- [ ] **Test 4 - Status when running:**
  - Start server
  - Register 2 worktrees via `iw start ISSUE-1` and `iw start ISSUE-2`
  - Run `iw server status`
  - Verify output shows "Server running on port 9876"
  - Verify output shows "Tracking 2 worktrees"
  - Verify uptime is displayed

- [ ] **Test 5 - Status when stopped:**
  - Ensure server is not running
  - Run `iw server status`
  - Verify output "Server is not running"

- [ ] **Test 6 - Auto-start still works:**
  - Ensure server is not running
  - Run `iw dashboard`
  - Verify server starts automatically
  - Verify browser opens

- [ ] **Test 7 - Config file port override:**
  - Create config with port 8080
  - Start server
  - Verify server runs on port 8080 (health check at 8080)
  - Verify `iw dashboard` opens http://localhost:8080

- [ ] **Test 8 - Stale PID file handling:**
  - Manually create PID file with non-existent PID (99999)
  - Run `iw server start`
  - Verify server starts successfully (stale PID detected)
  - Verify new PID file contains correct PID

### Regression Tests

- [ ] **Regression 1 - Dashboard displays worktrees:**
  - Start server
  - Register worktree via `iw start ISSUE-123`
  - Open dashboard
  - Verify worktree appears on page

- [ ] **Regression 2 - Auto-registration in CLI commands:**
  - Start server
  - Run `iw start ISSUE-456`
  - Verify worktree registered (check state.json)
  - Run `iw issue` in worktree directory
  - Verify lastSeenAt updated

- [ ] **Regression 3 - Server health check endpoint:**
  - Start server
  - Call `GET /health`
  - Verify response 200 OK with `{"status": "ok"}`

- [ ] **Regression 4 - State persistence:**
  - Start server
  - Register 3 worktrees
  - Stop server
  - Start server again
  - Verify state.json loaded correctly (3 worktrees present)

---

## Phase 3 Success Criteria

- [ ] All unit tests pass (`./iw test unit`)
- [ ] All E2E tests pass (`./iw test e2e`)
- [ ] `iw server start` launches background server successfully
- [ ] `iw server stop` gracefully shuts down server
- [ ] `iw server status` shows correct running state
- [ ] PID file management works correctly (create, read, remove)
- [ ] Config file created with default port 9876 if missing
- [ ] All components read port from single config file
- [ ] Stale PID files handled gracefully
- [ ] Health check passes before declaring start success
- [ ] Port conflicts detected and reported with clear error
- [ ] Auto-start in `iw dashboard` still works
- [ ] Auto-registration in CLI commands still works
- [ ] No regression in Phase 1 & 2 functionality

---

## Notes

**Implementation Tips:**

1. **Work incrementally**: Complete each numbered section fully before moving to next
2. **Test first**: Write failing tests before any implementation (strict TDD)
3. **Keep tests isolated**: Use temp directories for test data (no shared state)
4. **Clean up resources**: Ensure tests stop servers and remove temp files
5. **Follow existing patterns**: Match code style in CaskServer.scala and ServerClient.scala
6. **Error messages matter**: User-facing errors should be actionable
7. **Handle edge cases**: Stale PIDs, missing files, port conflicts

**Common Pitfalls:**

- Forgetting to stop test servers (use cleanup hooks)
- Hardcoding paths (use configurable paths)
- Not testing stale PID files (critical for robustness)
- Ignoring UNIX vs Windows differences (Phase 3 is UNIX-only)
- Race conditions in health checks (use proper timeouts)

**Dependencies:**

- All Phase 1 components (CaskServer, ServerState, StateRepository)
- All Phase 2 components (ServerClient, auto-registration)
- Existing ProcessHandle API (Java 9+)
- Existing upickle for JSON serialization

---

**Phase Status:** Complete

**Next Phase:** Phase 4 - Show issue details and status from tracker
