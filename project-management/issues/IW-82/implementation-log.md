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

## Phase 3: Run server with custom project directory (2026-01-20)

**What was built:**
- CLI flag: Added `--project=<path>` flag to `dashboard` command
- CaskServer: Added `projectPath: Option[os.Path]` constructor parameter
- Dashboard route: Uses custom project path for config loading and auto-prune checks
- UI indicator: Displays subtle project path indicator when custom project is used

**Decisions made:**
- Used `projectPath.getOrElse(os.pwd)` pattern - consistent with existing flags, maintains backward compatibility
- UI indicator styled subtly with gray text and monospace code font - doesn't dominate the dashboard header
- Kept existing API routes unchanged - they already support `?project=` query parameter

**Patterns applied:**
- Option type with getOrElse: Consistent with Phase 1's effectiveStatePath pattern
- Constructor parameter threading: Pass projectPath through CaskServer → DashboardService
- Conditional rendering: Only show project indicator when projectPath.isDefined

**Testing:**
- Unit tests: 1 (CaskServer constructor accepts projectPath parameter)
- Integration tests: 1 (GET / uses projectPath for config loading when provided)
- Pre-existing failures: 2 Zed button tests (unrelated to Phase 3)

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20260120-210000.md
- Major findings: 0 critical issues, 0 warnings, 2 suggestions (deferred)

**For next phases:**
- Available utilities: `effectiveProjectPath` pattern for project-aware operations
- Extension points: Phase 4's `--dev` flag can use `--project` internally
- Notes: All flags (`--state-path`, `--sample-data`, `--project`) are orthogonal and can be combined

**Files changed:**
```
M	.iw/commands/dashboard.scala
M	.iw/core/CaskServer.scala
M	.iw/core/DashboardService.scala
A	.iw/core/test/CaskServerTest.scala
```

---
