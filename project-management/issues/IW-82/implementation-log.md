# Implementation Log: Development mode for dashboard testing

Issue: IW-82

This log tracks the evolution of implementation across phases.

---

## Phase 1: Run server with custom state file (2026-01-20)

**What was built:**
- CLI parameter: Added `--state-path=<path>` flag to `dashboard` command
- Path resolution: Custom path takes precedence over default production path
- Debug output: Prints effective state path on server startup

**Decisions made:**
- Used `Option[String] = None` for CLI parameter - idiomatic Scala for optional values
- Only print state path when custom path is provided (avoids noise in default case)

**Patterns applied:**
- Option type: Using `Option[String]` with `getOrElse` for optional parameter handling
- Existing infrastructure reuse: CaskServer and StateRepository already support custom paths

**Testing:**
- Unit tests: 0 (no new domain logic, CLI script pattern)
- Integration tests: Manual verification planned

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260120-104400.md
- Major findings: No critical issues, 1 minor naming suggestion (optional)

**For next phases:**
- Available utilities: `effectiveStatePath` pattern can be reused for other path parameters
- Extension points: Config path could be parameterized similarly in Phase 4
- Notes: Production state at `~/.local/share/iw/server/state.json` remains untouched when custom path used

**Files changed:**
```
M	.iw/commands/dashboard.scala
```

---

## Phase 2: Load sample data for UI testing (2026-01-20)

**What was built:**
- `SampleDataGenerator.scala`: Pure utility generating complete `ServerState` with 5 sample worktrees
- CLI flag: Added `--sample-data` flag to `dashboard` command for development mode
- Test fixtures: Extended `TestFixtures.scala` with comprehensive sample data covering all domain types

**Sample data design:**
- 5 worktrees across 3 tracker types: Linear (IWLE-123, IWLE-456), GitHub (GH-100), YouTrack (YT-111, YT-222)
- Edge cases: GH-100 has no PR, YT-111 has no workflow/review state, GH-100 has no assignee
- PR states: Open (IWLE-123, YT-222), Merged (IWLE-456), Closed (YT-111)
- Progress levels: 10% (GH-100), 33% (IWLE-123), 67% (YT-222), 100% (IWLE-456)

**Decisions made:**
- Used imperative argument parsing (`var` + `while`) for CLI flags - simple but noted for future refactoring
- `generateSampleState()` uses `Instant.now()` for timestamps - generates fresh data each run
- Sample data in TestFixtures uses `lazy val` - initialized only when accessed in tests

**Patterns applied:**
- Pure function: `SampleDataGenerator.generateSampleState()` has no side effects
- Factory pattern: Central utility generates complete, consistent sample state
- Test fixture composition: `SampleData` object provides reusable test data

**Testing:**
- Unit tests: 22 tests across `SampleDataGeneratorTest.scala` and `SampleDataTest.scala`
- Test coverage: All cache types, edge cases, determinism, and serialization round-trip

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20260120-113500.md
- Major findings: 0 critical issues, 4 warnings (imperative arg parsing), 9 suggestions

**For next phases:**
- Available utilities: `SampleDataGenerator.generateSampleState()` for any sample data needs
- Extension points: Sample data can be extended with more worktrees or cache scenarios
- Notes: Warnings about imperative CLI parsing deferred - isolated to dashboard.scala

**Files changed:**
```
M	.iw/commands/dashboard.scala
A	.iw/core/domain/SampleDataGenerator.scala
A	.iw/core/test/SampleDataGeneratorTest.scala
A	.iw/core/test/SampleDataTest.scala
M	.iw/core/test/TestFixtures.scala
```

---

## Phase 3: Run server with custom project directory - SKIPPED (2026-01-23)

**Status:** Skipped after investigation

**Original intent:**
The analysis proposed a `--project=<path>` flag to "run the dashboard server pointing to a specific project directory" for testing UI features in a different project context.

**Investigation findings:**

After implementing and reviewing, we discovered the analysis was based on incorrect assumptions about the dashboard architecture:

1. **The dashboard is global, not project-specific**
   - It shows ALL registered worktrees from all projects
   - Main projects are derived FROM the worktrees themselves via `MainProjectService.deriveFromWorktrees()`
   - There is no "current project" concept at the dashboard level

2. **The `config` parameter in `renderDashboard()` is dead code**
   - It's passed but never used
   - Each worktree card loads its OWN config from its own `wt.path`
   - The config loaded at route level has no effect

3. **Auto-prune wouldn't change with `--project`**
   - Worktree paths are absolute (e.g., `/home/user/projects/foo`)
   - `os.Path(wt.path, basePath)` ignores `basePath` for absolute paths
   - Changing the base path has no effect

4. **API routes already support `?project=` parameters**
   - `/api/issues/search?project=<path>` - already works
   - `/api/worktrees/create` with `projectPath` form field - already works
   - The UI passes these via HTMX calls

**Conclusion:**

The `--project` flag as designed would accomplish nothing useful. The testing use cases are already covered:
- **Testing with isolated state** → `--state-path` (Phase 1)
- **Testing with sample data** → `--sample-data` (Phase 2)
- **Testing with specific tracker config** → API `?project=` params (already exist)

**Impact on remaining phases:**

Phase 4 and Phase 5 do NOT depend on Phase 3:
- Phase 4 combines `--state-path` + `--sample-data` into `--dev` flag
- Phase 5 validates isolation (state file, not project config)

**Decision:** Skip Phase 3, continue with Phase 4.

---

## Phase 4: Combined development mode flag (2026-01-23)

**What was built:**
- CLI flag: Added `--dev` flag to `dashboard` command that combines isolated state + sample data
- Temp directory: Auto-generates timestamped directory at `/tmp/iw-dev-<timestamp>/`
- Isolated config: Creates default `config.json` in temp directory (port 9876, localhost)
- Dev mode banner: Yellow "DEV MODE" banner displayed in dashboard header
- Console output: Informative messages showing all temp paths being used

**Decisions made:**
- Timestamped temp directory ensures multiple dev sessions can run in parallel
- Explicit `--state-path` takes precedence over auto-generated temp path
- Auto-enable `--sample-data` when `--dev` is used (convenience)
- Banner uses warning-style yellow background for visibility without being intrusive

**Patterns applied:**
- Threaded parameters: `devMode` parameter flows from CLI → CaskServer → DashboardService
- Conditional rendering: Banner rendered only when `devMode=true` (no empty div otherwise)
- Default parameters: `devMode: Boolean = false` maintains backward compatibility

**Testing:**
- Unit tests: 6 new tests covering:
  - DashboardService renders banner when devMode=true
  - DashboardService does NOT render banner when devMode=false
  - DashboardService banner has correct CSS class
  - CSS includes dev-mode-banner styles
  - CaskServer accepts devMode parameter
  - CaskServer factory method accepts devMode
- Fixed 2 pre-existing test bugs (Zed button tests missing issueCache data)

**Code review:**
- All tests passing (1328 unit tests)

**For next phases:**
- Dev mode infrastructure complete
- Phase 5 can use `--dev` for isolation validation testing

**Files changed:**
```
M	.iw/commands/dashboard.scala
M	.iw/core/CaskServer.scala
M	.iw/core/DashboardService.scala
M	.iw/core/test/CaskServerTest.scala
M	.iw/core/test/DashboardServiceTest.scala
```

---
