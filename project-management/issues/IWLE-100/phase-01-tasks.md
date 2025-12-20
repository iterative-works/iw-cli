# Phase 1 Tasks: View basic dashboard with registered worktrees

**Issue:** IWLE-100
**Phase:** 1 of 7
**Status:** Complete
**Estimated:** 8-12 hours

## Task List

### Setup (Est: 0.5h)

- [x] [setup] Add Cask 0.9.4 and Scalatags 0.13.1 dependencies to `.iw/core/project.scala`
- [x] [setup] Create directory structure `~/.local/share/iw/server/` for state persistence
- [x] [setup] Verify test infrastructure works with `./iw test`

### Domain Layer: WorktreeRegistration (Est: 1h)

- [x] [test] Write test for WorktreeRegistration creation with all fields (issueId, path, trackerType, team, timestamps)
- [x] [impl] Implement WorktreeRegistration case class in `.iw/core/src/iw/domain/WorktreeRegistration.scala`
- [x] [test] Write test for WorktreeRegistration with invalid issueId (empty string)
- [x] [impl] Add validation to WorktreeRegistration constructor

### Domain Layer: ServerState (Est: 1h)

- [x] [test] Write test for ServerState with empty worktrees map
- [x] [test] Write test for ServerState.listByActivity sorting (most recent first)
- [x] [impl] Implement ServerState case class in `.iw/core/src/iw/domain/ServerState.scala`
- [x] [impl] Implement listByActivity method returning sorted List[WorktreeRegistration]
- [x] [test] Write test for ServerState with multiple worktrees (verify sort order)
- [x] [impl] Verify sorting implementation handles edge cases (same timestamp, null timestamps)

### Infrastructure Layer: StateRepository (Est: 2h)

- [x] [test] Write test for StateRepository.read with non-existent file (should create empty state)
- [x] [impl] Implement StateRepository.read in `.iw/core/src/iw/infrastructure/StateRepository.scala`
- [x] [test] Write test for StateRepository.write serializes ServerState to JSON correctly
- [x] [impl] Implement StateRepository.write with JSON serialization
- [x] [test] Write test for StateRepository.write uses atomic writes (tmp file + rename)
- [x] [impl] Implement atomic write logic (write to .tmp file, rename to .json)
- [x] [test] Write test for StateRepository.read handles malformed JSON gracefully
- [x] [impl] Add error handling for malformed JSON (return Left with error message)
- [x] [test] Write integration test: write state, read it back, verify equality
- [x] [impl] Verify JSON format matches spec from phase-01-context.md

### Application Layer: ServerStateService (Est: 1.5h)

- [x] [impl] [reviewed] Write test for ServerStateService.load returns Right(ServerState) for valid state file
- [x] [impl] [reviewed] Implement ServerStateService.load in `.iw/core/src/iw/application/ServerStateService.scala`
- [x] [impl] [reviewed] Write test for ServerStateService.load returns Right(empty state) when file missing
- [x] [impl] [reviewed] Handle missing file case in load method
- [x] [impl] [reviewed] Write test for ServerStateService.save persists state correctly
- [x] [impl] [reviewed] Implement ServerStateService.save method
- [x] [impl] [reviewed] Write test for ServerStateService.listWorktrees returns sorted worktrees
- [x] [impl] [reviewed] Implement ServerStateService.listWorktrees (delegates to ServerState.listByActivity)

### Presentation Layer: WorktreeListView (Est: 1.5h)

- [x] [impl] [reviewed] Write test for WorktreeListView renders empty state message
- [x] [impl] [reviewed] Implement WorktreeListView.render in `.iw/core/src/iw/presentation/views/WorktreeListView.scala`
- [x] [impl] [reviewed] Write test for WorktreeListView renders single worktree card with all fields
- [x] [impl] [reviewed] Implement worktree card rendering with Scalatags (issue ID, title placeholder, timestamp)
- [x] [impl] [reviewed] Write test for WorktreeListView renders multiple worktree cards
- [x] [impl] [reviewed] Verify card layout matches design from phase-01-context.md
- [x] [impl] [reviewed] Write test for relative timestamp formatting ("2h ago", "1d ago")
- [x] [impl] [reviewed] Implement relative timestamp helper function

### Application Layer: DashboardService (Est: 1h)

- [x] [impl] [reviewed] Write test for DashboardService.renderDashboard with empty worktrees
- [x] [impl] [reviewed] Implement DashboardService.renderDashboard in `.iw/core/src/iw/application/DashboardService.scala`
- [x] [impl] [reviewed] Write test for DashboardService.renderDashboard with populated worktrees
- [x] [impl] [reviewed] Integrate WorktreeListView into full page HTML (header, body, minimal CSS)
- [x] [impl] [reviewed] Write test verifying generated HTML is valid (contains doctype, html, head, body)
- [x] [impl] [reviewed] Add inline CSS for basic styling (card borders, spacing, colors)

### Infrastructure Layer: CaskServer (Est: 2h)

