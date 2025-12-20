# Story-Driven Analysis: Add server dashboard for worktree monitoring

**Issue:** IWLE-100
**Created:** 2025-12-19
**Status:** Approved
**Classification:** Feature

## Problem Statement

When working with multiple worktrees and running agentic workflows, developers face several visibility and context-switching challenges:

- **Lack of overview:** Hard to see the overall picture of what's active, stalled, or needs attention across multiple worktrees
- **Context fragmentation:** Requires remembering state across workstreams when switching between tasks
- **Remote inaccessibility:** When away from the terminal (mobile, different machine), no way to check work status or access PR links
- **Manual status checking:** Currently requires navigating to each worktree directory and running commands

The value of solving this is significant: remote visibility into work status enables better workflow management, faster PR reviews on mobile, and clearer understanding of multi-worktree development status at a glance.

## User Stories

### Story 1: View basic dashboard with registered worktrees

```gherkin
Feature: Dashboard displays all tracked worktrees
  As a developer working with multiple worktrees
  I want to see a dashboard listing all my active worktrees
  So that I can quickly understand what work is in progress

Scenario: Dashboard shows registered worktrees with basic information
  Given I have 3 worktrees: IWLE-123, IWLE-456, and IWLE-789
  And each worktree has been used recently with iw commands
  When I run "iw dashboard" command
  Then my browser opens to localhost:9876
  And I see 3 worktree cards on the dashboard
  And each card shows the issue ID, title, and last activity timestamp
  And the worktrees are sorted by most recent activity first
```

**Estimated Effort:** 8-12h
**Complexity:** Moderate

**Technical Feasibility:**

This story establishes the foundation of the entire feature. The complexity is moderate because it requires:
- Setting up the Cask HTTP server infrastructure
- Implementing JSON state persistence in `~/.local/share/iw/server/state.json`
- Creating basic Scalatags HTML templates
- Integrating server lifecycle management into the CLI

The key technical challenge is ensuring the server starts automatically when needed (lazy start) and handles the state file correctly (create if missing, read/write atomic operations).

**Acceptance:**
- Dashboard accessible via browser at localhost:9876
- All registered worktrees appear as cards
- Basic information (ID, title, timestamp) is visible
- `iw dashboard` command opens browser automatically
- Server starts automatically if not running

---

### Story 2: Automatic worktree registration from CLI commands

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

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**

