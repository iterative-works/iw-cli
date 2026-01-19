# Story-Driven Analysis: Development mode for dashboard testing

**Issue:** IW-82
**Created:** 2026-01-19
**Status:** Draft
**Classification:** Feature

## Problem Statement

Developers need a safe way to test dashboard UI features and changes without affecting production data. Currently, there's no mechanism to run the server with test/mock data, making it risky to manually test new features or visual changes.

What capability is missing: The ability to run the dashboard server in an isolated development mode with sample data and customizable state.

What value does it provide: Enables confident UI testing and development without risk of corrupting real worktree state or configuration.

## User Stories

### Story 1: Run server with custom state file

```gherkin
Feature: Server with custom state file path
  As a developer
  I want to run the dashboard server with a custom state file path
  So that I can test UI features with isolated state without affecting my production data

Scenario: Starting server with custom state file
  Given I am in a project directory
  When I run "./iw dashboard --dev --state-path=/tmp/test-state.json"
  Then the server starts successfully
  And the server uses "/tmp/test-state.json" for state persistence
  And my production state at "~/.local/share/iw/server/state.json" is not modified
  And the dashboard opens in my browser
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because CaskServer already accepts `statePath` parameter. Main work is plumbing the CLI flag through dashboard.scala command to server initialization.

**Acceptance:**
- `./iw dashboard --dev --state-path=<path>` starts server with custom state
- Production state remains untouched
- Server persists worktrees to custom path
- Browser opens to dashboard

---

### Story 2: Run server with custom project directory

```gherkin
Feature: Server pointing to specific project
  As a developer
  I want to run the dashboard server pointing to a specific project directory
  So that I can test UI features in the context of a test project without being in that directory

Scenario: Starting server with custom project path
  Given I have a test project at "/tmp/test-project"
  And "/tmp/test-project/.iw/config.yaml" exists
  When I run "./iw dashboard --dev --project=/tmp/test-project"
  Then the server starts successfully
  And the server loads config from "/tmp/test-project/.iw/config.yaml"
  And worktree creation uses "/tmp/test-project" as the base path
  And issue searches use the test project's tracker configuration
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity because the server currently assumes `os.pwd` for project context. Will need to thread project path through multiple routes (dashboard, searchIssues, createWorktree, worktreeCard). Config loading already supports arbitrary paths, which helps.

**Acceptance:**
- `./iw dashboard --dev --project=<path>` uses specified project
- Config loaded from project's `.iw/config.yaml`
- Issue search uses project's tracker settings
- Worktree creation relative to project path
- Dashboard displays correct project context

---

### Story 3: Load sample data for UI testing

```gherkin
Feature: Server with sample worktree data
  As a developer
  I want to load sample worktrees and issues into the dashboard
  So that I can visually test UI layouts and interactions with realistic data

Scenario: Starting server with sample data
  Given I am in a project directory
  When I run "./iw dashboard --dev --sample-data"
  Then the server starts successfully
  And the state includes 3-5 sample worktrees with different statuses
  And the state includes cached issue data for each worktree
  And the state includes sample PR data and review states
  And the dashboard displays all sample worktrees
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate because we need to create realistic sample data matching the domain model. TestFixtures.scala already has SampleData helpers, but we need to expand this to include WorktreeRegistration, CachedIssue, CachedProgress, CachedPR, and CachedReviewState. The fixture creation itself is straightforward, but making it comprehensive enough to test all UI states requires thoughtful design.

**Acceptance:**
- `./iw dashboard --dev --sample-data` populates state with fixtures
- Sample includes diverse worktree scenarios (different statuses, trackers)
- Sample includes cached data (issues, PRs, progress, reviews)
- Dashboard renders all sample worktrees correctly
- UI states are visually testable (in progress, merged, draft, etc.)

---

### Story 4: Combined development mode flag

```gherkin
Feature: Convenient development mode
  As a developer
  I want a single --dev flag that enables all development features
  So that I can quickly start a safe testing environment

