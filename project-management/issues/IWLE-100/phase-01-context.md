# Phase 1 Context: View basic dashboard with registered worktrees

**Issue:** IWLE-100
**Phase:** 1 of 7
**Status:** Ready to implement
**Estimated Effort:** 8-12 hours

## Goals

This phase establishes the **foundation** for the entire server dashboard feature:

1. **HTTP Server Infrastructure**: Set up Cask HTTP server that can start, serve routes, and respond to requests
2. **State Persistence**: Implement JSON-based state storage at `~/.local/share/iw/server/state.json`
3. **Basic Dashboard UI**: Create HTML dashboard using Scalatags that displays registered worktrees
4. **CLI Command**: Add `iw dashboard` command that opens the dashboard in a browser
5. **Health Check**: Provide `/health` endpoint for server readiness verification

**Success Criteria:** Developer can run `iw dashboard`, see a browser open to `localhost:9876`, and view a list of registered worktrees with basic information (issue ID, title, last activity).

## Scope

### In Scope

**Domain Layer:**
- `WorktreeRegistration` value object (issue ID, path, tracker type, team, timestamps)
- `ServerState` model (collection of worktrees, basic operations)

**Application Layer:**
- `ServerStateService` (load state from JSON, save state to JSON, list worktrees)
- `DashboardService` (render dashboard HTML from state)

**Infrastructure Layer:**
- `StateRepository` (read/write JSON at `~/.local/share/iw/server/state.json`)
- `CaskServer` (HTTP server setup, route definitions)
- Create `~/.local/share/iw/server/` directory structure

**Presentation Layer:**
- `GET /` route (serves dashboard HTML)
- `GET /health` route (returns 200 OK when server is ready)
- `WorktreeListView` (Scalatags template rendering worktree cards)
- `dashboard.scala` command (opens browser to dashboard URL)

**Testing:**
- Unit tests for domain models and state service
- Integration tests for state JSON serialization/deserialization
- Integration tests for HTTP routes (`GET /`, `GET /health`)
- E2E test: Start server, register worktree manually, verify dashboard shows it

### Out of Scope

**NOT in this phase:**
- Auto-registration from CLI commands (Phase 2)
- Server lifecycle management (`iw server start/stop/status`) (Phase 3)
- Issue details from Linear/YouTrack (Phase 4)
- Phase/task progress tracking (Phase 5)
- Git status and PR links (Phase 6)
- Unregistration and cleanup (Phase 7)

**Manual workarounds for testing:**
- Manually create state.json with test data
- Manually start server process (no `iw server start` yet)
- No automatic registration (will add in Phase 2)

## Dependencies

### Prerequisites

**From existing codebase:**
- `~/.iw/core/` directory structure exists
- scala-cli build configuration in `project.scala`
- Existing command infrastructure (for adding `dashboard.scala`)

**New dependencies to add:**
- Cask 0.9.4 (HTTP server)
- Scalatags 0.13.1 (HTML generation)

**External tools:**
- None (browser assumed available)

### Blocked By

- None (first phase, establishes foundation)

### Blocks

- Phase 2 (needs server endpoints to register worktrees)
- Phase 3 (needs server infrastructure to manage)
- Phase 4-7 (all enhance the dashboard created here)

## Technical Approach

### Architecture Pattern

Following **Functional Core, Imperative Shell**:

1. **Functional Core (Domain + Application)**:
   - Pure domain models (`WorktreeRegistration`, `ServerState`)
   - Pure service functions (transforming state, sorting worktrees)
   - All business logic is testable without I/O

2. **Imperative Shell (Infrastructure + Presentation)**:
   - State repository handles JSON file I/O
   - Cask server handles HTTP I/O
   - Commands handle system calls (opening browser)

### State Management

**State file location:** `~/.local/share/iw/server/state.json`

**Initial structure:**
```json
{
  "worktrees": {
    "IWLE-123": {
      "issueId": "IWLE-123",
      "path": "/home/user/projects/repo/worktrees/IWLE-123",
      "trackerType": "linear",
      "team": "IWLE",
      "registeredAt": "2025-12-19T10:30:00Z",
      "lastSeenAt": "2025-12-19T14:22:00Z"
    }
  }
}
```

**State operations:**
- Load: Read JSON file, deserialize to `ServerState`, create empty if missing
- Save: Serialize `ServerState` to JSON, write atomically (tmp file + rename)
- List: Sort worktrees by `lastSeenAt` descending (most recent first)

### HTTP Server

**Port:** `9876` (hardcoded for Phase 1, will move to config in Phase 3)

**Routes:**
- `GET /` → Dashboard HTML (full page)
- `GET /health` → `{"status": "ok"}` JSON response