Moderate complexity due to:
- Implementing ServerClient HTTP client in core library
- Modifying existing commands (start, open, issue) to call registration endpoints
- Ensuring registration is best-effort (failures don't break CLI commands)
- Handling cases where server is not running (trigger lazy start)

The main challenge is retrofitting existing commands without breaking their current behavior and ensuring all error paths are handled gracefully.

**Acceptance:**
- `iw start` registers worktrees after successful creation
- `iw open` registers when opening existing worktrees
- `iw issue` updates last-seen timestamp
- Failed registration does not cause CLI command to fail
- Server auto-starts if not running when registration occurs

---

### Story 3: Show issue details and status from tracker

```gherkin
Feature: Dashboard displays issue tracker information
  As a developer reviewing work status
  I want to see issue details from Linear/YouTrack
  So that I understand what each worktree is working on

Scenario: Dashboard shows cached issue data with refresh
  Given worktree IWLE-123 exists for a Linear issue
  And the issue title is "Add user authentication"
  And the issue status is "In Progress"
  When I load the dashboard
  Then the worktree card shows "IWLE-123 · Add user authentication"
  And the card shows status "In Progress"
  And the issue data was fetched from Linear API
  And a clickable link to the Linear issue is displayed

Scenario: Issue data is cached with TTL
  Given issue IWLE-123 was fetched 3 minutes ago
  When I refresh the dashboard
  Then the cached issue data is used (no API call)
  And I see a note "cached 3m ago"

Scenario: Expired cache triggers refresh
  Given issue IWLE-123 was fetched 6 minutes ago
  When I refresh the dashboard
  Then a new API call fetches current issue data
  And the cache timestamp is updated
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**

Moderate complexity because:
- Reusing existing LinearClient and YouTrackClient implementations
- Implementing cache management with TTL (5 minutes for issues)
- Handling API failures gracefully (show stale cache with warning)
- Background refresh to avoid blocking dashboard render

Key challenges include deciding cache eviction strategy and handling mixed Linear/YouTrack worktrees in the same dashboard.

**Acceptance:**
- Issue title and status appear on worktree cards
- Issue data cached for 5 minutes before refresh
- Links to Linear/YouTrack issues are clickable
- API failures show last known data with warning
- Both Linear and YouTrack issues supported

---

### Story 4: Display phase and task progress

```gherkin
Feature: Dashboard shows agile workflow progress
  As a developer using agile iterative workflow
  I want to see which phase I'm on and task progress
  So that I can track implementation status at a glance

Scenario: Show current phase and task completion
  Given worktree IWLE-123 is in Phase 2 of 4 phases
  And the current phase has 8 completed tasks out of 15 total
  And the phase is "Validation errors"
  When I view the dashboard
  Then the card shows "Phase 2/4: Validation errors"
  And I see a progress bar at 53% (8/15)
  And I see "8/15 tasks" label

Scenario: Progress derived from task files
  Given worktree IWLE-123 exists
  And file "project-management/issues/IWLE-123/tasks.md" exists
  And file "project-management/issues/IWLE-123/phase-02-tasks.md" has task checkboxes
  When the dashboard loads
  Then the server parses the markdown files
  And counts checked vs total tasks
  And displays the progress percentage
```

**Estimated Effort:** 8-12h
**Complexity:** Complex

**Technical Feasibility:**

This is complex because:
- Parsing markdown files to extract task counts (checkbox syntax)
- Determining current phase from file structure and content
- Handling missing or malformed task files gracefully
- Computing progress percentages correctly

Main technical risk is handling all edge cases (no task files, empty files, invalid markdown, multiple phases in progress).

**Acceptance:**
- Current phase number and name displayed
- Task progress shown as "X/Y tasks" with percentage
- Visual progress bar rendered correctly
- Missing task files show "no tasks defined" instead of error
- Progress updates when task files change

---

### Story 5: Show git status and PR links

```gherkin
Feature: Dashboard displays git and PR information
  As a developer managing multiple worktrees
  I want to see git branch status and PR links
  So that I can quickly check what needs review

Scenario: Clean working directory indicated
  Given worktree IWLE-123 is on branch "IWLE-123-phase-02"
  And the working directory is clean (no uncommitted changes)
  When I view the dashboard
  Then the card shows "Branch: IWLE-123-phase-02 ✓ clean"
  And the clean indicator is styled in green

Scenario: Uncommitted changes warning
  Given worktree IWLE-456 has uncommitted changes
  When I view the dashboard
  Then the card shows "Branch: IWLE-456-fix ⚠ uncommitted"
  And the warning indicator is styled in yellow

Scenario: PR links displayed when available
  Given worktree IWLE-123 has an open PR for phase 2
  And the PR URL is "https://github.com/org/repo/pull/42"
  When I view the dashboard
  Then I see a "View PR ↗" link on the card
  And clicking it opens the GitHub PR in a new tab
  And the link shows PR state "open" badge
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**

Moderate complexity:
- Running git status commands in each worktree directory
- Parsing git output for clean/dirty status
- Fetching PR information from GitHub/GitLab APIs (using existing patterns)
- Caching PR data with shorter TTL (2 minutes)

Challenges include handling different git states (ahead/behind, conflicts, detached HEAD) and supporting both GitHub and GitLab PR link formats.

**Acceptance:**
- Git branch name displayed
- Clean vs dirty status clearly indicated
- PR links appear when PRs exist (phase or feature PRs)
- PR state (open/merged/closed) shown visually
- Non-existent worktrees show error state

---

### Story 6: Server lifecycle management

```gherkin
Feature: Control server start, stop, and status
  As a developer managing the dashboard
  I want explicit control over the server lifecycle
  So that I can start, stop, and check server status

Scenario: Start server explicitly
  Given the server is not running
  When I run "iw server start"
  Then the server starts in the background
  And the PID is written to ~/.local/share/iw/server/server.pid
  And I see "Server started on http://localhost:9876"
  And the health check endpoint responds

Scenario: Server auto-starts on first use
  Given the server is not running
  When I run "iw dashboard"
  Then the server starts automatically
  And waits for health check before opening browser
  And I see "Starting server..." message

Scenario: Stop server gracefully
  Given the server is running
  When I run "iw server stop"
  Then the server shuts down gracefully
  And the PID file is removed
  And I see "Server stopped"

Scenario: Check server status
  Given the server is running with 5 registered worktrees
  When I run "iw server status"
  Then I see "Server running on port 9876"
  And I see "Tracking 5 worktrees"
  And I see uptime information
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**

Moderate complexity due to:
- Process management (background daemon, PID file handling)
- Health check endpoint implementation
- Graceful shutdown handling
- Detecting if server is already running

Main challenges are cross-platform process management and ensuring PID file cleanup in all exit scenarios (crashes, SIGTERM, etc.).

**Acceptance:**
- `iw server start` launches background server
- `iw server stop` stops running server
- `iw server status` shows current state
- PID file accurately tracks running process
- Health check prevents race conditions on auto-start

---

### Story 7: Unregister worktrees when removed

```gherkin
Feature: Remove worktrees from dashboard when deleted
  As a developer cleaning up old work
  I want worktrees to disappear from dashboard when removed
  So that the dashboard stays current

Scenario: Removing worktree unregisters it
  Given worktree IWLE-123 is registered and visible on dashboard
  When I run "iw rm IWLE-123"
  And the worktree is successfully removed
  Then the worktree is unregistered from server state
  And it no longer appears on the dashboard

Scenario: Auto-prune non-existent worktrees
  Given worktree IWLE-456 is registered
  But the directory was deleted manually (not via iw rm)
  When the dashboard refreshes
  Then the server detects the missing directory
  And automatically removes IWLE-456 from state
  And the card disappears from dashboard
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**

This is straightforward because:
- Simple DELETE endpoint to unregister
- Filesystem check to verify worktree existence
- Auto-pruning logic runs on state read

The main work is modifying the `iw rm` command to call the unregister endpoint and implementing the pruning logic in state loading.

**Acceptance:**
- `iw rm` unregisters worktree after successful removal
- Non-existent paths pruned automatically on dashboard load
- Manually deleted worktrees disappear within 30s (next auto-refresh)
- Failed unregistration doesn't break `iw rm` command

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: View basic dashboard with registered worktrees

**Domain Layer:**
- `WorktreeRegistration` (issue ID, path, tracker type, team, timestamps)
- `ServerState` (collection of registered worktrees)

**Application Layer:**
- `ServerStateService` (load state, save state, list worktrees)
- `DashboardService` (render dashboard HTML)

**Infrastructure Layer:**
- `StateRepository` (JSON file persistence at `~/.local/share/iw/server/state.json`)
- `CaskServer` (HTTP server with routes)

**Presentation Layer:**
- `GET /` (full dashboard page)
- `GET /health` (health check endpoint)
- `WorktreeListView` (Scalatags template for worktree cards)
- `dashboard.scala` command (opens browser to dashboard URL)

---

### For Story 2: Automatic worktree registration from CLI commands

**Domain Layer:**
- `WorktreeRegistration` (same as Story 1, includes registration/update logic)

**Application Layer:**
- `WorktreeRegistrationService` (register, update last-seen)

**Infrastructure Layer:**
- `ServerClient` (HTTP client for CLI → Server communication)
- `ServerProcess` (start server, check if running)

**Presentation Layer:**
- `PUT /api/worktrees/{issueId}` (register/update worktree)
- Modified `start.scala`, `open.scala`, `issue.scala` commands

---

### For Story 3: Show issue details and status from tracker

**Domain Layer:**
- `IssueData` (title, status, assignee, URL)
- `CachedData` (value, fetched-at timestamp, TTL)

**Application Layer:**
- `IssueCacheService` (fetch with TTL, cache management)
- Integration with existing `LinearClient`, `YouTrackClient`

**Infrastructure Layer:**
- `CacheRepository` (read/write cached data in state.json)
- HTTP clients (reuse existing sttp-based implementations)

**Presentation Layer:**
- Enhanced `WorktreeCardView` (show issue title, status, tracker link)
- Cache timestamp display ("cached 3m ago")

---

### For Story 4: Display phase and task progress

**Domain Layer:**
- `WorkflowProgress` (current phase, total phases, task counts)
- `TaskFile` (phase number, path, task list)

**Application Layer:**
- `WorkflowProgressService` (parse task files, compute progress)
- `MarkdownTaskParser` (extract checkbox tasks from markdown)

**Infrastructure Layer:**
- Filesystem access to `project-management/issues/{ID}/` directories
- Markdown parsing (regex-based or simple parser)

**Presentation Layer:**
- `ProgressBar` component (visual progress indicator)
- Enhanced `WorktreeCardView` (phase info, task counts)

---

### For Story 5: Show git status and PR links

**Domain Layer:**
- `GitStatus` (branch name, clean/dirty, ahead/behind)
- `PullRequestData` (URL, state, PR number)

**Application Layer:**
- `GitStatusService` (run git commands, parse status)
- `PullRequestCacheService` (fetch PR data with TTL)

**Infrastructure Layer:**
- `GitAdapter` (execute git commands in worktree directories)
- `GitHubClient`, `GitLabClient` (fetch PR information)
- PR data cache (in state.json, 2-minute TTL)

**Presentation Layer:**
- Enhanced `WorktreeCardView` (git status, PR links)
- PR state badges (open/merged/closed)

---

### For Story 6: Server lifecycle management

**Domain Layer:**
- `ServerStatus` (running state, port, worktree count, uptime)

**Application Layer:**
- `ServerLifecycleService` (start, stop, status)

**Infrastructure Layer:**
- `ProcessManager` (spawn background process, PID file handling)
- `HealthCheckClient` (verify server is responding)

**Presentation Layer:**
- `server.scala` command (start, stop, status subcommands)
- `GET /api/status` (JSON status endpoint)
- PID file at `~/.local/share/iw/server/server.pid`

---

### For Story 7: Unregister worktrees when removed

**Application Layer:**
- `WorktreeUnregistrationService` (remove from state)
- `WorktreeExistenceChecker` (auto-prune missing paths)

**Infrastructure Layer:**
- Filesystem check (os.exists)
- State pruning logic

**Presentation Layer:**
- `DELETE /api/worktrees/{issueId}` (unregister endpoint)
- Modified `rm.scala` command (calls unregister after removal)

---

## Technical Decisions (Resolved)

The following technical decisions were resolved during analysis review:

### Port Configuration

**Decision:** Fixed port stored in `~/.local/share/iw/server/config.json`

```json
{
  "port": 9876
}
```

- All CLI invocations read port from this shared config file (single source of truth)
- If file doesn't exist, create with default port 9876
- No environment variable override (prevents multiple servers with same state.json)
- If port is in use by something else, error with clear message

---

### Multi-Project Worktree Tracking

**Decision:** Global dashboard showing all projects

- Single view of all worktrees across all projects
- Matches design intent for remote visibility
- No filtering by current project (would limit remote access value)
- Future consideration: add project grouping if clutter becomes an issue

---

### GitHub/GitLab PR Detection

**Decision:** Use `gh`/`glab` CLI tools

- Shell out to `gh pr view` / `glab mr view` for PR information
- Leverages existing user authentication (no new tokens needed)
- Cache results with 2-minute TTL
- Graceful fallback if CLI tools not installed (no PR link shown)

---

### Task File Parsing

**Decision:** Standard format only: `- [ ]` and `- [x]`

- Simple regex matching for checkbox syntax
- Matches what the agile workflow generates
- Ignores other bullet styles (`*`, `+`)
- Clear expectations, predictable behavior

---

### Server Auto-Start Reliability

**Decision:** Warn but continue (best-effort)

- If server fails to start, show warning like "Dashboard server unavailable"
- CLI command still proceeds and works normally
- User is aware of issue but workflow isn't blocked
- Reasonable timeout for health check (e.g., 5 seconds)

---

### HTMX Auto-Refresh Interval

**Decision:** 30 seconds, full page refresh

- Simple HTMX polling with `hx-trigger="every 30s"`
- Good balance of freshness vs overhead
- Matches design document specification
- Acceptable for mobile battery usage

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Basic dashboard): 8-12 hours
- Story 2 (Auto-registration): 6-8 hours
- Story 3 (Issue details): 6-8 hours
- Story 4 (Phase/task progress): 8-12 hours
- Story 5 (Git status and PRs): 6-8 hours
- Story 6 (Server lifecycle): 6-8 hours
- Story 7 (Unregister worktrees): 3-4 hours