Scenario: Starting server in development mode
  Given I am in a project directory
  When I run "./iw dashboard --dev"
  Then the server starts successfully
  And uses a temporary state file in "/tmp/iw-dev-<timestamp>/"
  And loads sample worktree data
  And the dashboard opens in my browser
  And I see a visible indicator that this is development mode
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward once Stories 1-3 are complete. This is primarily CLI argument parsing and sensible defaults. The development mode indicator requires a small UI change (banner or badge).

**Acceptance:**
- `./iw dashboard --dev` combines isolated state + sample data
- Temporary state path auto-generated with timestamp
- Visual indicator in dashboard UI (e.g., "DEV MODE" banner)
- All development features work together seamlessly

---

### Story 5: Validate development mode isolation

```gherkin
Feature: Development mode safety validation
  As a developer
  I want to ensure development mode never affects production data
  So that I can test confidently without fear of corruption

Scenario: Production state remains untouched in dev mode
  Given I have production worktrees at "~/.local/share/iw/server/state.json"
  And I have production config at "~/.local/share/iw/server/config.json"
  When I run "./iw dashboard --dev"
  And I create a worktree "TEST-999" in the dev dashboard
  And I unregister a sample worktree
  And I stop the dev server
  Then "~/.local/share/iw/server/state.json" is unchanged
  And "~/.local/share/iw/server/config.json" is unchanged
  And no production worktrees were affected
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward test scenario. No implementation work needed if Stories 1-4 are correct, but critical validation to ensure isolation guarantees hold. This is primarily E2E test coverage.

**Acceptance:**
- E2E test verifies production state isolation
- Test creates baseline production state
- Test runs dev mode and performs mutations
- Test verifies production state byte-for-byte identical
- Documentation clearly states isolation guarantees

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Run server with custom state file

**Domain Layer:**
- No new domain entities needed (reuse ServerState, WorktreeRegistration)

**Application Layer:**
- ServerStateService already supports custom state paths
- No changes needed

**Infrastructure Layer:**
- StateRepository already supports custom paths via constructor
- CaskServer already accepts `statePath` parameter

**Presentation Layer:**
- CLI flag parsing in dashboard.scala (--dev, --state-path)
- Pass custom state path to CaskServer.start()

---

### For Story 2: Run server with custom project directory

**Domain Layer:**
- ProjectContext value object (path to project root)
- No changes to existing entities

**Application Layer:**
- DashboardService needs project path context
- IssueSearchService needs project path for config loading
- WorktreeCreationService needs project path for relative worktrees

**Infrastructure Layer:**
- CaskServer needs project path parameter
- ConfigFileRepository already supports arbitrary paths
- Update route handlers to use project path instead of os.pwd

**Presentation Layer:**
- CLI flag parsing in dashboard.scala (--project)
- Pass project path to CaskServer constructor
- Dashboard UI shows project path indicator

---

### For Story 3: Load sample data for UI testing

**Domain Layer:**
- SampleDataGenerator utility (create fixtures)
- Expand existing SampleData object in TestFixtures.scala
- Sample entities for all cache types

**Application Layer:**
- DataInitializationService (populate state with samples)
- SampleDataService (generate diverse fixture sets)

**Infrastructure Layer:**
- StateRepository.initialize() method to load sample data
- No changes to persistence logic

**Presentation Layer:**
- CLI flag parsing in dashboard.scala (--sample-data)
- Trigger sample data load during server initialization
- No UI changes (sample data just appears as worktrees)

---

### For Story 4: Combined development mode flag

**Domain Layer:**
- DevelopmentConfig value object (encapsulates dev settings)

**Application Layer:**
- DevelopmentModeService (coordinate dev features)
- Temporary path generation utility

**Infrastructure Layer:**
- No changes to core infrastructure

**Presentation Layer:**
- CLI flag parsing in dashboard.scala (--dev)
- Development mode banner component in dashboard view
- Auto-enable --state-path + --sample-data

---

### For Story 5: Validate development mode isolation

**Domain Layer:**
- No new domain components

**Application Layer:**
- No new application components

**Infrastructure Layer:**
- No new infrastructure components

**Presentation Layer:**
- No new presentation components

**Testing Layer:**
- E2E test suite for isolation validation
- Baseline state creation utilities
- State comparison utilities

---

## Design Decisions (Resolved)

### Decision 1: Complete isolation for dev mode config

**Decision:** Dev mode uses completely isolated config.json in temp directory.

**Rationale:** Complete isolation is required for integration tests. The config only contains `port` and `hosts` (trivial to generate defaults), so the cost of isolation is minimal while the benefit (no conflicts, safe CI runs) is significant.

**Implementation:** Dev mode creates `config.json` in temp directory with default values (port 9876, hosts ["localhost"]).

---

### Decision 2: Comprehensive sample data

**Decision:** Include comprehensive sample data covering all UI states.

**Sample data includes:**
- ~10 worktrees across all tracker types (Linear, GitHub, YouTrack)
- All PR states (draft, open, merged, closed)
- Various progress states and review states
- Edge cases (missing assignee, old timestamps, different priorities)

**Rationale:** The purpose of dev mode is UI testing, so we need diverse data to exercise all visual states and catch edge case bugs.

---

### Decision 3: Hot reload via scala-cli passthrough

**Decision:** Support hot reload by passing `--restart` flag through to scala-cli.

**Implementation:** When user runs `./iw server serve --restart`, we pass `--restart` to `scala-cli run`, which handles file watching and process restart automatically.

**Important:** This is a scala-cli feature, not something we implement. The `--restart` flag is passed through verbatim to scala-cli. No custom file watching or restart logic needed.

**Usage:** `scala-cli run server.scala --restart` watches sources, recompiles on changes, and fully restarts the server process.

---

### Decision 4: Server command structure with daemon/foreground separation

**Decision:** Restructure server commands to clearly separate daemon and foreground modes.

**Commands:**
- `./iw server start [--dev] [--pid-file=<path>]` - daemonize, write PID, exit
- `./iw server stop [--dev] [--pid-file=<path>]` - read PID, kill process
- `./iw server status [--dev] [--pid-file=<path>]` - check if running
- `./iw server serve [--dev] [--restart]` - foreground, no PID file

**Flag behavior:**
- `--dev` uses isolated state/config in temp directory, defaults to separate PID file in temp dir
- `--pid-file=<path>` overrides PID location (enables multiple parallel servers for CI)
- `--restart` only valid with `serve` (passthrough to scala-cli for hot reload)

**Use cases:**
- Interactive development: `./iw server serve --dev --restart`
- CI test run: `./iw server start --dev --pid-file=/tmp/test-1.pid` → tests → `./iw server stop --dev --pid-file=/tmp/test-1.pid`
- Parallel CI: Multiple servers with different `--pid-file` paths

**Rationale:** This design supports both interactive development (foreground with hot reload) and CI automation (daemon with controllable PID files), while keeping production workflow unchanged.

---

## Technical Risks & Uncertainties

No unresolved CLARIFY markers remain. All design decisions have been made.

## Total Estimates

**Story Breakdown:**
- Story 1 (Run server with custom state file): 4-6 hours
- Story 2 (Run server with custom project directory): 6-8 hours
- Story 3 (Load sample data for UI testing): 6-8 hours
- Story 4 (Combined development mode flag): 3-4 hours
- Story 5 (Validate development mode isolation): 2-3 hours

**Total Range:** 21-29 hours

**Confidence:** Medium

**Reasoning:**
- **Existing infrastructure**: CaskServer already parameterized, reduces effort
- **Sample data precedent**: TestFixtures.scala shows pattern, reduces risk
- **Integration complexity**: Threading project path through routes is moderate complexity
- **Unknowns**: CLARIFY markers indicate several design decisions needed
- **Testing overhead**: E2E validation for isolation is critical but adds time

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Domain logic, value objects, sample data generation
2. **Integration Tests**: State repository with custom paths, config loading from custom project
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenario

**Story-Specific Testing Notes:**

**Story 1:**
- Unit: StateRepository with custom path validation
- Integration: CaskServer initialization with custom state path
- E2E: Run dashboard with --state-path, verify isolation from production

**Story 2:**
- Unit: ProjectContext validation, path resolution
- Integration: Config loading from custom project, worktree creation relative to project
- E2E: Run dashboard with --project, create worktree, verify correct project context

**Story 3:**
- Unit: SampleDataGenerator creates valid domain objects
- Integration: StateRepository loads sample data, ServerStateService initializes with samples
- E2E: Run dashboard with --sample-data, verify all samples render

**Story 4:**
- Unit: DevelopmentConfig combines settings correctly
- Integration: Temporary path generation, dev mode flag enables all features
- E2E: Run dashboard with --dev, verify temp state + samples + UI indicator

**Story 5:**
- Unit: No unit tests (validation story)
- Integration: No integration tests (validation story)
- E2E: Baseline production state, run dev mutations, verify production unchanged

**Test Data Strategy:**
- Reuse TestFixtures.scala SampleData patterns
- Create SampleDataGenerator for comprehensive fixtures
- Use real domain objects (no mocks for sample data)
- Fixtures should cover edge cases (missing assignee, old timestamps, various tracker types)

**Regression Coverage:**
- Ensure production dashboard still works (no --dev flags)
- Verify server.scala commands unaffected
- Check that normal worktree registration still uses production paths
- Confirm config loading backward compatible

## Deployment Considerations

### Database Changes
No database migrations needed (JSON file-based state).

**Story 1 migrations:**
- No schema changes (state format unchanged)

**Story 2 migrations:**
- No schema changes (config format unchanged)

**Story 3 migrations:**
- No schema changes (sample data uses existing format)

### Configuration Changes
- No environment variables needed
- No feature flags required
- CLI flags are opt-in (--dev, --state-path, --project, --sample-data)

### Rollout Strategy
Can deploy incrementally per story:
- Story 1 standalone: --state-path flag usable immediately
- Story 2 standalone: --project flag usable immediately
- Story 3 standalone: --sample-data flag usable immediately
- Story 4 combines: --dev flag depends on Stories 1-3
- Story 5 validation: Can run at any point

No feature flags needed since development mode is opt-in via CLI flags.

### Rollback Plan
If a story fails in production, no rollback needed:
- CLI flags are additive (opt-in)
- Production usage unaffected (no --dev flag = normal behavior)
- Worst case: remove CLI flag parsing for that story

## Dependencies

### Prerequisites
- Existing CaskServer infrastructure (already exists)
- StateRepository with custom paths (already supported)
- ConfigFileRepository with custom paths (already supported)
- TestFixtures.scala SampleData (already exists, needs expansion)

### Story Dependencies
- Story 4 (--dev flag) depends on Story 1 (--state-path) and Story 3 (--sample-data)
- Story 5 (isolation validation) depends on Story 1, 2, 3, 4 being complete
- Stories 1, 2, 3 can be implemented in parallel (no dependencies)

### External Blockers
- None identified

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Custom state file** - Establishes foundation for isolation, simplest implementation
2. **Story 3: Sample data** - Provides realistic test data, independent of project path complexity
3. **Story 2: Custom project directory** - Most complex, benefits from having state isolation and sample data already working
4. **Story 4: Combined --dev flag** - Brings it all together, depends on 1-3
5. **Story 5: Isolation validation** - Final safety net, validates entire feature

**Iteration Plan:**

- **Iteration 1** (Stories 1, 3): Core development mode (8-12h)
  - Can already test UI with isolated state + sample data
  - Delivers immediate value for UI testing

- **Iteration 2** (Story 2): Project context flexibility (6-8h)
  - Adds convenience for multi-project testing
  - More complex but builds on proven isolation

- **Iteration 3** (Stories 4, 5): Polish + validation (5-7h)
  - Convenient --dev flag for daily use
  - Comprehensive E2E safety validation

## Documentation Requirements

- [x] Gherkin scenarios serve as living documentation
- [ ] CLI documentation (./iw dashboard --help output)
- [ ] Developer guide section on development mode usage
- [ ] Sample data fixture documentation (what's included, how to extend)
- [ ] Architecture decision record for CLARIFY resolutions

---

**Analysis Status:** Approved

**Next Steps:**
1. Run `/iterative-works:ag-create-tasks IW-82` to map stories to implementation phases
2. Run `/iterative-works:ag-implement IW-82` for iterative story-by-story implementation
