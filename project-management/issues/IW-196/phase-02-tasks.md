# Phase 2 Tasks: Scalafix configuration check

## Tests First (TDD)

- [ ] [test] Create `ScalafixChecksTest.scala` with test: config file exists → Success
- [ ] [test] Add test: config file missing → Error with hint
- [ ] [test] Add test: config has DisableSyntax with all required rules → Success
- [ ] [test] Add test: config has DisableSyntax but missing some rules → WarningWithHint
- [ ] [test] Add test: config missing DisableSyntax entirely → WarningWithHint
- [ ] [test] Add test: config file unreadable → Error
- [ ] [test] Run tests to confirm they fail (no implementation yet)

## Implementation

- [ ] [impl] Create `ScalafixChecks.scala` in `core/model/` with pure check functions
- [ ] [impl] Implement `checkConfigExistsWith` - file existence check
- [ ] [impl] Implement `checkDisableSyntaxRulesWith` - DisableSyntax rule check with required sub-rules
- [ ] [impl] Add wrapper functions using real `os` operations
- [ ] [impl] Run unit tests to confirm they pass

## Integration

- [ ] [impl] Create `scalafix.hook-doctor.scala` in `.iw/commands/`
- [ ] [test] Add E2E test to `doctor.bats`: Scalafix check appears when `.scalafix.conf` exists
- [ ] [test] Add E2E test: Scalafix check shows error when `.scalafix.conf` missing

## Verification

- [ ] [verify] Run full unit test suite: `./iw test unit`
- [ ] [verify] Run E2E tests: `bats .iw/test/doctor.bats`