**Total Range:** 43 - 60 hours

**Confidence:** Medium

**Reasoning:**
- **Well-defined scope**: Design document provides clear specifications, reducing ambiguity
- **Existing patterns**: Project already uses similar HTTP clients (sttp), configuration handling, and process management
- **New dependencies**: Cask and Scalatags are new to the project, learning curve expected
- **Integration complexity**: Modifying existing commands carries risk of breaking current behavior
- **External API uncertainty**: GitHub/GitLab PR detection strategy not fully specified (CLARIFY marker)
- **Parsing complexity**: Markdown task parsing could have edge cases not anticipated

The estimate assumes:
- CLARIFY markers are resolved before implementation begins
- No major issues with Cask/Scalatags integration
- Existing LinearClient/YouTrackClient can be reused without modification
- Task file format is consistent and documented

---

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure domain logic, value objects, business rules
2. **Integration Tests**: HTTP endpoints, state persistence, external API calls
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenario

**Story-Specific Testing Notes:**

**Story 1: Basic dashboard**
- Unit: ServerState model, WorktreeRegistration value object
- Integration: State JSON serialization/deserialization, Cask route handling, `GET /` returns HTML
- E2E: Start server, register worktree, verify dashboard shows it in browser

**Story 2: Auto-registration**
- Unit: WorktreeRegistration creation logic, timestamp updates
- Integration: ServerClient HTTP calls, `PUT /api/worktrees/{id}` endpoint
- E2E: Run `iw start ISSUE-123`, verify registration via `GET /api/worktrees`, check dashboard

