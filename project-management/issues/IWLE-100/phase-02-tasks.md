# Phase 2 Tasks: Automatic worktree registration from CLI commands

**Issue:** IWLE-100
**Phase:** 2 of 7
**Story:** Story 2 - Automatic worktree registration from CLI commands
**Status:** Not started
**Created:** 2025-12-20

---

## Task Index

This phase implements automatic worktree registration from CLI commands, enabling the dashboard to track worktrees without manual API calls.

**Goals:**
- Registration endpoint `PUT /api/worktrees/{issueId}`
- ServerClient HTTP client for CLI-to-server communication
- Auto-registration in `iw start`, `iw open`, `iw issue`
- Best-effort registration (failures don't break CLI commands)
- Lazy server start when needed

**Testing approach:**
- Write tests FIRST for every feature
- Test domain logic with unit tests
- Test endpoints with integration tests
- Manual E2E verification of scenarios

---

## Setup Tasks

- [ ] [impl] [ ] [reviewed] Create `.iw/core/test` directory if it doesn't exist
- [ ] [impl] [ ] [reviewed] Verify munit test framework is available in project.scala

---

## Part 1: Pure Domain Logic - WorktreeRegistrationService

### 1.1 Test: WorktreeRegistrationService - Register new worktree

- [x] [test] [ ] [reviewed] Write test: `register` creates new WorktreeRegistration with current timestamp
- [x] [test] [ ] [reviewed] Write test: `register` adds WorktreeRegistration to ServerState.worktrees map
- [x] [test] [ ] [reviewed] Write test: `register` returns Right with new ServerState on success
- [x] [impl] [ ] [reviewed] Create `.iw/core/WorktreeRegistrationService.scala` with `register` method
- [x] [impl] [ ] [reviewed] Implement `register` to create new WorktreeRegistration
- [x] [impl] [ ] [reviewed] Verify all tests pass

### 1.2 Test: WorktreeRegistrationService - Update existing worktree

- [x] [test] [ ] [reviewed] Write test: `register` updates existing worktree's `lastSeenAt` timestamp
- [x] [test] [ ] [reviewed] Write test: `register` preserves `registeredAt` timestamp on update
- [x] [test] [ ] [reviewed] Write test: `register` updates path/trackerType/team if changed
- [x] [impl] [ ] [reviewed] Implement upsert logic in `register` method
- [x] [impl] [ ] [reviewed] Verify all tests pass

### 1.3 Test: WorktreeRegistrationService - updateLastSeen

- [x] [test] [ ] [reviewed] Write test: `updateLastSeen` updates timestamp for existing worktree
- [x] [test] [ ] [reviewed] Write test: `updateLastSeen` returns Left for non-existent worktree
- [x] [test] [ ] [reviewed] Write test: `updateLastSeen` preserves all other fields unchanged
- [x] [impl] [ ] [reviewed] Implement `updateLastSeen` method
- [x] [impl] [ ] [reviewed] Verify all tests pass

### 1.4 Test: WorktreeRegistrationService - Validation

- [x] [test] [ ] [reviewed] Write test: `register` returns Left for invalid issue ID format
- [x] [test] [ ] [reviewed] Write test: `register` returns Left for empty path
- [x] [test] [ ] [reviewed] Write test: `register` returns Left for invalid tracker type
- [x] [impl] [ ] [reviewed] Add validation logic to `register` method
- [x] [impl] [ ] [reviewed] Verify all tests pass

### 1.5 Commit

- [x] [impl] [ ] [reviewed] Run all unit tests for WorktreeRegistrationService
- [x] [impl] [ ] [reviewed] Commit: "feat(IWLE-100): Add WorktreeRegistrationService with tests"

---

## Part 2: HTTP Endpoint - CaskServer Registration Route

### 2.1 Test: PUT /api/worktrees/{issueId} - Success cases

- [x] [test] [ ] [reviewed] Write integration test: PUT with valid body creates new registration
- [x] [test] [ ] [reviewed] Write integration test: PUT returns 200 with registration JSON
- [x] [test] [ ] [reviewed] Write integration test: Response includes issueId, lastSeenAt, status
- [x] [test] [ ] [reviewed] Write integration test: Second PUT updates existing registration
- [x] [impl] [ ] [reviewed] Add `@cask.put("/api/worktrees/:issueId")` route to CaskServer.scala
- [x] [impl] [ ] [reviewed] Parse request body using upickle
- [x] [impl] [ ] [reviewed] Call WorktreeRegistrationService.register
- [x] [impl] [ ] [reviewed] Persist new state via StateRepository.write
- [x] [impl] [ ] [reviewed] Return 200 with success JSON
- [x] [impl] [ ] [reviewed] Verify integration tests pass

### 2.2 Test: PUT /api/worktrees/{issueId} - Error cases

- [x] [test] [ ] [reviewed] Write integration test: PUT with malformed JSON returns 400
- [x] [test] [ ] [reviewed] Write integration test: PUT with missing fields returns 400
- [x] [test] [ ] [reviewed] Write integration test: PUT with invalid issueId returns 400
- [x] [test] [ ] [reviewed] Write integration test: State persistence failure returns 500
- [x] [impl] [ ] [reviewed] Add error handling for JSON parsing errors
- [x] [impl] [ ] [reviewed] Add error handling for validation failures
- [x] [impl] [ ] [reviewed] Add error handling for state persistence failures
- [x] [impl] [ ] [reviewed] Return appropriate HTTP status codes
- [x] [impl] [ ] [reviewed] Verify all error tests pass

### 2.3 Manual testing and commit

- [x] [impl] [ ] [reviewed] Start server manually and verify health endpoint works
- [x] [impl] [ ] [reviewed] Test PUT endpoint with curl: register new worktree
- [x] [impl] [ ] [reviewed] Test PUT endpoint with curl: update existing worktree
- [x] [impl] [ ] [reviewed] Verify state.json is created/updated correctly
- [x] [impl] [ ] [reviewed] Commit: "feat(IWLE-100): Add PUT /api/worktrees/{issueId} endpoint"

---

## Part 3: HTTP Client - ServerClient

### 3.1 Test: ServerClient - Health check

- [ ] [test] [ ] [reviewed] Write test: `isHealthy` returns true when server responds to /health
- [ ] [test] [ ] [reviewed] Write test: `isHealthy` returns false when server is not running
- [ ] [test] [ ] [reviewed] Write test: `isHealthy` returns false on connection error
- [x] [impl] [ ] [reviewed] Create `.iw/core/ServerClient.scala` with `isHealthy` method
- [x] [impl] [ ] [reviewed] Implement `isHealthy` using sttp GET to /health endpoint
- [x] [impl] [ ] [reviewed] Handle connection errors and timeouts
- [x] [impl] [ ] [reviewed] Verify health check tests pass

### 3.2 Test: ServerClient - registerWorktree

- [ ] [test] [ ] [reviewed] Write test: `registerWorktree` sends PUT request with correct body
- [ ] [test] [ ] [reviewed] Write test: `registerWorktree` returns Right(()) on 200 response
- [ ] [test] [ ] [reviewed] Write test: `registerWorktree` returns Left(error) on 400 response
- [ ] [test] [ ] [reviewed] Write test: `registerWorktree` returns Left(error) on 500 response
- [ ] [test] [ ] [reviewed] Write test: `registerWorktree` returns Left(error) on connection failure
- [x] [impl] [ ] [reviewed] Implement `registerWorktree` method
- [x] [impl] [ ] [reviewed] Build JSON request body with path, trackerType, team
- [x] [impl] [ ] [reviewed] Send PUT request using sttp
- [x] [impl] [ ] [reviewed] Parse response and return Either
- [x] [impl] [ ] [reviewed] Handle all error cases gracefully
- [x] [impl] [ ] [reviewed] Verify all registration tests pass

### 3.3 Test: ServerClient - updateLastSeen

- [ ] [test] [ ] [reviewed] Write test: `updateLastSeen` sends PUT request with minimal body
- [ ] [test] [ ] [reviewed] Write test: `updateLastSeen` returns Right(()) on success
- [ ] [test] [ ] [reviewed] Write test: `updateLastSeen` returns Left(error) on failure
- [x] [impl] [ ] [reviewed] Implement `updateLastSeen` method
- [x] [impl] [ ] [reviewed] Send PUT request to existing endpoint (reuse registerWorktree logic)
- [x] [impl] [ ] [reviewed] Verify update tests pass

### 3.4 Test: ServerClient - ensureServerRunning

- [ ] [test] [ ] [reviewed] Write test: `ensureServerRunning` returns Right if server is healthy
- [ ] [test] [ ] [reviewed] Write test: `ensureServerRunning` starts server if not running
- [ ] [test] [ ] [reviewed] Write test: `ensureServerRunning` waits for health check after start
- [ ] [test] [ ] [reviewed] Write test: `ensureServerRunning` returns Left if start fails
- [ ] [test] [ ] [reviewed] Write test: `ensureServerRunning` times out after 5 seconds
- [x] [impl] [ ] [reviewed] Implement `ensureServerRunning` method
- [x] [impl] [ ] [reviewed] Check health first, return Right if already running
- [x] [impl] [ ] [reviewed] Start server in daemon thread if not running
- [x] [impl] [ ] [reviewed] Implement health check polling with timeout
- [x] [impl] [ ] [reviewed] Return Left on timeout or start failure
- [x] [impl] [ ] [reviewed] Verify server lifecycle tests pass

### 3.5 Integration: ServerClient with real server

- [x] [impl] [ ] [reviewed] Start test server on ephemeral port for integration tests
- [x] [impl] [ ] [reviewed] Test registerWorktree against real CaskServer endpoint
- [x] [impl] [ ] [reviewed] Test updateLastSeen against real CaskServer endpoint
- [x] [impl] [ ] [reviewed] Verify state.json is updated correctly
- [x] [impl] [ ] [reviewed] Verify all integration tests pass

### 3.6 Commit

- [x] [impl] [ ] [reviewed] Run all ServerClient tests
- [x] [impl] [ ] [reviewed] Commit: "feat(IWLE-100): Add ServerClient for CLI-to-server communication"

---

## Part 4: CLI Integration - start.scala

### 4.1 Test: start.scala - Registration after worktree creation

- [ ] [test] [ ] [reviewed] Write test: start command registers worktree after successful creation
- [ ] [test] [ ] [reviewed] Write test: registration includes correct path, trackerType, team
- [ ] [test] [ ] [reviewed] Write test: registration failure shows warning but doesn't fail command
- [ ] [test] [ ] [reviewed] Write test: start command succeeds even if server unavailable
- [x] [impl] [ ] [reviewed] Read `.iw/commands/start.scala` to understand current flow
- [x] [impl] [ ] [reviewed] Add ServerClient.registerWorktree call after worktree creation
- [x] [impl] [ ] [reviewed] Extract trackerType from config
- [x] [impl] [ ] [reviewed] Extract team from issueId
- [x] [impl] [ ] [reviewed] Add error handling: Left(error) -> Output.warn
- [x] [impl] [ ] [reviewed] Add error handling: Right(_) -> silent success
- [x] [impl] [ ] [reviewed] Verify registration happens before tmux session
- [x] [impl] [ ] [reviewed] Verify all start command tests pass

### 4.2 Manual testing and commit

- [ ] [impl] [ ] [reviewed] Run `./iw start IWLE-TEST-100` with server stopped
- [ ] [impl] [ ] [reviewed] Verify worktree is created successfully
- [ ] [impl] [ ] [reviewed] Verify server auto-starts
- [ ] [impl] [ ] [reviewed] Verify state.json contains IWLE-TEST-100
- [ ] [impl] [ ] [reviewed] Verify dashboard shows the new worktree
- [ ] [impl] [ ] [reviewed] Clean up test worktree
- [ ] [impl] [ ] [reviewed] Commit: "feat(IWLE-100): Auto-register worktrees in iw start"

---

## Part 5: CLI Integration - open.scala

### 5.1 Test: open.scala - Update lastSeenAt when opening

- [ ] [test] [ ] [reviewed] Write test: open command updates lastSeenAt timestamp
- [ ] [test] [ ] [reviewed] Write test: update failure shows warning but doesn't fail command
- [ ] [test] [ ] [reviewed] Write test: open command succeeds even if server unavailable
- [x] [impl] [ ] [reviewed] Read `.iw/commands/open.scala` to understand current flow
- [x] [impl] [ ] [reviewed] Add ServerClient.updateLastSeen call at entry point
- [x] [impl] [ ] [reviewed] Extract issueId from command arguments
- [x] [impl] [ ] [reviewed] Add error handling: Left(error) -> Output.warn
- [x] [impl] [ ] [reviewed] Verify all open command tests pass

### 5.2 Manual testing and commit

- [ ] [impl] [ ] [reviewed] Create test worktree with old lastSeenAt
- [ ] [impl] [ ] [reviewed] Wait 5 seconds to ensure timestamp difference
- [ ] [impl] [ ] [reviewed] Run `./iw open IWLE-TEST-100`
- [ ] [impl] [ ] [reviewed] Verify state.json shows newer lastSeenAt
- [ ] [impl] [ ] [reviewed] Verify dashboard shows updated timestamp
- [ ] [impl] [ ] [reviewed] Clean up test worktree
- [ ] [impl] [ ] [reviewed] Commit: "feat(IWLE-100): Update lastSeen in iw open"

---

## Part 6: CLI Integration - issue.scala

### 6.1 Test: issue.scala - Update lastSeenAt on completion

- [ ] [test] [ ] [reviewed] Write test: issue command updates lastSeenAt on success
- [ ] [test] [ ] [reviewed] Write test: update failure is silently ignored (best-effort)
- [ ] [test] [ ] [reviewed] Write test: issue command succeeds even if server unavailable
- [x] [impl] [ ] [reviewed] Read `.iw/commands/issue.scala` to understand current flow
- [x] [impl] [ ] [reviewed] Add ServerClient.updateLastSeen call at successful completion
- [x] [impl] [ ] [reviewed] Extract issueId from current worktree context
- [x] [impl] [ ] [reviewed] Handle errors silently (already at command exit)
- [x] [impl] [ ] [reviewed] Verify all issue command tests pass

### 6.2 Manual testing and commit

- [ ] [impl] [ ] [reviewed] Create test worktree
- [ ] [impl] [ ] [reviewed] cd into worktree directory
- [ ] [impl] [ ] [reviewed] Run `./iw issue`
- [ ] [impl] [ ] [reviewed] Verify lastSeenAt is updated in state.json
- [ ] [impl] [ ] [reviewed] Verify dashboard shows updated time
- [ ] [impl] [ ] [reviewed] Clean up test worktree
- [ ] [impl] [ ] [reviewed] Commit: "feat(IWLE-100): Update lastSeen in iw issue"

---

## Part 7: End-to-End Verification

### 7.1 E2E Scenario 1: Creating worktree registers it

- [ ] [impl] [ ] [reviewed] Clean slate: `rm -f ~/.local/share/iw/server/state.json`
- [ ] [impl] [ ] [reviewed] Stop any running server
- [ ] [impl] [ ] [reviewed] Run `./iw start IWLE-E2E-001`
- [ ] [impl] [ ] [reviewed] Verify worktree created successfully
- [ ] [impl] [ ] [reviewed] Verify state.json contains IWLE-E2E-001
- [ ] [impl] [ ] [reviewed] Verify dashboard shows IWLE-E2E-001
- [ ] [impl] [ ] [reviewed] Verify registration includes path, trackerType, team
- [ ] [impl] [ ] [reviewed] Clean up test worktree

### 7.2 E2E Scenario 2: Opening worktree updates timestamp

- [ ] [impl] [ ] [reviewed] Create worktree IWLE-E2E-002 with old timestamp
- [ ] [impl] [ ] [reviewed] Record original lastSeenAt value
- [ ] [impl] [ ] [reviewed] Wait 5 seconds
- [ ] [impl] [ ] [reviewed] Run `./iw open IWLE-E2E-002`
- [ ] [impl] [ ] [reviewed] Verify state.json has newer lastSeenAt
- [ ] [impl] [ ] [reviewed] Verify dashboard shows updated time
- [ ] [impl] [ ] [reviewed] Clean up test worktree

### 7.3 E2E Scenario 3: Issue command updates timestamp

- [ ] [impl] [ ] [reviewed] Create and register worktree IWLE-E2E-003
- [ ] [impl] [ ] [reviewed] cd into worktree directory
- [ ] [impl] [ ] [reviewed] Record original lastSeenAt
- [ ] [impl] [ ] [reviewed] Run `./iw issue`
- [ ] [impl] [ ] [reviewed] Verify lastSeenAt was updated
- [ ] [impl] [ ] [reviewed] Verify dashboard reflects new timestamp
- [ ] [impl] [ ] [reviewed] Clean up test worktree

### 7.4 E2E Scenario 4: Registration fails gracefully when server unavailable

- [ ] [impl] [ ] [reviewed] Stop server explicitly
- [ ] [impl] [ ] [reviewed] Modify ensureServerRunning to fail (temporarily)
- [ ] [impl] [ ] [reviewed] Run `./iw start IWLE-E2E-004`
- [ ] [impl] [ ] [reviewed] Verify worktree is created successfully
- [ ] [impl] [ ] [reviewed] Verify warning message is shown
- [ ] [impl] [ ] [reviewed] Verify command exits with success status
- [ ] [impl] [ ] [reviewed] Restore ensureServerRunning
- [ ] [impl] [ ] [reviewed] Clean up test worktree

### 7.5 E2E Scenario 5: Server auto-starts on first registration

- [ ] [impl] [ ] [reviewed] Stop server if running
- [ ] [impl] [ ] [reviewed] Run `./iw start IWLE-E2E-005`
- [ ] [impl] [ ] [reviewed] Verify server starts automatically
- [ ] [impl] [ ] [reviewed] Verify health check succeeds
- [ ] [impl] [ ] [reviewed] Verify registration completes
- [ ] [impl] [ ] [reviewed] Verify worktree appears on dashboard
- [ ] [impl] [ ] [reviewed] Clean up test worktree

---

## Part 8: Documentation and Cleanup

### 8.1 Code quality checks

- [ ] [impl] [ ] [reviewed] Run all unit tests: WorktreeRegistrationService
- [ ] [impl] [ ] [reviewed] Run all unit tests: ServerClient
- [ ] [impl] [ ] [reviewed] Run all integration tests: CaskServer
- [ ] [impl] [ ] [reviewed] Run all integration tests: CLI commands
- [ ] [impl] [ ] [reviewed] Check for compilation warnings
- [ ] [impl] [ ] [reviewed] Fix any warnings found
- [ ] [impl] [ ] [reviewed] Verify no regression in existing tests

### 8.2 Code review self-checks

- [ ] [impl] [ ] [reviewed] Verify ServerClient follows LinearClient patterns
- [ ] [impl] [ ] [reviewed] Verify all error paths return Either (no exceptions)
- [ ] [impl] [ ] [reviewed] Verify registration is truly best-effort (non-blocking)
- [ ] [impl] [ ] [reviewed] Verify CLI commands work when server is unavailable
- [ ] [impl] [ ] [reviewed] Verify lazy server start uses daemon thread
- [ ] [impl] [ ] [reviewed] Verify state.json writes are atomic (tmp + rename)

### 8.3 Documentation

- [ ] [impl] [ ] [reviewed] Add PURPOSE comments to new files
- [ ] [impl] [ ] [reviewed] Document complex parts (lazy start logic, error handling)
- [ ] [impl] [ ] [reviewed] Update implementation-log.md with Phase 2 summary
- [ ] [impl] [ ] [reviewed] Document key decisions and patterns used
- [ ] [impl] [ ] [reviewed] Commit: "docs(IWLE-100): Document Phase 2 implementation"

---

## Phase Success Criteria

Phase 2 is complete when:

### Functional Requirements

- [ ] PUT /api/worktrees/{issueId} endpoint implemented and working
  - [ ] Accepts JSON body with path, trackerType, team
  - [ ] Creates new registration if not exists
  - [ ] Updates lastSeenAt if already exists
  - [ ] Returns 200 with registration data on success
  - [ ] Returns 400 for invalid request body
  - [ ] Returns 500 on state persistence errors

- [ ] ServerClient HTTP client implemented
  - [ ] `registerWorktree()` sends PUT request
  - [ ] `updateLastSeen()` sends PUT request
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
- [ ] All E2E scenarios verified manually
- [ ] Code follows existing patterns (sttp usage, Either error handling)
- [ ] No new compilation warnings
- [ ] Git commits follow TDD: test -> implementation -> refactor

### Quality Checks

- [ ] ServerClient follows LinearClient patterns
- [ ] All error paths handled gracefully
- [ ] Registration is truly best-effort (non-blocking)
- [ ] Documentation updated with Phase 2 summary
- [ ] Complex parts commented appropriately

---

## Estimated Time

**Total: 6-8 hours**

- Part 1 (Domain logic): 1-2h
- Part 2 (HTTP endpoint): 1-2h
- Part 3 (HTTP client): 1-2h
- Part 4-6 (CLI integration): 2-3h
- Part 7 (E2E verification): 0.5-1h
- Part 8 (Documentation): 0.5h

---

## Notes

**TDD is mandatory for every task:**
1. Write failing test
2. Implement minimum code to pass
3. Verify test passes
4. Refactor while keeping tests green
5. Add additional test cases

**Best-effort philosophy:**
- Registration failures must not break CLI commands
- Use Either for all error handling (no exceptions)
- Log warnings for failures
- Commands succeed even if server is unavailable

**Lazy server start:**
- Reuse dashboard.scala pattern
- Daemon thread for background server
- 5-second timeout for health check
- If timeout, treat as unavailable (log warning)

**When to ask for help:**
- Task is ambiguous or unclear
- Implementation requires changing domain boundaries
- Unsure about pattern usage
- Tests are failing and reason is not obvious

---

**Status:** Ready for Implementation
**Next:** Run `/iterative-works:ag-implement IWLE-100 --phase 2`
