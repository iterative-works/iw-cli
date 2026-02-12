# Phase 1: Scalafmt configuration check

## Goal

Add a quality gate check for Scalafmt configuration to `iw doctor`. The check verifies that a project has a `.scalafmt.conf` file with a configured version. This establishes the pattern for all subsequent quality gate checks (Phases 2-5).

## Scope

### In Scope
- Pure check functions for Scalafmt config presence and version
- Hook-doctor file to expose checks for discovery
- Unit tests for check functions
- E2E test for doctor output

### Out of Scope
- Build system detection (Phase 5+)
- Skipping Scalafmt check for non-Scala projects (deferred to Phase 6 filtering)
- Check grouping/filtering (Phase 6)
- Fix remediation (Phase 7)

## Dependencies

- Phase 0 (complete): Check types now live in `core/model/`

## Approach

Follow the existing two-function pattern from `GitHubHookDoctor`:

1. **Pure check functions** in `core/model/ScalafmtChecks.scala`:
   - `checkConfigExistsWith(config, fileExists: os.Path => Boolean): CheckResult`
   - `checkVersionExistsWith(config, readFile: os.Path => Option[String]): CheckResult`
   - Wrapper functions that use real `os` operations

2. **Hook-doctor file** at `.iw/commands/scalafmt.hook-doctor.scala`:
   - Exposes check values for hook discovery

3. **Unit tests** in `.iw/core/test/ScalafmtChecksTest.scala`:
   - Test all three scenarios: config present with version, config missing, config without version

4. **E2E test** additions to `.iw/test/doctor.bats`:
   - Test Scalafmt check appears in doctor output

## Files to Create

- `.iw/core/model/ScalafmtChecks.scala` - Pure check functions
- `.iw/commands/scalafmt.hook-doctor.scala` - Hook discovery wrapper
- `.iw/core/test/ScalafmtChecksTest.scala` - Unit tests

## Files to Modify

- `.iw/test/doctor.bats` - Add E2E test for Scalafmt check

## Testing Strategy

### Unit Tests (ScalafmtChecksTest.scala)
- Config file exists with version → `Success`
- Config file missing → `Error` with hint to create `.scalafmt.conf`
- Config file exists but no version → `WarningWithHint` with hint to add version

### E2E Tests (doctor.bats)
- Doctor shows Scalafmt check results when `.scalafmt.conf` exists
- Doctor shows error when `.scalafmt.conf` missing

## Acceptance Criteria

- [x] `iw doctor` reports Scalafmt config presence and version status
- [x] Checks are pure functions with injected file-reading for testability
- [x] Unit tests cover all three scenarios
- [x] E2E test validates output format
- [x] Check functions follow the `*With` dependency injection pattern
- [x] Hook-doctor file follows existing naming convention (`scalafmt.hook-doctor.scala`)