**Story 3: Issue details**
- Unit: CachedData TTL logic, cache expiry calculation
- Integration: LinearClient/YouTrackClient calls, cache persistence
- E2E: Register worktree, verify issue title appears on dashboard, verify cache refresh after TTL

**Story 4: Phase/task progress**
- Unit: MarkdownTaskParser (checkbox counting), progress percentage calculation
- Integration: Filesystem access to task files, missing file handling
- E2E: Create worktree with task files, verify progress bar shows correct percentage

**Story 5: Git status and PRs**
- Unit: Git output parsing, PR URL construction
- Integration: GitAdapter commands, GitHub/GitLab API calls
- E2E: Create worktree with uncommitted changes, verify dashboard shows warning

**Story 6: Server lifecycle**
- Unit: PID file handling, health check retry logic
- Integration: Process spawning, HTTP health check endpoint
- E2E: Run `iw server start/stop/status`, verify PID file and process state

**Story 7: Unregister worktrees**
- Unit: Auto-prune logic, filesystem existence check
- Integration: `DELETE /api/worktrees/{id}` endpoint
- E2E: Run `iw rm ISSUE-123`, verify worktree disappears from dashboard

**Test Data Strategy:**
- Use temporary directories for worktree paths in tests
- Mock HTTP responses for Linear/YouTrack/GitHub/GitLab APIs
- Create fixture markdown files for task parsing tests
- Use ephemeral ports for server tests (avoid port conflicts)