- [x] [impl] [reviewed] Write integration test for GET /health returns 200 OK with JSON {"status":"ok"}
- [x] [impl] [reviewed] Implement CaskServer in `.iw/core/src/iw/infrastructure/CaskServer.scala` with /health endpoint
- [x] [impl] [reviewed] Write integration test for GET / returns 200 OK with HTML content
- [x] [impl] [reviewed] Implement GET / route calling DashboardService.renderDashboard
- [x] [impl] [reviewed] Write integration test for server binds to port 9876 successfully
- [x] [impl] [reviewed] Configure Cask to bind to hardcoded port 9876
- [ ] [impl] [ ] [reviewed] Write test for server handles port already in use gracefully
- [ ] [impl] [ ] [reviewed] Add error handling for port binding failures with clear error message
- [ ] [impl] [ ] [reviewed] Write integration test: start server, load state, verify dashboard shows worktrees
- [x] [impl] [reviewed] Wire ServerStateService into CaskServer routes

### Presentation Layer: dashboard.scala command (Est: 1.5h)

- [ ] [impl] [ ] [reviewed] Write E2E test for `iw dashboard` starts server when not running
- [x] [impl] [reviewed] Create `.iw/commands/dashboard.scala` with health check logic
- [ ] [impl] [ ] [reviewed] Write test for health check with 5 second timeout
- [x] [impl] [reviewed] Implement health check that polls localhost:9876/health
- [ ] [impl] [ ] [reviewed] Write test for browser opening on macOS (uses "open" command)
- [x] [impl] [reviewed] Implement platform detection (os.name) and browser opening logic
- [ ] [impl] [ ] [reviewed] Write test for browser opening on Linux (uses "xdg-open" command)
- [x] [impl] [reviewed] Add Linux browser opening support
- [ ] [impl] [ ] [reviewed] Write test for browser opening on Windows (uses "start" command)
- [x] [impl] [reviewed] Add Windows browser opening support
- [ ] [impl] [ ] [reviewed] Write test for fallback when browser command fails (prints URL)
- [x] [impl] [reviewed] Add error handling for browser opening failures

### End-to-End Testing (Est: 1h)

- [ ] [test] Create E2E test in `tests/e2e/dashboard.bats`: manually create state.json with test worktree
- [ ] [test] E2E test: start dashboard command in background
- [ ] [test] E2E test: verify health endpoint responds within 5 seconds
- [ ] [test] E2E test: curl GET / and verify HTML contains worktree issue ID
- [ ] [test] E2E test: verify worktree card shows last activity timestamp
- [ ] [test] E2E test: cleanup server process and state file after test
- [ ] [impl] Fix any failures discovered during E2E testing

### Integration & Polish (Est: 1h)

- [x] [impl] [reviewed] Add PURPOSE comments to all created files (2-line format)
- [x] [impl] [reviewed] Verify all unit tests pass: `./iw test unit`
- [ ] [impl] [ ] [reviewed] Verify all E2E tests pass: `./iw test e2e`
- [ ] [impl] [ ] [reviewed] Manual smoke test: create state.json, run `iw dashboard`, verify browser opens
- [ ] [impl] [ ] [reviewed] Manual smoke test: verify dashboard displays worktrees correctly
- [x] [impl] [reviewed] Review code for functional purity (domain/application layers are pure)
- [x] [impl] [reviewed] Verify no compilation warnings exist
- [ ] [impl] [ ] [reviewed] Update documentation: add dashboard command to README

## Task Groups Summary

1. **Setup**: 3 tasks (0.5h)
2. **Domain Layer**: 12 tasks (2h)
3. **Infrastructure Layer**: 20 tasks (4h)
4. **Application Layer**: 10 tasks (2.5h)
5. **Presentation Layer**: 18 tasks (4h)
6. **E2E Testing**: 7 tasks (1h)
7. **Integration**: 8 tasks (1h)

**Total Tasks:** 78
**Total Estimate:** 8-12 hours

## Notes

- All tests must be written BEFORE implementation
- Each test should verify ONE specific behavior
- Domain and application layers must remain pure (no I/O)
- Infrastructure layer handles all I/O (file system, HTTP, process management)
- Port 9876 is hardcoded for Phase 1 (will be configurable in Phase 3)
- Issue titles show placeholder "Issue title not yet loaded" (Phase 4 will fetch from tracker)
- State file location: `~/.local/share/iw/server/state.json`
- Atomic writes prevent state file corruption
- Browser opening must support macOS, Linux, and Windows

## Acceptance Criteria Checklist

Functional:
- [ ] `iw dashboard` command exists and is executable
- [ ] Command starts server on port 9876 if not running
- [ ] Command opens browser to `http://localhost:9876`
- [ ] Dashboard page loads and displays HTML
- [ ] Registered worktrees appear as cards on dashboard
- [ ] Each card shows: issue ID, title placeholder, last activity timestamp
- [ ] Worktrees sorted by most recent activity first
- [ ] `GET /health` endpoint returns 200 OK with JSON response
- [ ] State file created at `~/.local/share/iw/server/state.json` if missing
- [ ] Manual edits to state.json persist and display on dashboard

Non-Functional:
- [ ] Server starts within 2 seconds
- [ ] Dashboard page loads within 500ms
- [ ] Health check responds within 100ms
- [ ] State file writes are atomic (no corruption on crash)
- [ ] Clear error messages if port 9876 is already in use

Testing:
- [ ] Unit tests pass for domain and application layers
- [ ] Integration tests pass for state repository and HTTP routes
- [ ] E2E test verifies dashboard displays worktrees
- [ ] All tests run via `./iw test` without errors

Documentation:
- [ ] Code comments explain purpose of each file (PURPOSE: format)
- [ ] State file JSON schema documented in StateRepository
- [ ] Dashboard command usage documented

---

**Ready to implement:** Yes
**Next step:** Start with setup tasks, then follow TDD for domain layer