**Server lifecycle (temporary for Phase 1):**
- Server starts on `iw dashboard` command
- Runs in foreground (will background in Phase 3)
- Ctrl+C to stop (graceful shutdown)

### Dashboard UI

**Technology:** Scalatags (type-safe HTML generation)

**Layout:**
```
┌─────────────────────────────────────┐
│ iw Dashboard                        │
├─────────────────────────────────────┤
│                                     │
│  ┌─────────────────────────────┐   │
│  │ IWLE-123                    │   │
│  │ Add user authentication     │   │
│  │ Last activity: 2h ago       │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ IWLE-456                    │   │
│  │ Fix dashboard bug           │   │
│  │ Last activity: 1d ago       │   │
│  └─────────────────────────────┘   │
│                                     │
└─────────────────────────────────────┘
```

**Styling:** Inline CSS for Phase 1 (minimal, functional)

**Auto-refresh:** Manual refresh for Phase 1 (HTMX in later phase)

### CLI Command

**Command:** `iw dashboard`

**Behavior:**
1. Check if server is running (try connecting to `localhost:9876/health`)
2. If not running, print "Starting server..." and start in foreground
3. Wait for health check to succeed (5 second timeout)
4. Open browser to `http://localhost:9876`
5. Keep server running until Ctrl+C

**Browser opening:**
Platform-agnostic approach using os.name system property to select the correct command.

## Files to Modify/Create

### Domain Layer

**Create:** `.iw/core/src/iw/domain/WorktreeRegistration.scala`
- Case class with issue ID, path, tracker type, team, timestamps
- Pure value object, no I/O

**Create:** `.iw/core/src/iw/domain/ServerState.scala`
- Case class holding map of worktrees
- `listByActivity` method returns sorted list
- Pure functions only

### Application Layer

**Create:** `.iw/core/src/iw/application/ServerStateService.scala`
- `load(repo: StateRepository): Either[String, ServerState]`
- `save(state: ServerState, repo: StateRepository): Either[String, Unit]`
- `listWorktrees(state: ServerState): List[WorktreeRegistration]`

**Create:** `.iw/core/src/iw/application/DashboardService.scala`
- `renderDashboard(worktrees: List[WorktreeRegistration]): Tag`

### Infrastructure Layer

**Create:** `.iw/core/src/iw/infrastructure/StateRepository.scala`
- Read/write JSON state file
- Atomic writes (tmp + rename)
- Directory creation if missing

**Create:** `.iw/core/src/iw/infrastructure/CaskServer.scala`
- HTTP server with routes
- GET / and GET /health endpoints
- Port 9876 binding

### Presentation Layer

**Create:** `.iw/core/src/iw/presentation/views/WorktreeListView.scala`
- Scalatags rendering of worktree cards
- HTML generation from domain models

**Create:** `.iw/commands/dashboard.scala`
- Health check logic
- Server starting
- Browser opening

### Configuration

**Modify:** `.iw/core/project.scala`
- Add Cask and Scalatags dependencies

**Create:** Directory structure at `~/.local/share/iw/server/`

## Testing Strategy

### Unit Tests

**Domain models:**
- `WorktreeRegistration` creation and validation
- `ServerState.listByActivity` sorting logic
- Edge cases: empty state, single worktree, many worktrees

**Application services:**
- `ServerStateService.listWorktrees` returns sorted list
- `DashboardService.renderDashboard` generates valid HTML

**Location:** `.iw/core/test/src/iw/domain/ServerStateTest.scala`

### Integration Tests

**State persistence:**
- `StateRepository.read` handles missing file (creates empty state)
- `StateRepository.write` serializes correctly
- `StateRepository.write` is atomic (tmp file + rename)
- Deserialization handles malformed JSON gracefully

**HTTP routes:**
- `GET /` returns 200 OK with HTML content
- `GET /health` returns 200 OK with JSON response
- Server binds to port 9876 successfully

**Location:** `.iw/core/test/src/iw/infrastructure/StateRepositoryTest.scala`

### E2E Tests

**Scenario:** Dashboard shows registered worktrees

Test will:
1. Create state.json with test worktree
2. Start dashboard command in background
3. Verify health endpoint responds
4. Verify dashboard HTML contains worktree data
5. Clean up server and state file

**Location:** `tests/e2e/dashboard.bats`

### Test Coverage Goals

- Domain: 100% (pure functions, no I/O)
- Application: 100% (service functions)
- Infrastructure: 90% (allow for platform-specific I/O edge cases)
- E2E: Key user scenario (dashboard displays worktrees)

## Acceptance Criteria

**Functional Requirements:**

