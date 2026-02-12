# Phase 2: Scalafix configuration check

## Goal

Add a quality gate check for Scalafix configuration to `iw doctor`. The check verifies that a project has a `.scalafix.conf` file with the DisableSyntax rule configured (noNulls, noVars, noThrows, noReturns).

## Scope

### In Scope
- Pure check functions for Scalafix config presence and DisableSyntax rule
- Hook-doctor file to expose checks for discovery
- Unit tests for check functions
- E2E test for doctor output

### Out of Scope
- HOCON parsing library — use simple regex/string matching (same approach as Scalafmt version check)
- Check grouping/filtering (Phase 6)
- Fix remediation (Phase 7)

## Dependencies

- Phase 0 (complete): Check types in `core/model/`
- Phase 1 (complete): Pattern established by ScalafmtChecks

## Approach

Follow the same pattern as Phase 1 (ScalafmtChecks):

1. **Pure check functions** in `core/model/ScalafixChecks.scala`:
   - `checkConfigExistsWith(config, fileExists)`: File existence check
   - `checkDisableSyntaxRulesWith(config, readFile)`: Check for DisableSyntax rule with required sub-rules

2. **Hook-doctor file** at `.iw/commands/scalafix.hook-doctor.scala`

3. **Unit tests** in `.iw/core/test/ScalafixChecksTest.scala`

4. **E2E test** additions to `.iw/test/doctor.bats`

## Files to Create

- `.iw/core/model/ScalafixChecks.scala`
- `.iw/commands/scalafix.hook-doctor.scala`
- `.iw/core/test/ScalafixChecksTest.scala`

## Files to Modify

- `.iw/test/doctor.bats` - Add E2E test for Scalafix check

## Testing Strategy

### Unit Tests
- Config file exists → `Success`
- Config file missing → `Error` with hint
- Config has DisableSyntax with all required rules → `Success`
- Config has DisableSyntax but missing some rules → `WarningWithHint` listing missing rules
- Config missing DisableSyntax rule entirely → `WarningWithHint`
- Config file unreadable → `Error`

### E2E Tests
- Doctor shows Scalafix check results when `.scalafix.conf` exists
- Doctor shows error when `.scalafix.conf` missing

## Acceptance Criteria

- [x] `iw doctor` reports Scalafix config presence and DisableSyntax rule status
- [x] Checks are pure functions with injected file-reading for testability
- [x] Unit tests cover all scenarios
- [x] E2E test validates output format