**Regression Coverage:**
- Existing commands (`start`, `open`, `rm`, `issue`) must continue working if server is unavailable
- Failed server communication must not break CLI functionality
- State file corruption should not crash server (graceful degradation)

---

## Deployment Considerations

### Database Changes

No database. State is a single JSON file at `~/.local/share/iw/server/state.json`.

**Story 1 migrations:**
- Create `~/.local/share/iw/server/` directory if missing
- Initialize empty `state.json` if missing: `{"worktrees": {}, "cache": {"issues": {}, "prs": {}}}`

**No schema migrations needed** (JSON structure can evolve with backward-compatible reads)

### Configuration Changes

**New environment variables:**
- `IW_SERVER_PORT` (optional, default 9876)

**New dependencies in `.iw/core/project.scala`:**
```scala
//> using dep com.lihaoyi::cask::0.9.4
//> using dep com.lihaoyi::scalatags::0.13.1
```

### Rollout Strategy

Stories can be deployed incrementally:
- **Story 1**: Dashboard visible but no auto-registration (manual API calls for testing)
- **Story 2**: Auto-registration enables normal workflow
- **Stories 3-5**: Enhance dashboard incrementally (issue data, progress, git status)
- **Stories 6-7**: Lifecycle management and cleanup

**Feature flag approach:** Not needed. Server is opt-in (only starts when accessed).

