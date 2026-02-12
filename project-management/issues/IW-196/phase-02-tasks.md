# Phase 2 Tasks: Scalafix configuration check

## Tests First (TDD)

- [x] [test] Create `ScalafixChecksTest.scala` with test: config file exists → Success
- [x] [test] Add test: config file missing → Error with hint
- [x] [test] Add test: config has DisableSyntax with all required rules → Success
- [x] [test] Add test: config has DisableSyntax but missing some rules → WarningWithHint
- [x] [test] Add test: config missing DisableSyntax entirely → WarningWithHint
- [x] [test] Add test: config file unreadable → Error
- [x] [test] Run tests to confirm they fail (no implementation yet)

## Implementation

- [x] [impl] Create `ScalafixChecks.scala` in `core/model/` with pure check functions
- [x] [impl] Implement `checkConfigExistsWith` - file existence check
- [x] [impl] Implement `checkDisableSyntaxRulesWith` - DisableSyntax rule check with required sub-rules
- [x] [impl] Add wrapper functions using real `os` operations
- [x] [impl] Run unit tests to confirm they pass

## Integration

- [x] [impl] Create `scalafix.hook-doctor.scala` in `.iw/commands/`
- [x] [test] Add E2E test to `doctor.bats`: Scalafix check appears when `.scalafix.conf` exists
- [x] [test] Add E2E test: Scalafix check shows error when `.scalafix.conf` missing

## Verification

- [x] [verify] Run full unit test suite: `./iw test unit`
- [x] [verify] Run E2E tests: `bats .iw/test/doctor.bats`