- [ ] `iw dashboard` command exists and is executable
- [ ] Command starts server on port 9876 if not running
- [ ] Command opens browser to `http://localhost:9876`
- [ ] Dashboard page loads and displays HTML
- [ ] Registered worktrees appear as cards on dashboard
- [ ] Each card shows: issue ID, title (placeholder for now), last activity timestamp
- [ ] Worktrees sorted by most recent activity first
- [ ] `GET /health` endpoint returns 200 OK with JSON response
- [ ] State file created at `~/.local/share/iw/server/state.json` if missing
- [ ] Manual edits to state.json persist and display on dashboard

**Non-Functional Requirements:**

- [ ] Server starts within 2 seconds
- [ ] Dashboard page loads within 500ms
- [ ] Health check responds within 100ms
- [ ] State file writes are atomic (no corruption on crash)
- [ ] Clear error messages if port 9876 is already in use

**Testing Requirements:**

- [ ] Unit tests pass for domain and application layers
- [ ] Integration tests pass for state repository and HTTP routes
- [ ] E2E test verifies dashboard displays worktrees
- [ ] All tests run via `./iw test` without errors

**Documentation Requirements:**

- [ ] Code comments explain purpose of each file (PURPOSE: format)
- [ ] State file JSON schema documented in StateRepository
- [ ] Dashboard command usage documented (--help output)

## Implementation Notes

### Port Hardcoding (Temporary)

For Phase 1, port `9876` is hardcoded in:
- `CaskServer` initialization
- `dashboard.scala` command health check
- `dashboard.scala` browser URL

**Rationale:** Defer configuration management to Phase 3. Hardcoding allows us to validate the entire flow before adding configuration complexity.

**Tech debt to address in Phase 3:**
- Move port to `~/.local/share/iw/server/config.json`
- Add config service to read/write configuration
- Update all hardcoded references

### Title Placeholder

Phase 1 doesn't fetch issue data from Linear/YouTrack. Worktree cards show:
- Issue ID: `IWLE-123`
- Title: `"Issue title not yet loaded"` (placeholder)
- Last activity: `"2 hours ago"` (relative timestamp)

**Why:** Issue fetching is Phase 4. Keep Phase 1 focused on infrastructure.

### Manual State Creation for Testing

Since auto-registration is Phase 2, testers must manually create state.json to populate the dashboard with test data.

### Graceful Degradation

**Missing state file:** Create empty state, show "No worktrees registered" message

**Malformed state JSON:** Log error, use empty state, show warning banner on dashboard

**Port already in use:** Show clear error message guiding user to resolve the conflict

## Risks & Mitigations

### Risk 1: Cask/Scalatags Learning Curve

**Likelihood:** Medium
**Impact:** Medium (could delay implementation)

**Mitigation:**
- Start with minimal Cask example (single route)
- Reference Cask documentation and examples
- Use simplest Scalatags features first (basic tags, no custom components)
- Timebox exploration to 2 hours, ask for help if stuck

### Risk 2: State File Corruption

**Likelihood:** Low
**Impact:** High (lost dashboard data)

**Mitigation:**
- Implement atomic writes (tmp file + rename)
- Add JSON schema validation on read
- Graceful fallback to empty state on corruption
- Document recovery procedure (delete state.json, restart server)

### Risk 3: Browser Opening Fails

**Likelihood:** Medium
**Impact:** Low (user can manually navigate)

**Mitigation:**
- Detect platform (macOS, Linux, Windows) and use correct command
- Fallback: print URL if browser command fails
- Document manual access in error message

### Risk 4: Port Conflict

**Likelihood:** Low
**Impact:** Medium (server won't start)

**Mitigation:**
- Clear error message if bind fails
- Document workaround (kill conflicting process)
- Future: add port configuration (Phase 3)

## Open Questions

**None.** All decisions resolved during analysis phase.

## Next Steps

After Phase 1 completion:

1. **Verify acceptance criteria:** All checkboxes must be checked
2. **Run full test suite:** `./iw test` must pass
3. **Manual testing:** Start server, view dashboard, verify worktrees display
4. **Code review:** Review implementation against functional core principles
5. **Update tasks.md:** Mark Phase 1 as complete
6. **Generate Phase 2 context:** Continue to next phase

## Phase Completion Checklist

Before marking Phase 1 complete:

- [ ] All acceptance criteria met
- [ ] Unit tests written and passing
- [ ] Integration tests written and passing
- [ ] E2E test written and passing
- [ ] Code reviewed for functional purity
- [ ] No TODOs or FIXMEs in code
- [ ] Documentation updated (README, code comments)
- [ ] Manual testing completed successfully
- [ ] Git commits made with clear messages
- [ ] Ready to start Phase 2

---

**Status:** Ready for implementation
**Next Command:** Begin TDD cycle for domain models