### Rollback Plan

If a story fails in production:
- **Story 1-6**: Stop server (`iw server stop`), CLI commands still work normally
- **Story 7**: Worst case, unregister doesn't work, but dashboard still shows stale data (non-critical)

State file can be deleted to reset (`rm ~/.local/share/iw/server/state.json`).

---

## Dependencies

### Prerequisites
- Existing iw-cli installation with `start`, `open`, `rm`, `issue` commands
- Linear/YouTrack configuration in `.iw/config.conf` (already exists)
- Git repository with worktrees (already exists)
- No additional credentials needed for MVP (GitHub/GitLab PR detection may need tokens - see CLARIFY)

### Story Dependencies

**Sequential dependencies:**
- Story 2 depends on Story 1 (needs server and endpoints)
- Stories 3, 4, 5 depend on Story 1 (enhance existing dashboard)
- Story 7 depends on Story 2 (needs registration before unregistration)

**Can be parallelized:**
- Stories 3, 4, 5 are independent (can be implemented in any order)
- Story 6 is independent (lifecycle management orthogonal to dashboard features)

**Recommended order:**
1. Story 1 (foundation)
2. Story 2 (enables workflow)
3. Story 6 (before adding more complexity, solidify lifecycle)
4. Stories 3, 4, 5 (in parallel or any order)
5. Story 7 (cleanup, final touch)

### External Blockers

- **None for MVP**: All required APIs (Linear, YouTrack) already integrated
- **For PR links (Story 5)**: May need GitHub/GitLab API tokens (see CLARIFY marker)

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: View basic dashboard** - Establishes foundation (server, state, routes, HTML rendering)
2. **Story 2: Auto-registration** - Makes the feature usable in normal workflow
3. **Story 6: Server lifecycle** - Solidifies infrastructure before adding complexity
4. **Story 3: Issue details** - Adds value (tracker integration)
5. **Story 4: Phase/task progress** - Most complex, defer until stable base
6. **Story 5: Git status and PRs** - Enhances visibility
7. **Story 7: Unregister worktrees** - Cleanup, low-risk final touch

**Iteration Plan:**

- **Iteration 1** (Stories 1-2, ~16-20h): Core dashboard with auto-registration
  - Deliverable: Can view registered worktrees on dashboard, normal CLI workflow registers them

- **Iteration 2** (Stories 6, 3, ~12-16h): Lifecycle management and issue data
  - Deliverable: Explicit server control, issue titles/status visible

- **Iteration 3** (Stories 4-5, ~14-20h): Progress tracking and git status
  - Deliverable: Full featured dashboard with phase progress, PR links, git status

- **Iteration 4** (Story 7, ~3-4h): Cleanup and polish
  - Deliverable: Automatic cleanup of removed worktrees

---

## Documentation Requirements

- [x] Gherkin scenarios serve as living documentation
- [ ] API documentation for HTTP endpoints (`GET /`, `PUT /api/worktrees/{id}`, etc.)
- [ ] README section on dashboard usage (`iw dashboard`, `iw server` commands)
- [ ] State file format documentation (for debugging/manual intervention)
- [ ] Configuration documentation (IW_SERVER_PORT environment variable)
- [ ] Optional: systemd service file template for persistent server

---

**Analysis Status:** Ready for Implementation

All technical decisions have been resolved. Analysis approved on 2025-12-19.

**Next Steps:**
1. Generate phase-based tasks: `/iterative-works:ag-create-tasks IWLE-100`
2. Begin implementation: `/iterative-works:ag-implement IWLE-100`
